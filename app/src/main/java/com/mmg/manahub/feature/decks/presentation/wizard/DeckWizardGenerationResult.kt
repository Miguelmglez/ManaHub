package com.mmg.manahub.feature.decks.presentation.wizard
// COMMENTS_REVIEWED: 2026-09-17

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.template.WizardBuildStage
import com.mmg.manahub.feature.decks.presentation.components.label

// Deck Wizard 60-card wave (v6), plan §5 Phase 5.1: this file's own `Result` section
// (`ResultContent`/`ResultSourceBadge`/`GapWarningRow`/`ReportChip`/`WizardOwnedCardRow`/
// `WizardCommunitySuggestionRow`) and the `BuildStage.label()` extension were DELETED --
// `the deleted RESULT phase` no longer exists (S13: every format opens Deck Studio directly after
// persist) and the Casual-only `BuildStage`/`completedStages` progress pair it displayed went
// with the Casual DIRECTION/IDENTITY/MANUAL_ADDS screens (S1's UI unification). Only
// [GeneratingContent] (shared by every anchor) survives in this file.

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
        // H1 (design review): the Scaffold sets contentWindowInsets = WindowInsets(0), so nothing
        // auto-insets the bottom nav bar here -- WizardStickyButton compensates via its own
        // navigationBarsPadding() on every other step, but this bespoke button must do it too.
        Box(
            Modifier.fillMaxSize().navigationBarsPadding().padding(spacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text(stringResource(R.string.deck_wizard_build_error_title), style = ty.titleLarge, color = mc.textPrimary)
                Text(uiState.buildError, style = ty.bodyMedium, color = mc.textSecondary)
                Button(
                    onClick = onRetry,
                    shape = ChipShape,
                    colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent, contentColor = mc.background),
                ) { Text(stringResource(R.string.deck_wizard_retry)) }
                OutlinedButton(onClick = onCancel, shape = ChipShape, border = BorderStroke(1.dp, mc.textSecondary)) {
                    Text(stringResource(R.string.action_back), color = mc.textSecondary)
                }
            }
        }
        return
    }

    // Deck Wizard Commander v3 plan, Phase 6 (6.3) -- the wizard's own staged-progress track
    // (commanderBuildStage/commanderCompletedStages). Deck Wizard 60-card wave (v6, plan §5 Phase
    // 5.1): the legacy Casual buildStage/completedStages pair was deleted along with the
    // Casual-only DIRECTION/IDENTITY/MANUAL_ADDS/RESULT screens (S1's UI unification) --
    // generateCasualDeck no longer surfaces per-stage progress until run C unifies generation onto
    // this ONE track for every format, so a Casual build just shows the generic "Validating…"
    // fallback with an empty completed-stages list below (harmless: nothing renders there).
    val currentStageLabel = uiState.commanderBuildStage?.label() ?: stringResource(R.string.deck_wizard_stage_validating)

    Column(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(spacing.lg),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MagicLoadingSpinner(size = MagicLoadingSize.Medium)
            Spacer(Modifier.height(spacing.lg))
            Text(
                text = currentStageLabel,
                style = ty.titleMedium,
                color = mc.textPrimary,
            )
            Spacer(Modifier.height(spacing.lg))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                uiState.commanderCompletedStages.forEach { stage ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = mc.lifePositive, modifier = Modifier.size(16.dp))
                        Text(stage.label(), style = ty.labelMedium, color = mc.textSecondary)
                    }
                }
            }
        }
        OutlinedButton(
            onClick = onCancel,
            shape = ChipShape,
            border = BorderStroke(1.dp, mc.textSecondary),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(stringResource(R.string.action_cancel), color = mc.textSecondary)
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

