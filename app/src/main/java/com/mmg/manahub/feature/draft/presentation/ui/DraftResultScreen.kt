package com.mmg.manahub.feature.draft.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.draft.domain.model.DraftCard
import com.mmg.manahub.feature.draft.domain.model.DraftState
import com.mmg.manahub.feature.draft.presentation.viewmodel.DraftSimUiState
import com.mmg.manahub.feature.draft.presentation.viewmodel.DraftSimViewModel

/**
 * Result screen shown after all packs are drafted. Previews the drafted pool grouped by
 * mana value, then lets the user save the auto-built deck. On success a toast is shown and
 * [onDeckSaved] pops back to the draft home.
 */
@Composable
fun DraftResultScreen(
    onDeckSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: DraftSimViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val toastState = rememberMagicToastState()
    val savedMessage = stringResource(R.string.draft_sim_deck_saved)

    LaunchedEffect(state) {
        if (state is DraftSimUiState.Complete) {
            toastState.show(savedMessage, MagicToastType.SUCCESS)
            onDeckSaved()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ThemeBackground(modifier = Modifier.fillMaxSize())

        when (val s = state) {
            is DraftSimUiState.Building -> ResultContent(
                state = s.state,
                onSave = viewModel::onCompleteDraft,
                onBack = onBack,
                errorMessage = null,
            )

            is DraftSimUiState.Error -> ResultContent(
                // Keep the deck visible (if any) but surface the save error inline.
                state = null,
                onSave = viewModel::onCompleteDraft,
                onBack = onBack,
                errorMessage = stringResource(R.string.draft_sim_error_generic),
            )

            is DraftSimUiState.Complete -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = mc.primaryAccent)
            }

            else -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = mc.primaryAccent)
            }
        }

        MagicToastHost(toastState)
    }
}

@Composable
private fun ResultContent(
    state: DraftState?,
    onSave: () -> Unit,
    onBack: () -> Unit,
    errorMessage: String?,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    if (state == null && errorMessage != null) {
        FullErrorState(
            message = errorMessage,
            retryLabel = stringResource(R.string.draft_retry),
            onRetry = onSave,
        )
        return
    }

    val humanSeat = state?.seats?.firstOrNull { it.isHuman }
    val pool = humanSeat?.pool.orEmpty()
    // Group drafted cards by integer mana value for a readable deck preview.
    val grouped = pool.groupBy { it.card.cmc.toInt() }.toSortedMap()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = sp.sm, vertical = sp.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = mc.textPrimary,
                )
            }
            Text(
                text = stringResource(R.string.draft_sim_building_title),
                style = ty.titleLarge,
                color = mc.textPrimary,
            )
        }

        if (errorMessage != null) {
            InlineErrorState(
                message = errorMessage,
                retryLabel = stringResource(R.string.draft_retry),
                onRetry = onSave,
                modifier = Modifier.padding(horizontal = sp.lg, vertical = sp.xs),
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(sp.md),
            horizontalArrangement = Arrangement.spacedBy(sp.sm),
            verticalArrangement = Arrangement.spacedBy(sp.sm),
        ) {
            grouped.forEach { (cmc, cards) ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "CMC $cmc · ${cards.size}",
                        style = ty.labelLarge,
                        color = mc.textSecondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = sp.xs),
                    )
                }
                itemsIndexed(
                    items = cards,
                    key = { index, it -> "${it.card.scryfallId}:${it.isFoil}:$cmc:$index" },
                ) { _, card ->
                    DeckCardTile(card)
                }
            }

            // Basic lands footer (deck builder allocates 17).
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    shape = CardShape,
                    color = mc.surface,
                    modifier = Modifier.fillMaxWidth().padding(top = sp.sm),
                ) {
                    Text(
                        text = stringResource(R.string.draft_sim_basics_header, BASIC_LAND_COUNT),
                        style = ty.titleMedium,
                        color = mc.textPrimary,
                        modifier = Modifier.padding(sp.md),
                    )
                }
            }
        }

        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = sp.lg)
                .padding(bottom = sp.lg + navBarBottom),
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
        ) {
            Text(
                text = stringResource(R.string.draft_sim_save_deck),
                style = ty.labelLarge,
                color = mc.background,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DeckCardTile(card: DraftCard) {
    AsyncImage(
        model = card.card.imageNormal,
        contentDescription = card.card.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(CardShape),
    )
}

/** Number of basic lands the deck builder adds to a 40-card limited deck. */
private const val BASIC_LAND_COUNT = 17
