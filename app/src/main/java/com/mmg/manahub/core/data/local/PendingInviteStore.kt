package com.mmg.manahub.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Key used to persist a referral code that arrived before the user was authenticated. */
private val KEY_PENDING_INVITE_CODE = stringPreferencesKey("pending_invite_code")

/**
 * Stores a referral code received via deep link so it can be processed after the user
 * completes authentication. Uses the same [userPrefsDataStore] instance as
 * [UserPreferencesDataStore] and [SyncPreferencesStore] to avoid opening a second file.
 */
@Singleton
class PendingInviteStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Emits the stored referral code, or null when none is pending. */
    val flow: Flow<String?> = context.userPrefsDataStore.data
        .map { prefs -> prefs[KEY_PENDING_INVITE_CODE] }

    /** Persists [code] so it survives process death while the user logs in. */
    suspend fun save(code: String) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[KEY_PENDING_INVITE_CODE] = code
        }
    }

    /** Removes the stored code after it has been processed. */
    suspend fun clear() {
        context.userPrefsDataStore.edit { prefs ->
            prefs.remove(KEY_PENDING_INVITE_CODE)
        }
    }
}
