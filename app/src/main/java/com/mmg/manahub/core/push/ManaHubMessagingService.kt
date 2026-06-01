package com.mmg.manahub.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mmg.manahub.core.domain.repository.PushTokenRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ManaHubMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // FirebaseMessagingService is not Hilt-injectable, so resolve the repository via an EntryPoint.
        val repo = EntryPointAccessors
            .fromApplication(applicationContext, PushEntryPoint::class.java)
            .pushTokenRepository()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { repo.register(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Phase 6 will implement the full notification builder and routing here.
        android.util.Log.d("ManaHubFCM", "FCM message received: type=${message.data["type"]}")
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PushEntryPoint {
        fun pushTokenRepository(): PushTokenRepository
    }
}
