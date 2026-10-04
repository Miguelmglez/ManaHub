package com.mmg.manahub.feature.collection.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.mapper.toEntityCard
import com.mmg.manahub.core.data.network.RateLimitExhaustedException
import com.mmg.manahub.core.data.network.ScryfallRequestQueue
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.data.remote.dto.CardIdentifierDto
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.core.domain.repository.CardLookupIdentifier
import com.mmg.manahub.core.model.Card
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException

/** Connectivity is a wait gate, not a failed Scryfall attempt. */
class TransferNetworkAvailability(context: Context) {
    private val connectivity=context.applicationContext.getSystemService(ConnectivityManager::class.java)
    fun isOnline(): Boolean {
        val network=connectivity.activeNetwork ?: return false
        val capabilities=connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

/** Uses the established remote source and its shared limiter; no flattened error strings are parsed. */
class AndroidTransferResolutionGateway(
    private val cards: CardDao,
    private val remote: ScryfallRemoteDataSource,
    private val queue: ScryfallRequestQueue,
    private val online: () -> Boolean,
) : TransferResolutionGateway {
    override suspend fun cached(identifier: CardLookupIdentifier): TransferPrinting? {
        val id=identifier.scryfallId?.trim()?.lowercase()
        val set=identifier.setCode
        val collector=identifier.collectorNumber
        val name=identifier.name
        val stored=when {
            id!=null -> cards.getById(id)
            set!=null && collector!=null -> cards.findTransferPrinting(set,collector)
            name!=null -> cards.findTransferName(name,set)
            else -> null
        } ?: return null
        return TransferPrinting(stored.scryfallId,stored.name,stored.setCode,stored.collectorNumber)
    }

    override suspend fun lookup(identifiers: List<CardLookupIdentifier>): TransferLookupBatch {
        require(identifiers.size in 1..75)
        if(!online()) return TransferLookupBatch(emptyList(),emptyList(),TransferLookupFailure.OFFLINE)
        val cooldown=queue.cooldownRemainingMillis()
        if(cooldown>0L) return TransferLookupBatch(emptyList(),emptyList(),TransferLookupFailure.COOLDOWN,cooldown)
        return remote.lookupCollection(identifiers.map { CardIdentifierDto(id=it.scryfallId,name=it.name,set=it.setCode,collectorNumber=it.collectorNumber) }).fold(
            onSuccess={ (found,missing) -> cards.cacheTransferCards(found.map { it.toEntityCard() }); TransferLookupBatch(found.map { it.printing() },missing.map { CardLookupIdentifier(it.id,it.name,it.set,it.collectorNumber) }) },
            onFailure={ failure -> if(failure is CancellationException) throw failure; TransferLookupBatch(emptyList(),emptyList(),if(!online())TransferLookupFailure.OFFLINE else TransferLookupFailure.RETRYABLE,(failure as? RateLimitExhaustedException)?.retryAfterMs ?: 1000L) },
        )
    }

    override suspend fun fallbackName(name: String): TransferNameLookup {
        if(!online()) return TransferNameLookup(failure=TransferLookupFailure.OFFLINE)
        val cooldown=queue.cooldownRemainingMillis()
        if(cooldown>0L) return TransferNameLookup(failure=TransferLookupFailure.COOLDOWN,retryAfterMillis=cooldown)
        return remote.searchCardByName(name).fold(
            onSuccess={ cards.cacheTransferCards(listOf(it.toEntityCard())); TransferNameLookup(card=it.printing()) },
            onFailure={ failure ->
                if(failure is CancellationException) throw failure
                if(failure is ResponseException && failure.response.status.value==404) TransferNameLookup(notFound=true)
                else TransferNameLookup(failure=if(!online())TransferLookupFailure.OFFLINE else TransferLookupFailure.RETRYABLE,retryAfterMillis=(failure as? RateLimitExhaustedException)?.retryAfterMs ?: 1000L)
            },
        )
    }

    private fun Card.printing()=TransferPrinting(scryfallId,name,setCode,collectorNumber)
}
