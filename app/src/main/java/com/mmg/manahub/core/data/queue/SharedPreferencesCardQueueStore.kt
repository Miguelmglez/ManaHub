package com.mmg.manahub.core.data.queue

import android.content.Context
import androidx.core.content.edit

/**
 * Android [CardQueueStore] over the `scanner_prefs` / `scanner_queue_v1` SharedPreferences entry
 * the scanner has always used, so existing user queues survive the move to the shared queue.
 */
class SharedPreferencesCardQueueStore(context: Context) : CardQueueStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(PREF_KEY_QUEUE, null)

    override fun write(payload: String) {
        prefs.edit { putString(PREF_KEY_QUEUE, payload) }
    }

    private companion object {
        const val PREF_FILE = "scanner_prefs"
        const val PREF_KEY_QUEUE = "scanner_queue_v1"
    }
}
