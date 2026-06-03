package com.mmg.manahub.feature.tournament.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.data.local.entity.TournamentMatchEntity
import com.mmg.manahub.core.data.local.entity.TournamentPlayerEntity
import com.mmg.manahub.core.data.local.entity.projection.TournamentStanding
import com.mmg.manahub.core.ui.components.HexGridBackground
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import java.util.Locale

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
    var selectedTab  by rememberSaveable { mutableIntStateOf(0) }
    var recordResultForMatch by remember { mutableStateOf<TournamentMatchEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(mc.background)) {
        HexGridBackground(modifier = Modifier.fillMaxSize(), color = mc.primaryAccent.copy(alpha = 0.05f))

        Scaffold(
            contentWindowInsets = WindowInsets.statusBars,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text  = uiState.tournament?.name ?: stringResource(R.string.tournament_default_name),
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
                    actions = {
                        if (!uiState.isFinished && !uiState.isPaused) {
                            IconButton(onClick = { viewModel.pause() }) {
                                Icon(
                                    Icons.Default.Pause,
                                    contentDescription = stringResource(R.string.tournament_pause_cd),
                                    tint = mc.textSecondary,
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            containerColor = Color.Transparent,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // ── Tab row ───────────────────────────────────────────────────────
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor   = mc.backgroundSecondary.copy(alpha = 0.8f),
                    contentColor     = mc.primaryAccent,
                    indicator        = { tabPositions ->
                        @Suppress("DEPRECATION")
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color    = mc.primaryAccent,
                        )
                    },
                    divider = {}
                ) {
                    listOf(stringResource(R.string.tournament_tab_standings), stringResource(R.string.tournament_tab_matches)).forEachIndexed { i, title ->
                        Tab(
                            selected = selectedTab == i,
                            onClick  = { selectedTab = i },
                            text     = {
                                Text(
                                    text  = title.uppercase(Locale.getDefault()),
                                    style = MaterialTheme.magicTypography.labelLarge,
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
                        activeMatch     = uiState.activeMatch,
                        onStartNextMatch = {
                            viewModel.startNextMatch { matchId ->
                                onStartMatch(matchId, tournamentId)
                            }
                        },
                        onResumeActiveMatch = { matchId ->
                            viewModel.resumeMatch(matchId) { id ->
                                onStartMatch(id, tournamentId)
                            }
                        },
                        mc = mc
                    )
                    1 -> MatchesTab(
                        matches         = uiState.matches,
                        players         = uiState.players,
                        onStartMatch    = { matchId ->
                            viewModel.startMatch(matchId) { _ ->
                                onStartMatch(matchId, tournamentId)
                            }
                        },
                        onResumeMatch   = { matchId ->
                            viewModel.resumeMatch(matchId) { id ->
                                onStartMatch(id, tournamentId)
                            }
                        },
                        onResetMatch    = { matchId -> viewModel.resetMatch(matchId) },
                        onRecordResult  = { match -> recordResultForMatch = match },
                        mc = mc
                    )
                }
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
            onDraw    = {
                viewModel.recordDrawManual(match.id)
                recordResultForMatch = null
            },
            onDismiss = { recordResultForMatch = null },
        )
    }
}

// ── Standings tab ──────────────────────────────────────────────────────────────

@Composable
private fun StandingsTab(
    standings:           List<TournamentStanding>,
    isFinished:          Boolean,
    nextMatch:           TournamentMatchEntity?,
    activeMatch:         TournamentMatchEntity?,
    onStartNextMatch:    () -> Unit,
    onResumeActiveMatch: (Long) -> Unit,
    mc:                  com.mmg.manahub.core.ui.theme.MagicColors
) {
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
                    shape    = RoundedCornerShape(20.dp),
                    color    = playerColor.copy(alpha = 0.15f),
                    border   = BorderStroke(2.dp, playerColor.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Background Decoration
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = playerColor.copy(alpha = 0.1f),
                            modifier = Modifier
                                .size(140.dp)
                                .align(Alignment.CenterEnd)
                                .padding(end = 16.dp)
                        )

                        Column(
                            modifier            = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = mc.goldMtg,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.tournament_winner_label).uppercase(),
                                style = MaterialTheme.magicTypography.labelLarge,
                                color = mc.goldMtg,
                            )
                            Text(
                                text  = winner.player.playerName,
                                style = MaterialTheme.magicTypography.displayMedium,
                                color = mc.textPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                color = playerColor.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text  = "${winner.wins}W · ${winner.losses}L · ${winner.draws}D · ${winner.points} pts",
                                    style = MaterialTheme.magicTypography.bodyMedium,
                                    color = mc.textPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active match resume banner (soft-lock recovery)
        if (activeMatch != null) {
            item {
                Surface(
                    shape    = RoundedCornerShape(16.dp),
                    color    = mc.primaryAccent.copy(alpha = 0.12f),
                    border   = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier              = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(mc.primaryAccent)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.tournament_resume_match),
                                style = MaterialTheme.magicTypography.bodyMedium,
                                color = mc.textPrimary,
                            )
                        }
                        Button(
                            onClick  = { onResumeActiveMatch(activeMatch.id) },
                            colors   = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                            shape    = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.tournament_match_play), style = MaterialTheme.magicTypography.labelMedium)
                        }
                    }
                }
            }
        }

        // Next match button (only when no active match)
        if (!isFinished && nextMatch != null && activeMatch == null) {
            item {
                Button(
                    onClick  = onStartNextMatch,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.tournament_start_next), style = MaterialTheme.magicTypography.labelLarge)
                }
            }
        }

        // Table header
        item {
            Surface(
                color = mc.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Row(
                    modifier              = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.tournament_standings_header_player).uppercase(),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textDisabled
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        listOf(
                            stringResource(R.string.tournament_standings_w),
                            stringResource(R.string.tournament_standings_d),
                            stringResource(R.string.tournament_standings_l),
                            stringResource(R.string.tournament_standings_pts),
                        ).forEach { col ->
                            Text(
                                col.uppercase(),
                                style = MaterialTheme.magicTypography.labelSmall,
                                color = mc.textDisabled,
                                modifier = Modifier.width(20.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Standing rows
        itemsIndexed(standings, key = { _, s -> s.player.id }) { index, standing ->
            StandingRow(
                standing = standing,
                isFirst  = index == 0,
                isLast   = index == standings.size - 1
            )
        }
    }
}

@Composable
private fun StandingRow(
    standing: TournamentStanding,
    isFirst: Boolean,
    isLast: Boolean
) {
    val mc          = MaterialTheme.magicColors
    val playerColor = parseColor(standing.player.playerColor)

    Surface(
        shape    = RoundedCornerShape(
            bottomStart = if (isLast) 12.dp else 0.dp,
            bottomEnd   = if (isLast) 12.dp else 0.dp
        ),
        color    = if (standing.position == 1) playerColor.copy(alpha = 0.08f) else mc.surface.copy(alpha = 0.6f),
        border   = if (standing.position == 1) BorderStroke(0.5.dp, playerColor.copy(alpha = 0.3f)) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier              = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier              = Modifier.weight(1f),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.width(28.dp)) {
                        Text(
                            text     = when (standing.position) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "${standing.position}" },
                            fontSize = if (standing.position <= 3) 20.sp else 16.sp,
                            color    = if (standing.position <= 3) Color.Unspecified else mc.textDisabled
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(playerColor),
                    )
                    Text(
                        text     = standing.player.playerName,
                        style    = MaterialTheme.magicTypography.bodyLarge,
                        color    = if (standing.position == 1) mc.textPrimary else mc.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        "${standing.wins}",
                        style = MaterialTheme.magicTypography.bodyMedium,
                        color = mc.lifePositive,
                        modifier = Modifier.width(20.dp),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "${standing.draws}",
                        style = MaterialTheme.magicTypography.bodyMedium,
                        color = mc.textSecondary,
                        modifier = Modifier.width(20.dp),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "${standing.losses}",
                        style = MaterialTheme.magicTypography.bodyMedium,
                        color = mc.lifeNegative,
                        modifier = Modifier.width(20.dp),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text       = "${standing.points}",
                        style      = MaterialTheme.magicTypography.bodyLarge,
                        color      = if (standing.position == 1) mc.primaryAccent else mc.textPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(20.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (!isLast) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = mc.surfaceVariant.copy(alpha = 0.3f)
                )
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
    onResumeMatch:  (Long) -> Unit,
    onResetMatch:   (Long) -> Unit,
    onRecordResult: (TournamentMatchEntity) -> Unit,
    mc:             com.mmg.manahub.core.ui.theme.MagicColors
) {
    val playerMap = remember(players) { players.associateBy { it.id } }
    val byRound   = remember(matches) { matches.groupBy { it.round }.toSortedMap() }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        byRound.forEach { (round, roundMatches) ->
            item(key = "round_$round") {
                Surface(
                    color = mc.primaryAccent.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        stringResource(R.string.tournament_round_label, round).uppercase(),
                        style    = MaterialTheme.magicTypography.labelMedium,
                        color    = mc.primaryAccent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
            items(roundMatches, key = { it.id }) { match ->
                MatchRow(
                    match          = match,
                    playerMap      = playerMap,
                    onStart        = if (match.status == "PENDING") ({ onStartMatch(match.id) }) else null,
                    onResume       = if (match.status == "ACTIVE") ({ onResumeMatch(match.id) }) else null,
                    onReset        = if (match.status == "ACTIVE") ({ onResetMatch(match.id) }) else null,
                    onRecordResult = if (match.status == "PENDING") ({ onRecordResult(match) }) else null,
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
    onResume:       (() -> Unit)?,
    onReset:        (() -> Unit)?,
    onRecordResult: (() -> Unit)?,
) {
    val mc  = MaterialTheme.magicColors
    val ty  = MaterialTheme.magicTypography
    val ids = match.playerIds.trim('[', ']').split(",").mapNotNull { it.trim().toLongOrNull() }
    val isBye = ids.size == 1
    val p1  = playerMap[ids.getOrNull(0)]
    val p2  = if (isBye) null else playerMap[ids.getOrNull(1)]

    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = when (match.status) {
            "FINISHED" -> mc.surface.copy(alpha = 0.4f)
            "ACTIVE"   -> mc.primaryAccent.copy(alpha = 0.08f)
            else       -> mc.surface.copy(alpha = 0.7f)
        },
        border   = BorderStroke(
            width = if (match.status == "ACTIVE") 1.dp else 0.5.dp,
            color = when (match.status) {
                "ACTIVE" -> mc.primaryAccent
                else     -> mc.surfaceVariant
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier              = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                PlayerMatchSlot(player = p1, match = match, isP1 = true, modifier = Modifier.weight(1f))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier            = Modifier.padding(horizontal = 16.dp),
                ) {
                    val vsIconColor = when (match.status) {
                        "FINISHED" -> mc.lifePositive.copy(alpha = 0.5f)
                        "ACTIVE"   -> mc.primaryAccent
                        else       -> mc.textDisabled
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(vsIconColor.copy(alpha = 0.1f))
                            .border(1.dp, vsIconColor.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text  = when {
                                isBye -> "BYE"
                                match.status == "FINISHED" && match.winnerId == null -> "="
                                match.status == "FINISHED" -> "✓"
                                match.status == "ACTIVE"   -> "●"
                                else                       -> "VS"
                            },
                            style = ty.labelMedium,
                            color = vsIconColor,
                            fontSize = if (isBye) 10.sp else 12.sp
                        )
                    }

                    if (onStart != null) {
                        Spacer(Modifier.height(8.dp))
                        IconButton(
                            onClick = onStart,
                            modifier = Modifier.size(32.dp).background(mc.primaryAccent, CircleShape)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (onResume != null) {
                        Spacer(Modifier.height(8.dp))
                        IconButton(
                            onClick = onResume,
                            modifier = Modifier.size(32.dp).background(mc.primaryAccent, CircleShape)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                PlayerMatchSlot(player = p2, match = match, isP1 = false, modifier = Modifier.weight(1f))
            }

            // Action row
            if (onRecordResult != null || onReset != null) {
                HorizontalDivider(thickness = 0.5.dp, color = mc.surfaceVariant.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (onRecordResult != null && match.status == "PENDING") {
                        TextButton(
                            onClick        = onRecordResult,
                            modifier       = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text  = stringResource(R.string.tournament_record_result_manual),
                                style = ty.labelSmall,
                                color = mc.textSecondary,
                            )
                        }
                    }
                    if (onReset != null && match.status == "ACTIVE") {
                        TextButton(
                            onClick        = onReset,
                            modifier       = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text(
                                text  = stringResource(R.string.tournament_reset_match),
                                style = ty.labelSmall,
                                color = mc.lifeNegative.copy(alpha = 0.8f),
                            )
                        }
                    }
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
    onDraw:    () -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = mc.surface,
        title = {
            Text(
                text  = stringResource(R.string.tournament_who_won),
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
                            color = Color.White,
                        )
                    }
                }
                OutlinedButton(
                    onClick  = onDraw,
                    modifier = Modifier.fillMaxWidth(),
                    border   = BorderStroke(1.dp, mc.textSecondary.copy(alpha = 0.5f)),
                ) {
                    Text(
                        text  = stringResource(R.string.tournament_match_draw),
                        style = MaterialTheme.magicTypography.labelLarge,
                        color = mc.textSecondary,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = mc.textSecondary)
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
    val ty          = MaterialTheme.magicTypography
    val playerColor = parseColor(player?.playerColor ?: if (isP1) "#E63946" else "#4CC9F0")
    val isWinner    = match.winnerId == player?.id
    val isDraw      = match.status == "FINISHED" && match.winnerId == null

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(playerColor)
                .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text     = player?.playerName ?: "BYE",
            style    = if (isWinner) ty.bodyLarge else ty.bodyMedium,
            color    = if (isWinner) mc.lifePositive else mc.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal
        )
        if (match.status == "FINISHED") {
            Text(
                text  = when {
                    isWinner -> stringResource(R.string.tournament_match_win).uppercase()
                    isDraw   -> stringResource(R.string.tournament_match_draw).uppercase()
                    else     -> ""
                },
                style = ty.labelSmall,
                color = if (isWinner) mc.lifePositive else mc.textDisabled,
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    Color(0xFFC77DFF)
}
