package com.mmg.manahub.feature.profile.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
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

private const val REWARD_COLUMNS = 2

/**
 * Emits the Rewards tab as items of the Profile screen's single [androidx.compose.foundation.lazy.LazyColumn]:
 * a "N / M unlocked" header, then each [UnlockableKind] section as a header plus rows of
 * [REWARD_COLUMNS] cells. Locked items render with a "how to unlock" hint and cannot be equipped.
 *
 * @param board the full rewards board (every cosmetic, owned/locked/equipped flagged).
 * @param onEquip invoked when an owned, not-equipped cell is tapped.
 * @param onUnequip invoked when an owned, equipped cell is tapped.
 */
fun LazyListScope.rewardsTabItems(
    board: RewardsBoard,
    onEquip: (RewardUiModel) -> Unit,
    onUnequip: (RewardUiModel) -> Unit,
) {
    if (board.totalCount == 0) {
        item(key = "rewards_empty") {
            EmptyState(
                icon = Icons.Default.CardGiftcard,
                title = stringResource(R.string.rewards_empty_title),
                subtitle = stringResource(R.string.rewards_empty_desc),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MaterialTheme.spacing.xxl),
            )
        }
        return
    }

    item(key = "rewards_header") {
        RewardsHeader(
            ownedCount = board.ownedCount,
            totalCount = board.totalCount,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.lg,
                vertical = MaterialTheme.spacing.sm,
            ),
        )
    }

    REWARD_SECTIONS.forEach { (kind, sectionTitleRes) ->
        val rewards = board.byKind[kind].orEmpty()
        if (rewards.isEmpty()) return@forEach
        item(key = "rewards_section_${kind.name}", contentType = "reward_section") {
            RewardSectionTitle(
                titleRes = sectionTitleRes,
                modifier = Modifier.padding(
                    start = MaterialTheme.spacing.lg,
                    end = MaterialTheme.spacing.lg,
                    top = MaterialTheme.spacing.sm,
                ),
            )
        }
        val rows = rewards.chunked(REWARD_COLUMNS)
        items(
            count = rows.size,
            key = { index -> "rewards_row_${kind.name}_${rows[index].first().id}" },
            contentType = { "reward_row" },
        ) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.sm, vertical = MaterialTheme.spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            ) {
                val row = rows[index]
                row.forEach { reward ->
                    RewardCell(
                        reward = reward,
                        onEquip = { onEquip(reward) },
                        onUnequip = { onUnequip(reward) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(REWARD_COLUMNS - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** Fixed display order of sections: titles → badges → frames → rings. */
private val REWARD_SECTIONS = listOf(
    UnlockableKind.TITLE to R.string.reward_section_titles,
    UnlockableKind.BADGE to R.string.reward_section_badges,
    UnlockableKind.AVATAR_FRAME to R.string.reward_section_frames,
    UnlockableKind.LEVEL_RING_STYLE to R.string.reward_section_rings,
)

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

/** A section title (full-width row above a kind's cells). */
@Composable
private fun RewardSectionTitle(@StringRes titleRes: Int, modifier: Modifier = Modifier) {
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
    val name = reward.displayName
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
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.sm, vertical = MaterialTheme.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs),
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
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs),
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
        val title = AchievementCatalog.byId(rule.achievementId)?.title
        if (title != null) {
            stringResource(R.string.reward_unlock_hint_achievement, title)
        } else {
            stringResource(R.string.reward_unlock_hint_generic)
        }
    }
}
