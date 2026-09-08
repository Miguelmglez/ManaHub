package com.mmg.manahub.feature.scanner.presentation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

/**
 * Plays short PCM tones proportional to a scanned card's price.
 *
 * All audio buffers are generated programmatically in the constructor — no files
 * in `res/raw/` are needed. The three tones map to price brackets:
 *
 * - **neutral**  (440 Hz, A4)              → price < 1 €
 * - **high**     (880 Hz, A5)              → price 1–10 €
 * - **triumph**  (1047 Hz C6 + 784 Hz G5)  → price > 10 € (two-frequency chord)
 *
 * Each tone is 200 ms, 44 100 Hz, mono, 16-bit PCM.
 *
 * ### Playback strategy (WS5, `scanner-reliability-plan.md`, 2026-08-25)
 * One [AudioTrack] per tone is built ONCE, in [AudioTrack.MODE_STATIC], with its PCM buffer
 * written exactly once at construction time — [trackNeutral], [trackHigh] and [trackTriumph]
 * live for the whole process. A play request simply rewinds the track's playback head to 0 and
 * calls [AudioTrack.play] again, the standard documented technique for replaying a static-mode
 * track without reallocating it (`setPlaybackHeadPosition(0)` is only valid while the track is
 * stopped, which is why [playBuffer] always calls [AudioTrack.stop] first — a harmless no-op if
 * the track had already finished playing on its own).
 *
 * This replaces the previous implementation, which spawned a brand-new [Thread] and built a
 * brand-new [AudioTrack] on every single successful scan — significant thread and audio-HAL
 * churn over the long scanning sessions this whole reliability plan targets.
 *
 * Track-control calls (`stop` / `setPlaybackHeadPosition` / `play`) are dispatched on a single
 * bounded background executor ([playbackExecutor]) so the caller (the scanner's recognition
 * pipeline) is never blocked. Those calls return in microseconds — the actual audio plays out
 * asynchronously on the system's audio mixer, not on the executor thread — so a burst of scans
 * landing close together is simply serialized as a queue of near-instant control calls; no sound
 * is ever dropped, and the executor never backs up.
 *
 * [SoundManager] is a Hilt `@Singleton`, alive for the whole app process (see
 * `ScannerModule.provideSoundManager`). [release] genuinely stops and releases all three
 * [AudioTrack]s and shuts down [playbackExecutor], but **nothing should call it from a
 * screen-scoped lifecycle** (e.g. a `ViewModel.onCleared()`) — doing so would permanently kill
 * scan sounds for the rest of the process the next time the user opens the scanner, the exact
 * class of bug fixed for `CardOcrAnalyzer` in WS1 of the scanner reliability plan. `release()` is
 * therefore intentionally uncalled today (verified: no call site remains anywhere in the app)
 * and exists only for a future owner of the singleton's full lifecycle (e.g. process shutdown
 * hooks), should one ever be added.
 */
@Singleton
class SoundManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // ── Audio format constants ───────────────────────────────────────────────
    private val sampleRate = 44_100
    private val durationMs = 200

    // ── Shared AudioAttributes/format for all tracks ─────────────────────────
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val audioFormat = AudioFormat.Builder()
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(sampleRate)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .build()

    // ── One long-lived, pre-written AudioTrack per tone ──────────────────────
    private val trackNeutral: AudioTrack = buildStaticTrack(generateTone(440.0))
    private val trackHigh: AudioTrack = buildStaticTrack(generateTone(880.0))
    private val trackTriumph: AudioTrack = buildStaticTrack(generateChord(1047.0, 784.0))

    // ── Single bounded executor serializing playback control calls ──────────
    private val playbackExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var released = false

    // ─────────────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Plays the appropriate tone for the given price.
     *
     * If [priceEur] is null and [priceUsdFallback] is provided, the USD price is
     * converted at an approximate 0.9 rate and used instead.
     *
     * Playback is asynchronous; this function returns immediately.
     *
     * @param priceEur        Card price in EUR, or null if unavailable.
     * @param priceUsdFallback Card price in USD, used only when [priceEur] is null.
     */
    fun playForPrice(priceEur: Double?, priceUsdFallback: Double? = null) {
        val price = priceEur ?: (priceUsdFallback?.times(0.9))
        val track = when {
            price == null || price < 1.0 -> trackNeutral
            price < 10.0 -> trackHigh
            else -> trackTriumph
        }
        playBuffer(track)
    }

    /**
     * Releases the [AudioTrack]s and background executor held by this [SoundManager].
     *
     * After calling this, no further playback should be requested — see the class KDoc for
     * why this must never be wired to a screen-scoped lifecycle while [SoundManager] remains a
     * process-wide singleton.
     */
    fun release() {
        if (released) return
        released = true
        playbackExecutor.execute {
            for (track in listOf(trackNeutral, trackHigh, trackTriumph)) {
                try {
                    track.stop()
                } catch (_: Exception) {
                    // Track may already be stopped/uninitialized — non-critical.
                } finally {
                    track.release()
                }
            }
        }
        playbackExecutor.shutdown()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PCM generation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a single-frequency PCM sine wave.
     *
     * @param freqHz     Frequency in Hz.
     * @param durationMs Duration in milliseconds. Defaults to [SoundManager.durationMs].
     * @param sampleRate Sample rate in Hz. Defaults to [SoundManager.sampleRate].
     * @return           [ShortArray] with the PCM samples (amplitude capped at 60% of max).
     */
    private fun generateTone(
        freqHz: Double,
        durationMs: Int = this.durationMs,
        sampleRate: Int = this.sampleRate,
    ): ShortArray {
        val samples = sampleRate * durationMs / 1000
        return ShortArray(samples) { i ->
            val angle = 2.0 * PI * i * freqHz / sampleRate
            (sin(angle) * Short.MAX_VALUE * 0.6).toInt().toShort()
        }
    }

    /**
     * Generates a two-frequency chord by summing two sine waves and normalising
     * to prevent clipping (peak amplitude capped at 60% of max).
     *
     * @param freq1Hz First frequency in Hz.
     * @param freq2Hz Second frequency in Hz.
     * @return        [ShortArray] with the blended PCM samples.
     */
    private fun generateChord(freq1Hz: Double, freq2Hz: Double): ShortArray {
        val samples = sampleRate * durationMs / 1000
        return ShortArray(samples) { i ->
            val angle1 = 2.0 * PI * i * freq1Hz / sampleRate
            val angle2 = 2.0 * PI * i * freq2Hz / sampleRate
            val combined = (sin(angle1) + sin(angle2)) / 2.0  // normalise to [-1, 1]
            (combined * Short.MAX_VALUE * 0.6).toInt().toShort()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Track construction & playback
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a [AudioTrack.MODE_STATIC] track sized for [buffer] and writes it exactly once.
     * The returned track is replayed for the lifetime of this [SoundManager] via [playBuffer]
     * instead of being rebuilt on every play.
     *
     * @param buffer PCM samples to write into the track's static buffer.
     */
    private fun buildStaticTrack(buffer: ShortArray): AudioTrack {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSizeBytes = maxOf(minBufferSize, buffer.size * 2)
        val track = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSizeBytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(buffer, 0, buffer.size)
        return track
    }

    /**
     * Rewinds [track] to its start and plays it, on [playbackExecutor] so the caller is never
     * blocked. Control calls return immediately; the audio itself plays out on the system mixer.
     *
     * @param track One of the pre-built, pre-written tone tracks.
     */
    private fun playBuffer(track: AudioTrack) {
        if (released) return
        playbackExecutor.execute {
            try {
                track.stop()
                track.setPlaybackHeadPosition(0)
                track.play()
            } catch (_: Exception) {
                // Silently swallow any audio errors — sound is non-critical.
            }
        }
    }
}
