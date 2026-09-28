package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.domain.repository.NotificationPrefsRepository
import com.mmg.manahub.core.domain.repository.NotificationPrefsUnauthenticatedException
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Backend access for the `notification_prefs` row of one user; seam so the repository is unit-testable. */
interface NotificationPrefsRemoteSource {
    /** Persisted prefs map for [userId], or an empty map when no row exists. */
    suspend fun fetch(userId: String): Map<String, Boolean>
    suspend fun upsert(userId: String, prefs: Map<String, Boolean>)
}

/** PostgREST-backed [NotificationPrefsRemoteSource] over the `notification_prefs` table. */
class SupabaseNotificationPrefsRemoteSource(
    private val supabaseClient: SupabaseClient,
) : NotificationPrefsRemoteSource {

    override suspend fun fetch(userId: String): Map<String, Boolean> =
        supabaseClient.postgrest["notification_prefs"]
            .select { filter { eq("user_id", userId) } }
            .decodeSingleOrNull<NotificationPrefRow>()
            ?.prefs
            ?: emptyMap()

    override suspend fun upsert(userId: String, prefs: Map<String, Boolean>) {
        supabaseClient.postgrest["notification_prefs"]
            .upsert(NotificationPrefRow(userId = userId, prefs = prefs))
    }

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

/**
 * Supabase-backed implementation of [NotificationPrefsRepository].
 *
 * Storage model: a single row per user in the `notification_prefs` table, where the
 * `prefs` jsonb column holds the `event_type -> boolean` map. A missing key means the
 * event is enabled, so we only persist explicit overrides.
 *
 * The cache is keyed by the signed-in user: any access compares the current auth user with
 * the user whose prefs are cached and reloads (or clears) on a mismatch, so a sign-out /
 * sign-in on this singleton never leaks the previous account's map.
 */
class NotificationPrefsRepositoryImpl(
    private val remote: NotificationPrefsRemoteSource,
    private val currentUserId: () -> String?,
    private val dispatcherProvider: DispatcherProvider,
) : NotificationPrefsRepository {

    constructor(supabaseClient: SupabaseClient, dispatcherProvider: DispatcherProvider) : this(
        remote = SupabaseNotificationPrefsRemoteSource(supabaseClient),
        currentUserId = { supabaseClient.auth.currentUserOrNull()?.id },
        dispatcherProvider = dispatcherProvider,
    )

    private val _prefsFlow = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /** Guards the read-merge-upsert critical section against concurrent toggles. */
    private val writeMutex = Mutex()

    /** Guards [loadedUserId] and the load itself against concurrent subscribers. */
    private val loadMutex = Mutex()

    /** User whose prefs [_prefsFlow] currently holds; null until a fetch for a signed-in user succeeds. */
    private var loadedUserId: String? = null

    override val prefsFlow: Flow<Map<String, Boolean>> =
        _prefsFlow.asStateFlow().onStart { loadForCurrentUser(force = false) }

    override suspend fun setEventEnabled(eventType: String, enabled: Boolean): Result<Unit> =
        setEventsEnabled(listOf(eventType), enabled)

    override suspend fun setEventsEnabled(eventTypes: List<String>, enabled: Boolean): Result<Unit> {
        if (eventTypes.isEmpty()) return Result.success(Unit)
        val userId = currentUserId() ?: return Result.failure(NotificationPrefsUnauthenticatedException())
        return writeMutex.withLock {
            val previous = _prefsFlow.value
            // Optimistic publish so the switches move at once; rolled back below on failure
            _prefsFlow.value = previous + eventTypes.associateWith { enabled }
            runCatching {
                withContext(dispatcherProvider.io) {
                    // Merge onto the freshest server state, not the local cache a concurrent write may have changed
                    val merged = remote.fetch(userId).toMutableMap()
                    eventTypes.forEach { merged[it] = enabled }
                    remote.upsert(userId, merged)
                    merged
                }
            }.onSuccess { merged ->
                _prefsFlow.value = merged
                loadMutex.withLock { loadedUserId = userId }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _prefsFlow.value = previous
            }.map { }
        }
    }

    override suspend fun isEventEnabled(eventType: String): Boolean {
        // A missing key defaults to enabled (opt-out model).
        return currentPrefs()[eventType] ?: true
    }

    override suspend fun refresh(): Result<Unit> = loadForCurrentUser(force = true)

    /**
     * Ensures [_prefsFlow] holds the signed-in user's map. Clears the cache when nobody is signed
     * in or the user changed; fetches when not yet loaded for this user (or [force]).
     */
    private suspend fun loadForCurrentUser(force: Boolean): Result<Unit> = loadMutex.withLock {
        val userId = currentUserId()
        if (userId == null) {
            loadedUserId = null
            _prefsFlow.value = emptyMap()
            return@withLock Result.success(Unit)
        }
        if (!force && loadedUserId == userId) return@withLock Result.success(Unit)
        if (loadedUserId != userId) {
            // Never show another account's overrides while this user's row is in flight
            loadedUserId = null
            _prefsFlow.value = emptyMap()
        }
        runCatching { withContext(dispatcherProvider.io) { remote.fetch(userId) } }
            .onSuccess { prefs ->
                _prefsFlow.value = prefs
                loadedUserId = userId
            }
            .onFailure { e -> if (e is CancellationException) throw e }
            .map { }
    }

    /**
     * Returns the most relevant preference map: the cache once loaded for the signed-in user,
     * otherwise a fresh remote fetch. Falls back to an empty map (all enabled) on any failure.
     */
    private suspend fun currentPrefs(): Map<String, Boolean> {
        val userId = currentUserId() ?: return emptyMap()
        val cached = loadMutex.withLock { if (loadedUserId == userId) _prefsFlow.value else null }
        if (cached != null) return cached
        return runCatching { withContext(dispatcherProvider.io) { remote.fetch(userId) } }
            .onFailure { e -> if (e is CancellationException) throw e }
            .getOrDefault(emptyMap())
    }
}
