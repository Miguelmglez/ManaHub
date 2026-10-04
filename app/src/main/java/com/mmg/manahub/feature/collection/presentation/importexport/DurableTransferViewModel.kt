package com.mmg.manahub.feature.collection.presentation.importexport

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
    val needsOwnerChoice: Boolean=false,
    val error: String?=null,
    val editing: QueuedCard?=null,
    val editingVersion: Long?=null,
    val variants: List<Card> = emptyList(),
    val variantLoading: Boolean=false,
    val showVariants: Boolean=false,
    val provenance: List<TransferEntryProvenance> = emptyList(),
    val errors: TransferErrorPreview?=null,
    val decisions: List<TransferPendingReviewDecision> = emptyList(),
    val redirectId: String?=null,
    val expandedImage: String?=null,
    val ownedPrintings: Set<String> = emptySet(),
    val reporting: Boolean=false,
    val notice: String?=null,
    val inventory: List<TransferFileSummary> = emptyList(),
    val inventoryCursor: String?=null,
)

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
) : ViewModel() {
    private val mutableState=MutableStateFlow(DurableTransferUiState())
    val state=mutableState.asStateFlow()
    private val mutations=Mutex()
    private var owner: TransferOwner?=null
    init {
        viewModelScope.launch {
            combine(sessions.sessions,auth.sessionState) { session,_ -> session }.collectLatest { session ->
                owner=null
                mutableState.value=DurableTransferUiState()
                val available=(session as? TransferSession.Available)?.takeIf { observer.matchesObserved(it.owner) } ?: return@collectLatest
                owner=available.owner
                database.collectionTransferDao().observeReceipt(id.value).collectLatest { receipt ->
                    if(receipt==null || receipt.phase=="RECEIVING")return@collectLatest
                    val existing=database.collectionTransferDao().getJob(id.value,available.owner.storageKey())
                    if(existing==null) {
                        val result=repository.bindReceipt(id,available.owner,available.generation,TransferOrigin.SHARE,observer.canBindInitialReceipt(id,receipt.authGeneration,available))
                        if(result==TransferMutationResult.Accepted || result is TransferMutationResult.AlreadyReceived)observer.consumedReceipt(id)
                        if(result==TransferMutationResult.Accepted || result is TransferMutationResult.AlreadyReceived)observer.consumedReceipt(id)
                        if(result is TransferMutationResult.AlreadyReceived) {
                            mutableState.update { it.copy(loading=false,redirectId=result.id.value) }
                            return@collectLatest
                        }
                        if(result!=TransferMutationResult.Accepted) {
                            mutableState.update { it.copy(loading=false,needsOwnerChoice=receipt.capturedOwner==null,error=if(receipt.capturedOwner==null)"These files arrived before your current session. Review them here to continue." else "Sign in to the account that received these files to continue.") }
                            return@collectLatest
                        }
                    }
                    repository.observeSummary(id,available.owner).collectLatest { summary ->
                        mutableState.update { it.copy(summary=summary,entries=emptyList(),cards=emptyList(),cursor=null,loading=summary==null,needsOwnerChoice=false) }
                        if(summary!=null)refresh(available.owner,null)
                    }
                }
            }
        }
    }
    private fun currentOwner()=owner?.takeIf { observer.matchesObserved(it) && (sessions.currentSession as? TransferSession.Available)?.owner==it }
    private suspend fun refresh(captured: TransferOwner,cursor: TransferPageCursor?) {
        val capturedSession=sessions.currentSession
        try {
            val page=repository.readPage(id,captured,cursor)
            val cards=withContext(Dispatchers.Default) { database.cardDao().getByIds(page.entries.map { it.scryfallId }.distinct()).associate { it.scryfallId to it.toDomainCard() } }
            val visible=page.entries.mapNotNull { entry -> cards[entry.scryfallId]?.let { card -> QueuedCard(card,entry.quantity.coerceIn(1L,Int.MAX_VALUE.toLong()).toInt(),entry.isFoil,entry.language,entry.condition,card.setCode,0L,entry.id) } }
            val decisions=repository.readDecisions(id,captured,"")
            val owned=database.collectionTransferDao().ownedReviewPrintings(captured.storageKey(),page.entries.map { it.scryfallId }.distinct()).toSet()
            if(currentOwner()==captured && sessions.currentSession==capturedSession)mutableState.update { it.copy(entries=page.entries,cards=visible,cursor=page.next,loading=false,decisions=decisions,ownedPrintings=owned) }
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { if(currentOwner()==captured && sessions.currentSession==capturedSession)mutableState.update { it.copy(loading=false,error="Review changed. Reload the transfer.") } }
    }
    fun nextPage() { val captured=currentOwner() ?: return; val cursor=state.value.cursor ?: return; viewModelScope.launch { refresh(captured,cursor) } }
    fun firstPage() { val captured=currentOwner() ?: return; viewModelScope.launch { refresh(captured,null) } }
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
                if(result is TransferMutationResult.Rejected)mutableState.update { it.copy(error=when(result.error) { TransferError.QUANTITY_OVERFLOW->"Choose a quantity from 1 to 2147483647 explicitly, or exclude this entry. Original copies are retained.";TransferError.VARIANT_COLLISION->"This printing and these attributes already belong to another entry. Choose another variant. Both decisions and all source copies are retained.";else->"The review changed or this action is unavailable. Reload before trying again." }) }
                refresh(captured,null)
            }
        } }
    }
    fun bindExplicitly() {
        val available=sessions.currentSession as? TransferSession.Available ?: return
        viewModelScope.launch {
            val result=repository.bindReceipt(id,available.owner,available.generation,TransferOrigin.SHARE,true)
            if(sessions.currentSession!=available || !observer.matchesObserved(available.owner))return@launch
            when(result) {
                is TransferMutationResult.AlreadyReceived -> mutableState.update { it.copy(redirectId=result.id.value) }
                is TransferMutationResult.Rejected -> mutableState.update { it.copy(error="These files cannot be reviewed in this session.") }
                TransferMutationResult.Accepted -> Unit
            }
        }
    }
    fun selectFile(file: TransferFileSummary)=mutate { owner,summary -> repository.selectFile(id,owner,file.id,summary.generation,!file.selected) }
    fun includeRepeated(file: TransferFileSummary)=mutate { owner,summary -> repository.includeRepeatedFile(id,owner,file.id,summary.generation) }
    fun replace(file: TransferFileSummary,generation: Long,uri: Uri)=mutate { owner,summary ->
        if(summary.generation!=generation)return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
        if(files.replaceSource(id,owner,summary.generation,file.id,ContentTransferInputSource(resolver,uri)))repository.resume(id,owner) else TransferMutationResult.Rejected(TransferError.STORAGE_FAILURE)
    }
    fun writeReport(uri: Uri) {
        val captured=currentOwner() ?: return
        val capturedSession=sessions.currentSession
        viewModelScope.launch {
            mutableState.update { it.copy(reporting=true) }
            try {
                val count=reportWriter.write(id,captured,uri)
                if(currentOwner()==captured && sessions.currentSession==capturedSession)mutableState.update { it.copy(notice="Report saved: $count source records.") }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { if(sessions.currentSession==capturedSession)mutableState.update { it.copy(error="The report was not completed. Delete the partial document and try again.") } }
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
        else repository.discardPending(id,owner,consent.generation,consent.payloadVersion)
    }
    fun edit(card: QueuedCard) { mutableState.update { it.copy(editing=card,editingVersion=it.entries.firstOrNull { entry -> entry.id==card.id }?.version,variants=emptyList(),showVariants=false) }; loadVariants(card) }
    private fun loadVariants(card: QueuedCard) {
        val captured=currentOwner() ?: return
        val capturedSession=sessions.currentSession
        viewModelScope.launch {
            mutableState.update { it.copy(variantLoading=true) }
            try {
                val result=cardRepository.getCardArtVariants(card.card.name)
                if(currentOwner()==captured && sessions.currentSession==capturedSession && state.value.editing?.id==card.id)mutableState.update { it.copy(variants=(result as? DataResult.Success)?.data ?: emptyList(),variantLoading=false) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { if(sessions.currentSession==capturedSession)mutableState.update { it.copy(variantLoading=false,error="Variants could not be loaded. Try again.") } }
        }
    }
    fun closeEdit() { mutableState.update { it.copy(editing=null,editingVersion=null,showVariants=false) } }
    fun showVariants() { mutableState.update { it.copy(showVariants=true) } }
    fun hideVariants() { mutableState.update { it.copy(showVariants=false) } }
    fun selectVariant(card: Card) { mutableState.update { state -> state.copy(editing=state.editing?.copy(card=card,setCode=card.setCode),showVariants=false) } }
    fun expandImage(image: String) { mutableState.update { it.copy(expandedImage=image) } }
    fun closeImage() { mutableState.update { it.copy(expandedImage=null) } }
    fun update(card: QueuedCard)=mutate { owner,summary ->
        var entry=state.value.entries.firstOrNull { it.id==card.id } ?: return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
        if(state.value.editing?.id==card.id && state.value.editingVersion!=entry.version)return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
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
    fun adjust(card: QueuedCard,delta: Int) {
        val entry=state.value.entries.firstOrNull { it.id==card.id } ?: return
        if(entry.quantity>Int.MAX_VALUE.toLong()) { mutableState.update { it.copy(error="This original quantity exceeds the supported range. Set its quantity explicitly or exclude it.") };return }
        val next=entry.quantity+delta.toLong()
        if(next in 1L..Int.MAX_VALUE.toLong())update(card.copy(quantity=next.toInt()))
    }
    fun correctQuantity(entryId: String,version: Long,quantity: Long)=mutate { owner,summary ->
        val entry=state.value.entries.firstOrNull { it.id==entryId && it.version==version } ?: return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
        if(quantity !in 1L..Int.MAX_VALUE.toLong())return@mutate TransferMutationResult.Rejected(TransferError.QUANTITY_OVERFLOW)
        repository.editPendingEntry(id,owner,summary.generation,entry.copy(quantity=quantity))
    }
    private suspend fun readEntry(owner: TransferOwner,entryId: String): TransferReviewEntry? {
        val value=database.collectionTransferDao().reviewEntry(id.value,owner.storageKey(),entryId) ?: return null
        return TransferReviewEntry(value.id,value.scryfallId,value.isFoil,value.condition,value.language,value.quantity,value.appliedQuantity,value.excluded,null,TransferDestination.valueOf(value.destination),value.entryVersion,value.activeActionId?.let(::TransferActionId),value.state)
    }
    fun action(destination: TransferDestination,entryId: String?,generation: Long,payloadVersion: Long,entryVersion: Long?,acceptExclusions: Boolean,acceptRepeats: Boolean)=mutate { owner,summary ->
        if(summary.generation!=generation || summary.payloadVersion!=payloadVersion || (entryId!=null && state.value.entries.firstOrNull { it.id==entryId }?.version!=entryVersion))return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
        val chosen=if(entryId==null) {
            if(destination==TransferDestination.COLLECTION) {
                val reopened=repository.reopenWishlistSelection(id,owner,summary.generation,summary.payloadVersion)
                if(reopened!=TransferMutationResult.Accepted)return@mutate reopened
            }
            val current=repository.observeSummary(id,owner).filterNotNull().first()
            repository.chooseDestination(id,owner,current.generation,current.intentRevision,destination)
        } else {
            var entry=state.value.entries.firstOrNull { it.id==entryId } ?: return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
            if(entry.state=="WISHLIST_APPLIED") {
                val reopened=repository.reopenWishlistEntry(id,owner,entry.id,entry.version,summary.payloadVersion)
                if(reopened!=TransferMutationResult.Accepted)return@mutate reopened
                entry=readEntry(owner,entryId) ?: return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
            }
            repository.editPendingEntry(id,owner,summary.generation,entry.copy(destination=destination))
        }
        if(chosen!=TransferMutationResult.Accepted)return@mutate chosen
        val fresh=repository.observeSummary(id,owner).filterNotNull().first()
        val scope=if(entryId==null)TransferActionScope.DestinationSelection(fresh.intentRevision) else {
            val entry=database.collectionTransferDao().reviewEntry(id.value,owner.storageKey(),entryId) ?: return@mutate TransferMutationResult.Rejected(TransferError.REVIEW_CHANGED)
            TransferActionScope.Entry(entry.id,entry.entryVersion)
        }
        repository.confirmAction(owner,TransferActionRequest(TransferActionId(UUID.randomUUID().toString()),id,fresh.generation,destination,scope,acceptExclusions,acceptRepeats))
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
            catch(_: Exception) { if(currentOwner()==captured && sessions.currentSession==capturedSession)mutableState.update { it.copy(error="This review is no longer available. Reload it before continuing.") } }
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
    fun clearNotice() { mutableState.update { it.copy(notice=null) } }
}
