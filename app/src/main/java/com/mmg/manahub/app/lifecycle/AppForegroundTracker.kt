package com.mmg.manahub.app.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Emits once each time the app moves from no started activity to one (process-level ON_START),
 * without pulling in `lifecycle-process`. Register it with [Application.registerActivityLifecycleCallbacks].
 */
class AppForegroundTracker : Application.ActivityLifecycleCallbacks {

    private var startedActivities = 0

    private val _foregroundEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** One emission per foreground transition; no replay. */
    val foregroundEvents: SharedFlow<Unit> = _foregroundEvents.asSharedFlow()

    override fun onActivityStarted(activity: Activity) {
        // Callbacks arrive on the main thread, so the counter needs no synchronization.
        if (startedActivities++ == 0) _foregroundEvents.tryEmit(Unit)
    }

    override fun onActivityStopped(activity: Activity) {
        if (startedActivities > 0) startedActivities--
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
