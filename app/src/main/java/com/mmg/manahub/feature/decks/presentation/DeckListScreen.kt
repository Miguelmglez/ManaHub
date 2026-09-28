package com.mmg.manahub.feature.decks.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.DeckItem
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.HexGridBackground
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.rememberFabVisibility
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.decks.presentation.components.DeckImportSheet
import org.jetbrains.compose.resources.painterResource
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    onDeckClick:       (deckId: String) -> Unit,
    onPlaytestClick:   (deckId: String) -> Unit = {},
    onBrowseCommunityDecks: () -> Unit = {},
    viewModel:         DeckViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors

    var showCreateSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DeckListEvent.NavigateToDeck -> onDeckClick(event.deckId)
            }
        }
    }

    val listState = rememberLazyListState()
    val isFabVisible = rememberFabVisibility(listState)

    Box(modifier = Modifier.fillMaxSize().background(mc.background)) {
        HexGridBackground(modifier = Modifier.fillMaxSize(), color = mc.primaryAccent.copy(alpha = 0.05f))

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            floatingActionButton = {
                AnimatedVisibility(
                    visible = uiState.decks.isNotEmpty() && isFabVisible,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    FloatingActionButton(
                        onClick = { showCreateSheet = true },
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
                    uiState.isLoading -> MagicLoadingSpinner(
                        modifier = Modifier.align(Alignment.Center),
                    )

                    uiState.decks.isEmpty() -> EmptyDecksState(
                        onCreateClick = { showCreateSheet = true },
                        onBrowseCommunityDecks = onBrowseCommunityDecks,
                        modifier      = Modifier.align(Alignment.Center),
                    )

                    else -> {
                        LazyColumn(
                            state               = listState,
                            modifier            = Modifier.fillMaxSize(),
                            contentPadding      = PaddingValues(top = 8.dp, bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            items(uiState.decks, key = { it.id }) { deck ->
                                DeckItem(
                                    deck             = deck,
                                    onClick          = { onDeckClick(deck.id) },
                                    onDelete         = { viewModel.deleteDeck(deck.id) },
                                    onPlaytest       = if (deck.cardCount > 0) ({ onPlaytestClick(deck.id) }) else null,
                                    cardBackPainter  = painterResource(Res.drawable.mtg_card_back),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        com.mmg.manahub.feature.decks.presentation.components.DeckCreationSheet(
            onDismiss = { showCreateSheet = false },
            onCreate = { name, format ->
                viewModel.createDeck(name, format)
            }
        )
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
    onBrowseCommunityDecks: () -> Unit,
    modifier:      Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(
            title       = stringResource(R.string.decklist_empty_title),
            subtitle    = stringResource(R.string.decklist_empty_subtitle),
            icon        = Icons.Default.AutoAwesome,
            actionLabel = stringResource(R.string.decklist_empty_action),
            onAction    = onCreateClick,
            modifier    = Modifier,
        )

        TextButton(
            onClick  = onBrowseCommunityDecks,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        ) {
            Text(
                text  = "Browse Community Decks",
                style = ty.labelLarge,
                color = mc.textSecondary,
            )
        }
    }
}
