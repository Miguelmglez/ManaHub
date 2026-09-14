package com.mmg.manahub.feature.multiadd.presentation

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.collection.CardCommit
import com.mmg.manahub.core.domain.usecase.collection.CommitScannedCardsUseCase
import com.mmg.manahub.core.domain.usecase.search.BuildScryfallQueryUseCase
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardSelectionEntry
import com.mmg.manahub.core.model.CardSelectionSession
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.core.model.WishlistEntry
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.feature.multiadd.presentation.MultiAddCardViewModel.Companion.HIGH_CONFIDENCE_FRAMES
import com.mmg.manahub.feature.trades.domain.usecase.AddToWishlistUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MultiAddCardViewModel(
    private val searchCards: SearchCardsUseCase,
    private val buildScryfallQuery: BuildScryfallQueryUseCase,
    private val cardRepository: CardRepository,
    private val userCardRepository: UserCardRepository,
    private val commitScannedCards: CommitScannedCardsUseCase,
    private val addToWishlist: AddToWishlistUseCase,
    private val analyticsHelper: AnalyticsHelper,
    private val userPreferences:    UserPreferencesRepository,
    @ApplicationContext private val context: Context,
    ) : ViewModel() {
    private var _uiState = MutableStateFlow<MultiAddCardUiState>(MultiAddCardUiState())
    val uiState: StateFlow<MultiAddCardUiState> = _uiState.asStateFlow()

    private val textQueryFlow  = MutableStateFlow("")
    private val activeQueryFlow = MutableStateFlow<AdvancedSearchQuery?>(null)

    private val forceSearchTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var lastEffectiveQuery: String? = null
    private var variantLoadJob: kotlinx.coroutines.Job? = null

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

        /** SharedPreferences file name for scanner settings. */
        private const val PREF_FILE = "scanner_prefs"

        /** Key storing the serialized scan queue JSON. */
        private const val PREF_KEY_QUEUE = "scanner_queue_v1"
    }

    init {
        loadPersistedQueue()
        observeOwnedCardIdentityKeys()
    }
    fun preLoadItems(cards: List<Card>) {
        _uiState.update { it.copy(hasPreloadedItems = true, loadedCards = cards, isLoading = false) }
    }

    private fun observeOwnedCardIdentityKeys() {
        viewModelScope.launch {
            userCardRepository.observeCollection().collect { rows ->
                val keys = rows.mapTo(mutableSetOf()) { it.card.oracleId.ifBlank { it.card.name } }
                _uiState.update { it.copy(ownedCardIdentityKeys = keys) }
            }
        }
    }
    /**
     * Loads any previously persisted [ScanSession] from [SharedPreferences] and
     * updates [uiState] with the restored cards. Called once in [init].
     */
    private fun loadPersistedQueue() {
        val json = prefs.getString(PREF_KEY_QUEUE, null) ?: return
        try {
            val array = JSONArray(json)
            val entries = mutableListOf<CardSelectionEntry>()
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
                    priceUsdFoil     = if (obj.isNull("priceUsdFoil")) null else obj.optDouble("priceUsdFoil").takeIf { !it.isNaN() },
                    priceEur         = if (obj.isNull("priceEur")) null else obj.getDouble("priceEur"),
                    priceEurFoil     = if (obj.isNull("priceEurFoil")) null else obj.optDouble("priceEurFoil").takeIf { !it.isNaN() },
                    legalityStandard  = "",
                    legalityPioneer   = "",
                    legalityModern    = "",
                    legalityCommander = "",
                    flavorText        = null,
                    artist            = null,
                    scryfallUri       = "",
                )
                entries.add(
                    CardSelectionEntry(
                        card      = card,
                        quantity  = obj.getInt("quantity"),
                        isFoil    = obj.getBoolean("isFoil"),
                        language  = obj.getString("language"),
                        condition = obj.getString("condition"),
                        setCode   = obj.getString("setCode"),
                        timestamp = obj.getLong("timestamp"),
                        // Backward-compat: a queue persisted before this field existed has no
                        // "id" key -- fall back to a fresh one rather than failing the whole restore.
                        id        = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    )
                )
            }
            if (entries.isNotEmpty()) {
                _uiState.update { it.copy(scanSession = CardSelectionSession(entries)) }
            }
        } catch (e: Exception) {
            // Non-fatal: session data lost but app remains functional.
            // Track to detect schema migration issues after app updates.
            FirebaseCrashlytics.getInstance().apply {
                log("scanner_queue_restore_failed: ${e::class.simpleName}")
                recordException(RuntimeException("[ScannerViewModel] Queue deserialization failed", e))
            }
        }
    }
    private fun persistQueue() {
        val cards = _uiState.value.scanSession.entries
        val array = JSONArray()
        for (entry in cards) {
            val obj = JSONObject().apply {
                put("scryfallId",       entry.card.scryfallId)
                put("name",             entry.card.name)
                put("setCode",          entry.card.setCode)
                put("setName",          entry.card.setName)
                put("lang",             entry.card.lang)
                put("priceUsd",         entry.card.priceUsd ?: JSONObject.NULL)
                put("priceUsdFoil",     entry.card.priceUsdFoil ?: JSONObject.NULL)
                put("priceEur",         entry.card.priceEur ?: JSONObject.NULL)
                put("priceEurFoil",     entry.card.priceEurFoil ?: JSONObject.NULL)
                put("imageNormal",      entry.card.imageNormal ?: JSONObject.NULL)
                put("imageArtCrop",     entry.card.imageArtCrop ?: JSONObject.NULL)
                put("collectorNumber",  entry.card.collectorNumber)
                put("quantity",         entry.quantity)
                put("isFoil",           entry.isFoil)
                put("language",         entry.language)
                put("condition",        entry.condition)
                put("timestamp",        entry.timestamp)
                put("id",               entry.id)
            }
            array.put(obj)
        }
        prefs.edit { putString(PREF_KEY_QUEUE, array.toString()) }
    }

    fun toggleLayout() {
        _uiState.update { it.copy(showAsGrid = !it.showAsGrid) }
    }
    fun showLanguagePicker(show: Boolean) {
        _uiState.update { it.copy(showLanguagePicker = show) }
    }

    fun showCardStatusPicker(show: Boolean) {
        _uiState.update { it.copy(showCardStatusPicker = show) }
    }

    fun showAdvancedSearchSheet(show: Boolean) {
        _uiState.update { it.copy(showAdvancedSearch = show) }
    }

    fun showSelectedQueueSheet(show: Boolean) {
        _uiState.update { it.copy(showSelectedQueueSheet = show)}
    }

    fun forceSearch() {
        forceSearchTrigger.tryEmit(Unit)
    }




    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        textQueryFlow.value = query
    }

    fun onAdvancedQuerySearch(query: AdvancedSearchQuery) {
        _uiState.update { it.copy(activeQuery = query) }
        activeQueryFlow.value = query

        val nameCriterion = query.criteria.filterIsInstance<com.mmg.manahub.core.model.SearchCriterion.Name>().firstOrNull()
        if (nameCriterion != null && nameCriterion.value.isNotBlank()) {
            onQueryChange(nameCriterion.value)
        }
    }

    fun onClearFilters() {
        _uiState.update { it.copy(activeQuery = null) }
        activeQueryFlow.value = null
    }

    fun onClearAll() {
        _uiState.update {
            it.copy(
                query = "",
                activeQuery = null,
                loadedCards = it.firstLoadedCards.ifEmpty {emptyList()},
                isLoading = false,
                isLoadingMore = false,
                hasMore = false,
                currentPage = 1,
                error = null
            )
        }
        textQueryFlow.value = ""
        activeQueryFlow.value = null
        lastEffectiveQuery = null
    }

    fun onLanguageChange(code: String) {
        viewModelScope.launch {
            val lang = when (code) {
                "es"  -> com.mmg.manahub.core.model.CardLanguage.SPANISH
                "de"  -> com.mmg.manahub.core.model.CardLanguage.GERMAN
                "fr"  -> com.mmg.manahub.core.model.CardLanguage.FRENCH
                "it"  -> com.mmg.manahub.core.model.CardLanguage.ITALIAN
                "pt"  -> com.mmg.manahub.core.model.CardLanguage.PORTUGUESE
                "ja"  -> com.mmg.manahub.core.model.CardLanguage.JAPANESE
                "ko"  -> com.mmg.manahub.core.model.CardLanguage.KOREAN
                "ru"  -> com.mmg.manahub.core.model.CardLanguage.RUSSIAN
                "zhs" -> com.mmg.manahub.core.model.CardLanguage.CHINESE_SIMPLIFIED
                "zht" -> com.mmg.manahub.core.model.CardLanguage.CHINESE_TRADITIONAL
                else  -> com.mmg.manahub.core.model.CardLanguage.ENGLISH
            }
            userPreferences.setCardLanguage(lang)
        }
    }
    private fun quickAddCard(card: Card) {
        addToSession(card)
        _uiState.update { it.copy(toastMessage = card.name, toastType = MagicToastType.SUCCESS) }

    }

    private fun addToSession(card: Card) {
        _uiState.update { state ->
            val existingIndex = state.scanSession.entries.indexOfFirst { entry ->
                entry.card.scryfallId == card.scryfallId &&
                    entry.isFoil == state.selectedIsFoil &&
                    entry.language == card.lang &&
                    entry.condition == state.selectedCondition
            }
            val updatedEntries = if (existingIndex >= 0) {
                state.scanSession.entries.toMutableList().also {
                    it[existingIndex] = it[existingIndex].copy(
                        quantity = it[existingIndex].quantity + state.selectedQuantity,
                    )
                }
            } else {
                state.scanSession.entries + CardSelectionEntry(
                    card = card,
                    quantity = state.selectedQuantity,
                    isFoil = state.selectedIsFoil,
                    language = card.lang,
                    condition = state.selectedCondition,
                    setCode = card.setCode,
                    timestamp = System.currentTimeMillis(),
                    id = UUID.randomUUID().toString(),
                )
            }
            state.copy(scanSession = state.scanSession.copy(entries = updatedEntries))
        }
        persistQueue()
    }
    fun loadNextPage() {
        val currentState = _uiState.value
        val query = buildScryfallQuery(createQueryFromSelectedSet())

        if (!currentState.hasMore || currentState.isLoadingMore || currentState.processingData) return
        val nextPage = currentState.currentPage + 1
        _uiState.update { it.copy(isLoadingMore = true) }

        viewModelScope.launch {
            when (val result = searchCards(query, nextPage)) {
                is DataResult.Success -> {}

                is DataResult.Error -> {
                    _uiState.update {
                        it.copy(isLoadingMore = false, error = result.message)
                    }
                }
            }
        }
    }

    private fun createQueryFromSelectedSet(): AdvancedSearchQuery {
        val criterionList: List<SearchCriterion> = listOf(SearchCriterion.CardSet(setOf(_uiState.value.selectedSet!!.code)))
        return AdvancedSearchQuery(criteria = criterionList)
    }

    /** Closes the scan-queue bottom sheet. */
    fun onCloseQueue() {
        _uiState.update { it.copy(showSelectedQueueSheet = false) }
    }



    /** Clears the entire scan session, resets the anti-duplicate guard, and persists. */
    fun onClearSession() {
        _uiState.update {
            it.copy(
                scanSession = CardSelectionSession(),
                showSelectedQueueSheet = false,
            )
        }
        persistQueue()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Editing scanned cards
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Opens the edit sheet for a specific scanned card.
     * Fetches all available prints (sets) for that card to populate the set picker.
     */
    fun onEditScannedCard(entry: CardSelectionEntry) {


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

    fun onAddAllToCollection() {
        val state = _uiState.value
        val entries = state.scanSession.entries
        if (entries.isEmpty() || state.isCommittingQueue) return

        _uiState.update { it.copy(isCommittingQueue = true) }
        viewModelScope.launch {
            try {
                val result = commitScannedCards(entries.map { it.toCommit() })

                analyticsHelper.logEvent(
                    "scanner_add_all",
                    mapOf("count" to entries.size.toString(), "failed" to result.failedEntries.toString()),
                )

                if (result.failedEntries == 0) {
                    _uiState.update {
                        it.copy(
                            toastMessage = context.getString(R.string.scanner_toast_added_all_to_collection, entries.size),
                            toastType = MagicToastType.SUCCESS,
                        )
                    }
                    onClearSession()
                } else {
                    // Stable id, not timestamp: two queue entries can share a millisecond (burst
                    // recognition, or a duplicate-entry action firing twice), which would silently
                    // drop the failed one from this filter alongside the succeeded one.
                    val succeededIds = entries.filterIndexed { index, _ ->
                        result.entrySucceeded.getOrElse(index) { false }
                    }.mapTo(mutableSetOf()) { it.id }
                    _uiState.update { s ->
                        s.copy(
                            scanSession = s.scanSession.copy(
                                entries = s.scanSession.entries.filterNot { it.id in succeededIds },
                            ),
                            toastMessage = context.getString(
                                R.string.scanner_toast_add_all_partial_failure,
                                result.failedEntries,
                                entries.size,
                            ),
                            toastType = MagicToastType.WARNING,
                        )
                    }
                    persistQueue()
                }
            } finally {
                _uiState.update { it.copy(isCommittingQueue = false) }
            }
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
    //  Card Detail overlay
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Opens the detail overlay for [id].
     * @param fromQueue If true, closes the queue sheet first and flags it for restoration on close.
     */
    fun onOpenCardDetail(id: String, fromQueue: Boolean = false) {
        _uiState.update {
            it.clearedForOverlay().copy(
                selectedCardDetailId = id,
                showSelectedQueueSheet = if (fromQueue) false else it.showSelectedQueueSheet,
                returnToQueueOnDetailClose = fromQueue
            )
        }
    }

    /**
     * Closes the detail overlay. If it was opened from the queue, re-opens the queue sheet.
     */
    fun onCloseCardDetail() {
        _uiState.update {
            it.copy(
                selectedCardDetailId = null,
                showSelectedQueueSheet = if (it.returnToQueueOnDetailClose) true else it.showSelectedQueueSheet,
                returnToQueueOnDetailClose = false
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Variant selector
    // ─────────────────────────────────────────────────────────────────────────

    fun onOpenVariantSelector(entry: CardSelectionEntry) {
        variantLoadJob?.cancel()
        _uiState.update {
            it.clearedForOverlay().copy(
                showVariantSelector = true,
                variantSelectorEntry = entry,
                cardVariants = emptyList(),
                isLoadingVariants = true,
            )
        }
        variantLoadJob = viewModelScope.launch {
            val result = cardRepository.getCardArtVariants(entry.card.name)
            _uiState.update { state ->
                if (!state.showVariantSelector) return@update state
                if (result is DataResult.Success)
                    state.copy(cardVariants = result.data, isLoadingVariants = false)
                else
                    state.copy(isLoadingVariants = false)
            }
        }
    }

    fun onCloseVariantSelector() {
        variantLoadJob?.cancel()
        _uiState.update {
            it.copy(
                showVariantSelector = false,
                variantSelectorEntry = null,
                isLoadingVariants = false,
                cardVariants = emptyList(),
            )
        }
    }

    fun onSelectVariant(variant: Card) {
        val original = _uiState.value.variantSelectorEntry ?: return
        _uiState.update { state ->
            val updatedEntries = state.scanSession.entries.map {
                if (it.id == original.id) it.copy(card = variant, setCode = variant.setCode) else it
            }
            state.copy(
                scanSession = state.scanSession.copy(entries = updatedEntries),
                showVariantSelector = false,
                variantSelectorEntry = null,
                editingCard = if (state.editingCard?.id == original.id) {
                    state.editingCard.copy(card = variant, setCode = variant.setCode)
                } else state.editingCard
            )
        }
        persistQueue()
    }

    fun onExpandVariantImage(imageUrl: String) {
        if (imageUrl.isBlank()) return
        _uiState.update { it.clearedForOverlay().copy(expandedVariantImageUrl = imageUrl) }
    }

    fun onCloseExpandedImage() {
        _uiState.update { it.copy(expandedVariantImageUrl = null) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Duplicate scanned card
    // ─────────────────────────────────────────────────────────────────────────

    fun onIncrementSessionCardQuantity(entry: CardSelectionEntry) {
        _uiState.update { state ->
            val updatedEntries = state.scanSession.entries.map {
                if (it.id == entry.id) it.copy(quantity = it.quantity + 1) else it
            }
            state.copy(scanSession = state.scanSession.copy(entries = updatedEntries))
        }
        persistQueue()
    }

    fun onDecrementSessionCardQuantity(entry: CardSelectionEntry) {
        if (entry.quantity <= 1) {
            onRemoveSessionCard(entry)
            return
        }
        _uiState.update { state ->
            val updatedEntries = state.scanSession.entries.map {
                if (it.id == entry.id) it.copy(quantity = (it.quantity - 1).coerceAtLeast(1)) else it
            }
            state.copy(scanSession = state.scanSession.copy(entries = updatedEntries))
        }
        persistQueue()
    }

    fun onDuplicateSessionCard(original: CardSelectionEntry) {
        // id must also be regenerated -- copy() otherwise carries the original's id, giving two
        // distinct queue entries the same identity.
        val duplicate = original.copy(id = UUID.randomUUID().toString(), timestamp = System.currentTimeMillis())
        _uiState.update { state ->
            val index = state.scanSession.entries.indexOfFirst { it.id == original.id }
            val updatedEntries = if (index >= 0) {
                state.scanSession.entries.toMutableList().apply { add(index + 1, duplicate) }
            } else {
                state.scanSession.entries + duplicate
            }
            state.copy(scanSession = state.scanSession.copy(entries = updatedEntries))
        }
        persistQueue()
    }
    fun onToggleAutoDeleteOnAdd() {
        _uiState.update { it.copy(isAutoDeleteOnAddEnabled = !it.isAutoDeleteOnAddEnabled) }
    }

    /**
     * Adds a single queue entry to the user's local wishlist.
     * No authentication required — wishlist entries are stored locally via Room.
     */
    fun onAddEntryToWishlist(entry: CardSelectionEntry) {
        viewModelScope.launch {
            val wishlistEntry = WishlistEntry(
                id             = UUID.randomUUID().toString(),
                userId         = "",  // local-only; no auth required
                cardId         = entry.card.scryfallId,
                matchAnyVariant = false,
                isFoil         = entry.isFoil,
                condition      = entry.condition.uppercase().trim(),
                language       = entry.language.lowercase().trim(),
                createdAt      = System.currentTimeMillis(),
                card           = entry.card,
            )
            addToWishlist(wishlistEntry)
            analyticsHelper.logEvent(
                "scanner_entry_to_wishlist",
                mapOf("card_id" to entry.card.scryfallId)
            )
            _uiState.update {
                it.copy(
                    toastMessage = context.getString(R.string.scanner_toast_added_to_wishlist, entry.card.name),
                    toastType = MagicToastType.SUCCESS,
                )
            }
            if (_uiState.value.isAutoDeleteOnAddEnabled) {
                onRemoveSessionCard(entry)
            }
        }
    }

    fun isCardInSession(cardId: String):Boolean{
        val confirmedState = _uiState.value
        return confirmedState.scanSession.entries.any { entry ->
            entry.card.scryfallId == cardId
        }
    }

    fun handleCardSelection(selected : Card){
        val confirmedState = _uiState.value
        val isInSession = confirmedState.scanSession.entries.any { entry ->
            entry.card.scryfallId == selected.scryfallId &&
                entry.isFoil == confirmedState.selectedIsFoil &&
                entry.language == selected.lang &&
                entry.condition == confirmedState.selectedCondition
        }

        if (isInSession) {
            onRemoveSessionCardById(selected.scryfallId)
            return
        } else {
            quickAddCard(selected)
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
        val entries = _uiState.value.scanSession.entries
        if (entries.isEmpty()) return

        viewModelScope.launch {
            for (entry in entries) {
                val wishlistEntry = WishlistEntry(
                    id             = UUID.randomUUID().toString(),
                    userId         = "",  // local-only; no auth required
                    cardId         = entry.card.scryfallId,
                    matchAnyVariant = false,
                    isFoil         = entry.isFoil,
                    condition      = entry.condition.uppercase().trim(),
                    language       = entry.language.lowercase().trim(),
                    createdAt      = System.currentTimeMillis(),
                    card           = entry.card,
                )
                addToWishlist(wishlistEntry)
                _uiState.update {
                    it.copy(
                        toastMessage = context.getString(R.string.scanner_toast_added_to_wishlist, entry.card.name),
                        toastType = MagicToastType.SUCCESS,
                    )
                }
                kotlinx.coroutines.delay(100)
            }
            analyticsHelper.logEvent(
                "scanner_add_all_wishlist",
                mapOf("count" to entries.size.toString()),
            )
            _uiState.update {
                it.copy(
                    toastMessage = context.getString(R.string.scanner_toast_added_all_to_wishlist, entries.size),
                    toastType = MagicToastType.SUCCESS,
                )
            }
        }
    }

    /** Adds a single queue entry to the user's collection. */
    fun onAddEntryToCollection(entry: CardSelectionEntry) {
        viewModelScope.launch {
            // Route through the scanner commit use case so this counts as a scan
            // (CardScanned XP) rather than a manual add — and is never double-counted.
            val result = commitScannedCards(listOf(entry.toCommit()))
            analyticsHelper.logEvent(
                "scanner_entry_to_collection",
                mapOf("card_id" to entry.card.scryfallId)
            )
            // Write-path hardening audit (2026-09-06): a failed write must not report success or
            // remove the entry from the queue — the user would lose track of a card that was
            // never actually saved.
            if (result.failedEntries == 0) {
                _uiState.update {
                    it.copy(
                        toastMessage = context.getString(R.string.scanner_toast_added_to_collection, entry.card.name),
                        toastType = MagicToastType.SUCCESS,
                    )
                }
                if (_uiState.value.isAutoDeleteOnAddEnabled) {
                    onRemoveSessionCard(entry)
                }
            } else {
                _uiState.update {
                    it.copy(
                        toastMessage = context.getString(R.string.scanner_toast_add_failed, entry.card.name),
                        toastType = MagicToastType.ERROR,
                    )
                }
            }
        }
    }
    private fun CardSelectionEntry.toCommit(): CardCommit = CardCommit(
        scryfallId = card.scryfallId,
        isFoil     = isFoil,
        condition  = condition,
        language   = language,
        quantity   = quantity,
    )

    fun onRemoveSessionCard(entryId: CardSelectionEntry) {
        _uiState.update { state ->
            state.copy(
                scanSession = state.scanSession.copy(
                    entries = state.scanSession.entries.filter { it.id != entryId.id},
                ),)
        }
        persistQueue()
    }

    fun onRemoveSessionCardById(entryId: String) {
        _uiState.update { state ->
            state.copy(
                scanSession = state.scanSession.copy(
                    entries = state.scanSession.entries.filter { it.id != entryId },
                ),)
        }
        persistQueue()
    }

    private fun MultiAddCardUiState.clearedForOverlay(): MultiAddCardUiState =
        copy( )
}
