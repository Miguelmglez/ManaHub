package com.mmg.manahub.feature.decks.presentation.wizard
// COMMENTS_REVIEWED: 2026-09-16

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.core.model.DeckSlotEntry
import com.mmg.manahub.core.ui.components.CardRow
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
import com.mmg.manahub.feature.decks.domain.template.WizardDraftBuild
import com.mmg.manahub.feature.decks.presentation.components.CardDetailSheet

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

/** Deck Wizard 60-card wave (v6), plan §5 Phase 5.4 (S6): which [WizardDraftBuild.tentativeByRole]
 * card id a currently-open [CardDetailSheet] belongs to (`role = null` for a fallback-flagged
 * card, which has no ambiguity-group role at all) -- resolves both the [Card] to show and the
 * "selected copies" quantity to display, without duplicating that lookup at every call site. */
private data class ChoiceDetailSelection(val role: RoleKey?, val cardId: String)

/**
 * The Choice screen (plan 7.1-7.4; quantity-aware since Deck Wizard 60-card wave v6, plan §5 Phase
 * 5.4, S6): one section per [WizardDraftBuild.ambiguityGroups], showing the engine's own
 * tentative picks (pre-selected, always visible — never hidden by the [Card]-count cap below) plus
 * its top alternatives ordered by marginal gain (already the order
 * [BuildWizardDeckUseCase.buildWithGroups] hands back — see [AmbiguityGroup.candidateIds]'s own
 * KDoc), capped at [CHOICE_ALTERNATIVES_CAP] for display only. Zero scoring/classification logic of
 * this file's own — every candidate id resolves through [WizardDraftBuild.candidatesById].
 *
 * Every row is a shared [CardRow] (S6): Commander renders a `selected` boolean toggle, a 60-card
 * anchor a +/- quantity stepper. Tapping a row's image opens an inline, read-only
 * [CardDetailSheet] — the wizard no longer navigates to the full Card Detail screen from Choice
 * (the Info-icon tap target is gone with it).
 */
@Composable
internal fun ChoiceStepContent(
    uiState: DeckWizardUiState,
    onChangeQuantity: (RoleKey, String, Int) -> Unit,
    onAutoFillSection: (RoleKey) -> Unit,
    onFinish: () -> Unit,
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

    val isCommanderFormat = uiState.selectedFormat?.isCommanderFormat == true
    var detailSelection by remember { mutableStateOf<ChoiceDetailSelection?>(null) }

    // W7 fix 5.5 (design review P1): a compact "N of M sections decided" summary -- a section
    // counts as decided the moment the user has touched it (present in choiceSelections), exactly
    // BuildWizardDeckUseCase.finalize's own "absent = still on the tentative default" contract.
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
            if (draft.fallbackStandaloneIds.isNotEmpty() || draft.fallbackOffPlanIds.isNotEmpty()) {
                item(key = "choice_fallback_flag") {
                    FallbackFlagCard(draft = draft, onOpenDetail = { id -> detailSelection = ChoiceDetailSelection(null, id) })
                }
            }
            items(draft.ambiguityGroups, key = { it.sectionId }) { group ->
                val tentative = draft.tentativeCopies(group.sectionId)
                val current = uiState.choiceSelections[group.sectionId] ?: tentative
                ChoiceSectionCard(
                    group = group,
                    draft = draft,
                    isCommanderFormat = isCommanderFormat,
                    current = current,
                    tentative = tentative,
                    isDecided = uiState.choiceSelections.containsKey(group.sectionId),
                    onChangeQuantity = { cardId, delta -> onChangeQuantity(group.sectionId, cardId, delta) },
                    onAutoFill = { onAutoFillSection(group.sectionId) },
                    onOpenDetail = { id -> detailSelection = ChoiceDetailSelection(group.sectionId, id) },
                )
            }
        }
        WizardStickyButton(
            label = stringResource(R.string.deck_wizard_choice_finish),
            enabled = true,
            onClick = onFinish,
        )
    }

    detailSelection?.let { selection ->
        val card = draft.candidatesById[selection.cardId]
        if (card != null) {
            val quantity = if (selection.role != null) {
                (uiState.choiceSelections[selection.role] ?: draft.tentativeCopies(selection.role))[selection.cardId] ?: 0
            } else {
                draft.placedNonLand.firstOrNull { it.card.scryfallId == selection.cardId }?.quantity ?: 1
            }
            CardDetailSheet(
                deckCard = DeckSlotEntry(scryfallId = card.scryfallId, quantity = quantity, isSideboard = false, card = card, source = DeckCardSource.WIZARD),
                displayCard = card,
                isLoadingDetail = false,
                isCommander = false,
                isCommanderSelectionContext = false,
                tags = card.tags + card.userTags,
                onAdd = {},
                onRemove = {},
                onDelete = {},
                onChooseAsCommander = {},
                onRemoveCommander = {},
                onDismiss = { detailSelection = null },
                readOnly = true,
            )
        }
    }
}

/** Deck Wizard Commander v5 (D4): a read-only, honest flag for slots the wizard could only fill
 * once no on-plan candidate remained -- Standalone (own role, off-skeleton) shown first, off-plan
 * (the genuine last resort) after. Never interactive beyond opening a card's own detail: there is
 * nothing to choose here, only to see. */
@Composable
private fun FallbackFlagCard(draft: WizardDraftBuild, onOpenDetail: (String) -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    val standaloneCards = remember(draft) { draft.fallbackStandaloneIds.mapNotNull { id -> draft.candidatesById[id]?.let { id to it } } }
    val offPlanCards = remember(draft) { draft.fallbackOffPlanIds.mapNotNull { id -> draft.candidatesById[id]?.let { id to it } } }

    Surface(shape = CardShape, color = mc.backgroundSecondary, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = mc.secondaryAccent, modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(R.string.deck_wizard_choice_fallback_title),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                )
            }
            if (standaloneCards.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs), modifier = Modifier.padding(top = spacing.xs)) {
                    Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = mc.goldMtg, modifier = Modifier.size(16.dp))
                    Text(
                        text = stringResource(R.string.deck_wizard_choice_fallback_standalone_subtitle, standaloneCards.size),
                        style = ty.bodySmall,
                        color = mc.textSecondary,
                    )
                }
                FallbackFlagCardList(cards = standaloneCards, onOpenDetail = onOpenDetail, modifier = Modifier.padding(top = spacing.sm))
            }
            if (offPlanCards.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs), modifier = Modifier.padding(top = spacing.sm)) {
                    Icon(imageVector = Icons.Default.HelpOutline, contentDescription = null, tint = mc.textDisabled, modifier = Modifier.size(16.dp))
                    Text(
                        text = stringResource(R.string.deck_wizard_choice_fallback_offplan_subtitle, offPlanCards.size),
                        style = ty.bodySmall,
                        color = mc.textSecondary,
                    )
                }
                FallbackFlagCardList(cards = offPlanCards, onOpenDetail = onOpenDetail, modifier = Modifier.padding(top = spacing.sm))
            }
        }
    }
}

/** One [FallbackFlagCard] subsection's card list -- capped at [DEFAULT_VISIBLE_ALTERNATIVES] with a
 * "Show N more" affordance, mirroring [ChoiceSectionCard]'s own alternatives cap on the same screen. */
@Composable
private fun FallbackFlagCardList(cards: List<Pair<String, Card>>, onOpenDetail: (String) -> Unit, modifier: Modifier = Modifier) {
    val ty = MaterialTheme.magicTypography
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    var expanded by remember(cards) { mutableStateOf(false) }
    val visibleCards = if (expanded) cards else cards.take(DEFAULT_VISIBLE_ALTERNATIVES)
    val hiddenCount = cards.size - visibleCards.size

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        visibleCards.forEach { (id, card) ->
            // S6: FallbackFlagCard rows are informational only -- CardRow(onImageClick) with no
            // add/remove/select affordance, per plan §5 Phase 5.4.
            CardRow(
                card = card,
                isInCollection = true,
                onClick = { onOpenDetail(id) },
                onRemove = null,
                onImageClick = { onOpenDetail(id) },
            )
        }
        if (!expanded && hiddenCount > 0) {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = true },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(R.string.deck_wizard_choice_show_more, hiddenCount),
                    style = ty.labelMedium,
                    color = mc.primaryAccent,
                )
            }
        }
    }
}

@Composable
private fun ChoiceSectionCard(
    group: AmbiguityGroup,
    draft: WizardDraftBuild,
    isCommanderFormat: Boolean,
    current: Map<String, Int>,
    tentative: Map<String, Int>,
    isDecided: Boolean,
    onChangeQuantity: (String, Int) -> Unit,
    onAutoFill: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    // W7 fix 6 (design review P2): memoised per section -- both resolved through the SAME
    // candidatesById map the engine itself scored against, and otherwise recomputed every
    // recomposition (e.g. every toggle in ANY other section, since this Composable reads from the
    // shared uiState).
    val tentativeIds = remember(group, draft) { draft.tentativeByRole[group.sectionId].orEmpty().distinct() }
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

    // Deck Wizard 60-card wave (v6), plan §5 Phase 5.4 (S6): the section progress now counts
    // COPIES, not distinct ids.
    val totalSelected = current.values.sum()
    val isFull = totalSelected >= group.remainingSlots

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
                    text = stringResource(R.string.deck_wizard_choice_section_progress, totalSelected, group.remainingSlots),
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
                        isCommanderFormat = isCommanderFormat,
                        quantity = current[id] ?: 0,
                        isTentativeDefault = true,
                        // A tap that would ADD past the cap is a no-op in the VM too -- addEnabled
                        // is a visual affordance only, never the actual gate.
                        addEnabled = !isFull,
                        onIncrement = { onChangeQuantity(id, 1) },
                        onDecrement = { onChangeQuantity(id, -1) },
                        onOpenDetail = { onOpenDetail(id) },
                    )
                }
                visibleAlternativeCards.forEach { (id, card) ->
                    ChoiceCandidateRow(
                        card = card,
                        isCommanderFormat = isCommanderFormat,
                        quantity = current[id] ?: 0,
                        isTentativeDefault = false,
                        addEnabled = !isFull,
                        onIncrement = { onChangeQuantity(id, 1) },
                        onDecrement = { onChangeQuantity(id, -1) },
                        onOpenDetail = { onOpenDetail(id) },
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
            if (totalSelected < group.remainingSlots) {
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

/** S6: Commander renders a `selected` (quantity > 0) toggle row -- tapping it selects/deselects,
 * mirroring [CardRow]'s own boolean-selection convention. A 60-card anchor renders the +/-
 * quantity stepper instead (`onAdd`/`onRemove`, `onClick` also increments). Tapping the image
 * opens the read-only detail sheet in both cases -- the old dedicated Info icon is gone (S6: "the
 * Info icon is gone"). */
@Composable
private fun ChoiceCandidateRow(
    card: Card,
    isCommanderFormat: Boolean,
    quantity: Int,
    isTentativeDefault: Boolean,
    addEnabled: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val badge: (@Composable RowScope.() -> Unit)? = if (isTentativeDefault) {
        {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = mc.secondaryAccent,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(spacing.xxs))
            Text(
                text = stringResource(R.string.deck_wizard_choice_wizards_pick),
                style = ty.labelSmall,
                color = mc.secondaryAccent,
            )
        }
    } else {
        null
    }

    if (isCommanderFormat) {
        CardRow(
            card = card,
            isInCollection = true,
            selected = quantity > 0,
            onClick = { if (quantity > 0) onDecrement() else onIncrement() },
            onRemove = null,
            onImageClick = onOpenDetail,
            extraSupportingContent = badge,
        )
    } else {
        CardRow(
            card = card,
            isInCollection = true,
            quantity = quantity,
            onAdd = onIncrement,
            onRemove = onDecrement,
            addEnabled = addEnabled,
            onClick = onIncrement,
            onImageClick = onOpenDetail,
            extraSupportingContent = badge,
        )
    }
}
