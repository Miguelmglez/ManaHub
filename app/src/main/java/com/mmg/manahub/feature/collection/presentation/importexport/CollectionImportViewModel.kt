package com.mmg.manahub.feature.collection.presentation.importexport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.collection.transfer.CollectionFileGateway
import com.mmg.manahub.core.domain.collection.transfer.CollectionImportParser
import com.mmg.manahub.core.domain.collection.transfer.CollectionImportResolution
import com.mmg.manahub.core.domain.collection.transfer.CollectionImportUnresolvedStore
import com.mmg.manahub.core.domain.collection.transfer.FileTooLargeException
import com.mmg.manahub.core.domain.collection.transfer.ParsedCollectionImport
import com.mmg.manahub.core.domain.collection.transfer.ResolveCollectionImportUseCase
import com.mmg.manahub.core.domain.collection.transfer.transferCountBucket
import com.mmg.manahub.core.domain.repository.CardQueueRepository
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.usecase.queue.AddAllToCollectionResult
import com.mmg.manahub.core.domain.usecase.queue.CardQueueActions
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.QueuedCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Collection "Import to collection": parses a pasted list or a file, resolves it in Scryfall batches,
 * and lets the user review the result in a PRIVATE persistent queue before committing it.
 *
 * The queue and its [CardQueueActions] are Koin singletons separate from the shared AddCard/Scanner
 * queue, so a long review survives navigation and process death without ever touching that queue.
 * Commits go through a no-XP committer and run in an app-lifetime scope on the main thread (the
 * queue repository is not synchronized).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionImportViewModel(
    private val resolveImport: ResolveCollectionImportUseCase,
    private val fileGateway: CollectionFileGateway,
    private val queueRepository: CardQueueRepository,
    private val queueActions: CardQueueActions,
    private val cardRepository: CardRepository,
    private val userCardRepository: UserCardRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val unresolvedStore: CollectionImportUnresolvedStore,
    private val crashReporter: CrashReporter,
    appScope: CoroutineScope,
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val progressThrottle: () -> ImportProgressThrottle = { ImportProgressThrottle() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CollectionImportUiState(
            queue = queueRepository.queue.value,
            isCommitting = queueActions.isCommitting.value,
            isAddingAllToWishlist = queueActions.isAddingAllToWishlist.value,
            inFlightIds = queueActions.inFlightIds.value,
        )
    )
    val uiState: StateFlow<CollectionImportUiState> = _uiState.asStateFlow()

    // App-lifetime, not viewModelScope: a commit cancelled halfway would leave already-written
    // entries queued, to be written again next time. Its callbacks keep this ViewModel (and the
    // state it holds) alive until the batch finishes, which is the price of that guarantee.
    private val commitScope = CoroutineScope(appScope.coroutineContext + mainDispatcher)

    private var resolveJob: Job? = null
    private var printsLoadJob: Job? = null
    private var variantLoadJob: Job? = null

    init {
        viewModelScope.launch {
            queueRepository.queue.collect { queue -> _uiState.update { it.copy(queue = queue) } }
        }
        viewModelScope.launch {
            queueActions.isCommitting.collect { v -> _uiState.update { it.copy(isCommitting = v) } }
        }
        viewModelScope.launch {
            queueActions.isAddingAllToWishlist.collect { v -> _uiState.update { it.copy(isAddingAllToWishlist = v) } }
        }
        viewModelScope.launch {
            queueActions.inFlightIds.collect { ids -> _uiState.update { it.copy(inFlightIds = ids) } }
        }
        viewModelScope.launch {
            userPreferencesRepository.preferredCurrencyFlow
                .catch { }
                .collect { currency -> _uiState.update { it.copy(preferredCurrency = currency) } }
        }
        observeOwnedCardIdentityKeys()
        restoreUnresolvedLines()
    }

    // The queue outlives the process; its unresolved lines must too, or a restored review offers
    // "show N lines not found" for lines that no longer exist.
    private fun restoreUnresolvedLines() {
        viewModelScope.launch {
            val restored = withContext(ioDispatcher) { runCatching { unresolvedStore.read() }.getOrDefault(emptyList()) }
            if (restored.isEmpty()) return@launch
            _uiState.update {
                if (it.unresolvedLines.isEmpty()) {
                    it.copy(unresolvedLines = restored, unresolvedTotal = restored.size)
                } else it
            }
        }
    }

    private fun persistUnresolvedLines(lines: List<String>) {
        viewModelScope.launch(ioDispatcher) { runCatching { unresolvedStore.write(lines) } }
    }

    // Only collected while the review sheet is actually showing: the owned badge is only shown
    // there, and rebuilding the identity set on every Room invalidation is not free.
    private fun observeOwnedCardIdentityKeys() {
        viewModelScope.launch {
            _uiState.map { it.isQueueSheetOpen }
                .distinctUntilChanged()
                .flatMapLatest { visible ->
                    if (visible) userCardRepository.observeCollection().map { rows ->
                        HashSet<String>(rows.size * 2).also { keys ->
                            rows.forEach { row ->
                                row.card.oracleId.takeIf { it.isNotBlank() }?.let(keys::add)
                                keys.add(row.card.name)
                            }
                        }
                    } else flowOf(emptySet())
                }
                .catch { }
                .collect { keys -> _uiState.update { it.copy(ownedCardIdentityKeys = keys) } }
        }
    }

    // ── Entry point ──────────────────────────────────────────────────────────

    /** "Import to collection": offers to resume a pending review instead of silently discarding it. */
    fun onImportRequested() {
        if (queueRepository.queue.value.isNotEmpty()) {
            _uiState.update { it.copy(isResumePromptVisible = true) }
        } else {
            openInputSheet()
        }
    }

    fun onResumeReview() {
        _uiState.update { it.copy(isResumePromptVisible = false, isQueueSheetVisible = true) }
    }

    /** Opens the input sheet; its results merge into the pending review. */
    fun onImportMore() {
        _uiState.update { it.copy(isResumePromptVisible = false) }
        openInputSheet()
    }

    fun onDismissResumePrompt() {
        _uiState.update { it.copy(isResumePromptVisible = false) }
    }

    private fun openInputSheet() {
        _uiState.update { it.copy(isInputSheetVisible = true, inputError = null) }
    }

    /** Closes the input sheet and cancels a resolution in progress (nothing was queued yet). */
    fun onDismissInputSheet() {
        resolveJob?.cancel()
        _uiState.update {
            it.copy(isInputSheetVisible = false, isResolving = false, inputError = null, progressProcessed = 0, progressTotal = 0)
        }
    }

    // ── Parse + resolve ──────────────────────────────────────────────────────

    fun onImportText(text: String) {
        // The byte ceiling is a property of the IMPORT, not of the file path: a paste reaches the
        // same parser with no gateway in front of it to enforce MAX_FILE_BYTES.
        if (text.length.toLong() * Char.SIZE_BYTES > MAX_FILE_BYTES) {
            failImport(ImportSource.PASTE, CollectionImportError.FileTooLarge, "paste_too_large")
            return
        }
        startImport(ImportSource.PASTE) { text }
    }

    /** Reads the picked document off the main thread; its text never reaches the UI. */
    fun onImportFile(location: String) {
        startImport(ImportSource.FILE) { fileGateway.readText(location, MAX_FILE_BYTES) }
    }

    private fun startImport(source: ImportSource, readText: suspend () -> String) {
        // A second tap while a resolution is running is dropped in silence on purpose: the input
        // sheet already shows the progress row, so there is nothing new to tell the user.
        if (resolveJob?.isActive == true) return
        _uiState.update { it.copy(isResolving = true, inputError = null, progressProcessed = 0, progressTotal = 0) }
        resolveJob = viewModelScope.launch {
            val text = try {
                readText()
            } catch (c: CancellationException) {
                throw c
            } catch (e: FileTooLargeException) {
                failImport(source, CollectionImportError.FileTooLarge, "file_too_large")
                return@launch
            } catch (e: Exception) {
                recordNonFatal("collection_import_file_read", e)
                failImport(source, CollectionImportError.FileUnreadable, "file_unreadable")
                return@launch
            }
            // The picker must accept */* (providers mislabel .csv), so a picked image only fails here.
            if (withContext(parseDispatcher) { CollectionImportParser.looksBinary(text) }) {
                failImport(source, CollectionImportError.FileUnreadable, "not_text")
                return@launch
            }
            val parsed = withContext(parseDispatcher) {
                // Parsing is a long non-suspending loop: without this probe, dismissing the sheet
                // leaves it running the whole document on a Default thread.
                val context = currentCoroutineContext()
                CollectionImportParser.parse(text) { context.ensureActive() }
            }
            crashReporter.log("collection_import_started")
            crashReporter.setCustomKey("collection_import_input", "${source.key}:${parsed.format.name}")
            crashReporter.setCustomKey("collection_import_lines_bucket", transferCountBucket(parsed.lines.size))
            if (parsed.lines.isEmpty()) {
                failImport(source, CollectionImportError.NothingRecognized, "nothing_recognized")
                return@launch
            }
            // A byte cap is not a row cap: 500 KB of text is already ~8k rows, and every one of them
            // would be resolved, held in memory and re-encoded on every later edit.
            if (parsed.lines.size > MAX_IMPORT_QUEUE_ENTRIES) {
                failImport(source, CollectionImportError.TooManyLines(MAX_IMPORT_QUEUE_ENTRIES), "too_many_lines")
                return@launch
            }
            resolve(source, parsed)
        }
    }

    private suspend fun resolve(source: ImportSource, parsed: ParsedCollectionImport) {
        _uiState.update { it.copy(progressTotal = parsed.lines.size) }
        val resolution = try {
            // The use case is dispatcher-free by design (commonMain): identifier building, the
            // per-chunk index and the final merge are all CPU-bound over thousands of lines.
            withContext(parseDispatcher) {
                val throttle = progressThrottle()
                resolveImport(parsed) { processed, total ->
                    if (throttle.shouldEmit(processed, total)) {
                        _uiState.update { it.copy(progressProcessed = processed, progressTotal = total) }
                    }
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            recordNonFatal("collection_import_resolve", e)
            failImport(source, CollectionImportError.LookupFailed, "lookup_exception")
            return
        }
        when (resolution) {
            is CollectionImportResolution.RateLimited -> failImport(
                source,
                CollectionImportError.RateLimited((resolution.retryAfterMs + 999) / 1000),
                "rate_limited",
            )
            CollectionImportResolution.Failed -> failImport(source, CollectionImportError.LookupFailed, "lookup_failed")
            is CollectionImportResolution.Resolved -> onResolved(source, resolution)
        }
    }

    private fun onResolved(source: ImportSource, resolution: CollectionImportResolution.Resolved) {
        crashReporter.log("collection_import_resolved")
        crashReporter.setCustomKey("collection_import_unresolved_bucket", transferCountBucket(resolution.unresolvedCount))
        if (resolution.entries.isEmpty()) {
            _uiState.update {
                it.copy(
                    isResolving = false,
                    inputError = CollectionImportError.NothingResolved,
                    unresolvedLines = resolution.unresolvedLines,
                    unresolvedTotal = resolution.unresolvedCount,
                )
            }
            persistUnresolvedLines(resolution.unresolvedLines)
            return
        }
        val merge = mergeIntoQueue(resolution.entries) ?: run {
            failImport(source, CollectionImportError.TooManyLines(MAX_IMPORT_QUEUE_ENTRIES), "queue_full")
            return
        }
        val clampedCopies = (resolution.clampedCopies.toLong() + merge)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (clampedCopies > 0) crashReporter.log("collection_import_quantity_clamped")
        _uiState.update {
            it.copy(
                isResolving = false,
                isInputSheetVisible = false,
                isQueueSheetVisible = true,
                progressProcessed = 0,
                progressTotal = 0,
                unresolvedLines = resolution.unresolvedLines,
                unresolvedTotal = resolution.unresolvedCount,
                isUnresolvedDialogVisible = resolution.unresolvedLines.isNotEmpty(),
                queueToast = CollectionImportToast.Resolved(
                    entries = resolution.entries.size,
                    unresolved = resolution.unresolvedCount,
                    clampedCopies = clampedCopies,
                ),
            )
        }
        persistUnresolvedLines(resolution.unresolvedLines)
    }

    /**
     * Merges [entries] into the review queue with ONE persisted write: bumping an existing row only
     * adds surplus copies, which removeCommitted keeps. Returns the copies the per-row quantity cap
     * had to drop (Long: it can exceed Int.MAX_VALUE), or null when the merge would push the queue
     * past [MAX_IMPORT_QUEUE_ENTRIES].
     */
    private fun mergeIntoQueue(entries: List<QueuedCard>): Long? {
        val current = queueRepository.queue.value
        if (current.isEmpty()) {
            if (entries.size > MAX_IMPORT_QUEUE_ENTRIES) return null
            queueRepository.addAll(entries)
            return 0L
        }
        val merged = current.toMutableList()
        val indexByKey = HashMap<String, Int>(merged.size)
        merged.forEachIndexed { i, entry -> indexByKey.putIfAbsent(entry.mergeKey(), i) }
        var clamped = 0L
        entries.forEach { entry ->
            val index = indexByKey[entry.mergeKey()]
            if (index == null) {
                indexByKey[entry.mergeKey()] = merged.size
                merged += entry
            } else {
                val wanted = merged[index].quantity.toLong() + entry.quantity
                val capped = wanted.coerceAtMost(CollectionImportParser.MAX_QUANTITY_PER_LINE.toLong())
                clamped += wanted - capped
                merged[index] = merged[index].copy(quantity = capped.toInt())
            }
        }
        if (merged.size > MAX_IMPORT_QUEUE_ENTRIES) return null
        queueRepository.replaceAll(merged)
        return clamped
    }

    private fun QueuedCard.mergeKey() = "${card.scryfallId}|$isFoil|$language|$condition"

    // The reason rides in the breadcrumb name, not a fifth custom key (CLAUDE.md caps an op at 3-4).
    private fun failImport(source: ImportSource, error: CollectionImportError, reason: String) {
        crashReporter.log("collection_import_failed_$reason")
        crashReporter.setCustomKey("collection_import_input", source.key)
        _uiState.update { it.copy(isResolving = false, inputError = error) }
    }

    fun onShowUnresolved() {
        if (_uiState.value.unresolvedLines.isEmpty()) return
        _uiState.update { it.copy(isUnresolvedDialogVisible = true) }
    }

    fun onDismissUnresolved() {
        _uiState.update { it.copy(isUnresolvedDialogVisible = false) }
    }

    // ── Review queue ─────────────────────────────────────────────────────────

    fun onOpenQueueSheet() {
        if (queueRepository.queue.value.isEmpty()) return
        _uiState.update { it.copy(isQueueSheetVisible = true) }
    }

    fun onCloseQueueSheet() {
        _uiState.update { it.copy(isQueueSheetVisible = false) }
    }

    fun onRemoveEntry(entry: QueuedCard) = queueRepository.remove(entry.id)

    fun onClearQueue() {
        queueRepository.clear()
        _uiState.update {
            it.copy(
                isQueueSheetVisible = false,
                unresolvedLines = emptyList(),
                unresolvedTotal = 0,
                isUnresolvedDialogVisible = false,
            )
        }
        persistUnresolvedLines(emptyList())
    }

    fun onIncrementQuantity(entry: QueuedCard) = queueRepository.incrementQuantity(entry.id)

    fun onDecrementQuantity(entry: QueuedCard) = queueRepository.decrementQuantity(entry.id)

    fun onDuplicateEntry(entry: QueuedCard) {
        queueRepository.duplicate(entry)
    }

    fun onToggleAutoDeleteOnAdd() {
        _uiState.update { it.copy(isAutoDeleteOnAddEnabled = !it.isAutoDeleteOnAddEnabled) }
    }

    fun onAddEntryToCollection(entry: QueuedCard) {
        queueActions.addEntryToCollection(commitScope, entry, _uiState.value.isAutoDeleteOnAddEnabled) { ok ->
            showToast(
                if (ok) CollectionImportToast.AddedToCollection(entry.card.name)
                else CollectionImportToast.AddFailed(entry.card.name)
            )
            closeQueueSheetIfEmpty()
        }
    }

    fun onAddEntryToWishlist(entry: QueuedCard) {
        queueActions.addEntryToWishlist(commitScope, entry, _uiState.value.isAutoDeleteOnAddEnabled) { result ->
            showToast(
                if (result.isSuccess) CollectionImportToast.AddedToWishlist(entry.card.name)
                else CollectionImportToast.AddFailed(entry.card.name)
            )
            closeQueueSheetIfEmpty()
        }
    }

    // Auto-delete-on-add can empty the queue one row at a time; the sheet flag has to follow, or the
    // owned-cards observer keeps collecting for the ViewModel's whole life.
    private fun closeQueueSheetIfEmpty() {
        if (queueRepository.queue.value.isEmpty()) _uiState.update { it.copy(isQueueSheetVisible = false) }
    }

    fun onAddAllToCollection() {
        val entries = queueRepository.queue.value.size
        queueActions.addAllToCollection(commitScope) { result ->
            crashReporter.log("collection_import_committed_collection")
            crashReporter.setCustomKey("collection_import_commit_bucket", transferCountBucket(entries))
            when (result) {
                is AddAllToCollectionResult.Success -> {
                    _uiState.update { it.copy(isQueueSheetVisible = queueRepository.queue.value.isNotEmpty()) }
                    showToast(CollectionImportToast.AddedAllToCollection(result.committedEntries))
                }
                is AddAllToCollectionResult.PartialFailure -> {
                    crashReporter.setCustomKey("collection_import_commit_failed_bucket", transferCountBucket(result.failedEntries))
                    showToast(CollectionImportToast.AddAllPartialFailure(result.failedEntries, result.totalEntries))
                }
            }
        }
    }

    fun onAddAllToWishlist() {
        var failed = 0
        queueActions.addAllToWishlist(
            scope = commitScope,
            onEntryAdded = { _, result -> if (result.isFailure) failed++ },
        ) { total ->
            crashReporter.log("collection_import_committed_wishlist")
            crashReporter.setCustomKey("collection_import_commit_bucket", transferCountBucket(total))
            showToast(
                if (failed == 0) CollectionImportToast.AddedAllToWishlist(total)
                else CollectionImportToast.AddAllPartialFailure(failed = failed, total = total)
            )
        }
    }

    private fun showToast(toast: CollectionImportToast) {
        _uiState.update { it.copy(queueToast = toast) }
    }

    fun onQueueToastShown() {
        _uiState.update { it.copy(queueToast = null) }
    }

    // ── Edit + variant selection ─────────────────────────────────────────────

    fun onEditEntry(entry: QueuedCard) {
        printsLoadJob?.cancel()
        _uiState.update { it.copy(editingEntry = entry, availablePrints = emptyList(), isLoadingPrints = true) }
        printsLoadJob = viewModelScope.launch {
            val result = cardRepository.getCardPrints(entry.card.name)
            _uiState.update { state ->
                if (state.editingEntry?.id != entry.id) return@update state
                if (result is DataResult.Success) state.copy(availablePrints = result.data, isLoadingPrints = false)
                else state.copy(isLoadingPrints = false)
            }
        }
    }

    fun onUpdateEntry(updated: QueuedCard) {
        val original = _uiState.value.editingEntry ?: return
        queueRepository.update(updated.copy(id = original.id))
        onCloseEditSheet()
    }

    fun onCloseEditSheet() {
        printsLoadJob?.cancel()
        _uiState.update { it.copy(editingEntry = null, availablePrints = emptyList(), isLoadingPrints = false) }
    }

    fun onOpenVariantSelector(entry: QueuedCard) {
        variantLoadJob?.cancel()
        _uiState.update { it.copy(variantSelectorEntry = entry, cardVariants = emptyList(), isLoadingVariants = true) }
        variantLoadJob = viewModelScope.launch {
            val result = cardRepository.getCardArtVariants(entry.card.name)
            _uiState.update { state ->
                if (state.variantSelectorEntry?.id != entry.id) return@update state
                if (result is DataResult.Success) state.copy(cardVariants = result.data, isLoadingVariants = false)
                else state.copy(isLoadingVariants = false)
            }
        }
    }

    fun onCloseVariantSelector() {
        variantLoadJob?.cancel()
        _uiState.update { it.copy(variantSelectorEntry = null, cardVariants = emptyList(), isLoadingVariants = false) }
    }

    fun onSelectVariant(variant: Card) {
        val original = _uiState.value.variantSelectorEntry ?: return
        queueRepository.queue.value.firstOrNull { it.id == original.id }?.let { current ->
            queueRepository.update(current.copy(card = variant, setCode = variant.setCode))
        }
        onCloseVariantSelector()
        _uiState.update { state ->
            val editing = state.editingEntry
            if (editing?.id == original.id) {
                state.copy(editingEntry = editing.copy(card = variant, setCode = variant.setCode))
            } else state
        }
    }

    fun onExpandVariantImage(imageUrl: String) {
        if (imageUrl.isBlank()) return
        _uiState.update { it.copy(expandedVariantImageUrl = imageUrl) }
    }

    fun onCloseExpandedImage() {
        _uiState.update { it.copy(expandedVariantImageUrl = null) }
    }

    // The owned-card key set is collection-sized; drop it as soon as the screen is gone, since a
    // commitScope callback can keep this ViewModel referenced past onCleared.
    override fun onCleared() {
        _uiState.update { it.copy(ownedCardIdentityKeys = emptySet()) }
        super.onCleared()
    }

    // Parse/IO messages can echo file content: record the exception type only.
    private fun recordNonFatal(tag: String, e: Throwable) {
        crashReporter.recordException(RuntimeException("[$tag] ${e::class.simpleName}"))
    }

    private enum class ImportSource(val key: String) { PASTE("paste"), FILE("file") }

    companion object {
        const val MAX_FILE_BYTES = 5L * 1024 * 1024

        /** Rows the review queue accepts. Every mutation re-encodes the whole list to storage. */
        const val MAX_IMPORT_QUEUE_ENTRIES = 2_000
    }
}
