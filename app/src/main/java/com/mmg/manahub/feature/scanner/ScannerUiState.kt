package com.mmg.manahub.feature.scanner

import android.graphics.PointF
import com.mmg.manahub.core.domain.model.Card

// ─────────────────────────────────────────────────────────────────────────────
//  Session models
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single card entry inside a scan session, capturing all collection parameters
 * chosen by the user at the moment of scanning.
 */
data class ScannedCard(
    val card: Card,
    val quantity: Int,
    val isFoil: Boolean,
    val language: String,
    val condition: String,
    val setCode: String,
    val timestamp: Long,
)

/**
 * Accumulates all cards scanned in the current session.
 * Duplicate entries (same scryfallId + isFoil + language + condition) are
 * merged by incrementing [ScannedCard.quantity] rather than creating a new row.
 */
data class ScanSession(
    val cards: List<ScannedCard> = emptyList(),
)

// ─────────────────────────────────────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full UI state for the no-modal scanner screen.
 *
 * @property isFlashOn              Whether the camera torch is on.
 * @property isSearching            An embedding lookup is in-flight.
 * @property lastDetectedCard       The most recently confirmed card from Scryfall.
 * @property error                  Transient error message shown in the bottom bar.
 * @property scanSession            Accumulated cards for the current session.
 * @property selectedIsFoil         Current foil toggle in the mode bar.
 * @property selectedLanguage       Current language code in the mode bar (e.g. "en").
 * @property selectedCondition      Current condition code in the mode bar (e.g. "NM").
 * @property selectedQuantity       Current quantity in the mode bar (1–4).
 * @property lockedSetCode          When non-null, only cards of this set are auto-added.
 * @property isQuickMode            Auto-adds detected cards without any confirmation UI.
 * @property isLookupOnly           Cards are only shown in the bottom bar, never added.
 * @property showQueueSheet         Controls the scan-queue ModalBottomSheet.
 * @property showSettingsSheet      Controls the settings ModalBottomSheet.
 * @property toastMessage           One-shot toast text (cleared after display).
 * @property detectedCorners        Four corner points of the detected card in frame pixel
 *                                  coordinates, or null when no card is in the frame.
 *                                  Updated by [ScannerViewModel.onRecognitionResult] so that
 *                                  the Canvas overlay reads from a single source of truth.
 * @property multiSelectedIds       Set of scryfallId values selected in the queue sheet.
 * @property isSoundEnabled         Whether sound effects play on successful card add.
 * @property languageMismatch       True when the scanned card's language differs from
 *                                  [selectedLanguage] and [isQuickMode] is on; card is shown
 *                                  but not added automatically.
 * @property showAmbiguitySelector  True when a card was identified as ambiguous in normal mode;
 *                                  shows an inline [DropdownMenu] to confirm or skip.
 * @property showPriceDetailSheet   True when the price detail [ModalBottomSheet] is open
 *                                  (only reachable in Lookup Only mode).
 * @property hasFlash               True when the current camera hardware has a flash unit.
 *                                  Populated asynchronously after the camera binds; defaults to
 *                                  true so the flash button is visible until confirmed otherwise.
 * @property embeddingDbVersionReady    True once DataStore has emitted the initial persisted version.
 *                                      False during the brief startup window before first emission.
 * @property embeddingDbVersion     Version number of the currently loaded embedding database
 *                                  (0 = never downloaded from R2; ≥1 = full DB available).
 * @property isEmbeddingDbUpdating  True while [EmbeddingDatabaseUpdateWorker] is running.
 * @property embeddingDbDownloadProgress Download progress 0f–1f from [WorkInfo.progress]. 0f when
 *                                       idle or not downloading.
 * @property embeddingDbLoaded      True once the in-memory embedding DB has at least one entry.
 * @property embeddingDbCardCount   Number of card embeddings currently in memory (0 = not loaded).
 */
data class ScannerUiState(
    val isFlashOn: Boolean = false,
    val hasFlash: Boolean = true,
    val isSearching: Boolean = false,
    val lastDetectedCard: Card? = null,
    val error: String? = null,
    val scanSession: ScanSession = ScanSession(),
    // Mode bar state
    val selectedIsFoil: Boolean = false,
    val selectedLanguage: String = "en",
    val selectedCondition: String = "NM",
    val selectedQuantity: Int = 1,
    val lockedSetCode: String? = null,
    // Behaviour modes
    val isQuickMode: Boolean = true,
    val isLookupOnly: Boolean = false,
    // Sheet visibility
    val showQueueSheet: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val showEditSheet: Boolean = false,
    val showPriceDetailSheet: Boolean = false,
    // Edit card
    val editingCard: ScannedCard? = null,
    val availablePrints: List<Card> = emptyList(),
    val isLoadingPrints: Boolean = false,
    // Toast
    val toastMessage: String? = null,
    // Queue multi-select
    val multiSelectedIds: Set<String> = emptySet(),
    // Card outline overlay — populated by CardRecognizer via onRecognitionResult
    val detectedCorners: List<PointF>? = null,
    // Sound
    val isSoundEnabled: Boolean = true,
    // Language mismatch indicator (Quick Mode only)
    val languageMismatch: Boolean = false,
    // Ambiguity resolution (normal mode only)
    val showAmbiguitySelector: Boolean = false,
    // Embedding database update status (sourced from DataStore + WorkManager)
    val embeddingDbVersionReady: Boolean = false,
    val embeddingDbVersion: Int = 0,
    val isEmbeddingDbUpdating: Boolean = false,
    val embeddingDbDownloadProgress: Float = 0f,
    // True once the in-memory embedding DB has at least one entry (loaded from file or assets)
    val embeddingDbLoaded: Boolean = false,
    // Number of card embeddings currently in memory (0 = DB not loaded yet)
    val embeddingDbCardCount: Int = 0,
    // Rolling FPS counter — only populated in DEBUG builds, always 0 in release
    val fps: Int = 0,
)
