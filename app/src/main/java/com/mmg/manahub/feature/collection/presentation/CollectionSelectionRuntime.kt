package com.mmg.manahub.feature.collection.presentation

import androidx.paging.*
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.feature.collection.data.TransferAuthSessionObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicLong

/** A committed collection snapshot and the paging source generation built from it. */
data class CollectionSelectionSnapshot(
    val summary: CollectionSelectionSummary,
    val query: CollectionSelectionQuery,
    val generation: Long,
)

/** Snapshot and Paging data published together so the screen never pairs different generations. */
data class CollectionSelectionRuntimeState(
    val snapshot: CollectionSelectionSnapshot? = null,
    val pagingData: PagingData<CollectionSelectionGroup> = PagingData.empty(),
    val pageReadyGeneration: Long? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/** Paging observes immutable query generations, so stale source results cannot publish under a new owner. */
class CollectionSelectionRuntime(
    private val database: MtgDatabase,
    private val repository: CollectionSelectionRepository,
    private val sessions: TransferSessionGate,
    private val observer: TransferAuthSessionObserver,
    private val invalidations: () -> Flow<Unit> = {
        // Startup capture is explicit; an initial Room emission would recapture on reactivation.
        database.invalidationTracker.createFlow(
            "cards",
            "user_card_collection",
            "local_wishlists",
            "local_open_for_trade",
            emitInitialState = false,
        ).map { Unit }
    },
) {
    private val active = MutableStateFlow(false)
    private val mutableInvalidationVersion = MutableStateFlow(0L)
    private val mutableRetryVersion = MutableStateFlow(0L)
    private val mutableRuntimeState = MutableStateFlow(CollectionSelectionRuntimeState())
    val runtimeState = mutableRuntimeState.asStateFlow()
    private val stateLock = Any()
    private val retiredSnapshots = mutableMapOf<Long, CollectionGeneration>()
    private var currentGeneration: CollectionGeneration? = null

    private val mutableSummary = MutableStateFlow<CollectionSelectionSummary?>(null)
    val summary = mutableSummary.asStateFlow()
    private val mutableSnapshot = MutableStateFlow<CollectionSelectionSnapshot?>(null)
    /** Latest captured summary paired with its query and page generation. */
    val snapshot = mutableSnapshot.asStateFlow()
    private val mutablePageReadyGeneration = MutableStateFlow<Long?>(null)
    /** Generation whose first repository page has completed. */
    val pageReadyGeneration = mutablePageReadyGeneration.asStateFlow()
    private val mutablePaging = MutableStateFlow(PagingData.empty<CollectionSelectionGroup>())
    val paging = mutablePaging.asStateFlow()
    private val mutableLoading = MutableStateFlow(false)
    val loading = mutableLoading.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val snapshotGeneration = AtomicLong(0L)
    var tags: Set<CardTag> = emptySet(); private set

    /** Cards stays active while its back-stack entry remains STARTED under another route. */
    fun setActive(value: Boolean) {
        active.value = value
    }

    /** Repeats the current snapshot capture after a visible collection load failure. */
    fun retry() {
        updateRuntimeState { it.copy(isLoading = true, error = null) }
        mutableRetryVersion.update { it + 1L }
    }

    /** Releases older immutable snapshots after the screen has loaded the current generation. */
    suspend fun confirmGeneration(generation: Long) {
        val retired = synchronized(stateLock) {
            if (mutableRuntimeState.value.snapshot?.generation != generation) {
                emptyList()
            } else {
                retiredSnapshots.keys.filter { it <= generation }
                    .mapNotNull(retiredSnapshots::remove)
            }
        }
        cleanupGenerations(retired)
    }

    private fun updateRuntimeState(
        transform: (CollectionSelectionRuntimeState) -> CollectionSelectionRuntimeState,
    ) {
        synchronized(stateLock) {
            val next = transform(mutableRuntimeState.value)
            mutableRuntimeState.value = next
            mutableSnapshot.value = next.snapshot
            mutableSummary.value = next.snapshot?.summary
            mutablePaging.value = next.pagingData
            mutablePageReadyGeneration.value = next.pageReadyGeneration
            mutableLoading.value = next.isLoading
            mutableError.value = next.error
        }
    }

    private fun clear() {
        updateRuntimeState { CollectionSelectionRuntimeState() }
        tags = emptySet()
    }

    private fun pager(
        owner: TransferOwner,
        id: String,
        captured: TransferSession,
        generation: Long,
        initialPage: CollectionSelectionPage?,
    ) = Pager(
        PagingConfig(
            pageSize = 50,
            initialLoadSize = 50,
            prefetchDistance = 10,
            maxSize = 200,
            enablePlaceholders = false,
        ),
    ) {
        object : PagingSource<Long, CollectionSelectionGroup>() {
            override fun getRefreshKey(state: PagingState<Long, CollectionSelectionGroup>): Long? = null

            override suspend fun load(
                params: LoadParams<Long>,
            ): LoadResult<Long, CollectionSelectionGroup> = try {
                if (sessions.currentSession != captured || !observer.matchesObserved(owner)) {
                    throw TransferReadException(TransferError.OWNER_CHANGED)
                }
                val page = if (params is LoadParams.Refresh && params.key == null && initialPage != null) {
                    initialPage
                } else {
                    repository.page(owner, id, params.key ?: 0L)
                }
                if (sessions.currentSession != captured || !observer.matchesObserved(owner)) {
                    throw TransferReadException(TransferError.OWNER_CHANGED)
                }
                if (params is LoadParams.Refresh) {
                    updateRuntimeState { current ->
                        if (current.snapshot?.generation == generation) {
                            current.copy(pageReadyGeneration = generation)
                        } else {
                            current
                        }
                    }
                }
                val key = params.key ?: 0L
                LoadResult.Page(
                    page.groups,
                    prevKey = if (key == 0L) null else (key - 50L).coerceAtLeast(0L),
                    nextKey = page.nextOrdinal,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                LoadResult.Error(failure)
            }
        }
    }.flow

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start(scope: CoroutineScope, query: Flow<CollectionSelectionQuery>): Job = scope.launch {
        val pagingScope = CoroutineScope(currentCoroutineContext())
        val invalidationJob = launch {
            invalidations().debounce(300).collect {
                mutableInvalidationVersion.update { it + 1L }
            }
        }
        val refreshSignals = combine(mutableInvalidationVersion, mutableRetryVersion) { invalidations, retries ->
            RefreshSignals(invalidations, retries)
        }
        var observedOwner: TransferOwner? = null
        var ownerObserved = false

        try {
            combine(sessions.sessions, observer.identities, query, active, refreshSignals) {
                    session, _, selection, visible, signals ->
                Request(session, selection, visible, signals.invalidationVersion, signals.retryVersion)
            }.collectLatest request@{ request ->
                val captured = request.session
                val currentOwner = (captured as? TransferSession.Available)?.owner
                val ownerMatches = currentOwner != null && observer.matchesObserved(currentOwner)
                val ownerChanged = !ownerObserved || currentOwner != observedOwner
                if (ownerChanged || (currentOwner != null && !ownerMatches)) {
                    cleanupAllGenerations()
                    clear()
                    observedOwner = currentOwner
                    ownerObserved = true
                }

                if (currentOwner == null || !ownerMatches) return@request
                if (!request.active) {
                    updateRuntimeState { it.copy(isLoading = false) }
                    return@request
                }

                val current = synchronized(stateLock) { currentGeneration }
                val needsCapture = current == null || current.owner != currentOwner ||
                    current.query != request.selection ||
                    current.invalidationVersion != request.invalidationVersion ||
                    current.retryVersion != request.retryVersion ||
                    mutableRuntimeState.value.error != null ||
                    mutableRuntimeState.value.isLoading
                if (!needsCapture) return@request
                val sameQueryRefresh = current != null && current.owner == currentOwner &&
                    current.query == request.selection
                val currentState = mutableRuntimeState.value
                if (!sameQueryRefresh || currentState.error != null || currentState.isLoading) {
                    updateRuntimeState { it.copy(isLoading = true, error = null) }
                }

                var candidate: String? = null
                var stagedPagingJob: Job? = null
                var publishPagingData: CompletableDeferred<Unit>? = null
                var published = false

                try {
                    val result = repository.capture(currentOwner, request.selection)
                    candidate = result.id
                    if (sessions.currentSession != captured || !observer.matchesObserved(currentOwner)) {
                        return@request
                    }

                    // Same-query refreshes stage the exact first page the candidate pager will deliver.
                    val initialPage = if (sameQueryRefresh) {
                        repository.page(currentOwner, result.id, 0L).also {
                            if (sessions.currentSession != captured || !observer.matchesObserved(currentOwner)) {
                                return@request
                            }
                        }
                    } else {
                        null
                    }
                    val generation = snapshotGeneration.incrementAndGet()
                    val nextPagingData = CompletableDeferred<PagingData<CollectionSelectionGroup>>()
                    val allowPagingUpdates = CompletableDeferred<Unit>()
                    publishPagingData = allowPagingUpdates
                    stagedPagingJob = pagingScope.launch {
                        var firstEmission = true
                        pager(currentOwner, result.id, captured, generation, initialPage)
                            .cachedIn(this)
                            .collectLatest { data ->
                                if (firstEmission) {
                                    firstEmission = false
                                    nextPagingData.complete(data)
                                    allowPagingUpdates.await()
                                } else if (
                                    sessions.currentSession == captured &&
                                    observer.matchesObserved(currentOwner)
                                ) {
                                    updateRuntimeState { state ->
                                        if (state.snapshot?.generation == generation) {
                                            state.copy(pagingData = data)
                                        } else {
                                            state
                                        }
                                    }
                                }
                            }
                    }
                    val pagingData = nextPagingData.await()
                    if (sessions.currentSession != captured || !observer.matchesObserved(currentOwner)) {
                        return@request
                    }

                    val nextGeneration = CollectionGeneration(
                        owner = currentOwner,
                        snapshotId = result.id,
                        query = request.selection,
                        generation = generation,
                        invalidationVersion = request.invalidationVersion,
                        retryVersion = request.retryVersion,
                        pagingJob = stagedPagingJob,
                    )
                    synchronized(stateLock) {
                        currentGeneration?.let { retiredSnapshots[generation] = it }
                        currentGeneration = nextGeneration
                        updateRuntimeState {
                            CollectionSelectionRuntimeState(
                                snapshot = CollectionSelectionSnapshot(result, request.selection, generation),
                                pagingData = pagingData,
                                pageReadyGeneration = null,
                                isLoading = false,
                                error = null,
                            )
                        }
                    }
                    candidate = null
                    tags = result.tags
                    allowPagingUpdates.complete(Unit)
                    stagedPagingJob = null
                    published = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (sessions.currentSession == captured && observer.matchesObserved(currentOwner)) {
                        updateRuntimeState {
                            it.copy(
                                error = "Collection could not be prepared. Try again.",
                                isLoading = false,
                            )
                        }
                    }
                } finally {
                    if (!published) publishPagingData?.complete(Unit)
                    withContext(NonCancellable + Dispatchers.IO) {
                        try {
                            stagedPagingJob?.cancelAndJoin()
                        } finally {
                            candidate?.let { id -> repository.discard(currentOwner, id) }
                        }
                    }
                }
            }
        } finally {
            withContext(NonCancellable) { invalidationJob.cancelAndJoin() }
            cleanupAllGenerations()
        }
    }

    private suspend fun cleanupAllGenerations() {
        val generations = synchronized(stateLock) {
            buildList {
                currentGeneration?.let { add(it) }
                addAll(retiredSnapshots.values)
                currentGeneration = null
                retiredSnapshots.clear()
            }
        }
        cleanupGenerations(generations)
    }

    private suspend fun cleanupGenerations(generations: List<CollectionGeneration>) {
        if (generations.isEmpty()) return
        withContext(NonCancellable + Dispatchers.IO) {
            generations.forEach { generation ->
                runCatching { generation.pagingJob?.cancelAndJoin() }
                runCatching { repository.discard(generation.owner, generation.snapshotId) }
            }
        }
    }

    private data class CollectionGeneration(
        val owner: TransferOwner,
        val snapshotId: String,
        val query: CollectionSelectionQuery,
        val generation: Long,
        val invalidationVersion: Long,
        val retryVersion: Long,
        val pagingJob: Job?,
    )

    private data class RefreshSignals(
        val invalidationVersion: Long,
        val retryVersion: Long,
    )

    private data class Request(
        val session: TransferSession,
        val selection: CollectionSelectionQuery,
        val active: Boolean,
        val invalidationVersion: Long,
        val retryVersion: Long,
    )
}
