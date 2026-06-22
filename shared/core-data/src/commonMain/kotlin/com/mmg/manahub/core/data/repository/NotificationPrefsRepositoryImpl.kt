package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.domain.repository.NotificationPrefsRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase-backed implementation of [NotificationPrefsRepository].
 *
 * Storage model: a single row per user in the `notification_prefs` table, where the
 * `prefs` jsonb column holds the `event_type -> boolean` map. A missing key means the
 * event is enabled, so we only persist explicit overrides.
 *
 * Concurrency: [setEventEnabled] performs a read-merge-upsert sequence. A [Mutex] serialises
 * these so two rapid toggles cannot race and clobber each other's merged result.
 */
class NotificationPrefsRepositoryImpl(
    private val supabaseClient: SupabaseClient,
    private val dispatcherProvider: DispatcherProvider,
) : NotificationPrefsRepository {

    private val _prefsFlow = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /** Guards the read-merge-upsert critical section against concurrent toggles. */
    private val writeMutex = Mutex()

    /** Guards the one-shot lazy-load flag against concurrent first-subscribers. */
    private val loadMutex = Mutex()

    /** Whether the initial remote load has been triggered. */
    private var loaded = false

    override val prefsFlow: Flow<Map<String, Boolean>> =
        _prefsFlow.asStateFlow().onStart {
            // Load lazily on the first subscriber only; subsequent subscribers reuse the cache.
            val shouldLoad = loadMutex.withLock {
                if (!loaded) { loaded = true; true } else false
            }
            if (shouldLoad) {
                runCatching { refresh() }
                    .onFailure { }
            }
        }

    override suspend fun setEventEnabled(eventType: String, enabled: Boolean) =
        withContext(dispatcherProvider.io) {
            val userId = currentUserId() ?: return@withContext
            writeMutex.withLock {
                // Read the freshest server state inside the lock so we merge onto the true
                // current map, not a stale local cache that a concurrent write may have changed.
                val current = fetchRemotePrefs(userId).toMutableMap()
                current[eventType] = enabled
                supabaseClient.postgrest["notification_prefs"]
                    .upsert(NotificationPrefRow(userId = userId, prefs = current))
                _prefsFlow.value = current
            }
        }

    override suspend fun isEventEnabled(eventType: String): Boolean =
        withContext(dispatcherProvider.io) {
            // A missing key defaults to enabled (opt-out model).
            currentPrefs()[eventType] ?: true
        }

    /**
     * Returns the most relevant preference map: the in-memory cache once loaded, otherwise a
     * fresh remote fetch. Falls back to an empty map (all enabled) on any failure.
     */
    private suspend fun currentPrefs(): Map<String, Boolean> {
        val alreadyLoaded = loadMutex.withLock { loaded }
        if (alreadyLoaded) return _prefsFlow.value
        val userId = currentUserId() ?: return emptyMap()
        return runCatching { fetchRemotePrefs(userId) }
            .onFailure { }
            .getOrDefault(emptyMap())
    }

    /** Forces a remote read and publishes it to [_prefsFlow]. */
    private suspend fun refresh() = withContext(dispatcherProvider.io) {
        val userId = currentUserId() ?: return@withContext
        _prefsFlow.value = fetchRemotePrefs(userId)
    }

    /** Fetches the persisted prefs map for [userId], or an empty map when no row exists. */
    private suspend fun fetchRemotePrefs(userId: String): Map<String, Boolean> =
        supabaseClient.postgrest["notification_prefs"]
            .select { filter { eq("user_id", userId) } }
            .decodeSingleOrNull<NotificationPrefRow>()
            ?.prefs
            ?: emptyMap()

    private fun currentUserId(): String? = supabaseClient.auth.currentUserOrNull()?.id

    /**
     * Maps to a single row of the `notification_prefs` table.
     *
     * @property userId Supabase auth user id (primary key).
     * @property prefs The `event_type -> boolean` jsonb map.
     */
    @Serializable
    private data class NotificationPrefRow(
        @SerialName("user_id") val userId: String,
        @SerialName("prefs") val prefs: Map<String, Boolean>,
    )
}
