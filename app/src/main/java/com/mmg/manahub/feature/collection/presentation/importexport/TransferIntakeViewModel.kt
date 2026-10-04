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
    private val mutableError=MutableStateFlow<String?>(null)
    val error=mutableError.asStateFlow()
    private val mutableReceiving=MutableStateFlow(false)
    val receiving=mutableReceiving.asStateFlow()
    val recoveryNotice=quarantine.notice
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val unfinished=combine(sessions.sessions,observer.identities) { session,_ -> session }.flatMapLatest { captured ->
        val available=(captured as? TransferSession.Available)?.takeIf { observer.matchesObserved(it.owner) }
        if(available==null)flowOf(emptyList()) else database.collectionTransferDao().observeReconciliationJobs(available.owner.storageKey()).map { rows ->
            if(sessions.currentSession==captured && observer.matchesObserved(available.owner))rows.map { PendingTransfer(it.id,it.phase,it.acceptedCopies,it.appliedCopies) } else emptyList()
        }.onStart { emit(emptyList()) }
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000L),emptyList())
    private val activeReceipts=ConcurrentHashMap.newKeySet<String>()

    fun receiveIntent(intent: Intent,restoredId: String?=null,onIdentity: (String)->Unit={}): Boolean {
        if(!AndroidTransferDelivery.handles(intent))return false
        val result=AndroidTransferDelivery.normalize(intent)
        if(result is TransferDeliveryResult.Rejected) { mutableError.value="Open or share CSV or TXT files"; return true }
        val uris=(result as TransferDeliveryResult.Accepted).sources.map(Uri::parse)
        receiveUris(uris,deliveryId=restoredId,recordIdentity=onIdentity)
        return true
    }

    fun receiveUris(uris: List<Uri>,deliveryId: String?=null,recordIdentity: (String)->Unit={}) {
        val result=normalizeTransferDelivery(TransferDeliveryInput(TransferDeliveryAction.MULTIPLE,streams=uris.map(Uri::toString)))
        if(result !is TransferDeliveryResult.Accepted) { mutableError.value="Open or share up to 10 CSV or TXT files"; return }
        val id=deliveryId?.takeIf { runCatching { TransferJobId(it) }.isSuccess } ?: UUID.randomUUID().toString()
        recordIdentity(id)
        submit(TransferJobId(id),result.sources.map { ContentTransferInputSource(resolver,Uri.parse(it)) },deliveryId==null)
    }

    fun receiveText(text: String) {
        if(text.isBlank())return
        val id=TransferJobId(UUID.randomUUID().toString())
        submit(id,listOf(object: TransferInputSource {
            override val identity="paste:${id.value}"
            override fun open()=ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))
        }))
    }

    private fun submit(id: TransferJobId,sources: List<TransferInputSource>,newReceipt: Boolean=true) {
        val seen=saved.get<List<String>>("transfer_seen") ?: emptyList()
        if(!activeReceipts.add(id.value))return
        saved["transfer_seen"]=(seen+id.value).takeLast(32)
        saved["transfer_pending"]=(pending.value+id.value).distinct()
        val captured=observer.observedSession(sessions)
        if(newReceipt && id.value !in seen)observer.captureInitialReceipt(id)
        if(newReceipt && id.value !in seen)observer.captureInitialReceipt(id)
        viewModelScope.launch(Dispatchers.IO) {
            mutableReceiving.value=true
            try { files.receive(id,(captured as? TransferSession.Available)?.generation ?: 0L,sources,captured) }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { mutableError.value="The files could not be received. Review the transfer or share them again." }
            finally { activeReceipts.remove(id.value); mutableReceiving.value=activeReceipts.isNotEmpty() }
        }
    }

    fun routed(id: String) { saved["transfer_pending"]=pending.value.filterNot { it==id } }
    fun clearError() { mutableError.value=null }
    fun saveRecovery(uri: Uri) { viewModelScope.launch {
        try { quarantine.saveRecovery(uri) }
        catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { mutableError.value="Recovery was not saved. Delete the partial document and try again." }
    } }
}

data class PendingTransfer(val id: String,val phase: String,val accepted: Long,val applied: Long)
