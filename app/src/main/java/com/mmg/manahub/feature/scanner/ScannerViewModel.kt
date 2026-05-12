package com.mmg.manahub.feature.scanner

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.usecase.collection.AddCardToCollectionUseCase
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.feature.scanner.ScannerViewModel.Companion.ANTI_DUPLICATE_MS
import com.mmg.manahub.feature.scanner.ScannerViewModel.Companion.HIGH_CONFIDENCE_FRAMES
import com.mmg.manahub.feature.scanner.ScannerViewModel.Companion.PREF_FILE
import com.mmg.manahub.feature.scanner.ScannerViewModel.Companion.PREF_KEY_QUEUE
import com.mmg.manahub.feature.scanner.ScannerViewModel.Companion.STABILITY_FRAMES
import com.mmg.manahub.feature.trades.domain.model.WishlistEntry
import com.mmg.manahub.feature.trades.domain.usecase.AddToWishlistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

// COMMENTED OUT — no longer needed with ML Kit OCR pipeline
// import androidx.lifecycle.asFlow
// import androidx.work.WorkInfo
// import androidx.work.WorkManager
// import com.mmg.manahub.core.data.local.UserPreferencesDataStore

/**
 * ViewModel for the scanner screen.
 *
 * Receives [RecognitionResult] from [CardRecognizer] and applies:
 * - **Stability buffer**: the same scryfallId must appear [STABILITY_FRAMES] consecutive
 *   times before the card is treated as confirmed.
 * - **Anti-duplicate guard**: once a card is added to the session, the same card is
 *   ignored for [ANTI_DUPLICATE_MS] milliseconds to avoid re-adding when the user
 *   hasn't moved the camera.
 * - **Set lock filter**: when [ScannerUiState.lockedSetCode] is non-null, only cards
 *   whose [Card.setCode] matches are processed; others are silently ignored.
 * - **Language mismatch**: when [ScannerUiState.selectedLanguage] is not "en" and the
 *   scanned card's [Card.lang] differs, the card is displayed but not auto-added in
 *   Quick Mode, and a [ScannerUiState.languageMismatch] indicator is shown.
 * - **Ambiguity selector**: when the recognition result is ambiguous and the scanner is
 *   in normal mode (not Quick, not Lookup Only), a dialog is shown to let the user confirm.
 *
 * **Queue persistence**: the [ScanSession] is serialized to [SharedPreferences] on every
 * mutation, using [PREF_KEY_QUEUE] inside the [PREF_FILE] preferences file.
 * On ViewModel init, any persisted queue is restored automatically.
 *
 * Modes (controlled by the settings sheet):
 * - **Quick Mode ON**:  auto-adds the confirmed card to the session.
 * - **Lookup Only ON**: only shows the card in the bottom bar, never adds it.
 * - **Neither**:        shows the card in the bottom bar; user taps "+" to add.
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val cardRepository: CardRepository,
    private val addToCollection: AddCardToCollectionUseCase,
    private val addToWishlist: AddToWishlistUseCase,
    private val analyticsHelper: AnalyticsHelper,
    private val soundManager: SoundManager,
    @ApplicationContext private val context: Context,
    // COMMENTED OUT — embedding DB and WorkManager no longer injected with OCR pipeline
    // val embeddingDatabase: EmbeddingDatabase,
    // private val userPreferencesDataStore: UserPreferencesDataStore,
    // private val workManager: WorkManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    // ── Stability buffer ─────────────────────────────────────────────────────
    private val recentMatches = ArrayDeque<String>(STABILITY_FRAMES)

    // ── Rolling FPS counter (DEBUG only) ─────────────────────────────────────
    private val frameTimestamps = ArrayDeque<Long>(11)

    // ── Anti-duplicate guard ─────────────────────────────────────────────────
    private var lastAddedId: String? = null
    private var lastAddedTime: Long = 0L

    // ── SharedPreferences for queue persistence ───────────────────────────────
    private val prefs by lazy {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    }

    companion object {
        /**
         * Number of consecutive identical matches required before a card is confirmed.
         */
        private const val STABILITY_FRAMES = 3

        /**
         * Reduced stability requirement for high-confidence matches.
         * Since OCR + exact-name lookup always returns similarity = 1.0f,
         * [HIGH_CONFIDENCE_FRAMES] = 1 is always used.
         */
        private const val HIGH_CONFIDENCE_FRAMES = 1

        /** Minimum time in ms before the same card can be added again. */
        private const val ANTI_DUPLICATE_MS = 800L

        /** Default language code — used to determine when the language filter is active. */
        private const val DEFAULT_LANGUAGE = "en"

        /** SharedPreferences file name for scanner settings. */
        private const val PREF_FILE = "scanner_prefs"

        /** Key storing the serialized scan queue JSON. */
        private const val PREF_KEY_QUEUE = "scanner_queue_v1"
    }

    init {
        loadPersistedQueue()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Queue persistence — SharedPreferences + org.json
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Serializes the current [ScanSession] to JSON and saves it to [SharedPreferences].
     * Must be called after any mutation that modifies [ScannerUiState.scanSession].
     */
    private fun persistQueue() {
        val cards = _uiState.value.scanSession.cards
        val array = JSONArray()
        for (entry in cards) {
            val obj = JSONObject().apply {
                put("scryfallId",       entry.card.scryfallId)
                put("name",             entry.card.name)
                put("setCode",          entry.card.setCode)
                put("setName",          entry.card.setName)
                put("lang",             entry.card.lang)
                put("priceUsd",         entry.card.priceUsd ?: JSONObject.NULL)
                put("priceEur",         entry.card.priceEur ?: JSONObject.NULL)
                put("imageNormal",      entry.card.imageNormal ?: JSONObject.NULL)
                put("imageArtCrop",     entry.card.imageArtCrop ?: JSONObject.NULL)
                put("collectorNumber",  entry.card.collectorNumber)
                put("quantity",         entry.quantity)
                put("isFoil",           entry.isFoil)
                put("language",         entry.language)
                put("condition",        entry.condition)
                put("timestamp",        entry.timestamp)
            }
            array.put(obj)
        }
        prefs.edit().putString(PREF_KEY_QUEUE, array.toString()).apply()
    }

    /**
     * Loads any previously persisted [ScanSession] from [SharedPreferences] and
     * updates [uiState] with the restored cards. Called once in [init].
     */
    private fun loadPersistedQueue() {
        val json = prefs.getString(PREF_KEY_QUEUE, null) ?: return
        try {
            val array = JSONArray(json)
            val cards = mutableListOf<ScannedCard>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val card = Card(
                    scryfallId       = obj.getString("scryfallId"),
                    name             = obj.getString("name"),
                    printedName      = null,
                    manaCost         = null,
                    cmc              = 0.0,
                    colors           = emptyList(),
                    colorIdentity    = emptyList(),
                    typeLine         = "",
                    printedTypeLine  = null,
                    oracleText       = null,
                    printedText      = null,
                    keywords         = emptyList(),
                    power            = null,
                    toughness        = null,
                    loyalty          = null,
                    setCode          = obj.getString("setCode"),
                    setName          = obj.getString("setName"),
                    collectorNumber  = obj.getString("collectorNumber"),
                    rarity           = "",
                    releasedAt       = "",
                    frameEffects     = emptyList(),
                    promoTypes       = emptyList(),
                    lang             = obj.getString("lang"),
                    imageNormal      = obj.optString("imageNormal").takeIf { it.isNotEmpty() },
                    imageArtCrop     = obj.optString("imageArtCrop").takeIf { it.isNotEmpty() },
                    imageBackNormal  = null,
                    priceUsd         = if (obj.isNull("priceUsd")) null else obj.getDouble("priceUsd"),
                    priceUsdFoil     = null,
                    priceEur         = if (obj.isNull("priceEur")) null else obj.getDouble("priceEur"),
                    priceEurFoil     = null,
                    legalityStandard  = "",
                    legalityPioneer   = "",
                    legalityModern    = "",
                    legalityCommander = "",
                    flavorText        = null,
                    artist            = null,
                    scryfallUri       = "",
                )
                cards.add(
                    ScannedCard(
                        card      = card,
                        quantity  = obj.getInt("quantity"),
                        isFoil    = obj.getBoolean("isFoil"),
                        language  = obj.getString("language"),
                        condition = obj.getString("condition"),
                        setCode   = obj.getString("setCode"),
                        timestamp = obj.getLong("timestamp"),
                    )
                )
            }
            if (cards.isNotEmpty()) {
                _uiState.update { it.copy(scanSession = ScanSession(cards)) }
            }
        } catch (e: Exception) {
            android.util.Log.w("ScannerViewModel", "Failed to restore persisted queue", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ML recognition entry point — called by CardRecognizer
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Processes a [RecognitionResult] from [CardRecognizer].
     *
     * - [RecognitionResult.NoCard]     → clears the overlay and search indicator.
     * - [RecognitionResult.Detected]   → shows searching indicator but keeps going.
     * - [RecognitionResult.Identified] → applies set lock, language mismatch,
     *   stability + anti-duplicate logic, then routes to the appropriate mode.
     */
    fun onRecognitionResult(result: RecognitionResult) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
            if (com.mmg.manahub.BuildConfig.DEBUG) {
                val now = System.currentTimeMillis()
                frameTimestamps.addLast(now)
                if (frameTimestamps.size > 10) frameTimestamps.removeFirst()
                val fps = if (frameTimestamps.size >= 2) {
                    val span = frameTimestamps.last() - frameTimestamps.first()
                    if (span > 0) ((frameTimestamps.size - 1) * 1000L / span).toInt() else 0
                } else 0
                android.util.Log.d("ScannerFPS", "fps=$fps")
            }
            processRecognitionResult(result)
        }
    }

    private fun processRecognitionResult(result: RecognitionResult) {
        when (result) {
            RecognitionResult.NoCard -> {
                recentMatches.clear()
                _uiState.update {
                    it.copy(
                        detectedCorners = null,
                        isSearching = false,
                        languageMismatch = false,
                    )
                }
            }

            is RecognitionResult.Detected -> {
                recentMatches.clear()
                _uiState.update {
                    it.copy(
                        detectedCorners = result.corners.ifEmpty { null },
                        isSearching = true,
                        languageMismatch = false,
                    )
                }
            }

            is RecognitionResult.Identified -> {
                _uiState.update {
                    it.copy(detectedCorners = result.corners.ifEmpty { null })
                }

                val state = _uiState.value

                // ── Set lock filter (applied before stability) ───────────────
                if (state.lockedSetCode != null &&
                    result.card.setCode.lowercase() != state.lockedSetCode.lowercase()
                ) {
                    recentMatches.clear()
                    return
                }

                val id = result.card.scryfallId

                // ── Anti-duplicate guard ─────────────────────────────────────
                val now = System.currentTimeMillis()
                if (id == lastAddedId && now - lastAddedTime < ANTI_DUPLICATE_MS) {
                    recentMatches.clear()
                    return
                }

                // ── Adaptive stability buffer ────────────────────────────────
                val requiredFrames = HIGH_CONFIDENCE_FRAMES

                if (recentMatches.size >= requiredFrames) recentMatches.removeFirst()
                recentMatches.addLast(id)

                if (recentMatches.size < requiredFrames || recentMatches.any { it != id }) {
                    _uiState.update { it.copy(isSearching = true) }
                    return
                }

                // ── Card confirmed — reset buffer ────────────────────────────
                recentMatches.clear()
                _uiState.update { it.copy(isSearching = false, lastDetectedCard = result.card) }

                val confirmedState = _uiState.value

                // Skip adding if already in session with same attributes
                val isInSession = confirmedState.scanSession.cards.any { entry ->
                    entry.card.scryfallId == result.card.scryfallId &&
                            entry.isFoil == confirmedState.selectedIsFoil &&
                            entry.language == confirmedState.selectedLanguage &&
                            entry.condition == confirmedState.selectedCondition
                }

                if (isInSession) {
                    _uiState.update { it.copy(languageMismatch = false) }
                    return
                }

                when {
                    confirmedState.isLookupOnly -> {
                        _uiState.update { it.copy(languageMismatch = false) }
                    }

                    confirmedState.isQuickMode -> {
                        if (confirmedState.selectedLanguage != DEFAULT_LANGUAGE &&
                            result.card.lang != confirmedState.selectedLanguage
                        ) {
                            _uiState.update { it.copy(languageMismatch = true) }
                            return
                        }
                        _uiState.update { it.copy(languageMismatch = false) }
                        quickAddCard(result.card)
                    }

                    result.ambiguous -> {
                        _uiState.update {
                            it.copy(
                                showAmbiguitySelector = true,
                                languageMismatch = false,
                            )
                        }
                    }

                    else -> {
                        _uiState.update { it.copy(languageMismatch = false) }
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Quick add — auto-adds a confirmed card (Quick Mode)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Adds [card] to the scan session (queue only — no automatic collection write).
     * Updates [lastAddedId] and [lastAddedTime] to activate the anti-duplicate guard.
     * Used both by Quick Mode (automatic) and [onManualAddCurrentCard] (manual confirmation).
     */
    private fun quickAddCard(card: Card) {
        addToSession(card)
        lastAddedId = card.scryfallId
        lastAddedTime = System.currentTimeMillis()

        _uiState.update { it.copy(toastMessage = card.name) }

        if (_uiState.value.isSoundEnabled) {
            soundManager.playForPrice(
                priceEur = card.priceEur,
                priceUsdFallback = card.priceUsd,
            )
        }
    }

    /**
     * Merges [card] into the current [ScanSession] and persists the updated queue.
     * Increments quantity if an entry with the same key (scryfallId + isFoil + language + condition)
     * already exists; otherwise appends a new [ScannedCard].
     */
    private fun addToSession(card: Card) {
        _uiState.update { state ->
            val existingIndex = state.scanSession.cards.indexOfFirst { entry ->
                entry.card.scryfallId == card.scryfallId &&
                    entry.isFoil == state.selectedIsFoil &&
                    entry.language == state.selectedLanguage &&
                    entry.condition == state.selectedCondition
            }
            val updatedCards = if (existingIndex >= 0) {
                state.scanSession.cards.toMutableList().also {
                    it[existingIndex] = it[existingIndex].copy(
                        quantity = it[existingIndex].quantity + state.selectedQuantity,
                    )
                }
            } else {
                state.scanSession.cards + ScannedCard(
                    card = card,
                    quantity = state.selectedQuantity,
                    isFoil = state.selectedIsFoil,
                    language = state.selectedLanguage,
                    condition = state.selectedCondition,
                    setCode = card.setCode,
                    timestamp = System.currentTimeMillis(),
                )
            }
            state.copy(scanSession = state.scanSession.copy(cards = updatedCards))
        }
        persistQueue()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Manual add (non-quick mode): user taps the confirm button in the bottom bar
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called when the user explicitly confirms adding the currently displayed card.
     * Reuses [quickAddCard] logic.
     */
    fun onManualAddCurrentCard() {
        val card = _uiState.value.lastDetectedCard ?: return
        quickAddCard(card)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Individual Actions (Collection & Wishlist)
    // ─────────────────────────────────────────────────────────────────────────

    /** Adds a single queue entry to the user's collection. */
    fun onAddEntryToCollection(entry: ScannedCard) {
        viewModelScope.launch {
            addToCollection(
                scryfallId = entry.card.scryfallId,
                isFoil = entry.isFoil,
                isAlternativeArt = false, // Scanner doesn't track alt art
                condition = entry.condition,
                language = entry.language,
                quantity = entry.quantity,
            )
            analyticsHelper.logEvent(
                "scanner_entry_to_collection",
                mapOf("card_id" to entry.card.scryfallId)
            )
            _uiState.update {
                it.copy(toastMessage = context.getString(R.string.scanner_toast_added_to_collection, entry.card.name))
            }
        }
    }

    /**
     * Adds a single queue entry to the user's local wishlist.
     * No authentication required — wishlist entries are stored locally via Room.
     */
    fun onAddEntryToWishlist(entry: ScannedCard) {
        viewModelScope.launch {
            val wishlistEntry = WishlistEntry(
                id             = UUID.randomUUID().toString(),
                userId         = "",  // local-only; no auth required
                cardId         = entry.card.scryfallId,
                matchAnyVariant = false,
                isFoil         = entry.isFoil,
                condition      = entry.condition.uppercase().trim(),
                language       = entry.language.lowercase().trim(),
                isAltArt       = false,
                createdAt      = System.currentTimeMillis(),
                card           = entry.card,
            )
            addToWishlist(wishlistEntry)
            analyticsHelper.logEvent(
                "scanner_entry_to_wishlist",
                mapOf("card_id" to entry.card.scryfallId)
            )
            _uiState.update {
                it.copy(toastMessage = context.getString(R.string.scanner_toast_added_to_wishlist, entry.card.name))
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Bulk Actions
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Adds all queue entries to the user's local wishlist.
     * No authentication required — entries are stored locally via Room.
     */
    fun onAddAllToWishlist() {
        val cards = _uiState.value.scanSession.cards
        if (cards.isEmpty()) return

        viewModelScope.launch {
            cards.forEach { entry ->
                val wishlistEntry = WishlistEntry(
                    id             = UUID.randomUUID().toString(),
                    userId         = "",  // local-only; no auth required
                    cardId         = entry.card.scryfallId,
                    matchAnyVariant = false,
                    isFoil         = entry.isFoil,
                    condition      = entry.condition.uppercase().trim(),
                    language       = entry.language.lowercase().trim(),
                    isAltArt       = false,
                    createdAt      = System.currentTimeMillis(),
                    card           = entry.card,
                )
                addToWishlist(wishlistEntry)
            }
            analyticsHelper.logEvent(
                "scanner_add_all_wishlist",
                mapOf("count" to cards.size.toString()),
            )
            _uiState.update {
                it.copy(toastMessage = context.getString(R.string.scanner_toast_added_all_to_wishlist, cards.size))
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Common ViewModel Actions
    // ─────────────────────────────────────────────────────────────────────────

    /** Dismisses the ambiguity resolution dialog without adding the card. */
    fun onDismissAmbiguitySelector() {
        _uiState.update {
            it.copy(
                showAmbiguitySelector = false,
                lastDetectedCard = null,
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Price detail sheet (Lookup Only mode)
    // ─────────────────────────────────────────────────────────────────────────

    /** Opens the price detail [ModalBottomSheet] for the currently detected card. */
    fun onOpenPriceDetail() {
        _uiState.update { it.copy(showPriceDetailSheet = true) }
    }

    /** Closes the price detail [ModalBottomSheet]. */
    fun onClosePriceDetail() {
        _uiState.update { it.copy(showPriceDetailSheet = false) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Sound toggle
    // ─────────────────────────────────────────────────────────────────────────

    /** Toggles sound effects on or off. */
    fun onToggleSound() {
        _uiState.update { it.copy(isSoundEnabled = !it.isSoundEnabled) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Mode bar actions
    // ─────────────────────────────────────────────────────────────────────────

    /** Toggles the foil chip in the mode bar. */
    fun onToggleFoil() {
        _uiState.update { it.copy(selectedIsFoil = !it.selectedIsFoil) }
    }

    /** Updates the selected language code. */
    fun onLanguageSelected(language: String) {
        _uiState.update { it.copy(selectedLanguage = language, languageMismatch = false) }
    }

    /** Updates the selected condition code. */
    fun onConditionSelected(condition: String) {
        _uiState.update { it.copy(selectedCondition = condition) }
    }

    /** Updates the selected quantity. */
    fun onQuantitySelected(quantity: Int) {
        _uiState.update { it.copy(selectedQuantity = quantity) }
    }

    /** Sets or clears the locked set filter. */
    fun onSetLockSelected(setCode: String?) {
        _uiState.update { it.copy(lockedSetCode = setCode) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Queue actions & filtering
    // ─────────────────────────────────────────────────────────────────────────

    /** Updates the search query for filtering the scan queue. */
    fun onQueueSearchQueryChanged(query: String) {
        // Filtering is handled in the UI layer (ScanQueueSheet) for performance;
        // the query is stored here for future ViewModel-side filtering if needed.
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Editing scanned cards
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Opens the edit sheet for a specific scanned card.
     * Fetches all available prints (sets) for that card to populate the set picker.
     */
    fun onEditScannedCard(entry: ScannedCard) {
        _uiState.update {
            it.copy(
                editingCard = entry,
                showEditSheet = true,
                availablePrints = emptyList(),
                isLoadingPrints = true,
            )
        }

        viewModelScope.launch {
            val result = cardRepository.getCardPrints(entry.card.name)
            if (result is DataResult.Success) {
                _uiState.update {
                    it.copy(
                        availablePrints = result.data,
                        isLoadingPrints = false,
                    )
                }
            } else {
                _uiState.update { it.copy(isLoadingPrints = false) }
            }
        }
    }

    /**
     * Updates an existing entry in the scan session with new attributes,
     * then persists the updated queue to SharedPreferences.
     */
    fun onUpdateScannedCard(updatedEntry: ScannedCard) {
        val original = _uiState.value.editingCard ?: return
        _uiState.update { state ->
            val updatedList = state.scanSession.cards.map {
                if (it === original) updatedEntry else it
            }
            state.copy(
                scanSession = state.scanSession.copy(cards = updatedList),
                showEditSheet = false,
                editingCard = null,
            )
        }
        persistQueue()
    }

    /** Closes the edit sheet without saving. */
    fun onCloseEditSheet() {
        _uiState.update { it.copy(showEditSheet = false, editingCard = null) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Flash
    // ─────────────────────────────────────────────────────────────────────────

    /** Toggles the camera torch. */
    fun onToggleFlash() {
        _uiState.update { it.copy(isFlashOn = !it.isFlashOn) }
    }

    /**
     * Updates [ScannerUiState.hasFlash] after the camera binds and hardware availability
     * is confirmed via [androidx.camera.core.CameraInfo.hasFlashUnit].
     */
    fun onFlashAvailabilityChanged(available: Boolean) {
        _uiState.update { it.copy(hasFlash = available) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Settings bottom sheet
    // ─────────────────────────────────────────────────────────────────────────

    /** Opens the settings bottom sheet. */
    fun onOpenSettings() {
        _uiState.update { it.copy(showSettingsSheet = true) }
    }

    /** Closes the settings bottom sheet. */
    fun onCloseSettings() {
        _uiState.update { it.copy(showSettingsSheet = false) }
    }

    /** Toggles Quick Mode (auto-add on detect). */
    fun onToggleQuickMode() {
        _uiState.update { it.copy(isQuickMode = !it.isQuickMode, languageMismatch = false) }
    }

    /** Toggles Lookup Only mode (show price, never add). */
    fun onToggleLookupOnly() {
        _uiState.update { it.copy(isLookupOnly = !it.isLookupOnly) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Queue bottom sheet
    // ─────────────────────────────────────────────────────────────────────────

    /** Opens the scan-queue bottom sheet. */
    fun onOpenQueue() {
        _uiState.update { it.copy(showQueueSheet = true, multiSelectedIds = emptySet()) }
    }

    /** Closes the scan-queue bottom sheet. */
    fun onCloseQueue() {
        _uiState.update { it.copy(showQueueSheet = false, multiSelectedIds = emptySet()) }
    }

    /** Removes a single [ScannedCard] from the session and persists the change. */
    fun onRemoveSessionCard(entry: ScannedCard) {
        _uiState.update { state ->
            state.copy(
                scanSession = state.scanSession.copy(
                    cards = state.scanSession.cards.filter { it !== entry },
                ),
                multiSelectedIds = state.multiSelectedIds - entry.card.scryfallId,
            )
        }
        persistQueue()
    }

    /** Clears the entire scan session, resets the anti-duplicate guard, and persists. */
    fun onClearSession() {
        _uiState.update {
            it.copy(
                scanSession = ScanSession(),
                multiSelectedIds = emptySet(),
                showQueueSheet = false,
            )
        }
        lastAddedId = null
        lastAddedTime = 0L
        persistQueue()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Multi-select in the queue sheet
    // ─────────────────────────────────────────────────────────────────────────

    /** Toggles the long-press selection state of a session entry. */
    fun onToggleMultiSelect(entry: ScannedCard) {
        _uiState.update { state ->
            val id = entry.card.scryfallId
            val updated = if (id in state.multiSelectedIds) {
                state.multiSelectedIds - id
            } else {
                state.multiSelectedIds + id
            }
            state.copy(multiSelectedIds = updated)
        }
    }

    /** Deletes all currently selected entries from the session and persists the change. */
    fun onDeleteSelected() {
        _uiState.update { state ->
            state.copy(
                scanSession = state.scanSession.copy(
                    cards = state.scanSession.cards.filter {
                        it.card.scryfallId !in state.multiSelectedIds
                    },
                ),
                multiSelectedIds = emptySet(),
            )
        }
        persistQueue()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Add all from queue to collection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Persists every [ScannedCard] in the session to the collection,
     * then clears the queue and closes the sheet.
     */
    fun onAddAllToCollection() {
        val cards = _uiState.value.scanSession.cards
        if (cards.isEmpty()) return

        viewModelScope.launch {
            cards.forEach { entry ->
                repeat(entry.quantity) {
                    addToCollection(
                        scryfallId = entry.card.scryfallId,
                        isFoil = entry.isFoil,
                        condition = entry.condition,
                        language = entry.language,
                    )
                }
            }
            analyticsHelper.logEvent(
                "scanner_add_all",
                mapOf("count" to cards.size.toString()),
            )
            _uiState.update {
                it.copy(toastMessage = context.getString(R.string.scanner_toast_added_all_to_collection, cards.size))
            }
            onClearSession()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Toast dismissal
    // ─────────────────────────────────────────────────────────────────────────

    /** Clears the one-shot toast message after it has been displayed. */
    fun onToastDismissed() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Cleanup
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        soundManager.release()
    }
}
