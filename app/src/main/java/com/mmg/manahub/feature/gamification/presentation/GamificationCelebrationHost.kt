package com.mmg.manahub.feature.gamification.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.gamification.domain.catalog.AchievementCatalog
import com.mmg.manahub.core.gamification.domain.model.AchievementUiModel
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Global host for the achievement-unlock celebration overlay (ADR-002, Phase 1, Chunk B).
 *
 * Mounted once at the app root (above [com.mmg.manahub.app.navigation.AppNavGraph]) so a celebration
 * plays regardless of which screen is foreground. Backed by [GamificationCelebrationViewModel], which
 * exposes the oldest pending unlock (or null when the queue is empty or gamification is disabled).
 *
 * The queue is sequential: while [GamificationCelebrationViewModel.current] is non-null the overlay
 * shows that single achievement; on dismiss it calls [GamificationCelebrationViewModel.onCelebrationShown]
 * to stamp `celebrated_at`, and the next pending item surfaces as the flow re-emits.
 *
 * This composable is fully self-contained (it provides its own ViewModel) so the root only needs to
 * place `GamificationCelebrationHost()` in the root `Box`.
 */
@Composable
fun GamificationCelebrationHost(
    modifier: Modifier = Modifier,
    viewModel: GamificationCelebrationViewModel = hiltViewModel(),
) {
    val current by viewModel.current.collectAsStateWithLifecycle()

    // A stable key per distinct celebration so the snapshot below survives the exit transition.
    val key = when (val c = current) {
        is GamificationCelebrationViewModel.CelebrationItem.Achievement -> "ach:${c.model.id}"
        is GamificationCelebrationViewModel.CelebrationItem.LevelUp -> "lvl:${c.level}"
        null -> null
    }

    // Render above all content. AnimatedVisibility scrim fade frames the overlay's enter/exit.
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = current != null,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(220)),
        ) {
            // Snapshot the item so it survives the exit transition after current becomes null.
            val shown = remember(key) { current }
            when (shown) {
                is GamificationCelebrationViewModel.CelebrationItem.Achievement ->
                    CelebrationOverlay(
                        achievement = shown.model,
                        onDismiss = { viewModel.onCelebrationShown(shown.model.id) },
                    )

                is GamificationCelebrationViewModel.CelebrationItem.LevelUp ->
                    LevelUpOverlay(
                        level = shown.level,
                        onDismiss = { viewModel.onLevelUpShown(shown.level) },
                    )

                null -> Unit
            }
        }
    }
}

/**
 * Full-screen LEVEL-UP celebration (ADR-002, Phase 3): the same scrim + procedural particle burst as
 * the achievement overlay, but a RING-BURST variant — an expanding ring stroke with the new level
 * number, drawn in gold/lifePositive accents. Reuses the particle generator; asset-free. Skippable by
 * tap or system Back.
 *
 * @param level the newly-reached level.
 * @param onDismiss invoked on tap/back (the VM advances the celebrated baseline).
 */
@Composable
private fun LevelUpOverlay(
    level: Int,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors

    val a11y = stringResource(R.string.reward_level_up_a11y, level)
    val dismissLabel = stringResource(R.string.reward_level_up_dismiss_action)

    BackHandler { onDismiss() }

    // Card scales in; the ring + particle burst progress 0→1 once.
    val cardScale = remember { androidx.compose.animation.core.Animatable(0.6f) }
    val burst = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(level) {
        cardScale.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(level) {
        burst.animateTo(1f, tween(durationMillis = 1000, easing = LinearEasing))
    }

    val particles = remember(level) { generateParticles("level_up_$level") }
    val accent = mc.lifePositive
    val gold = mc.goldMtg

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(Float.MAX_VALUE)
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .semantics {
                contentDescription = a11y
                role = Role.Button
                onClick(label = dismissLabel) { onDismiss(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Ring burst + particle burst behind the level number.
        Box(
            modifier = Modifier
                .size(280.dp) // intentional: fixed burst diameter
                .drawBehind {
                    val progress = burst.value
                    val maxRadius = size.minDimension * 0.5f

                    // Expanding gold ring stroke (the "ring-burst" hero element).
                    val ringRadius = maxRadius * (0.35f + progress * 0.6f)
                    val ringAlpha = (1f - progress).coerceIn(0f, 1f)
                    drawCircle(
                        color = gold.copy(alpha = ringAlpha),
                        radius = ringRadius,
                        center = center,
                        style = Stroke(width = 6.dp.toPx() * (1f - progress * 0.4f)),
                    )

                    // Reused particle ring.
                    particles.forEach { p ->
                        val dist = maxRadius * progress * p.distanceFactor
                        val cx = center.x + cos(p.angle) * dist
                        val cy = center.y + sin(p.angle) * dist
                        val alpha = (1f - progress).coerceIn(0f, 1f)
                        val radius = p.radiusPx * (1f - progress * 0.4f)
                        drawCircle(
                            color = (if (p.gold) gold else accent).copy(alpha = alpha),
                            radius = radius,
                            center = Offset(cx, cy),
                        )
                    }
                },
        )

        // Level-up card.
        Surface(
            shape = CardShape,
            color = mc.surface,
            modifier = Modifier
                .padding(MaterialTheme.spacing.xl)
                .scale(cardScale.value)
                .graphicsLayer { alpha = cardScale.value.coerceIn(0f, 1f) },
        ) {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            ) {
                Text(text = "✦", style = MaterialTheme.magicTypography.displayLarge, color = gold)
                Text(
                    text = stringResource(R.string.reward_level_up_title, level),
                    style = MaterialTheme.magicTypography.titleLarge,
                    color = mc.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.reward_level_up_subtitle),
                    style = MaterialTheme.magicTypography.bodyMedium,
                    color = accent,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.celebration_tap_to_dismiss),
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textSecondary,
                    modifier = Modifier.padding(top = MaterialTheme.spacing.sm),
                )
            }
        }
    }
}

/**
 * Full-screen unlock celebration: a scrim, a procedural particle burst (Canvas, no assets), and the
 * achievement card scaling + fading in. Skippable by tapping anywhere, which invokes [onDismiss].
 *
 * Stateless aside from its own entrance animation. Tokens only; the positive accent is `lifePositive`
 * (there is no success token). Carries a single merged content description for screen readers.
 */
@Composable
private fun CelebrationOverlay(
    achievement: AchievementUiModel,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors

    // Resolve the just-unlocked tier's XP reward from the catalog (display only — XP already granted).
    val tier = achievement.tierReached.coerceAtLeast(1)
    val xpReward = remember(achievement.id, tier) {
        AchievementCatalog.byId(achievement.id)
            ?.tiers?.getOrNull(tier - 1)?.xpReward
            ?: 0
    }

    val title = achievement.title
    val a11y = stringResource(R.string.celebration_a11y, title, tier, xpReward)
    val dismissLabel = stringResource(R.string.celebration_dismiss_action)

    // System back dismisses the overlay (same as tapping the scrim).
    BackHandler { onDismiss() }

    // Entrance: scrim already faded by AnimatedVisibility; here the card scales in and the particle
    // burst progresses 0→1 once. Two independent LaunchedEffects so the animations run in parallel.
    val cardScale = remember { Animatable(0.6f) }
    val burst = remember { Animatable(0f) }
    LaunchedEffect(achievement.id) {
        cardScale.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(achievement.id) {
        burst.animateTo(1f, tween(durationMillis = 900, easing = LinearEasing))
    }

    // Stable particle seeds for this achievement so recomposition doesn't reshuffle them.
    val particles = remember(achievement.id) { generateParticles(achievement.id) }
    val accent = mc.lifePositive
    val gold = mc.goldMtg

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(Float.MAX_VALUE)
            .background(Color.Black.copy(alpha = 0.72f))
            // Tap anywhere to skip/dismiss. No ripple — the whole scrim is the target.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .semantics {
                contentDescription = a11y
                role = Role.Button
                onClick(label = dismissLabel) { onDismiss(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Procedural particle burst behind the card.
        Box(
            modifier = Modifier
                .size(280.dp) // intentional: fixed burst diameter
                .drawBehind {
                    val progress = burst.value
                    val maxRadius = size.minDimension * 0.5f
                    particles.forEach { p ->
                        val dist = maxRadius * progress * p.distanceFactor
                        val cx = center.x + cos(p.angle) * dist
                        val cy = center.y + sin(p.angle) * dist
                        // Fade out as the burst expands; shrink slightly.
                        val alpha = (1f - progress).coerceIn(0f, 1f)
                        val radius = p.radiusPx * (1f - progress * 0.4f)
                        drawCircle(
                            color = (if (p.gold) gold else accent).copy(alpha = alpha),
                            radius = radius,
                            center = Offset(cx, cy),
                        )
                    }
                },
        )

        // Achievement card.
        Surface(
            shape = CardShape,
            color = mc.surface,
            modifier = Modifier
                .padding(MaterialTheme.spacing.xl)
                .scale(cardScale.value)
                .graphicsLayer { alpha = cardScale.value.coerceIn(0f, 1f) },
        ) {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.celebration_unlocked_label).uppercase(),
                    style = MaterialTheme.magicTypography.labelLarge,
                    color = accent,
                    textAlign = TextAlign.Center,
                )
                Text(text = achievement.emoji, style = MaterialTheme.magicTypography.displayLarge)
                Text(
                    text = title,
                    style = MaterialTheme.magicTypography.titleLarge,
                    color = mc.textPrimary,
                    textAlign = TextAlign.Center,
                )
                if (achievement.maxTier > 1) {
                    Text(
                        text = stringResource(
                            R.string.achievement_tier_indicator,
                            tier,
                            achievement.maxTier,
                        ),
                        style = MaterialTheme.magicTypography.labelMedium,
                        // Small gold text is borderline-AA on the light theme (HallowedPrint, ~4.0:1);
                        // use textSecondary for this small tier indicator. Large gold accents are unchanged.
                        color = mc.textSecondary,
                    )
                }
                if (xpReward > 0) {
                    Text(
                        text = stringResource(R.string.celebration_xp_reward, xpReward),
                        style = MaterialTheme.magicTypography.titleMedium,
                        color = accent,
                    )
                }
                Text(
                    text = stringResource(R.string.celebration_tap_to_dismiss),
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textSecondary,
                    modifier = Modifier.padding(top = MaterialTheme.spacing.sm),
                )
            }
        }
    }
}

/** One procedural particle in the burst. */
private data class Particle(
    val angle: Float,
    val distanceFactor: Float,
    val radiusPx: Float,
    val gold: Boolean,
)

/**
 * Deterministically generates a particle ring for [seedId] so the same unlock always bursts the
 * same way (no reshuffle on recomposition). 24 particles around the circle with jittered radius and
 * distance; ~1/3 are gold.
 */
private fun generateParticles(seedId: String): List<Particle> {
    val rng = Random(seedId.hashCode())
    val count = 24
    return List(count) { i ->
        val baseAngle = (i.toFloat() / count) * (2f * Math.PI.toFloat())
        val jitter = (rng.nextFloat() - 0.5f) * 0.25f
        Particle(
            angle = baseAngle + jitter,
            distanceFactor = 0.7f + rng.nextFloat() * 0.3f,
            radiusPx = 6f + rng.nextFloat() * 8f,
            gold = rng.nextFloat() < 0.34f,
        )
    }
}
