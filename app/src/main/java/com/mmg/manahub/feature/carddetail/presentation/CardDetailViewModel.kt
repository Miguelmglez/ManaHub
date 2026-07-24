package com.mmg.manahub.feature.carddetail.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.tagging.label
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.core.model.UserDefinedTag
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UpdateEntryOutcome
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.usecase.card.RefreshCardStrategyTagsUseCase
import com.mmg.manahub.core.domain.usecase.collection.AddCardToCollectionUseCase
import com.mmg.manahub.core.domain.usecase.collection.UpdateCollectionEntryUseCase
import com.mmg.manahub.core.util.CardConstants
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.util.recordSafeNonFatal
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.model.WishlistEntry
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.feature.trades.domain.usecase.AddToWishlistUseCase
import com.mmg.manahub.feature.trades.domain.usecase.UpdateWishlistEntryUseCase
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CardDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val cardRepo: CardRepository,
    private val userCardRepo: UserCardRepository,
    private val deckRepo: DeckRepository,
    private val addToCollection: AddCardToCollectionUseCase,
    private val addToWishlistUseCase: AddToWishlistUseCase,
    private val wishlistRepo: WishlistRepository,
    private val openForTradeRepo: OpenForTradeRepository,
    private val userPrefs: UserPreferencesRepository,
    private val authRepository: AuthRepository,
    private val helper: AnalyticsHelper,
    private val updateCollectionEntry: UpdateCollectionEntryUseCase,
    private val updateWishlistEntry: UpdateWishlistEntryUseCase,
    private val refreshCardStrategyTags: RefreshCardStrategyTagsUseCase,
) : ViewModel() {

    private val initialScryfallId: String = checkNotNull(savedStateHandle["scryfallId"])
    private val scryfallIdFlow = MutableStateFlow(initialScryfallId)
    private val scryfallId: String get() = scryfallIdFlow.value

    // The currently-loaded [Card], mirrored from every point [_uiState.card] is written. Used to
    // derive the oracle-wide identity (oracleId, name) that the Collection/Wishlist/Trade sections
    // now key off (Card Versions & Languages, Phase 1B) — distinct from [scryfallIdFlow], which is
    // the exact printing being displayed.
    private val loadedCardFlow = MutableStateFlow<Card?>(null)

    /**
     * Emits (oracleId, name) whenever the loaded card's ORACLE IDENTITY changes — deliberately
     * `distinctUntilChanged` so a same-oracle field update (price refresh, tag edit) that re-emits
     * [loadedCardFlow] does not tear down and rebuild the downstream Room observers below.
     */
    private val cardIdentityFlow = loadedCardFlow
        .filterNotNull()
        .map { it.oracleId to it.name }
        .distinctUntilChanged()

    private val _uiState = MutableStateFlow(CardDetailUiState())
    val uiState: StateFlow<CardDetailUiState> = _uiState.asStateFlow()

    // One-shot UI events (toasts, navigation, etc.)
    private val _events = MutableSharedFlow<CardDetailEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<CardDetailEvent> = _events.asSharedFlow()

    // Tracks the in-flight variant-load coroutine so it can be cancelled on dismiss.
    private var variantJob: Job? = null

    // Tracks the in-flight language-prints-load coroutine so it can be cancelled on dismiss.
    private var languageJob: Job? = null

    // One-shot guard for the entry-only English-first redirect (see loadCard()) — runs at most
    // once per ViewModel instance, on the FIRST id this VM ever loads. Every subsequent id change
    // (explicit language/variant picks) is a user choice and must never be redirected.
    private var appliedInitialEnglishRedirect = false

    init {
        loadCard()
        observeUserCards()
        observeWishlistEntries()
        observeTradeEntries()
        observeDecks()
        viewModelScope.launch {
            userPrefs.userDefinedTagsFlow.collect { tags ->
                _uiState.update { it.copy(userDefinedTags = tags) }
            }
        }
    }

    // ── Observation ───────────────────────────────────────────────────────────

    private fun observeDecks() {
        viewModelScope.launch {
            scryfallIdFlow.flatMapLatest { id ->
                deckRepo.observeDecksContainingCard(id)
            }.catch { /* decks section is non-critical */ }
                .collect { decks -> _uiState.update { it.copy(decksContainingCard = decks) } }
        }
    }

    private fun loadCard() {
        viewModelScope.launch {
            scryfallIdFlow.collectLatest { id ->
                // Entry-only English-first redirect (2026-07-23): a collection entry saved in a
                // non-English language still shows its ENGLISH artwork in the list thumbnail the
                // user tapped (Collection/Deck Studio images fall back to the English sibling —
                // see feedback_collection_english_sibling_image_fallback), so the shared-element
                // transition into this screen must land on the English printing too, or it
                // visibly flashes into a different-language image for one frame. This block runs
                // AT MOST ONCE per ViewModel instance, on the very first id this VM ever loads —
                // every subsequent id change (onSelectLanguagePrint / onSelectVariant /
                // onSelectFallbackLanguage, i.e. an explicit user pick) must display exactly what
                // was chosen, so the flag is set FIRST, unconditionally, before any suspending
                // work — the non-English card is never painted here, not even for one frame.
                val result: DataResult<Card> = if (!appliedInitialEnglishRedirect) {
                    appliedInitialEnglishRedirect = true
                    val initialResult = cardRepo.getCardById(id)
                    val initialCard = (initialResult as? DataResult.Success)?.data
                    if (initialCard != null && initialCard.lang != "en") {
                        val languageResult = cardRepo.getLanguagePrints(initialCard.setCode, initialCard.collectorNumber)
                        val englishId = (languageResult as? DataResult.Success)?.data
                            ?.firstOrNull { it.lang == "en" }
                            ?.scryfallId
                        if (englishId != null && englishId != id) {
                            scryfallIdFlow.value = englishId
                            return@collectLatest
                        }
                    }
                    initialResult
                } else {
                    cardRepo.getCardById(id)
                }
                when (result) {
                    is DataResult.Success -> {
                        val card = result.data
                        // The opened print displays as-is, in its own language — no forced
                        // redirect to an English printing (F-13 removed). printedName/printedText
                        // (with an oracle-English fallback when null) already handle localized
                        // display for foreign prints in CardDetailScreen.
                        _uiState.update { it.copy(card = card, isLoading = false, isStale = result.isStale) }
                        loadedCardFlow.value = card
                        if (card.oracleId.isBlank()) {
                            // Edge-case audit A3 (2026-07-15): this printing's cached row predates
                            // the oracle_id column — trigger a one-shot background refresh so it
                            // (and the oracle-wide Collection/Wishlist/Trade sections that key off
                            // oracleId, Phase 1B) stop silently excluding it. Fire-and-forget: the
                            // observeCard Room collector below picks up the refreshed row
                            // automatically once it lands, no manual _uiState write needed here.
                            viewModelScope.launch {
                                runCatching { cardRepo.refreshCardById(id) }
                            }
                        } else {
                            // Deck Engine Unification plan, D8, §5 Phase 5c ("card detail view" read
                            // point). Every viewed card — owned or not — gets one chance to catch up
                            // with the offline pipeline's precomputed strategy tags, even if it was
                            // cached long before this table existed (a fresh-cache short-circuit in
                            // getCardById would otherwise never re-trigger tag resolution for it).
                            // Fire-and-forget, additive-only, never blocks card display: the
                            // observeCard Room collector below picks up the merged tags
                            // automatically if any new ones are found.
                            viewModelScope.launch {
                                runCatching { refreshCardStrategyTags(id, card.oracleId, card.tags) }
                            }
                        }
                    }

                    is DataResult.Error -> _uiState.update {
                        it.copy(error = result.message, isLoading = false)
                    }
                }
            }
        }
        viewModelScope.launch {
            scryfallIdFlow.flatMapLatest { id ->
                cardRepo.observeCard(id)
            }.filterNotNull()
                .collect { card ->
                    _uiState.update {
                        it.copy(
                            card = card,
                            isStale = card.isStale
                        )
                    }
                    loadedCardFlow.value = card
                }
        }
    }

    private fun observeUserCards() {
        viewModelScope.launch {
            combine(
                authRepository.sessionState,
                cardIdentityFlow
            ) { state, identity ->
                val userId = (state as? SessionState.Authenticated)?.user?.id
                Triple(identity.first, identity.second, userId)
            }.flatMapLatest { (oracleId, name, userId) ->
                userCardRepo.observeVersionsByOracle(oracleId, name, userId)
            }.catch { e ->
                // Edge-case audit B/11 (2026-07-15): this oracle-wide observer (Phase 1B) had NO
                // .catch{} — unlike the pre-existing observeDecks — so a Room exception here would
                // propagate uncaught out of viewModelScope.launch. Capture the source key BEFORE
                // reporting (mirrors the established home_flow_error_source pattern).
                FirebaseCrashlytics.getInstance().setCustomKey("carddetail_flow_error_source", "user_cards")
                recordSafeNonFatal("carddetail_flow_error", e)
            }.collect { rows ->
                _uiState.update { it.copy(userCards = rows) }
            }
        }
    }

    private fun observeWishlistEntries() {
        viewModelScope.launch {
            cardIdentityFlow.flatMapLatest { (oracleId, name) ->
                wishlistRepo.observeVersionsByOracle(oracleId, name)
            }.catch { e ->
                FirebaseCrashlytics.getInstance().setCustomKey("carddetail_flow_error_source", "wishlist_entries")
                recordSafeNonFatal("carddetail_flow_error", e)
            }.collect { entries ->
                _uiState.update { it.copy(wishlistEntries = entries) }
            }
        }
    }

    private fun observeTradeEntries() {
        viewModelScope.launch {
            cardIdentityFlow.flatMapLatest { (oracleId, name) ->
                openForTradeRepo.observeVersionsByOracle(oracleId, name)
            }.catch { e ->
                FirebaseCrashlytics.getInstance().setCustomKey("carddetail_flow_error_source", "trade_entries")
                recordSafeNonFatal("carddetail_flow_error", e)
            }.collect { entries ->
                val qtyMap = entries.associate { it.userCardId to it.quantity }
                _uiState.update { it.copy(tradeQuantities = qtyMap) }
            }
        }
    }

    // ── Sheet visibility ──────────────────────────────────────────────────────

    /** Opens the add-to-collection sheet defaulted to the currently displayed printing (ADD mode). */
    fun onShowAddSheet() {
        val card = _uiState.value.card
        _uiState.update { it.copy(showAddSheet = true, sheetPrinting = card, entryBeingEdited = null) }
    }

    fun onDismissAddSheet() =
        _uiState.update { it.copy(showAddSheet = false, sheetPrinting = null, entryBeingEdited = null) }

    /** Opens the add-to-collection sheet in EDIT mode for an existing collection entry. */
    fun onEditCollectionEntry(entry: UserCardWithCard) {
        FirebaseCrashlytics.getInstance().log("card_detail_edit_collection_entry_opened")
        _uiState.update {
            it.copy(showAddSheet = true, sheetPrinting = entry.card, entryBeingEdited = entry)
        }
    }

    /** Opens the add-to-wishlist sheet defaulted to the currently displayed printing (ADD mode). */
    fun onShowWishlistSheet() {
        val card = _uiState.value.card
        _uiState.update { it.copy(showWishlistSheet = true, sheetPrinting = card, wishlistEntryBeingEdited = null) }
    }

    fun onDismissWishlistSheet() =
        _uiState.update { it.copy(showWishlistSheet = false, sheetPrinting = null, wishlistEntryBeingEdited = null) }

    /** Opens the add-to-wishlist sheet in EDIT mode for an existing wishlist entry. */
    fun onEditWishlistEntry(entry: WishlistEntry) {
        val printingCard = entry.card
        if (printingCard == null) {
            viewModelScope.launch {
                _events.emit(CardDetailEvent.ShowToast("Could not load this entry's card", ToastSeverity.ERROR))
            }
            return
        }
        FirebaseCrashlytics.getInstance().log("card_detail_edit_wishlist_entry_opened")
        _uiState.update {
            it.copy(showWishlistSheet = true, sheetPrinting = printingCard, wishlistEntryBeingEdited = entry)
        }
    }

    fun onShowTradeSheet() = _uiState.update { it.copy(showTradeSheet = true) }
    fun onDismissTradeSheet() = _uiState.update { it.copy(showTradeSheet = false) }
    fun onShowTagPicker() = _uiState.update { it.copy(showTagPicker = true) }
    fun onDismissTagPicker() = _uiState.update { it.copy(showTagPicker = false) }
    fun onRequestDelete(uc: UserCard) = _uiState.update { it.copy(cardToDelete = uc) }
    fun onDismissDeleteConfirm() = _uiState.update { it.copy(cardToDelete = null) }
    fun onRequestDeleteWishlist(entry: WishlistEntry) = _uiState.update { it.copy(wishlistEntryToDelete = entry) }
    fun onDismissWishlistDeleteConfirm() = _uiState.update { it.copy(wishlistEntryToDelete = null) }
    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }

    // ── Language selector (Card Versions & Languages, Phase 1B) ─────────────────

    /**
     * Opens the topbar language selector for the currently DISPLAYED printing and loads every
     * language printed for its exact set + collector number.
     */
    fun onOpenLanguageSelector() {
        val card = _uiState.value.card ?: return
        languageJob?.cancel()
        _uiState.update { it.copy(showLanguageSelector = true, isLoadingLanguages = true, languagePrints = emptyList()) }
        helper.logEvent("open_language_selector", mapOf("card_id" to card.scryfallId))
        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("language_prints_set_code", card.setCode)
            setCustomKey("language_prints_collector_number", card.collectorNumber)
            log("card_detail_language_selector_opened")
        }
        languageJob = viewModelScope.launch {
            FirebaseCrashlytics.getInstance().log("card_detail_language_prints_fetch_started")
            try {
                when (val result = cardRepo.getLanguagePrints(card.setCode, card.collectorNumber)) {
                    is DataResult.Success -> {
                        _uiState.update {
                            it.copy(languagePrints = result.data, isLoadingLanguages = false)
                        }
                        FirebaseCrashlytics.getInstance().log("card_detail_language_prints_fetch_success")
                    }
                    // Empty/error both fall back to the full language list in the UI layer — the
                    // user can still pick a language, they just won't navigate anywhere real.
                    is DataResult.Error -> {
                        _uiState.update {
                            it.copy(languagePrints = emptyList(), isLoadingLanguages = false)
                        }
                        FirebaseCrashlytics.getInstance().apply {
                            // Enum-id classification only — never the raw error message (no PII
                            // leak risk either way here, but keeps this consistent with the
                            // project's "enum ids only" telemetry rule).
                            setCustomKey(
                                "language_prints_error_type",
                                if (result.message == "SCRYFALL_404") "not_found" else "network_error",
                            )
                            log("card_detail_language_prints_fetch_failed")
                        }
                    }
                }
            } finally {
                _uiState.update { it.copy(isLoadingLanguages = false) }
            }
        }
    }

    fun onCloseLanguageSelector() {
        languageJob?.cancel()
        _uiState.update { it.copy(showLanguageSelector = false, languagePrints = emptyList(), isLoadingLanguages = false) }
    }

    /** Picks a REAL print returned by [onOpenLanguageSelector]; re-keys the displayed printing. */
    fun onSelectLanguagePrint(print: Card) {
        _uiState.update { it.copy(showLanguageSelector = false) }
        // Guard against the StateFlow self-assignment no-op trap (see
        // feedback_carddetail_variant_lookup_bugs memory): assigning the same value is a silent
        // no-op that would otherwise leave the sheet's dismissal as the only visible effect, which
        // is fine here — but skip the write entirely to make the intent explicit.
        if (print.scryfallId == scryfallId) return
        helper.logEvent("select_language_print", mapOf("card_id" to print.scryfallId))
        FirebaseCrashlytics.getInstance().log("card_detail_language_print_selected")
        scryfallIdFlow.value = print.scryfallId
    }

    /**
     * Picks a language from the FALLBACK list (no real print exists for it at this set/number).
     * Per product decision, only real prints navigate — this just informs the user.
     */
    fun onSelectFallbackLanguage(langCode: String) {
        _uiState.update { it.copy(showLanguageSelector = false) }
        val current = _uiState.value.card?.lang
        if (langCode == current) return
        // Previously zero telemetry for this outcome (toast only) — high product value: measures
        // how often users hit a "print not available in this language" dead end.
        FirebaseCrashlytics.getInstance().log("card_detail_language_fallback_selected")
        viewModelScope.launch {
            _events.emit(
                CardDetailEvent.ShowToast(
                    "This print is not available in ${CardConstants.getLanguageName(langCode)}",
                    ToastSeverity.INFO,
                )
            )
        }
    }

    // ── Variant (other prints) selector ─────────────────────────────────────────

    /** Opens the variant selector from the main screen's "Explore All Versions" row. */
    fun onOpenVariantSelector() {
        val cardName = _uiState.value.card?.name ?: return
        openVariantSelector(VariantSelectorSource.SCREEN, cardName)
    }

    /**
     * Opens the variant selector from WITHIN the add/edit sheet's "Set / Variant" field. Selecting
     * a variant here swaps [CardDetailUiState.sheetPrinting] in place instead of navigating away —
     * see [onSelectVariant].
     */
    fun onOpenVariantSelectorForSheet() {
        val cardName = (_uiState.value.sheetPrinting ?: _uiState.value.card)?.name ?: return
        openVariantSelector(VariantSelectorSource.SHEET, cardName)
    }

    private fun openVariantSelector(source: VariantSelectorSource, cardName: String) {
        variantJob?.cancel()
        _uiState.update {
            it.copy(
                showVariantSelector = true,
                isLoadingVariants = true,
                cardVariants = emptyList(),
                variantSelectorSource = source,
            )
        }
        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("variant_selector_source", source.name)
            log("card_detail_variant_selector_opened")
        }
        variantJob = viewModelScope.launch {
            try {
                when (val result = cardRepo.getCardArtVariants(cardName)) {
                    is DataResult.Success -> _uiState.update { it.copy(cardVariants = result.data, isLoadingVariants = false) }
                    is DataResult.Error -> {
                        _uiState.update { it.copy(isLoadingVariants = false) }
                        FirebaseCrashlytics.getInstance().apply {
                            setCustomKey("variant_selector_error_type", "network_error")
                            log("card_detail_variant_fetch_failed")
                        }
                        _events.emit(CardDetailEvent.ShowToast("Could not load other prints", ToastSeverity.ERROR))
                    }
                }
            } finally {
                _uiState.update { it.copy(isLoadingVariants = false) }
            }
        }
    }

    /**
     * Dismisses the variant selector. Deliberately does NOT touch [CardDetailUiState.showAddSheet] /
     * [CardDetailUiState.showWishlistSheet] — when [CardDetailUiState.variantSelectorSource] is
     * [VariantSelectorSource.SHEET] the underlying sheet was never closed, so the user is returned
     * to it automatically.
     */
    fun onCloseVariantSelector() {
        variantJob?.cancel()
        _uiState.update { it.copy(showVariantSelector = false, cardVariants = emptyList(), isLoadingVariants = false, expandedVariantImageUrl = null) }
    }

    /**
     * Picks a variant. [VariantSelectorSource.SCREEN] emits a one-shot navigation event (unchanged
     * behavior); [VariantSelectorSource.SHEET] instead swaps [CardDetailUiState.sheetPrinting] so the
     * still-open add/edit sheet reflects the newly chosen printing.
     */
    fun onSelectVariant(card: Card) {
        when (_uiState.value.variantSelectorSource) {
            VariantSelectorSource.SCREEN -> {
                // getCardArtVariants has no lang: filter (Scryfall returns English objects for
                // every variant), so navigating straight to `card.scryfallId` would silently
                // force English even when the user was viewing a localized print. Try to resolve
                // the SAME printing (set + collector number) in the language that was already
                // being displayed before this navigation, falling back to the tapped English
                // printing when no such localized object exists or the lookup fails.
                val previousLang = _uiState.value.card?.lang
                _uiState.update { it.copy(showVariantSelector = false) }
                viewModelScope.launch {
                    val targetScryfallId = if (previousLang != null) {
                        val result = cardRepo.getLanguagePrints(card.setCode, card.collectorNumber)
                        (result as? DataResult.Success)?.data
                            ?.firstOrNull { it.lang == previousLang }
                            ?.scryfallId
                            ?: card.scryfallId
                    } else {
                        card.scryfallId
                    }
                    _events.emit(CardDetailEvent.NavigateToCard(targetScryfallId))
                }
            }
            VariantSelectorSource.SHEET -> {
                _uiState.update { it.copy(showVariantSelector = false, sheetPrinting = card) }
            }
        }
    }

    fun onExpandVariantImage(url: String) = _uiState.update { it.copy(expandedVariantImageUrl = url) }
    fun onCloseExpandedImage() = _uiState.update { it.copy(expandedVariantImageUrl = null) }

    // ── Collection mutations ──────────────────────────────────────────────────

    /**
     * Resolves the exact Scryfall printing id for [basePrinting]'s set + collector number in
     * [language] — so the CardEntity ultimately FK'd from the collection/wishlist entry (and thus
     * its price fields) matches what the user actually selected, not whichever printing happened to
     * be cached as [basePrinting] (see feedback memory on the AddCardSheet price bug).
     */
    private suspend fun resolvePricedScryfallId(basePrinting: Card, language: String): String {
        if (basePrinting.lang == language) return basePrinting.scryfallId
        val result = cardRepo.getLanguagePrints(basePrinting.setCode, basePrinting.collectorNumber)
        if (result is DataResult.Success) {
            result.data.firstOrNull { it.lang == language }?.let { return it.scryfallId }
        }
        // No Scryfall object exists for this exact (set, collector number, language) — see if the
        // user already owns an identical copy (same set/number/language) whose linked card has real
        // price data, and reuse it; otherwise fall back to the originally-targeted printing (its
        // price may be for the wrong language, but there is nothing better to link to — this mirrors
        // every other case where Scryfall simply has no price for a card).
        _uiState.value.userCards.firstOrNull { row ->
            row.card.setCode == basePrinting.setCode &&
                row.card.collectorNumber == basePrinting.collectorNumber &&
                row.userCard.language == language &&
                (row.card.priceUsd != null || row.card.priceEur != null)
        }?.let { return it.card.scryfallId }
        return basePrinting.scryfallId
    }

    fun onAddToCollection(
        isFoil: Boolean,
        condition: String, language: String, quantity: Int,
    ) {
        viewModelScope.launch {
            // The chosen printing may differ from the displayed one (Set / Variant field); resolve
            // the printing matching the SELECTED language so price fields line up (see
            // resolvePricedScryfallId).
            val basePrinting = _uiState.value.sheetPrinting ?: _uiState.value.card
            val targetScryfallId = basePrinting?.let { resolvePricedScryfallId(it, language) } ?: scryfallId
            val result = addToCollection(
                scryfallId = targetScryfallId,
                isFoil = isFoil,
                condition = condition,
                language = language,
                quantity = quantity,
            )
            if (result is DataResult.Error) {
                _uiState.update { it.copy(error = result.message) }
                helper.logEvent("error_add_card_collection", mapOf("card_id" to targetScryfallId))
                _events.emit(
                    CardDetailEvent.ShowToast(
                        "Failed to add card", ToastSeverity.ERROR,
                    )
                )
            } else {
                helper.logEvent("add_card_collection", mapOf("card_id" to targetScryfallId))
                _events.emit(CardDetailEvent.ShowToast("Card added to your collection"))
            }
            _uiState.update { it.copy(showAddSheet = false, sheetPrinting = null) }
        }
    }

    /**
     * Card Versions & Languages, Phase 1B. Edits [CardDetailUiState.entryBeingEdited] in place via
     * [UpdateCollectionEntryUseCase] — the atomic re-point/merge is fully owned by that use case /
     * [UserCardRepository.updateEntryWithMerge]; the oracle-wide observer picks up the change
     * automatically (no manual reload).
     */
    fun onUpdateCollectionEntry(
        isFoil: Boolean,
        condition: String, language: String, quantity: Int,
    ) {
        val entry = _uiState.value.entryBeingEdited ?: return
        val userId = (authRepository.sessionState.value as? SessionState.Authenticated)?.user?.id
        viewModelScope.launch {
            val basePrinting = _uiState.value.sheetPrinting ?: entry.card
            val targetScryfallId = resolvePricedScryfallId(basePrinting, language)
            val result = updateCollectionEntry(
                entryId = entry.userCard.id,
                newScryfallId = targetScryfallId,
                isFoil = isFoil,
                condition = condition,
                language = language,
                quantity = quantity,
                userId = userId,
            )
            when {
                result is DataResult.Error -> {
                    helper.logEvent("error_update_collection_entry", mapOf("entry_id" to entry.userCard.id))
                    _uiState.update { it.copy(error = result.message) }
                    _events.emit(CardDetailEvent.ShowToast("Failed to update entry", ToastSeverity.ERROR))
                }
                result is DataResult.Success && result.data == UpdateEntryOutcome.ENTRY_NOT_FOUND -> {
                    // A2 (edge-case audit, 2026-07-15): the entry was deleted concurrently
                    // (another device / sync) between opening the edit sheet and confirming — tell
                    // the user honestly instead of the misleading "Entry updated".
                    helper.logEvent("error_update_collection_entry_not_found", mapOf("entry_id" to entry.userCard.id))
                    _events.emit(CardDetailEvent.ShowToast("This entry no longer exists", ToastSeverity.ERROR))
                }
                else -> {
                    helper.logEvent("update_collection_entry", mapOf("entry_id" to entry.userCard.id))
                    _events.emit(CardDetailEvent.ShowToast("Entry updated"))
                }
            }
            _uiState.update { it.copy(showAddSheet = false, sheetPrinting = null, entryBeingEdited = null) }
        }
    }

    fun onAddToWishlist(
        isFoil: Boolean,
        condition: String, language: String, quantity: Int,
    ) {
        viewModelScope.launch {
            val basePrinting = _uiState.value.sheetPrinting ?: _uiState.value.card
            val targetScryfallId = basePrinting?.let { resolvePricedScryfallId(it, language) } ?: scryfallId
            val entry = WishlistEntry(
                id              = UUID.randomUUID().toString(),
                userId          = "",
                cardId          = targetScryfallId,
                quantity        = quantity,
                matchAnyVariant = false,
                isFoil          = isFoil,
                condition       = condition.uppercase().trim(),
                language        = language.lowercase().trim(),
                createdAt       = System.currentTimeMillis(),
            )
            addToWishlistUseCase(entry)
                .onSuccess {
                    helper.logEvent("add_card_wishlist", mapOf("card_id" to targetScryfallId))
                    _events.emit(CardDetailEvent.ShowToast("Added to wishlist"))
                }
                .onFailure { e ->
                    helper.logEvent("error_add_card_wishlist", mapOf("card_id" to targetScryfallId))
                    _uiState.update { it.copy(error = e.message) }
                    _events.emit(
                        CardDetailEvent.ShowToast(
                            "Could not add to wishlist: ${e.message}",
                            ToastSeverity.ERROR
                        )
                    )
                }
            _uiState.update { it.copy(showWishlistSheet = false, sheetPrinting = null) }
        }
    }

    /**
     * Card Versions & Languages, Phase 1B. Edits [CardDetailUiState.wishlistEntryBeingEdited] in
     * place via [UpdateWishlistEntryUseCase] — see that use case's KDoc for the remote-first merge
     * semantics. The oracle-wide observer picks up the change automatically (no manual reload).
     */
    fun onUpdateWishlistEntry(
        isFoil: Boolean,
        condition: String, language: String, quantity: Int,
    ) {
        val entry = _uiState.value.wishlistEntryBeingEdited ?: return
        viewModelScope.launch {
            val basePrinting = _uiState.value.sheetPrinting ?: entry.card
            val targetCardId = basePrinting?.let { resolvePricedScryfallId(it, language) } ?: entry.cardId
            updateWishlistEntry(
                entryId = entry.id,
                newCardId = targetCardId,
                isFoil = isFoil,
                condition = condition.uppercase().trim(),
                language = language.lowercase().trim(),
                quantity = quantity,
            )
                .onSuccess { outcome ->
                    if (outcome == UpdateEntryOutcome.ENTRY_NOT_FOUND) {
                        // A2 (edge-case audit, 2026-07-15): the entry was deleted concurrently
                        // (another device / sync) between opening the edit sheet and confirming —
                        // tell the user honestly instead of the misleading "Entry updated".
                        helper.logEvent("error_update_wishlist_entry_not_found", mapOf("entry_id" to entry.id))
                        _events.emit(CardDetailEvent.ShowToast("This entry no longer exists", ToastSeverity.ERROR))
                    } else {
                        helper.logEvent("update_wishlist_entry", mapOf("entry_id" to entry.id))
                        _events.emit(CardDetailEvent.ShowToast("Entry updated"))
                    }
                }
                .onFailure { e ->
                    helper.logEvent("error_update_wishlist_entry", mapOf("entry_id" to entry.id))
                    _uiState.update { it.copy(error = e.message) }
                    _events.emit(
                        CardDetailEvent.ShowToast(
                            "Failed to update entry: ${e.message}",
                            ToastSeverity.ERROR
                        )
                    )
                }
            _uiState.update { it.copy(showWishlistSheet = false, sheetPrinting = null, wishlistEntryBeingEdited = null) }
        }
    }

    fun onConfirmTradeSelection(selections: Map<String, Int>) {
        val userCards = _uiState.value.userCards
        val currentQty = _uiState.value.tradeQuantities
        viewModelScope.launch {
            var anyError = false
            var totalOffered = 0
            selections.forEach { (id, desiredQty) ->
                val uc = userCards.find { it.userCard.id == id }?.userCard ?: return@forEach
                val prevQty = currentQty[id] ?: 0
                if (desiredQty == prevQty) {
                    totalOffered += desiredQty
                    return@forEach
                }
                if (desiredQty > 0) {
                    // Add or update the trade entry — push immediately when online.
                    val userId = (authRepository.sessionState.value as? SessionState.Authenticated)?.user?.id
                    val tradeResult = if (userId != null) {
                        openForTradeRepo.addAndSync(
                            scryfallId = uc.scryfallId,
                            localCollectionId = uc.id,
                            quantity = desiredQty,
                            isFoil = uc.isFoil,
                            condition = uc.condition,
                            language = uc.language,
                            userId = userId,
                        )
                    } else {
                        openForTradeRepo.addLocal(
                            scryfallId = uc.scryfallId,
                            localCollectionId = uc.id,
                            quantity = desiredQty,
                            isFoil = uc.isFoil,
                            condition = uc.condition,
                            language = uc.language,
                        )
                    }
                    tradeResult.onFailure { e ->
                        anyError = true
                        _uiState.update { it.copy(error = e.message) }
                    }
                    totalOffered += desiredQty
                } else {
                    // Remove the trade entry
                    openForTradeRepo.removeByCollectionId(uc.id)
                        .onFailure { e ->
                            anyError = true
                            _uiState.update { it.copy(error = e.message) }
                        }
                }
            }
            if (!anyError) {
                _events.emit(
                    CardDetailEvent.ShowToast(
                        if (totalOffered > 0) "$totalOffered ${if (totalOffered == 1) "copy" else "copies"} offered for trade"
                        else "Trade offers cleared"
                    )
                )
            }
            _uiState.update { it.copy(showTradeSheet = false) }
        }
    }

    fun onDeleteCard(userCardId: String) {
        viewModelScope.launch {
            runCatching { userCardRepo.deleteCard(userCardId) }
                .onSuccess {
                    helper.logEvent("delete_card", mapOf("card_id" to userCardId))
                    _uiState.update { it.copy(cardToDelete = null) } }
                .onFailure { e ->
                    helper.logEvent("error_delete_card", mapOf("card_id" to userCardId))
                    _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun onDeleteWishlistEntry(id: String) {
        viewModelScope.launch {
            wishlistRepo.removeLocal(id)
                .onSuccess {
                    helper.logEvent("delete_wishlist_entry", mapOf("id" to id))
                    _uiState.update { it.copy(wishlistEntryToDelete = null) }
                }
                .onFailure { e ->
                    helper.logEvent("error_delete_wishlist_entry", mapOf("id" to id))
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    // ── Auto-tag mutations ────────────────────────────────────────────────────

    fun onRemoveTag(tag: CardTag) {
        val current = _uiState.value.card?.tags ?: return
        val updated = current - tag
        _uiState.update { it.copy(card = it.card?.copy(tags = updated)) }
        viewModelScope.launch {
            runCatching { cardRepo.updateCardTags(scryfallId, updated) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    // ── User-tag mutations ────────────────────────────────────────────────────

    fun onAddUserTag(tag: CardTag) {
        val current = _uiState.value.card?.userTags ?: return
        if (tag in current) return
        val updated = current + tag
        // Optimistic update so the UI refreshes immediately
        _uiState.update { it.copy(card = it.card?.copy(userTags = updated)) }
        viewModelScope.launch {
            runCatching { cardRepo.updateUserTags(scryfallId, updated) }
                .onSuccess {
                    helper.logEvent("add_user_tag", mapOf("tag" to tag.label()))
                    _events.emit(CardDetailEvent.ShowToast("Tag '${tag.label()}' added")) }
                .onFailure { e ->
                    // Roll back on failure
                    helper.logEvent("error_add_user_tag", mapOf("tag" to tag.label()))
                    _uiState.update {
                        it.copy(
                            card = it.card?.copy(userTags = current),
                            error = e.message
                        )
                    }
                }
        }
    }

    fun onRemoveUserTag(tag: CardTag) {
        val current = _uiState.value.card?.userTags ?: return
        val updated = current - tag
        _uiState.update { it.copy(card = it.card?.copy(userTags = updated)) }
        viewModelScope.launch {
            runCatching { cardRepo.updateUserTags(scryfallId, updated) }
                .onSuccess {
                    helper.logEvent("remove_user_tag", mapOf("tag" to tag.label()))
                }
                .onFailure { e ->
                    helper.logEvent("error_remove_user_tag", mapOf("tag" to tag.label()))
                    _uiState.update {
                        it.copy(
                            card = it.card?.copy(userTags = current),
                            error = e.message
                        )
                    }
                }
        }
    }

    fun onSaveAndAddCustomTag(label: String, categoryKey: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        val key = trimmed.lowercase()
            .replace(' ', '_')
            .replace(Regex("[^a-z0-9_]"), "")
            .take(50)
        if (key.isEmpty()) return
        val newTag = CardTag(key, TagCategory.CUSTOM)
        val updatedUserTags = ((_uiState.value.card?.userTags ?: emptyList()) + newTag).distinct()
        // Optimistic update
        _uiState.update { it.copy(card = it.card?.copy(userTags = updatedUserTags)) }
        val userDefinedTag = UserDefinedTag(key = key, label = trimmed, categoryKey = categoryKey)
        viewModelScope.launch {
            runCatching {
                userPrefs.saveUserDefinedTag(userDefinedTag)
                cardRepo.updateUserTags(scryfallId, updatedUserTags)
            }.onSuccess {
                helper.logEvent("save_custom_tag", mapOf("label" to label, "category" to categoryKey))
                _events.emit(CardDetailEvent.ShowToast("'$trimmed' tag created and added"))
            }.onFailure { e ->
                helper.logEvent("error_save_custom_tag", mapOf("label" to label, "category" to categoryKey))
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // ── User-defined tag management ───────────────────────────────────────────

    fun onDeleteUserDefinedTag(key: String) {
        viewModelScope.launch {
            runCatching { userPrefs.deleteUserDefinedTag(key) }
                .onSuccess {
                    helper.logEvent("delete_custom_tag", mapOf("key" to key))
                    _events.emit(
                        CardDetailEvent.ShowToast(
                            "Custom tag deleted",
                            ToastSeverity.INFO
                        )
                    )
                }
                .onFailure { e ->
                    helper.logEvent("error_delete_custom_tag", mapOf("key" to key))
                    _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun onUpdateUserDefinedTag(key: String, newLabel: String) {
        val trimmed = newLabel.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val existing = _uiState.value.userDefinedTags.find { it.key == key } ?: return@launch
            runCatching { userPrefs.saveUserDefinedTag(existing.copy(label = trimmed)) }
                .onSuccess {
                    helper.logEvent("update_custom_tag", mapOf("label" to newLabel, "key" to key))
                    _events.emit(CardDetailEvent.ShowToast("Tag renamed to '$trimmed'")) }
                .onFailure { e ->
                    helper.logEvent("error_update_custom_tag", mapOf("label" to newLabel, "key" to key))
                    _uiState.update { it.copy(error = e.message) } }
        }
    }

    // ── Suggested tag mutations ───────────────────────────────────────────────

    fun onConfirmSuggestedTag(tag: CardTag) {
        viewModelScope.launch {
            runCatching { cardRepo.confirmSuggestedTag(scryfallId, tag) }
                .onSuccess {
                    helper.logEvent("confirm_suggested_tag", mapOf("tag" to tag.label()))
                    _events.emit(CardDetailEvent.ShowToast("Tag '${tag.label()}' confirmed")) }
                .onFailure { e ->
                    helper.logEvent("error_confirm_suggested_tag", mapOf("tag" to tag.label()))
                    _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun onDismissSuggestedTag(tag: CardTag) {
        viewModelScope.launch {
            runCatching { cardRepo.dismissSuggestedTag(scryfallId, tag) }
                .onSuccess {
                    helper.logEvent("dismiss_suggested_tag", mapOf("tag" to tag.label()))
                }
                .onFailure { e ->
                    helper.logEvent("error_dismiss_suggested_tag", mapOf("tag" to tag.label()))
                    _uiState.update { it.copy(error = e.message) } }
        }
    }
}
