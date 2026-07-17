package com.mmg.manahub.feature.decks.presentation.wizard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.koin.androidx.compose.koinViewModel

/** Steps 1-4 shown by [WizardStepIndicator]; GENERATING/RESULT replace the whole body instead. */
private val STEP_PHASES = listOf(WizardPhase.FORMAT, WizardPhase.DIRECTION, WizardPhase.IDENTITY, WizardPhase.REVIEW)

/**
 * Deck Builder v2 (`docs/plans/deck-builder-v2-plan.md` §3.4) — the 4-step wizard + generation +
 * result flow. A single full-screen composable with INTERNAL phase state driven by
 * [DeckWizardViewModel] (mirrors the Playtest mulligan/battle-phase-in-one-screen precedent — see
 * CLAUDE.md's Deck Playtest section for why this shape was chosen over per-step nav destinations).
 *
 * @param onBack pops the wizard off the back stack (invoked once [DeckWizardViewModel.onBackPressed]
 *   says there is nothing left to unwind internally).
 * @param onOpenDeckStudio navigates into [com.mmg.manahub.app.navigation.Screen.DeckStudio] for the
 *   freshly built deck (Result screen's primary CTA) — the caller pops the wizard off the stack too
 *   so returning from Deck Studio lands on whatever screen opened the wizard, not back on Result.
 * @param onCardClick opens the full Card Detail screen (Direction commander search / seed search /
 *   Result suggestion taps).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckWizardScreen(
    onBack: () -> Unit,
    onOpenDeckStudio: (deckId: String) -> Unit,
    onCardClick: (String) -> Unit,
    viewModel: DeckWizardViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val toastState = rememberMagicToastState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        FirebaseCrashlytics.getInstance().log("screen_viewed: deck_wizard")
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DeckWizardEvent.ShowToast -> toastState.show(event.message, MagicToastType.INFO)
                is DeckWizardEvent.OpenDeckStudio -> onOpenDeckStudio(event.deckId)
            }
        }
    }

    val handleBack: () -> Unit = { if (viewModel.onBackPressed()) onBack() }
    BackHandler(onBack = handleBack)

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = mc.background,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                DeckWizardTopBar(
                    phase = uiState.phase,
                    onBack = handleBack,
                )
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                AnimatedContent(
                    targetState = uiState.phase,
                    transitionSpec = {
                        val forward = STEP_PHASES.indexOf(targetState) >= STEP_PHASES.indexOf(initialState)
                        if (forward) {
                            (slideInHorizontally(tween(250)) { it / 4 } + fadeIn(tween(250))) togetherWith
                                (slideOutHorizontally(tween(250)) { -it / 4 } + fadeOut(tween(150)))
                        } else {
                            (slideInHorizontally(tween(250)) { -it / 4 } + fadeIn(tween(250))) togetherWith
                                (slideOutHorizontally(tween(250)) { it / 4 } + fadeOut(tween(150)))
                        }
                    },
                    label = "DeckWizardPhase",
                ) { phase ->
                    when (phase) {
                        WizardPhase.FORMAT -> FormatStepContent(
                            uiState = uiState,
                            onSelectFormat = viewModel::onSelectFormat,
                            onNext = viewModel::onNextFromFormat,
                        )
                        WizardPhase.DIRECTION -> DirectionStepContent(
                            uiState = uiState,
                            onSelectStrategy = viewModel::onSelectStrategyDirection,
                            onSelectTribe = viewModel::onSelectTribeDirection,
                            onCommanderQueryChange = viewModel::onCommanderQueryChange,
                            onSelectCommander = viewModel::onSelectCommander,
                            onClearCommander = viewModel::onClearCommander,
                            onToggleSeedPicker = viewModel::onToggleSeedPicker,
                            onSeedQueryChange = viewModel::onSeedQueryChange,
                            onAddSeed = viewModel::onAddSeed,
                            onRemoveSeed = viewModel::onRemoveSeed,
                            onNext = viewModel::onNextFromDirection,
                        )
                        WizardPhase.IDENTITY -> IdentityStepContent(
                            uiState = uiState,
                            onToggleColor = viewModel::onToggleColor,
                            onSelectTheme = viewModel::onSelectThemeHint,
                            onNext = viewModel::onNextFromIdentity,
                        )
                        WizardPhase.REVIEW -> ReviewStepContent(
                            uiState = uiState,
                            onToggleFillLands = viewModel::onToggleFillLands,
                            onGenerate = viewModel::onGenerate,
                        )
                        WizardPhase.GENERATING -> GeneratingContent(
                            uiState = uiState,
                            onCancel = viewModel::onCancelGeneration,
                            onRetry = viewModel::onRetryGeneration,
                        )
                        WizardPhase.RESULT -> ResultContent(
                            uiState = uiState,
                            onAddSuggestion = viewModel::onAddCommunitySuggestion,
                            onCardClick = onCardClick,
                            onOpenDeckStudio = viewModel::onOpenDeckStudio,
                            onBack = handleBack,
                        )
                    }
                }
            }
        }

        MagicToastHost(state = toastState, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun DeckWizardTopBar(phase: WizardPhase, onBack: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(color = mc.backgroundSecondary) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.xs, vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = mc.textSecondary)
                }
                Text(
                    text = stringResource(R.string.deck_wizard_title),
                    style = ty.titleLarge,
                    color = mc.textPrimary,
                    modifier = Modifier.padding(start = spacing.sm),
                )
            }
            val stepIndex = STEP_PHASES.indexOf(phase)
            if (stepIndex >= 0) {
                WizardStepIndicator(
                    stepIndex = stepIndex,
                    stepCount = STEP_PHASES.size,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.sm),
                )
            }
        }
    }
}

/** A lightweight "Step N of M" segmented progress bar (design spec: 4-segment `WizardStepIndicator`,
 * shown only for steps 1-4 — Generating/Result replace the whole body). */
@Composable
private fun WizardStepIndicator(stepIndex: Int, stepCount: Int, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.deck_wizard_step_of, stepIndex + 1, stepCount),
            style = ty.labelSmall,
            color = mc.textSecondary,
        )
        Spacer(Modifier.height(MaterialTheme.spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs), modifier = Modifier.fillMaxWidth()) {
            repeat(stepCount) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(ChipShape)
                        // C2 (design review): mc.surfaceVariant is ~1.1:1 contrast against
                        // surface/background on HallowedPrint (the app's one light theme) --
                        // a theme-agnostic alpha-tinted foreground reads on every palette
                        // (feedback_light_theme_contrast_tokens precedent).
                        .background(if (index <= stepIndex) mc.primaryAccent else mc.textDisabled.copy(alpha = 0.25f)),
                )
            }
        }
    }
}

/** Sticky bottom CTA reused by every step-1-4 content composable (mirrors [com.mmg.manahub.feature
 * .decks.presentation.components.SeedsContent]'s own sticky-button pattern). */
@Composable
internal fun WizardStickyButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(color = mc.background, modifier = modifier.fillMaxWidth().navigationBarsPadding()) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = ChipShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = mc.primaryAccent,
                contentColor = mc.background,
                // C2 (design review): surfaceVariant is near-invisible on HallowedPrint; this is
                // the PRIMARY CTA before a selection is made, so it must stay visibly a button.
                disabledContainerColor = mc.textDisabled.copy(alpha = 0.25f),
                disabledContentColor = mc.textDisabled,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.md).height(52.dp),
        ) {
            Text(text = label, style = ty.titleMedium)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Step 1 — Format
// ═══════════════════════════════════════════════════════════════════════════════

private val V1_FORMATS = listOf(DeckFormat.COMMANDER, DeckFormat.CASUAL)
private val COMING_SOON_FORMATS = listOf(
    DeckFormat.STANDARD, DeckFormat.PIONEER, DeckFormat.MODERN,
    DeckFormat.LEGACY, DeckFormat.VINTAGE, DeckFormat.PAUPER,
)

@Composable
private fun FormatStepContent(
    uiState: DeckWizardUiState,
    onSelectFormat: (DeckFormat) -> Unit,
    onNext: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    // C1 (design review): the sticky CTA is a real Column sibling (not a Box overlay with a
    // guessed bottom-padding reservation) — its real measured height, whatever the device's
    // nav-bar inset turns out to be, is what the grid actually loses, never a magic-number guess.
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = spacing.lg, vertical = spacing.md)) {
            Text(stringResource(R.string.deck_wizard_format_title), style = ty.titleLarge, color = mc.textPrimary)
            Text(
                stringResource(R.string.deck_wizard_format_subtitle),
                style = ty.bodyMedium,
                color = mc.textSecondary,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            items(V1_FORMATS, key = { it.name }) { format ->
                FormatCard(
                    format = format,
                    selected = uiState.selectedFormat == format,
                    comingSoon = false,
                    onClick = { onSelectFormat(format) },
                )
            }
            items(COMING_SOON_FORMATS, key = { it.name }) { format ->
                FormatCard(format = format, selected = false, comingSoon = true, onClick = {})
            }
        }
        WizardStickyButton(
            label = stringResource(R.string.deck_wizard_next),
            enabled = uiState.selectedFormat != null,
            onClick = onNext,
        )
    }
}

@Composable
private fun FormatCard(format: DeckFormat, selected: Boolean, comingSoon: Boolean, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(
        onClick = onClick,
        enabled = !comingSoon,
        shape = CardShape,
        color = if (selected) mc.primaryAccent.copy(alpha = 0.14f) else mc.surface,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, mc.primaryAccent) else null,
        modifier = Modifier.fillMaxWidth().aspectRatio(1.6f),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(spacing.md),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (selected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = mc.primaryAccent, modifier = Modifier.size(18.dp))
                    }
                }
                Column {
                    Text(
                        text = format.displayName,
                        style = ty.titleMedium,
                        color = if (comingSoon) mc.textDisabled else mc.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (comingSoon) {
                        // C2 (design review): surfaceVariant would double up with the already-dim
                        // textDisabled label into a near-invisible badge on HallowedPrint.
                        Surface(shape = ChipShape, color = mc.textDisabled.copy(alpha = 0.25f), modifier = Modifier.padding(top = spacing.xxs)) {
                            Text(
                                text = stringResource(R.string.deck_wizard_coming_soon),
                                style = ty.labelSmall,
                                color = mc.textDisabled,
                                modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.xxs),
                            )
                        }
                    }
                }
            }
            if (comingSoon) {
                Box(Modifier.fillMaxSize().background(mc.background.copy(alpha = 0.35f)))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Step 4 — Review & Generate
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ReviewStepContent(
    uiState: DeckWizardUiState,
    onToggleFillLands: () -> Unit,
    onGenerate: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    // C1 (design review): the sticky CTA is a real Column sibling, weight(1f) + verticalScroll on
    // the content above it -- no guessed bottom-padding reservation (see FormatStepContent).
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Spacer(Modifier.height(spacing.md))
            Text(stringResource(R.string.deck_wizard_review_title), style = ty.titleLarge, color = mc.textPrimary)

            Surface(shape = CardShape, color = mc.backgroundSecondary, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    ReviewRow(stringResource(R.string.deck_wizard_review_format), uiState.selectedFormat?.displayName ?: "—")
                    ReviewRow(
                        stringResource(R.string.deck_wizard_review_direction),
                        uiState.selectedCommander?.name
                            ?: uiState.selectedStrategyHint?.displayName
                            ?: uiState.selectedTribeLabel
                            ?: stringResource(R.string.deck_wizard_review_direction_none),
                    )
                    ReviewRow(
                        stringResource(R.string.deck_wizard_review_colors),
                        if (uiState.colorIdentity.isEmpty()) stringResource(R.string.deck_seeds_identity_colorless)
                        else uiState.colorIdentity.joinToString(" ") { it.symbol },
                    )
                    if (uiState.seedCards.isNotEmpty()) {
                        ReviewRow(stringResource(R.string.deck_wizard_review_seeds), uiState.seedCards.size.toString())
                    }
                }
            }

            Surface(
                onClick = onToggleFillLands,
                shape = CardShape,
                color = mc.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(spacing.md).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.deck_wizard_fill_lands_title), style = ty.bodyMedium, color = mc.textPrimary)
                        Text(stringResource(R.string.deck_wizard_fill_lands_subtitle), style = ty.labelSmall, color = mc.textSecondary)
                    }
                    Switch(
                        checked = uiState.fillLands,
                        onCheckedChange = { onToggleFillLands() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = mc.onAccent,
                            checkedTrackColor = mc.primaryAccent,
                            uncheckedThumbColor = mc.textDisabled,
                            uncheckedTrackColor = mc.surfaceVariant,
                        ),
                    )
                }
            }

            Surface(shape = CardShape, color = mc.goldMtg.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(spacing.md), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = mc.goldMtg, modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.deck_wizard_expectation_copy),
                        style = ty.bodySmall,
                        color = mc.textSecondary,
                        modifier = Modifier.padding(start = spacing.sm),
                    )
                }
            }
        }
        WizardStickyButton(
            label = stringResource(R.string.deck_wizard_generate_cta),
            enabled = true,
            onClick = onGenerate,
        )
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = ty.bodyMedium, color = mc.textSecondary)
        Text(value, style = ty.bodyMedium, color = mc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
