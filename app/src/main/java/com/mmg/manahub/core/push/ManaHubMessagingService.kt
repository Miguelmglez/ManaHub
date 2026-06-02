package com.mmg.manahub.core.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mmg.manahub.R
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.repository.PushTokenRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ManaHubMessagingService : FirebaseMessagingService() {

    // Service-scoped coroutine scope so in-flight work is cancelled when the service is destroyed.
    // SupervisorJob ensures one failing child does not cancel the scope.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNewToken(token: String) {
        // FirebaseMessagingService is not Hilt-injectable, so resolve the repository via an EntryPoint.
        val repo = EntryPointAccessors
            .fromApplication(applicationContext, PushEntryPoint::class.java)
            .pushTokenRepository()
        serviceScope.launch {
            runCatching { repo.register(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"] ?: return
        val title = data["title"] ?: message.notification?.title ?: return
        val body = data["body"] ?: message.notification?.body ?: return
        val deeplink = data["deeplink"]
        val threadId = data["thread_id"]?.takeIf { it.isNotEmpty() }
        val entityId = data["entity_id"] ?: ""
        val eventId = data["event_id"] ?: ""

        // Foreground suppression: skip if the user is already viewing this exact screen.
        if (deeplink != null && ForegroundScreenTracker.isViewingDeeplink(deeplink)) {
            // Breadcrumb only — no deeplink or user-identifiable data is logged.
            FirebaseCrashlytics.getInstance().log("FCM suppressed (foreground): type=$type")
            return
        }

        // Rollout gate: never display a notification while the feature flag is off,
        // regardless of what FCM delivered. runBlocking is safe here because
        // onMessageReceived is already invoked off the main thread.
        val dataStore = EntryPointAccessors
            .fromApplication(applicationContext, PushEntryPoint::class.java)
            .userPreferencesDataStore()
        val pushEnabled = runBlocking { dataStore.pushNotificationsEnabledFlow.first() }
        if (!pushEnabled) return

        val channelId = when {
            type == "trade_proposed" || type == "trade_countered" || type == "trade_accepted" -> "trades_high"
            type.startsWith("friend") -> "friends"
            else -> "trades_updates"
        }

        // Breadcrumb before building the notification — no PII, only type and eventId.
        FirebaseCrashlytics.getInstance().log("FCM received: type=$type eventId=$eventId")

        // Log push_received event — no PII, only event type and channel.
        FirebaseAnalytics.getInstance(this).logEvent("push_received") {
            param("event_type", type)
            param("channel_id", channelId)
        }

        val notificationId = eventId.hashCode()
        // Grouping tag: collapse repeated notifications for the same thread/friend.
        val tag = threadId?.let { "thread:$it" } ?: "friend:$entityId"

        val pendingIntent = deeplink?.let { link ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
                setPackage(packageName)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            PendingIntent.getActivity(
                this, notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        getSystemService(NotificationManager::class.java).notify(tag, notificationId, notification)

        // Log push_shown event — same non-PII params as push_received.
        FirebaseAnalytics.getInstance(this).logEvent("push_shown") {
            param("event_type", type)
            param("channel_id", channelId)
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PushEntryPoint {
        fun pushTokenRepository(): PushTokenRepository
        fun userPreferencesDataStore(): UserPreferencesDataStore
    }
}
