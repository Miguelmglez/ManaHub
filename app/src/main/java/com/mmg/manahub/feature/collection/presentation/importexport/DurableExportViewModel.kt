package com.mmg.manahub.feature.collection.presentation.importexport

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.feature.collection.data.TransferAuthSessionObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class DurableExportUiState(
    val value: DurableCollectionExport?=null,
    val busy: Boolean=false,
    val requestSave: Boolean=false,
    val share: PendingExportShare?=null,
    val notice: String?=null,
)

/** UI intent starts freezing before any SAF picker; each retained request expires with its auth epoch. */
class DurableExportViewModel(
    private val saved: SavedStateHandle,
    private val repository: CollectionExportRepository,
    private val sessions: TransferSessionGate,
    private val observer: TransferAuthSessionObserver,
    private val reporter: com.mmg.manahub.core.common.CrashReporter? = null,
) : ViewModel() {
    private val mutable=MutableStateFlow(DurableExportUiState())
    val state=mutable.asStateFlow()
    private var epoch: TransferSession.Available?=null
    private var observation: Job?=null
    private var operation: Job?=null
    private var pickerEpoch: TransferSession.Available?=null
    private var pickerJob: String?=null
    private var reportConsent: Pair<TransferSession.Available,String>?=null
    private var shareEpoch: TransferSession.Available?=null
    private fun telemetry(event: String,failure: Boolean=false) {
        val value=state.value.value
        reporter?.let { exportTelemetry(it,event,value?.format?.name,value?.rows ?: 0L,TransferFailureCategory.UNKNOWN,failure) }
    }
    init { viewModelScope.launch {
        combine(sessions.sessions,observer.identities) { session,_ -> session }.collectLatest { session ->
            if(pickerEpoch!=null)telemetry("save_cancelled")
            if(state.value.share!=null)telemetry("share_cancelled")
            operation?.cancel();observation?.cancel();epoch=null;pickerEpoch=null;shareEpoch=null;mutable.value=DurableExportUiState()
            val available=(session as? TransferSession.Available)?.takeIf { observer.matchesObserved(it.owner) } ?: return@collectLatest
            epoch=available
            val id=try { saved.get<String>("durableExportId")?.takeIf { saved.get<String>("durableExportOwner")==available.owner.storageKeyForUi() } ?: repository.latest(available.owner) } catch(_: Exception) { null }
            if(id!=null)watch(id,available)
        }
    } }
    private fun current()=epoch?.takeIf { sessions.currentSession==it && observer.matchesObserved(it.owner) }
    private fun watch(id: String,captured: TransferSession.Available) {
        saved["durableExportId"]=id;saved["durableExportOwner"]=captured.owner.storageKeyForUi()
        observation?.cancel()
        observation=viewModelScope.launch {
            repository.observe(captured.owner,id).collect { value -> if(current()==captured)mutable.update { it.copy(value=value) } }
        }
    }
    private fun run(task: suspend (TransferSession.Available)->Unit) {
        val captured=current() ?: return
        if(operation?.isActive==true)return
        operation=viewModelScope.launch {
            mutable.update { it.copy(busy=true,notice=null) }
            try { task(captured) }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { if(current()==captured)mutable.update { it.copy(notice="Export could not finish. The frozen selection is retained. A destination may contain an incomplete copy; choose a new destination or retry.") } }
            finally { if(current()==captured)mutable.update { it.copy(busy=false) } }
        }
    }
    fun begin(query: CollectionSelectionQuery,format: CollectionFileFormat,target: CollectionExportTarget)=run { captured ->
        if(pickerEpoch!=null)return@run
        mutable.update { it.copy(value=null,share=null) }
        val id=repository.create(captured.owner,query,format,target)
        if(current()!=captured)return@run
        watch(id,captured);prepared(repository.prepare(captured.owner,id),captured)
    }
    fun retry()=run { captured -> state.value.value?.let { telemetry("retry");prepared(repository.prepare(captured.owner,it.id),captured) } }
    fun availableOnly()=run { captured ->
        val displayed=state.value.value?.takeIf { it.phase==CollectionExportPhase.NEEDS_METADATA } ?: return@run
        telemetry("available_only_requested")
        prepared(repository.prepare(captured.owner,displayed.id,true),captured)
    }
    private suspend fun prepared(value: DurableCollectionExport,captured: TransferSession.Available) {
        if(current()!=captured)return
        mutable.update { it.copy(value=value) }
        if(value.phase in setOf(CollectionExportPhase.READY,CollectionExportPhase.SAVED,CollectionExportPhase.SHARED)) {
            if(value.target==CollectionExportTarget.SAVE)requestSave()
            else {
                val location=repository.share(captured.owner,value.id)
                if(current()==captured) { shareEpoch=captured;mutable.update { it.copy(share=PendingExportShare(location,value.format.mimeType)) } }
            }
        }
    }
    fun requestSave() {
        pickerEpoch=current() ?: return
        pickerJob=state.value.value?.id ?: return
        mutable.update { it.copy(requestSave=true) }
    }
    fun pickerLaunched() { mutable.update { it.copy(requestSave=false) } }
    fun cancelPickerRequest() {
        if(!state.value.requestSave)return
        telemetry("save_cancelled")
        pickerEpoch=null;pickerJob=null
        mutable.update { it.copy(requestSave=false) }
    }
    fun save(location: String?) {
        val captured=pickerEpoch ?: return
        pickerEpoch=null
        if(location==null || current()!=captured) { telemetry("save_cancelled");pickerJob=null;return }
        val id=pickerJob?.takeIf { it==state.value.value?.id } ?: return
        pickerJob=null
        run { session -> repository.save(session.owner,id,location);if(current()==session)mutable.update { it.copy(notice="Saved ${it.value?.rows ?: 0L} rows. ${it.value?.omittedRows ?: 0L} omitted source rows; keep the omissions report when any are missing.") } }
    }
    fun reportRequested() { val session=current() ?: return;val id=state.value.value?.id ?: return;reportConsent=session to id }
    fun report(location: String) {
        val consent=reportConsent ?: return;reportConsent=null
        if(current()!=consent.first || state.value.value?.id!=consent.second)return
        run { captured -> repository.writeOmissions(captured.owner,consent.second,location);if(current()==captured)mutable.update { it.copy(notice="Full omissions report saved.") } }
    }
    fun isCurrentOwner(): Boolean=current()!=null
    fun canLaunchShare(): Boolean=current()!=null && current()==shareEpoch
    fun shared(cancelled: Boolean=false) { if(cancelled && state.value.share!=null)telemetry("share_cancelled");shareEpoch=null;mutable.update { it.copy(share=null) } }
    fun shareChooserFailed() { telemetry("share_chooser_failed",failure=true) }
    fun clearNotice() { mutable.update { it.copy(notice=null) } }
    private fun TransferOwner.storageKeyForUi()=when(this) { is TransferOwner.Account->"account:$id";is TransferOwner.VerifiedGuest->"guest:$installationToken" }
}

