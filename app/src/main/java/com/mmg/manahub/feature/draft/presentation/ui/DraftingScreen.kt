package com.mmg.manahub.feature.draft.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.GroupingMode
import com.mmg.manahub.core.ui.components.CardFullScreenDialog
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.GroupingFlowSelector
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.components.OracleText
import com.mmg.manahub.core.ui.theme.ButtonShape
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

/** Sort weight for a rarity string: mythic first, common last. */
private fun rarityOrder(rarity: String): Int = when (rarity.lowercase()) {
    "mythic" -> 0
    "rare" -> 1
    "uncommon" -> 2
    "common" -> 3
    else -> 4
}

/**
 * The active drafting screen: shows the human's current pack, lets them inspect and pick a card or
 * auto-pick, exposes their accumulated pool via a filterable bottom sheet, and (optionally) a pick
 * timer. Navigates to the result screen once the draft transitions to BUILDING.
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

    var zoomedCard by remember { mutableStateOf<Card?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        ThemeBackground(modifier = Modifier.fillMaxSize())

        when (val s = state) {
            is DraftSimUiState.Drafting -> DraftingContent(
                state = s,
                onPick = viewModel::onPick,
                onAutoPick = viewModel::onAutoPick,
                onBack = onBack,
                onZoomFromPool = { zoomedCard = it }
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

        zoomedCard?.let { card ->
            CardFullScreenDialog(
                card = card,
                onDismiss = { zoomedCard = null }
            )
        }
    }
}

@Composable
private fun DraftingContent(
    state: DraftSimUiState.Drafting,
    onPick: (String) -> Unit,
    onAutoPick: () -> Unit,
    onBack: () -> Unit,
    onZoomFromPool: (Card) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    val humanSeat = remember(state.state) { state.state.seats.firstOrNull { it.isHuman } }
    val navBarBottom = WindowInsetsBottom()

    // The currently zoomed card (null = no zoom sheet) and pool-sheet visibility.
    var selectedCard by remember { mutableStateOf<DraftCard?>(null) }
    var showPoolSheet by remember { mutableStateOf(false) }

    // Rarity-sorted pack (mythic → common, then alphabetical) for predictable scanning.
    val sortedPack = remember(state.currentPack) {
        state.currentPack.sortedWith(
            compareBy({ rarityOrder(it.card.rarity) }, { it.card.name }),
        )
    }

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

            // Integrated circular timer + Pick counter
            val secondsLeft = state.timerSecondsLeft
            val isUrgent = secondsLeft != null && secondsLeft <= 5

            val infiniteTransition = rememberInfiniteTransition(label = "timer_pulse")
            val pulseScale by if (isUrgent) {
                infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "pulse_scale",
                )
            } else {
                remember { mutableStateOf(1f) }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    },
            ) {
                if (secondsLeft != null) {
                    val total = state.state.config.pickTimerSeconds ?: secondsLeft.coerceAtLeast(1)
                    val progress = (secondsLeft.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress,
                        label = "timer_progress",
                    )
                    val barColor = if (isUrgent) mc.lifeNegative else mc.primaryAccent

                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = barColor,
                        trackColor = mc.surfaceVariant,
                        strokeWidth = 3.dp,
                        strokeCap = StrokeCap.Round,
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = mc.surfaceVariant,
                        strokeWidth = 2.dp,
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.state.round.toString(),
                        style = ty.labelSmall,
                        color = mc.textSecondary,
                        fontSize = 10.sp,
                    )
                    Text(
                        text = state.state.pickNumber.toString(),
                        style = ty.titleMedium,
                        color = if (isUrgent) mc.lifeNegative else mc.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.width(sp.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.draft_sim_pick_counter,
                        state.state.round,
                        state.state.pickNumber,
                    ).uppercase(),
                    style = ty.labelMedium,
                    color = mc.textSecondary,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Pool size badge
            Surface(
                onClick = {showPoolSheet = true},
                shape = ChipShape,
                color = mc.primaryAccent.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.3f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = sp.md, vertical = sp.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.Style,
                        contentDescription = null,
                        tint = mc.primaryAccent,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = state.poolSize.toString(),
                        style = ty.labelMedium,
                        color = mc.primaryAccent,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // ── Pack grid ────────────────────────────────────────────────────────────
        if (sortedPack.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.draft_sim_empty_pack),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            AnimatedContent(
                targetState = sortedPack,
                transitionSpec = {
                    (slideInHorizontally { it } + fadeIn())
                        .togetherWith(slideOutHorizontally { -it } + fadeOut())
                },
                label = "pack_transition",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { pack ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(sp.md),
                    horizontalArrangement = Arrangement.spacedBy(sp.sm),
                    verticalArrangement = Arrangement.spacedBy(sp.sm),
                ) {
                    itemsIndexed(
                        items = pack,
                        key = { index, it -> "${it.card.scryfallId}:${it.isFoil}:$index" },
                    ) { index, draftCard ->
                        // Delay the entrance of each card slightly for a staggered effect.
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(index * 30L)
                            visible = true
                        }

                        AnimatedVisibility(
                            visible = visible,
                            enter = scaleIn(tween(400)) + fadeIn(tween(400)),
                        ) {
                            DraftPackCard(
                                draftCard = draftCard,
                                isSuggested = draftCard.card.scryfallId == state.suggestedPickId,
                                onTap = { selectedCard = draftCard },
                            )
                        }
                    }
                }
            }
        }

        // ── Bottom action bar ─────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sp.lg, vertical = sp.sm)
                .padding(bottom = navBarBottom),
            verticalArrangement = Arrangement.spacedBy(sp.xs)
        ) {

            // Suggested pick shortcut — opens the zoom sheet for the engine's recommended card.
            val suggestedCard = state.suggestedPickId?.let { id ->
                sortedPack.firstOrNull { it.card.scryfallId == id }
            }
            if (suggestedCard != null) {
                Button(
                    onClick = { selectedCard = suggestedCard },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = ButtonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = mc.surface,
                        contentColor = mc.textPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = mc.goldMtg,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(sp.sm))
                        Text(
                            text = stringResource(R.string.draft_sim_suggested_pick),
                            style = ty.labelLarge,
                        )
                    }
                }
            }

            // Auto-pick button.
            Button(
                onClick = onAutoPick,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = mc.surface,
                    contentColor = mc.textPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = null,
                        tint = mc.goldMtg,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(sp.sm))
                    Text(
                        text = stringResource(R.string.draft_sim_auto_pick),
                        style = ty.labelLarge,
                    )
                }
            }
        }
    }

    // ── Modals ────────────────────────────────────────────────────────────────
    selectedCard?.let { card ->
        CardZoomSheet(
            draftCard = card,
            onConfirmPick = {
                val id = card.card.scryfallId
                selectedCard = null
                onPick(id)
            },
            onDismiss = { selectedCard = null },
        )
    }

    if (showPoolSheet) {
        PoolBottomSheet(
            pool = humanSeat?.pool ?: emptyList(),
            onDismiss = { showPoolSheet = false },
            onZoomCard = onZoomFromPool
        )
    }
}

/**
 * A single card in the draft pack. Tap to open the zoom sheet (no direct pick). Long-press flips a
 * double-faced card to its back image while held. A suggested card is outlined in gold.
 */
@Composable
private fun DraftPackCard(
    draftCard: DraftCard,
    isSuggested: Boolean,
    onTap: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val card = draftCard.card
    val hasBack = card.imageBackNormal != null
    var showingBack by remember(card.scryfallId) { mutableStateOf(false) }

    // Scale animation on press
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        label = "card_scale",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = 4.dp.toPx()
                shape = CardShape
                clip = true
            }
            .clip(CardShape)
            .then(
                if (isSuggested) Modifier.border(2.dp, mc.goldMtg, CardShape) else Modifier,
            )
            .pointerInput(card.scryfallId, hasBack) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { if (hasBack) showingBack = true },
                    onPress = {
                        isPressed = true
                        try {
                            if (hasBack) {
                                tryAwaitRelease()
                                showingBack = false
                            } else {
                                awaitRelease()
                            }
                        } finally {
                            isPressed = false
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

        // Foil shimmer overlay - animated
        if (draftCard.isFoil) {
            val shimmerTransition = rememberInfiniteTransition(label = "foil_shimmer")
            val offset by shimmerTransition.animateFloat(
                initialValue = -500f,
                targetValue = 500f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2500),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "shimmer_offset",
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x11FFFFFF),
                                Color(0x33FF00FF),
                                Color(0x3300FFFF),
                                Color(0x11FFFFFF),
                                Color.Transparent,
                            ),
                            start = androidx.compose.ui.geometry.Offset(offset, offset),
                            end = androidx.compose.ui.geometry.Offset(offset + 300f, offset + 300f),
                        ),
                    ),
            )
        }
    }
}

/**
 * Full-screen zoom sheet for a single draft card. Shows the full card image (tap to flip a DFC),
 * tier/foil badges, name, mana cost, type line, oracle text, and a primary "Pick this card" action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardZoomSheet(
    draftCard: DraftCard,
    onConfirmPick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    val card = draftCard.card
    val hasBack = !card.imageBackNormal.isNullOrBlank()
    var showingBack by remember(card.scryfallId) { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (showingBack) -180f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "CardFlip",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = mc.background,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Close button row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = sp.sm, vertical = sp.xs),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = mc.textSecondary)
                }
            }

            // Card image with flip
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(0.716f)
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 12f * density
                    }
                    .clip(CardShape)
                    .then(
                        if (hasBack) Modifier.clickable { showingBack = !showingBack } else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = card.imageNormal,
                    contentDescription = card.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (rotation >= -90f) 1f else 0f },
                )
                if (hasBack) {
                    AsyncImage(
                        model = card.imageBackNormal,
                        contentDescription = card.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationY = 180f
                                alpha = if (rotation < -90f) 1f else 0f
                            },
                    )
                }
            }

            Spacer(Modifier.height(sp.md))

            // Badges row (tier + foil)
            if (draftCard.tierRating != null || draftCard.isFoil) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(sp.xs),
                    modifier = Modifier.padding(horizontal = sp.lg),
                ) {
                    draftCard.tierRating?.let { rating ->
                        Surface(shape = ChipShape, color = mc.goldMtg.copy(alpha = 0.18f)) {
                            Text(
                                text = rating,
                                style = ty.labelMedium,
                                color = mc.goldMtg,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = sp.sm, vertical = sp.xxs),
                            )
                        }
                    }
                    if (draftCard.isFoil) {
                        Surface(shape = ChipShape, color = mc.primaryAccent.copy(alpha = 0.18f)) {
                            Text(
                                text = "Foil",
                                style = ty.labelMedium,
                                color = mc.primaryAccent,
                                modifier = Modifier.padding(horizontal = sp.sm, vertical = sp.xxs),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(sp.sm))
            }

            // Card name + mana cost
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = sp.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val displayName = card.printedName?.takeIf { it.isNotBlank() } ?: card.name
                    CardName(
                        name = displayName,
                        showFrontOnly = true,
                        style = ty.titleMedium,
                        color = mc.textPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    card.manaCost?.let { ManaCostImages(manaCost = it, symbolSize = 18.dp) }
                }
                val typeLine = card.printedTypeLine?.takeIf { it.isNotBlank() } ?: card.typeLine
                Text(typeLine, style = ty.labelMedium, color = mc.textSecondary)
                Spacer(Modifier.height(sp.xs))
                HorizontalDivider(color = mc.surfaceVariant)
                Spacer(Modifier.height(sp.xs))
                val oracleText = card.oracleText?.takeIf { it.isNotBlank() } ?: card.printedText ?: ""
                if (oracleText.isNotBlank()) {
                    OracleText(text = oracleText, style = ty.bodySmall)
                }
            }

            Spacer(Modifier.height(sp.lg))

            // Pick button
            Button(
                onClick = onConfirmPick,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(52.dp),
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
            ) {
                Text(
                    text = stringResource(R.string.draft_sim_confirm_pick),
                    style = ty.labelLarge,
                    color = mc.background,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(sp.lg))
        }
    }
}

/**
 * Filterable bottom sheet showing the human's drafted pool. Cards can be filtered by rarity, color,
 * and type; the result is rarity-sorted and rendered as an adaptive art-crop grid.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PoolBottomSheet(
    pool: List<DraftCard>,
    onDismiss: () -> Unit,
    onZoomCard: (Card) -> Unit
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    var selectedRarity by remember { mutableStateOf<String?>(null) }
    var selectedColor by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var groupingMode by remember { mutableStateOf(GroupingMode.TYPE) }

    val filtered = remember(pool, selectedRarity, selectedColor, selectedType) {
        pool.filter { dc ->
            val card = dc.card
            val rarityOk = selectedRarity == null || card.rarity.equals(selectedRarity, ignoreCase = true)
            val colorOk = when (selectedColor) {
                null -> true
                "Multicolor" -> card.colors.size >= 2
                "Colorless" -> card.colors.isEmpty() && !card.typeLine.contains("Land", ignoreCase = true)
                "Land" -> card.typeLine.contains("Land", ignoreCase = true)
                else -> card.colors.size == 1 && card.colors.firstOrNull() == selectedColor
            }
            val typeOk = selectedType == null || card.typeLine.contains(selectedType!!, ignoreCase = true)
            rarityOk && colorOk && typeOk
        }
    }

    val grouped = remember(filtered, groupingMode) {
        groupDraftCards(filtered, groupingMode)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = mc.backgroundSecondary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = mc.textDisabled) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f),
        ) {
            // Title + count
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = sp.lg, vertical = sp.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.draft_sim_pool_panel_title),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.draft_sim_pool_filter_showing, filtered.size, pool.size),
                    style = ty.labelSmall,
                    color = mc.textSecondary,
                )
            }

            // Grouping Selector
            Column(Modifier.padding(horizontal = sp.lg, vertical = sp.xs)) {
                GroupingFlowSelector(
                    selected = groupingMode,
                    onSelect = { groupingMode = it }
                )
            }

            // Rarity filter
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = sp.lg),
                horizontalArrangement = Arrangement.spacedBy(sp.xs),
                verticalArrangement = Arrangement.spacedBy(sp.xs),
            ) {
                listOf("mythic", "rare", "uncommon", "common").forEach { rarity ->
                    val label = when (rarity) {
                        "mythic" -> stringResource(R.string.draft_sim_rarity_mythic)
                        "rare" -> stringResource(R.string.draft_sim_rarity_rare)
                        "uncommon" -> stringResource(R.string.draft_sim_rarity_uncommon)
                        else -> stringResource(R.string.draft_sim_rarity_common)
                    }
                    FilterChip(
                        selected = selectedRarity == rarity,
                        onClick = { selectedRarity = if (selectedRarity == rarity) null else rarity },
                        label = { Text(label, style = ty.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = mc.primaryAccent,
                            selectedLabelColor = mc.background,
                            labelColor = mc.textSecondary,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(sp.xs))

            // Color filter
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = sp.lg),
                horizontalArrangement = Arrangement.spacedBy(sp.xs),
                verticalArrangement = Arrangement.spacedBy(sp.xs),
            ) {
                listOf("W", "U", "B", "R", "G").forEach { code ->
                    FilterChip(
                        selected = selectedColor == code,
                        onClick = { selectedColor = if (selectedColor == code) null else code },
                        label = { ManaSymbolImage(token = code, size = 20.dp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = mc.primaryAccent.copy(alpha = 0.18f),
                            labelColor = mc.textSecondary,
                        ),
                    )
                }
                FilterChip(
                    selected = selectedColor == "Multicolor",
                    onClick = { selectedColor = if (selectedColor == "Multicolor") null else "Multicolor" },
                    label = { Text(stringResource(R.string.collection_filter_multicolor), style = ty.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = mc.primaryAccent,
                        selectedLabelColor = mc.background,
                        labelColor = mc.textSecondary,
                    ),
                )
                FilterChip(
                    selected = selectedColor == "Colorless",
                    onClick = { selectedColor = if (selectedColor == "Colorless") null else "Colorless" },
                    label = { Text(stringResource(R.string.stats_color_colorless), style = ty.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = mc.primaryAccent,
                        selectedLabelColor = mc.background,
                        labelColor = mc.textSecondary,
                    ),
                )
                FilterChip(
                    selected = selectedColor == "Land",
                    onClick = { selectedColor = if (selectedColor == "Land") null else "Land" },
                    label = { Text(stringResource(R.string.deckbuilder_lands), style = ty.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = mc.primaryAccent,
                        selectedLabelColor = mc.background,
                        labelColor = mc.textSecondary,
                    ),
                )
            }

            Spacer(Modifier.height(sp.xs))

            // Type filter
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = sp.lg),
                horizontalArrangement = Arrangement.spacedBy(sp.xs),
                verticalArrangement = Arrangement.spacedBy(sp.xs),
            ) {
                listOf(
                    "Creature" to R.string.deckdetail_group_creatures,
                    "Instant" to R.string.deckdetail_group_instants,
                    "Sorcery" to R.string.deckdetail_group_sorceries,
                    "Enchantment" to R.string.deckdetail_group_enchantments,
                    "Artifact" to R.string.deckdetail_group_artifacts,
                    "Planeswalker" to R.string.deckdetail_group_planeswalkers,
                ).forEach { (type, resId) ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = if (selectedType == type) null else type },
                        label = { Text(stringResource(resId), style = ty.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = mc.primaryAccent,
                            selectedLabelColor = mc.background,
                            labelColor = mc.textSecondary,
                        ),
                    )
                }
            }

            HorizontalDivider(color = mc.surfaceVariant, modifier = Modifier.padding(vertical = sp.xs))

            // Pool grid
            if (filtered.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.draft_sim_pool_empty),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(sp.md),
                    horizontalArrangement = Arrangement.spacedBy(sp.xs),
                    verticalArrangement = Arrangement.spacedBy(sp.xs),
                ) {
                    grouped.forEach { (groupLabel, cards) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            PoolGroupHeader(label = groupLabel, count = cards.size)
                        }
                        itemsIndexed(
                            items = cards,
                            key = { index, dc -> "pool_${groupLabel}_${dc.card.scryfallId}:${if (dc.isFoil) "f" else "n"}:$index" },
                        ) { _, dc ->
                            AsyncImage(
                                model = dc.card.imageNormal,
                                contentDescription = dc.card.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .aspectRatio(0.716f)
                                    .clip(CardShape)
                                    .clickable { onZoomCard(dc.card) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PoolGroupHeader(label: String, count: Int) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    val colorName = when {
        label == "W" -> stringResource(R.string.collection_filter_white)
        label == "U" -> stringResource(R.string.collection_filter_blue)
        label == "B" -> stringResource(R.string.collection_filter_black)
        label == "R" -> stringResource(R.string.collection_filter_red)
        label == "G" -> stringResource(R.string.collection_filter_green)
        label == "Multicolor" -> stringResource(R.string.collection_filter_multicolor)
        label == "Colorless" -> stringResource(R.string.stats_color_colorless)
        label == "Land" || label == "Lands" -> stringResource(R.string.deckbuilder_lands)
        label == "Creatures" -> stringResource(R.string.deckdetail_group_creatures)
        label == "Instants" -> stringResource(R.string.deckdetail_group_instants)
        label == "Sorceries" -> stringResource(R.string.deckdetail_group_sorceries)
        label == "Enchantments" -> stringResource(R.string.deckdetail_group_enchantments)
        label == "Artifacts" -> stringResource(R.string.deckdetail_group_artifacts)
        label == "Planeswalkers" -> stringResource(R.string.deckdetail_group_planeswalkers)
        label == "Other" -> stringResource(R.string.deckdetail_group_other)
        label.all { it.isDigit() } -> {
            val cmc = label.toInt()
            if (cmc == 7) stringResource(R.string.deckbuilder_cost_7_plus)
            else stringResource(R.string.deckbuilder_cost_value, cmc)
        }
        else -> label
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = sp.sm, bottom = sp.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.xs)
    ) {
        if (label.length == 1 && label[0] in "WUBRG") {
            ManaSymbolImage(token = label, size = 18.dp)
        }
        Text(
            text = "$colorName ($count)",
            style = ty.labelMedium,
            color = mc.goldMtg,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun groupDraftCards(cards: List<DraftCard>, mode: GroupingMode): List<Pair<String, List<DraftCard>>> {
    return when (mode) {
        GroupingMode.TYPE -> {
            val order = listOf("Creatures", "Instants", "Sorceries", "Enchantments", "Artifacts", "Planeswalkers", "Lands", "Other")
            val groups = cards.groupBy { entry ->
                val type = entry.card.typeLine
                when {
                    type.contains("Creature") -> "Creatures"
                    type.contains("Instant") -> "Instants"
                    type.contains("Sorcery") -> "Sorceries"
                    type.contains("Enchantment") -> "Enchantments"
                    type.contains("Artifact") -> "Artifacts"
                    type.contains("Planeswalker") -> "Planeswalkers"
                    type.contains("Land") -> "Lands"
                    else -> "Other"
                }
            }
            order.mapNotNull { label ->
                val list = groups[label]?.sortedBy { it.card.name } ?: emptyList()
                if (list.isEmpty()) null else label to list
            }
        }
        GroupingMode.COLOR -> {
            val order = listOf("W", "U", "B", "R", "G", "Multicolor", "Colorless", "Land")
            val groups = cards.groupBy { entry ->
                val card = entry.card
                if (card.typeLine.contains("Land")) return@groupBy "Land"
                when (card.colors.size) {
                    0 -> "Colorless"
                    1 -> card.colors.first()
                    else -> "Multicolor"
                }
            }
            order.mapNotNull { label ->
                val list = groups[label]?.sortedBy { it.card.name } ?: emptyList()
                if (list.isEmpty()) null else label to list
            }
        }
        GroupingMode.COST -> {
            val groups = cards.groupBy { entry ->
                (entry.card.cmc.toInt().coerceIn(0, 7)).toString()
            }
            groups.entries.filter { it.value.isNotEmpty() }
                .sortedBy { it.key.toInt() }
                .map { it.key to it.value.sortedBy { it.card.name } }
        }
        GroupingMode.TAG -> {
            val tagMap = mutableMapOf<String, MutableList<DraftCard>>()
            cards.forEach { entry ->
                val tags = entry.card.tags + entry.card.userTags
                if (tags.isEmpty()) {
                    tagMap.getOrPut("Untagged") { mutableListOf() }.add(entry)
                } else {
                    tags.forEach { tag ->
                        tagMap.getOrPut(tag.label) { mutableListOf() }.add(entry)
                    }
                }
            }
            tagMap.entries.filter { it.value.isNotEmpty() }
                .sortedByDescending { it.value.size }
                .map { it.key to it.value.sortedBy { it.card.name } }
        }
    }
}

/** Color commitment pips, sized proportionally to the seat's accumulated weights. */
@Composable
private fun ColorPips(commitment: Map<String, Float>) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing

    val colorFor: (String) -> Color = {
        when (it) {
            "W" -> mc.manaW; "U" -> mc.manaU; "B" -> mc.manaB
            "R" -> mc.manaR; "G" -> mc.manaG; else -> mc.manaC
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(sp.xxs),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp),
    ) {
        COLOR_ORDER.forEach { letter ->
            val weight = commitment[letter] ?: 0f
            val alpha = if (weight <= 0f) 0.15f else 1f
            val size = if (weight > 10f) 14.dp else if (weight > 0f) 12.dp else 10.dp

            Box(
                modifier = Modifier
                    .size(size)
                    .clip(ChipShape)
                    .background(colorFor(letter).copy(alpha = alpha))
                    .then(
                        if (weight > 0f) Modifier.border(0.5.dp, Color.White.copy(alpha = 0.3f), ChipShape)
                        else Modifier,
                    ),
            )
        }
    }
}

@Composable
private fun WindowInsetsBottom() =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
