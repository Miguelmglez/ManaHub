package com.mmg.manahub.feature.decks.presentation.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.core.model.DeckSlotEntry
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.CardRow
import com.mmg.manahub.core.ui.components.ManaColorPicker
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeRoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.RoleKey
import com.mmg.manahub.feature.decks.domain.engine.ResolvedArchetypeSkeleton
import com.mmg.manahub.feature.decks.domain.engine.StrategyPickerSelection
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.presentation.components.CardDetailSheet
import com.mmg.manahub.feature.decks.presentation.components.StrategyPickerSheet
import com.mmg.manahub.feature.decks.presentation.components.TribeOption
import org.jetbrains.compose.resources.painterResource

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Wizard & Engine Rework plan (docs/plans/deck-wizard-rework-plan.md), Workstream 2 --
//  the new Commander-only step sequence: COMMANDER_PICK (2.1) -> STRATEGY (2.2) -> MANUAL_ADDS
//  (2.3, SHARED with a future Casual caller -- Workstream 3).
// ═══════════════════════════════════════════════════════════════════════════════

// ── 2.1 — COMMANDER_PICK ──────────────────────────────────────────────────────

/**
 * The new first Commander step: a color-identity filter row ([ManaColorPicker]) + search bar over
 * commander candidates. Candidate pool per D-G: local-collection commanders
 * ([com.mmg.manahub.feature.decks.domain.engine.CommanderEligibility], surfaced via
 * [DeckWizardUiState.collectionProfile]'s existing `commanderCandidates`) by default, OR (behind
 * [DeckWizardUiState.includeOutsideCollection], D-A) a live Scryfall `is:commander` search
 * ([DeckWizardUiState.commanderSearchResults]). Tapping a candidate opens the SHARED
 * [CardDetailSheet] (the one in `presentation/components/`, NOT the legacy private duplicate) in
 * its commander-selection context, mirroring [com.mmg.manahub.feature.decks.presentation
 * .DeckStudioScreen]'s exact "Choose Commander" wiring precedent.
 *
 * Once [DeckWizardUiState.selectedCommander] is set, this step shows the picked commander's
 * [CardRow] (commander style) + a "Change commander" affordance instead of the candidate list (same as the
 * old Direction-embedded commander picker's UX).
 */
@Composable
internal fun CommanderPickStepContent(
    uiState: DeckWizardUiState,
    onToggleColorFilter: (String) -> Unit,
    onToggleIncludeOutsideCollection: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectCommander: (Card) -> Unit,
    onClearCommander: () -> Unit,
    onNext: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    var inspectionCard by remember { mutableStateOf<Card?>(null) }

    // Computed OUTSIDE the LazyColumn content block: LazyListScope.() -> Unit is a plain DSL
    // lambda, not @Composable, so `remember` cannot be called from within it (see
    // ManualAddsStepContent below for the same established pattern).
    val commander = uiState.selectedCommander
    val query = uiState.commanderQuery.trim()
    val filterSymbols = uiState.commanderColorFilter.map { it.symbol }.toSet()
    val localCandidates = remember(uiState.collectionProfile, filterSymbols, query) {
        uiState.collectionProfile?.commanderCandidates.orEmpty()
            .map { it.card }
            .filter { card -> filterSymbols.isEmpty() || card.colorIdentity.all { it in filterSymbols } }
            .filter { card -> query.isEmpty() || card.name.contains(query, ignoreCase = true) }
    }
    val showOutsideResults = uiState.includeOutsideCollection && query.length >= 2
    val candidatesToShow = if (showOutsideResults) uiState.commanderSearchResults else localCandidates

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item(key = "header") {
                Column {
                    Text(stringResource(R.string.deck_wizard_commander_pick_title), style = ty.titleLarge, color = mc.textPrimary)
                    Text(
                        stringResource(R.string.deck_wizard_commander_pick_subtitle),
                        style = ty.bodyMedium,
                        color = mc.textSecondary,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                }
            }

            if (commander != null) {
                item(key = "commander_banner") {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        CardRow(
                    card = commander,
                    isInCollection = true, // It's a selected commander from a pool/collection
                    onClick = { /* Detail already visible if needed */ },
                    onRemove = null,
                    isCommander = true,
                    modifier = Modifier.fillMaxWidth()
                )
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClearCommander),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.deck_wizard_change_commander), style = ty.labelMedium, color = mc.textSecondary)
                        }
                    }
                }
            } else {
                item(key = "color_filter") {
                    ManaColorPicker(
                        selectedColors = uiState.commanderColorFilter.map { it.symbol }.toSet(),
                        onToggleColor = onToggleColorFilter,
                        colors = listOf("All", "W", "U", "B", "R", "G"),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "outside_toggle") {
                    OutsideCollectionToggleRow(checked = uiState.includeOutsideCollection, onToggle = onToggleIncludeOutsideCollection)
                }
                item(key = "search") {
                    OutlinedTextField(
                        value = uiState.commanderQuery,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.deck_wizard_commander_search_hint), style = ty.bodyMedium, color = mc.textDisabled) },
                        leadingIcon = {
                            if (uiState.isSearchingCommander) {
                                MagicLoadingSpinner(size = MagicLoadingSize.XSmall)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, tint = mc.textSecondary)
                            }
                        },
                        shape = CardShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = mc.primaryAccent,
                            unfocusedBorderColor = mc.surfaceVariant,
                            focusedTextColor = mc.textPrimary,
                            unfocusedTextColor = mc.textPrimary,
                            cursorColor = mc.primaryAccent,
                        ),
                    )
                }

                if (candidatesToShow.isNotEmpty()) {
                    item(key = "candidates") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            items(candidatesToShow, key = { "cmdcand_${it.scryfallId}" }) { card ->
                                CommanderCandidateTile(card = card, onClick = { inspectionCard = card })
                            }
                        }
                    }
                } else if (!showOutsideResults) {
                    item(key = "empty") {
                        Text(stringResource(R.string.deck_wizard_commander_pick_empty), style = ty.bodySmall, color = mc.textSecondary)
                    }
                }
            }
        }
        WizardStickyButton(
            label = stringResource(R.string.deck_wizard_next),
            enabled = uiState.selectedCommander != null,
            onClick = onNext,
        )
    }

    inspectionCard?.let { card ->
        CardDetailSheet(
            deckCard = DeckSlotEntry(scryfallId = card.scryfallId, quantity = 0, isSideboard = false, card = card, source = DeckCardSource.USER),
            displayCard = card,
            isLoadingDetail = false,
            isCommander = false,
            isCommanderSelectionContext = true,
            tags = card.tags + card.userTags,
            onAdd = {},
            onRemove = {},
            onDelete = {},
            onChooseAsCommander = { chosen ->
                onSelectCommander(chosen)
                inspectionCard = null
            },
            onRemoveCommander = { inspectionCard = null },
            onDismiss = { inspectionCard = null },
        )
    }
}

/** A simple, tap-to-open card image tile for the COMMANDER_PICK candidate row -- unlike
 * [WizardCardImageTile] (Direction step's seed/commander tiles), this has ONE action only (tap
 * opens the shared [CardDetailSheet]), so it does not need that tile's separate magnifier badge. */
@Composable
private fun CommanderCandidateTile(card: Card, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    Box(
        modifier = Modifier
            .width(88.dp)
            .aspectRatio(63f / 88f)
            .clip(CardShape)
            .background(mc.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = card.imageNormal,
            contentDescription = card.name,
            placeholder = painterResource(Res.drawable.mtg_card_back),
            error = painterResource(Res.drawable.mtg_card_back),
            fallback = painterResource(Res.drawable.mtg_card_back),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The D-A "include outside your collection" toggle row -- shared visual shape between
 * COMMANDER_PICK (2.1), MANUAL_ADDS (2.3), and (Workstream 3) Flow A's own seed search. Internal
 * (not `private`) so [CardsFlowDirectionContent] in `DeckWizardDirectionIdentity.kt` can reuse it
 * too, per the plan's own "reuse WS2.1's D-A toggle/pattern, don't reinvent it" instruction. */
@Composable
internal fun OutsideCollectionToggleRow(checked: Boolean, onToggle: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(onClick = onToggle, shape = CardShape, color = mc.surface, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(spacing.md).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.deck_wizard_include_outside_collection_title), style = ty.bodyMedium, color = mc.textPrimary)
                Text(stringResource(R.string.deck_wizard_include_outside_collection_subtitle), style = ty.labelSmall, color = mc.textSecondary)
            }
            Switch(
                checked = checked,
                onCheckedChange = { onToggle() },
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

// ── 2.2 — STRATEGY ────────────────────────────────────────────────────────────

/**
 * Once a commander is picked, derive its supported strategies ([DeckWizardUiState
 * .commanderStrategyCandidates]) and render them through the SHARED [StrategyPickerSheet]
 * (Workstream 1.2), restricted to exactly those candidates -- the picker itself always additionally
 * offers [ArchetypeId.GENERIC] ("Balanced") as the Commander-only escape hatch (D-B/plan 2.2).
 */
@Composable
internal fun StrategyStepContent(
    uiState: DeckWizardUiState,
    onSelectArchetype: (ArchetypeId?) -> Unit,
    onToggleTheme: (ThemeId) -> Unit,
    onSelectTribe: (String?) -> Unit,
    onNext: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val candidates = uiState.commanderStrategyCandidates
    val selection = StrategyPickerSelection(
        archetype = uiState.selectedArchetype,
        themes = uiState.selectedStrategyThemes,
        tribe = uiState.selectedTribeKey,
    )
    val availableTribes = remember(candidates.tribes) {
        candidates.tribes.map { TribeOption(key = it.key, label = it.label) }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = spacing.lg, vertical = spacing.md)) {
            Text(stringResource(R.string.deck_wizard_strategy_step_title), style = ty.titleLarge, color = mc.textPrimary)
            Text(
                stringResource(R.string.deck_wizard_strategy_step_subtitle),
                style = ty.bodyMedium,
                color = mc.textSecondary,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
        if (uiState.isLoadingCommanderStrategies) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                MagicLoadingSpinner()
            }
        } else {
            StrategyPickerSheet(
                selection = selection,
                availableTribes = availableTribes,
                onSelectArchetype = onSelectArchetype,
                onToggleTheme = onToggleTheme,
                onSelectTribe = onSelectTribe,
                modifier = Modifier.weight(1f),
                availableArchetypes = candidates.archetypes.toSet(),
                availableThemes = candidates.themes.toSet(),
            )
        }
        WizardStickyButton(
            label = stringResource(R.string.deck_wizard_next),
            enabled = true,
            onClick = onNext,
        )
    }
}

// ── 2.3 — MANUAL_ADDS (SHARED, WS3 mounts this same composable for the Casual flows) ─────────

private data class ManualAddsRoleSection(val key: RoleKey, val label: String, val filled: Int, val target: Int)

/** Groups [seedCards] by their best-matching role (per [ArchetypeRoleClassifier.classify], among
 * only the keys [skeleton] actually assigns a non-zero ideal band to) and returns one section per
 * such role, target-descending -- "seeds-only counts" per plan 2.3 (a card whose best role isn't in
 * [skeleton] at all contributes to no section, but is still a real seed; this is a display grouping,
 * never an enforcement gate). */
private fun buildRoleSections(skeleton: ResolvedArchetypeSkeleton, seedCards: List<Card>): List<ManualAddsRoleSection> {
    val wantedKeys = skeleton.roleTargets.filterValues { it.ideal > 0 }.keys
    val filledByRole = mutableMapOf<RoleKey, Int>()
    seedCards.forEach { card ->
        val scores = ArchetypeRoleClassifier.classify(card).filterKeys { it in wantedKeys }
        val bestKey = scores.maxByOrNull { it.value }?.key ?: return@forEach
        filledByRole[bestKey] = (filledByRole[bestKey] ?: 0) + 1
    }
    return skeleton.roleTargets.entries
        .filter { it.value.ideal > 0 }
        .sortedByDescending { it.value.ideal }
        .map { (key, target) -> ManualAddsRoleSection(key, ArchetypeRoleClassifier.label(key), filledByRole[key] ?: 0, target.ideal) }
}

/**
 * Skeleton-guided, fully skippable card search + add step (plan 2.3). Built SHARED for reuse: every
 * parameter this composable reads off [uiState] is format-agnostic (skeleton/seeds/color identity/
 * search state), so a future Casual caller (Workstream 3) can mount it unchanged once its own VM
 * paths populate the same [DeckWizardUiState] fields.
 *
 * Hard-filters candidates by [DeckWizardUiState.colorIdentity] subset (`cardIdentity ⊆
 * commanderIdentity`, colorless always included per the empty-list-vacuous-`all{}` rule) --
 * Workstream 9 note: this reads whatever is CURRENTLY in [DeckWizardUiState.colorIdentity] at call
 * time; if a future workstream makes color identity multi-sourced/derived differently, this filter
 * point is the one to revisit.
 */
@Composable
internal fun ManualAddsStepContent(
    uiState: DeckWizardUiState,
    onQueryChange: (String) -> Unit,
    onToggleIncludeOutsideCollection: () -> Unit,
    onSelectRoleFilter: (String?) -> Unit,
    onAddSeed: (Card) -> Unit,
    onRemoveSeed: (Card) -> Unit,
    onNext: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val skeleton = uiState.manualAddsSkeleton

    val roleSections = remember(skeleton, uiState.seedCards) {
        skeleton?.let { buildRoleSections(it, uiState.seedCards) }.orEmpty()
    }

    val identitySymbols = remember(uiState.colorIdentity) { uiState.colorIdentity.map { it.symbol }.toSet() }
    val query = uiState.manualAddsQuery.trim()
    val seededIds = remember(uiState.seedCards) { uiState.seedCards.map { it.scryfallId }.toSet() }

    val localCandidates = remember(uiState.ownedCards, seededIds, identitySymbols, query, uiState.manualAddsRoleFilter) {
        uiState.ownedCards
            .filterNot { it.scryfallId in seededIds }
            .filter { card -> card.colorIdentity.all { it in identitySymbols } }
            .filter { card -> query.isEmpty() || card.name.contains(query, ignoreCase = true) }
            .filter { card ->
                val roleFilter = uiState.manualAddsRoleFilter ?: return@filter true
                (ArchetypeRoleClassifier.classify(card)[roleFilter] ?: 0f) > 0f
            }
            .take(MANUAL_ADDS_RESULT_CAP)
    }
    val showOutsideResults = uiState.includeOutsideCollection && query.length >= 2
    val candidatesToShow = if (showOutsideResults) uiState.manualAddsSearchResults else localCandidates

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item(key = "header") {
                Column {
                    Text(stringResource(R.string.deck_wizard_manual_adds_title), style = ty.titleLarge, color = mc.textPrimary)
                    Text(
                        stringResource(R.string.deck_wizard_manual_adds_subtitle),
                        style = ty.bodyMedium,
                        color = mc.textSecondary,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                }
            }

            if (roleSections.isNotEmpty()) {
                item(key = "role_sections") {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        roleSections.forEach { section ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(section.label, style = ty.bodyMedium, color = mc.textSecondary)
                                Text(
                                    stringResource(R.string.deck_wizard_manual_adds_role_progress, section.filled, section.target),
                                    style = ty.labelMedium,
                                    color = mc.textPrimary,
                                )
                            }
                        }
                    }
                }
                item(key = "role_filters") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        roleSections.forEach { section ->
                            DirectionChip(
                                label = section.label,
                                selected = uiState.manualAddsRoleFilter == section.key,
                                onClick = { onSelectRoleFilter(section.key) },
                            )
                        }
                    }
                }
            }

            item(key = "outside_toggle") {
                OutsideCollectionToggleRow(checked = uiState.includeOutsideCollection, onToggle = onToggleIncludeOutsideCollection)
            }
            item(key = "search") {
                OutlinedTextField(
                    value = uiState.manualAddsQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.deck_wizard_manual_adds_search_hint), style = ty.bodyMedium, color = mc.textDisabled) },
                    leadingIcon = {
                        if (uiState.isSearchingManualAdds) {
                            MagicLoadingSpinner(size = MagicLoadingSize.XSmall)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, tint = mc.textSecondary)
                        }
                    },
                    shape = CardShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = mc.primaryAccent,
                        unfocusedBorderColor = mc.surfaceVariant,
                        focusedTextColor = mc.textPrimary,
                        unfocusedTextColor = mc.textPrimary,
                        cursorColor = mc.primaryAccent,
                    ),
                )
            }

            if (candidatesToShow.isEmpty()) {
                item(key = "empty") {
                    Text(stringResource(R.string.deck_wizard_manual_adds_empty), style = ty.bodySmall, color = mc.textSecondary)
                }
            } else {
                items(candidatesToShow, key = { "manualcand_${it.scryfallId}" }) { card ->
                    CardRow(card = card, isInCollection = !showOutsideResults, onClick = { onAddSeed(card) }, onRemove = null)
                }
            }

            if (uiState.seedCards.isNotEmpty()) {
                item(key = "added_header") {
                    Text(
                        stringResource(R.string.deck_wizard_manual_adds_added_title),
                        style = ty.labelLarge,
                        color = mc.primaryAccent,
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                }
                items(uiState.seedCards, key = { "manualadded_${it.scryfallId}" }) { card ->
                    CardRow(card = card, isInCollection = true, onClick = {}, onRemove = { onRemoveSeed(card) })
                }
            }
        }
        WizardStickyButton(
            label = stringResource(R.string.deck_wizard_next),
            enabled = true,
            onClick = onNext,
        )
    }
}

private const val MANUAL_ADDS_RESULT_CAP = 40
