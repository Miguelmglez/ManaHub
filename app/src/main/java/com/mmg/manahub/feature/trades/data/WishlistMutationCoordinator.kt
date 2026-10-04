package com.mmg.manahub.feature.trades.data

import androidx.room.withTransaction
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.TradeListOwner
import com.mmg.manahub.core.data.local.entity.LocalWishlistEntity
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.core.domain.repository.UpdateEntryOutcome
import com.mmg.manahub.core.model.WishlistEntry
import com.mmg.manahub.feature.collection.data.VerifiedTransferGuestIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.util.UUID

/** Every production wishlist edit commits its wanted payload or retained deletion intent locally. */
class WishlistMutationCoordinator(
    private val database: MtgDatabase,
    private val sessions: TransferSessionGate,
    private val auth: StateFlow<SessionState>,
    private val guest: VerifiedTransferGuestIdentity,
    private val delivery: TransferWishlistSync,
    private val reporter: com.mmg.manahub.core.common.CrashReporter? = null,
) {
    private val dao get()=database.localWishlistDao()
    private fun matches(owner: TransferOwner): Boolean = when(owner) {
        is TransferOwner.Account -> (auth.value as? SessionState.Authenticated)?.user?.id==owner.id
        is TransferOwner.VerifiedGuest -> auth.value==SessionState.Unauthenticated && guest.currentVerifiedOwner==owner
    }
    private suspend fun capture(): TransferOwner {
        val owner=when(val state=auth.value) {
            is SessionState.Authenticated -> TransferOwner.Account(state.user.id)
            SessionState.Unauthenticated -> guest.ensureLocalIdentity()
            SessionState.Loading -> throw TransferSessionChangedException()
        }
        if(!matches(owner))throw TransferSessionChangedException()
        withTimeout(5000L) { sessions.sessions.first { (it as? TransferSession.Available)?.owner==owner || !matches(owner) } }
        if(!matches(owner))throw TransferSessionChangedException()
        return owner
    }
    private suspend fun <T> mutate(expected: String?=null, operation: suspend (TransferOwner,String,()->Unit)->T): Result<T> = try {
        val owner=capture()
        if(expected!=null && (owner as? TransferOwner.Account)?.id!=expected)throw TransferSessionChangedException()
        val localOwner=when(owner) { is TransferOwner.Account -> owner.id; is TransferOwner.VerifiedGuest -> TradeListOwner.GUEST }
        val result=sessions.withOwner(owner) { _,guard ->
            val ensure={ guard(); if(!matches(owner))throw TransferSessionChangedException() }
            database.withTransaction { ensure(); val result=operation(owner,localOwner,ensure); ensure(); result }
        } ?: throw TransferSessionChangedException()
        if(!matches(owner))throw TransferSessionChangedException()
        Result.success(result)
    } catch(cancelled: CancellationException) { throw cancelled }
      catch(error: Exception) {
          if(!isExpectedTransferInterruption(error))reporter?.apply {
              setCustomKey("collection_wishlist_phase","local_mutation_failed")
              setCustomKey("collection_wishlist_attempted_bucket",transferCountBucket(1))
              setCustomKey("collection_wishlist_acknowledged_bucket",transferCountBucket(0))
              setCustomKey("collection_wishlist_failure_category",transferFailureCategory(error,TransferFailureCategory.STORAGE).value)
              log("collection_wishlist_local_mutation_failed")
              recordException(IllegalStateException("collection_wishlist_local_mutation_failed"))
          }
          Result.failure(IllegalStateException("Wishlist local mutation unavailable"))
      }
    private fun storage(owner: TransferOwner)=when(owner) { is TransferOwner.Account -> "account:${owner.id}"; is TransferOwner.VerifiedGuest -> "guest:${owner.installationToken}" }
    private suspend fun byId(owner: TransferOwner,key: String,id: String)=if(owner is TransferOwner.VerifiedGuest)dao.provenLocalById(storage(owner),id) else dao.getById(id,key)
    private suspend fun byAttributes(owner: TransferOwner,key: String,printing: String,matchAny: Boolean,foil: Boolean?,condition: String?,language: String?)=
        if(owner is TransferOwner.VerifiedGuest)dao.provenLocalByAttributes(storage(owner),printing,matchAny,foil,condition,language)
        else dao.getByAttributes(printing,matchAny,foil,condition,language,key)
    private suspend fun save(row: LocalWishlistEntity, owner: TransferOwner, existing: Boolean) {
        if(owner is TransferOwner.VerifiedGuest) {
            if(!existing) { dao.insertProvenLocal(row,storage(owner)); return }
            require(dao.provenLocalById(storage(owner),row.id)!=null)
        }
        if(existing)dao.update(row) else dao.insert(row)
        dao.stageManaged(row,capturedOwner=storage(owner))
    }
    private suspend fun remove(row: LocalWishlistEntity, owner: TransferOwner) {
        require(owner !is TransferOwner.VerifiedGuest || dao.provenLocalById(storage(owner),row.id)!=null)
        dao.stageManaged(row,deleted=true,capturedOwner=storage(owner)); dao.deleteById(row.id,row.ownerUserId!!)
    }
    suspend fun add(entries: List<WishlistEntry>, expected: String?=null): Result<Unit> = mutate(expected) { owner,key,ensure ->
        for(entry in entries) {
            ensure(); require(entry.quantity>0)
            val existing=byAttributes(owner,key,entry.cardId,entry.matchAnyVariant,entry.isFoil,entry.condition,entry.language)
            val copies=transferCollectionQuantity(existing?.quantity,false,entry.quantity.toLong())
            val row=existing?.copy(quantity=copies,synced=false) ?: LocalWishlistEntity(if(owner is TransferOwner.VerifiedGuest)UUID.randomUUID().toString() else entry.id,entry.cardId,copies,entry.matchAnyVariant,entry.isFoil,entry.condition,entry.language,false,entry.createdAt,key)
            save(row,owner,existing!=null)
        }
    }
    suspend fun remove(id: String): Result<Unit> = mutate { owner,key,_ -> byId(owner,key,id)?.let { remove(it,owner) }; Unit }
    suspend fun quantity(id: String, copies: Int): Result<Unit> = mutate { owner,key,_ ->
        byId(owner,key,id)?.let { if(copies<=0)remove(it,owner) else save(it.copy(quantity=copies,synced=false),owner,true) }; Unit
    }
    suspend fun decrement(printing: String, copies: Int, foil: Boolean?=null, condition: String?=null, language: String?=null): Result<Unit> = mutate { owner,key,ensure ->
        require(copies>0)
        val rows=if(owner is TransferOwner.VerifiedGuest)dao.provenLocalByPrinting(storage(owner),printing) else dao.getByScryfallId(printing,key)
        val targets=if(foil==null)rows else listOfNotNull(rows.firstOrNull { (it.isFoil ?: false)==foil && (it.condition==null || it.condition.equals(condition,true)) && (it.language==null || it.language.equals(language,true)) } ?: rows.firstOrNull { it.matchAnyVariant })
        for(row in targets) { ensure(); val remaining=row.quantity.toLong()-copies; if(remaining<=0L)remove(row,owner) else save(row.copy(quantity=remaining.toInt(),synced=false),owner,true) }
    }
    suspend fun edit(id: String, printing: String, foil: Boolean?, condition: String?, language: String?, copies: Int, expected: String?): Result<UpdateEntryOutcome> = mutate(expected) { owner,key,_ ->
        require(copies>0)
        val edited=byId(owner,key,id) ?: return@mutate UpdateEntryOutcome.ENTRY_NOT_FOUND
        val existing=byAttributes(owner,key,printing,edited.matchAnyVariant,foil,condition,language)
        if(existing!=null && existing.id!=id) {
            save(existing.copy(quantity=transferCollectionQuantity(existing.quantity,false,copies.toLong()),synced=false),owner,true)
            remove(edited,owner)
        } else save(edited.copy(scryfallId=printing,isFoil=foil,condition=condition,language=language,quantity=copies,synced=false),owner,true)
        UpdateEntryOutcome.UPDATED
    }
    suspend fun synchronize(userId: String): Result<Int> {
        val localIdentity=guest.read()
        var acknowledged=0
        do {
            val local=mutate(userId) { owner,key,ensure ->
                val adopted=localIdentity?.let { dao.adoptProvenLocalPage(storage(it),key) } ?: emptyList()
                val rows=dao.unmanagedUnsyncedPage(key)
                for(row in rows) { ensure(); dao.stageManaged(row,capturedOwner=storage(owner)) }
                (adopted+rows.map { it.id }).distinct()
            }
            if(local.isFailure)return Result.failure(local.exceptionOrNull()!!)
            delivery.runSlice(TransferOwner.Account(userId))
            val completed=mutate(userId) { _,key,_ ->
                local.getOrThrow().count { id -> dao.managed(id)?.pending==false && dao.getById(id,key)?.synced==true }
            }
            if(completed.isFailure)return Result.failure(completed.exceptionOrNull()!!)
            acknowledged+=completed.getOrThrow()
        } while(local.getOrThrow().isNotEmpty())
        return Result.success(acknowledged)
    }
    suspend fun addAndSynchronize(entry: WishlistEntry, userId: String): Result<Unit> {
        val result=add(listOf(entry),userId)
        if(result.isSuccess)delivery.runSlice(TransferOwner.Account(userId))
        return if((auth.value as? SessionState.Authenticated)?.user?.id==userId)result else Result.failure(IllegalStateException("Wishlist owner changed"))
    }
    suspend fun putAbsolute(entry: WishlistEntry): Result<Unit> = mutate(entry.userId.takeIf { it.isNotBlank() }) { owner,key,_ ->
        require(owner is TransferOwner.Account && entry.quantity>0)
        val previous=dao.getById(entry.id,key)
        save(LocalWishlistEntity(entry.id,entry.cardId,entry.quantity,entry.matchAnyVariant,entry.isFoil,entry.condition,entry.language,false,previous?.createdAt ?: entry.createdAt,key),owner,previous!=null)
    }
    suspend fun removeRemote(id: String): Result<Unit> = mutate { owner,key,_ ->
        require(owner is TransferOwner.Account)
        val row=dao.getById(id,key)
        if(row!=null)remove(row,owner) else dao.stageMissingDeletion(id,storage(owner))
    }
    suspend fun evictAmbiguous(userId: String): Result<Unit> = mutate(userId) { _,_,_ -> Unit }
}
