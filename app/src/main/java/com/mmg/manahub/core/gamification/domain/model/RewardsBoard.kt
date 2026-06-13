package com.mmg.manahub.core.gamification.domain.model

import com.mmg.manahub.core.gamification.domain.catalog.RenderSpec
import com.mmg.manahub.core.gamification.domain.catalog.UnlockRule
import com.mmg.manahub.core.gamification.domain.catalog.UnlockableKind

/**
 * One unlockable cosmetic as a UI-ready model for the Rewards tab (Phase 3, Chunk B).
 *
 * Joins the static [com.mmg.manahub.core.gamification.domain.catalog.UnlockableCatalog] with the
 * player's `entitlements` (ownership) and DataStore-equipped selection. LOCKED items are still
 * rendered (driven off the catalog) so the player can see what's available; [unlockRule] is exposed so
 * Chunk B can format a localized "how to unlock" hint (e.g. "Reach level 10" / "Unlock the Collector
 * achievement"). Formatting the hint in the UI layer keeps the domain free of string/`Context`
 * concerns.
 *
 * @param id stable unlockable id.
 * @param kind the cosmetic family.
 * @param displayNameRes English display name resource.
 * @param renderSpec procedural render description (Chunk B draws from this).
 * @param isOwned true when the player has an entitlement for [id].
 * @param isEquipped true when [id] is currently equipped in its slot.
 * @param unlockRule the condition that grants it (used by the UI to format the locked-state hint).
 */
data class RewardUiModel(
    val id: String,
    val kind: UnlockableKind,
    val displayNameRes: Int,
    val renderSpec: RenderSpec,
    val isOwned: Boolean,
    val isEquipped: Boolean,
    val unlockRule: UnlockRule,
)

/**
 * The full Rewards board (Phase 3): every catalog cosmetic grouped by kind, each item flagged
 * owned/equipped. Stable, catalog-ordered (so emissions don't reshuffle the grid). Chunk B renders one
 * section per [UnlockableKind].
 *
 * @param byKind cosmetics grouped by kind; each list is catalog-sorted (sortOrder then id).
 */
data class RewardsBoard(
    val byKind: Map<UnlockableKind, List<RewardUiModel>>,
) {
    /** Convenience: all reward models flattened (kind order is not guaranteed). */
    val all: List<RewardUiModel> get() = byKind.values.flatten()

    /** Count of owned cosmetics across all kinds (for a "12 / 21 unlocked" header). */
    val ownedCount: Int get() = all.count { it.isOwned }

    /** Total number of cosmetics in the catalog. */
    val totalCount: Int get() = all.size

    companion object {
        /** An empty board (no kinds) — used as a safe default before the first emission. */
        val EMPTY: RewardsBoard = RewardsBoard(emptyMap())
    }
}
