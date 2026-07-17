package com.mmg.manahub.feature.scanner.presentation

import android.graphics.PointF
import com.mmg.manahub.core.model.Card

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
 * @property isSearching            An OCR lookup is in-flight.
 * @property lastDetectedCard       The most recently confirmed card from Scryfall.
 * @property error                  Transient error message shown in the bottom bar.
 * @property scanSession            Accumulated cards for the current session.
 * @property selectedIsFoil         Current foil toggle in the mode bar.
 * @property selectedLanguage       Current language code in the mode bar (e.g. "en").
 * @property selectedCondition      Current condition code in the mode bar (e.g. "NM").
 * @property selectedQuantity       Current quantity in the mode bar (1–4).
 * @property lockedSetCode          When non-null, only cards of this set are auto-added.
 * @property showQueueSheet         Controls the scan-queue ModalBottomSheet.
 * @property toastMessage           One-shot toast text (cleared after display).
 * @property detectedCorners        Four corner points of the detected card in frame pixel
 *                                  coordinates, or null when no card is in the frame.
 *                                  Updated by [ScannerViewModel.onRecognitionResult] so that
 *                                  the Canvas overlay reads from a single source of truth.
 * @property multiSelectedIds       Set of scryfallId values selected in the queue sheet.
 * @property isSoundEnabled         Whether sound effects play on successful card add.
 * @property showAmbiguitySelector  True when a card was identified as ambiguous in normal mode;
 *                                  shows an inline [DropdownMenu] to confirm or skip.
 * @property showPriceDetailSheet   True when the price detail [ModalBottomSheet] is open
 *                                  (only reachable in Lookup Only mode).
 * @property hasFlash               True when the current camera hardware has a flash unit.
 *                                  Populated asynchronously after the camera binds; defaults to
 *                                  true so the flash button is visible until confirmed otherwise.
 * @property isRecognitionPausedByUser  True when the user explicitly paused recognition via the
 *                                  top-bar toggle, independent of any sheet-driven pause (queue,
 *                                  settings, edit, variant selector, expanded image).
 * @property isAutoDeleteOnAddEnabled  True when a per-entry "Add to collection"/"Add to wishlist"
 *                                  action in [ScanQueueSheet] should also remove that entry from
 *                                  the queue once the add succeeds.
 * @property ownedCardIdentityKeys  Live set of identity keys (`oracleId.ifBlank { name }`) already
 *                                  present in the user's collection — feeds the "already in
 *                                  collection" badge in [QueueCardItem]. Kept up to date by a
 *                                  [ScannerViewModel] collector on `UserCardRepository.observeCollection()`.
 */
data class ScannerUiState(
    val isFlashOn: Boolean = false,
    val hasFlash: Boolean = true,
    val isRecognitionPausedByUser: Boolean = false,
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
    // Sheet visibility
    val showQueueSheet: Boolean = false,
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
    // Auto-delete a queue entry once it's individually added to collection/wishlist
    val isAutoDeleteOnAddEnabled: Boolean = false,
    // "Already in collection" badge — live identity-key set, see KDoc above
    val ownedCardIdentityKeys: Set<String> = emptySet(),
    // Language mismatch indicator (Quick Mode only)
    val languageMismatch: Boolean = false,
    // Ambiguity resolution (normal mode only)
    val showAmbiguitySelector: Boolean = false,
    // Card Detail overlay (Phase 2 scanner UX, 2026-07-17)
    val selectedCardDetailId: String? = null,
    val returnToQueueOnDetailClose: Boolean = false,
    // Rolling FPS counter — only populated in DEBUG builds, always 0 in release
    val fps: Int = 0,

    // Variant selector sheet
    val showVariantSelector: Boolean = false,
    val variantSelectorEntry: ScannedCard? = null,
    val cardVariants: List<Card> = emptyList(),
    val isLoadingVariants: Boolean = false,
    // Full-screen image viewer
    val expandedVariantImageUrl: String? = null,

    // COMMENTED OUT — embedding DB fields no longer needed with ML Kit OCR pipeline
    // val embeddingDbVersionReady: Boolean = false,
    // val embeddingDbVersion: Int = 0,
    // val isEmbeddingDbUpdating: Boolean = false,
    // val embeddingDbDownloadProgress: Float = 0f,
    // val embeddingDbLoaded: Boolean = false,
    // val embeddingDbCardCount: Int = 0,
)
