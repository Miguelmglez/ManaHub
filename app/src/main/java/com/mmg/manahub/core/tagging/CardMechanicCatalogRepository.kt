package com.mmg.manahub.core.tagging

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.CardMechanicCatalogRemoteDataSourceContract
import com.mmg.manahub.core.data.remote.dto.CardMechanicCatalogDto
import com.mmg.manahub.core.data.tagging.toDictionaryEntry
import com.mmg.manahub.core.data.tagging.verifiedQueryOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CardMechanicCatalogRepository(
    private val preferences: UserPreferencesDataStore,
    private val remote: CardMechanicCatalogRemoteDataSourceContract,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val refreshMutex = Mutex()
    private val _entries = MutableStateFlow<List<CardMechanicCatalogDto>>(emptyList())
    val entries: StateFlow<List<CardMechanicCatalogDto>> = _entries.asStateFlow()

    suspend fun loadCached() = refreshMutex.withLock {
        val stored = preferences.cardMechanicCatalogFlow.first()
        val cached = runCatching { json.decodeFromString<List<CardMechanicCatalogDto>>(stored) }.getOrNull()
            ?: return@withLock
        if (isValidSnapshot(cached)) apply(cached)
    }

    suspend fun refresh(): Boolean = refreshMutex.withLock {
        try {
            val snapshot = mutableListOf<CardMechanicCatalogDto>()
            while (true) {
                val page = remote.getActivePage(snapshot.size.toLong(), PAGE_SIZE)
                snapshot += page
                if (snapshot.size > MAX_ENTRIES || !isValidSnapshot(snapshot)) return@withLock false
                if (page.size < PAGE_SIZE) break
            }
            preferences.saveCardMechanicCatalog(json.encodeToString(snapshot))
            apply(snapshot)
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            false
        }
    }

    fun verifiedQueryFor(key: String): String? =
        _entries.value.firstOrNull { it.key == key }?.verifiedQueryOrNull()

    private fun apply(snapshot: List<CardMechanicCatalogDto>) {
        TagDictionary.applyRemoteEntries(snapshot.map { it.toDictionaryEntry() })
        _entries.value = snapshot
    }

    private fun isValidSnapshot(snapshot: List<CardMechanicCatalogDto>): Boolean {
        if (snapshot.size > MAX_ENTRIES) return false
        if (snapshot.zipWithNext().any { (left, right) -> left.key >= right.key }) return false
        return snapshot.all { row ->
            runCatching { row.toDictionaryEntry() }.isSuccess &&
                ((row.scryfallQuery == null) == (row.scryfallQueryVerifiedAt == null))
        }
    }

    private companion object {
        const val PAGE_SIZE = 500L
        const val MAX_ENTRIES = 20_000
    }
}
