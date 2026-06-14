package com.mmg.manahub.feature.decks.presentation.improvement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.usecase.AddSuggestion
import com.mmg.manahub.feature.decks.domain.usecase.BudgetConstraints
import com.mmg.manahub.feature.decks.domain.usecase.DeckHealth
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.presentation.improvement.components.AddSuggestionRow
import com.mmg.manahub.feature.decks.presentation.improvement.components.BudgetFilterBar
import com.mmg.manahub.feature.decks.presentation.improvement.components.CutSuggestionRow
import com.mmg.manahub.feature.decks.presentation.improvement.components.HealthScoreRing
import com.mmg.manahub.feature.decks.presentation.improvement.components.ManaCurveChart
import com.mmg.manahub.feature.decks.presentation.improvement.components.RoleCoverageRow
import com.mmg.manahub.feature.decks.presentation.improvement.components.WarningChip
import com.mmg.manahub.feature.decks.presentation.improvement.components.key
import com.mmg.manahub.feature.decks.presentation.improvement.components.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckImprovementScreen(
    onBack: () -> Unit,
    viewModel: DeckImprovementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val toastState = rememberMagicToastState()

    val cutMessage = stringResource(R.string.deck_doctor_toast_cut)
    val addMessage = stringResource(R.string.deck_doctor_toast_add)
    val externalFailedMessage = stringResource(R.string.deck_doctor_toast_external_failed)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DeckImprovementEvent.CardCut ->
                    toastState.show(String.format(cutMessage, event.cardName), MagicToastType.SUCCESS)
                is DeckImprovementEvent.CardAdded ->
                    toastState.show(String.format(addMessage, event.cardName), MagicToastType.SUCCESS)
                DeckImprovementEvent.ExternalPoolFailed ->
                    toastState.show(externalFailedMessage, MagicToastType.ERROR)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor      = mc.background,
            contentWindowInsets = WindowInsets.statusBars,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.deck_improve_title), style = ty.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = mc.textSecondary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.backgroundSecondary)
                )
            }
        ) { padding ->
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = mc.primaryAccent)
                    }
                }
                uiState.error != null -> {
                    FullErrorState(
                        message = uiState.error ?: stringResource(R.string.deck_health_error),
                        modifier = Modifier.padding(padding),
                    )
                }
                else -> {
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        DeckDoctorTabRow(
                            selected = uiState.selectedTab,
                            onSelect = viewModel::onTabSelected,
                        )
                        when (uiState.selectedTab) {
                            DeckDoctorTab.HEALTH -> HealthTab(health = uiState.health)
                            DeckDoctorTab.CUT -> CutTab(
                                cuts = uiState.cuts,
                                onCut = { fit -> viewModel.onCut(fit.card.scryfallId, fit.card.name) },
                            )
                            DeckDoctorTab.ADD -> AddTab(
                                adds = uiState.adds,
                                budget = uiState.budget,
                                totalCostEur = uiState.addsTotalCostEur,
                                cardsToBuy = uiState.addsCardsToBuy,
                                isAddsLoading = uiState.isAddsLoading,
                                onBudgetChanged = viewModel::onBudgetChanged,
                                onAdd = { suggestion ->
                                    viewModel.onAdd(suggestion.fit.card.scryfallId, suggestion.fit.card.name)
                                },
                            )
                        }
                    }
                }
            }
        }
        MagicToastHost(toastState)
    }
}

@Composable
private fun DeckDoctorTabRow(
    selected: DeckDoctorTab,
    onSelect: (DeckDoctorTab) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val tabs = DeckDoctorTab.entries

    TabRow(
        selectedTabIndex = tabs.indexOf(selected),
        containerColor = mc.backgroundSecondary,
        contentColor = mc.primaryAccent,
        indicator = { positions ->
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(positions[tabs.indexOf(selected)]),
                color = mc.primaryAccent,
            )
        },
        modifier = Modifier.fillMaxWidth().selectableGroup(),
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selected
            Tab(
                selected = isSelected,
                onClick = { onSelect(tab) },
                selectedContentColor = mc.primaryAccent,
                unselectedContentColor = mc.textSecondary,
                text = {
                    Text(
                        text = stringResource(
                            when (tab) {
                                DeckDoctorTab.HEALTH -> R.string.deck_doctor_tab_health
                                DeckDoctorTab.CUT -> R.string.deck_doctor_tab_cut
                                DeckDoctorTab.ADD -> R.string.deck_doctor_tab_add
                            }
                        ),
                        style = ty.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
            )
        }
    }
}

@Composable
private fun HealthTab(health: DeckHealth?) {
    val mc = MaterialTheme.magicColors

    if (health == null) {
        EmptyState(
            title = stringResource(R.string.deck_health_empty_title),
            icon = Icons.Default.AutoFixHigh,
        )
        return
    }

    val evaluation = health.evaluation

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(MaterialTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xl),
    ) {
        // Score ring
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                HealthScoreRing(score = evaluation.healthScore)
            }
        }

        // Role coverage
        item { HealthSectionHeader(stringResource(R.string.deck_health_section_roles), mc.primaryAccent) }
        items(evaluation.roleCoverage, key = { it.role.name }) { coverage ->
            RoleCoverageRow(coverage = coverage)
        }

        // Mana curve (non-lands only)
        item { HealthSectionHeader(stringResource(R.string.deck_health_section_curve), mc.primaryAccent) }
        item {
            Surface(color = mc.surface, shape = CardShape, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(MaterialTheme.spacing.md)) {
                    Text(
                        text = stringResource(R.string.deck_health_avg_cmc, String.format(java.util.Locale.US, "%.1f", evaluation.avgCmc)),
                        style = MaterialTheme.magicTypography.bodySmall,
                        color = mc.textSecondary,
                    )
                    Spacer(Modifier.padding(top = MaterialTheme.spacing.sm))
                    ManaCurveChart(histogram = evaluation.curveHistogram)
                }
            }
        }

        // Warnings
        if (evaluation.warnings.isNotEmpty()) {
            item { HealthSectionHeader(stringResource(R.string.deck_health_section_warnings), mc.lifeNegative) }
            items(evaluation.warnings, key = { it.key }) { warning ->
                WarningChip(text = warning.label())
            }
        }
    }
}

@Composable
private fun CutTab(
    cuts: List<CardFit>,
    onCut: (CardFit) -> Unit,
) {
    if (cuts.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.deck_doctor_cut_empty_title),
            icon = Icons.Default.AutoFixHigh,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(MaterialTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        item { SuggestionsCaption(stringResource(R.string.deck_doctor_cut_caption)) }
        items(cuts, key = { it.card.scryfallId }) { fit ->
            CutSuggestionRow(fit = fit, onCut = { onCut(fit) })
        }
    }
}

@Composable
private fun AddTab(
    adds: List<AddSuggestion>,
    budget: BudgetConstraints,
    totalCostEur: Double,
    cardsToBuy: Int,
    isAddsLoading: Boolean,
    onBudgetChanged: (BudgetConstraints) -> Unit,
    onAdd: (AddSuggestion) -> Unit,
) {
    val mc = MaterialTheme.magicColors

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(MaterialTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        // Budget filter bar is always present so the user can constrain even an empty result set.
        item("budget_bar") {
            BudgetFilterBar(budget = budget, onBudgetChanged = onBudgetChanged)
        }
        item("budget_summary") {
            BudgetSummary(totalCostEur = totalCostEur, cardsToBuy = cardsToBuy)
        }

        when {
            isAddsLoading -> item("adds_loading") {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = mc.primaryAccent)
                }
            }
            adds.isEmpty() -> item("adds_empty") {
                EmptyState(
                    title = stringResource(R.string.deck_doctor_add_empty_title),
                    subtitle = stringResource(R.string.deck_doctor_add_empty_subtitle),
                    icon = Icons.Default.AutoFixHigh,
                )
            }
            else -> {
                item("adds_caption") { SuggestionsCaption(stringResource(R.string.deck_doctor_add_caption)) }
                items(adds, key = { it.fit.card.scryfallId }) { suggestion ->
                    AddSuggestionRow(suggestion = suggestion, onAdd = { onAdd(suggestion) })
                }
            }
        }
    }
}

/** "To buy: X € of Y cards" header (or an all-owned hint when nothing needs buying). */
@Composable
private fun BudgetSummary(totalCostEur: Double, cardsToBuy: Int) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val text = if (cardsToBuy == 0) {
        stringResource(R.string.deck_doctor_budget_all_owned)
    } else {
        stringResource(
            R.string.deck_doctor_budget_to_buy,
            String.format(java.util.Locale.US, "%.2f", totalCostEur),
            cardsToBuy,
        )
    }
    Text(
        text = text,
        style = ty.bodySmall,
        color = mc.textSecondary,
        modifier = Modifier.padding(vertical = MaterialTheme.spacing.xs),
    )
}

@Composable
private fun SuggestionsCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.magicTypography.bodySmall,
        color = MaterialTheme.magicColors.textSecondary,
        modifier = Modifier.padding(bottom = MaterialTheme.spacing.sm),
    )
}

@Composable
private fun HealthSectionHeader(text: String, color: Color) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.magicTypography.labelMedium,
        color = color,
        fontWeight = FontWeight.Bold,
    )
}
