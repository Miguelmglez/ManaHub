package com.mmg.manahub.feature.draft.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.CardRarity
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ManaCurveChart
import com.mmg.manahub.core.ui.components.SetSymbol
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.coloredShadow
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

        // Celebratory sparkle overlay
        if (state is DraftSimUiState.Building || state is DraftSimUiState.Complete) {
            CelebrationSparkles()
        }
    }
}

@Composable
private fun CelebrationSparkles() {
    val mc = MaterialTheme.magicColors
    val infiniteTransition = rememberInfiniteTransition(label = "sparkles")
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkle_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        mc.primaryAccent.copy(alpha = alpha * 0.5f),
                        Color.Transparent
                    ),
                    center = androidx.compose.ui.geometry.Offset(200f, 200f),
                    radius = 400f
                )
            )
            .graphicsLayer { this.alpha = alpha }
    )
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
    val cmcDistribution = remember(pool) {
        pool.groupBy { it.card.cmc.toInt() }.mapValues { it.value.size }
    }
    
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // --- Header ---
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.draft_sim_building_title),
                    style = ty.titleLarge,
                    color = mc.textPrimary,
                )
                if (state != null) {
                    Text(
                        text = state.config.setCode.uppercase(),
                        style = ty.labelSmall,
                        color = mc.textSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (state != null) {
                SetSymbol(
                    setCode = state.config.setCode,
                    rarity = CardRarity.MYTHIC,
                    size = 32.dp,
                    modifier = Modifier.padding(end = sp.md)
                )
            }
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
            // --- Analysis Section ---
            item(span = { GridItemSpan(maxLineSpan) }) {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { -20 }
                ) {
                    Surface(
                        shape = CardShape,
                        color = mc.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = sp.md)
                            .coloredShadow(
                                color = mc.primaryAccent.copy(alpha = 0.1f),
                                borderRadius = 12.dp,
                                blurRadius = 12.dp
                            )
                    ) {
                        Column(modifier = Modifier.padding(sp.md)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(sp.sm)
                            ) {
                                Icon(
                                    Icons.Default.BarChart,
                                    contentDescription = null,
                                    tint = mc.primaryAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "DECK ANALYSIS",
                                    style = ty.labelMedium,
                                    color = mc.textPrimary,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(Modifier.height(sp.md))
                            ManaCurveChart(
                                cmcDistribution = cmcDistribution,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(sp.md))
                            HorizontalDivider(color = mc.surfaceVariant, thickness = 0.5.dp)
                            Spacer(Modifier.height(sp.md))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatItem(label = "POOL", value = pool.size.toString())
                                StatItem(label = "NON-LAND", value = (pool.size).toString())
                                StatItem(label = "LANDS", value = BASIC_LAND_COUNT.toString())
                            }
                        }
                    }
                }
            }

            grouped.forEach { (cmc, cards) ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { 
                        kotlinx.coroutines.delay(100L + cmc * 50L)
                        visible = true 
                    }
                    
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn() + slideInVertically { it / 2 }
                    ) {
                        Row(
                            modifier = Modifier.padding(top = sp.sm, bottom = sp.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(sp.sm)
                        ) {
                            Surface(
                                shape = ChipShape,
                                color = mc.primaryAccent.copy(alpha = 0.15f),
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = cmc.toString(),
                                        style = ty.labelSmall,
                                        color = mc.primaryAccent,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Text(
                                text = "CMC $cmc",
                                style = ty.labelLarge,
                                color = mc.textPrimary,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${cards.size} cards",
                                style = ty.labelMedium,
                                color = mc.textSecondary
                            )
                        }
                    }
                }
                
                itemsIndexed(
                    items = cards,
                    key = { index, it -> "${it.card.scryfallId}:${it.isFoil}:$cmc:$index" },
                ) { index, card ->
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(200L + cmc * 50L + index * 20L)
                        visible = true
                    }
                    
                    AnimatedVisibility(
                        visible = visible,
                        enter = scaleIn(tween(300)) + fadeIn(tween(300))
                    ) {
                        DeckCardTile(card)
                    }
                }
            }

            // Basic lands footer (deck builder allocates 17).
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    shape = CardShape,
                    color = mc.surface,
                    modifier = Modifier.fillMaxWidth().padding(top = sp.md),
                ) {
                    Row(
                        modifier = Modifier.padding(sp.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(sp.sm)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = mc.lifePositive,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.draft_sim_basics_header, BASIC_LAND_COUNT),
                            style = ty.titleMedium,
                            color = mc.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = sp.lg)
                .padding(bottom = sp.lg + navBarBottom),
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.draft_sim_save_deck).uppercase(),
                style = ty.labelLarge,
                color = mc.background,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = ty.titleMedium, color = mc.textPrimary, fontWeight = FontWeight.Bold)
        Text(text = label, style = ty.labelSmall, color = mc.textDisabled)
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
