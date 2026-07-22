package com.mmg.manahub.feature.decks.presentation.wizard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.template.BuildStage
import com.mmg.manahub.feature.decks.domain.template.CategoryFill
import com.mmg.manahub.feature.decks.domain.template.DeckGap
import com.mmg.manahub.feature.decks.domain.template.TemplateBuildResult
import com.mmg.manahub.feature.decks.domain.template.TemplateCardSuggestion
import com.mmg.manahub.feature.decks.domain.template.TemplateSource
import com.mmg.manahub.feature.decks.presentation.components.SuggestionGrouping

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

    Column(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(spacing.lg),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = mc.primaryAccent, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(spacing.lg))
            Text(
                text = uiState.buildStage?.label() ?: stringResource(R.string.deck_wizard_stage_validating),
                style = ty.titleMedium,
                color = mc.textPrimary,
            )
            Spacer(Modifier.height(spacing.lg))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                uiState.completedStages.forEach { stage ->
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
private fun BuildStage.label(): String = stringResource(
    when (this) {
        BuildStage.VALIDATING -> R.string.deck_wizard_stage_validating
        BuildStage.ANALYZING_COLLECTION -> R.string.deck_wizard_stage_analyzing_collection
        BuildStage.FETCHING_COMMUNITY -> R.string.deck_wizard_stage_fetching_community
        BuildStage.MAPPING_CATEGORIES -> R.string.deck_wizard_stage_mapping_categories
        BuildStage.FILLING_FROM_COLLECTION -> R.string.deck_wizard_stage_filling_from_collection
        BuildStage.RESOLVING_GAPS -> R.string.deck_wizard_stage_resolving_gaps
        BuildStage.TOP_UP_FROM_COLLECTION -> R.string.deck_wizard_stage_top_up_from_collection
        BuildStage.FILLING_LANDS -> R.string.deck_wizard_stage_filling_lands
        BuildStage.DONE -> R.string.deck_wizard_stage_done
    }
)

// ═══════════════════════════════════════════════════════════════════════════════
//  Result
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun ResultContent(
    uiState: DeckWizardUiState,
    onAddSuggestion: (categoryId: String, TemplateCardSuggestion) -> Unit,
    onCardClick: (String) -> Unit,
    onOpenDeckStudio: () -> Unit,
    onBack: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val result = uiState.buildResult

    if (result == null) {
        // M2 (design review): a defensive dead end otherwise -- give the user an explicit way
        // forward instead of relying on an undiscoverable system-back gesture.
        EmptyState(
            title = stringResource(R.string.deck_wizard_build_error_title),
            subtitle = stringResource(R.string.deck_wizard_build_error),
            icon = Icons.Default.AutoAwesome,
            actionLabel = stringResource(R.string.action_back),
            onAction = onBack,
        )
        return
    }

    // H2 (design review): grouping/sorting is O(n) to O(n*m) work -- remember it keyed on the
    // result so an unrelated recomposition (toast state, animation tick) never re-runs it. ALL
    // three must be computed HERE, in the composable function's own body, not inside the
    // LazyColumn content lambda below (LazyListScope's content is a plain, non-@Composable
    // lambda -- `remember` there fails to compile, the same class of bug as
    // feedback_lazylistscope_content_not_composable).
    val ownedByCategory = remember(result) { SuggestionGrouping.groupDeckEntries(result.deckCards) }
    val ownedById = remember(ownedByCategory) { ownedByCategory.associate { it.first.id to it.second } }
    // Per-category, two strictly separated subsections (D3/D8) — an id present in EITHER the
    // owned grouping or the community-suggestions grouping gets its own block; never interleaved
    // within a category, and a category with only one side simply omits the other.
    val categoryIds = remember(ownedByCategory, result.communitySuggestions) {
        (ownedByCategory.map { it.first } + result.communitySuggestions.map { it.category })
            .distinctBy { it.id }
            .sortedByDescending { category ->
                (ownedById[category.id]?.size ?: 0) +
                    (result.communitySuggestions.firstOrNull { it.category.id == category.id }?.suggestions?.size ?: 0)
            }
    }

    // C1 (design review): the sticky CTA is a real Column sibling, not a Box overlay with a
    // guessed bottom-padding reservation (see FormatStepContent for the full rationale).
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, top = spacing.md, bottom = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item(key = "source_badge") {
                ResultSourceBadge(source = result.templateSource, gamePlan = result.gamePlan)
            }

            if (result.colorConsistencyWarning) {
                item(key = "color_warning") {
                    Surface(shape = CardShape, color = mc.goldMtg.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.deck_wizard_color_discipline_hint),
                            style = ty.bodySmall,
                            color = mc.goldMtg,
                            modifier = Modifier.padding(spacing.md),
                        )
                    }
                }
            }

            // Deck Engine Unification (D3): a structured, honest gap declaration -- the owned
            // collection had nothing left clearing the fit floor for these slots, so the build
            // reports the gap instead of placing a weak filler card. One line per gap ("missing 3
            // Ramp in {G}").
            if (result.gaps.isNotEmpty()) {
                item(key = "gaps_header") {
                    Text(stringResource(R.string.deck_wizard_gaps_title), style = ty.labelLarge, color = mc.goldMtg)
                }
                items(result.gaps, key = { "gap_${it.categoryId}" }) { gap ->
                    GapWarningRow(gap)
                }
            }

            if (result.report.isNotEmpty()) {
                item(key = "report_header") {
                    Text(stringResource(R.string.deck_wizard_result_report_title), style = ty.labelLarge, color = mc.primaryAccent)
                }
                item(key = "report_grid") {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        result.report.forEach { fill: CategoryFill -> ReportChip(fill) }
                    }
                }
            }

            categoryIds.forEach { category ->
                item(key = "cat_header_${category.id}") {
                    Text(category.displayLabel.uppercase(), style = ty.labelLarge, color = mc.textSecondary)
                }
                val owned = ownedById[category.id].orEmpty()
                if (owned.isNotEmpty()) {
                    item(key = "cat_owned_label_${category.id}") {
                        Text(stringResource(R.string.deck_wizard_result_from_collection), style = ty.labelMedium, color = mc.lifePositive)
                    }
                    items(owned, key = { "owned_${category.id}_${it.card.scryfallId}" }) { entry ->
                        WizardOwnedCardRow(card = entry.card, quantity = entry.quantity, onClick = { onCardClick(entry.card.scryfallId) })
                    }
                }
                val community = result.communitySuggestions.firstOrNull { it.category.id == category.id }?.suggestions.orEmpty()
                if (community.isNotEmpty()) {
                    item(key = "cat_community_label_${category.id}") {
                        Text(stringResource(R.string.deck_wizard_result_community_picks), style = ty.labelMedium, color = mc.secondaryAccent)
                    }
                    items(community, key = { "comm_${category.id}_${it.card.scryfallId}" }) { suggestion ->
                        WizardCommunitySuggestionRow(
                            suggestion = suggestion,
                            onAdd = { onAddSuggestion(category.id, suggestion) },
                            onCardClick = { onCardClick(suggestion.card.scryfallId) },
                        )
                    }
                }
            }
        }

        WizardStickyButton(
            label = stringResource(R.string.deck_wizard_open_studio),
            enabled = true,
            onClick = onOpenDeckStudio,
        )
    }
}

@Composable
private fun ResultSourceBadge(source: TemplateSource, gamePlan: String?) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(shape = CardShape, color = mc.backgroundSecondary, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Surface(shape = ChipShape, color = mc.primaryAccent.copy(alpha = 0.16f)) {
                Text(
                    text = if (source == TemplateSource.COMMUNITY) {
                        stringResource(R.string.deck_wizard_source_community)
                    } else {
                        stringResource(R.string.deck_wizard_source_synthetic)
                    },
                    style = ty.labelSmall,
                    color = mc.primaryAccent,
                    modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
                )
            }
            if (gamePlan != null) {
                Text(gamePlan, style = ty.bodyMedium, color = mc.textPrimary, modifier = Modifier.padding(top = spacing.xs))
            }
        }
    }
}

/** Deck Engine Unification (D3): one structured gap declaration -- "Missing N {label} in {colors}"
 * (colorless decks drop the "in {colors}" clause entirely; there is nothing to name). */
@Composable
private fun GapWarningRow(gap: DeckGap) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val colorLabel = gap.colors.joinToString("") { it.symbol }
    val text = if (colorLabel.isEmpty()) {
        stringResource(R.string.deck_wizard_gap_row_no_colors, gap.missingCount, gap.categoryLabel)
    } else {
        stringResource(R.string.deck_wizard_gap_row_with_colors, gap.missingCount, gap.categoryLabel, colorLabel)
    }
    Surface(shape = CardShape, color = mc.goldMtg.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
        Text(text = text, style = ty.bodySmall, color = mc.goldMtg, modifier = Modifier.padding(MaterialTheme.spacing.md))
    }
}

@Composable
private fun ReportChip(fill: CategoryFill) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val complete = fill.filled >= fill.target && fill.target > 0
    Surface(
        shape = ChipShape,
        color = if (complete) mc.lifePositive.copy(alpha = 0.14f) else mc.surface,
        border = BorderStroke(0.5.dp, mc.surfaceVariant),
    ) {
        Text(
            text = stringResource(R.string.deck_wizard_report_chip, fill.category.displayLabel, fill.filled, fill.target),
            style = ty.labelMedium,
            color = if (complete) mc.lifePositive else mc.textSecondary,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.sm, vertical = MaterialTheme.spacing.xs),
        )
    }
}

/** View-only row for an OWNED card already written into the deck — no action button (it is
 * already in the deck; tapping opens the full Card Detail screen). */
@Composable
private fun WizardOwnedCardRow(card: Card, quantity: Int, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(onClick = onClick, shape = CardShape, color = mc.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            AsyncImage(
                model = card.imageArtCrop ?: card.imageNormal,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 44.dp, height = 62.dp).clip(CardShape),
            )
            CardName(
                name = card.name, style = ty.titleMedium, color = mc.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (quantity > 1) {
                Text(text = "×$quantity", style = ty.labelMedium, color = mc.textSecondary)
            }
        }
    }
}

/** View-only community pick (D8) with an explicit "Add" button — never auto-added. Mirrors
 * [com.mmg.manahub.feature.decks.presentation.components.CommunityAddSuggestionRow]'s visual
 * language for a [TemplateCardSuggestion] rather than a Motor B [com.mmg.manahub.feature.decks
 * .domain.usecase.CommunityAddSuggestion] (different shape: weight/suggestedCopies, no
 * inclusionPct/synergy breakdown). */
@Composable
private fun WizardCommunitySuggestionRow(
    suggestion: TemplateCardSuggestion,
    onAdd: () -> Unit,
    onCardClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val card = suggestion.card
    Surface(onClick = onCardClick, shape = CardShape, color = mc.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            AsyncImage(
                model = card.imageArtCrop ?: card.imageNormal,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 44.dp, height = 62.dp).clip(CardShape),
            )
            Column(Modifier.weight(1f)) {
                CardName(name = card.name, style = ty.titleMedium, color = mc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (suggestion.suggestedCopies > 1) {
                    Text(
                        text = stringResource(R.string.deck_wizard_suggested_copies, suggestion.suggestedCopies),
                        style = ty.labelSmall,
                        color = mc.textSecondary,
                    )
                }
            }
            IconButton(onClick = onAdd, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.deck_doctor_add_cd, card.name), tint = mc.lifePositive)
            }
        }
    }
}
