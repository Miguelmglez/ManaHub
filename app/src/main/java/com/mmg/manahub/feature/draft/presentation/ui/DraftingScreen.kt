package com.mmg.manahub.feature.draft.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.draft.domain.model.DraftCard
import com.mmg.manahub.feature.draft.presentation.viewmodel.DraftSimUiState
import com.mmg.manahub.feature.draft.presentation.viewmodel.DraftSimViewModel

/** Single-letter color → token color for the commitment pips. */
private val COLOR_ORDER = listOf("W", "U", "B", "R", "G")

/**
 * The active drafting screen: shows the human's current pack, lets them pick a card or auto-pick,
 * displays their accumulated pool, and (optionally) a pick timer. Navigates to the result screen
 * once the draft transitions to BUILDING.
 */
@Composable
fun DraftingScreen(
    onNavigateToResult: () -> Unit,
    onBack: () -> Unit,
    viewModel: DraftSimViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors

    // Pause/resume the pick timer with the screen lifecycle.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.onScreenPaused()
                Lifecycle.Event.ON_RESUME -> viewModel.onScreenResumed()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Navigate forward once the draft is done picking. Guarded so two consecutive BUILDING/COMPLETE
    // emissions cannot fire onNavigateToResult() twice (which would push the result screen twice).
    val hasNavigated = remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (!hasNavigated.value &&
            (state is DraftSimUiState.Building || state is DraftSimUiState.Complete)
        ) {
            hasNavigated.value = true
            onNavigateToResult()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ThemeBackground(modifier = Modifier.fillMaxSize())

        when (val s = state) {
            is DraftSimUiState.Drafting -> DraftingContent(
                state = s,
                onPick = viewModel::onPick,
                onAutoPick = viewModel::onAutoPick,
                onBack = onBack,
            )

            is DraftSimUiState.Error -> FullErrorState(
                message = stringResource(R.string.draft_sim_error_generic),
                retryLabel = stringResource(R.string.action_back),
                onRetry = onBack,
            )

            // Building / Complete are handled by LaunchedEffect navigation; Loading otherwise.
            else -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = mc.primaryAccent)
            }
        }
    }
}

@Composable
private fun DraftingContent(
    state: DraftSimUiState.Drafting,
    onPick: (String) -> Unit,
    onAutoPick: () -> Unit,
    onBack: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    val humanSeat = remember(state.state) { state.state.seats.firstOrNull { it.isHuman } }
    val navBarBottom = WindowInsetsBottom()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // ── Top bar ────────────────────────────────────────────────────────────
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
                    text = stringResource(
                        R.string.draft_sim_pick_counter,
                        state.state.round,
                        state.state.pickNumber,
                    ),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                )
                ColorPips(commitment = humanSeat?.colorCommitment.orEmpty())
            }
            // Pool size badge
            Surface(shape = ChipShape, color = mc.surface) {
                Text(
                    text = stringResource(R.string.draft_sim_pool_size, state.poolSize),
                    style = ty.labelMedium,
                    color = mc.primaryAccent,
                    modifier = Modifier.padding(horizontal = sp.md, vertical = sp.xs),
                )
            }
        }

        // ── Timer bar ──────────────────────────────────────────────────────────
        val secondsLeft = state.timerSecondsLeft
        if (secondsLeft != null) {
            val total = state.state.config.pickTimerSeconds ?: secondsLeft.coerceAtLeast(1)
            val fraction = (secondsLeft.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
            val barColor = if (secondsLeft <= 5) mc.lifeNegative else mc.primaryAccent
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(horizontal = sp.lg, vertical = sp.xs),
                color = barColor,
                trackColor = mc.surfaceVariant,
            )
        }

        // ── Pack grid ────────────────────────────────────────────────────────────
        if (state.currentPack.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.draft_sim_empty_pack),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(sp.md),
                horizontalArrangement = Arrangement.spacedBy(sp.sm),
                verticalArrangement = Arrangement.spacedBy(sp.sm),
            ) {
                itemsIndexed(
                    items = state.currentPack,
                    // Include the index so two copies of the same card (possible after
                    // balanceColors) do not produce duplicate grid keys, which crash Compose.
                    key = { index, it -> "${it.card.scryfallId}:${it.isFoil}:$index" },
                ) { _, draftCard ->
                    DraftPackCard(
                        draftCard = draftCard,
                        onClick = { onPick(draftCard.card.scryfallId) },
                    )
                }
            }
        }

        // ── Pool panel (compact, grouped by color) ───────────────────────────────
        if (humanSeat != null && humanSeat.pool.isNotEmpty()) {
            PoolPanel(pool = humanSeat.pool)
        }

        // ── Auto-pick action ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sp.lg, vertical = sp.sm)
                .padding(bottom = navBarBottom),
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                onClick = onAutoPick,
                shape = ChipShape,
                color = mc.surface,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = sp.md, vertical = sp.sm),
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = null,
                        tint = mc.goldMtg,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(sp.xs))
                    Text(
                        text = stringResource(R.string.draft_sim_auto_pick),
                        style = ty.labelLarge,
                        color = mc.textPrimary,
                    )
                }
            }
        }
    }
}

/**
 * A single card in the draft pack. Tap to pick. Long-press flips a double-faced card
 * to its back image while held.
 */
@Composable
private fun DraftPackCard(
    draftCard: DraftCard,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val card = draftCard.card
    val hasBack = card.imageBackNormal != null
    var showingBack by remember(card.scryfallId) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(CardShape)
            .pointerInput(card.scryfallId, hasBack) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { if (hasBack) showingBack = true },
                    onPress = {
                        if (hasBack) {
                            tryAwaitRelease()
                            showingBack = false
                        }
                    },
                )
            },
    ) {
        AsyncImage(
            model = if (showingBack) card.imageBackNormal else card.imageNormal,
            contentDescription = card.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        // Foil shimmer overlay
        if (draftCard.isFoil) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0x33FFFFFF),
                                Color(0x11FF00FF),
                                Color(0x3300FFFF),
                            ),
                        ),
                    ),
            )
        }
    }
}

/** Color commitment pips, sized proportionally to the seat's accumulated weights. */
@Composable
private fun ColorPips(commitment: Map<String, Float>) {
    val mc = MaterialTheme.magicColors
    val colorFor: (String) -> Color = {
        when (it) {
            "W" -> mc.manaW; "U" -> mc.manaU; "B" -> mc.manaB
            "R" -> mc.manaR; "G" -> mc.manaG; else -> mc.manaC
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        COLOR_ORDER.forEach { letter ->
            val weight = commitment[letter] ?: 0f
            val alpha = if (weight <= 0f) 0.18f else 1f
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(ChipShape)
                    .background(colorFor(letter).copy(alpha = alpha)),
            )
        }
    }
}

/** A compact horizontal strip of the human's drafted pool, grouped by color identity. */
@Composable
private fun PoolPanel(pool: List<DraftCard>) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    Surface(
        color = mc.backgroundSecondary.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = sp.md, vertical = sp.sm)) {
            Text(
                text = stringResource(R.string.draft_sim_pool_panel_title),
                style = ty.labelMedium,
                color = mc.textSecondary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(sp.xs))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 44.dp),
                modifier = Modifier.fillMaxWidth().height(80.dp),
                horizontalArrangement = Arrangement.spacedBy(sp.xs),
                verticalArrangement = Arrangement.spacedBy(sp.xs),
            ) {
                itemsIndexed(
                    items = pool,
                    key = { index, it -> "${it.card.scryfallId}:${it.isFoil}:$index" },
                ) { _, card ->
                    AsyncImage(
                        model = card.card.imageArtCrop ?: card.card.imageNormal,
                        contentDescription = card.card.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(ChipShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun WindowInsetsBottom() =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
