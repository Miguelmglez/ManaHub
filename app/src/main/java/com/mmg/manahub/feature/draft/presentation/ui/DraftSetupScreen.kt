package com.mmg.manahub.feature.draft.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
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

/**
 * Configuration screen for a draft simulation: mode selection and an optional pick timer.
 * Transitions to the drafting screen via [onNavigateToDrafting] once a draft is started.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftSetupScreen(
    onNavigateToDrafting: () -> Unit,
    onBack: () -> Unit,
    viewModel: DraftSimViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    val toastState = rememberMagicToastState()

    var selectedMode by remember { mutableStateOf(DraftMode.DRAFT) }
    var timerIndex by remember { mutableStateOf(0) }

    // Navigate to drafting once a draft begins.
    LaunchedEffect(state) {
        if (state is DraftSimUiState.Drafting) {
            onNavigateToDrafting()
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
            }

            when (val s = state) {
                is DraftSimUiState.Loading -> LoadingContent()

                is DraftSimUiState.Error -> FullErrorState(
                    message = stringResource(s.error.toMessageRes()),
                    retryLabel = if (s.retryable) stringResource(R.string.draft_retry) else null,
                    onRetry = if (s.retryable) viewModel::retryLoadSet else null,
                )

                is DraftSimUiState.SetupReady -> SetupForm(
                    setName = s.setName,
                    selectedMode = selectedMode,
                    onModeSelected = { selectedMode = it },
                    timerIndex = timerIndex,
                    onTimerChanged = { timerIndex = it },
                    onStart = {
                        val pickTimer = TIMER_PRESETS[timerIndex].takeIf { it > 0 }
                        viewModel.startDraft(
                            DraftConfig(
                                setCode = s.setCode,
                                mode = selectedMode,
                                seatCount = if (selectedMode == DraftMode.SEALED) 1 else 8,
                                packCount = if (selectedMode == DraftMode.SEALED) 6 else 3,
                                pickTimerSeconds = pickTimer,
                            )
                        )
                    },
                    startEnabled = state !is DraftSimUiState.Loading,
                )

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupForm(
    setName: String,
    selectedMode: DraftMode,
    onModeSelected: (DraftMode) -> Unit,
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

        // Mode selector
        SectionLabel(stringResource(R.string.draft_sim_mode_label))
        Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
            FilterChip(
                selected = selectedMode == DraftMode.DRAFT,
                onClick = { onModeSelected(DraftMode.DRAFT) },
                label = { Text(stringResource(R.string.draft_sim_mode_draft)) },
                shape = ChipShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = mc.primaryAccent.copy(alpha = 0.18f),
                    selectedLabelColor = mc.primaryAccent,
                    labelColor = mc.textSecondary,
                ),
            )
            FilterChip(
                selected = selectedMode == DraftMode.SEALED,
                onClick = { onModeSelected(DraftMode.SEALED) },
                label = { Text(stringResource(R.string.draft_sim_mode_sealed)) },
                shape = ChipShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = mc.primaryAccent.copy(alpha = 0.18f),
                    selectedLabelColor = mc.primaryAccent,
                    labelColor = mc.textSecondary,
                ),
            )
        }

        // Seat info (fixed)
        Surface(shape = CardShape, color = mc.surface, tonalElevation = 1.dp) {
            Text(
                text = stringResource(R.string.draft_sim_players_label),
                style = ty.bodyMedium,
                color = mc.textPrimary,
                modifier = Modifier.padding(sp.md),
            )
        }

        // Timer (only meaningful for Draft mode; Sealed has no per-pick rounds)
        if (selectedMode == DraftMode.DRAFT) {
            SectionLabel(stringResource(R.string.draft_sim_timer_label))
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
                    modifier = Modifier.width(48.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onStart,
            enabled = startEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(bottom = sp.lg),
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
        ) {
            Text(
                text = stringResource(R.string.draft_sim_start_button),
                style = ty.labelLarge,
                color = mc.background,
                fontWeight = FontWeight.Bold,
            )
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
