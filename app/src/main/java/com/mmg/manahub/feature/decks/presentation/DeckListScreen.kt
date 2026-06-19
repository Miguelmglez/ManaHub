package com.mmg.manahub.feature.decks.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.DeckItem
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.decks.presentation.components.DeckImportSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    onDeckClick:       (deckId: String) -> Unit,
    onCreateDeck:      () -> Unit,
    onPlaytestClick:   (deckId: String) -> Unit = {},
    viewModel:         DeckViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            if (uiState.decks.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onCreateDeck,
                    containerColor = mc.primaryAccent,
                    contentColor = mc.background,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator(
                    color    = mc.primaryAccent,
                    modifier = Modifier.align(Alignment.Center),
                )

                uiState.decks.isEmpty() -> EmptyDecksState(
                    onCreateClick = onCreateDeck,
                    modifier      = Modifier.align(Alignment.Center),
                )

                else -> {
                    LazyColumn(
                        modifier            = Modifier.fillMaxSize(),
                        contentPadding      = PaddingValues(top = 8.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        items(uiState.decks, key = { it.id }) { deck ->
                            DeckItem(
                                deck        = deck,
                                onClick     = { onDeckClick(deck.id) },
                                onDelete    = { viewModel.deleteDeck(deck.id) },
                                onPlaytest  = if (DeckFeatureFlags.PLAYTEST_ENABLED) ({ onPlaytestClick(deck.id) }) else null,
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.showImportSheet) {
        DeckImportSheet(
            isLoading = uiState.isImporting,
            error     = uiState.importError,
            onImport  = viewModel::importDeck,
            onDismiss = viewModel::onDismissImportSheet,
        )
    }

    uiState.error?.let {
        LaunchedEffect(it) { viewModel.onErrorDismissed() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyDecksState(
    onCreateClick: () -> Unit,
    modifier:      Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Column(
        modifier            = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Aesthetic Placeholder for empty state
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            mc.primaryAccent.copy(alpha = 0.2f),
                            mc.background,
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.LibraryBooks,
                contentDescription = null,
                tint               = mc.textDisabled,
                modifier           = Modifier.size(64.dp),
            )
        }
        
        Text(
            stringResource(R.string.decklist_empty_title),
            style = ty.titleMedium,
            color = mc.textPrimary,
        )
        Text(
            stringResource(R.string.decklist_empty_subtitle),
            style = ty.bodyMedium,
            color = mc.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = onCreateClick,
            colors  = ButtonDefaults.outlinedButtonColors(contentColor = mc.primaryAccent),
            border  = BorderStroke(1.dp, mc.primaryAccent),
            shape   = RoundedCornerShape(12.dp)
        ) {
            Text(
                stringResource(R.string.decklist_empty_action),
                style = ty.labelLarge,
            )
        }
    }
}
