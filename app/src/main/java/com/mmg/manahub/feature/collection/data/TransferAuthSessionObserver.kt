package com.mmg.manahub.feature.collection.data

import android.content.Context
import com.mmg.manahub.core.data.local.dao.CollectionTransferDao
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.collection.transfer.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Installation-local provenance is established after authentication resolves to signed out. */
class VerifiedTransferGuestIdentity(private val context: Context) {
    private val mutex=Mutex()
    private val verified=MutableStateFlow<TransferOwner.VerifiedGuest?>(null)
    val currentVerifiedOwner: TransferOwner.VerifiedGuest? get()=verified.value
    suspend fun read(): TransferOwner.VerifiedGuest? = withContext(Dispatchers.IO) {
        val owner=context.getSharedPreferences("collection_transfer_identity",Context.MODE_PRIVATE).getString("verified_guest",null)
            ?.takeIf { runCatching { TransferJobId(it) }.isSuccess }?.let { TransferOwner.VerifiedGuest(it) }
        verified.value=owner
        owner
    }
    suspend fun ensureLocalIdentity(): TransferOwner.VerifiedGuest = mutex.withLock { withContext(Dispatchers.IO) {
        val preferences=context.getSharedPreferences("collection_transfer_identity",Context.MODE_PRIVATE)
        val token=read()?.installationToken ?: UUID.randomUUID().toString()
        check(preferences.edit().putString("verified_guest",token).commit())
        verified.value=TransferOwner.VerifiedGuest(token)
        TransferOwner.VerifiedGuest(token)
    } }
}

/** Resolved authentication determines identity; loading never becomes a local session. */
class TransferAuthSessionObserver(
    private val auth: StateFlow<SessionState>,
    private val guest: VerifiedTransferGuestIdentity,
    private val gate: TransferSessionGate,
    private val dao: CollectionTransferDao,
) {
    private val initialReceipts=ConcurrentHashMap<String,Long>()
    @Volatile private var firstResolvedSession: TransferSession.Available?=null
    val identities: StateFlow<SessionState> get()=auth
    fun captureInitialReceipt(id: TransferJobId) {
        if(auth.value==SessionState.Loading && firstResolvedSession==null && gate.currentGeneration==0L)
            initialReceipts.putIfAbsent(id.value,gate.currentGeneration)
    }
    fun canBindInitialReceipt(id: TransferJobId,receiptGeneration: Long,session: TransferSession.Available): Boolean =
        initialReceipts[id.value]==receiptGeneration && firstResolvedSession==session && gate.currentSession==session && matchesObserved(session.owner)
    fun consumedReceipt(id: TransferJobId) { initialReceipts.remove(id.value) }
    fun matchesObserved(owner: TransferOwner): Boolean = when(owner) {
        is TransferOwner.Account -> (auth.value as? SessionState.Authenticated)?.user?.id==owner.id
        is TransferOwner.VerifiedGuest -> auth.value==SessionState.Unauthenticated && guest.currentVerifiedOwner==owner
    }
    /** Old observer generations cannot continue parsing or publish stale resolution results. */
    fun observedSession(gate: TransferSessionGate): TransferSession = gate.currentSession.takeIf {
        (it as? TransferSession.Available)?.owner?.let(::matchesObserved)==true
    } ?: TransferSession.Loading
    fun start(scope: CoroutineScope): Job = scope.launch {
        auth.collectLatest { state ->
                val owner=when(state) {
                    is SessionState.Authenticated -> TransferOwner.Account(state.user.id)
                    SessionState.Unauthenticated -> guest.ensureLocalIdentity()
                    SessionState.Loading -> null
                }
                if(auth.value!=state) return@collectLatest
                if(owner!=null && firstResolvedSession==null)firstResolvedSession=TransferSession.Available(owner,gate.currentGeneration+1L)
                gate.changeOwner(owner) { previous ->
                    if(previous!=null) dao.pauseOwnerJobs(previous.storageKey())
                }
            }
    }
}

internal fun TransferOwner.storageKey(): String = when(this) {
    is TransferOwner.Account -> "account:$id"
    is TransferOwner.VerifiedGuest -> "guest:$installationToken"
}
