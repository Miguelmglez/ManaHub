package com.mmg.manahub.core.data.repository

import android.util.Log
import androidx.work.WorkManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.push.PushTokenRemoteDataSource
import com.mmg.manahub.core.domain.repository.PushTokenRepository
import com.mmg.manahub.core.push.RegisterPushTokenWorker
import com.mmg.manahub.core.push.UnregisterPushTokenWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushTokenRepositoryImpl @Inject constructor(
    private val dataSource: PushTokenRemoteDataSource,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val workManager: WorkManager,
) : PushTokenRepository {

    override suspend fun register(token: String) {
        val locale = userPreferencesDataStore.preferencesFlow.first().appLanguage.code
        runCatching { dataSource.upsert(token, locale) }
            .onFailure {
                Log.w(TAG, "register failed, enqueuing retry worker", it)
                FirebaseCrashlytics.getInstance().recordException(it)
                RegisterPushTokenWorker.enqueue(workManager, token, locale)
            }
    }

    override suspend fun unregister(token: String) {
        runCatching { dataSource.delete(token) }
            .onFailure {
                Log.w(TAG, "unregister failed, enqueuing retry worker", it)
                FirebaseCrashlytics.getInstance().recordException(it)
                UnregisterPushTokenWorker.enqueue(workManager, token)
            }
    }

    override suspend fun unregisterAll() {
        // Best-effort: the account is being deleted, so a failure here is non-fatal.
        runCatching { dataSource.deleteAll() }
            .onFailure { Log.w(TAG, "unregisterAll failed (account deletion in progress)", it) }
    }

    override suspend fun updateLocale(locale: String) {
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            dataSource.upsert(token, locale)
        }.onFailure { Log.w(TAG, "updateLocale failed", it) }
    }

    private companion object {
        const val TAG = "PushTokenRepository"
    }
}
