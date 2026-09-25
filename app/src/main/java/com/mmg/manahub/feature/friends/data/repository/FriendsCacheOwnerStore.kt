package com.mmg.manahub.feature.friends.data.repository

import com.mmg.manahub.core.data.local.UserPreferencesDataStore

/** Persists which account the local friends cache belongs to. */
interface FriendsCacheOwnerStore {
    suspend fun get(): String?
    suspend fun set(userId: String?)
}

/** [FriendsCacheOwnerStore] backed by the shared preferences DataStore. */
class DataStoreFriendsCacheOwnerStore(
    private val preferences: UserPreferencesDataStore,
) : FriendsCacheOwnerStore {
    override suspend fun get(): String? = preferences.getFriendsCacheOwnerUserId()
    override suspend fun set(userId: String?) = preferences.setFriendsCacheOwnerUserId(userId)
}
