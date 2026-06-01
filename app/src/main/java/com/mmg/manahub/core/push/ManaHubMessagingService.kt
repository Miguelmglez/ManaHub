package com.mmg.manahub.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ManaHubMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Phase 5 will inject PushTokenRepository here via Hilt EntryPoint and register the token.
        // For now, just log it.
        android.util.Log.d("ManaHubFCM", "New FCM token received (registration handled in Phase 5)")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Phase 6 will implement the full notification builder and routing here.
        android.util.Log.d("ManaHubFCM", "FCM message received: type=${message.data["type"]}")
    }
}
