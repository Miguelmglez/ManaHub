package com.mmg.manahub.core.gamification.domain.model

import com.mmg.manahub.core.gamification.domain.model.EquippedCosmetics.Companion.MAX_EQUIPPED_BADGES


/**
 * The player's currently-equipped cosmetics (Phase 3).
 *
 * Equipped selection is a presentation choice persisted in DataStore (NOT in `entitlements`, which only
 * records ownership). A null/absent slot means "nothing equipped". At most [MAX_EQUIPPED_BADGES] badges
 * may be equipped at once.
 *
 * Chunk B's Profile hero reads this to render the equipped title, badges, avatar frame, and level-ring
 * style. All ids are stable [com.mmg.manahub.core.gamification.domain.catalog.UnlockableId] strings.
 *
 * @param titleId equipped TITLE id, or null.
 * @param badgeIds equipped BADGE ids in display order (size 0..[MAX_EQUIPPED_BADGES]).
 * @param avatarFrameId equipped AVATAR_FRAME id, or null.
 * @param levelRingStyleId equipped LEVEL_RING_STYLE id, or null.
 */
data class EquippedCosmetics(
    val titleId: String? = null,
    val badgeIds: List<String> = emptyList(),
    val avatarFrameId: String? = null,
    val levelRingStyleId: String? = null,
) {
    companion object {
        /** Maximum number of badges that can be equipped simultaneously. */
        const val MAX_EQUIPPED_BADGES: Int = 3

        /** Nothing equipped. */
        val NONE: EquippedCosmetics = EquippedCosmetics()
    }
}
