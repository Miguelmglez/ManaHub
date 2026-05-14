package com.mmg.manahub.feature.scanner.presentation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * Playback runs on a background thread so the main thread is never blocked.
 */
@Singleton
class SoundManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // ── Audio format constants ───────────────────────────────────────────────
    private val sampleRate = 44_100
    private val durationMs = 200

    // ── Pre-generated PCM buffers ────────────────────────────────────────────
    private val toneNeutral: ShortArray = generateTone(440.0)
    private val toneHigh: ShortArray = generateTone(880.0)
    private val toneTriumph: ShortArray = generateChord(1047.0, 784.0)

    // ── Shared AudioAttributes for all tracks ────────────────────────────────
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val audioFormat = AudioFormat.Builder()
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(sampleRate)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .build()

    private val minBufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
    )

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
        val buffer = when {
            price == null || price < 1.0 -> toneNeutral
            price < 10.0 -> toneHigh
            else -> toneTriumph
        }
        playBuffer(buffer)
    }

    /**
     * Releases any resources held by the [SoundManager].
     *
     * After calling this, no further playback should be requested.
     */
    fun release() {
        // Buffers are plain ShortArrays — no native resources to release.
        // AudioTrack instances are created per-play and released inside the thread.
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
    //  Playback
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Writes [buffer] to a new [AudioTrack] and plays it on a daemon background thread.
     *
     * The track is released automatically after playback completes.
     *
     * @param buffer PCM samples to play.
     */
    private fun playBuffer(buffer: ShortArray) {
        Thread {
            val bufferSizeBytes = maxOf(minBufferSize, buffer.size * 2)
            val track = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSizeBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            try {
                track.write(buffer, 0, buffer.size)
                track.play()
                // Wait for playback to finish before releasing
                val durationMillis = (buffer.size.toLong() * 1000L / sampleRate) + 50L
                Thread.sleep(durationMillis)
            } catch (_: Exception) {
                // Silently swallow any audio errors — sound is non-critical
            } finally {
                track.stop()
                track.release()
            }
        }.apply { isDaemon = true }.start()
    }
}
