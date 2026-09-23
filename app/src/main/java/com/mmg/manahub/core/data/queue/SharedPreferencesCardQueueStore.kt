package com.mmg.manahub.core.data.queue

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

/**
 * Android [CardQueueStore] over a SharedPreferences entry. The default [fileName] + [key] are the
 * `scanner_prefs`/`scanner_queue_v1` entry the scanner has always used, so existing user queues
 * survive the move to the shared queue; [deckQueueKey] names the Deck Scanner's per-deck queues.
 *
 * @param blockingWrite writes with `commit()` instead of `apply()`. Only for a caller that already
 *   writes off the main thread: `apply()` defers a large payload to QueuedWork, which then blocks
 *   the main thread while it drains at `onPause`.
 */
class SharedPreferencesCardQueueStore(
    context: Context,
    private val key: String = PREF_KEY_QUEUE,
    fileName: String = PREF_FILE,
    private val blockingWrite: Boolean = false,
) : CardQueueStore {

    private val prefs = context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(key, null)

    override fun write(payload: String) {
        prefs.edit(commit = blockingWrite) { putString(key, payload) }
    }

    companion object {
        private const val PREF_FILE = "scanner_prefs"
        private const val PREF_KEY_QUEUE = "scanner_queue_v1"
        private const val PREF_KEY_DECK_QUEUE_PREFIX = "scanner_deck_queue_v1_"

        /**
         * Preference FILE of the Collection import review queue. Its own file, not `scanner_prefs`:
         * SharedPreferences rewrites the whole file on every write, so an MB-scale import payload
         * would be re-serialised by every unrelated scanner write and stay resident in memory.
         */
        const val COLLECTION_IMPORT_PREF_FILE = "collection_import_prefs"

        /** Preference key of the Collection import review queue (never the shared queue). */
        const val COLLECTION_IMPORT_QUEUE_KEY = "collection_import_queue_v1"

        /** Preference key of the import lines no card could be resolved for. */
        const val COLLECTION_IMPORT_UNRESOLVED_KEY = "collection_import_unresolved_v1"

        /** Preference key of the Deck Scanner queue for [deckId] (one queue per deck). */
        fun deckQueueKey(deckId: String): String = "$PREF_KEY_DECK_QUEUE_PREFIX${Uri.encode(deckId)}"
    }
}
