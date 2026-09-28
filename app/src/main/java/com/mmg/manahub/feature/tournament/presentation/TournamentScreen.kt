package com.mmg.manahub.feature.tournament.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Edit
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.MagicLoadingFooter
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.model.TournamentMatch
import com.mmg.manahub.core.model.TournamentPlayer
import com.mmg.manahub.core.model.TournamentStanding
import com.mmg.manahub.core.ui.components.HexGridBackground
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardCornerRadius
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.tournament.domain.engine.TournamentIdCodec
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentScreen(
    tournamentId:   Long,
    onNavigateBack: () -> Unit,
    onStartMatch:   (matchId: Long, tournamentId: Long) -> Boolean,
    viewModel:      TournamentViewModel = koinViewModel(),
) {
    val uiState     by viewModel.uiState.collectAsStateWithLifecycle()
    val toastState   = rememberMagicToastState()
    val pausedMsg    = stringResource(R.string.tournament_paused_toast)
    // A paused tournament accepts no match or result action until it is explicitly resumed
    val guardPaused: (() -> Unit) -> Unit = { action ->
        if (uiState.isPaused) toastState.show(pausedMsg, MagicToastType.INFO) else action()
    }
    // Returning from the game screen releases the launch guard so Start/Resume work again
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onGameNavigationConsumed() }

    val actionFailedMsg = stringResource(R.string.tournament_action_failed)
    val alreadyRecordedMsg = stringResource(R.string.tournament_result_already_recorded)
    LaunchedEffect(Unit) {
        viewModel.notices.collect { notice ->
            val (msg, type) = when (notice) {
                TournamentNotice.ACTION_FAILED -> actionFailedMsg to MagicToastType.ERROR
                TournamentNotice.RESULT_ALREADY_RECORDED -> alreadyRecordedMsg to MagicToastType.INFO
            }
            toastState.show(msg, type)
        }
    }
    val mc           = MaterialTheme.magicColors
    var selectedTab  by rememberSaveable { mutableIntStateOf(0) }
    var recordResultForMatch by remember { mutableStateOf<TournamentMatch?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(mc.background)) {
        HexGridBackground(modifier = Modifier.fillMaxSize(), color = mc.primaryAccent.copy(alpha = 0.05f))

        Scaffold(
            contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.navigationBars),
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
                        if (!uiState.isFinished) {
                            if (uiState.isPaused) {
                                IconButton(onClick = { viewModel.resumeTournament() }) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = stringResource(R.string.tournament_resume_cd),
                                        tint = mc.primaryAccent,
                                    )
                                }
                            } else {
                                IconButton(onClick = { viewModel.pause() }) {
                                    Icon(
                                        Icons.Default.Pause,
                                        contentDescription = stringResource(R.string.tournament_pause_cd),
                                        tint = mc.textSecondary,
                                    )
                                }
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
                                    color = if (selectedTab == i) mc.primaryAccent else mc.textSecondary,
                                )
                            },
                        )
                    }
                }

                when {
                    uiState.isLoading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        MagicLoadingFooter(label = stringResource(R.string.tournament_loading))
                    }
                    uiState.tournament == null -> FullErrorState(
                        message = stringResource(R.string.tournament_load_failed),
                        retryLabel = stringResource(R.string.action_back),
                        onRetry = onNavigateBack,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> when (selectedTab) {
                    0 -> StandingsTab(
                        standings       = uiState.standings,
                        isFinished      = uiState.isFinished,
                        nextMatch       = uiState.nextMatch,
                        activeMatch     = uiState.activeMatch,
                        onStartNextMatch = {
                            guardPaused {
                                viewModel.startNextMatch { matchId ->
                                    onStartMatch(matchId, tournamentId)
                                }
                            }
                        },
                        onResumeActiveMatch = { matchId ->
                            guardPaused {
                                viewModel.resumeMatch(matchId) { id ->
                                    onStartMatch(id, tournamentId)
                                }
                            }
                        },
                        mc = mc
                    )
                    else -> MatchesTab(
                        matches         = uiState.matches,
                        players         = uiState.players,
                        activeMatch     = uiState.activeMatch,
                        onStartMatch    = { matchId ->
                            guardPaused {
                                viewModel.startMatch(matchId) { _ ->
                                    onStartMatch(matchId, tournamentId)
                                }
                            }
                        },
                        onResumeMatch   = { matchId ->
                            guardPaused {
                                viewModel.resumeMatch(matchId) { id ->
                                    onStartMatch(id, tournamentId)
                                }
                            }
                        },
                        onResetMatch    = { matchId -> guardPaused { viewModel.resetMatch(matchId) } },
                        onRecordResult  = { match -> guardPaused { recordResultForMatch = match } },
                        mc = mc
                    )
                    }
                }
            }
        }

        MagicToastHost(state = toastState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    // Manual result entry dialog
    recordResultForMatch?.let { match ->
        val ids = TournamentIdCodec.decodeIds(match.playerIds)
        val p1  = uiState.players.find { it.id == ids.getOrNull(0) }
        val p2  = uiState.players.find { it.id == ids.getOrNull(1) }
        // M5 (documented constraint): tournaments are effectively 1v1 — Swiss / Single-Elim / Round
        // Robin pairings are always 2-player ("[a,b]"), so a "Commander tournament" is 1v1 Commander.
        // This dialog deliberately only exposes p1/p2; it does NOT support N-player pods. Generalising
        // to pods would require multiplayer pairing generation that does not exist today.
        RecordResultDialog(
            p1            = p1,
            p2            = p2,
            // M4: a draw in SINGLE_ELIM strands the bracket (no winner advances, never re-prompted),
            // so the Draw option is hidden for knockout. Other structures allow draws (1 point each).
            allowDraw     = uiState.tournament?.structure != "SINGLE_ELIM",
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
    nextMatch:           TournamentMatch?,
    activeMatch:         TournamentMatch?,
    onStartNextMatch:    () -> Unit,
    onResumeActiveMatch: (Long) -> Unit,
    mc:                  com.mmg.manahub.core.ui.theme.MagicColors
) {
    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(MaterialTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        // Winner banner
        if (isFinished && standings.isNotEmpty()) {
            item {
                val winner      = standings.first()
                val sharedFirst = standings.filter { it.isSharedFirst }
                val playerColor = parseColor(winner.player.playerColor)
                Surface(
                    shape    = CardShape,
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
                                .padding(end = MaterialTheme.spacing.lg)
                        )

                        Column(
                            modifier            = Modifier.padding(MaterialTheme.spacing.xl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = mc.goldMtg,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(MaterialTheme.spacing.sm))
                            Text(
                                text = if (sharedFirst.size > 1) {
                                    stringResource(R.string.tournament_shared_first_label).uppercase()
                                } else {
                                    stringResource(R.string.tournament_winner_label).uppercase()
                                },
                                style = MaterialTheme.magicTypography.labelLarge,
                                color = mc.textSecondary,
                            )
                            Text(
                                // Seed order alone separated a shared first place: name everyone, crown nobody
                                text  = if (sharedFirst.size > 1) {
                                    sharedFirst.joinToString(" · ") { it.player.playerName }
                                } else {
                                    winner.player.playerName
                                },
                                style = MaterialTheme.magicTypography.displayMedium,
                                color = mc.textPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(MaterialTheme.spacing.sm))
                            Surface(
                                color = playerColor.copy(alpha = 0.2f),
                                shape = ChipShape
                            ) {
                                Text(
                                    text  = "${winner.wins}W · ${winner.losses}L · ${winner.draws}D · ${winner.points} pts",
                                    style = MaterialTheme.magicTypography.bodyMedium,
                                    color = mc.textPrimary,
                                    modifier = Modifier.padding(
                                        horizontal = MaterialTheme.spacing.md,
                                        vertical   = MaterialTheme.spacing.xs,
                                    )
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
                    shape    = CardShape,
                    color    = mc.primaryAccent.copy(alpha = 0.12f),
                    border   = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier              = Modifier.padding(
                            horizontal = MaterialTheme.spacing.lg,
                            vertical   = MaterialTheme.spacing.md,
                        ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(MaterialTheme.spacing.md)
                                    .clip(CircleShape)
                                    .background(mc.primaryAccent)
                            )
                            Spacer(Modifier.width(MaterialTheme.spacing.md))
                            Text(
                                stringResource(R.string.tournament_resume_match),
                                style = MaterialTheme.magicTypography.bodyMedium,
                                color = mc.textPrimary,
                            )
                        }
                        MagicCtaButton(
                            onClick = { onResumeActiveMatch(activeMatch.id) },
                            text = stringResource(R.string.tournament_match_play),
                            icon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                    }
                }
            }
        }

        // Next match button (only when no active match)
        if (!isFinished && nextMatch != null && activeMatch == null) {
            item {
                MagicCtaButton(
                    onClick = onStartNextMatch,
                    text = stringResource(R.string.tournament_start_next),
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Table header
        item {
            Surface(
                color = mc.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(topStart = CardCornerRadius, topEnd = CardCornerRadius),
                modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.sm)
            ) {
                Row(
                    modifier              = Modifier.padding(
                        horizontal = MaterialTheme.spacing.lg,
                        vertical   = MaterialTheme.spacing.sm,
                    ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.tournament_standings_header_player).uppercase(),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xl)) {
                        listOf(
                            stringResource(R.string.tournament_standings_w),
                            stringResource(R.string.tournament_standings_d),
                            stringResource(R.string.tournament_standings_l),
                            stringResource(R.string.tournament_standings_pts),
                        ).forEach { col ->
                            Text(
                                col.uppercase(),
                                style = MaterialTheme.magicTypography.labelSmall,
                                color = mc.textSecondary,
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
            bottomStart = if (isLast) CardCornerRadius else 0.dp,
            bottomEnd   = if (isLast) CardCornerRadius else 0.dp
        ),
        color    = if (standing.position == 1) playerColor.copy(alpha = 0.08f) else mc.surface.copy(alpha = 0.6f),
        border   = if (standing.position == 1) BorderStroke(0.5.dp, playerColor.copy(alpha = 0.3f)) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier              = Modifier.padding(
                    horizontal = MaterialTheme.spacing.lg,
                    vertical   = MaterialTheme.spacing.md,
                ),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
                    modifier              = Modifier.weight(1f),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.width(28.dp)) {
                        Text(
                            text     = when (standing.position) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "${standing.position}" },
                            fontSize = if (standing.position <= 3) 20.sp else 16.sp,
                            color    = if (standing.position <= 3) Color.Unspecified else mc.textSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(MaterialTheme.spacing.md)
                            .clip(CircleShape)
                            .background(playerColor),
                    )
                    Text(
                        text     = standing.player.playerName,
                        style    = MaterialTheme.magicTypography.bodyLarge,
                        color    = if (standing.position == 1) mc.textPrimary else mc.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xl),
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
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg),
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
    matches:        List<TournamentMatch>,

    players:        List<TournamentPlayer>,
    activeMatch:    TournamentMatch?,
    onStartMatch:   (Long) -> Unit,
    onResumeMatch:  (Long) -> Unit,
    onResetMatch:   (Long) -> Unit,
    onRecordResult: (TournamentMatch) -> Unit,
    mc:             com.mmg.manahub.core.ui.theme.MagicColors
) {
    val playerMap = remember(players) { players.associateBy { it.id } }
    val byRound   = remember(matches) { matches.groupBy { it.round }.toSortedMap() }

    if (matches.isEmpty()) {
        EmptyState(
            icon = Icons.Default.EmojiEvents,
            title = stringResource(R.string.tournament_no_matches_title),
            subtitle = stringResource(R.string.tournament_no_matches_subtitle),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(MaterialTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
    ) {
        byRound.forEach { (round, roundMatches) ->
            item(key = "round_$round") {
                Surface(
                    color = mc.primaryAccent.copy(alpha = 0.1f),
                    shape = ChipShape,
                    modifier = Modifier.padding(vertical = MaterialTheme.spacing.xs)
                ) {
                    Text(
                        stringResource(R.string.tournament_round_label, round).uppercase(),
                        style    = MaterialTheme.magicTypography.labelMedium,
                        color    = mc.primaryAccent,
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.spacing.md,
                            vertical   = MaterialTheme.spacing.xs,
                        ),
                    )
                }
            }
            items(roundMatches, key = { it.id }) { match ->
                MatchRow(
                    match          = match,
                    playerMap      = playerMap,
                    // Only one match can be in play: a PENDING row cannot start while another is ACTIVE
                    onStart        = if (match.status == "PENDING" && activeMatch == null) ({ onStartMatch(match.id) }) else null,
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
    match:          TournamentMatch,
    playerMap:      Map<Long, TournamentPlayer>,
    onStart:        (() -> Unit)?,
    onResume:       (() -> Unit)?,
    onReset:        (() -> Unit)?,
    onRecordResult: (() -> Unit)?,
) {
    val mc  = MaterialTheme.magicColors
    val ty  = MaterialTheme.magicTypography
    val ids = TournamentIdCodec.decodeIds(match.playerIds)
    val isBye = ids.size == 1
    val p1  = playerMap[ids.getOrNull(0)]
    val p2  = if (isBye) null else playerMap[ids.getOrNull(1)]

    Surface(
        shape    = CardShape,
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
                modifier              = Modifier.padding(
                    horizontal = MaterialTheme.spacing.lg,
                    vertical   = MaterialTheme.spacing.lg,
                ),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                PlayerMatchSlot(player = p1, match = match, isP1 = true, modifier = Modifier.weight(1f))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier            = Modifier.padding(horizontal = MaterialTheme.spacing.lg),
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
                        Spacer(Modifier.height(MaterialTheme.spacing.sm))
                        IconButton(onClick = onStart) {
                            Box(
                                modifier = Modifier.size(32.dp).background(mc.primaryAccent, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = stringResource(R.string.tournament_match_start_cd),
                                    tint = mc.onAccent,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    if (onResume != null) {
                        Spacer(Modifier.height(MaterialTheme.spacing.sm))
                        IconButton(onClick = onResume) {
                            Box(
                                modifier = Modifier.size(32.dp).background(mc.primaryAccent, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = stringResource(R.string.tournament_match_resume_cd),
                                    tint = mc.onAccent,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
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
                        MagicCtaButton(
                            onClick = onRecordResult,
                            text = stringResource(R.string.tournament_record_result_manual),
                            style = MagicCtaStyle.Ghost,
                            color = MagicCtaColor.Neutral,
                            icon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (onReset != null && match.status == "ACTIVE") {
                        MagicCtaButton(
                            onClick = onReset,
                            text = stringResource(R.string.tournament_reset_match),
                            style = MagicCtaStyle.Ghost,
                            color = MagicCtaColor.Error,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordResultDialog(
    p1:        TournamentPlayer?,
    p2:        TournamentPlayer?,
    allowDraw: Boolean,
    onConfirm: (winnerId: Long) -> Unit,
    onDraw:    () -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    var selectedWinner by remember { mutableStateOf<TournamentPlayer?>(null) }
    MagicAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.tournament_who_won),
        dismissLabel = stringResource(R.string.action_cancel),
        onDismiss = onDismiss,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                // Two-step: recording a result also advances the round, so it must not fire on one tap
                listOfNotNull(p1, p2).forEach { player ->
                    val playerColor = parseColor(player.playerColor)
                    val isSelected = selectedWinner?.id == player.id
                    // Tonal fill + player-coloured border + theme ink (CardTagChip pattern):
                    // mc.onAccent is paired with primaryAccent, not with a player colour
                    Surface(
                        onClick = { selectedWinner = player },
                        shape = ButtonShape,
                        color = playerColor.copy(alpha = if (isSelected) 0.28f else 0.12f),
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, playerColor),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text  = player.playerName,
                            style = MaterialTheme.magicTypography.labelLarge,
                            color = mc.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = MaterialTheme.spacing.md),
                        )
                    }
                }
                selectedWinner?.let { winner ->
                    MagicCtaButton(
                        onClick = { onConfirm(winner.id) },
                        text = stringResource(R.string.tournament_confirm_winner, winner.playerName),
                        color = MagicCtaColor.Success,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // M4: Draw is hidden for SINGLE_ELIM (a knockout draw strands the bracket).
                if (allowDraw) {
                    MagicCtaButton(
                        onClick = onDraw,
                        text = stringResource(R.string.tournament_match_draw),
                        style = MagicCtaStyle.Outlined,
                        color = MagicCtaColor.Neutral,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    )
}

@Composable
private fun PlayerMatchSlot(
    player:   TournamentPlayer?,
    match:    TournamentMatch,
    isP1:     Boolean,
    modifier: Modifier = Modifier,
) {
    val mc          = MaterialTheme.magicColors
    val ty          = MaterialTheme.magicTypography
    val playerColor = player?.playerColor?.let { parseColor(it) }
        ?: if (isP1) mc.lifeNegative else mc.primaryAccent
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
                .border(2.dp, mc.textPrimary.copy(alpha = 0.2f), CircleShape),
        )
        Spacer(Modifier.height(MaterialTheme.spacing.sm))
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

/**
 * Fallback color used when a stored player color string fails to parse. A neutral violet;
 * not theme-derived because [parseColor] is a pure non-composable util with no theme access.
 */

@Composable
private fun parseColor(hex: String): Color {
    // Fall back to the THEME's accent: a hardcoded violet clashed with 11 of the 12 palettes
    val fallback = MaterialTheme.magicColors.primaryAccent
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        fallback
    }
}
