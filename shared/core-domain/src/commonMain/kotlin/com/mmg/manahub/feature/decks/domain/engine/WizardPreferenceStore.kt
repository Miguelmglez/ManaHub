package com.mmg.manahub.feature.decks.domain.engine
// COMMENTS_REVIEWED: 2026-09-14

import com.mmg.manahub.core.common.KeyValueStore

/**
 * W6 Task 5 (E8) — cards the user previously chose on the (future, W7) Choice screen, remembered as
 * a small, bounded preference prior for later builds: same discipline as the retired community
 * prior — it may reorder a near-tie, it can never override a band need (see
 * [PlacementScorer.PREFERENCE_BONUS]'s own KDoc for why it is bounded well below any real
 * under-ideal contribution).
 */
interface WizardPreferenceStore {
    /** Records that the user picked [cardId] on the Choice screen. Move-to-front, deduplicated,
     * capped at [MAX_PREFERENCES] — the newest picks are the ones worth remembering. */
    suspend fun recordPick(cardId: String)

    /** Every remembered preferred card id, most-recent-first. */
    suspend fun preferredCardIds(): List<String>

    companion object {
        const val MAX_PREFERENCES = 50
    }
}

/** [KeyValueStore]-backed [WizardPreferenceStore] — a single comma-joined string key, no Room
 * migration needed (E8's own "no Room migration if a simple key-value store suffices"). */
class KeyValueWizardPreferenceStore(private val store: KeyValueStore) : WizardPreferenceStore {

    override suspend fun recordPick(cardId: String) {
        val updated = (listOf(cardId) + preferredCardIds().filterNot { it == cardId }).take(WizardPreferenceStore.MAX_PREFERENCES)
        store.putString(KEY, updated.joinToString(","))
    }

    override suspend fun preferredCardIds(): List<String> =
        store.getString(KEY)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

    private companion object {
        const val KEY = "wizard_preference_cards"
    }
}
