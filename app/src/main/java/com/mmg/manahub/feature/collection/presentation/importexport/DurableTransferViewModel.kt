package com.mmg.manahub.feature.collection.presentation.importexport

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import android.content.ContentResolver
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.mapper.toDomainCard
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.model.*
import com.mmg.manahub.feature.collection.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class DurableTransferUiState(
    val summary: TransferSummary?=null,
    val entries: List<TransferReviewEntry> = emptyList(),
    val cards: List<QueuedCard> = emptyList(),
    val cursor: TransferPageCursor?=null,
    val loading: Boolean=true,
    val ownerChoice: TransferOwnerChoice?=null,
    val ownerConsent: TransferSession.Available?=null,
    val needsOwnerChoice: Boolean=false,
    val scope: TransferReviewScope=TransferReviewScope.PENDING,
    val deleteOnAdd: Boolean=true,
    val inverted: Boolean=false,
    val publishedInverted: Boolean=false,
    val completionNotice: TransferCompletionNotice?=null,
    val presentationSession: TransferSession.Available?=null,
    val previous: TransferReviewCursor?=null,
    val next: TransferReviewCursor?=null,
    val paging: Boolean=false,
    val error: TransferPresentationFailure?=null,
    val editing: QueuedCard?=null,
    val editingVersion: Long?=null,
    val editingSession: TransferSession.Available?=null,
    val variants: List<Card> = emptyList(),
    val variantLoading: Boolean=false,
    val showVariants: Boolean=false,
    val provenance: List<TransferEntryProvenance> = emptyList(),
    val errors: TransferErrorPreview?=null,
    val decisions: List<TransferPendingReviewDecision> = emptyList(),
    val decisionNames: Map<String,String> = emptyMap(),
    val redirectId: String?=null,
    val expandedImage: String?=null,
    val ownedPrintings: Set<String> = emptySet(),
    val reporting: Boolean=false,
    val reportRecords: Long?=null,
    val inventory: List<TransferFileSummary> = emptyList(),
    val inventoryCursor: String?=null,
)

data class TransferCompletionNotice(val id: String,val destination: TransferDestination,val entries: Long,val copies: Long,val partial: Boolean=false)

private data class PendingTransferNotice(val session: TransferSession.Available,val entries: Long=0L,val copies: Long=0L)

data class TransferDiscardConsent(val generation: Long,val payloadVersion: Long,val session: TransferSession.Available)

/** Review hydrates one bounded page and persists each deliberate mutation before refreshing it. */
@OptIn(ExperimentalCoroutinesApi::class)
class DurableTransferViewModel(
    val id: TransferJobId,
    private val repository: CollectionTransferRepository,
    private val database: MtgDatabase,
    private val sessions: TransferSessionGate,
    private val observer: TransferAuthSessionObserver,
    private val auth: AuthRepository,
    private val cardRepository: CardRepository,
    private val files: AndroidCollectionTransferFileStore,
    private val resolver: ContentResolver,
    private val reportWriter: AndroidTransferReportWriter,
    private val savedState: SavedStateHandle=SavedStateHandle(),
) : ViewModel() {
    private fun initialState()=DurableTransferUiState(deleteOnAdd=savedState["deleteOnAdd"] ?: true,inverted=savedState["inverted"] ?: false,scope=if(savedState.get<Boolean>("deleteOnAdd")!=false)TransferReviewScope.PENDING else TransferReviewScope.QUEUE)
    private val mutableState=MutableStateFlow(initialState())
    val state=mutableState.asStateFlow()
    private val mutations=Mutex()
    private var owner: TransferOwner?=null
    private var windowStart: String?=null
    private var windowPages=1
    private var readEpoch=0L
    private var pendingWindow: Pair<String?,Int>?=null
    private val pendingActionNotices=mutableMapOf<TransferActionId,PendingTransferNotice>()
    init {
        viewModelScope.launch {
            combine(sessions.sessions,auth.sessionState) { session,_ -> session }.collectLatest { session ->
                owner=null
                pendingActionNotices.clear()
                readEpoch++
                pendingWindow=null
                windowStart=null
                windowPages=1
                mutableState.value=initialState()
                val available=(session as? TransferSession.Available)?.takeIf { observer.matchesObserved(it.owner) } ?: return@collectLatest
                owner=available.owner
                mutableState.update { it.copy(presentationSession=available) }
                database.collectionTransferDao().observeReceipt(id.value).collectLatest { receipt ->
                    if(receipt==null || receipt.phase=="RECEIVING")return@collectLatest
                    val existing=database.collectionTransferDao().getJob(id.value,available.owner.storageKey())
                    if(existing==null) {
                        val result=repository.bindReceipt(id,available.owner,available.generation,TransferOrigin.SHARE,observer.canBindInitialReceipt(id,receipt.authGeneration,available))
                        if(result==TransferMutationResult.Accepted || result is TransferMutationResult.AlreadyReceived)observer.consumedReceipt(id)
                        if(result is TransferMutationResult.AlreadyReceived) {
                            mutableState.update { it.copy(loading=false,redirectId=result.id.value) }
                            return@collectLatest
                        }
                        if(result!=TransferMutationResult.Accepted) {
                            val failure=(result as? TransferMutationResult.Rejected)?.error
                            val choice=if(failure==TransferError.OWNER_CHANGED) {
                                when(receipt.capturedOwner) {
                                    null -> TransferOwnerChoice.NEUTRAL_RECEIPT
                                    available.owner.storageKey() -> TransferOwnerChoice.SESSION_CHANGED
                                    else -> TransferOwnerChoice.OTHER_ACCOUNT
                                }
                            } else null
                            mutableState.update { it.copy(loading=false,ownerChoice=choice,ownerConsent=available,needsOwnerChoice=choice!=null,
                                error=if(choice==null) { if(failure==TransferError.STORAGE_FAILURE)TransferPresentationFailure.STORAGE else TransferPresentationFailure.RECEIVE } else null) }
                            return@collectLatest
                        }
                    }
                    repository.observeSummary(id,available.owner).collectLatest { summary ->
                        val incompatible=state.value.summary?.generation!=summary?.generation
                        if(incompatible) { readEpoch++;windowStart=null;windowPages=1;pendingWindow=null;mutableState.update { it.copy(entries=emptyList(),cards=emptyList(),previous=null,next=null) } }
                        mutableState.update { it.copy(summary=summary,loading=summary==null,needsOwnerChoice=false,ownerChoice=null) }
                        if(summary!=null) {
                            checkCompletions()
                            mutations.withLock { refresh(available.owner,null) }
                        }
                    }
                }
            }
        }
    }
    private fun currentOwner()=owner?.takeIf { observer.matchesObserved(it) && (sessions.currentSession as? TransferSession.Available)?.owner==it }
    private suspend fun publish(captured: TransferOwner, entries: List<TransferReviewEntry>, previous: TransferReviewCursor?, next: TransferReviewCursor?, capturedSession: TransferSession, scope: TransferReviewScope, epoch: Long, generation: Long) {
        val metadata=withContext(Dispatchers.IO) { database.cardDao().getByIds(entries.map { it.scryfallId }.distinct()).associate { it.scryfallId to it.toDomainCard() } }
        val visible=entries.mapNotNull { entry -> metadata[entry.scryfallId]?.let { card -> QueuedCard(card,entry.quantity.coerceIn(1L,Int.MAX_VALUE.toLong()).toInt(),entry.isFoil,entry.language,entry.condition,card.setCode,0L,entry.id) } }
        val owned=withContext(Dispatchers.IO) { database.collectionTransferDao().ownedReviewPrintings(captured.storageKey(),entries.map { it.scryfallId }.distinct()).toSet() }
        if(currentOwner()==captured && sessions.currentSession==capturedSession && state.value.scope==scope && readEpoch==epoch && state.value.summary?.generation==generation)
            mutableState.update { it.copy(entries=entries,cards=visible,previous=previous,next=next,loading=false,paging=false,ownedPrintings=owned,publishedInverted=it.inverted) }
    }
    private suspend fun readOrderedPage(captured: TransferOwner, cursor: TransferReviewCursor?, scope: TransferReviewScope, backward: Boolean=false): TransferReviewPage {
        val inverted=state.value.inverted
        val direction=if(backward != inverted)TransferPageDirection.BACKWARD else TransferPageDirection.FORWARD
        val page=repository.readReviewPage(id,captured,cursor,scope,direction)
        return if(inverted)TransferReviewPage(page.entries.asReversed(),page.next,page.previous) else page
    }
    fun toggleInversion() {
        val captured=currentOwner() ?: return
        savedState["inverted"]=!state.value.inverted
        readEpoch++;windowStart=null;windowPages=1;pendingWindow=null
        mutableState.update { it.copy(inverted=!it.inverted,entries=it.entries.asReversed(),cards=it.cards.asReversed(),previous=it.next,next=it.previous,paging=true) }
        viewModelScope.launch { mutations.withLock { refresh(captured,null) } }
    }
    fun setDeleteOnAdd(enabled: Boolean) {
        if(state.value.deleteOnAdd==enabled)return
        savedState["deleteOnAdd"]=enabled
        mutableState.update { it.copy(deleteOnAdd=enabled) }
        changeScope(if(enabled)TransferReviewScope.PENDING else TransferReviewScope.QUEUE)
    }
    private suspend fun refresh(captured: TransferOwner,cursor: TransferPageCursor?) {
        val session=sessions.currentSession
        val scope=state.value.scope
        val epoch=++readEpoch
        try {
            val generation=state.value.summary?.generation ?: return
            var page=readOrderedPage(captured,windowStart?.let { TransferReviewCursor(id,generation,scope,it) },scope)
            var entries=page.entries
            val previous=page.previous
            repeat(windowPages-1) {
                page.next?.let { next -> page=readOrderedPage(captured,next,scope);entries=entries+page.entries }
            }
            val decisions=repository.readDecisions(id,captured,"")
            val names=withContext(Dispatchers.IO) { database.cardDao().getByIds(decisions.map { it.entry.scryfallId }.distinct()).associate { it.scryfallId to it.name } }
            if(sessions.currentSession==session && readEpoch==epoch)mutableState.update { it.copy(decisions=decisions,decisionNames=decisions.associate { it.entryId to (names[it.entry.scryfallId] ?: it.entry.scryfallId) }) }
            publish(captured,entries,previous,page.next,session,scope,epoch,generation)
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { if(currentOwner()==captured && sessions.currentSession==session)mutableState.update { it.copy(loading=false,paging=false,error=TransferPresentationFailure.REVIEW_CHANGED) } }
    }
    fun changeScope(scope: TransferReviewScope) {
        if(state.value.scope==scope)return
        val captured=currentOwner() ?: return
        readEpoch++
        if(state.value.scope==TransferReviewScope.PENDING)pendingWindow=windowStart to windowPages
        val restored=if(scope==TransferReviewScope.PENDING)pendingWindow else null
        windowStart=restored?.first;windowPages=restored?.second ?: 1
        mutableState.update { it.copy(scope=scope,entries=emptyList(),cards=emptyList(),previous=null,next=null,loading=true) }
        viewModelScope.launch { mutations.withLock { refresh(captured,null) } }
    }
    fun nextPage()=loadPage(false)
    fun previousPage()=loadPage(true)
    private fun loadPage(backward: Boolean) {
        val captured=currentOwner() ?: return
        if(state.value.paging)return
        val inverted=state.value.inverted
        val cursor=(if(backward)state.value.previous else state.value.next) ?: return
        val session=sessions.currentSession
        val scope=state.value.scope
        mutableState.update { it.copy(paging=true) }
        viewModelScope.launch { mutations.withLock {
            try {
                if(sessions.currentSession!=session || state.value.scope!=scope || state.value.inverted!=inverted)return@withLock
                val epoch=++readEpoch
                val page=readOrderedPage(captured,cursor,scope,backward)
                val old=state.value.entries
                val combined=(if(backward)page.entries+old else old+page.entries).distinctBy { it.id }
                val entries=if(backward)combined.take(200) else combined.takeLast(200)
                windowPages=((entries.size+49)/50).coerceAtLeast(1)
                if(backward)windowStart=page.previous?.anchorId?.let { entries.firstOrNull()?.id }?.let { anchor ->
                    val before=readOrderedPage(captured,TransferReviewCursor(id,cursor.generation,scope,anchor),scope,true)
                    before.entries.lastOrNull()?.id
                } else if(combined.size>200)windowStart=combined[combined.size-201].id
                val previous=if(backward)page.previous else if(combined.size>200)TransferReviewCursor(id,cursor.generation,scope,entries.first().id) else state.value.previous
                val next=if(backward && combined.size>200)TransferReviewCursor(id,cursor.generation,scope,entries.last().id) else if(backward)state.value.next else page.next
                publish(captured,entries,previous,next,session,scope,epoch,cursor.generation)
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { if(sessions.currentSession==session)mutableState.update { it.copy(paging=false,error=TransferPresentationFailure.PAGE) } }
        } }
    }
    fun firstPage() { val captured=currentOwner() ?: return;windowStart=null;windowPages=1;viewModelScope.launch { mutations.withLock { refresh(captured,null) } } }
    fun duplicate(card: QueuedCard) {
        val displayed=state.value.entries.firstOrNull { it.id==card.id } ?: return
        val displayedSummary=state.value.summary ?: return
        val consent=captureSession() ?: return
        if(displayed.state!="PENDING" || displayed.activeActionId!=null || displayed.quantity !in 1L..Int.MAX_VALUE.toLong())return
        mutate { owner,summary ->
            if(sessions.currentSession!=consent || summary.generation!=displayedSummary.generation || summary.payloadVersion!=displayedSummary.payloadVersion)return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
            repository.duplicatePendingEntry(id,owner,displayedSummary.generation,displayedSummary.payloadVersion,displayed.id,displayed.version,UUID.randomUUID().toString())
        }
    }
    private suspend fun checkCompletions() {
        val captured=currentOwner() ?: return
        val capturedSession=captureSession() ?: return
        val attempts=pendingActionNotices.filterValues { it.session==capturedSession }.toMap()
        for((actionId,notified) in attempts) {
            try {
                val action=withContext(Dispatchers.IO) { database.collectionTransferDao().action(actionId.value,captured.storageKey()) } ?: continue
                if(action.phase !in setOf("COMPLETED","REVIEW_REQUIRED","FAILED_RETRYABLE","INVALIDATED","DISCARDED"))continue
                val completed=repository.readAction(captured,actionId)
                if(currentOwner()!=captured || sessions.currentSession!=capturedSession)return
                if(pendingActionNotices[actionId]!=notified)continue
                val addedEntries=completed.completedEntries-notified.entries
                val addedCopies=completed.completedCopies-notified.copies
                if(addedEntries>0L && addedCopies>0L) {
                    val partial=completed.completedEntries!=completed.entries || completed.completedCopies!=completed.copies
                    pendingActionNotices[actionId]=notified.copy(entries=completed.completedEntries,copies=completed.completedCopies)
                    mutableState.update { it.copy(completionNotice=TransferCompletionNotice(UUID.randomUUID().toString(),completed.destination,addedEntries,addedCopies,partial)) }
                }
                if(action.phase in setOf("COMPLETED","INVALIDATED","DISCARDED"))pendingActionNotices.remove(actionId)
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { }
        }
    }
    fun clearCompletionNotice() { mutableState.update { it.copy(completionNotice=null) } }
    fun details() {
        errors()
        val session=sessions.currentSession
        readBounded { captured ->
            val decisions=repository.readDecisions(id,captured,"")
            if(sessions.currentSession==session)mutableState.update { it.copy(decisions=decisions) }
        }
    }
    fun captureSession(): TransferSession.Available?=(sessions.currentSession as? TransferSession.Available)?.takeIf { currentOwner()==it.owner }
    private fun mutate(operation: suspend (TransferOwner,TransferSummary)->TransferMutationResult) {
        val captured=currentOwner() ?: return
        val capturedSession=sessions.currentSession
        viewModelScope.launch { mutations.withLock {
            val summary=state.value.summary ?: return@withLock
            if(currentOwner()!=captured || sessions.currentSession!=capturedSession)return@withLock
            val result=try { coroutineScope {
                val operationScope=this
                val watcher=launch(start=CoroutineStart.UNDISPATCHED) {
                    combine(sessions.sessions,observer.identities) { session,_ -> session }.collect { current ->
                        if(current!=capturedSession || !observer.matchesObserved(captured))operationScope.cancel("Transfer owner changed")
                    }
                }
                try { operation(captured,summary) } finally { watcher.cancel() }
            } } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { TransferMutationResult.Rejected(TransferError.STORAGE_FAILURE) }
            if(currentOwner()==captured && sessions.currentSession==capturedSession) {
                if(result is TransferMutationResult.Rejected)mutableState.update { it.copy(error=when(result.error) { TransferError.QUANTITY_OVERFLOW->TransferPresentationFailure.QUANTITY_OVERFLOW;TransferError.VARIANT_COLLISION->TransferPresentationFailure.VARIANT_COLLISION;TransferError.STORAGE_FAILURE->TransferPresentationFailure.STORAGE;else->TransferPresentationFailure.REVIEW_CHANGED }) }
                refresh(captured,null)
            }
        } }
    }
    fun bindExplicitly(available: TransferSession.Available) {
        if(sessions.currentSession!=available || !observer.matchesObserved(available.owner))return
        viewModelScope.launch {
            val result=repository.bindReceipt(id,available.owner,available.generation,TransferOrigin.SHARE,true)
            if(sessions.currentSession!=available || !observer.matchesObserved(available.owner))return@launch
            when(result) {
                is TransferMutationResult.AlreadyReceived -> mutableState.update { it.copy(redirectId=result.id.value) }
                is TransferMutationResult.Rejected -> mutableState.update { it.copy(error=TransferPresentationFailure.OWNER) }
                TransferMutationResult.Accepted -> Unit
            }
        }
    }
    fun selectFile(file: TransferFileSummary)=mutate { owner,summary -> repository.selectFile(id,owner,file.id,summary.generation,!file.selected) }
    fun includeRepeated(file: TransferFileSummary)=mutate { owner,summary -> repository.includeRepeatedFile(id,owner,file.id,summary.generation) }
    fun replace(file: TransferFileSummary,generation: Long,uri: Uri,consent: TransferSession.Available)=mutate { owner,summary ->
        if(sessions.currentSession!=consent || summary.generation!=generation)return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
        if(files.replaceSource(id,owner,summary.generation,file.id,ContentTransferInputSource(resolver,uri)))repository.resume(id,owner) else TransferMutationResult.Rejected(TransferError.STORAGE_FAILURE)
    }
    fun writeReport(uri: Uri, consent: TransferSession.Available?=null) {
        if(consent!=null && sessions.currentSession!=consent)return
        val captured=currentOwner() ?: return
        val capturedSession=sessions.currentSession
        viewModelScope.launch {
            mutableState.update { it.copy(reporting=true) }
            try {
                val count=reportWriter.write(id,captured,uri,consent)
                if(currentOwner()==captured && sessions.currentSession==capturedSession)mutableState.update { it.copy(reportRecords=count) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { if(sessions.currentSession==capturedSession)mutableState.update { it.copy(error=TransferPresentationFailure.REPORT) } }
            finally { if(sessions.currentSession==capturedSession)mutableState.update { it.copy(reporting=false) } }
        }
    }
    fun pause()=mutate { owner,_ -> repository.pause(id,owner) }
    fun resume()=mutate { owner,_ -> repository.resume(id,owner) }
    fun captureDiscard(): TransferDiscardConsent? {
        val summary=state.value.summary ?: return null
        val session=sessions.currentSession as? TransferSession.Available ?: return null
        if(currentOwner()!=session.owner)return null
        return TransferDiscardConsent(summary.generation,summary.payloadVersion,session)
    }
    fun discard(consent: TransferDiscardConsent)=mutate { owner,summary ->
        if(sessions.currentSession!=consent.session || summary.generation!=consent.generation || summary.payloadVersion!=consent.payloadVersion)
            TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
        else repository.discardPending(id,owner,consent.generation,consent.payloadVersion).also { result ->
            if(result==TransferMutationResult.Accepted) {
                savedState["deleteOnAdd"]=true
                windowStart=null;windowPages=1;pendingWindow=null
                mutableState.update { it.copy(deleteOnAdd=true,scope=TransferReviewScope.PENDING,entries=emptyList(),cards=emptyList(),previous=null,next=null) }
            }
        }
    }
    fun edit(card: QueuedCard) { mutableState.update { it.copy(editing=card,editingVersion=it.entries.firstOrNull { entry -> entry.id==card.id }?.version,editingSession=captureSession(),variants=emptyList(),showVariants=false) }; loadVariants(card) }
    private fun loadVariants(card: QueuedCard) {
        val captured=currentOwner() ?: return
        val capturedSession=sessions.currentSession
        viewModelScope.launch {
            mutableState.update { it.copy(variantLoading=true) }
            try {
                val result=cardRepository.getCardArtVariants(card.card.name)
                if(currentOwner()==captured && sessions.currentSession==capturedSession && state.value.editing?.id==card.id)mutableState.update { it.copy(variants=(result as? DataResult.Success)?.data ?: emptyList(),variantLoading=false) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { if(sessions.currentSession==capturedSession)mutableState.update { it.copy(variantLoading=false,error=TransferPresentationFailure.VARIANTS) } }
        }
    }
    fun closeEdit() { mutableState.update { it.copy(editing=null,editingVersion=null,editingSession=null,showVariants=false) } }
    fun showVariants() { mutableState.update { it.copy(showVariants=true) } }
    fun hideVariants() { mutableState.update { it.copy(showVariants=false) } }
    fun selectVariant(card: Card) { mutableState.update { state -> state.copy(editing=state.editing?.copy(card=card,setCode=card.setCode),showVariants=false) } }
    fun expandImage(image: String) { mutableState.update { it.copy(expandedImage=image) } }
    fun closeImage() { mutableState.update { it.copy(expandedImage=null) } }
    fun update(card: QueuedCard)=mutate { owner,summary ->
        var entry=state.value.entries.firstOrNull { it.id==card.id } ?: return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
        if(state.value.editing?.id==card.id && (state.value.editingVersion!=entry.version || state.value.editingSession!=sessions.currentSession))return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
        if(entry.quantity>Int.MAX_VALUE.toLong())return@mutate TransferMutationResult.Rejected(TransferError.QUANTITY_OVERFLOW)
        if(entry.state=="WISHLIST_APPLIED") {
            val reopened=repository.reopenWishlistEntry(id,owner,entry.id,entry.version,summary.payloadVersion)
            if(reopened!=TransferMutationResult.Accepted)return@mutate reopened
            entry=readEntry(owner,entry.id) ?: return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
        }
        val result=repository.editPendingEntry(id,owner,summary.generation,entry.copy(scryfallId=card.card.scryfallId,quantity=card.quantity.toLong(),isFoil=card.isFoil,condition=card.condition,language=card.language))
        if(result==TransferMutationResult.Accepted)closeEdit()
        result
    }
    fun exclude(card: QueuedCard)=mutate { owner,summary -> val entry=state.value.entries.first { it.id==card.id }; repository.editPendingEntry(id,owner,summary.generation,entry.copy(excluded=true)) }
    fun adjust(card: QueuedCard,delta: Int)=mutate { owner,summary ->
        val entry=readEntry(owner,card.id) ?: return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
        if(entry.quantity>Int.MAX_VALUE.toLong())return@mutate TransferMutationResult.Rejected(TransferError.QUANTITY_OVERFLOW)
        val next=entry.quantity+delta
        if(next !in 1L..Int.MAX_VALUE.toLong())return@mutate TransferMutationResult.Rejected(TransferError.QUANTITY_OVERFLOW)
        repository.editPendingEntry(id,owner,summary.generation,entry.copy(quantity=next))
    }
    fun correctQuantity(entryId: String,version: Long,quantity: Long,consent: TransferSession.Available?=null)=mutate { owner,summary ->
        if(consent!=null && sessions.currentSession!=consent)return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
        val entry=state.value.entries.firstOrNull { it.id==entryId && it.version==version } ?: return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
        if(quantity !in 1L..Int.MAX_VALUE.toLong())return@mutate TransferMutationResult.Rejected(TransferError.QUANTITY_OVERFLOW)
        repository.editPendingEntry(id,owner,summary.generation,entry.copy(quantity=quantity))
    }
    private suspend fun readEntry(owner: TransferOwner,entryId: String): TransferReviewEntry? {
        val value=database.collectionTransferDao().reviewEntry(id.value,owner.storageKey(),entryId) ?: return null
        return TransferReviewEntry(value.id,value.scryfallId,value.isFoil,value.condition,value.language,value.quantity,value.appliedQuantity,value.excluded,null,TransferDestination.valueOf(value.destination),value.entryVersion,value.activeActionId?.let(::TransferActionId),value.state)
    }
    fun action(destination: TransferDestination,entryId: String?,generation: Long,payloadVersion: Long,entryVersion: Long?,acceptExclusions: Boolean,acceptRepeats: Boolean,consentSession: TransferSession.Available?=null,followWishlist: Boolean=false)=mutate { owner,summary ->
        val originalSession=captureSession() ?: return@mutate TransferMutationResult.Rejected(TransferError.OWNER_CHANGED)
        val durable=withContext(Dispatchers.IO) { database.collectionTransferDao().getJob(id.value,owner.storageKey()) }
        if(durable?.generation!=generation || durable.payloadVersion!=payloadVersion)return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
        if((consentSession!=null && sessions.currentSession!=consentSession) || summary.generation!=generation || summary.payloadVersion!=payloadVersion || (entryId!=null && state.value.entries.firstOrNull { it.id==entryId }?.version!=entryVersion))return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
        var expectedPayload=payloadVersion
        var expectedRevision=durable.intentRevision
        var expectedEntryVersion=entryVersion
        val chosen=if(entryId==null) {
            if(destination==TransferDestination.COLLECTION && followWishlist) {
                if(summary.retainedWishlistEntries==0L)return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
                val reopened=repository.reopenWishlistSelection(id,owner,generation,payloadVersion)
                if(reopened!=TransferMutationResult.Accepted)return@mutate reopened
                expectedPayload++;expectedRevision++
            }
            val result=repository.chooseDestination(id,owner,generation,expectedRevision,destination)
            if(result==TransferMutationResult.Accepted){expectedPayload++;expectedRevision++}
            result
        } else {
            var entry=state.value.entries.firstOrNull { it.id==entryId } ?: return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
            if(entry.state=="WISHLIST_APPLIED") {
                if(destination!=TransferDestination.COLLECTION)return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
                val reopened=repository.reopenWishlistEntry(id,owner,entry.id,entry.version,payloadVersion)
                if(reopened!=TransferMutationResult.Accepted)return@mutate reopened
                val reopenedEntry=readEntry(owner,entryId) ?: return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
                if(reopenedEntry.version!=entry.version+1L)return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
                entry=reopenedEntry
                expectedPayload++;expectedRevision++
            }
            val result=repository.editPendingEntry(id,owner,generation,entry.copy(destination=destination))
            if(result==TransferMutationResult.Accepted){expectedPayload++;expectedRevision++;expectedEntryVersion=entry.version+1L}
            result
        }
        if(chosen!=TransferMutationResult.Accepted)return@mutate chosen
        val scope=if(entryId==null)TransferActionScope.DestinationSelection(expectedRevision)
            else TransferActionScope.Entry(entryId,expectedEntryVersion ?: return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED))
        val actionId=TransferActionId(UUID.randomUUID().toString())
        val result=repository.confirmAction(owner,TransferActionRequest(actionId,id,generation,destination,scope,acceptExclusions,acceptRepeats,expectedPayloadVersion=expectedPayload))
        if(result==TransferMutationResult.Accepted && sessions.currentSession==originalSession && currentOwner()==owner) {
            pendingActionNotices[actionId]=PendingTransferNotice(originalSession)
            checkCompletions()
        }
        result
    }

    fun decision(value: TransferPendingReviewDecision,choice: TransferReviewDecision)=mutate { owner,summary ->
        if(value.generation!=summary.generation)TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
        else repository.decideReviewEdit(id,owner,value.entryId,value.generation,choice)
    }
    private fun readBounded(operation: suspend (TransferOwner)->Unit) {
        val captured=currentOwner() ?: return
        val capturedSession=sessions.currentSession
        viewModelScope.launch {
            try { operation(captured) }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { if(currentOwner()==captured && sessions.currentSession==capturedSession)mutableState.update { it.copy(error=TransferPresentationFailure.UNAVAILABLE) } }
        }
    }
    fun inspect(entryId: String) {
        val capturedSession=sessions.currentSession
        readBounded { captured -> val result=repository.readProvenance(id,captured,entryId);if(currentOwner()==captured && sessions.currentSession==capturedSession)mutableState.update { it.copy(provenance=result) } }
    }
    fun errors() {
        val capturedSession=sessions.currentSession
        readBounded { captured -> val result=repository.readErrorPreview(id,captured);if(currentOwner()==captured && sessions.currentSession==capturedSession)mutableState.update { it.copy(errors=result) } }
    }
    fun inventory(next: Boolean=false) {
        val captured=currentOwner() ?: return
        val capturedSession=sessions.currentSession
        val cursor=if(next)state.value.inventoryCursor ?: return else ""
        readBounded {
            val page=repository.readFileInventory(id,captured,cursor)
            if(currentOwner()==captured && sessions.currentSession==capturedSession)mutableState.update { it.copy(inventory=page.files,inventoryCursor=page.nextAfterId) }
        }
    }
    fun clearError() { mutableState.update { it.copy(error=null) } }
    fun clearNotice() { mutableState.update { it.copy(reportRecords=null) } }
}
