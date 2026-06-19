package com.mmg.manahub.feature.profile.presentation

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.gamification.domain.model.QuestBoard
import com.mmg.manahub.core.gamification.domain.model.QuestUiModel
import com.mmg.manahub.core.gamification.domain.model.StreakUiModel
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/**
 * The Quests tab body (ADR-002, Phase 2). Renders a streak header followed by the active daily and
 * weekly quests. Each quest exposes a determinate progress bar, a progress label, and a Claim CTA
 * when completed (or a "Claimed" state once taken).
 *
 * Stateless: the caller (ProfileScreen) hoists [board] + [streak] from the ViewModel and provides the
 * [onClaim] handler. Uses a single [LazyColumn] keyed by quest [QuestUiModel.instanceId] (stable, never
 * duplicated) and the shared [EmptyState] for the empty case.
 *
 * @param board the active quest board (daily + weekly).
 * @param streak the daily-activity streak (count + freeze tokens).
 * @param onClaim invoked with the quest instance id when the user taps Claim.
 * @param contentPadding bottom inset so the list clears the nav bar.
 */
@Composable
fun QuestsTab(
    board: QuestBoard,
    streak: StreakUiModel,
    onClaim: (instanceId: String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val noQuests = board.daily.isEmpty() && board.weekly.isEmpty()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        item(key = "streak_header") {
            StreakHeader(
                streak = streak,
                modifier = Modifier.padding(
                    start = MaterialTheme.spacing.lg,
                    end = MaterialTheme.spacing.lg,
                    top = MaterialTheme.spacing.sm,
                    bottom = MaterialTheme.spacing.xs,
                ),
            )
        }

        if (noQuests) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Default.Flag,
                    title = stringResource(R.string.quests_empty_title),
                    subtitle = stringResource(R.string.quests_empty_desc),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MaterialTheme.spacing.xxl),
                )
            }
            return@LazyColumn
        }

        if (board.daily.isNotEmpty()) {
            item(key = "header_daily") {
                QuestSectionHeader(
                    titleRes = R.string.quests_section_daily,
                    modifier = Modifier.padding(
                        start = MaterialTheme.spacing.lg,
                        end = MaterialTheme.spacing.lg,
                        top = MaterialTheme.spacing.md,
                        bottom = MaterialTheme.spacing.xs,
                    ),
                )
            }
            items(items = board.daily, key = { it.instanceId }) { quest ->
                QuestRow(
                    quest = quest,
                    onClaim = onClaim,
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg),
                )
            }
        }

        if (board.weekly.isNotEmpty()) {
            item(key = "header_weekly") {
                QuestSectionHeader(
                    titleRes = R.string.quests_section_weekly,
                    modifier = Modifier.padding(
                        start = MaterialTheme.spacing.lg,
                        end = MaterialTheme.spacing.lg,
                        top = MaterialTheme.spacing.md,
                        bottom = MaterialTheme.spacing.xs,
                    ),
                )
            }
            items(items = board.weekly, key = { it.instanceId }) { quest ->
                QuestRow(
                    quest = quest,
                    onClaim = onClaim,
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg),
                )
            }
        }
    }
}

/**
 * Streak header. Frames freeze tokens POSITIVELY (they protect the streak) — never punitive. The
 * active-flame accent uses [com.mmg.manahub.core.ui.theme.MagicColors.lifePositive]; small labels stay
 * on text tokens so they pass AA on HallowedPrint (goldMtg fails small-text contrast there).
 */
@Composable
private fun StreakHeader(
    streak: StreakUiModel,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val active = streak.current > 0
    val flameColor = if (active) mc.lifePositive else mc.textDisabled

    Surface(
        shape = CardShape,
        color = mc.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(ChipShape)
                    .background(flameColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = flameColor,
                    modifier = Modifier.size(24.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs),
            ) {
                Text(
                    text = if (active) {
                        stringResource(R.string.quests_streak_days, streak.current)
                    } else {
                        stringResource(R.string.quests_streak_none)
                    },
                    style = MaterialTheme.magicTypography.titleMedium,
                    color = mc.textPrimary,
                )
                Text(
                    text = stringResource(R.string.quests_streak_freeze_hint),
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Freeze-token chip — positively framed ("N freezes").
            if (streak.freezeTokens > 0) {
                Surface(shape = ChipShape, color = mc.secondaryAccent.copy(alpha = 0.16f)) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.spacing.sm,
                            vertical = MaterialTheme.spacing.xs,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AcUnit,
                            contentDescription = null,
                            tint = mc.secondaryAccent,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = stringResource(R.string.quests_streak_freezes, streak.freezeTokens),
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

/** Section header (Daily / Weekly). */
@Composable
private fun QuestSectionHeader(@StringRes titleRes: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(titleRes).uppercase(),
        style = MaterialTheme.magicTypography.labelLarge,
        color = MaterialTheme.magicColors.primaryAccent,
        modifier = modifier,
    )
}

/**
 * A single quest card: emoji badge, title, description, a determinate progress bar, the "x/target"
 * label, the XP reward, and a Claim button (when claimable) or a "Claimed" state.
 *
 * Stateless. Tokens only. The whole card carries a merged content description; the Claim button keeps
 * its own click semantics (≥48dp) so the action stays reachable by assistive tech.
 */
@Composable
fun QuestRow(
    quest: QuestUiModel,
    onClaim: (instanceId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val title = stringResource(quest.titleRes)
    val a11y = stringResource(
        R.string.quests_row_a11y,
        title,
        quest.progress.coerceAtMost(quest.target),
        quest.target,
        quest.xpReward,
    )

    Surface(
        shape = CardShape,
        color = mc.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Emoji badge — gold-tinted once claimed, accent while active/claimable.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(ChipShape)
                    .background(
                        if (quest.isClaimed) mc.goldMtg.copy(alpha = 0.18f)
                        else mc.primaryAccent.copy(alpha = 0.14f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = quest.emoji, style = MaterialTheme.magicTypography.titleLarge)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = a11y },
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.magicTypography.titleMedium,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(quest.descRes),
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                QuestProgressBar(
                    progress = quest.progressFraction,
                    claimed = quest.isClaimed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MaterialTheme.spacing.xs),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MaterialTheme.spacing.xxs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            R.string.quests_progress_value,
                            quest.progress.coerceAtMost(quest.target),
                            quest.target,
                        ),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary,
                    )
                    Text(
                        text = stringResource(R.string.quests_reward_xp, quest.xpReward),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textPrimary,
                    )
                }
            }

            // Trailing CTA / state.
            when {
                quest.isClaimable -> ClaimButton(title = title, onClick = { onClaim(quest.instanceId) })
                quest.isClaimed -> ClaimedBadge()
                else -> Unit // in-progress: no trailing control
            }
        }
    }
}

/** Filled "Claim" button (≥48dp). */
@Composable
private fun ClaimButton(title: String, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    Surface(
        shape = ChipShape,
        color = mc.primaryAccent,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(ChipShape)
            .clickable(
                onClickLabel = stringResource(R.string.quests_claim_a11y, title),
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.md,
                vertical = MaterialTheme.spacing.sm,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.quests_claim).uppercase(),
                style = MaterialTheme.magicTypography.labelMedium,
                color = mc.background,
                maxLines = 1,
            )
        }
    }
}

/** Static "Claimed" state with a check glyph (gold accent). */
@Composable
private fun ClaimedBadge() {
    val mc = MaterialTheme.magicColors
    Row(
        modifier = Modifier.heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = mc.goldMtg,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.quests_claimed),
            style = MaterialTheme.magicTypography.labelSmall,
            color = mc.textSecondary,
            maxLines = 1,
        )
    }
}

/**
 * A thin determinate progress bar for a quest.
 *
 * [com.mmg.manahub.core.ui.components.MagicProgressBar] is an *indeterminate* shimmer, so it cannot
 * show a real fraction — this is a small determinate companion (mirrors the Achievements tier bar).
 * Track uses a theme-agnostic low-alpha foreground (NOT surfaceVariant, which is invisible on
 * HallowedPrint); fill uses gold when claimed, the primary accent while in progress.
 */
@Composable
private fun QuestProgressBar(
    progress: Float,
    claimed: Boolean,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 400),
        label = "questProgress",
    )
    val fillColor = if (claimed) mc.goldMtg else mc.primaryAccent

    Box(
        modifier = modifier
            .height(6.dp) // intentional: thin bar
            .clip(CircleShape)
            .background(mc.textDisabled.copy(alpha = 0.25f)),
    ) {
        Box(
            modifier = Modifier
                .height(6.dp) // intentional: thin bar
                .clip(CircleShape)
                .background(fillColor)
                .layout { measurable, constraints ->
                    val width = (constraints.maxWidth * animated).toInt().coerceAtLeast(0)
                    val placeable = measurable.measure(
                        constraints.copy(minWidth = width, maxWidth = width),
                    )
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                },
        )
    }
}
