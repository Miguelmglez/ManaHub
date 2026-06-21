package com.mmg.manahub.feature.tournament.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.mmg.manahub.R
import com.mmg.manahub.core.data.local.entity.TournamentEntity
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.HexGridBackground
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentListScreen(
    onNavigateBack:     () -> Unit,
    onCreateTournament: () -> Unit,
    onOpenTournament:   (Long) -> Unit,
    viewModel:          TournamentListViewModel = koinViewModel(),
) {
    val tournaments by viewModel.tournaments.collectAsStateWithLifecycle()
    val mc           = MaterialTheme.magicColors

    Box(modifier = Modifier.fillMaxSize().background(mc.background)) {
        HexGridBackground(modifier = Modifier.fillMaxSize(), color = mc.primaryAccent.copy(alpha = 0.05f))

        Scaffold(
            contentWindowInsets = WindowInsets.statusBars,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.tournament_list_title),
                            style = MaterialTheme.magicTypography.titleLarge,
                            color = mc.textPrimary,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                tint = mc.textPrimary,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onCreateTournament) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.tournament_new_cd),
                                tint = mc.primaryAccent,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            containerColor = Color.Transparent,
        ) { padding ->
            if (tournaments.isEmpty()) {
                EmptyState(
                    icon        = Icons.Default.EmojiEvents,
                    title       = stringResource(R.string.tournament_empty_title),
                    actionLabel = stringResource(R.string.tournament_empty_action),
                    onAction    = onCreateTournament,
                    modifier    = Modifier.fillMaxSize().padding(padding),
                )
            } else {
                val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                LazyColumn(
                    modifier            = Modifier.fillMaxSize().padding(padding),
                    contentPadding      = PaddingValues(
                        start  = MaterialTheme.spacing.lg,
                        top    = MaterialTheme.spacing.lg,
                        end    = MaterialTheme.spacing.lg,
                        bottom = MaterialTheme.spacing.lg + navBarBottom,
                    ),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
                ) {
                    itemsIndexed(tournaments, key = { _, t -> t.id }) { index, tournament ->
                        var visible by remember(tournament.id) { mutableStateOf(false) }
                        LaunchedEffect(tournament.id) { visible = true }
                        val delay = (index % 10) * 40
                        AnimatedVisibility(
                            visible = visible,
                            enter   = fadeIn(tween(400, delayMillis = delay)) +
                                      scaleIn(tween(400, delayMillis = delay), initialScale = 0.95f),
                        ) {
                            TournamentListItem(
                                tournament = tournament,
                                onClick    = { onOpenTournament(tournament.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TournamentListItem(
    tournament: TournamentEntity,
    onClick:    () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val statusColor = when (tournament.status) {
        "ACTIVE"   -> mc.primaryAccent
        "FINISHED" -> mc.lifePositive
        "PAUSED"   -> mc.goldMtg
        else       -> mc.textDisabled
    }
    val statusIcon = when (tournament.status) {
        "ACTIVE"   -> Icons.Default.PlayArrow
        "FINISHED" -> Icons.Default.Check
        else       -> null
    }
    val structureIcon = when (tournament.structure) {
        "ROUND_ROBIN" -> "⟳"
        "SWISS"       -> "♟"
        "SINGLE_ELIM" -> "⚔"
        else          -> "🏆"
    }

    Surface(
        shape    = CardShape,
        color    = mc.surface.copy(alpha = 0.8f),
        border   = BorderStroke(0.5.dp, mc.surfaceVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier              = Modifier.padding(MaterialTheme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            // Structure Icon Circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(mc.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text(structureIcon, fontSize = 20.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = tournament.name,
                    style = ty.bodyLarge,
                    color = mc.textPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text  = tournament.format,
                        style = ty.labelSmall,
                        color = mc.primaryAccent,
                    )
                    Text(
                        text  = " · ${tournamentStructureLabel(tournament.structure)}",
                        style = ty.bodySmall,
                        color = mc.textSecondary,
                    )
                }
            }

            // Status Chip
            Surface(
                color = statusColor.copy(alpha = 0.12f),
                shape = ChipShape,
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = MaterialTheme.spacing.sm,
                        vertical   = MaterialTheme.spacing.xs,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
                ) {
                    if (statusIcon != null) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text  = tournamentStatusLabel(tournament.status),
                        style = ty.labelSmall,
                        color = statusColor,
                    )
                }
            }
        }
    }
}

/**
 * Maps a raw tournament status code to a localized label, falling back to the raw code
 * for any unrecognized value.
 */
@Composable
internal fun tournamentStatusLabel(status: String): String = when (status) {
    "ACTIVE"   -> stringResource(R.string.tournament_status_active)
    "FINISHED" -> stringResource(R.string.tournament_status_finished)
    "PAUSED"   -> stringResource(R.string.tournament_status_paused)
    "PENDING"  -> stringResource(R.string.tournament_status_pending)
    else       -> status
}

/**
 * Maps a raw tournament structure code to a short localized label, falling back to a
 * human-readable form of the raw code for any unrecognized value.
 */
@Composable
internal fun tournamentStructureLabel(structure: String): String = when (structure) {
    "ROUND_ROBIN" -> stringResource(R.string.tournament_structure_round_robin_short)
    "SWISS"       -> stringResource(R.string.tournament_structure_swiss_short)
    "SINGLE_ELIM" -> stringResource(R.string.tournament_structure_single_elim_short)
    else          -> structure.replace("_", " ")
}
