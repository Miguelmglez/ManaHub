package com.mmg.manahub.core.data.queue

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

/**
 * Android [CardQueueStore] over a `scanner_prefs` SharedPreferences entry. The default [key] is the
 * `scanner_queue_v1` entry the scanner has always used, so existing user queues survive the move to
 * the shared queue; [deckQueueKey] names the Deck Scanner's per-deck queues.
 */
class SharedPreferencesCardQueueStore(
    context: Context,
    private val key: String = PREF_KEY_QUEUE,
) : CardQueueStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(key, null)

    override fun write(payload: String) {
        prefs.edit { putString(key, payload) }
    }

    companion object {
        private const val PREF_FILE = "scanner_prefs"
        private const val PREF_KEY_QUEUE = "scanner_queue_v1"
        private const val PREF_KEY_DECK_QUEUE_PREFIX = "scanner_deck_queue_v1_"

        /** Preference key of the Collection import review queue (never the shared queue). */
        const val COLLECTION_IMPORT_QUEUE_KEY = "collection_import_queue_v1"

        /** Preference key of the Deck Scanner queue for [deckId] (one queue per deck). */
        fun deckQueueKey(deckId: String): String = "$PREF_KEY_DECK_QUEUE_PREFIX${Uri.encode(deckId)}"
    }
}
