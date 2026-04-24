package com.mmg.manahub.feature.decks.improvement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.decks.engine.AnalysisPoint
import com.mmg.manahub.feature.decks.engine.AnalysisSeverity
import com.mmg.manahub.feature.decks.engine.ImprovementSuggestion
import com.mmg.manahub.feature.decks.engine.SuggestionActionType
import com.mmg.manahub.feature.decks.components.DeckSummaryCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckImprovementScreen(
    onBack: () -> Unit,
    viewModel: DeckImprovementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Scaffold(
        containerColor = mc.background,
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
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = mc.primaryAccent)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    DeckSummaryCard(
                        totalCards = uiState.totalCards,
                        targetCount = uiState.targetCount,
                        manaCurve = uiState.manaCurve,
                        maxInCurve = uiState.maxInCurve,
                        deckCards = uiState.cards,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                uiState.report?.let { report ->
                    if (report.strengths.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.deck_improve_strengths), mc.lifePositive) }
                        items(report.strengths) { AnalysisItem(it) }
                    }

                    if (report.weaknesses.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.deck_improve_weaknesses), mc.lifeNegative) }
                        items(report.weaknesses) { AnalysisItem(it) }
                    }

                    if (report.suggestions.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.deck_improve_suggestions), mc.goldMtg) }
                        items(report.suggestions) { suggestion ->
                            SuggestionItem(
                                suggestion = suggestion,
                                isApplied = suggestion.magicCard.card.scryfallId in uiState.appliedSuggestions,
                                onApply = { viewModel.applySuggestion(suggestion) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, color: Color) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.magicTypography.labelMedium,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun AnalysisItem(point: AnalysisPoint) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val tint = when (point.severity) {
        AnalysisSeverity.INFO -> mc.lifePositive
        AnalysisSeverity.WARNING -> mc.goldMtg
        AnalysisSeverity.ERROR -> mc.lifeNegative
    }

    Surface(
        color = mc.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(tint, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(point.descriptionResId), style = ty.bodyMedium, color = mc.textPrimary)
        }
    }
}

@Composable
private fun SuggestionItem(
    suggestion: ImprovementSuggestion,
    isApplied: Boolean,
    onApply: () -> Unit
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        color = mc.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = when (suggestion.actionType) {
                        SuggestionActionType.ADD_FROM_COLLECTION -> stringResource(R.string.deck_improve_suggestion_add_from_collection, suggestion.magicCard.card.name)
                        SuggestionActionType.SWAP_FROM_SIDEBOARD -> stringResource(R.string.deck_improve_suggestion_swap_from_sideboard, suggestion.magicCard.card.name, suggestion.swapFor?.card?.name ?: "another card")
                    },
                    style = ty.bodyLarge,
                    color = mc.textPrimary
                )
                Text(
                    text = stringResource(suggestion.reasonResId),
                    style = ty.labelSmall,
                    color = mc.textSecondary
                )
            }
            
            if (isApplied) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = mc.lifePositive)
            } else {
                IconButton(onClick = onApply) {
                    val icon = when (suggestion.actionType) {
                        SuggestionActionType.ADD_FROM_COLLECTION -> Icons.Default.Add
                        SuggestionActionType.SWAP_FROM_SIDEBOARD -> Icons.Default.SwapHoriz
                    }
                    Icon(icon, contentDescription = null, tint = mc.primaryAccent)
                }
            }
        }
    }
}


















