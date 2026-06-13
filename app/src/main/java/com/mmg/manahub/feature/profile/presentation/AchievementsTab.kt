package com.mmg.manahub.feature.profile.presentation

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.gamification.domain.model.AchievementCategory
import com.mmg.manahub.core.gamification.domain.model.AchievementUiModel
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.TimeAgoFormatter

/**
 * The Achievements tab body (ADR-002, Phase 1). Renders the full static catalog supplied by
 * [achievements], grouped by [AchievementCategory] with a section header per category. Each
 * achievement is an [AchievementRow]; secret-and-still-locked achievements render masked ("???").
 *
 * Stateless: the caller (ProfileScreen) hoists the achievement list from the ViewModel. Uses a
 * [LazyColumn] keyed by achievement id (stable, never duplicated) and the shared [EmptyState] for the
 * (rare) empty case. Bottom inset is supplied by [contentPadding] so the list clears the nav bar.
 *
 * @param achievements the full catalog projection (locked + unlocked); already category-complete.
 * @param contentPadding padding applied to the list content (typically the nav-bar bottom inset).
 */
@Composable
fun AchievementsTab(
    achievements: List<AchievementUiModel>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    if (achievements.isEmpty()) {
        EmptyState(
            icon = Icons.Default.EmojiEvents,
            title = stringResource(R.string.achievements_empty_title),
            subtitle = stringResource(R.string.achievements_empty_desc),
            modifier = modifier,
        )
        return
    }

    // Group by category and order sections by the category's declared display order.
    val grouped = achievements
        .groupBy { it.category }
        .toSortedMap(compareBy { it.order })

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        grouped.forEach { (category, items) ->
            item(key = "header_${category.name}") {
                CategoryHeader(
                    titleRes = category.titleRes,
                    modifier = Modifier.padding(
                        start = MaterialTheme.spacing.lg,
                        end = MaterialTheme.spacing.lg,
                        top = MaterialTheme.spacing.md,
                        bottom = MaterialTheme.spacing.xs,
                    ),
                )
            }
            items(items = items, key = { it.id }) { achievement ->
                AchievementRow(
                    achievement = achievement,
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg),
                )
            }
        }
    }
}

/** Section header for one achievement category. */
@Composable
private fun CategoryHeader(@StringRes titleRes: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(titleRes).uppercase(),
        style = MaterialTheme.magicTypography.labelLarge,
        color = MaterialTheme.magicColors.primaryAccent,
        modifier = modifier,
    )
}

/**
 * A single achievement card: emoji, title, description, a determinate tier progress bar, a tier
 * indicator, and the relative unlock time when unlocked. A secret-and-locked achievement
 * ([AchievementUiModel.isMasked]) is rendered with a lock glyph and "???" placeholders and no
 * progress detail, so its existence is not spoiled.
 *
 * Stateless. Tokens only; the whole card carries a single merged content description for screen
 * readers (the inner texts are decorative once announced).
 */
@Composable
fun AchievementRow(
    achievement: AchievementUiModel,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val masked = achievement.isMasked
    val unlocked = achievement.isUnlocked

    val title = if (masked) {
        stringResource(R.string.achievement_masked_title)
    } else {
        stringResource(achievement.titleRes)
    }
    val description = if (masked) {
        stringResource(R.string.achievement_masked_desc)
    } else {
        stringResource(achievement.descRes)
    }
    val emoji = if (masked) "🔒" else achievement.emoji

    val a11y = if (unlocked) {
        stringResource(
            R.string.achievement_row_a11y_unlocked,
            title,
            achievement.tierReached,
            achievement.maxTier,
        )
    } else {
        stringResource(
            R.string.achievement_row_a11y_locked,
            title,
            achievement.currentValue.coerceAtMost(achievement.tierThresholds.lastOrNull() ?: 0),
            achievement.tierThresholds.lastOrNull() ?: 0,
        )
    }

    Surface(
        shape = CardShape,
        color = mc.surface,
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = a11y },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Emoji badge — tinted gold when unlocked, muted when locked.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(ChipShape)
                    .background(
                        if (unlocked) mc.goldMtg.copy(alpha = 0.18f)
                        else mc.surfaceVariant,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = emoji, style = MaterialTheme.magicTypography.titleLarge)
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.magicTypography.titleMedium,
                    color = if (unlocked) mc.textPrimary else mc.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description,
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // Progress detail is hidden for masked secrets (no spoilers).
                if (!masked) {
                    AchievementProgressBar(
                        progress = achievement.progressFraction,
                        unlocked = unlocked,
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
                                R.string.achievement_tier_indicator,
                                achievement.tierReached,
                                achievement.maxTier,
                            ),
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = if (unlocked) mc.textPrimary else mc.textSecondary,
                        )
                        val unlockedAt = achievement.unlockedAt
                        if (unlockedAt != null) {
                            Text(
                                text = stringResource(
                                    R.string.achievement_unlocked_ago,
                                    TimeAgoFormatter.format(unlockedAt),
                                ),
                                style = MaterialTheme.magicTypography.labelSmall,
                                color = mc.textSecondary,
                            )
                        } else {
                            Text(
                                text = stringResource(
                                    R.string.achievement_progress_value,
                                    achievement.currentValue.coerceAtMost(
                                        achievement.tierThresholds.lastOrNull() ?: 0,
                                    ),
                                    achievement.tierThresholds.lastOrNull() ?: 0,
                                ),
                                style = MaterialTheme.magicTypography.labelSmall,
                                color = mc.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A thin determinate progress bar for an achievement tier.
 *
 * [com.mmg.manahub.core.ui.components.MagicProgressBar] is an *indeterminate* shimmer (loading), so
 * it cannot show a real fraction — this is a small determinate companion. Track is a muted fill;
 * the value uses gold when the achievement is unlocked (maxed/celebratory) and the primary accent
 * while still in progress. Animated so changes glide.
 *
 * @param progress fraction in [0f, 1f].
 * @param unlocked whether the achievement is unlocked (drives the accent color).
 */
@Composable
private fun AchievementProgressBar(
    progress: Float,
    unlocked: Boolean,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 400),
        label = "achievementProgress",
    )
    val fillColor = if (unlocked) mc.goldMtg else mc.primaryAccent

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
                // Width = animated fraction of the track, computed in the layout pass so it
                // tracks the parent's measured width exactly (no fillMaxWidth(fraction) rounding).
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

/** Maps an [AchievementCategory] to its section-header string resource. */
@get:StringRes
private val AchievementCategory.titleRes: Int
    get() = when (this) {
        AchievementCategory.COLLECTION -> R.string.achievement_category_collection
        AchievementCategory.GAMES -> R.string.achievement_category_games
        AchievementCategory.DECKS -> R.string.achievement_category_decks
        AchievementCategory.SURVEYS -> R.string.achievement_category_surveys
        AchievementCategory.TOURNAMENTS -> R.string.achievement_category_tournaments
        AchievementCategory.SOCIAL -> R.string.achievement_category_social
        AchievementCategory.DEDICATION -> R.string.achievement_category_dedication
    }
