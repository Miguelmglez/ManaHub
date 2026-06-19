package com.mmg.manahub.feature.profile.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.gamification.domain.catalog.AchievementCatalog
import com.mmg.manahub.core.gamification.domain.catalog.UnlockRule
import com.mmg.manahub.core.gamification.domain.catalog.UnlockableKind
import com.mmg.manahub.core.gamification.domain.model.RewardUiModel
import com.mmg.manahub.core.gamification.domain.model.RewardsBoard
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.gamification.presentation.RewardPreview

/**
 * The Rewards tab body (ADR-002, Phase 3, Chunk B). Renders the full cosmetics catalog grouped by
 * [UnlockableKind] with a "N / M unlocked" header, driven entirely off [board] so locked items still
 * render with a "how to unlock" hint.
 *
 * Layout is a SINGLE [LazyVerticalGrid] (2 cols) to avoid nested same-axis scroll containers: the
 * header and each section title occupy a full-span item, and the cells flow as 2-column grid items
 * keyed by the stable [RewardUiModel.id] (ids are unique across the board, never index).
 *
 * Stateless: equip/unequip are hoisted to the ViewModel. Locked cells are not tappable to equip.
 *
 * @param board the full rewards board (every cosmetic, owned/locked/equipped flagged).
 * @param onEquip invoked when an owned, not-equipped cell is tapped.
 * @param onUnequip invoked when an owned, equipped cell is tapped.
 * @param contentPadding bottom inset so the grid clears the nav bar.
 */
@Composable
fun RewardsTab(
    board: RewardsBoard,
    onEquip: (RewardUiModel) -> Unit,
    onUnequip: (RewardUiModel) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    if (board.totalCount == 0) {
        EmptyState(
            icon = Icons.Default.CardGiftcard,
            title = stringResource(R.string.rewards_empty_title),
            subtitle = stringResource(R.string.rewards_empty_desc),
            modifier = modifier,
        )
        return
    }

    // Fixed display order of sections: titles → badges → frames → rings.
    val sections = listOf(
        UnlockableKind.TITLE to R.string.reward_section_titles,
        UnlockableKind.BADGE to R.string.reward_section_badges,
        UnlockableKind.AVATAR_FRAME to R.string.reward_section_frames,
        UnlockableKind.LEVEL_RING_STYLE to R.string.reward_section_rings,
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        // Header (full span).
        item(key = "rewards_header", span = { GridItemSpan(maxLineSpan) }) {
            RewardsHeader(
                ownedCount = board.ownedCount,
                totalCount = board.totalCount,
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.spacing.lg,
                    vertical = MaterialTheme.spacing.sm,
                ),
            )
        }

        sections.forEach { (kind, sectionTitleRes) ->
            val items = board.byKind[kind].orEmpty()
            if (items.isNotEmpty()) {
                item(key = "section_${kind.name}", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(
                        titleRes = sectionTitleRes,
                        modifier = Modifier.padding(
                            start = MaterialTheme.spacing.lg,
                            end = MaterialTheme.spacing.lg,
                            top = MaterialTheme.spacing.sm,
                        ),
                    )
                }
                items(
                    count = items.size,
                    key = { index -> items[index].id },
                ) { index ->
                    val reward = items[index]
                    RewardCell(
                        reward = reward,
                        onEquip = { onEquip(reward) },
                        onUnequip = { onUnequip(reward) },
                        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.xs),
                    )
                }
            }
        }
    }
}

/** "N / M unlocked" header. */
@Composable
private fun RewardsHeader(
    ownedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.reward_count_summary, ownedCount, totalCount),
        style = MaterialTheme.magicTypography.titleMedium,
        color = MaterialTheme.magicColors.textPrimary,
        modifier = modifier,
    )
}

/** A section header (full-span row above a kind's cells). */
@Composable
private fun SectionHeader(@StringRes titleRes: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(titleRes).uppercase(),
        style = MaterialTheme.magicTypography.labelLarge,
        color = MaterialTheme.magicColors.primaryAccent,
        modifier = modifier,
    )
}

/**
 * A single reward cell: the procedural preview, the display name, and a state affordance.
 *
 * - Owned + equipped → highlighted border + "Equipped" marker; tapping invokes [onUnequip].
 * - Owned + not equipped → tappable; tapping invokes [onEquip].
 * - Locked → dimmed preview + lock glyph + the formatted unlock hint; NOT tappable to equip.
 *
 * Carries a merged [Role.Button] semantics with a meaningful description (name + owned/locked +
 * equipped state). Cell min-height keeps the touch target ≥48dp.
 */
@Composable
private fun RewardCell(
    reward: RewardUiModel,
    onEquip: () -> Unit,
    onUnequip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val name = stringResource(reward.displayNameRes)
    val locked = !reward.isOwned
    val equipped = reward.isEquipped

    val hint = if (locked) unlockHint(reward.unlockRule) else null
    val a11y = when {
        equipped -> stringResource(R.string.reward_cell_a11y_equipped, name)
        reward.isOwned -> stringResource(R.string.reward_cell_a11y_owned, name)
        else -> stringResource(R.string.reward_cell_a11y_locked, name, hint.orEmpty())
    }

    val borderColor = if (equipped) mc.primaryAccent else mc.surfaceVariant

    Surface(
        shape = CardShape,
        color = mc.surface,
        border = BorderStroke(width = if (equipped) 2.dp else 1.dp, color = borderColor),
        modifier = modifier
            .fillMaxWidth()
            // defaultMinSize (not a fixed height) so 2-line names/hints at large font scale don't clip.
            .defaultMinSize(minHeight = 150.dp)
            .selectable(
                selected = equipped,
                enabled = reward.isOwned,
                role = Role.Button,
                onClick = { if (equipped) onUnequip() else onEquip() },
            )
            .clearAndSetSemantics { contentDescription = a11y },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs, Alignment.CenterVertically),
        ) {
            // Preview (dimmed when locked).
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .alpha(if (locked) 0.35f else 1f),
                contentAlignment = Alignment.Center,
            ) {
                RewardPreview(
                    kind = reward.kind,
                    renderSpec = reward.renderSpec,
                    name = name,
                    modifier = Modifier.size(64.dp),
                )
            }

            Text(
                text = name,
                style = MaterialTheme.magicTypography.labelLarge,
                color = if (locked) mc.textSecondary else mc.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            when {
                equipped -> EquippedMarker()
                locked -> LockedMarker(hint = hint.orEmpty())
                else -> Spacer(modifier = Modifier.height(MaterialTheme.spacing.md))
            }
        }
    }
}

/** Small "Equipped" pill shown on the equipped cell. */
@Composable
private fun EquippedMarker(modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    Surface(shape = ChipShape, color = mc.primaryAccent.copy(alpha = 0.18f), modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.sm, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.reward_equipped),
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.primaryAccent,
            )
        }
    }
}

/** Lock glyph + the formatted unlock hint shown on a locked cell. */
@Composable
private fun LockedMarker(hint: String, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = mc.textSecondary,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = hint,
            style = MaterialTheme.magicTypography.labelSmall,
            color = mc.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Formats the "how to unlock" hint for a locked cosmetic from its [UnlockRule].
 *
 * - [UnlockRule.LevelAtLeast] → "Reach level N".
 * - [UnlockRule.AchievementUnlocked] → "Unlock the <title> achievement", resolving the achievement's
 *   title from [AchievementCatalog]; if the id is unknown, falls back to the generic hint.
 */
@Composable
private fun unlockHint(rule: UnlockRule): String = when (rule) {
    is UnlockRule.LevelAtLeast -> stringResource(R.string.reward_unlock_hint_level, rule.level)
    is UnlockRule.AchievementUnlocked -> {
        val titleRes = AchievementCatalog.byId(rule.achievementId)?.titleRes
        if (titleRes != null) {
            stringResource(R.string.reward_unlock_hint_achievement, stringResource(titleRes))
        } else {
            stringResource(R.string.reward_unlock_hint_generic)
        }
    }
}
