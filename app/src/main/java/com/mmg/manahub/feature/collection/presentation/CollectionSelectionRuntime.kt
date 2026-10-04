package com.mmg.manahub.feature.collection.presentation

import androidx.paging.*
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.feature.collection.data.TransferAuthSessionObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Paging observes immutable query generations, so stale source results cannot publish under a new owner. */
class CollectionSelectionRuntime(
    private val database: MtgDatabase,
    private val repository: CollectionSelectionRepository,
    private val sessions: TransferSessionGate,
    private val observer: TransferAuthSessionObserver,
    private val invalidations: () -> Flow<Unit> = {
        database.invalidationTracker.createFlow("cards", "user_card_collection", "local_wishlists", "local_open_for_trade").map { Unit }
    },
) {
    private val active=MutableStateFlow(false)
    private val mutableSummary=MutableStateFlow<CollectionSelectionSummary?>(null)
    val summary=mutableSummary.asStateFlow()
    private val mutablePaging=MutableStateFlow<PagingData<CollectionSelectionGroup>>(PagingData.empty())
    val paging=mutablePaging.asStateFlow()
    private val mutableLoading=MutableStateFlow(false)
    val loading=mutableLoading.asStateFlow()
    private val mutableError=MutableStateFlow<String?>(null)
    val error=mutableError.asStateFlow()
    var tags: Set<CardTag> = emptySet();private set

    /** Hidden destinations do not rebuild immutable collection snapshots. */
    fun setActive(value: Boolean) { active.value=value }

    private data class Request(val session: TransferSession,val selection: CollectionSelectionQuery,val active: Boolean)

    private fun clear() {
        mutablePaging.value=PagingData.empty();mutableSummary.value=null;tags=emptySet();mutableError.value=null;mutableLoading.value=false
    }

    private fun pager(owner: TransferOwner,id: String,captured: TransferSession) =
        Pager(PagingConfig(pageSize=50,initialLoadSize=50,prefetchDistance=10,maxSize=200,enablePlaceholders=false)) {
            object: PagingSource<Long,CollectionSelectionGroup>() {
                override fun getRefreshKey(state: PagingState<Long,CollectionSelectionGroup>): Long?=null
                override suspend fun load(params: LoadParams<Long>): LoadResult<Long,CollectionSelectionGroup> = try {
                    if(sessions.currentSession!=captured || !observer.matchesObserved(owner))throw TransferReadException(TransferError.OWNER_CHANGED)
                    val page=repository.page(owner,id,params.key ?: 0L)
                    if(sessions.currentSession!=captured || !observer.matchesObserved(owner))throw TransferReadException(TransferError.OWNER_CHANGED)
                    val key=params.key ?: 0L
                    LoadResult.Page(page.groups,prevKey=if(key==0L)null else (key-50L).coerceAtLeast(0L),nextKey=page.nextOrdinal)
                } catch(cancelled: CancellationException) { throw cancelled }
                catch(failure: Exception) { LoadResult.Error(failure) }
            }
        }.flow

    @OptIn(ExperimentalCoroutinesApi::class,FlowPreview::class)
    fun start(scope: CoroutineScope,query: Flow<CollectionSelectionQuery>): Job=scope.launch {
        combine(sessions.sessions,observer.identities,query,active) { session,_,selection,visible -> Request(session,selection,visible) }
            .onEach { clear() }
            .collectLatest request@ { request ->
                val captured=request.session
                val available=(captured as? TransferSession.Available)?.takeIf { request.active && observer.matchesObserved(it.owner) } ?: return@request
                coroutineScope {
                    var snapshot: String?=null
                    var pagingJob: Job?=null
                    try {
                        invalidations().debounce(300).collectLatest refresh@ {
                            mutableLoading.value=mutableSummary.value==null
                            var candidate: String?=null
                            try {
                                val result=repository.capture(available.owner,request.selection)
                                candidate=result.id
                                if(sessions.currentSession!=captured || !observer.matchesObserved(available.owner))return@refresh
                                pagingJob?.cancelAndJoin()
                                val previous=snapshot
                                snapshot=result.id;candidate=null
                                mutableSummary.value=result;tags=result.tags;mutableError.value=null;mutableLoading.value=false
                                pagingJob=launch {
                                    pager(available.owner,result.id,captured).cachedIn(this).collectLatest { data ->
                                        if(sessions.currentSession==captured && observer.matchesObserved(available.owner))mutablePaging.value=data
                                    }
                                }
                                previous?.let { id -> withContext(NonCancellable+Dispatchers.IO) { repository.discard(available.owner,id) } }
                            } catch(cancelled: CancellationException) { throw cancelled }
                            catch(_: Exception) { if(sessions.currentSession==captured && observer.matchesObserved(available.owner)){mutableError.value="Collection could not be prepared. Try again.";mutableLoading.value=false} }
                            finally { candidate?.let { id -> withContext(NonCancellable+Dispatchers.IO) { repository.discard(available.owner,id) } } }
                        }
                    } finally {
                        withContext(NonCancellable+Dispatchers.IO) {
                            pagingJob?.cancelAndJoin()
                            snapshot?.let { id -> repository.discard(available.owner,id) }
                        }
                    }
                }
            }
    }
}
