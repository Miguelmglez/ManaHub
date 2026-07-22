package com.mmg.manahub.feature.decks.presentation.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ColorStrategyEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckIdentitySeedTags
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.domain.template.CollectionTribeSignal
import com.mmg.manahub.feature.decks.domain.template.OwnedCommanderCandidate
import com.mmg.manahub.feature.decks.domain.usecase.SeedStrategyCandidate
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════════════════
//  Step 2 — Direction
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Deck Engine Unification plan (§5 Phase 3.1) — dispatches Step 2's content by
 * [DeckWizardUiState.entryFlow]. [WizardEntryFlow.CARDS] is the ORIGINAL wizard content
 * ([CardsFlowDirectionContent], unchanged in shape, now also showing a ranked "Suggested
 * strategies" section once seeds exist); [WizardEntryFlow.COLORS]/[WizardEntryFlow.STRATEGY] are the
 * two NEW flows, both defined in `DeckWizardEntryFlows.kt`. All three converge on the SAME
 * one-slot archetype/theme/tribe pick and the SAME [onAddSeed]/[onRemoveSeed] seed list — see each
 * flow's own KDoc for its specific UX.
 */
@Composable
internal fun DirectionStepContent(
    uiState: DeckWizardUiState,
    onSelectDirectionTag: (CardTag) -> Unit,
    onSelectTribe: (CollectionTribeSignal) -> Unit,
    onCommanderQueryChange: (String) -> Unit,
    onSelectCommander: (Card) -> Unit,
    onClearCommander: () -> Unit,
    onToggleSeedPicker: () -> Unit,
    onSeedQueryChange: (String) -> Unit,
    onAddSeed: (Card) -> Unit,
    onRemoveSeed: (Card) -> Unit,
    onSelectSeedStrategyCandidate: (SeedStrategyCandidate) -> Unit,
    onToggleColorFlowColor: (ManaColor) -> Unit,
    onSelectColorAffinityEntry: (ColorStrategyEntry) -> Unit,
    onTaxonomyQueryChange: (String) -> Unit,
    onSelectTaxonomyArchetype: (ArchetypeId) -> Unit,
    onSelectTaxonomyTheme: (ThemeId) -> Unit,
    onSelectColorCombo: (ColorComboSuggestion) -> Unit,
    onToggleSuggestedSeed: (Card) -> Unit,
    onNext: () -> Unit,
) {
    when (uiState.entryFlow) {
        WizardEntryFlow.CARDS -> CardsFlowDirectionContent(
            uiState = uiState,
            onSelectDirectionTag = onSelectDirectionTag,
            onSelectTribe = onSelectTribe,
            onCommanderQueryChange = onCommanderQueryChange,
            onSelectCommander = onSelectCommander,
            onClearCommander = onClearCommander,
            onToggleSeedPicker = onToggleSeedPicker,
            onSeedQueryChange = onSeedQueryChange,
            onAddSeed = onAddSeed,
            onRemoveSeed = onRemoveSeed,
            onSelectSeedStrategyCandidate = onSelectSeedStrategyCandidate,
            onNext = onNext,
        )
        WizardEntryFlow.COLORS -> ColorsFlowDirectionContent(
            uiState = uiState,
            onToggleColor = onToggleColorFlowColor,
            onSelectAffinityEntry = onSelectColorAffinityEntry,
            onToggleSuggestedSeed = onToggleSuggestedSeed,
            onNext = onNext,
        )
        WizardEntryFlow.STRATEGY -> StrategyFlowDirectionContent(
            uiState = uiState,
            onQueryChange = onTaxonomyQueryChange,
            onSelectArchetype = onSelectTaxonomyArchetype,
            onSelectTheme = onSelectTaxonomyTheme,
            onSelectCombo = onSelectColorCombo,
            onToggleSuggestedSeed = onToggleSuggestedSeed,
            onNext = onNext,
        )
    }
}

@Composable
private fun CardsFlowDirectionContent(
    uiState: DeckWizardUiState,
    onSelectDirectionTag: (CardTag) -> Unit,
    onSelectTribe: (CollectionTribeSignal) -> Unit,
    onCommanderQueryChange: (String) -> Unit,
    onSelectCommander: (Card) -> Unit,
    onClearCommander: () -> Unit,
    onToggleSeedPicker: () -> Unit,
    onSeedQueryChange: (String) -> Unit,
    onAddSeed: (Card) -> Unit,
    onRemoveSeed: (Card) -> Unit,
    onSelectSeedStrategyCandidate: (SeedStrategyCandidate) -> Unit,
    onNext: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val isCommanderFormat = uiState.selectedFormat == DeckFormat.COMMANDER
    val canProceed = !isCommanderFormat || uiState.selectedCommander != null

    // C1 (design review): the sticky CTA is a real Column sibling, not a Box overlay with a
    // guessed bottom-padding reservation (see FormatStepContent for the full rationale).
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, top = spacing.md, bottom = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item(key = "header") {
                Column {
                    Text(stringResource(R.string.deck_wizard_direction_title), style = ty.titleLarge, color = mc.textPrimary)
                    Text(
                        stringResource(R.string.deck_wizard_direction_subtitle),
                        style = ty.bodyMedium,
                        color = mc.textSecondary,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                }
            }

            if (isCommanderFormat) {
                item(key = "commander_section") {
                    CommanderPickerSection(
                        uiState = uiState,
                        onQueryChange = onCommanderQueryChange,
                        onSelect = onSelectCommander,
                        onClear = onClearCommander,
                    )
                }
            }

            item(key = "collection_leans") {
                CollectionLeanSection(
                    uiState = uiState,
                    onSelectDirectionTag = onSelectDirectionTag,
                    onSelectTribe = onSelectTribe,
                )
            }

            item(key = "seed_toggle") {
                SeedPickerToggleRow(expanded = uiState.showSeedPicker, onToggle = onToggleSeedPicker)
            }
            if (uiState.showSeedPicker) {
                item(key = "seed_search") {
                    SeedSearchInline(query = uiState.seedQuery, isSearching = uiState.isSearchingSeeds, onQueryChange = onSeedQueryChange)
                }
                if (uiState.seedQuery.trim().length >= 2 && uiState.seedSearchResults.isNotEmpty()) {
                    items(uiState.seedSearchResults, key = { "seedres_${it.scryfallId}" }) { card ->
                        WizardCardPickRow(card = card, onAdd = { onAddSeed(card) })
                    }
                }
                if (uiState.seedCards.isNotEmpty()) {
                    item(key = "seed_picked_header") {
                        Text(
                            stringResource(R.string.deck_seeds_picked_title),
                            style = ty.labelMedium,
                            color = mc.textSecondary,
                            modifier = Modifier.padding(top = spacing.xs),
                        )
                    }
                    items(uiState.seedCards, key = { "seedpick_${it.scryfallId}" }) { card ->
                        WizardCardPickedRow(card = card, onRemove = { onRemoveSeed(card) })
                    }
                }
            }

            // Deck Engine Unification plan (§5 Phase 3.2, RC5) — once at least one seed (or the
            // commander) is picked, rank viable strategies FROM the seeds themselves instead of
            // leaving the user to guess a Direction chip that may not even fit what they just picked.
            val suggestion = uiState.seedStrategySuggestion
            if (suggestion != null) {
                item(key = "seed_strategy_header") {
                    Text(
                        stringResource(R.string.deck_wizard_suggested_strategies_title),
                        style = ty.labelLarge,
                        color = mc.primaryAccent,
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                }
                if (!suggestion.isCoherent) {
                    item(key = "seed_coherence_warning") {
                        SeedCoherenceWarning()
                    }
                }
                if (suggestion.candidates.isEmpty()) {
                    item(key = "seed_strategy_empty") {
                        Text(
                            stringResource(R.string.deck_wizard_suggested_strategies_empty),
                            style = ty.bodySmall,
                            color = mc.textSecondary,
                        )
                    }
                } else {
                    item(key = "seed_strategy_chips") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(spacing.xs),
                        ) {
                            suggestion.candidates.forEach { candidate ->
                                val selected = candidate.profile.archetype == uiState.selectedArchetype &&
                                    candidate.profile.themes.firstOrNull() == uiState.selectedDirectionTheme &&
                                    candidate.profile.tribe == uiState.selectedTribeKey
                                DirectionChip(
                                    label = candidate.label,
                                    selected = selected,
                                    onClick = { onSelectSeedStrategyCandidate(candidate) },
                                )
                            }
                        }
                    }
                }
            }
        }
        WizardStickyButton(
            label = stringResource(R.string.deck_wizard_next),
            enabled = canProceed,
            onClick = onNext,
        )
    }
}

/** Shown when [com.mmg.manahub.feature.decks.domain.usecase.SeedStrategySuggestion.isCoherent] is
 * false — the seeds' own identity-tag/tribe fingerprints barely overlap each other (plan §5 3.2:
 * "incoherent seed set -> inline explanation, never a silent bad build"). The ranked candidates below
 * this banner still render — the user can pick one anyway, or go back and trim their seed picks. */
@Composable
private fun SeedCoherenceWarning() {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(shape = CardShape, color = mc.goldMtg.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.deck_wizard_seed_coherence_warning),
            style = ty.bodySmall,
            color = mc.textSecondary,
            modifier = Modifier.padding(MaterialTheme.spacing.md),
        )
    }
}

@Composable
private fun CommanderPickerSection(
    uiState: DeckWizardUiState,
    onQueryChange: (String) -> Unit,
    onSelect: (Card) -> Unit,
    onClear: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(stringResource(R.string.deck_wizard_commander_section_title), style = ty.labelLarge, color = mc.primaryAccent)

        val commander = uiState.selectedCommander
        if (commander != null) {
            WizardHeroRow(card = commander, onRemove = onClear)
            return@Column
        }

        val candidates = uiState.collectionProfile?.commanderCandidates.orEmpty()
        if (candidates.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                items(candidates, key = { "cand_${it.card.scryfallId}" }) { candidate: OwnedCommanderCandidate ->
                    CommanderCandidateCard(candidate = candidate, onClick = { onSelect(candidate.card) })
                }
            }
        }

        OutlinedTextField(
            value = uiState.commanderQuery,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.deck_wizard_commander_search_hint), style = ty.bodyMedium, color = mc.textDisabled) },
            leadingIcon = {
                if (uiState.isSearchingCommander) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = mc.primaryAccent, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Search, contentDescription = null, tint = mc.textSecondary)
                }
            },
            trailingIcon = if (uiState.commanderQuery.isNotEmpty()) {
                { IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_close), tint = mc.textSecondary) } }
            } else null,
            shape = CardShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = mc.primaryAccent,
                unfocusedBorderColor = mc.surfaceVariant,
                focusedTextColor = mc.textPrimary,
                unfocusedTextColor = mc.textPrimary,
                cursorColor = mc.primaryAccent,
            ),
        )
        if (uiState.commanderQuery.trim().length >= 2 && uiState.commanderSearchResults.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                // L2 (design review): explicit key for stability across recomposition, matching
                // the sibling seed-search-results list's LazyColumn `key = {...}` convention.
                uiState.commanderSearchResults.take(8).forEach { card ->
                    key(card.scryfallId) {
                        WizardCardPickRow(card = card, onAdd = { onSelect(card) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CommanderCandidateCard(candidate: OwnedCommanderCandidate, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(onClick = onClick, shape = CardShape, color = mc.surface, modifier = Modifier.width(120.dp)) {
        Column(Modifier.padding(MaterialTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
            AsyncImage(
                model = candidate.card.imageArtCrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(72.dp).clip(ChipShape),
            )
            CardName(name = candidate.card.name, style = ty.labelMedium, color = mc.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Deck Engine Unification plan (D2): renders one Direction chip per STRATEGY tag the collection
 * leans into ([signal.tag][com.mmg.manahub.feature.decks.domain.template.CollectionStrategySignal
 * .tag]) that resolves onto the unified taxonomy via [DeckIdentitySeedTags.archetypeForTag] or
 * `.themeForTag`. A tag with STILL no [com.mmg.manahub.feature.decks.domain.engine.ArchetypeId]/
 * [ThemeId] home under the new taxonomy is filtered out of the rendered list entirely (never a
 * "dead" -- selectable-but-inert -- chip); this is the taxonomy-era continuation of the Wizard
 * Quality Campaign B1 "every rendered chip must do something when tapped" fix, NOT a regression of
 * it -- the pre-unification fix kept a chip alive via a raw-[CardTag] pin fallback, but D2
 * deliberately closed that fallback slot (the taxonomy is now `StrategyProfile`-only, with no
 * "arbitrary tag" pin anywhere left to carry it). See [DeckIdentitySeedTags]'s `THEME_TAGS` KDoc
 * for how the 4 originally-called-out dead tags (`plus_counters`/`death_triggers`/`spellslinger`/
 * `etb`) all resolve under the new mapping.
 */
@Composable
private fun CollectionLeanSection(
    uiState: DeckWizardUiState,
    onSelectDirectionTag: (CardTag) -> Unit,
    onSelectTribe: (CollectionTribeSignal) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(stringResource(R.string.deck_wizard_direction_leans_title), style = ty.labelLarge, color = mc.primaryAccent)

        if (uiState.isLoadingProfile) {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = mc.primaryAccent, modifier = Modifier.size(24.dp))
            }
            return@Column
        }

        val profile = uiState.collectionProfile
        if (profile == null || (profile.colorShares.isEmpty() && profile.dominantStrategies.isEmpty() && profile.dominantTribes.isEmpty())) {
            Text(
                stringResource(R.string.deck_wizard_direction_leans_empty),
                style = ty.bodySmall,
                color = mc.textSecondary,
            )
            return@Column
        }

        profile.colorShares.take(3).forEach { share ->
            ColorLeanRow(color = share.color, sharePercent = (share.share * 100).roundToInt())
        }

        if (profile.dominantStrategies.isNotEmpty() || profile.dominantTribes.isNotEmpty()) {
            Spacer(Modifier.height(spacing.xxs))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                // D2: only render chips whose tag resolves onto the unified taxonomy -- see this
                // composable's class KDoc.
                val archetypeByTag = profile.dominantStrategies.associate { it.tag to DeckIdentitySeedTags.archetypeForTag(it.tag) }
                val themeByTag = profile.dominantStrategies.associate { it.tag to DeckIdentitySeedTags.themeForTag(it.tag) }
                profile.dominantStrategies.forEach { signal ->
                    val archetype = archetypeByTag[signal.tag]
                    val theme = themeByTag[signal.tag]
                    if (archetype == null && theme == null) return@forEach
                    val selected = when {
                        archetype != null -> archetype == uiState.selectedArchetype
                        else -> theme == uiState.selectedDirectionTheme
                    }
                    DirectionChip(
                        label = stringResource(R.string.deck_wizard_direction_chip_strategy, signal.tag.displayLabel, signal.copies),
                        selected = selected,
                        onClick = { onSelectDirectionTag(signal.tag) },
                    )
                }
                profile.dominantTribes.forEach { tribe: CollectionTribeSignal ->
                    // Edge-case audit (Wizard Quality Campaign final gate, 2026-07-19): compare by
                    // the stable tribeKey, not the pluralized displayLabel -- the label is
                    // display-only, the key is what actually threads into StrategyProfile.tribe.
                    val selected = tribe.tribeKey == uiState.selectedTribeKey
                    DirectionChip(
                        label = stringResource(R.string.deck_wizard_direction_chip_tribe, tribe.copies, tribe.displayLabel),
                        selected = selected,
                        onClick = { onSelectTribe(tribe) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorLeanRow(color: ManaColor, sharePercent: Int) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        ManaSymbolImage(token = color.symbol, size = 20.dp)
        Text(
            text = stringResource(R.string.deck_wizard_direction_color_lean, color.displayName, sharePercent),
            style = ty.bodySmall,
            color = mc.textSecondary,
        )
    }
}

@Composable
internal fun DirectionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(
        onClick = onClick,
        shape = ChipShape,
        color = if (selected) mc.primaryAccent.copy(alpha = 0.18f) else mc.surface,
        border = if (selected) BorderStroke(1.dp, mc.primaryAccent) else BorderStroke(0.5.dp, mc.surfaceVariant),
    ) {
        Text(
            text = label,
            style = ty.labelMedium,
            color = if (selected) mc.primaryAccent else mc.textSecondary,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(horizontal = MaterialTheme.spacing.sm),
        )
    }
}

@Composable
private fun SeedPickerToggleRow(expanded: Boolean, onToggle: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.deck_wizard_seed_picker_toggle), style = ty.labelLarge, color = mc.primaryAccent, modifier = Modifier.weight(1f))
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = mc.primaryAccent,
        )
    }
}

@Composable
private fun SeedSearchInline(query: String, isSearching: Boolean, onQueryChange: (String) -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.deck_seeds_search_hint), style = ty.bodyMedium, color = mc.textDisabled) },
        leadingIcon = {
            if (isSearching) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = mc.primaryAccent, strokeWidth = 2.dp)
            else Icon(Icons.Default.Search, contentDescription = null, tint = mc.textSecondary)
        },
        shape = CardShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = mc.primaryAccent,
            unfocusedBorderColor = mc.surfaceVariant,
            focusedTextColor = mc.textPrimary,
            unfocusedTextColor = mc.textPrimary,
            cursorColor = mc.primaryAccent,
        ),
    )
}

@Composable
private fun WizardCardPickRow(card: Card, onAdd: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(shape = CardShape, color = mc.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            AsyncImage(
                model = card.imageArtCrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 52.dp, height = 38.dp).clip(ChipShape),
            )
            Column(Modifier.weight(1f)) {
                CardName(name = card.name, style = ty.bodyMedium, color = mc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(card.setName, style = ty.labelSmall, color = mc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onAdd, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.deck_seeds_add_seed), tint = mc.primaryAccent)
            }
        }
    }
}

@Composable
private fun WizardCardPickedRow(card: Card, onRemove: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(shape = CardShape, color = mc.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            AsyncImage(
                model = card.imageArtCrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 52.dp, height = 38.dp).clip(ChipShape),
            )
            CardName(
                name = card.name, style = ty.bodyMedium, color = mc.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.deck_seeds_remove_seed), tint = mc.textSecondary)
            }
        }
    }
}

@Composable
private fun WizardHeroRow(card: Card, onRemove: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(shape = CardShape, color = mc.backgroundSecondary, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            AsyncImage(
                model = card.imageArtCrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 64.dp, height = 46.dp).clip(ChipShape),
            )
            Column(Modifier.weight(1f)) {
                CardName(name = card.name, style = ty.titleMedium, color = mc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(card.typeLine, style = ty.labelSmall, color = mc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.deck_wizard_change_commander), tint = mc.textSecondary)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Step 3 — Identity
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun IdentityStepContent(
    uiState: DeckWizardUiState,
    onToggleColor: (ManaColor) -> Unit,
    onSelectTheme: (String?) -> Unit,
    onNext: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val isCommanderFormat = uiState.selectedFormat == DeckFormat.COMMANDER

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
            Column {
                Text(stringResource(R.string.deck_wizard_identity_title), style = ty.titleLarge, color = mc.textPrimary)
                Text(
                    if (isCommanderFormat) stringResource(R.string.deck_wizard_identity_subtitle_commander)
                    else stringResource(R.string.deck_wizard_identity_subtitle_casual),
                    style = ty.bodyMedium,
                    color = mc.textSecondary,
                    modifier = Modifier.padding(top = spacing.xxs),
                )
            }

            Surface(shape = CardShape, color = mc.backgroundSecondary, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(stringResource(R.string.deck_seeds_identity_colors).uppercase(), style = ty.labelMedium, color = mc.primaryAccent)
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        ManaColor.entries.filter { it != ManaColor.C }.forEach { color ->
                            ColorToggleChip(
                                color = color,
                                selected = color in uiState.colorIdentity,
                                readOnly = isCommanderFormat,
                                onClick = { onToggleColor(color) },
                            )
                        }
                    }
                    if (uiState.colorIdentity.isEmpty()) {
                        Text(stringResource(R.string.deck_seeds_identity_colorless), style = ty.bodySmall, color = mc.textSecondary)
                    }
                }
            }

            if (uiState.showColorDisciplineHint) {
                Surface(shape = CardShape, color = mc.goldMtg.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.deck_wizard_color_discipline_hint),
                        style = ty.bodySmall,
                        color = mc.textSecondary,
                        modifier = Modifier.padding(spacing.md),
                    )
                }
            }

            if (uiState.isLoadingThemeTags) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = mc.primaryAccent, modifier = Modifier.size(20.dp))
                }
            } else {
                // D2: only offer EDHREC theme strings that resolve onto a real ThemeId -- an
                // unresolvable free-form aggregate tag has nowhere to bind under the unified
                // taxonomy (mirrors CollectionLeanSection's own dead-chip filter).
                val resolvableThemeTags = remember(uiState.availableThemeTags) {
                    uiState.availableThemeTags.filter { ThemeId.fromDisplayName(it) != null }
                }
                if (resolvableThemeTags.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(stringResource(R.string.deck_wizard_theme_picker_title), style = ty.labelLarge, color = mc.primaryAccent)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(spacing.xs),
                        ) {
                            resolvableThemeTags.forEach { theme ->
                                DirectionChip(label = theme, selected = theme == uiState.selectedThemeHint, onClick = { onSelectTheme(theme) })
                            }
                        }
                    }
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

@Composable
internal fun ColorToggleChip(color: ManaColor, selected: Boolean, readOnly: Boolean, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val border = if (selected) BorderStroke(1.5.dp, mc.primaryAccent) else BorderStroke(0.5.dp, mc.surfaceVariant)
    val background = if (selected) mc.primaryAccent.copy(alpha = 0.18f) else mc.surface
    val content: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            ManaSymbolImage(token = color.symbol, size = 24.dp)
        }
    }
    // Read-only (Commander -- colors are derived from the commander, not user-editable): a plain
    // Surface with no `onClick` overload so it never shows an interactive ripple. M3 (design
    // review): a screen reader gets no other signal these are fixed, not toggleable -- an explicit
    // contentDescription distinguishes the two variants for TalkBack users.
    if (readOnly) {
        val fixedColorDescription = stringResource(R.string.deck_wizard_color_fixed_by_commander, color.displayName)
        Surface(
            shape = CircleShape,
            color = background,
            border = border,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = fixedColorDescription },
            content = content,
        )
    } else {
        Surface(onClick = onClick, shape = CircleShape, color = background, border = border, modifier = Modifier.size(48.dp), content = content)
    }
}
