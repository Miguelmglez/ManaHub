package com.mmg.manahub.feature.tournament

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.data.local.entity.TournamentMatchEntity
import com.mmg.manahub.core.data.local.entity.TournamentPlayerEntity
import com.mmg.manahub.core.data.local.entity.projection.TournamentStanding
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentScreen(
    tournamentId:   Long,
    onNavigateBack: () -> Unit,
    onStartMatch:   (matchId: Long, tournamentId: Long) -> Unit,
    viewModel:      TournamentViewModel = hiltViewModel(),
) {
    val uiState     by viewModel.uiState.collectAsStateWithLifecycle()
    val mc           = MaterialTheme.magicColors
    var selectedTab  by remember { mutableIntStateOf(0) }
    var recordResultForMatch by remember { mutableStateOf<TournamentMatchEntity?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text  = uiState.tournament?.name ?: "Tournament",
                        style = MaterialTheme.magicTypography.titleMedium,
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.backgroundSecondary),
            )
        },
        containerColor = mc.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Tab row ───────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = mc.backgroundSecondary,
                contentColor     = mc.primaryAccent,
                indicator        = { tabPositions ->
                    @Suppress("DEPRECATION")
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color    = mc.primaryAccent,
                    )
                },
            ) {
                listOf(stringResource(R.string.tournament_tab_standings), stringResource(R.string.tournament_tab_matches)).forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick  = { selectedTab = i },
                        text     = {
                            Text(
                                text  = title,
                                style = MaterialTheme.magicTypography.labelMedium,
                                color = if (selectedTab == i) mc.primaryAccent else mc.textDisabled,
                            )
                        },
                    )
                }
            }

            when (selectedTab) {
                0 -> StandingsTab(
                    standings       = uiState.standings,
                    isFinished      = uiState.isFinished,
                    nextMatch       = uiState.nextMatch,
                    onStartNextMatch = {
                        viewModel.startNextMatch { matchId ->
                            onStartMatch(matchId, tournamentId)
                        }
                    },
                )
                1 -> MatchesTab(
                    matches         = uiState.matches,
                    players         = uiState.players,
                    onStartMatch    = { matchId ->
                        viewModel.startMatch(matchId) { _ ->
                            onStartMatch(matchId, tournamentId)
                        }
                    },
                    onRecordResult  = { match -> recordResultForMatch = match },
                )
            }
        }
    }

    // Manual result entry dialog
    recordResultForMatch?.let { match ->
        val ids = match.playerIds.trim('[', ']').split(",").mapNotNull { it.trim().toLongOrNull() }
        val p1  = uiState.players.find { it.id == ids.getOrNull(0) }
        val p2  = uiState.players.find { it.id == ids.getOrNull(1) }
        RecordResultDialog(
            p1        = p1,
            p2        = p2,
            onConfirm = { winnerId ->
                viewModel.recordMatchResultManual(match.id, winnerId)
                recordResultForMatch = null
            },
            onDismiss = { recordResultForMatch = null },
        )
    }
}

// ── Standings tab ──────────────────────────────────────────────────────────────

@Composable
private fun StandingsTab(
    standings:        List<TournamentStanding>,
    isFinished:       Boolean,
    nextMatch:        TournamentMatchEntity?,
    onStartNextMatch: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Winner banner
        if (isFinished && standings.isNotEmpty()) {
            item {
                val winner      = standings.first()
                val playerColor = parseColor(winner.player.playerColor)
                Surface(
                    shape    = RoundedCornerShape(16.dp),
                    color    = playerColor.copy(alpha = 0.15f),
                    border   = BorderStroke(1.5.dp, playerColor.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier            = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.tournament_winner_label),
                            style = MaterialTheme.magicTypography.labelLarge,
                            color = mc.goldMtg,
                        )
                        Text(
                            text  = winner.player.playerName,
                            style = MaterialTheme.magicTypography.displayMedium,
                            color = playerColor,
                        )
                        Text(
                            text  = "${winner.wins}W · ${winner.losses}L · ${winner.points} pts",
                            style = MaterialTheme.magicTypography.bodyMedium,
                            color = mc.textSecondary,
                        )
                    }
                }
            }
        }

        // Next match button
        if (!isFinished && nextMatch != null) {
            item {
                Button(
                    onClick  = onStartNextMatch,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                ) {
                    Text(stringResource(R.string.tournament_start_next), style = MaterialTheme.magicTypography.labelLarge)
                }
            }
        }

        // Table header
        item {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.tournament_standings_header_player), style = MaterialTheme.magicTypography.labelSmall, color = mc.textDisabled)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(stringResource(R.string.tournament_standings_w), stringResource(R.string.tournament_standings_l), stringResource(R.string.tournament_standings_pts), stringResource(R.string.tournament_standings_life)).forEach { col ->
                        Text(col, style = MaterialTheme.magicTypography.labelSmall, color = mc.textDisabled)
                    }
                }
            }
        }

        // Standing rows
        items(standings) { standing ->
            StandingRow(standing = standing)
        }
    }
}

@Composable
private fun StandingRow(standing: TournamentStanding) {
    val mc          = MaterialTheme.magicColors
    val playerColor = parseColor(standing.player.playerColor)

    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = if (standing.position == 1) playerColor.copy(alpha = 0.1f) else mc.surface,
        border   = if (standing.position == 1) BorderStroke(1.dp, playerColor.copy(alpha = 0.4f)) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier              = Modifier.weight(1f),
            ) {
                Text(
                    text     = when (standing.position) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "${standing.position}" },
                    fontSize = if (standing.position <= 3) 20.sp else 16.sp,
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(playerColor),
                )
                Text(
                    text     = standing.player.playerName,
                    style    = MaterialTheme.magicTypography.bodyMedium,
                    color    = if (standing.position == 1) playerColor else mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("${standing.wins}",   style = MaterialTheme.magicTypography.bodyMedium, color = mc.lifePositive)
                Text("${standing.losses}", style = MaterialTheme.magicTypography.bodyMedium, color = mc.lifeNegative)
                Text(
                    text       = "${standing.points}",
                    style      = MaterialTheme.magicTypography.bodyMedium,
                    color      = mc.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text("${standing.lifeTotal}", style = MaterialTheme.magicTypography.bodySmall, color = mc.textSecondary)
            }
        }
    }
}

// ── Matches tab ────────────────────────────────────────────────────────────────

@Composable
private fun MatchesTab(
    matches:        List<TournamentMatchEntity>,
    players:        List<TournamentPlayerEntity>,
    onStartMatch:   (Long) -> Unit,
    onRecordResult: (TournamentMatchEntity) -> Unit,
) {
    val playerMap = remember(players) { players.associateBy { it.id } }
    val byRound   = remember(matches) { matches.groupBy { it.round } }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        byRound.forEach { (round, roundMatches) ->
            item {
                Text(
                    stringResource(R.string.tournament_round_label, round),
                    style    = MaterialTheme.magicTypography.labelLarge,
                    color    = MaterialTheme.magicColors.textSecondary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            items(roundMatches) { match ->
                MatchRow(
                    match          = match,
                    playerMap      = playerMap,
                    onStart        = if (match.status == "PENDING") ({ onStartMatch(match.id) }) else null,
                    onRecordResult = if (match.status != "FINISHED") ({ onRecordResult(match) }) else null,
                )
            }
        }
    }
}

@Composable
private fun MatchRow(
    match:          TournamentMatchEntity,
    playerMap:      Map<Long, TournamentPlayerEntity>,
    onStart:        (() -> Unit)?,
    onRecordResult: (() -> Unit)?,
) {
    val mc  = MaterialTheme.magicColors
    val ids = match.playerIds.trim('[', ']').split(",").mapNotNull { it.trim().toLongOrNull() }
    val p1  = playerMap[ids.getOrNull(0)]
    val p2  = playerMap[ids.getOrNull(1)]

    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = when (match.status) {
            "FINISHED" -> mc.surface.copy(alpha = 0.5f)
            "ACTIVE"   -> mc.primaryAccent.copy(alpha = 0.08f)
            else       -> mc.surface
        },
        border   = BorderStroke(
            width = 0.5.dp,
            color = when (match.status) {
                "ACTIVE" -> mc.primaryAccent.copy(alpha = 0.5f)
                else     -> mc.surfaceVariant
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier              = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                PlayerMatchSlot(player = p1, match = match, isP1 = true, modifier = Modifier.weight(1f))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier            = Modifier.padding(horizontal = 12.dp),
                ) {
                    Text(
                        text  = when (match.status) { "FINISHED" -> "✓"; "ACTIVE" -> "●"; else -> stringResource(R.string.tournament_match_vs) },
                        style = MaterialTheme.magicTypography.titleMedium,
                        color = when (match.status) {
                            "FINISHED" -> mc.lifePositive.copy(alpha = 0.6f)
                            "ACTIVE"   -> mc.primaryAccent
                            else       -> mc.textDisabled
                        },
                    )
                    if (onStart != null) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick        = onStart,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(stringResource(R.string.tournament_match_play), style = MaterialTheme.magicTypography.labelSmall, color = mc.primaryAccent)
                        }
                    }
                }

                PlayerMatchSlot(player = p2, match = match, isP1 = false, modifier = Modifier.weight(1f))
            }

            // Record result button for unfinished matches
            if (onRecordResult != null && match.status != "FINISHED") {
                HorizontalDivider(thickness = 0.5.dp, color = mc.surfaceVariant)
                TextButton(
                    onClick        = onRecordResult,
                    modifier       = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text  = "Record result manually",
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordResultDialog(
    p1:        TournamentPlayerEntity?,
    p2:        TournamentPlayerEntity?,
    onConfirm: (winnerId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = mc.surface,
        title = {
            Text(
                text  = "Who won?",
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOfNotNull(p1, p2).forEach { player ->
                    val playerColor = parseColor(player.playerColor)
                    Button(
                        onClick  = { onConfirm(player.id) },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(containerColor = playerColor.copy(alpha = 0.85f)),
                    ) {
                        Text(
                            text  = player.playerName,
                            style = MaterialTheme.magicTypography.labelLarge,
                            color = androidx.compose.ui.graphics.Color.White,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = mc.textSecondary)
            }
        },
    )
}

@Composable
private fun PlayerMatchSlot(
    player:   TournamentPlayerEntity?,
    match:    TournamentMatchEntity,
    isP1:     Boolean,
    modifier: Modifier = Modifier,
) {
    val mc          = MaterialTheme.magicColors
    val playerColor = parseColor(player?.playerColor ?: if (isP1) "#E63946" else "#4CC9F0")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(playerColor),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text     = player?.playerName ?: "?",
            style    = MaterialTheme.magicTypography.bodyMedium,
            color    = if (match.winnerId == player?.id) mc.lifePositive else mc.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (match.winnerId == player?.id) {
            Text("WIN", style = MaterialTheme.magicTypography.labelSmall, color = mc.lifePositive)
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    Color(0xFFC77DFF)
}
