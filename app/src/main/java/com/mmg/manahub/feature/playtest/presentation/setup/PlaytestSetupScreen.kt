package com.mmg.manahub.feature.playtest.presentation.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.mmg.manahub.core.ui.theme.spacing
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.MagicCardInspectionOverlay
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.PlaytestEligibility
import com.mmg.manahub.core.model.PlaytestSetup
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaytestSetupScreen(
    onBack: () -> Unit,
    onNavigateToHand: (PlaytestSetup) -> Unit,
    viewModel: PlaytestSetupViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val toastState = rememberMagicToastState()

    // Consume one-shot navigation events from the buffered Channel. Collected with
    // LaunchedEffect(Unit){ collect } — NOT collectAsStateWithLifecycle — so each emission
    // is delivered exactly once and repeated equal events are never collapsed.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PlaytestSetupEvent.NavigateToHand -> onNavigateToHand(event.setup)
            }
        }
    }

    // Reset the navigation flag whenever we resume this screen (e.g., returning from a playtest).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.resetNavigation()
    }

    var inspectionCard by remember { mutableStateOf<Card?>(null) }
    var inspectionRect by remember { mutableStateOf(Rect.Zero) }
    var isDismissingInspection by remember { mutableStateOf(false) }
    var boxCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { boxCoords = it }
    ) {
        Scaffold(
            containerColor      = mc.background,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text  = stringResource(R.string.playtest_setup_title),
                            style = ty.titleMedium,
                            color = mc.textPrimary,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                tint = mc.textPrimary,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = mc.background,
                    ),
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when {
                    uiState.isLoading -> CircularProgressIndicator(
                        color    = mc.primaryAccent,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    uiState.errorMessage != null -> FullErrorState(
                        message    = uiState.errorMessage!!,
                        retryLabel = stringResource(R.string.action_back),
                        onRetry    = onBack,
                    )

                    else -> SetupContent(
                        uiState            = uiState,
                        onDrawCountChange  = viewModel::setDrawCount,
                        onDrawHand         = viewModel::onDrawHand,
                        onInspectCommander = { card, rect ->
                            inspectionCard = card
                            inspectionRect = rect
                        },
                        boxCoords          = boxCoords,
                    )
                }
            }
        }

        MagicToastHost(
            state    = toastState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        inspectionCard?.let { card ->
            MagicCardInspectionOverlay(
                card             = card,
                initialRect      = inspectionRect,
                isVisible        = true,
                isDismissing     = isDismissingInspection,
                onDismissRequest = { isDismissingInspection = true },
                onDismiss        = {
                    inspectionCard = null
                    isDismissingInspection = false
                },
            )
        }
    }

    // Surface ineligibility reason as a toast on first load.
    // Removed: redundant with UI fixed error state.
}

@Composable
private fun SetupContent(
    uiState: PlaytestSetupUiState,
    onDrawCountChange: (Int) -> Unit,
    onDrawHand: () -> Unit,
    onInspectCommander: (Card, Rect) -> Unit,
    boxCoords: LayoutCoordinates?,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    val isEligible = uiState.eligibility is PlaytestEligibility.Eligible

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(sp.xl),
    ) {
        // Deck Hero Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        ) {
            // Background Image with Gradient Overlay
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(uiState.deckImageUrl ?: R.drawable.mtg_card_back)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale      = ContentScale.Crop,
                alignment         = Alignment.TopCenter,
                modifier          = Modifier.fillMaxSize()
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                mc.background.copy(alpha = 0.6f),
                                mc.background
                            )
                        )
                    )
            )

            // Deck Info Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(sp.xl)
            ) {
                Text(
                    text     = uiState.deckName,
                    style    = ty.displayMedium.copy(fontWeight = FontWeight.Bold),
                    color    = mc.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(sp.xs))

                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(sp.sm)
                ) {
                    Text(
                        text  = uiState.deckFormat.replaceFirstChar { it.uppercase() },
                        style = ty.titleMedium,
                        color = mc.textSecondary,
                    )
                    
                    // Color Identity Symbols
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        uiState.colorIdentitySymbols.forEach { symbol ->
                            ManaSymbolImage(token = symbol, size = 20.dp)
                        }
                    }
                }
            }
        }

        Column(
            modifier            = Modifier
                .padding(horizontal = sp.xl)
                .padding(bottom = sp.xl),
            verticalArrangement = Arrangement.spacedBy(sp.xl)
        ) {
            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(sp.md)
            ) {
                Surface(
                    color    = mc.surface,
                    shape    = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(sp.md),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text  = uiState.mainboardCount.toString(),
                            style = ty.titleLarge,
                            color = mc.textPrimary
                        )
                        Text(
                            text  = stringResource(R.string.stats_total_cards).uppercase(),
                            style = ty.labelSmall,
                            color = mc.textSecondary
                        )
                    }
                }

                if (uiState.commanderCard != null) {
                    Surface(
                        color    = mc.surface,
                        shape    = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(sp.md),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text  = "1",
                                style = ty.titleLarge,
                                color = mc.textPrimary
                            )
                            Text(
                                text  = stringResource(R.string.carddetail_deck).uppercase(),
                                style = ty.labelSmall,
                                color = mc.textSecondary
                            )
                        }
                    }
                }
            }

            // Commander Section
            if (uiState.commanderCard != null) {
                Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
                    Text(
                        text  = stringResource(R.string.playtest_command_zone_label),
                        style = ty.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = mc.textPrimary,
                    )
                    
                    Surface(
                        color = mc.surface,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Calculated when layout is ready
                            }
                    ) {
                        var cardRect by remember { mutableStateOf(Rect.Zero) }

                        Row(
                            modifier = Modifier
                                .clickable { onInspectCommander(uiState.commanderCard, cardRect) }
                                .padding(sp.lg),
                            horizontalArrangement = Arrangement.spacedBy(sp.lg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = uiState.commanderCard.imageNormal,
                                contentDescription = uiState.commanderCard.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .width(120.dp)
                                    .aspectRatio(63f / 88f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .onGloballyPositioned { coords ->
                                        if (boxCoords != null && boxCoords.isAttached && coords.isAttached) {
                                            cardRect = boxCoords.localBoundingBoxOf(coords)
                                        }
                                    }
                            )
                            
                            Column(verticalArrangement = Arrangement.spacedBy(sp.xs)) {
                                CardName(
                                    name = uiState.commanderCard.name,
                                    style = ty.titleMedium,
                                    color = mc.textPrimary
                                )
                                Text(
                                    text = uiState.commanderCard.typeLine,
                                    style = ty.bodySmall,
                                    color = mc.textSecondary
                                )
                                Spacer(modifier = Modifier.height(sp.xs))
                                Surface(
                                    color = mc.goldMtg.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "COMMANDER",
                                        style = ty.labelSmall,
                                        color = mc.goldMtg,
                                        modifier = Modifier.padding(horizontal = sp.sm, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Eligibility warning.
            if (!isEligible && uiState.eligibility != null) {
                val ineligible = uiState.eligibility as PlaytestEligibility.Ineligible
                Surface(
                    color    = mc.lifeNegative.copy(alpha = 0.12f),
                    shape    = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier          = Modifier.padding(sp.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(sp.sm)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove, // Better than nothing, ideally an Error icon
                            contentDescription = null,
                            tint = mc.lifeNegative,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text     = ineligible.reason,
                            style    = ty.bodySmall,
                            color    = mc.lifeNegative,
                        )
                    }
                }
            }

            // Settings Section
            Surface(
                color = mc.surface,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(sp.lg),
                    verticalArrangement = Arrangement.spacedBy(sp.lg)
                ) {
                    Text(
                        text  = stringResource(R.string.playtest_draw_count_label),
                        style = ty.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = mc.textPrimary,
                    )
                    
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier              = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick  = { onDrawCountChange(uiState.drawCount - 1) },
                            enabled  = uiState.drawCount > 1,
                        ) {
                            Icon(
                                Icons.Default.Remove, 
                                contentDescription = null, 
                                tint = if (uiState.drawCount > 1) mc.primaryAccent else mc.textDisabled,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        AnimatedContent(
                            targetState  = uiState.drawCount,
                            transitionSpec = {
                                fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                            },
                            label = "DrawCountTransition",
                        ) { count ->
                            Text(
                                text      = count.toString(),
                                style     = ty.displayLarge.copy(fontWeight = FontWeight.Black),
                                color     = mc.primaryAccent,
                                textAlign = TextAlign.Center,
                            )
                        }
                        
                        IconButton(
                            onClick = { onDrawCountChange(uiState.drawCount + 1) },
                            enabled = uiState.drawCount < 10,
                        ) {
                            Icon(
                                Icons.Default.Add, 
                                contentDescription = null, 
                                tint = if (uiState.drawCount < 10) mc.primaryAccent else mc.textDisabled,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            // Primary CTA.
            Button(
                onClick  = onDrawHand,
                enabled  = isEligible,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = mc.primaryAccent,
                    disabledContainerColor = mc.primaryAccent.copy(alpha = 0.35f),
                ),
            ) {
                Text(
                    text  = stringResource(R.string.playtest_draw_hand_cta),
                    style = ty.titleMedium,
                    color = mc.onAccent,
                )
            }
        }
    }
}
