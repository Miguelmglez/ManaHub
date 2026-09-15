package com.mmg.manahub.feature.decks.presentation.wizard
// COMMENTS_REVIEWED: 2026-09-15

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeRoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.RoleKey
import com.mmg.manahub.feature.decks.domain.template.AmbiguityGroup
import com.mmg.manahub.feature.decks.domain.template.CommanderDraftBuild
import org.jetbrains.compose.resources.painterResource

/** Deck Wizard v4, W7 Task B (R10) — capped alternatives shown per ambiguous section (E4). Verified
 * against the real 180-spec harness (`docs/plans/deck-wizard-commander-v4-progress.md`, Run 13):
 * covers the observed p75 (9 candidates/group) fully; only the top decile of sections truncate, and
 * "Choose for me"/"Let the wizard finish" still resolve from the engine's full candidate pool. */
internal const val CHOICE_ALTERNATIVES_CAP = 10

/** W7 fix 5.5 (design review P1): a section collapses its alternatives to this many by default --
 * measured max is 8 sections/build (7.0c), so an uncollapsed worst case (1 tentative + up to 10
 * alternatives) per section adds up fast. Tentative picks are NEVER collapsed (plan 7.1). */
internal const val DEFAULT_VISIBLE_ALTERNATIVES = 3

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

    // W7 fix 5.5 (design review P1): a compact "N of M sections decided" summary -- a section
    // counts as decided the moment the user has touched it (present in choiceSelections), exactly
    // BuildCommanderDeckUseCase.finalize's own "absent = still on the tentative default" contract.
    val decidedSections = draft.ambiguityGroups.count { uiState.choiceSelections.containsKey(it.sectionId) }
    val totalSections = draft.ambiguityGroups.size

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
                    if (totalSections > 1) {
                        Text(
                            stringResource(R.string.deck_wizard_choice_sections_progress, decidedSections, totalSections),
                            style = ty.labelMedium,
                            color = mc.primaryAccent,
                            modifier = Modifier.padding(top = spacing.xs),
                        )
                    }
                }
            }
            items(draft.ambiguityGroups, key = { it.sectionId }) { group ->
                val selected = uiState.choiceSelections[group.sectionId] ?: draft.tentativeByRole[group.sectionId].orEmpty()
                ChoiceSectionCard(
                    group = group,
                    draft = draft,
                    selectedIds = selected,
                    isDecided = uiState.choiceSelections.containsKey(group.sectionId),
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
    isDecided: Boolean,
    onToggle: (String) -> Unit,
    onAutoFill: () -> Unit,
    onCardClick: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val selectedSet = selectedIds.toSet()

    // W7 fix 6 (design review P2): memoised per section -- both resolved through the SAME
    // candidatesById map the engine itself scored against, and otherwise recomputed every
    // recomposition (e.g. every toggle in ANY other section, since this Composable reads from the
    // shared uiState).
    val tentativeIds = remember(group, draft) { draft.tentativeByRole[group.sectionId].orEmpty() }
    val alternativeIds = remember(group, draft) {
        val tentativeSet = tentativeIds.toSet()
        choiceDisplayIds(tentativeIds, group.candidateIds).filterNot { it in tentativeSet }
    }
    val tentativeCards = remember(tentativeIds, draft) {
        tentativeIds.mapNotNull { id -> draft.candidatesById[id]?.let { id to it } }
    }
    val alternativeCards = remember(alternativeIds, draft) {
        alternativeIds.mapNotNull { id -> draft.candidatesById[id]?.let { id to it } }
    }

    // W7 fix 5.5: collapse alternatives beyond the default window -- tentative picks are NEVER
    // collapsed. Keyed on the section id so each card keeps its own expand state independently.
    var expanded by remember(group.sectionId) { mutableStateOf(false) }
    val visibleAlternativeCards = if (expanded) alternativeCards else alternativeCards.take(DEFAULT_VISIBLE_ALTERNATIVES)
    val hiddenAlternativesCount = alternativeCards.size - visibleAlternativeCards.size

    // W7 fix 6 (design review P2): hoisted out of the per-row loop below -- identical for every
    // row in this section.
    val isFull = selectedIds.size >= group.remainingSlots

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
                    // W7 fix 6 (design review P2): "Choose N" + "x/N selected" merged into ONE
                    // line that reads as a unit for TalkBack.
                    text = stringResource(R.string.deck_wizard_choice_section_progress, selectedIds.size, group.remainingSlots),
                    style = ty.labelMedium,
                    color = if (isDecided) mc.lifePositive else mc.primaryAccent,
                )
            }
            Text(
                text = if (isDecided) {
                    stringResource(R.string.deck_wizard_choice_decided_by_you)
                } else {
                    stringResource(R.string.deck_wizard_choice_still_on_wizards_picks)
                },
                style = ty.bodySmall,
                color = mc.textSecondary,
                modifier = Modifier.padding(top = spacing.xxs, bottom = spacing.sm),
            )
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                tentativeCards.forEach { (id, card) ->
                    ChoiceCandidateRow(
                        card = card,
                        isSelected = id in selectedSet,
                        isTentativeDefault = true,
                        enabled = id in selectedSet || !isFull,
                        onToggle = { onToggle(id) },
                        onCardClick = { onCardClick(id) },
                    )
                }
                visibleAlternativeCards.forEach { (id, card) ->
                    ChoiceCandidateRow(
                        card = card,
                        isSelected = id in selectedSet,
                        isTentativeDefault = false,
                        // A tap that would ADD past the cap is a no-op in the VM too (defensive
                        // here so the row doesn't even look tappable once the section is full).
                        enabled = id in selectedSet || !isFull,
                        onToggle = { onToggle(id) },
                        onCardClick = { onCardClick(id) },
                    )
                }
            }
            if (!expanded && hiddenAlternativesCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { expanded = true },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = stringResource(R.string.deck_wizard_choice_show_more, hiddenAlternativesCount),
                        style = ty.labelMedium,
                        color = mc.primaryAccent,
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
    // W7 fix 4 (design review P0-1): a disabled (cap-reached) row must LOOK disabled -- dimmed
    // surface/content -- since Surface's own `enabled` already swallows the tap but gave no visual
    // signal on its own.
    val rowAlpha = if (enabled) 1f else 0.45f
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // W7 fix 4 (design review P0-2): Modifier.selectable applies the `selected`/Role
            // .Checkbox semantics TalkBack needs -- a plain Surface(onClick, enabled) never
            // announced selection state at all.
            .selectable(selected = isSelected, enabled = enabled, role = Role.Checkbox, onClick = onToggle),
        color = if (isSelected) mc.primaryAccent.copy(alpha = 0.12f * rowAlpha) else mc.surface.copy(alpha = rowAlpha),
    ) {
        Row(
            modifier = Modifier.padding(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (!enabled) mc.textDisabled.copy(alpha = 0.5f) else if (isSelected) mc.primaryAccent else mc.textDisabled,
                modifier = Modifier.size(24.dp),
            )
            AsyncImage(
                model = card.imageArtCrop ?: card.imageNormal,
                contentDescription = null,
                placeholder = painterResource(Res.drawable.mtg_card_back),
                error = painterResource(Res.drawable.mtg_card_back),
                fallback = painterResource(Res.drawable.mtg_card_back),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 44.dp, height = 62.dp).clip(CardShape),
            )
            Column(Modifier.weight(1f)) {
                CardName(
                    name = card.name,
                    style = ty.bodyMedium,
                    color = if (enabled) mc.textPrimary else mc.textDisabled,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isTentativeDefault) {
                    // W7 fix 5.4 (design review P1): a distinct badge (icon + tonal chip),
                    // independent of the row's own selection tint, replacing the old
                    // labelSmall/textSecondary caption that read too weakly and was easily
                    // confused with "currently selected".
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                        modifier = Modifier.padding(top = spacing.xxs),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = mc.secondaryAccent,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = stringResource(R.string.deck_wizard_choice_wizards_pick),
                            style = ty.labelSmall,
                            color = mc.secondaryAccent,
                        )
                    }
                }
            }
            // A separate, smaller tap target for card inspection -- distinct from the row's own
            // toggle-selection Surface.onClick above it, so a user can inspect a candidate without
            // accidentally changing their selection.
            IconButton(onClick = onCardClick, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.Info,
                    // W7 fix 5.7 (design review P1): a dedicated description -- the old
                    // `card.name` duplicated the row's own visible name for no extra information.
                    contentDescription = stringResource(R.string.deck_wizard_choice_view_details, card.name),
                    tint = mc.textSecondary,
                )
            }
        }
    }
}
