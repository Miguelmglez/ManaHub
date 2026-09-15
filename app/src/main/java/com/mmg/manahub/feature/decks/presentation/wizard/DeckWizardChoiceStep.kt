package com.mmg.manahub.feature.decks.presentation.wizard
// COMMENTS_REVIEWED: 2026-09-15

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeRoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.RoleKey
import com.mmg.manahub.feature.decks.domain.template.AmbiguityGroup
import com.mmg.manahub.feature.decks.domain.template.CommanderDraftBuild

/** Deck Wizard v4, W7 Task B (R10) — capped alternatives shown per ambiguous section (E4). Verified
 * against the real 180-spec harness (`docs/plans/deck-wizard-commander-v4-progress.md`, Run 13):
 * covers the observed p75 (9 candidates/group) fully; only the top decile of sections truncate, and
 * "Choose for me"/"Let the wizard finish" still resolve from the engine's full candidate pool. */
internal const val CHOICE_ALTERNATIVES_CAP = 10

/** The ids a section displays: [tentativeIds] ALWAYS show (never hidden by [cap] -- plan 7.1), then
 * [alternativeIds] (already gain-ordered, see [AmbiguityGroup.candidateIds]'s own KDoc) truncated to
 * [cap], de-duplicated (a tentative default can never also appear in [alternativeIds] by
 * construction, but de-duping keeps this function safe against a caller that violates that). A
 * plain (non-`@Composable`) function so it is JVM-unit-testable without Compose. */
internal fun choiceDisplayIds(tentativeIds: List<String>, alternativeIds: List<String>, cap: Int = CHOICE_ALTERNATIVES_CAP): List<String> =
    (tentativeIds + alternativeIds.take(cap)).distinct()

/**
 * The Choice screen (plan 7.1-7.4): one section per [CommanderDraftBuild.ambiguityGroups], showing
 * the engine's own tentative picks (pre-selected, always visible — never hidden by the [Card]-count
 * cap below) plus its top alternatives ordered by marginal gain (already the order
 * [BuildCommanderDeckUseCase.buildWithGroups] hands back — see [AmbiguityGroup.candidateIds]'s own
 * KDoc), capped at [CHOICE_ALTERNATIVES_CAP] for display only. Zero scoring/classification logic of
 * this file's own — every candidate id resolves through [CommanderDraftBuild.candidatesById].
 */
@Composable
internal fun ChoiceStepContent(
    uiState: DeckWizardUiState,
    onToggleCard: (RoleKey, String) -> Unit,
    onAutoFillSection: (RoleKey) -> Unit,
    onFinish: () -> Unit,
    onCardClick: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val draft = uiState.commanderDraftBuild

    if (draft == null) {
        EmptyState(
            title = stringResource(R.string.deck_wizard_choice_error_title),
            subtitle = stringResource(R.string.deck_wizard_choice_error_message),
            icon = Icons.Default.AutoAwesome,
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item(key = "choice_header") {
                Column {
                    Text(stringResource(R.string.deck_wizard_choice_title), style = ty.titleLarge, color = mc.textPrimary)
                    Text(
                        stringResource(R.string.deck_wizard_choice_subtitle),
                        style = ty.bodyMedium,
                        color = mc.textSecondary,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                }
            }
            items(draft.ambiguityGroups, key = { it.sectionId }) { group ->
                val selected = uiState.choiceSelections[group.sectionId] ?: draft.tentativeByRole[group.sectionId].orEmpty()
                ChoiceSectionCard(
                    group = group,
                    draft = draft,
                    selectedIds = selected,
                    onToggle = { cardId -> onToggleCard(group.sectionId, cardId) },
                    onAutoFill = { onAutoFillSection(group.sectionId) },
                    onCardClick = onCardClick,
                )
            }
        }
        WizardStickyButton(
            label = stringResource(R.string.deck_wizard_choice_finish),
            enabled = true,
            onClick = onFinish,
        )
    }
}

@Composable
private fun ChoiceSectionCard(
    group: AmbiguityGroup,
    draft: CommanderDraftBuild,
    selectedIds: List<String>,
    onToggle: (String) -> Unit,
    onAutoFill: () -> Unit,
    onCardClick: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val tentativeIds = draft.tentativeByRole[group.sectionId].orEmpty()
    val selectedSet = selectedIds.toSet()

    // Both resolved through the SAME candidatesById map the engine itself scored against.
    val displayIds = choiceDisplayIds(tentativeIds, group.candidateIds)
    val displayCards = displayIds.mapNotNull { id -> draft.candidatesById[id]?.let { id to it } }

    Surface(shape = CardShape, color = mc.backgroundSecondary, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ArchetypeRoleClassifier.label(group.sectionId),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.deck_wizard_choice_section_choose, group.remainingSlots),
                    style = ty.labelMedium,
                    color = mc.primaryAccent,
                )
            }
            Text(
                text = stringResource(R.string.deck_wizard_choice_section_selected, selectedIds.size, group.remainingSlots),
                style = ty.bodySmall,
                color = mc.textSecondary,
                modifier = Modifier.padding(top = spacing.xxs, bottom = spacing.sm),
            )
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                displayCards.forEach { (id, card) ->
                    val isSelected = id in selectedSet
                    val isFull = selectedIds.size >= group.remainingSlots
                    ChoiceCandidateRow(
                        card = card,
                        isSelected = isSelected,
                        isTentativeDefault = id in tentativeIds,
                        // A tap that would ADD past the cap is a no-op in the VM too (defensive
                        // here so the row doesn't even look tappable once the section is full).
                        enabled = isSelected || !isFull,
                        onToggle = { onToggle(id) },
                        onCardClick = { onCardClick(id) },
                    )
                }
            }
            if (selectedIds.size < group.remainingSlots) {
                MagicCtaButton(
                    text = stringResource(R.string.deck_wizard_choice_autofill_section),
                    onClick = onAutoFill,
                    style = MagicCtaStyle.Ghost,
                    color = MagicCtaColor.Neutral,
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun ChoiceCandidateRow(
    card: Card,
    isSelected: Boolean,
    isTentativeDefault: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onCardClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(
        onClick = onToggle,
        enabled = enabled,
        shape = CardShape,
        color = if (isSelected) mc.primaryAccent.copy(alpha = 0.12f) else mc.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) mc.primaryAccent else mc.textDisabled,
                modifier = Modifier.size(24.dp),
            )
            AsyncImage(
                model = card.imageArtCrop ?: card.imageNormal,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 44.dp, height = 62.dp).clip(CardShape),
            )
            Column(Modifier.weight(1f)) {
                CardName(
                    name = card.name,
                    style = ty.bodyMedium,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isTentativeDefault) {
                    Text(
                        text = stringResource(R.string.deck_wizard_choice_current_pick),
                        style = ty.labelSmall,
                        color = mc.textSecondary,
                    )
                }
            }
            // A separate, smaller tap target for card inspection -- distinct from the row's own
            // toggle-selection Surface.onClick above it, so a user can inspect a candidate without
            // accidentally changing their selection.
            IconButton(onClick = onCardClick, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Info, contentDescription = card.name, tint = mc.textSecondary)
            }
        }
    }
}
