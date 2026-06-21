package com.mmg.manahub.feature.draft.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.CardRarity
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.SetSymbol
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.draft.domain.model.DraftConfig
import com.mmg.manahub.feature.draft.domain.model.DraftError
import com.mmg.manahub.feature.draft.domain.model.DraftMode
import com.mmg.manahub.feature.draft.presentation.viewmodel.DraftSimUiState
import com.mmg.manahub.feature.draft.presentation.viewmodel.DraftSimViewModel

/** Valid pick-timer presets (seconds). The first entry (0) means "no timer". */
private val TIMER_PRESETS = listOf(0, 15, 30, 60)

/** Allowed draft pod sizes: 2–10 seats (one human + the rest bots). */
private const val MIN_PLAYERS = 2
private const val MAX_PLAYERS = 10
private const val DEFAULT_PLAYERS = 8

/**
 * Size of the small inline info icon in the player-count selector. Sits between the spacing tokens
 * `sp.lg` (16dp) and `sp.xl` (24dp); a named constant keeps it readable as a non-touch decorative
 * glyph without forcing it onto the spacing scale.
 */
private val PlayerInfoIconSize = 18.dp

/**
 * Configuration screen for a draft simulation: mode selection and an optional pick timer.
 * Transitions to the drafting screen via [onNavigateToDrafting] once a draft is started.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftSetupScreen(
    onNavigateToDrafting: () -> Unit,
    onBack: () -> Unit,
    viewModel: DraftSimViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    val toastState = rememberMagicToastState()

    var timerIndex by remember { mutableStateOf(0) }
    var playerCount by remember { mutableStateOf(DEFAULT_PLAYERS) }

    // Navigate to drafting once a draft begins.
    LaunchedEffect(state) {
        if (state is DraftSimUiState.Drafting) {
            onNavigateToDrafting()
        }
    }

    var showForm by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is DraftSimUiState.SetupReady) {
            showForm = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ThemeBackground(modifier = Modifier.fillMaxSize())
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Top bar
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
                Spacer(Modifier.width(sp.xs))
                Text(
                    text = stringResource(R.string.draft_sim_setup_title),
                    style = ty.titleLarge,
                    color = mc.textPrimary,
                )
                if (state is DraftSimUiState.SetupReady) {
                    Spacer(Modifier.weight(1f))
                    SetSymbol(
                        setCode = (state as DraftSimUiState.SetupReady).setCode,
                        rarity = CardRarity.RARE,
                        size = 32.dp,
                        modifier = Modifier.padding(end = sp.md)
                    )
                }
            }

            when (val s = state) {
                is DraftSimUiState.Loading -> LoadingContent()

                is DraftSimUiState.Error -> FullErrorState(
                    message = stringResource(s.error.toMessageRes()),
                    retryLabel = if (s.retryable) stringResource(R.string.draft_retry) else null,
                    onRetry = if (s.retryable) viewModel::retryLoadSet else null,
                )

                is DraftSimUiState.SetupReady -> {
                    AnimatedVisibility(
                        visible = showForm,
                        enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 2 }
                    ) {
                        SetupForm(
                            setName = s.setName,
                            playerCount = playerCount,
                            onPlayerCountChanged = { playerCount = it },
                            timerIndex = timerIndex,
                            onTimerChanged = { timerIndex = it },
                            onStart = {
                                val pickTimer = TIMER_PRESETS[timerIndex].takeIf { it > 0 }
                                viewModel.startDraft(
                                    DraftConfig(
                                        setCode = s.setCode,
                                        mode = DraftMode.DRAFT,
                                        seatCount = playerCount,
                                        packCount = 3,
                                        pickTimerSeconds = pickTimer,
                                    )
                                )
                            },
                            startEnabled = true,
                        )
                    }
                }

                // Building / Complete are owned by the result screen; setup just waits.
                else -> LoadingContent()
            }
        }

        MagicToastHost(toastState)
    }
}

@Composable
private fun LoadingContent() {
    val mc = MaterialTheme.magicColors
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = mc.primaryAccent)
    }
}

@Composable
private fun SetupForm(
    setName: String,
    playerCount: Int,
    onPlayerCountChanged: (Int) -> Unit,
    timerIndex: Int,
    onTimerChanged: (Int) -> Unit,
    onStart: () -> Unit,
    startEnabled: Boolean,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = sp.lg),
        verticalArrangement = Arrangement.spacedBy(sp.lg),
    ) {
        Text(
            text = stringResource(R.string.draft_sim_setup_description, setName),
            style = ty.bodyMedium,
            color = mc.textSecondary,
        )

        // Players
        SectionLabel(stringResource(R.string.draft_sim_players_label))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(mc.surface, CardShape)
                .padding(sp.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = playerCount.toFloat(),
                    onValueChange = { onPlayerCountChanged(it.toInt()) },
                    valueRange = MIN_PLAYERS.toFloat()..MAX_PLAYERS.toFloat(),
                    steps = (MAX_PLAYERS - MIN_PLAYERS) - 1,
                    colors = SliderDefaults.colors(
                        thumbColor = mc.primaryAccent,
                        activeTrackColor = mc.primaryAccent,
                        inactiveTrackColor = mc.surfaceVariant,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(sp.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = mc.primaryAccent,
                        modifier = Modifier.size(PlayerInfoIconSize),
                    )
                    Spacer(Modifier.width(sp.xs))
                    Text(
                        text = playerCount.toString(),
                        style = ty.labelLarge,
                        color = mc.textPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(sp.xl),
                    )
                }
            }
            Text(
                text = stringResource(
                    R.string.draft_sim_players_count,
                    playerCount,
                    playerCount - 1,
                ),
                style = ty.labelSmall,
                color = mc.textSecondary,
                modifier = Modifier.padding(start = sp.xs),
            )
        }

        // Timer
        SectionLabel(stringResource(R.string.draft_sim_timer_label))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(mc.surface, CardShape)
                .padding(sp.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = timerIndex.toFloat(),
                    onValueChange = { onTimerChanged(it.toInt()) },
                    valueRange = 0f..(TIMER_PRESETS.size - 1).toFloat(),
                    steps = TIMER_PRESETS.size - 2,
                    colors = SliderDefaults.colors(
                        thumbColor = mc.primaryAccent,
                        activeTrackColor = mc.primaryAccent,
                        inactiveTrackColor = mc.surfaceVariant,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(sp.md))
                val timerValue = TIMER_PRESETS[timerIndex]
                Text(
                    text = if (timerValue == 0) stringResource(R.string.draft_sim_timer_off)
                    else stringResource(R.string.draft_sim_timer_seconds, timerValue),
                    style = ty.labelLarge,
                    color = mc.textPrimary,
                    modifier = Modifier.width(52.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TIMER_PRESETS.forEachIndexed { index, value ->
                    Text(
                        text = if (value == 0) "Off" else "${value}s",
                        style = ty.labelSmall,
                        color = if (timerIndex == index) mc.primaryAccent else mc.textDisabled,
                        fontWeight = if (timerIndex == index) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onStart,
            enabled = startEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(bottom = sp.lg)
                .graphicsLayer {
                    shadowElevation = 8.dp.toPx()
                    shape = ButtonShape
                    clip = true
                },
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = mc.primaryAccent,
                disabledContainerColor = mc.surfaceVariant
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Subtle shimmer background could be added here if we had a dedicated modifier
                Text(
                    text = stringResource(R.string.draft_sim_start_button).uppercase(),
                    style = ty.labelLarge,
                    color = if (startEnabled) mc.background else mc.textDisabled,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.magicTypography.labelLarge,
        color = MaterialTheme.magicColors.textPrimary,
        fontWeight = FontWeight.Bold,
    )
}

/** Maps a [DraftError] to a user-facing string resource. */
private fun DraftError.toMessageRes(): Int = when (this) {
    DraftError.SetNotDraftable -> R.string.draft_sim_error_not_draftable
    DraftError.OfflineNoCache -> R.string.draft_sim_error_offline
    DraftError.RatingsMissing -> R.string.draft_sim_error_generic
    DraftError.SetNotDownloaded -> R.string.draft_sim_error_offline
    is DraftError.Unexpected -> R.string.draft_sim_error_generic
}
