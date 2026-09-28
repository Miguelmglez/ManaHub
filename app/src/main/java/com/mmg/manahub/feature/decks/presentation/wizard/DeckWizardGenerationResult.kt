package com.mmg.manahub.feature.decks.presentation.wizard
// COMMENTS_REVIEWED: 2026-09-17

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.MagicCard
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.SmallCardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.template.WizardBuildStage
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════════════════════════
//  Generating
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun GeneratingContent(
    uiState: DeckWizardUiState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    if (uiState.buildError != null) {
        BuildErrorContent(
            error = uiState.buildError,
            isRetryable = uiState.isBuildErrorRetryable,
            onRetry = onRetry,
            onCancel = onCancel,
        )
        return
    }

    val allStages = remember {
        listOf(
            WizardBuildStage.RESOLVING_PLAN,
            WizardBuildStage.PLACING_MANUAL_ADDS,
            WizardBuildStage.PLACING_CARDS,
            WizardBuildStage.FILLING_LANDS,
            WizardBuildStage.VERIFYING_AND_REFINING,
            WizardBuildStage.WRITING_DECK,
            WizardBuildStage.DONE,
        )
    }

    var visualStageIndex by remember { mutableStateOf(0) }
    val currentVisualStage = allStages.getOrElse(visualStageIndex) { WizardBuildStage.DONE }
    val visualCompletedStages = remember { mutableStateListOf<WizardBuildStage>() }

    LaunchedEffect(Unit) {
        for (i in 1 until allStages.size) {
            delay(450)
            val prevStage = allStages[i - 1]
            if (prevStage !in visualCompletedStages) {
                visualCompletedStages.add(prevStage)
            }
            visualStageIndex = i
        }
    }

    val currentStageLabel = currentVisualStage.label()
    val targetProgress = (visualStageIndex.toFloat() + 1f) / allStages.size.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress.coerceIn(0.1f, 1f),
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "DeckGenerationProgress",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "GenerationCorePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "CoreScalePulse",
    )
    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "CoreAuraAlpha",
    )
    val sparkleRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "SparkleRotation",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(spacing.lg),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GenerationContextCard(
            uiState = uiState,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(spacing.md))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(200.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        mc.primaryAccent.copy(alpha = auraAlpha),
                                        mc.primaryAccent.copy(alpha = 0.05f),
                                        Color.Transparent,
                                    )
                                )
                            )
                    )

                    val trackColor = mc.surfaceVariant.copy(alpha = 0.5f)
                    val progressColor = mc.primaryAccent
                    Canvas(modifier = Modifier.size(160.dp)) {
                        val strokeWidth = 8.dp.toPx()
                        drawCircle(
                            color = trackColor,
                            style = Stroke(width = strokeWidth),
                        )
                        drawArc(
                            color = progressColor,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedProgress,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = mc.primaryAccent,
                            modifier = Modifier
                                .size(32.dp)
                                .rotate(sparkleRotation),
                        )
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            text = "${(animatedProgress * 100).toInt()}%",
                            style = ty.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = mc.textPrimary,
                        )
                        Text(
                            text = "AI ENGINE",
                            style = ty.labelSmall,
                            color = mc.textSecondary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(Modifier.height(spacing.lg))

                Surface(
                    shape = CardShape,
                    color = mc.primaryAccent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.sm),
                ) {
                    Row(
                        modifier = Modifier.padding(spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        MagicLoadingSpinner(size = MagicLoadingSize.Small)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CURRENT STEP",
                                style = ty.labelSmall,
                                color = mc.primaryAccent,
                                fontWeight = FontWeight.Bold,
                            )
                            AnimatedContent(
                                targetState = currentStageLabel,
                                transitionSpec = {
                                    (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                                },
                                label = "CurrentStageText",
                            ) { text ->
                                Text(
                                    text = text,
                                    style = ty.titleMedium,
                                    color = mc.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(spacing.md))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    horizontalAlignment = Alignment.Start,
                ) {
                    visualCompletedStages.forEach { stage ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = mc.lifePositive.copy(alpha = 0.06f),
                                        shape = ChipShape,
                                    )
                                    .padding(horizontal = spacing.md, vertical = spacing.xs),
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = mc.lifePositive,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = stage.label(),
                                    style = ty.bodyMedium,
                                    color = mc.textPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "Done",
                                    style = ty.labelSmall,
                                    color = mc.lifePositive,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(spacing.md))

        OutlinedButton(
            onClick = onCancel,
            shape = ChipShape,
            border = BorderStroke(1.dp, mc.textSecondary.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(
                text = stringResource(R.string.action_cancel),
                color = mc.textSecondary,
                style = ty.labelLarge,
            )
        }
    }
}

@Composable
private fun GenerationContextCard(
    uiState: DeckWizardUiState,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    val commander = uiState.selectedCommander
    val format = uiState.selectedFormat

    Surface(
        modifier = modifier,
        shape = CardShape,
        color = mc.surface,
        border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier.padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            if (commander != null) {
                Box(
                    modifier = Modifier.size(width = 44.dp, height = 62.dp),
                ) {
                    MagicCard(
                        card = commander,
                        shape = SmallCardShape,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BUILDING COMMANDER DECK",
                        style = ty.labelSmall,
                        color = mc.primaryAccent,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = commander.name,
                        style = ty.titleMedium,
                        color = mc.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val cost = commander.manaCost
                    if (!cost.isNullOrEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        ManaCostImages(
                            manaCost = cost,
                            symbolSize = 14.dp,
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(mc.primaryAccent.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = mc.primaryAccent,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BUILDING DECK",
                        style = ty.labelSmall,
                        color = mc.primaryAccent,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = format?.displayName ?: "Deck",
                        style = ty.titleMedium,
                        color = mc.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = if (uiState.seeds.isNotEmpty()) {
                        "${uiState.seeds.size} seed card(s) selected"
                    } else {
                        "${format?.targetDeckSize ?: 60} Cards Format"
                    }
                    Text(
                        text = subtitle,
                        style = ty.bodySmall,
                        color = mc.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun BuildErrorContent(
    error: String,
    isRetryable: Boolean,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CardShape,
            color = mc.surface,
            border = BorderStroke(1.dp, mc.lifeNegative.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(mc.lifeNegative.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = mc.lifeNegative,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.deck_wizard_build_error_title),
                    style = ty.titleLarge,
                    color = mc.textPrimary,
                )
                Text(
                    text = error,
                    style = ty.bodyMedium,
                    color = mc.textSecondary,
                )
                Spacer(Modifier.height(spacing.xs))
                if (isRetryable) {
                    MagicCtaButton(
                        text = stringResource(R.string.deck_wizard_retry),
                        onClick = onRetry,
                        style = MagicCtaStyle.Filled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedButton(
                    onClick = onCancel,
                    shape = ChipShape,
                    border = BorderStroke(1.dp, mc.textSecondary),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(stringResource(R.string.action_back), color = mc.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun WizardBuildStage.label(): String = stringResource(
    when (this) {
        WizardBuildStage.RESOLVING_PLAN -> R.string.deck_wizard_commander_stage_resolving_plan
        WizardBuildStage.PLACING_MANUAL_ADDS -> R.string.deck_wizard_commander_stage_placing_manual_adds
        WizardBuildStage.PLACING_CARDS -> R.string.deck_wizard_commander_stage_placing_cards
        WizardBuildStage.FILLING_LANDS -> R.string.deck_wizard_stage_filling_lands
        WizardBuildStage.VERIFYING_AND_REFINING -> R.string.deck_wizard_commander_stage_verifying
        WizardBuildStage.WRITING_DECK -> R.string.deck_wizard_commander_stage_writing_deck
        WizardBuildStage.DONE -> R.string.deck_wizard_stage_done
    }
)
