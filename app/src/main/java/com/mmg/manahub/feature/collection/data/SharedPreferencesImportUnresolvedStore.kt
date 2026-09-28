package com.mmg.manahub.feature.collection.data

import android.content.Context
import androidx.core.content.edit
import com.mmg.manahub.core.data.queue.SharedPreferencesCardQueueStore
import com.mmg.manahub.core.domain.collection.transfer.CollectionImportUnresolvedStore
import com.mmg.manahub.core.domain.collection.transfer.MAX_PERSISTED_UNRESOLVED_LINES

/**
 * [CollectionImportUnresolvedStore] over the same preference file as the import review queue, so
 * the two are restored together. Lines are newline-joined: they come from `String.lines()` and can
 * therefore never contain a newline themselves.
 */
class SharedPreferencesImportUnresolvedStore(context: Context) : CollectionImportUnresolvedStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(SharedPreferencesCardQueueStore.COLLECTION_IMPORT_PREF_FILE, Context.MODE_PRIVATE)

    override fun read(): List<String> =
        prefs.getString(SharedPreferencesCardQueueStore.COLLECTION_IMPORT_UNRESOLVED_KEY, null)
            ?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.toList()
            .orEmpty()

    override fun write(lines: List<String>) {
        prefs.edit {
            if (lines.isEmpty()) {
                remove(SharedPreferencesCardQueueStore.COLLECTION_IMPORT_UNRESOLVED_KEY)
            } else {
                putString(
                    SharedPreferencesCardQueueStore.COLLECTION_IMPORT_UNRESOLVED_KEY,
                    lines.take(MAX_PERSISTED_UNRESOLVED_LINES).joinToString("\n"),
                )
            }
        }
    }
}
