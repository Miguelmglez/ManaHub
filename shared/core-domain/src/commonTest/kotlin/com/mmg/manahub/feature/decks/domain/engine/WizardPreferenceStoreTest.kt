package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.common.KeyValueStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeKeyValueStore : KeyValueStore {
    private val strings = mutableMapOf<String, String>()
    private val booleans = mutableMapOf<String, Boolean>()
    override suspend fun getString(key: String, default: String?): String? = strings[key] ?: default
    override suspend fun putString(key: String, value: String) { strings[key] = value }
    override suspend fun getBoolean(key: String, default: Boolean): Boolean = booleans[key] ?: default
    override suspend fun putBoolean(key: String, value: Boolean) { booleans[key] = value }
    override suspend fun remove(key: String) { strings.remove(key); booleans.remove(key) }
    override suspend fun clear() { strings.clear(); booleans.clear() }
}

/** W6 Task 5 (E8) gate: [KeyValueWizardPreferenceStore]'s move-to-front, dedup, and cap contract. */
class WizardPreferenceStoreTest {

    @Test
    fun `recordPick moves an existing id to the front instead of duplicating it`() = runTest {
        val store = KeyValueWizardPreferenceStore(FakeKeyValueStore())
        store.recordPick("a")
        store.recordPick("b")
        store.recordPick("a")
        assertEquals(listOf("a", "b"), store.preferredCardIds())
    }

    @Test
    fun `recordPick caps the remembered list at MAX_PREFERENCES, dropping the oldest`() = runTest {
        val store = KeyValueWizardPreferenceStore(FakeKeyValueStore())
        repeat(WizardPreferenceStore.MAX_PREFERENCES + 5) { i -> store.recordPick("card-$i") }
        val ids = store.preferredCardIds()
        assertEquals(WizardPreferenceStore.MAX_PREFERENCES, ids.size)
        assertEquals("card-${WizardPreferenceStore.MAX_PREFERENCES + 4}", ids.first(), "the most recent pick must be first")
    }

    @Test
    fun `a fresh store has no preferences`() = runTest {
        val store = KeyValueWizardPreferenceStore(FakeKeyValueStore())
        assertEquals(emptyList(), store.preferredCardIds())
    }
}
