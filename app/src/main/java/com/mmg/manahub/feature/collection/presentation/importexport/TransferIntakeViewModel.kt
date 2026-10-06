package com.mmg.manahub.feature.collection.presentation.importexport

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.feature.collection.data.*
import com.mmg.manahub.core.data.local.MtgDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Activity-local intake preserves receipt identities across recreation without retaining URI state. */
class TransferIntakeViewModel(
    private val saved: SavedStateHandle,
    private val resolver: ContentResolver,
    private val files: AndroidCollectionTransferFileStore,
    private val sessions: TransferSessionGate,
    private val observer: TransferAuthSessionObserver,
    private val quarantine: LegacyCollectionImportQuarantine,
    private val database: MtgDatabase,
) : ViewModel() {
    val pending: StateFlow<List<String>> = saved.getStateFlow("transfer_pending",emptyList())
    private val mutableError=MutableStateFlow<TransferIntakeFailure?>(null)
    val error=mutableError.asStateFlow()
    private val mutableReceiving=MutableStateFlow(false)
    val receiving=mutableReceiving.asStateFlow()
    val recoveryNotice=quarantine.notice
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val unfinished=combine(sessions.sessions,observer.identities) { session,_ -> session }.flatMapLatest { captured ->
        val available=(captured as? TransferSession.Available)?.takeIf { observer.matchesObserved(it.owner) }
        if(available==null)flowOf(emptyList()) else database.collectionTransferDao().observeReconciliationJobs(available.owner.storageKey()).map { rows ->
            if(sessions.currentSession==captured && observer.matchesObserved(available.owner))rows.map { job -> val totals=database.collectionTransferDao().repositoryTotals(job.id,available.owner.storageKey());PendingTransfer(job.id,job.phase,totals.pendingCopies,0L) }.takeIf { sessions.currentSession==captured && observer.matchesObserved(available.owner) } ?: emptyList() else emptyList()
        }.onStart { emit(emptyList()) }
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000L),emptyList())
    private val activeReceipts=ConcurrentHashMap.newKeySet<String>()
    private var freshPickerSession: TransferSession.Available? = null
    val pickerReady = combine(sessions.sessions, observer.identities, receiving) { _, _, receiving ->
        observer.observedSession(sessions) is TransferSession.Available && !receiving
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    fun capturePicker(): Boolean {
        val session = observer.observedSession(sessions) as? TransferSession.Available ?: return false
        if (receiving.value || saved.get<String>("picker_owner") != null) return false
        saved["picker_owner"] = session.owner.storageKey()
        saved["picker_generation"] = session.generation
        freshPickerSession = session
        return true
    }

    fun receivePickerResult(uris: List<Uri>) {
        val key = saved.get<String>("picker_owner")
        val generation = saved.get<Long>("picker_generation")
        saved.remove<String>("picker_owner")
        saved.remove<Long>("picker_generation")
        if (uris.isEmpty()) return
        val owner = when {
            key?.startsWith("account:") == true -> TransferOwner.Account(key.removePrefix("account:"))
            key?.startsWith("guest:") == true -> TransferOwner.VerifiedGuest(key.removePrefix("guest:"))
            else -> null
        }
        val fresh = freshPickerSession
        freshPickerSession = null
        val captured = if (owner != null && generation != null && fresh == TransferSession.Available(owner, generation)) fresh else TransferSession.Loading
        receiveUris(uris, capturedSession = captured, allowInitialProof = false)
    }

    fun receiveIntent(intent: Intent,restoredId: String?=null,onIdentity: (String)->Unit={}): Boolean {
        if(!AndroidTransferDelivery.handles(intent))return false
        val result=AndroidTransferDelivery.normalize(intent)
        if(result is TransferDeliveryResult.Rejected) { mutableError.value=TransferIntakeFailure.INVALID_DELIVERY; return true }
        val uris=(result as TransferDeliveryResult.Accepted).sources.map(Uri::parse)
        receiveUris(uris,deliveryId=restoredId,recordIdentity=onIdentity)
        return true
    }

    fun receiveUris(uris: List<Uri>,deliveryId: String?=null,recordIdentity: (String)->Unit={},capturedSession: TransferSession?=null,allowInitialProof: Boolean=true) {
        val result=normalizeTransferDelivery(TransferDeliveryInput(TransferDeliveryAction.MULTIPLE,streams=uris.map(Uri::toString)))
        if(result !is TransferDeliveryResult.Accepted) { mutableError.value=TransferIntakeFailure.TOO_MANY_FILES; return }
        val id=deliveryId?.takeIf { runCatching { TransferJobId(it) }.isSuccess } ?: UUID.randomUUID().toString()
        recordIdentity(id)
        submit(TransferJobId(id),result.sources.map { ContentTransferInputSource(resolver,Uri.parse(it)) },deliveryId==null && allowInitialProof,capturedSession)
    }

    fun receiveText(text: String) {
        if(text.isBlank())return
        val id=TransferJobId(UUID.randomUUID().toString())
        submit(id,listOf(object: TransferInputSource {
            override val identity="paste:${id.value}"
            override fun open()=ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))
        }))
    }

    private fun submit(id: TransferJobId,sources: List<TransferInputSource>,newReceipt: Boolean=true,capturedSession: TransferSession?=null) {
        val seen=saved.get<List<String>>("transfer_seen") ?: emptyList()
        if(!activeReceipts.add(id.value))return
        saved["transfer_seen"]=(seen+id.value).takeLast(32)
        saved["transfer_pending"]=(pending.value+id.value).distinct()
        val captured=capturedSession ?: observer.observedSession(sessions)
        if(newReceipt && id.value !in seen)observer.captureInitialReceipt(id)
        viewModelScope.launch(Dispatchers.IO) {
            mutableReceiving.value=true
            try { files.receive(id,(captured as? TransferSession.Available)?.generation ?: 0L,sources,captured) }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { mutableError.value=TransferIntakeFailure.RECEIVE }
            finally { activeReceipts.remove(id.value); mutableReceiving.value=activeReceipts.isNotEmpty() }
        }
    }

    fun routed(id: String) { saved["transfer_pending"]=pending.value.filterNot { it==id } }
    fun clearError() { mutableError.value=null }
    fun saveRecovery(uri: Uri) { viewModelScope.launch {
        try { quarantine.saveRecovery(uri) }
        catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { mutableError.value=TransferIntakeFailure.RECOVERY }
    } }
}

data class PendingTransfer(val id: String,val phase: String,val accepted: Long,val applied: Long)
