package com.mmg.manahub.feature.profile.presentation

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.CollectionStats
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.presentation.AccountSection
import com.mmg.manahub.feature.auth.presentation.AuthUiState
import com.mmg.manahub.feature.auth.presentation.AuthViewModel
import com.mmg.manahub.feature.auth.presentation.LoginSheet
import java.util.Locale
import kotlin.math.roundToInt

/** Tabs shown under the Profile hero (Phase 2 adds Quests; Rewards arrives in Phase 3). */
enum class ProfileTab { OVERVIEW, ACHIEVEMENTS, QUESTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    onSettingsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onFriendsClick: () -> Unit,
    /** Initial tab to open on (deep-linked from Home widgets in Phase 2). Default = Overview. */
    initialTab: ProfileTab = ProfileTab.OVERVIEW,
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

    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity
    val appUpdateManager = remember { com.google.android.play.core.appupdate.AppUpdateManagerFactory.create(context) }
    var updateAvailable by remember { mutableStateOf(false) }
    var appUpdateInfo by remember { mutableStateOf<com.google.android.play.core.appupdate.AppUpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == com.google.android.play.core.install.model.UpdateAvailability.UPDATE_AVAILABLE
                && info.isUpdateTypeAllowed(com.google.android.play.core.install.model.AppUpdateType.FLEXIBLE)
            ) {
                updateAvailable = true
                appUpdateInfo = info
            }
        }
    }

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
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden }
        )
        ModalBottomSheet(
            onDismissRequest = { showAccountSheet = false },
            sheetState = sheetState,
            containerColor = mc.backgroundSecondary,
            dragHandle = null,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showAccountSheet = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = mc.textSecondary
                        )
                    }
                }
                AccountSection(
                    sessionState = sessionState,
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
    }

    // Reset ui state after account deletion so the screen returns to Idle cleanly.
    // The sessionState Flow will automatically emit Unauthenticated after the account is removed.
    LaunchedEffect(authUiState) {
        if (authUiState is AuthUiState.AccountDeleted) {
            showAccountSheet = false
            authViewModel.resetUiState()
        }
    }

    // Quest-claim feedback. The one-shot Channel is collected here and surfaced via MagicToast.
    val toastState = rememberMagicToastState()
    val claimSuccessTemplate = stringResource(R.string.quests_claim_success)
    val claimFailedMessage = stringResource(R.string.quests_claim_failed)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProfileViewModel.Event.QuestClaimed ->
                    toastState.show(
                        message = String.format(claimSuccessTemplate, event.xpAwarded),
                        type = MagicToastType.SUCCESS,
                    )
                ProfileViewModel.Event.QuestClaimFailed ->
                    toastState.show(message = claimFailedMessage, type = MagicToastType.ERROR)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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

        // Tab selection lives in the screen (transient UI state). When gamification is OFF the tab
        // row is hidden entirely and only the Overview content is shown — gamification disappears.
        var selectedTab by rememberSaveable(uiState.gamificationEnabled) {
            mutableStateOf(if (uiState.gamificationEnabled) initialTab else ProfileTab.OVERVIEW)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Hero (always visible, above the tab row) ──────────────────────
            ProfileHeroSection(
                name = uiState.playerName,
                avatarUrl = uiState.avatarUrl,
                gameTag = (sessionState as? SessionState.Authenticated)?.user?.gameTag,
                progression = uiState.progression.takeIf { uiState.gamificationEnabled },
                onEditClick = { showProfileEdit = true },
            )

            // ── Tab row (only when gamification is enabled) ───────────────────
            if (uiState.gamificationEnabled) {
                ProfileTabRow(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                )
            }

            // ── Tab content ───────────────────────────────────────────────────
            val bottomInset = PaddingValues(bottom = 32.dp + navBarBottom)
            when {
                uiState.gamificationEnabled && selectedTab == ProfileTab.ACHIEVEMENTS -> {
                    AchievementsTab(
                        achievements = uiState.achievements,
                        contentPadding = bottomInset,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp),
                    )
                }

                uiState.gamificationEnabled && selectedTab == ProfileTab.QUESTS -> {
                    QuestsTab(
                        board = uiState.questBoard,
                        streak = uiState.streak,
                        onClaim = viewModel::claimQuest,
                        contentPadding = bottomInset,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp),
                    )
                }

                else -> {
                    OverviewTabContent(
                        uiState = uiState,
                        sessionState = sessionState,
                        viewModel = viewModel,
                        contentPadding = bottomInset,
                        updateAvailable = updateAvailable,
                        onFriendsClick = onFriendsClick,
                        onStatsClick = onStatsClick,
                        onUpdateClick = {
                            appUpdateInfo?.let { info ->
                                activity?.let {
                                    appUpdateManager.startUpdateFlowForResult(
                                        info,
                                        com.google.android.play.core.install.model.AppUpdateType.FLEXIBLE,
                                        it,
                                        500,
                                    )
                                }
                            }
                        },
                        onRateClick = {
                            val reviewManager = com.google.android.play.core.review.ReviewManagerFactory.create(context)
                            val request = reviewManager.requestReviewFlow()
                            request.addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val reviewInfo = task.result
                                    activity?.let { reviewManager.launchReviewFlow(it, reviewInfo) }
                                } else {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=${context.packageName}"))
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")))
                                    }
                                }
                            }
                        },
                        onFeedbackClick = { showFeedbackSheet = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
        MagicToastHost(toastState)
    }
}

// ── Tab row ─────────────────────────────────────────────────────────────────────

/**
 * The Profile tab selector (Overview / Achievements). Shown only when gamification is enabled.
 * Stateless: selection is hoisted to the screen. Uses MagicTheme tokens and exposes selected-state
 * semantics; each tab is a full-height (≥48dp) row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTabRow(
    selectedTab: ProfileTab,
    onTabSelected: (ProfileTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = modifier.fillMaxWidth(),
        containerColor = mc.background,
        contentColor = mc.primaryAccent,
        indicator = { positions ->
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(positions[selectedTab.ordinal]),
                color = mc.primaryAccent,
            )
        },
        divider = {},
    ) {
        ProfileTab.entries.forEach { tab ->
            val labelRes = when (tab) {
                ProfileTab.OVERVIEW -> R.string.profile_tab_overview
                ProfileTab.ACHIEVEMENTS -> R.string.profile_tab_achievements
                ProfileTab.QUESTS -> R.string.profile_tab_quests
            }
            val selected = tab == selectedTab
            Tab(
                selected = selected,
                onClick = { onTabSelected(tab) },
                modifier = Modifier.height(48.dp),
                selectedContentColor = mc.primaryAccent,
                unselectedContentColor = mc.textSecondary,
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.magicTypography.labelLarge,
                )
            }
        }
    }
}

// ── Overview tab content ─────────────────────────────────────────────────────────

/**
 * The Overview tab — the original Profile body (friends, KPIs, collection summary, update/rate/
 * feedback rows, footer), moved verbatim into a tab. Account-gated rows behave exactly as before.
 * Stateless wrapper: state + actions are hoisted from the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewTabContent(
    uiState: ProfileViewModel.UiState,
    sessionState: SessionState,
    viewModel: ProfileViewModel,
    contentPadding: PaddingValues,
    updateAvailable: Boolean,
    onFriendsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onRateClick: () -> Unit,
    onFeedbackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        // ── Friends ───────────────────────────────────────────────────────
        if (sessionState is SessionState.Authenticated) {
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

        // ── Collection summary ────────────────────────────────────────────
        uiState.collectionStats?.let { stats ->
            item {
                CollectionSummarySection(
                    stats = stats,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    viewModel = viewModel,
                )
            }
        }

        // ── App Update ────────────────────────────────────────────────────
        if (updateAvailable) {
            item {
                AppUpdateRow(
                    onClick = onUpdateClick,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        // ── Rate the app ──────────────────────────────────────────────────
        item {
            RateAppRow(
                onClick = onRateClick,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // ── Send feedback ─────────────────────────────────────────────────
        item {
            SendFeedbackRow(
                onClick = onFeedbackClick,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // ── Footer ────────────────────────────────────────────────────────
        item {
            AppInfoFooter(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
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
    /**
     * Gamification progression for the read-only XP ring (ADR-002, Phase 0). When null the ring
     * and level chip are not drawn — the caller passes null while gamification is disabled or the
     * progression flow has not yet emitted.
     */
    progression: com.mmg.manahub.core.gamification.domain.model.PlayerProgression? = null,
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

        // Read-only XP ring + level chip (ADR-002, Phase 0). Overlaid top-end of the hero.
        // Only drawn when gamification is enabled AND progression has emitted (caller passes null
        // otherwise, so the hero renders with no ring as a neutral fallback).
        if (progression != null) {
            val span = progression.xpForNextLevel
            val ringProgress = if (span > 0L) {
                progression.xpIntoLevel.toFloat() / span.toFloat()
            } else {
                0f
            }
            ProfileLevelRing(
                level = progression.level,
                progress = ringProgress,
                contentDescription = stringResource(
                    R.string.profile_level_ring_a11y,
                    progression.level,
                    progression.xpIntoLevel,
                    progression.xpForNextLevel,
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            )
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

// ── App Update row ────────────────────────────────────────────────────────────

@Composable
private fun AppUpdateRow(
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
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "New app update available",
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

// ── Rate App row ──────────────────────────────────────────────────────────────

@Composable
private fun RateAppRow(
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
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = mc.goldMtg,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Rate the app",
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

