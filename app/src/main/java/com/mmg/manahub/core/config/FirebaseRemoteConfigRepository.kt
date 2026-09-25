package com.mmg.manahub.core.config

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.mmg.manahub.core.domain.config.AppUpdatePolicy
import com.mmg.manahub.core.domain.config.KillSwitch
import com.mmg.manahub.core.domain.config.RemoteConfig
import com.mmg.manahub.core.domain.config.RemoteConfigKeys
import com.mmg.manahub.core.domain.config.RemoteConfigRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Firebase Remote Config backed [RemoteConfigRepository].
 *
 * Defaults come from [RemoteConfigKeys.defaults] (no XML defaults file). Activated values are
 * persisted by Firebase, so a forced-update state survives offline restarts after one fetch.
 * Every failure is a breadcrumb only: being offline is a normal condition.
 */
class FirebaseRemoteConfigRepository(
    private val remoteConfig: FirebaseRemoteConfig,
    private val isDebugBuild: Boolean,
) : RemoteConfigRepository {

    private val _config = MutableStateFlow(RemoteConfig.DEFAULT)
    override val config: StateFlow<RemoteConfig> = _config.asStateFlow()

    private val initMutex = Mutex()
    private var initialized = false
    private var realtimeListenerRegistered = false

    override suspend fun refresh() {
        ensureInitialized()
        try {
            val activated = remoteConfig.fetchAndActivate().await()
            if (activated) FirebaseCrashlytics.getInstance().log("remote_config_activated")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            FirebaseCrashlytics.getInstance().log("remote_config_fetch_failed")
        }
        publish()
    }

    private suspend fun ensureInitialized() = initMutex.withLock {
        if (initialized) return@withLock
        try {
            remoteConfig.setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(if (isDebugBuild) 0L else RELEASE_FETCH_INTERVAL_SECONDS)
                    .build(),
            ).await()
            remoteConfig.setDefaultsAsync(RemoteConfigKeys.defaults()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            FirebaseCrashlytics.getInstance().log("remote_config_fetch_failed")
        }
        initialized = true
        // Publishes values activated in a previous session before the network fetch completes.
        publish()
        registerRealtimeListener()
    }

    private fun registerRealtimeListener() {
        if (realtimeListenerRegistered) return
        realtimeListenerRegistered = true
        remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) {
                remoteConfig.activate().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        FirebaseCrashlytics.getInstance().log("remote_config_activated")
                        publish()
                    } else {
                        FirebaseCrashlytics.getInstance().log("remote_config_fetch_failed")
                    }
                }
            }

            override fun onError(error: FirebaseRemoteConfigException) {
                FirebaseCrashlytics.getInstance().log("remote_config_fetch_failed")
            }
        })
    }

    private fun publish() {
        runCatching { readConfig() }.onSuccess { _config.value = it }
    }

    private fun readConfig(): RemoteConfig = RemoteConfig(
        killedFeatures = KillSwitch.entries.filter { remoteConfig.getBoolean(it.remoteKey) }.toSet(),
        appUpdatePolicy = AppUpdatePolicy(
            minSupportedVersionCode = remoteConfig.getLong(RemoteConfigKeys.MIN_SUPPORTED_VERSION_CODE),
            latestVersionCode = remoteConfig.getLong(RemoteConfigKeys.LATEST_VERSION_CODE),
            forceUpdateMessage = remoteConfig.getString(RemoteConfigKeys.FORCE_UPDATE_MESSAGE),
        ),
    )

    private companion object {
        const val RELEASE_FETCH_INTERVAL_SECONDS = 3_600L
    }
}
