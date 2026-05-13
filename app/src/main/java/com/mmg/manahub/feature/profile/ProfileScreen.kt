package com.mmg.manahub.feature.profile

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mmg.manahub.R
import com.mmg.manahub.core.data.local.dao.DeckStatsRow
import com.mmg.manahub.core.data.local.entity.GameSessionWithPlayers
import com.mmg.manahub.core.domain.model.Achievement
import com.mmg.manahub.core.domain.model.CollectionStats
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.theme.MarcellusFontFamily
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.presentation.AccountSection
import com.mmg.manahub.feature.auth.presentation.AuthUiState
import com.mmg.manahub.feature.auth.presentation.AuthViewModel
import com.mmg.manahub.feature.auth.presentation.LoginSheet
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    onSettingsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onFriendsClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionState by authViewModel.sessionState.collectAsStateWithLifecycle()
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    var showProfileEdit by remember { mutableStateOf(false) }
    var showFeedbackSheet by remember { mutableStateOf(false) }
    var showLoginSheet by remember { mutableStateOf(false) }
    var loginSheetInitialTab by remember { mutableIntStateOf(0) }
    var showAccountSheet by remember { mutableStateOf(false) }

    if (showProfileEdit) {
        ProfileEditSheet(
            onDismiss = { showProfileEdit = false },
            onNicknameUpdate = if (sessionState is SessionState.Authenticated) {
                authViewModel::updateNickname
            } else {
                null
            }
        )
    }

    if (showFeedbackSheet) {
        FeedbackSheet(onDismiss = { showFeedbackSheet = false })
    }

    if (showLoginSheet) {
        LoginSheet(
            authViewModel = authViewModel,
            initialTab = loginSheetInitialTab,
            initialNickname = uiState.playerName,
            initialAvatarUrl = uiState.avatarUrl,
            onDismiss = { showLoginSheet = false },
        )
    }

    if (showAccountSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAccountSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = mc.backgroundSecondary,
            dragHandle = { Spacer(modifier = Modifier.height(8.dp)) },
        ) {
            AccountSection(
                sessionState = sessionState,
                authUiState = authUiState,
                onLoginClick = {
                    showAccountSheet = false
                    loginSheetInitialTab = 0
                    showLoginSheet = true
                },
                onSignUpClick = {
                    showAccountSheet = false
                    loginSheetInitialTab = 1
                    showLoginSheet = true
                },
                onSignOutClick = { authViewModel.signOut() },
                onDeleteAccountClick = { authViewModel.deleteAccount() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                playerName = uiState.playerName,
                avatarUrl = uiState.avatarUrl,
            )
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }

    // Reset ui state after account deletion so the screen returns to Idle cleanly.
    // The sessionState Flow will automatically emit Unauthenticated after the account is removed.
    LaunchedEffect(authUiState) {
        if (authUiState is AuthUiState.AccountDeleted) {
            showAccountSheet = false
            authViewModel.resetUiState()
        }
    }

    Scaffold(
        containerColor = mc.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Surface(
                color = mc.backgroundSecondary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.profile_title),
                        style = MaterialTheme.magicTypography.titleLarge,
                        color = mc.textPrimary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Account",
                        tint = if (sessionState is SessionState.Authenticated) mc.primaryAccent else mc.textPrimary,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(24.dp)
                            .clickable { showAccountSheet = true },
                    )
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = mc.textPrimary,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(24.dp)
                            .clickable(onClick = onSettingsClick),
                    )

                }
            }
        },
    ) { padding ->
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp + navBarBottom),
        ) {

            // ── Hero ──────────────────────────────────────────────────────────
            item {
                ProfileHeroSection(
                    name = uiState.playerName,
                    avatarUrl = uiState.avatarUrl,
                    gameTag = (sessionState as? SessionState.Authenticated)?.user?.gameTag,
                    onEditClick = { showProfileEdit = true },
                )
            }

            // ── Friends ───────────────────────────────────────────────────────
            if (sessionState is SessionState.Authenticated){
                item {
                    FriendsSummaryRow(
                        friendCount = uiState.friendCount,
                        pendingCount = uiState.pendingFriendCount,
                        onClick = onFriendsClick,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // ── KPI grid ──────────────────────────────────────────────────────
            item {
                ProfileKpiSection(
                    uiState = uiState,
                    favouriteColor = uiState.favouriteColor,
                    mostValuableColor = uiState.mostValuableColor,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clickable { onStatsClick() },
                )
            }
            /*
                        // ── Best deck ─────────────────────────────────────────────────────
                        if (uiState.deckStats.isNotEmpty()) {
                            item {
                                BestDeckSection(
                                    deck = uiState.deckStats.first(),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                        }

                                    // ── Survey insights ───────────────────────────────────────────────
                                    if (uiState.surveyCount > 0) {
                                        item {
                                            SurveyInsightsSection(
                                                uiState = uiState,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                            )
                                        }
                                    }

                                                // ── Achievements ──────────────────────────────────────────────────
                                                if (uiState.achievements.isNotEmpty()) {
                                                    item {
                                                        AchievementsSection(
                                                            achievements = uiState.achievements,
                                                            modifier     = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                                        )
                                                    }
                                                }*/

            /* // ── Recent games ──────────────────────────────────────────────────
             if (uiState.recentSessions.isNotEmpty()) {
                 item {
                     SectionTitle(
                         text = stringResource(R.string.profile_recent_games),
                         modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
                     )
                 }
                 items(uiState.recentSessions) { session ->
                     RecentGameRow(
                         session = session,
                         playerName = uiState.playerName,
                         modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                     )
                 }
             }*/

            // ── Collection summary ────────────────────────────────────────────
            uiState.collectionStats?.let { stats ->
                item {
                    CollectionSummarySection(
                        stats = stats,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        viewModel = viewModel
                    )
                }
            }

            // ── Send feedback ─────────────────────────────────────────────────
            item {
                SendFeedbackRow(
                    onClick = { showFeedbackSheet = true },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // ── Footer ────────────────────────────────────────────────────────
            item {
                AppInfoFooter(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

// ── Hero section ──────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeroSection(
    name: String,
    avatarUrl: String?,
    /** Server-generated game tag (e.g. "#A3KX9Z"). Displayed as a badge next to the name. */
    gameTag: String? = null,
    onEditClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors

    // State to track the image's aspect ratio (defaults to 16:9)
    var imageRatio by remember(avatarUrl) { mutableFloatStateOf(1.77f) }

    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .fillMaxWidth()
            // Use a dynamic aspect ratio constrained to reasonable limits
            .aspectRatio(imageRatio.coerceIn(1.2f, 2.5f))
            .animateContentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onEditClick() },
    ) {
        // Background: planeswalker art or fallback gradient
        if (avatarUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                onSuccess = { state ->
                    val size = state.painter.intrinsicSize
                    if (size.width > 0 && size.height > 0) {
                        imageRatio = size.width / size.height
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                mc.primaryAccent.copy(alpha = 0.3f),
                                mc.background,
                            ),
                        ),
                    ),
            ) {
                ThemeBackground(modifier = Modifier.fillMaxSize())
                Text(
                    text = name.take(1).uppercase().ifEmpty { "✦" },
                    style = MaterialTheme.magicTypography.lifeNumberMd.copy(
                        fontSize = 72.sp,
                        color = mc.primaryAccent.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        // Dark gradient overlay (bottom→top) for text legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
                ),
        )


        // Name + game tag badge — bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = name.ifEmpty { stringResource(R.string.game_setup_default_player_name) },
                style = MaterialTheme.magicTypography.titleLarge.copy(
                    fontSize = 26.sp,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Game tag badge — displayed only when the user is authenticated and has a tag.
            if (gameTag != null) {
                Box(
                    modifier = Modifier
                        .background(
                            color = mc.primaryAccent.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = gameTag,
                        color = mc.primaryAccent,
                        style = MaterialTheme.magicTypography.labelSmall.copy(fontSize = 11.sp),
                    )
                }
            }
        }
    }
}

// ── KPI grid ──────────────────────────────────────────────────────────────────

@Composable
private fun ProfileKpiSection(
    uiState: ProfileViewModel.UiState,
    favouriteColor: String?,
    mostValuableColor: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (uiState.totalGames > 0) {
            SectionTitle(stringResource(R.string.profile_section_game_stats))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KpiCell(
                    stringResource(R.string.profile_stat_games),
                    uiState.totalGames.toString(),
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                KpiCell(
                    stringResource(R.string.profile_stat_wins),
                    uiState.totalWins.toString(),
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                KpiCell(
                    stringResource(R.string.profile_stat_win_pct),
                    "${(uiState.winRate * 100).roundToInt()}%",
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
        SectionTitle(stringResource(R.string.profile_section_collection_stats))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCell(
                stringResource(R.string.profile_stat_unique_cards),
                (uiState.collectionStats?.uniqueCards ?: 0).toString(),
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                accent = true
            )
            ColorStatCard(
                label = stringResource(R.string.profile_stat_fav_color),
                colorCode = favouriteColor,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            ColorStatCard(
                label = stringResource(R.string.profile_stat_top_value),
                colorCode = mostValuableColor,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun KpiCell(
    label: String,
    value: String,
    modifier: Modifier,
    accent: Boolean = false,
) {
    val mc = MaterialTheme.magicColors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(mc.surface)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Text(
            value,
            style = MaterialTheme.magicTypography.titleLarge,
            color = if (accent) mc.goldMtg else mc.primaryAccent,
        )
        Text(
            label,
            style = MaterialTheme.magicTypography.labelSmall,
            color = mc.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Best deck section ─────────────────────────────────────────────────────────

@Composable
private fun BestDeckSection(
    deck: DeckStatsRow,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val winRate = if (deck.totalGames > 0) deck.wins.toFloat() / deck.totalGames else 0f
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle(stringResource(R.string.profile_best_deck))
        Surface(shape = RoundedCornerShape(14.dp), color = mc.surface) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = deck.deckName ?: "???",
                        style = MaterialTheme.magicTypography.bodyMedium,
                        color = mc.textPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${deck.totalGames}G · ${(winRate * 100).roundToInt()}% WR",
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.primaryAccent,
                    )
                }
                LinearProgressIndicator(
                    progress = { winRate },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = mc.primaryAccent,
                    trackColor = mc.surfaceVariant,
                )
            }
        }
    }
}

// ── Survey insights section ───────────────────────────────────────────────────

@Composable
private fun SurveyInsightsSection(
    uiState: ProfileViewModel.UiState,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle(stringResource(R.string.profile_survey_insights))
        Surface(shape = RoundedCornerShape(14.dp), color = mc.surface) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InsightRow(
                    stringResource(R.string.profile_insight_hand_quality),
                    "${(uiState.avgHandRating * 20).roundToInt()}%",
                    (uiState.avgHandRating / 5f).toFloat()
                )
                InsightRow(
                    stringResource(R.string.profile_insight_mana_issues),
                    stringResource(R.string.profile_insight_mana_games, uiState.manaIssueCount),
                    (uiState.manaIssueCount.toFloat() / uiState.totalGames.coerceAtLeast(1)).coerceAtMost(
                        1f
                    )
                )
            }
        }
    }
}

@Composable
private fun InsightRow(label: String, value: String, progress: Float) {
    val mc = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
            Text(value, style = MaterialTheme.magicTypography.labelSmall, color = mc.primaryAccent)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = mc.primaryAccent,
            trackColor = mc.surfaceVariant,
        )
    }
}

// ── Achievements section ──────────────────────────────────────────────────────

@Composable
private fun AchievementsSection(
    achievements: List<Achievement>,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val unlocked = achievements.filter { it.isUnlocked }
    val inProgress = achievements.filter { !it.isUnlocked && (it.progress ?: 0f) > 0f }
    val locked = achievements.filter { !it.isUnlocked && (it.progress ?: 0f) == 0f }

    var selectedAchievement by remember { mutableStateOf<Achievement?>(null) }
    var showLocked by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(
            stringResource(R.string.profile_achievements),
            modifier = Modifier.padding(top = 8.dp)
        )

        // Unlocked grid
        if (unlocked.isNotEmpty()) {
            Text(
                stringResource(R.string.profile_achievements_unlocked, unlocked.size),
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textSecondary
            )
            unlocked.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { ach ->
                        AchievementBadge(
                            achievement = ach,
                            onClick = { selectedAchievement = ach },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        // In-progress
        if (inProgress.isNotEmpty()) {
            Text(
                stringResource(R.string.profile_achievements_in_progress, inProgress.size),
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textSecondary
            )
            inProgress.forEach { ach ->
                AchievementProgressRow(
                    achievement = ach,
                    onClick = { selectedAchievement = ach },
                )
            }
        }

        // Locked — collapsed
        if (locked.isNotEmpty()) {
            TextButton(
                onClick = { showLocked = !showLocked },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text = if (showLocked) stringResource(R.string.profile_achievements_hide_locked) else stringResource(
                        R.string.profile_achievements_locked_count,
                        locked.size
                    ),
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textDisabled,
                )
            }
            if (showLocked) {
                locked.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { ach ->
                            AchievementBadge(
                                achievement = ach,
                                onClick = { selectedAchievement = ach },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }

    selectedAchievement?.let { ach ->
        AchievementDetailDialog(
            achievement = ach,
            onDismiss = { selectedAchievement = null },
        )
    }
}

@Composable
private fun AchievementBadge(
    achievement: Achievement,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (achievement.isUnlocked) mc.surface else mc.surface.copy(alpha = 0.4f),
        border = if (achievement.isUnlocked)
            BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.4f))
        else null,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = achievement.emoji,
                style = MaterialTheme.magicTypography.bodyLarge.copy(
                    fontSize = 22.sp,
                    color = if (achievement.isUnlocked) Color.Unspecified else mc.textDisabled
                ),
            )
            Text(
                text = achievement.title,
                style = MaterialTheme.magicTypography.labelSmall,
                color = if (achievement.isUnlocked) mc.textPrimary else mc.textDisabled,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AchievementProgressRow(
    achievement: Achievement,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = mc.surface,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                achievement.emoji,
                style = MaterialTheme.magicTypography.bodyLarge.copy(fontSize = 20.sp),
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        achievement.title,
                        style = MaterialTheme.magicTypography.bodySmall,
                        color = mc.textPrimary
                    )
                    achievement.progressLabel?.let {
                        Text(
                            it,
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.primaryAccent
                        )
                    }
                }
                achievement.progress?.let { prog ->
                    LinearProgressIndicator(
                        progress = { prog },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = mc.primaryAccent,
                        trackColor = mc.surfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AchievementDetailDialog(
    achievement: Achievement,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.profile_achievement_ok)) }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    achievement.emoji,
                    style = MaterialTheme.magicTypography.displayMedium.copy(fontSize = 28.sp)
                )
                Text(
                    achievement.title,
                    style = MaterialTheme.magicTypography.titleMedium,
                    color = mc.textPrimary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = mc.primaryAccent.copy(alpha = 0.1f),
                ) {
                    Text(
                        text = achievement.category.label,
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.primaryAccent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Text(
                    achievement.description,
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textSecondary
                )
                if (!achievement.isUnlocked) {
                    achievement.progress?.let { prog ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    stringResource(R.string.profile_progress),
                                    style = MaterialTheme.magicTypography.labelSmall,
                                    color = mc.textDisabled
                                )
                                achievement.progressLabel?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.magicTypography.labelSmall,
                                        color = mc.primaryAccent
                                    )
                                }
                            }
                            LinearProgressIndicator(
                                progress = { prog },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = mc.primaryAccent,
                                trackColor = mc.surfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        containerColor = mc.backgroundSecondary,
    )
}

// ── Color stat card ───────────────────────────────────────────────────────────

@Composable
private fun ColorStatCard(
    label: String,
    colorCode: String?,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(mc.surface)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        if (colorCode != null) {
            ManaSymbolImage(token = colorCode, size = 26.dp)
        } else {
            Text("—", style = MaterialTheme.magicTypography.titleLarge, color = mc.primaryAccent)
        }
        Text(
            label,
            style = MaterialTheme.magicTypography.labelSmall,
            color = mc.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Recent games row ──────────────────────────────────────────────────────────

@Composable
private fun RecentGameRow(
    session: GameSessionWithPlayers,
    playerName: String,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val dateStr = dateFormat.format(Date(session.session.playedAt))

    val myPlayer = session.players.find { it.playerName == playerName }
    val isWin = myPlayer?.isWinner == true

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = mc.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.session.mode,
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textSecondary,
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textDisabled,
                )
            }
            Text(
                text = if (isWin) stringResource(R.string.profile_win) else stringResource(R.string.profile_loss),
                style = MaterialTheme.magicTypography.titleMedium,
                color = if (isWin) mc.primaryAccent else mc.textDisabled,
            )
        }
    }
}

// ── Collection summary ────────────────────────────────────────────────────────

@Composable
private fun CollectionSummarySection(
    stats: CollectionStats,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel,
) {
    val mc = MaterialTheme.magicColors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currency = uiState.preferredCurrency

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle(stringResource(R.string.profile_collection_summary))
        Surface(shape = RoundedCornerShape(14.dp), color = mc.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        stringResource(R.string.profile_total_cards),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary
                    )
                    Text(
                        stats.totalCards.toString(),
                        style = MaterialTheme.magicTypography.titleMedium,
                        color = mc.textPrimary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        stringResource(R.string.profile_est_value),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary
                    )
                    val formattedPrice = PriceFormatter.format(
                        amount = if (currency == PreferredCurrency.EUR) stats.totalValueEur else stats.totalValueUsd,
                        currency = currency
                    )
                    Text(
                        text = formattedPrice,
                        style = MaterialTheme.magicTypography.titleMedium,
                        color = mc.goldMtg
                    )
                }
            }
        }
    }
}


// ── Sections ──────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.magicTypography.labelLarge,
        color = MaterialTheme.magicColors.textSecondary,
        modifier = modifier,
    )
}


@Composable
private fun AppInfoFooter(modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val version = remember { com.mmg.manahub.BuildConfig.VERSION_NAME }
    val versionType = remember { com.mmg.manahub.BuildConfig.BUILD_TYPE.capitalize() }
    val build = remember { com.mmg.manahub.BuildConfig.VERSION_CODE }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_version, version, versionType,build),
            style = MaterialTheme.magicTypography.labelSmall,
            color = mc.textDisabled
        )
        Text(
            text = stringResource(R.string.profile_developed_by),
            style = MaterialTheme.magicTypography.labelSmall,
            color = mc.textDisabled,
            textAlign = TextAlign.Center
        )
    }
}

// ── Send Feedback row ─────────────────────────────────────────────────────────

/**
 * A tappable row that opens the feedback sheet.
 *
 * Styled consistently with the rest of the profile screen — icon on the left,
 * label in the centre, and a chevron arrow on the right.
 */
@Composable
private fun SendFeedbackRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = mc.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.feedback_title),
                style = MaterialTheme.magicTypography.bodyMedium,
                color = mc.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = mc.textDisabled,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun FriendsSummaryRow(
    friendCount: Int,
    pendingCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = mc.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Group,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.friends_title),
                style = MaterialTheme.magicTypography.bodyMedium,
                color = mc.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (pendingCount > 0) {
                Badge(containerColor = mc.primaryAccent) {
                    Text(
                        pendingCount.toString(),
                        color = Color.White,
                        style = MaterialTheme.magicTypography.labelSmall,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = friendCount.toString(),
                style = MaterialTheme.magicTypography.bodySmall,
                color = mc.textSecondary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = mc.textDisabled,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000L
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
