package com.mmg.manahub.feature.scanner.data

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.mapper.toDomainCard
import com.mmg.manahub.core.data.network.RateLimitExhaustedException
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.util.recordSafeNonFatal
import com.mmg.manahub.feature.scanner.domain.model.OcrCandidate
import com.mmg.manahub.feature.scanner.domain.model.RecognitionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * [ImageAnalysis.Analyzer] that identifies MTG cards by running ML Kit OCR on a
 * restricted name-zone strip of each camera frame.
 *
 * Pipeline per frame:
 * 1. Throttle (800 ms between processed frames) and re-entrance guard.
 * 2. Pass [ImageProxy.image] (YUV_420_888 [android.media.Image]) to [CardOcrAnalyzer].
 *    ML Kit receives the raw media image with [ImageProxy.imageInfo.rotationDegrees] so it
 *    handles rotation internally — no manual YUV→BGR conversion is needed.
 * 3. [CardOcrAnalyzer.extractCardName] filters OCR results to the on-screen name zone and
 *    scores every candidate (WS2.A) — [OcrCandidate.score] gates the network calls below.
 * 4. Name→card resolution — see the "Call-budget pipeline" section below.
 * 5. Emit [RecognitionResult] via [onResult].
 *
 * ### Guard + resource-ownership contract (WS1, 2026-08-24)
 * [isProcessing] and the per-frame [ImageProxy] are owned **entirely by the launched
 * coroutine**, never by the analyzer thread that calls [analyze]:
 * - [analyze] (the analyzer thread) only ever *reads* [isProcessing]; it never sets it to
 *   `true`. This is deliberate — if it set the guard before launching, a scope that never
 *   runs the coroutine body (already cancelled) or that dies mid-`launch` would latch the
 *   guard forever with no code path left to clear it.
 * - The coroutine sets [isProcessing] to `true` as its first statement, and a `finally` block
 *   both closes the [ImageProxy] and resets the guard on every path: success, null OCR
 *   result, timeout, any other exception, or cancellation.
 * - The OCR call is wrapped in [withTimeout] ([OCR_TIMEOUT_MS]); a stuck ML Kit `Task` can
 *   therefore never latch the guard past the timeout window.
 * - A **stall watchdog** is kept as a backstop: if a later frame observes [isProcessing]
 *   still `true` after [STALL_THRESHOLD_MS], it force-resets the guard and continues
 *   processing the new frame instead of dropping it, logging a `scanner_ocr_stall_recovered`
 *   breadcrumb.
 * - [CancellationException] is rethrown after cleanup, never swallowed as a generic pipeline
 *   failure — a cancelled coroutine is not an OCR/resolution error.
 *
 * ### Call-budget pipeline (WS2.B, 2026-08-24 — `docs/plans/scanner-reliability-plan.md`)
 * A single hard-to-read card used to cost ~3.75 Scryfall requests/second forever (finding F7):
 * every processed frame re-hit the network with up to 3 doomed calls, none of them ever cached
 * on failure. Every gate below runs strictly in order, and **any** gate short-circuits straight
 * to [RecognitionResult.NoCard] with zero network cost:
 *
 * 1. Candidate must exist and clear [MIN_CONFIDENCE_SCORE] (W2.5) — see the constant's KDoc for
 *    the threshold rationale.
 * 2. The **local 3-second memo** ([lastOcrCard]) short-circuits when the same physical card is
 *    still in frame — predates this workstream, kept as-is.
 * 3. **Pre-resolution stability** (W2.5): the same normalized OCR text must appear on
 *    [STABILITY_FRAMES_PRE_RESOLUTION] consecutive processed frames before any network call is
 *    even considered — this alone removes the majority of storm traffic, since one-frame OCR
 *    noise never survives it.
 * 4. **Negative cache** ([ScannerNameResolver], W2.6): a normalized name that already failed
 *    this session is trusted for [ScannerNameResolver.TTL_MS] — zero network on a repeat miss.
 * 5. **Active rate-limit cooldown** (W2.10): while [rateLimitedUntilMs] is in the future, every
 *    lookup is suspended outright — OCR keeps running so the on-screen guide stays live, but the
 *    scanner stops feeding an already-struggling shared queue.
 * 6. **Local-first Room lookup** (W2.7): a card the user already owns/has cached resolves
 *    instantly via [CardDao.findByExactNameForLanguage] — zero network.
 * 7. **Scanner-local lookup budget** ([LOOKUP_BUDGET_MAX] per [LOOKUP_BUDGET_WINDOW_MS], W2.10):
 *    a hard ceiling independent of every gate above, so a pathological OCR flip-flop (a
 *    genuinely different normalized string every 2 frames) still cannot exceed the budget.
 * 8. **The network ladder itself** ([resolveCard], W2.8) — at most 2 Scryfall calls per attempt,
 *    language-aware (see that function's KDoc).
 *
 * A result surviving all the way to a network round-trip is still subject to **staleness
 * rejection** (W2.9): if [generation] was bumped (pause / sheet open / language change / screen
 * exit) or more than [RESULT_MAX_AGE_MS] elapsed while the call was in flight (typically because
 * it queued behind the shared [com.mmg.manahub.core.data.network.RateLimitedQueue] cooldown), the
 * result is silently dropped — this is what kills the "cards appear minutes later" symptom (F8).
 *
 * @param cardRepository   Domain repository used to resolve card names via Scryfall.
 * @param cardOcrAnalyzer  ML Kit OCR wrapper that extracts the card name from the name zone.
 * @param scope            [CoroutineScope] for suspending Scryfall calls.
 * @param cardDao          Local Room cache, used ONLY for the W2.7 local-first lookup — never
 *                         written to directly (writes happen through [cardRepository]'s own
 *                         upsert paths on a successful network resolution).
 * @param initialLanguage  Initial value for [selectedLanguage] (default `"en"`).
 * @param ioDispatcher     Dispatcher used for [CardRepository]/[CardDao] calls. Defaults to
 *                         [Dispatchers.IO] in production; injectable so tests can substitute a
 *                         virtual-time `TestDispatcher` sharing the same scheduler as [scope] and
 *                         get deterministic `advanceUntilIdle()` behaviour instead of racing a
 *                         real dispatcher hop.
 * @param onResult         Callback invoked on each processed frame with the recognition outcome.
 */
class CardRecognizer(
    private val cardRepository: CardRepository,
    private val cardOcrAnalyzer: CardOcrAnalyzer,
    private val scope: CoroutineScope,
    private val cardDao: CardDao,
    initialLanguage: String = "en",
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val onResult: (RecognitionResult) -> Unit,
) : ImageAnalysis.Analyzer {

    companion object {
        /** Max time budget for a single OCR call before it is treated as a timed-out frame. */
        const val OCR_TIMEOUT_MS = 2_500L

        /** If [isProcessing] has been held this long, assume it latched and force-reset it. */
        const val STALL_THRESHOLD_MS = 5_000L

        /**
         * Minimum [OcrCandidate.score] required before ANY Scryfall call is even considered
         * (W2.5). `CardOcrAnalyzer`'s scoring is dominated by relative line height
         * (`WEIGHT_HEIGHT = 100f`) — the card name, being the tallest line in the zone, always
         * scores `heightRatio == 1.0` (its own definition: height ÷ tallest-in-zone height) and
         * is EXEMPT from the keyword penalty while tallest, so a genuine name floors at
         * `100` (height alone) even with zero positional credit, typically landing `100-140`
         * once horizontal/vertical centring (`+25`/`+15` max) is added. A non-tallest rival
         * (type line, rules text) nets its own height contribution (commonly `60-90` for a
         * smaller-font line) MINUS whichever penalty applies — a dashed type line loses
         * `PENALTY_DASH = 80` (net ≤ 10-50 even with full positional credit), rules text over 40
         * chars loses `PENALTY_RULES_TEXT = 60` (net ≤ 30-70), a non-tallest keyword hit loses
         * `PENALTY_KEYWORD = 50` (net ≤ 40-80). `45f` sits with ~55 points of margin below a real
         * name's ~100 floor (room for a badly off-centre name) while staying above where the
         * common penalized-junk cases land once netted.
         */
        const val MIN_CONFIDENCE_SCORE = 45f

        /**
         * Consecutive PROCESSED frames the same normalized OCR text must appear on before any
         * Scryfall call is attempted (W2.5). `ScannerViewModel`'s own post-RESOLUTION stability
         * gate ([HIGH_CONFIDENCE_FRAMES] = 1) is unrelated and stays as-is — this gate runs
         * strictly BEFORE resolution, the ViewModel's gate runs strictly AFTER.
         */
        const val STABILITY_FRAMES_PRE_RESOLUTION = 2

        /**
         * A resolved result older than this (wall-clock, measured from the frame that triggered
         * it) is dropped instead of reaching [onResult] — W2.9. Chosen to comfortably exceed a
         * normal Scryfall round-trip (sub-second) while catching the specific failure mode this
         * exists for: a call that queued behind the shared rate-limit cooldown ladder
         * (`RateLimitConfig`, 1s→2s→5s→15s, `maxCooldownMs = 30s`) and completed long after the
         * user moved the camera away.
         */
        const val RESULT_MAX_AGE_MS = 4_000L

        /** Rolling window for the scanner-local Scryfall lookup budget (W2.10). */
        const val LOOKUP_BUDGET_WINDOW_MS = 10_000L

        /** Max Scryfall lookup ATTEMPTS (each ≤ 2 HTTP calls, see [resolveCard]) per [LOOKUP_BUDGET_WINDOW_MS]. */
        const val LOOKUP_BUDGET_MAX = 6

        /** TTL for the local "same card still in frame" memo — predates WS2.B, kept as-is. */
        private const val OCR_CACHE_TTL_MS = 3_000L
    }

    private val minIntervalMs = 800L
    @Volatile private var lastProcessedMs = 0L
    private val isProcessing = AtomicBoolean(false)

    /** Wall-clock time [isProcessing] was last set to `true`; backs the stall watchdog. */
    @Volatile private var processingStartedAtMs = 0L

    /**
     * Current scan language. A custom setter (rather than a bare `@Volatile var`) so changing it
     * transparently resets every per-language-session mechanism below (W2.11) — existing call
     * sites that simply assign `recognizer.selectedLanguage = newLanguage` (see `ScannerScreen`'s
     * `LaunchedEffect(selectedLanguage)`) get the reset for free, no call-site change needed.
     */
    @Volatile
    var selectedLanguage: String = initialLanguage
        set(value) {
            if (field == value) return
            field = value
            // WS4: low-cardinality (6 values) session context tagging every later non-fatal
            // with the active scan language — essential for diagnosing language-specific bugs.
            FirebaseCrashlytics.getInstance().setCustomKey("scanner_selected_lang", value)
            resetForLanguageChange()
        }

    // ── Local 3s memo — "same physical card still in frame", predates WS2.B ──────────────────
    private var lastOcrKey: String? = null
    private var lastOcrCard: Card? = null
    private var lastOcrTimeMs: Long = 0L
    private var lastOcrLanguageFallback: Boolean = false

    // ── W2.5: pre-resolution stability buffer ─────────────────────────────────────────────────
    private var lastStableKey: String? = null
    private var stableFrameCount: Int = 0

    // ── W2.6: negative cache (scanner-local, session-scoped) ──────────────────────────────────
    private val negativeCache = ScannerNameResolver()

    // ── W2.9: staleness — bumped on pause / sheet open / language change / screen exit ────────
    private val generation = AtomicInteger(0)

    // ── W2.10: active rate-limit cooldown + rolling lookup budget ─────────────────────────────
    @Volatile private var rateLimitedUntilMs: Long? = null
    private val lookupTimestamps = ArrayDeque<Long>()

    // ── WS4 (2026-08-25, scanner-reliability-plan.md): session counters for the call-budget
    // skip gates. These gates can fire up to ~1.25×/second (the 800 ms frame throttle), so a
    // log() breadcrumb per occurrence would flood Crashlytics' ring buffer — an accumulating
    // instance counter flushed via setCustomKey (in-memory overwrite, no network/I/O) is used
    // instead. Kept as separate `var`s (not a map) so each has a fixed, typo-proof key name. ──
    private var lowConfidenceSkipCount = 0
    private var negativeCacheSkipCount = 0
    private var budgetSkipCount = 0
    private var staleDropGenerationCount = 0
    private var staleDropAgeCount = 0

    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()

        if (now - lastProcessedMs < minIntervalMs) {
            imageProxy.close()
            return
        }

        if (isProcessing.get()) {
            val heldForMs = now - processingStartedAtMs
            if (heldForMs < STALL_THRESHOLD_MS) {
                // A previous frame's coroutine is still legitimately in flight — drop this
                // one, same as today.
                imageProxy.close()
                return
            }
            // Stall watchdog: the guard has been held far longer than any real OCR call
            // should take. Force-reset and process this frame instead of dropping it too.
            FirebaseCrashlytics.getInstance().log("scanner_ocr_stall_recovered")
            isProcessing.set(false)
        }

        lastProcessedMs = now

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            onResult(RecognitionResult.NoCard)
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        // Launches on [scope]'s own dispatcher (Dispatchers.Default in production — see
        // ScannerScreen's recognizerScope) rather than forcing Dispatchers.Default here.
        // This keeps prod behaviour identical while letting tests inject a virtual-time
        // TestScope/StandardTestDispatcher to deterministically exercise withTimeout.
        scope.launch {
            // First statement inside the coroutine: the guard is only ever claimed once the
            // coroutine body is actually running (see class KDoc for why).
            isProcessing.set(true)
            processingStartedAtMs = System.currentTimeMillis()
            val myGeneration = generation.get()
            try {
                // imageProxy must remain open until ML Kit finishes consuming mediaImage —
                // it is closed in the finally block below, on every path.
                val candidate = extractCardNameWithTimeout(mediaImage, rotationDegrees)

                if (candidate == null) {
                    resetStabilityBuffer()
                    if (com.mmg.manahub.BuildConfig.DEBUG) {
                        android.util.Log.d("CardRecognizer", "OCR: nothing in name zone")
                    }
                    onResult(RecognitionResult.NoCard)
                    return@launch
                }

                // WS4: numeric score only — never the OCR text itself (PII/free-text guard).
                FirebaseCrashlytics.getInstance().setCustomKey("scanner_ocr_score", candidate.score)

                if (candidate.score < MIN_CONFIDENCE_SCORE) {
                    resetStabilityBuffer()
                    lowConfidenceSkipCount++
                    FirebaseCrashlytics.getInstance()
                        .setCustomKey("scanner_low_confidence_skips_session", lowConfidenceSkipCount)
                    if (com.mmg.manahub.BuildConfig.DEBUG) {
                        android.util.Log.d(
                            "CardRecognizer",
                            "OCR: candidate below confidence threshold (score=${candidate.score})",
                        )
                    }
                    onResult(RecognitionResult.NoCard)
                    return@launch
                }

                val cardName = candidate.text
                val normalizedKey = normalizeForKey(cardName)

                if (com.mmg.manahub.BuildConfig.DEBUG) {
                    android.util.Log.d(
                        "CardRecognizer",
                        "OCR [$selectedLanguage]: '$cardName' (score=${candidate.score})",
                    )
                }

                val resolvedAtMs = System.currentTimeMillis()

                // ── Local 3s memo: same physical card still in frame — zero network ─────────
                if (normalizedKey == lastOcrKey &&
                    lastOcrCard != null &&
                    (resolvedAtMs - lastOcrTimeMs) < OCR_CACHE_TTL_MS
                ) {
                    onResult(
                        RecognitionResult.Identified(
                            card = lastOcrCard!!,
                            similarity = 1.0f,
                            ambiguous = false,
                            corners = emptyList(),
                            languageFallback = lastOcrLanguageFallback,
                        )
                    )
                    return@launch
                }

                // ── W2.5: pre-resolution stability ───────────────────────────────────────────
                if (normalizedKey == lastStableKey) {
                    stableFrameCount++
                } else {
                    lastStableKey = normalizedKey
                    stableFrameCount = 1
                }
                if (stableFrameCount < STABILITY_FRAMES_PRE_RESOLUTION) {
                    onResult(RecognitionResult.NoCard)
                    return@launch
                }

                // ── W2.6: negative cache ─────────────────────────────────────────────────────
                if (negativeCache.isNegative(normalizedKey)) {
                    negativeCacheSkipCount++
                    FirebaseCrashlytics.getInstance()
                        .setCustomKey("scanner_negcache_hits_session", negativeCacheSkipCount)
                    onResult(RecognitionResult.NoCard)
                    return@launch
                }

                // ── W2.10: suspend all lookups while an active cooldown is running ──────────
                val activeCooldown = rateLimitedUntilMs
                if (activeCooldown != null && resolvedAtMs < activeCooldown) {
                    onResult(RecognitionResult.NoCard)
                    return@launch
                }

                // ── W2.7: local-first Room lookup — zero network for an already-cached card ─
                val localCard = withContext(ioDispatcher) {
                    cardDao.findByExactNameForLanguage(cardName, selectedLanguage)
                }?.toDomainCard()
                if (localCard != null) {
                    onResult(cacheAndBuildIdentified(normalizedKey, localCard))
                    return@launch
                }

                // ── W2.10: scanner-local rolling lookup budget ──────────────────────────────
                if (!tryAcquireLookupBudget()) {
                    budgetSkipCount++
                    FirebaseCrashlytics.getInstance()
                        .setCustomKey("scanner_budget_skips_session", budgetSkipCount)
                    onResult(RecognitionResult.NoCard)
                    return@launch
                }

                // ── W2.8: network resolution ladder (≤2 calls) ──────────────────────────────
                val langAtAttemptStart = selectedLanguage
                val outcome = resolveCard(cardName, langAtAttemptStart)

                // ── W2.9: staleness rejection — drop a superseded or too-late result ────────
                // WS4: kept as TWO separate counters (not one combined) — which one dominates
                // tells us whether stale drops come from pause/language-change (generation) or
                // a rate-limit queue backlog (age); a combined counter would lose that signal.
                if (myGeneration != generation.get()) {
                    staleDropGenerationCount++
                    FirebaseCrashlytics.getInstance()
                        .setCustomKey("scanner_stale_drop_generation_count", staleDropGenerationCount)
                    return@launch
                }
                if (System.currentTimeMillis() - processingStartedAtMs > RESULT_MAX_AGE_MS) {
                    staleDropAgeCount++
                    FirebaseCrashlytics.getInstance()
                        .setCustomKey("scanner_stale_drop_age_count", staleDropAgeCount)
                    return@launch
                }

                when (outcome) {
                    is ResolutionOutcome.Found -> {
                        onResult(cacheAndBuildIdentified(normalizedKey, outcome.card))
                    }
                    ResolutionOutcome.NotFound -> {
                        negativeCache.recordFailure(normalizedKey)
                        onResult(RecognitionResult.NoCard)
                    }
                    is ResolutionOutcome.RateLimited -> {
                        val newCutoff = System.currentTimeMillis() + outcome.retryAfterMs
                        val wasAlreadyActive = rateLimitedUntilMs?.let { it > System.currentTimeMillis() } == true
                        rateLimitedUntilMs = newCutoff
                        recordSafeNonFatal(
                            "scanner_scryfall_rate_limited",
                            RuntimeException("retryAfterMs=${outcome.retryAfterMs}"),
                        )
                        if (wasAlreadyActive) {
                            onResult(RecognitionResult.NoCard)
                        } else {
                            onResult(RecognitionResult.RateLimited(outcome.retryAfterMs))
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Not a pipeline failure — the scope was torn down or the frame superseded.
                // Cleanup still runs via `finally`; rethrow afterwards.
                throw e
            } catch (e: Exception) {
                if (com.mmg.manahub.BuildConfig.DEBUG) {
                    android.util.Log.w("CardRecognizer", "Pipeline exception", e)
                } else {
                    android.util.Log.w("CardRecognizer", "Pipeline exception: ${e.javaClass.simpleName}")
                }
                onResult(RecognitionResult.NoCard)
            } finally {
                imageProxy.close()
                isProcessing.set(false)
            }
        }
    }

    /**
     * Bumps [generation], invalidating any in-flight resolution attempt's result the moment it
     * completes (W2.9). Call whenever the current attempt is no longer relevant: recognition
     * paused (top-bar toggle or a covering sheet/overlay), and defensively on screen exit
     * (`recognizerScope.cancel()` already prevents the coroutine from completing, but bumping
     * here too costs nothing and removes any doubt).
     */
    fun bumpGeneration() {
        generation.incrementAndGet()
    }

    /**
     * Resets the pipeline state that must not survive a full camera stop (W3.5,
     * `scanner-reliability-plan.md`, 2026-08-24 — `ScannerScreen`'s `CameraPreview` calling
     * `cameraProvider.unbindAll()` for a covering sheet/overlay, as opposed to an
     * `isRecognitionPausedByUser`-style analyzer-only pause):
     * - [bumpGeneration] (reused, not duplicated) so a resolution attempt already in flight the
     *   moment the camera stops has its result dropped on arrival, same mechanism as any other
     *   pause.
     * - [resetStabilityBuffer] so a resumed session does not silently "complete" a 2-frame match
     *   against OCR text collected before the stop — the first frame after resume must re-earn
     *   stability from zero.
     *
     * Deliberately leaves the local 3s memo ([lastOcrCard]) and the negative cache
     * ([negativeCache]) untouched: unlike a language change ([resetForLanguageChange]), the
     * underlying card data and any prior failed lookups are still valid after a camera stop —
     * there's no reason to force a fresh network round trip for a card the user is still
     * pointing at moments later, or to re-attempt a name that just failed under the SAME
     * language.
     */
    fun resetForCameraStop() {
        bumpGeneration()
        resetStabilityBuffer()
    }

    /**
     * Resets every per-language-session mechanism (W2.11): the negative cache would otherwise
     * keep rejecting names purely because they failed under the OLD language's search; the
     * pre-resolution stability buffer and 3s memo reference OCR text that was resolved (or not)
     * under the old language; an active rate-limit cooldown is cleared too — the shared
     * [com.mmg.manahub.core.data.network.RateLimitedQueue] still enforces its own cooldown gate
     * server-side, so this only affects how eagerly THIS recognizer retries, not whether a retry
     * can bypass the shared limiter.
     */
    private fun resetForLanguageChange() {
        negativeCache.clear()
        generation.incrementAndGet()
        resetStabilityBuffer()
        lastOcrKey = null
        lastOcrCard = null
        lastOcrTimeMs = 0L
        lastOcrLanguageFallback = false
        rateLimitedUntilMs = null
    }

    private fun resetStabilityBuffer() {
        lastStableKey = null
        stableFrameCount = 0
    }

    /**
     * Normalizes [text] into the KEY used for the stability buffer / negative cache / 3s memo —
     * lowercase, trimmed, internal whitespace collapsed, diacritics stripped (NFD decomposition +
     * removing combining marks). The QUERY sent to Scryfall always uses the original [OcrCandidate.text],
     * never this normalized form — normalization is a cache/comparison concern only.
     */
    private fun normalizeForKey(text: String): String {
        val collapsed = text.lowercase().trim().replace(Regex("\\s+"), " ")
        val decomposed = Normalizer.normalize(collapsed, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "")
    }

    /**
     * Caches [card] as the current 3s memo entry and builds the [RecognitionResult.Identified] to
     * emit, computing [RecognitionResult.Identified.languageFallback] once here so both the
     * emission and any later memo-cache hit stay consistent.
     */
    private fun cacheAndBuildIdentified(normalizedKey: String, card: Card): RecognitionResult.Identified {
        val languageFallback = selectedLanguage != "en" && card.lang != selectedLanguage
        lastOcrKey = normalizedKey
        lastOcrCard = card
        lastOcrTimeMs = System.currentTimeMillis()
        lastOcrLanguageFallback = languageFallback
        return RecognitionResult.Identified(
            card = card,
            similarity = 1.0f,
            ambiguous = false,
            corners = emptyList(),
            languageFallback = languageFallback,
        )
    }

    /** Sliding-window budget gate (W2.10) — evicts entries older than [LOOKUP_BUDGET_WINDOW_MS] first. */
    private fun tryAcquireLookupBudget(): Boolean {
        val now = System.currentTimeMillis()
        while (lookupTimestamps.isNotEmpty() && now - lookupTimestamps.first() > LOOKUP_BUDGET_WINDOW_MS) {
            lookupTimestamps.removeFirst()
        }
        // WS4: recorded on BOTH the allowed and the rejected path (before the early return)
        // so the gauge always reflects the current window size, not just successful lookups.
        FirebaseCrashlytics.getInstance().setCustomKey("scanner_lookups_last_10s", lookupTimestamps.size)
        if (lookupTimestamps.size >= LOOKUP_BUDGET_MAX) return false
        lookupTimestamps.addLast(now)
        return true
    }

    /**
     * Runs [CardOcrAnalyzer.extractCardName] under a [OCR_TIMEOUT_MS] budget. On timeout,
     * records a `scanner_ocr_timeout` non-fatal (no raw OCR text — never PII) and treats the
     * frame as having produced no candidate, same as a genuine "nothing detected" result.
     */
    private suspend fun extractCardNameWithTimeout(
        mediaImage: android.media.Image,
        rotationDegrees: Int,
    ): OcrCandidate? {
        return try {
            withTimeout(OCR_TIMEOUT_MS) {
                cardOcrAnalyzer.extractCardName(mediaImage, rotationDegrees)
            }
        } catch (e: TimeoutCancellationException) {
            recordSafeNonFatal("scanner_ocr_timeout", e)
            null
        }
    }

    // ── W2.8: network resolution ladder ────────────────────────────────────────────────────────

    private sealed interface ResolutionOutcome {
        data class Found(val card: Card) : ResolutionOutcome
        data object NotFound : ResolutionOutcome
        data class RateLimited(val retryAfterMs: Long) : ResolutionOutcome
    }

    /**
     * At most 2 Scryfall calls per attempt, in either branch:
     * - `lang == "en"`: [callExactName] then, only on a non-rate-limited miss, [callFuzzyName].
     * - `lang != "en"`: [callPrintedName] (the ONLY way to match a non-English PRINTED name —
     *   `/cards/named`, used by the English branch, is English-only) then, only on a
     *   non-rate-limited miss, a SINGLE [callExactName] fallback (never the fuzzy sibling too —
     *   that would make this branch 3 calls deep, doubling this path's share of the shared
     *   Scryfall budget for no real benefit; a printing that isn't found by either an exact
     *   localized search OR an exact English name is treated as not found). A card resolved via
     *   this fallback is marked `languageFallback` by the caller (compares `card.lang` against
     *   [selectedLanguage] at emission time — see [cacheAndBuildIdentified]).
     */
    private suspend fun resolveCard(cardName: String, lang: String): ResolutionOutcome =
        if (lang == "en") resolveEnglishLadder(cardName) else resolveLocalizedLadder(cardName, lang)

    private suspend fun resolveEnglishLadder(cardName: String): ResolutionOutcome {
        val exact = callExactName(cardName)
        if (exact !is ResolutionOutcome.NotFound) return exact
        return callFuzzyName(cardName)
    }

    private suspend fun resolveLocalizedLadder(cardName: String, lang: String): ResolutionOutcome {
        val printed = callPrintedName(cardName, lang)
        if (printed !is ResolutionOutcome.NotFound) return printed
        return callExactName(cardName)
    }

    private suspend fun callExactName(cardName: String): ResolutionOutcome {
        val result = withContext(ioDispatcher) { cardRepository.getCardByExactName(cardName) }
        result.getOrNull()?.let { return ResolutionOutcome.Found(it) }
        val exception = result.exceptionOrNull()
        if (exception is RateLimitExhaustedException) {
            return ResolutionOutcome.RateLimited(exception.retryAfterMs)
        }
        return ResolutionOutcome.NotFound
    }

    private suspend fun callFuzzyName(cardName: String): ResolutionOutcome =
        withContext(ioDispatcher) { cardRepository.searchCardByName(cardName) }.toOutcome()

    private suspend fun callPrintedName(cardName: String, lang: String): ResolutionOutcome =
        withContext(ioDispatcher) { cardRepository.searchCardPrintedName(cardName, lang) }.toOutcome()

    private fun DataResult<Card>.toOutcome(): ResolutionOutcome = when (this) {
        is DataResult.Success -> ResolutionOutcome.Found(data)
        is DataResult.Error -> {
            val retryAfterMs = RateLimitExhaustedException.retryAfterMsOrNull(message)
            if (retryAfterMs != null) ResolutionOutcome.RateLimited(retryAfterMs) else ResolutionOutcome.NotFound
        }
    }
}
