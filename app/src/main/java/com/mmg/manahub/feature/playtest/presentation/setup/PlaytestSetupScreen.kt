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
import androidx.compose.material.icons.filled.Style
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
import com.mmg.manahub.core.ui.components.MagicCtaButton
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
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.mtg_card_back
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

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PlaytestSetupEvent.NavigateToHand -> onNavigateToHand(event.setup)
            }
        }
    }

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
            containerColor = mc.background,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.playtest_setup_title),
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
                        color = mc.primaryAccent,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    uiState.errorMessage != null -> FullErrorState(
                        message = uiState.errorMessage!!,
                        retryLabel = stringResource(R.string.action_back),
                        onRetry = onBack,
                    )

                    else -> SetupContent(
                        uiState = uiState,
                        onDrawCountChange = viewModel::setDrawCount,
                        onStartCountChange = viewModel::setStartCount,
                        onDrawHand = viewModel::onDrawHand,
                        onInspectCommander = { card, rect ->
                            inspectionCard = card
                            inspectionRect = rect
                        },
                        boxCoords = boxCoords,
                    )
                }
            }
        }

        MagicToastHost(
            state = toastState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        inspectionCard?.let { card ->
            MagicCardInspectionOverlay(
                card = card,
                initialRect = inspectionRect,
                isVisible = true,
                isDismissing = isDismissingInspection,
                onDismissRequest = { isDismissingInspection = true },
                onDismiss = {
                    inspectionCard = null
                    isDismissingInspection = false
                },
            )
        }
    }
}

@Composable
private fun SetupContent(
    uiState: PlaytestSetupUiState,
    onDrawCountChange: (Int) -> Unit,
    onStartCountChange: (Int) -> Unit,
    onDrawHand: () -> Unit,
    onInspectCommander: (Card, Rect) -> Unit,
    boxCoords: LayoutCoordinates?,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    val isEligible = uiState.eligibility is PlaytestEligibility.Eligible

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(sp.md),
    ) {
        // Deck Hero Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(uiState.deckImageUrl ?: Res.drawable.mtg_card_back)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize()
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

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(sp.xl)
            ) {
                Text(
                    text = uiState.deckName,
                    style = ty.displayMedium.copy(fontWeight = FontWeight.Bold),
                    color = mc.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(sp.xs))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(sp.sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Style,
                        contentDescription = null,
                        tint = mc.textSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = uiState.mainboardCount.toString(),
                        style = ty.titleMedium,
                        color = mc.textSecondary
                    )

                    Text(
                        text = uiState.deckFormat.replaceFirstChar { it.uppercase() },
                        style = ty.titleMedium,
                        color = mc.textSecondary,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        uiState.colorIdentitySymbols.forEach { symbol ->
                            ManaSymbolImage(token = symbol, size = 20.dp)
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .padding(horizontal = sp.md)
                .padding(bottom = sp.md),
            verticalArrangement = Arrangement.spacedBy(sp.md)
        ) {

            // Commander Section
            if (uiState.commanderCard != null) {
                Surface(
                    color = mc.surface,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    var cardRect by remember { mutableStateOf(Rect.Zero) }

                    Row(
                        modifier = Modifier
                            .clickable {
                                onInspectCommander(
                                    uiState.commanderCard,
                                    cardRect
                                )
                            }
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
                                    modifier = Modifier.padding(
                                        horizontal = sp.sm,
                                        vertical = 2.dp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Eligibility warning
            if (!isEligible && uiState.eligibility != null) {
                val ineligible = uiState.eligibility as PlaytestEligibility.Ineligible
                Surface(
                    color = mc.lifeNegative.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(sp.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(sp.sm)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = null,
                            tint = mc.lifeNegative,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = ineligible.reason,
                            style = ty.bodySmall,
                            color = mc.lifeNegative,
                        )
                    }
                }
            }

            // Settings Section - Draw Count
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
                        text = stringResource(R.string.playtest_draw_count_label),
                        style = ty.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = mc.textPrimary,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { onDrawCountChange(uiState.drawCount - 1) },
                            enabled = uiState.drawCount > 1,
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = null,
                                tint = if (uiState.drawCount > 1) mc.primaryAccent else mc.textDisabled,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        AnimatedContent(
                            targetState = uiState.drawCount,
                            transitionSpec = {
                                fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                            },
                            label = "DrawCountTransition",
                        ) { count ->
                            Text(
                                text = count.toString(),
                                style = ty.displayLarge.copy(fontWeight = FontWeight.Black),
                                color = mc.primaryAccent,
                                textAlign = TextAlign.Center,
                            )
                        }

                        IconButton(
                            onClick = { onDrawCountChange(uiState.drawCount + 1) },
                            enabled = uiState.drawCount < uiState.maxDrawCount,
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = if (uiState.drawCount < uiState.maxDrawCount) mc.primaryAccent else mc.textDisabled,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            // Settings Section - Start Count
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
                        text = stringResource(R.string.playtest_start_count_label),
                        style = ty.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = mc.textPrimary,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { onStartCountChange(uiState.startCount - 1) },
                            enabled = uiState.startCount > 1,
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = null,
                                tint = if (uiState.startCount > 1) mc.primaryAccent else mc.textDisabled,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        AnimatedContent(
                            targetState = uiState.startCount,
                            transitionSpec = {
                                fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                            },
                            label = "StartCountTransition",
                        ) { count ->
                            Text(
                                text = count.toString(),
                                style = ty.displayLarge.copy(fontWeight = FontWeight.Black),
                                color = mc.primaryAccent,
                                textAlign = TextAlign.Center,
                            )
                        }

                        IconButton(
                            onClick = { onStartCountChange(uiState.startCount + 1) },
                            enabled = uiState.startCount < uiState.drawCount,
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = if (uiState.startCount < uiState.drawCount) mc.primaryAccent else mc.textDisabled,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            // Primary CTA
            MagicCtaButton(
                text = stringResource(R.string.playtest_draw_hand_cta),
                onClick = onDrawHand,
                enabled = true,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            )
        }
    }
}
