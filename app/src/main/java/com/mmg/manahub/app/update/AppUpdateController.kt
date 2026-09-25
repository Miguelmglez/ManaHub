package com.mmg.manahub.app.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallException
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallErrorCode
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.domain.config.AppUpdatePolicy
import com.mmg.manahub.core.domain.config.RemoteConfigRepository
import com.mmg.manahub.core.domain.update.AppUpdateRequirement
import com.mmg.manahub.core.domain.update.AppUpdateState
import com.mmg.manahub.core.domain.update.AppUpdateStatusProvider
import com.mmg.manahub.core.domain.update.EvaluateAppUpdateRequirementUseCase
import com.mmg.manahub.core.util.recordNonFatal
import com.mmg.manahub.core.util.recordSafeNonFatal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.lang.ref.WeakReference

/**
 * Single owner of Google Play in-app updates, combined with the Remote Config force-update policy.
 *
 * App-scoped singleton; the hosting Activity calls [bind] from `onCreate` so the controller can
 * register its result launcher and follow the Activity lifecycle (listener registration,
 * resume checks for downloaded / in-progress updates, cleanup on destroy).
 */
class AppUpdateController(
    private val appContext: Context,
    remoteConfigRepository: RemoteConfigRepository,
    private val evaluateAppUpdateRequirement: EvaluateAppUpdateRequirementUseCase,
    private val currentVersionCode: Long,
) : AppUpdateStatusProvider, DefaultLifecycleObserver {

    private sealed interface PlayStatus {
        data object Unknown : PlayStatus
        data class Available(val flexibleAllowed: Boolean, val immediateAllowed: Boolean) : PlayStatus
        data object Downloading : PlayStatus
        data object Downloaded : PlayStatus
    }

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playStatus = MutableStateFlow<PlayStatus>(PlayStatus.Unknown)
    private val remoteConfig = remoteConfigRepository.config

    private var activityRef: WeakReference<ComponentActivity>? = null
    private var launcher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var installListenerRegistered = false
    private var lastStartedUpdateType: Int? = null

    private val installListener = InstallStateUpdatedListener { installState ->
        when (installState.installStatus()) {
            InstallStatus.PENDING, InstallStatus.DOWNLOADING -> playStatus.value = PlayStatus.Downloading
            InstallStatus.DOWNLOADED -> markDownloaded()
            InstallStatus.INSTALLED -> playStatus.value = PlayStatus.Unknown
            InstallStatus.FAILED, InstallStatus.CANCELED -> checkForUpdate()
            else -> Unit
        }
    }

    override val state: StateFlow<AppUpdateState> =
        combine(playStatus, remoteConfig) { play, config -> resolveState(play, config.appUpdatePolicy) }
            .stateIn(scope, SharingStarted.Eagerly, AppUpdateState.None)

    /**
     * Attaches the controller to [activity]. Must be called from `onCreate`, before the Activity is
     * STARTED, because it registers an Activity Result launcher.
     */
    fun bind(activity: ComponentActivity) {
        activityRef = WeakReference(activity)
        launcher = activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result -> onFlowResult(result.resultCode) }
        activity.lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        if (!installListenerRegistered) {
            appUpdateManager.registerListener(installListener)
            installListenerRegistered = true
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        checkForUpdate()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        owner.lifecycle.removeObserver(this)
        // A newer Activity instance may already be bound; only tear down our own binding.
        if (activityRef?.get() !== owner) return
        activityRef = null
        launcher = null
        if (installListenerRegistered) {
            appUpdateManager.unregisterListener(installListener)
            installListenerRegistered = false
        }
    }

    override fun requestUpdate() {
        when (state.value) {
            AppUpdateState.Downloaded -> {
                completeUpdate()
                return
            }
            AppUpdateState.Downloading -> return
            else -> Unit
        }
        scope.launch {
            val forced = isForced()
            val info = fetchUpdateInfo() ?: run {
                openStoreListing()
                return@launch
            }
            val availability = info.updateAvailability()
            val type = when {
                forced && availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> AppUpdateType.IMMEDIATE
                availability != UpdateAvailability.UPDATE_AVAILABLE -> null
                forced -> AppUpdateType.IMMEDIATE.takeIf { info.isUpdateTypeAllowed(it) }
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> AppUpdateType.FLEXIBLE
                info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> AppUpdateType.IMMEDIATE
                else -> null
            }
            if (type == null || !startUpdateFlow(info, type)) openStoreListing()
        }
    }

    override fun completeUpdate() {
        FirebaseCrashlytics.getInstance().log("app_update_completed_requested")
        appUpdateManager.completeUpdate().addOnFailureListener { e ->
            recordSafeNonFatal("app_update_complete_failed", e)
        }
    }

    private fun checkForUpdate() {
        scope.launch {
            val info = fetchUpdateInfo() ?: return@launch
            val availability = info.updateAvailability()
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("app_update_availability", availabilityName(availability))
                log("app_update_check_result")
            }
            val installStatus = info.installStatus()
            when {
                installStatus == InstallStatus.DOWNLOADED -> markDownloaded()
                availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS &&
                    (lastStartedUpdateType == AppUpdateType.IMMEDIATE || isForced()) ->
                    startUpdateFlow(info, AppUpdateType.IMMEDIATE)
                installStatus == InstallStatus.PENDING || installStatus == InstallStatus.DOWNLOADING ->
                    playStatus.value = PlayStatus.Downloading
                availability == UpdateAvailability.UPDATE_AVAILABLE ->
                    playStatus.value = PlayStatus.Available(
                        flexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
                        immediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
                    )
                else -> playStatus.value = PlayStatus.Unknown
            }
        }
    }

    private suspend fun fetchUpdateInfo(): AppUpdateInfo? = try {
        appUpdateManager.appUpdateInfo.await()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onCheckFailed(e)
        null
    }

    private fun onCheckFailed(e: Exception) {
        val errorCode = (e as? InstallException)?.errorCode
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCustomKey("app_update_error_code", errorCode ?: UNKNOWN_ERROR_CODE)
        // Sideloaded / non-Play installs are expected; they must not flood non-fatals.
        if (errorCode == InstallErrorCode.ERROR_APP_NOT_OWNED || errorCode == InstallErrorCode.ERROR_API_NOT_AVAILABLE) {
            crashlytics.log("app_update_check_failed")
        } else {
            recordNonFatal("app_update_check_failed", e)
        }
    }

    // Each AppUpdateInfo can start a flow only once, so callers always pass a freshly fetched one.
    private fun startUpdateFlow(info: AppUpdateInfo, type: Int): Boolean {
        val activeLauncher = launcher ?: return false
        val started = try {
            appUpdateManager.startUpdateFlowForResult(
                info,
                activeLauncher,
                AppUpdateOptions.newBuilder(type).build(),
            )
        } catch (e: Exception) {
            recordSafeNonFatal("app_update_flow_start_failed", e)
            false
        }
        if (started) {
            lastStartedUpdateType = type
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("app_update_type", if (type == AppUpdateType.IMMEDIATE) "immediate" else "flexible")
                log("app_update_flow_started")
            }
        }
        return started
    }

    private fun onFlowResult(resultCode: Int) {
        val result = when (resultCode) {
            Activity.RESULT_OK -> "ok"
            Activity.RESULT_CANCELED -> "cancelled"
            ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> "failed"
            else -> "other"
        }
        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("app_update_flow_result", result)
            log("app_update_flow_result")
        }
        if (resultCode != Activity.RESULT_OK) checkForUpdate()
    }

    private fun markDownloaded() {
        if (playStatus.value == PlayStatus.Downloaded) return
        FirebaseCrashlytics.getInstance().log("app_update_downloaded")
        playStatus.value = PlayStatus.Downloaded
    }

    private fun openStoreListing() {
        val packageName = appContext.packageName
        val context: Context = activityRef?.get() ?: appContext
        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("app_update_type", "store_listing")
            log("app_update_flow_started")
        }
        val opened = listOf(
            "market://details?id=$packageName",
            "https://play.google.com/store/apps/details?id=$packageName",
        ).any { url ->
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
        if (!opened) recordNonFatal("app_update_store_listing_unavailable")
    }

    private fun isForced(): Boolean =
        evaluateAppUpdateRequirement(currentVersionCode, remoteConfig.value.appUpdatePolicy) ==
            AppUpdateRequirement.Forced

    private fun resolveState(play: PlayStatus, policy: AppUpdatePolicy): AppUpdateState {
        val requirement = evaluateAppUpdateRequirement(currentVersionCode, policy)
        if (requirement == AppUpdateRequirement.Forced) {
            return AppUpdateState.Forced(
                message = policy.forceUpdateMessage,
                minSupportedVersionCode = policy.minSupportedVersionCode,
            )
        }
        return when (play) {
            PlayStatus.Downloaded -> AppUpdateState.Downloaded
            PlayStatus.Downloading -> AppUpdateState.Downloading
            is PlayStatus.Available ->
                AppUpdateState.Available(inAppFlowAvailable = play.flexibleAllowed || play.immediateAllowed)
            PlayStatus.Unknown ->
                if (requirement == AppUpdateRequirement.Optional) {
                    AppUpdateState.Available(inAppFlowAvailable = false)
                } else {
                    AppUpdateState.None
                }
        }
    }

    private fun availabilityName(availability: Int): String = when (availability) {
        UpdateAvailability.UPDATE_NOT_AVAILABLE -> "UPDATE_NOT_AVAILABLE"
        UpdateAvailability.UPDATE_AVAILABLE -> "UPDATE_AVAILABLE"
        UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> "DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS"
        else -> "UNKNOWN"
    }

    private companion object {
        const val UNKNOWN_ERROR_CODE = -1
    }
}
