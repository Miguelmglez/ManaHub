package com.mmg.manahub.core.tagging

import com.mmg.manahub.core.data.local.CardMechanicRefreshCheckpoint
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.dto.CardMechanicCatalogDto
import com.mmg.manahub.core.domain.usecase.card.HydrateCollectionStrategyTagsUseCase
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CardMechanicCatalogRehydrator(
    private val preferences: UserPreferencesDataStore,
    private val hydrate: HydrateCollectionStrategyTagsUseCase,
    private val currentOwnerUserId: () -> String?,
) {
    suspend fun rehydrate(ownerUserId: String, catalog: List<CardMechanicCatalogDto>) {
        if (ownerUserId.isBlank() || catalog.isEmpty() || currentOwnerUserId() != ownerUserId) return
        val signature = signature(catalog)
        val saved = preferences.cardMechanicRefreshCheckpointFlow.first()
        var checkpoint = saved.firstOrNull {
            it.ownerUserId == ownerUserId && it.signature == signature
        } ?: CardMechanicRefreshCheckpoint(ownerUserId = ownerUserId, signature = signature).also {
            preferences.saveCardMechanicRefreshCheckpoint(it)
        }
        if (checkpoint.complete) return

        while (true) {
            currentCoroutineContext().ensureActive()
            if (currentOwnerUserId() != ownerUserId) return
            val page = hydrate.rehydrateCatalogPage(ownerUserId, checkpoint.cursor, isOwnerActive = {
                currentOwnerUserId() == ownerUserId
            })
            currentCoroutineContext().ensureActive()
            if (currentOwnerUserId() != ownerUserId) return
            checkpoint = checkpoint.copy(
                cursor = page.lastScryfallId ?: checkpoint.cursor,
                complete = !page.hasMore,
            )
            preferences.saveCardMechanicRefreshCheckpoint(checkpoint)
            if (!page.hasMore) return
        }
    }

    internal fun signature(catalog: List<CardMechanicCatalogDto>): String {
        val payload = Json.encodeToString(catalog.sortedBy { it.key })
        return MessageDigest.getInstance("SHA-256")
            .digest(payload.encodeToByteArray())
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}
