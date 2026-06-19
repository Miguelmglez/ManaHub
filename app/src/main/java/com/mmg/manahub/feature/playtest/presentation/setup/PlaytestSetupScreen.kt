package com.mmg.manahub.feature.playtest.presentation.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.playtest.domain.model.PlaytestEligibility
import com.mmg.manahub.feature.playtest.domain.model.PlaytestSetup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaytestSetupScreen(
    onBack: () -> Unit,
    onNavigateToHand: (PlaytestSetup) -> Unit,
    viewModel: PlaytestSetupViewModel = hiltViewModel(),
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

    Box {
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
                        uiState  = uiState,
                        onDrawCountChange  = viewModel::setDrawCount,
                        onOnThePlayChange  = viewModel::setOnThePlay,
                        onDrawHand         = viewModel::onDrawHand,
                    )
                }
            }
        }

        MagicToastHost(
            state    = toastState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // Surface ineligibility reason as a toast on first load.
    LaunchedEffect(uiState.eligibility) {
        val ineligible = uiState.eligibility as? PlaytestEligibility.Ineligible ?: return@LaunchedEffect
        toastState.show(ineligible.reason, MagicToastType.INFO)
    }
}

@Composable
private fun SetupContent(
    uiState: PlaytestSetupUiState,
    onDrawCountChange: (Int) -> Unit,
    onOnThePlayChange: (Boolean) -> Unit,
    onDrawHand: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val isEligible = uiState.eligibility is PlaytestEligibility.Eligible

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // Deck header card.
        Surface(
            color  = mc.surface,
            shape  = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier          = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (uiState.commanderCard?.imageNormal != null) {
                    AsyncImage(
                        model             = uiState.commanderCard.imageNormal,
                        contentDescription = uiState.commanderCard.name,
                        contentScale      = ContentScale.Crop,
                        modifier          = Modifier
                            .size(width = 48.dp, height = 67.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = uiState.deckName,
                        style = ty.titleMedium,
                        color = mc.textPrimary,
                    )
                    Text(
                        text  = uiState.deckFormat.replaceFirstChar { it.uppercase() },
                        style = ty.bodySmall,
                        color = mc.textSecondary,
                    )
                    Text(
                        text  = stringResource(R.string.playtest_card_count, uiState.mainboardCount),
                        style = ty.bodySmall,
                        color = if (isEligible) mc.textSecondary else mc.lifeNegative,
                    )
                }
            }
        }

        // Eligibility warning.
        if (!isEligible && uiState.eligibility != null) {
            val ineligible = uiState.eligibility as PlaytestEligibility.Ineligible
            Surface(
                color    = mc.lifeNegative.copy(alpha = 0.12f),
                shape    = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text     = ineligible.reason,
                    style    = ty.bodySmall,
                    color    = mc.lifeNegative,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        // Commander info strip.
        if (uiState.commanderCard != null && uiState.deckFormat == "commander") {
            Surface(
                color  = mc.primaryAccent.copy(alpha = 0.08f),
                shape  = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text     = stringResource(R.string.playtest_commander_library_note),
                    style    = ty.bodySmall,
                    color    = mc.primaryAccent,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        // Cards to draw stepper.
        Text(
            text  = stringResource(R.string.playtest_draw_count_label),
            style = ty.labelMedium,
            color = mc.textSecondary,
        )
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier              = Modifier.fillMaxWidth(),
        ) {
            IconButton(
                onClick  = { onDrawCountChange(uiState.drawCount - 1) },
                enabled  = uiState.drawCount > 1,
            ) {
                Icon(Icons.Default.Remove, contentDescription = null, tint = mc.primaryAccent)
            }

            Spacer(Modifier.width(8.dp))

            AnimatedContent(
                targetState  = uiState.drawCount,
                transitionSpec = {
                    fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                },
                label = "DrawCountTransition",
            ) { count ->
                Text(
                    text      = count.toString(),
                    style     = ty.titleMedium,
                    color     = mc.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.width(48.dp),
                )
            }

            Spacer(Modifier.width(8.dp))

            IconButton(
                onClick = { onDrawCountChange(uiState.drawCount + 1) },
                enabled = uiState.drawCount < 10,
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = mc.primaryAccent)
            }
        }

        // On the play / on the draw selector.
        Text(
            text  = stringResource(R.string.playtest_play_draw_label),
            style = ty.labelMedium,
            color = mc.textSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uiState.isOnThePlay,
                onClick  = { onOnThePlayChange(true) },
                label    = { Text(stringResource(R.string.playtest_on_the_play), style = ty.labelSmall) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = mc.primaryAccent.copy(alpha = 0.2f),
                    selectedLabelColor     = mc.primaryAccent,
                    containerColor         = mc.surface,
                    labelColor             = mc.textSecondary,
                ),
            )
            FilterChip(
                selected = !uiState.isOnThePlay,
                onClick  = { onOnThePlayChange(false) },
                label    = { Text(stringResource(R.string.playtest_on_the_draw), style = ty.labelSmall) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = mc.primaryAccent.copy(alpha = 0.2f),
                    selectedLabelColor     = mc.primaryAccent,
                    containerColor         = mc.surface,
                    labelColor             = mc.textSecondary,
                ),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Primary CTA.
        Button(
            onClick  = onDrawHand,
            enabled  = isEligible,
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor         = mc.primaryAccent,
                disabledContainerColor = mc.primaryAccent.copy(alpha = 0.35f),
            ),
        ) {
            Text(
                text  = stringResource(R.string.playtest_draw_hand_cta),
                style = ty.labelLarge,
                color = mc.background,
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}
