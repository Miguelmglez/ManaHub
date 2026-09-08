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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.CardRow
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.SmallCardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import org.jetbrains.compose.resources.painterResource
import org.koin.androidx.compose.koinViewModel

/**
 * Steps shown by [WizardStepIndicator]; GENERATING/RESULT replace the whole body instead.
 * Deck Engine Unification plan (§5 Phase 3.1) / Deck Wizard & Engine Rework plan (Workstream 2/3) —
 * this is COMPUTED, not a fixed list: Commander runs `FORMAT → COMMANDER_PICK → STRATEGY →
 * MANUAL_ADDS → REVIEW` (see [WizardPhase]'s KDoc); every Casual flow (A/B/C) now runs the SAME
 * shape as of Workstream 3 -- `FORMAT → ENTRY → DIRECTION → MANUAL_ADDS → REVIEW`
 * ([WizardPhase.IDENTITY] is unreachable dead code for every flow now, see
 * [DeckWizardViewModel.onNextFromDirection]'s KDoc) -- so this no longer branches on [uiState]'s
 * entry flow at all; kept as a function (not a `val`) for parity with the Commander branch above it.
 */
private fun stepPhasesFor(uiState: DeckWizardUiState): List<WizardPhase> {
    if (uiState.selectedFormat == DeckFormat.COMMANDER) {
        return listOf(WizardPhase.FORMAT, WizardPhase.COMMANDER_PICK, WizardPhase.STRATEGY, WizardPhase.MANUAL_ADDS, WizardPhase.REVIEW)
    }
    return listOf(WizardPhase.FORMAT, WizardPhase.ENTRY, WizardPhase.DIRECTION, WizardPhase.MANUAL_ADDS, WizardPhase.REVIEW)
}

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
                    uiState = uiState,
                    onBack = handleBack,
                )
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                AnimatedContent(
                    targetState = uiState.phase,
                    transitionSpec = {
                        val stepPhases = stepPhasesFor(uiState)
                        val forward = stepPhases.indexOf(targetState) >= stepPhases.indexOf(initialState)
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
                        WizardPhase.ENTRY -> EntryStepContent(
                            onSelect = viewModel::onSelectEntryFlow,
                        )
                        WizardPhase.COMMANDER_PICK -> CommanderPickStepContent(
                            uiState = uiState,
                            onToggleColorFilter = viewModel::onToggleCommanderColorFilter,
                            onToggleIncludeOutsideCollection = viewModel::onToggleIncludeOutsideCollection,
                            onQueryChange = viewModel::onCommanderQueryChange,
                            onSelectCommander = viewModel::onSelectCommander,
                            onClearCommander = viewModel::onClearCommander,
                            onNext = viewModel::onNextFromCommanderPick,
                        )
                        WizardPhase.STRATEGY -> StrategyStepContent(
                            uiState = uiState,
                            onSelectArchetype = viewModel::onSelectStrategyArchetype,
                            onToggleTheme = viewModel::onToggleStrategyTheme,
                            onSelectTribe = viewModel::onSelectStrategyTribe,
                            onNext = viewModel::onNextFromStrategy,
                        )
                        WizardPhase.MANUAL_ADDS -> ManualAddsStepContent(
                            uiState = uiState,
                            onQueryChange = viewModel::onManualAddsQueryChange,
                            onToggleIncludeOutsideCollection = viewModel::onToggleIncludeOutsideCollection,
                            onSelectRoleFilter = viewModel::onSelectManualAddsRoleFilter,
                            onAddSeed = viewModel::onAddSeed,
                            onRemoveSeed = viewModel::onRemoveSeed,
                            onNext = viewModel::onNextFromManualAdds,
                        )
                        WizardPhase.DIRECTION -> DirectionStepContent(
                            uiState = uiState,
                            onSelectDirectionTag = viewModel::onSelectDirectionTag,
                            onSelectTribe = viewModel::onSelectTribeDirection,
                            onCommanderQueryChange = viewModel::onCommanderQueryChange,
                            onSelectCommander = viewModel::onSelectCommander,
                            onClearCommander = viewModel::onClearCommander,
                            onToggleSeedPicker = viewModel::onToggleSeedPicker,
                            onToggleIncludeOutsideCollection = viewModel::onToggleIncludeOutsideCollection,
                            onSeedQueryChange = viewModel::onSeedQueryChange,
                            onAddSeed = viewModel::onAddSeed,
                            onRemoveSeed = viewModel::onRemoveSeed,
                            onSelectSeedStrategyCandidate = viewModel::onSelectSeedStrategyCandidate,
                            onToggleCardsFlowColor = viewModel::onToggleCardsFlowColor,
                            onToggleColorFlowColor = viewModel::onToggleColorFlowColor,
                            onSelectColorAffinityEntry = viewModel::onSelectColorAffinityEntry,
                            onTaxonomyQueryChange = viewModel::onTaxonomyQueryChange,
                            onSelectTaxonomyArchetype = viewModel::onSelectTaxonomyArchetype,
                            onSelectTaxonomyTheme = viewModel::onSelectTaxonomyTheme,
                            onSelectColorCombo = viewModel::onSelectColorCombo,
                            onToggleSuggestedSeed = viewModel::onToggleSuggestedSeed,
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
                            onToggleUseCommunityData = viewModel::onToggleUseCommunityData,
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
private fun DeckWizardTopBar(uiState: DeckWizardUiState, onBack: () -> Unit) {
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
            val stepPhases = remember(uiState.selectedFormat, uiState.entryFlow) { stepPhasesFor(uiState) }
            val stepIndex = stepPhases.indexOf(uiState.phase)
            if (stepIndex >= 0) {
                WizardStepIndicator(
                    stepIndex = stepIndex,
                    stepCount = stepPhases.size,
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

/** Sticky bottom CTA reused by every step-1-4 content composable (mirrors the retired legacy
 * `SeedsContent`'s own sticky-button pattern). */
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
    // nav-bar inset turns out to be, is what the list actually loses, never a magic-number guess.
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
        // Visual-overhaul pass: one-format-per-row list matching the "Pick a strategy" step's
        // StrategyOptionRow shape (DeckWizardEntryFlows.kt) instead of the old icon-badge grid
        // tile -- see FormatCard's KDoc for why the row body is a local duplicate rather than an
        // import of that file-private composable.
        LazyColumn(
            contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
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

/**
 * One or two short, factual rules sentences per format so a [FormatCard] explains what the format
 * actually IS instead of just naming it -- real MTG rules text (English), never invented. Returns
 * `null` for any [DeckFormat] not covered here (there is none today, but this stays defensive).
 */
private fun formatRulesDescriptionRes(format: DeckFormat): Int? = when (format) {
    DeckFormat.COMMANDER -> R.string.deck_wizard_format_desc_commander
    DeckFormat.CASUAL -> R.string.deck_wizard_format_desc_casual
    DeckFormat.STANDARD -> R.string.deck_wizard_format_desc_standard
    DeckFormat.PIONEER -> R.string.deck_wizard_format_desc_pioneer
    DeckFormat.MODERN -> R.string.deck_wizard_format_desc_modern
    DeckFormat.LEGACY -> R.string.deck_wizard_format_desc_legacy
    DeckFormat.VINTAGE -> R.string.deck_wizard_format_desc_vintage
    DeckFormat.PAUPER -> R.string.deck_wizard_format_desc_pauper
    else -> null
}

/**
 * A [DeckFormat] pick, one full-width row per format -- deliberately built to the EXACT visual
 * shape of `StrategyOptionRow` (DeckWizardEntryFlows.kt, the "Pick a strategy" step's row: label +
 * 2-line description in a weighted Column, trailing 24dp circular selected-checkmark, same
 * selected fill/border/typography). `StrategyOptionRow` itself is `private` in a different file
 * with no other cross-file consumer -- this codebase's existing convention for a wizard-only row
 * shape shared only in spirit (not code) across files is a short per-file duplicate rather than
 * promoting it to `internal` and creating cross-file coupling for a single extra call site.
 */
@Composable
private fun FormatCard(format: DeckFormat, selected: Boolean, comingSoon: Boolean, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val descriptionRes = formatRulesDescriptionRes(format)

    Surface(
        onClick = onClick,
        enabled = !comingSoon,
        shape = SmallCardShape,
        color = if (selected) mc.primaryAccent.copy(alpha = 0.12f) else mc.surface,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, mc.primaryAccent) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(spacing.md).heightIn(min = 48.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        text = format.displayName,
                        style = ty.titleMedium,
                        color = if (comingSoon) mc.textDisabled else mc.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (comingSoon) {
                        // C2 (design review, kept from the old grid tile): surfaceVariant would
                        // double up with the already-dim textDisabled label into a near-invisible
                        // badge on HallowedPrint.
                        Surface(shape = ChipShape, color = mc.textDisabled.copy(alpha = 0.25f)) {
                            Text(
                                text = stringResource(R.string.deck_wizard_coming_soon),
                                style = ty.labelSmall,
                                color = mc.textDisabled,
                                modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.xxs),
                            )
                        }
                    }
                }
                if (descriptionRes != null) {
                    Text(
                        text = stringResource(descriptionRes),
                        style = ty.bodySmall,
                        color = if (comingSoon) mc.textDisabled else mc.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = if (selected) mc.primaryAccent else mc.surfaceVariant,
                modifier = Modifier.size(24.dp),
            ) {
                if (selected) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = mc.onAccent, modifier = Modifier.size(16.dp))
                    }
                }
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
    onToggleUseCommunityData: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Spacer(Modifier.height(spacing.md))
            Text(stringResource(R.string.deck_wizard_review_title), style = ty.titleLarge, color = mc.textPrimary)

            uiState.selectedCommander?.let { commander ->
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    ReviewSectionLabel(stringResource(R.string.deck_wizard_review_commander_section))
                    CardRow(
                        card = commander,
                        isInCollection = true,
                        onClick = { /* Detail already visible if needed */ },
                        onRemove = null,
                        isCommander = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (uiState.seedCards.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    ReviewSectionLabel(stringResource(R.string.deck_wizard_review_seeds))
                    // A nested-scroll horizontal LazyRow inside the outer verticalScroll Column is
                    // safe here (different axis) -- same reasoning as the task's guidance for this
                    // screen; each tile keyed by scryfallId (unique, no duplicate-copy collision
                    // since seed cards are deduped by id upstream in the ViewModel).
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        items(uiState.seedCards, key = { it.scryfallId }) { card -> ReviewSeedCardTile(card) }
                    }
                }
            }

            Surface(shape = SmallCardShape, color = mc.backgroundSecondary, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    ReviewRow(stringResource(R.string.deck_wizard_review_format), uiState.selectedFormat?.displayName ?: "—")
                    ReviewChipRow(
                        label = stringResource(R.string.deck_wizard_review_direction),
                        chipText = uiState.selectedCommander?.name
                            ?: uiState.selectedArchetype?.displayName
                            ?: uiState.selectedDirectionTheme?.displayName
                            ?: uiState.selectedTribeLabel
                            ?: stringResource(R.string.deck_wizard_review_direction_none),
                    )
                    ReviewColorsRow(
                        label = stringResource(R.string.deck_wizard_review_colors),
                        colors = uiState.colorIdentity,
                    )
                }
            }

            Surface(
                onClick = onToggleFillLands,
                shape = SmallCardShape,
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

            // Deck Engine Unification plan (§5 Phase 3.5) — the shared source step. Hidden entirely
            // when the global community-engine flag is off (never a disabled-but-visible toggle for
            // a capability the user can't actually use — mirrors the Coming Soon format cards'
            // "never advertise something inert" convention).
            if (uiState.communityEngineAvailable) {
                Surface(
                    onClick = onToggleUseCommunityData,
                    shape = SmallCardShape,
                    color = mc.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(spacing.md).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.deck_wizard_use_community_data_title), style = ty.bodyMedium, color = mc.textPrimary)
                            Text(stringResource(R.string.deck_wizard_use_community_data_subtitle), style = ty.labelSmall, color = mc.textSecondary)
                        }
                        Switch(
                            checked = uiState.useCommunityData,
                            onCheckedChange = { onToggleUseCommunityData() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = mc.onAccent,
                                checkedTrackColor = mc.primaryAccent,
                                uncheckedThumbColor = mc.textDisabled,
                                uncheckedTrackColor = mc.surfaceVariant,
                            ),
                        )
                    }
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

/** Small uppercase-weight section header above a Review-step block (Commander / Seed cards). */
@Composable
private fun ReviewSectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.magicTypography.labelLarge, color = MaterialTheme.magicColors.textSecondary)
}

/** One seed card as a compact image tile for the Review step's horizontal seed-cards strip. */
@Composable
private fun ReviewSeedCardTile(card: Card) {
    val mc = MaterialTheme.magicColors
    AsyncImage(
        model = card.imageNormal,
        contentDescription = card.name,
        placeholder = painterResource(Res.drawable.mtg_card_back),
        error = painterResource(Res.drawable.mtg_card_back),
        fallback = painterResource(Res.drawable.mtg_card_back),
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(width = 72.dp, height = 100.dp)
            .clip(SmallCardShape)
            .background(mc.surfaceVariant),
    )
}

/** A [ReviewRow] variant that renders its value as a tonal chip -- used for the Direction/
 * archetype pick, which is a single discrete choice rather than free-form text. */
@Composable
private fun ReviewChipRow(label: String, chipText: String) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = ty.bodyMedium, color = mc.textSecondary)
        Surface(shape = ChipShape, color = mc.primaryAccent.copy(alpha = 0.14f)) {
            Text(
                text = chipText,
                style = ty.labelMedium,
                color = mc.primaryAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
            )
        }
    }
}

/** Renders the deck's color identity as real WUBRG mana-symbol icons ([ManaCostImages]) instead of
 * the plain `.symbol` letters -- falls back to the existing "Colorless" copy when empty. */
@Composable
private fun ReviewColorsRow(label: String, colors: Set<ManaColor>) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = ty.bodyMedium, color = mc.textSecondary)
        if (colors.isEmpty()) {
            Text(stringResource(R.string.deck_seeds_identity_colorless), style = ty.bodyMedium, color = mc.textPrimary)
        } else {
            // ManaColor.symbol is already a bare WUBRG letter ("W"/"U"/...), the exact token shape
            // ManaSymbolImage/ManaCostImages expects (it maps token -> scryfalls's card-symbols
            // SVG) -- wrapped in "{}" only to satisfy ManaCostImages' cost-string parser.
            ManaCostImages(
                manaCost = colors.sortedBy { it.ordinal }.joinToString(separator = "") { "{${it.symbol}}" },
                symbolSize = 20.dp,
                spacing = spacing.xxs,
            )
        }
    }
}
