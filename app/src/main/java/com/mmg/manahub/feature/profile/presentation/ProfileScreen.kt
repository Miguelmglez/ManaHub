package com.mmg.manahub.feature.profile.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.update.AppUpdateState
import com.mmg.manahub.core.gamification.domain.catalog.UnlockableCatalog
import com.mmg.manahub.core.gamification.domain.model.EquippedCosmetics
import com.mmg.manahub.core.gamification.domain.model.PlayerProgression
import com.mmg.manahub.core.model.CollectionStats
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.components.ManaTabItem
import com.mmg.manahub.core.ui.components.ManaTabRow
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.onOverlayScrim
import com.mmg.manahub.core.ui.theme.overlayScrim
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.core.util.recordNonFatal
import com.mmg.manahub.feature.auth.presentation.AccountSection
import com.mmg.manahub.feature.auth.presentation.AuthViewModel
import com.mmg.manahub.feature.auth.presentation.LoginSheet
import com.mmg.manahub.feature.gamification.presentation.AvatarFrameRing
import com.mmg.manahub.feature.gamification.presentation.BadgeEmblem
import com.mmg.manahub.feature.gamification.presentation.TitleText
import org.koin.androidx.compose.koinViewModel
import java.util.Locale
import kotlin.math.roundToInt

/** Tabs shown under the Profile hero; every tab but [OVERVIEW] needs gamification. */
enum class ProfileTab {
    OVERVIEW, ACHIEVEMENTS, QUESTS, REWARDS;

    companion object {
        /** Maps the `?tab=` route argument to a tab; unknown or missing values open [OVERVIEW]. */
        fun fromRouteArg(value: String?): ProfileTab = when (value?.lowercase()) {
            "achievements" -> ACHIEVEMENTS
            "quests" -> QUESTS
            "rewards" -> REWARDS
            else -> OVERVIEW
        }
    }
}

/** The tab actually shown: any gamification tab falls back to [ProfileTab.OVERVIEW] while unavailable. */
internal fun resolveProfileTab(selected: ProfileTab, gamificationAvailable: Boolean): ProfileTab =
    if (gamificationAvailable) selected else ProfileTab.OVERVIEW

private val HERO_MAX_HEIGHT = 360.dp
private const val HERO_DEFAULT_RATIO = 1.77f
private const val HERO_MIN_RATIO = 1.2f
private const val HERO_MAX_RATIO = 2.5f
private const val MAX_TOP_VALUE_SYMBOLS = 3

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = koinViewModel(),
    authViewModel: AuthViewModel = koinViewModel(),
    onSettingsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onFriendsClick: () -> Unit,
    /** Navigates to the account-management screen; sign-out/delete-account/security live there. */
    onManageAccountClick: () -> Unit,
    /** Tab requested by a deep link; applied once gamification is known to be available. */
    initialTab: ProfileTab = ProfileTab.OVERVIEW,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionState by authViewModel.sessionState.collectAsStateWithLifecycle()
    val appUpdateState by viewModel.appUpdateState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    val context = LocalContext.current

    var showProfileEdit by rememberSaveable { mutableStateOf(false) }
    var showFeedbackSheet by rememberSaveable { mutableStateOf(false) }
    var showLoginSheet by rememberSaveable { mutableStateOf(false) }
    var loginSheetInitialTab by rememberSaveable { mutableIntStateOf(0) }
    var showAccountSheet by rememberSaveable { mutableStateOf(false) }

    var selectedTab by rememberSaveable { mutableStateOf(ProfileTab.OVERVIEW) }
    var initialTabApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.gamificationEnabled) {
        if (uiState.gamificationEnabled && !initialTabApplied) {
            selectedTab = initialTab
            initialTabApplied = true
        }
    }
    val activeTab = resolveProfileTab(selectedTab, uiState.gamificationEnabled)

    if (showProfileEdit) {
        ProfileEditSheet(onDismiss = { showProfileEdit = false })
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
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAccountSheet = false },
            sheetState = sheetState,
            containerColor = mc.backgroundSecondary,
            dragHandle = null,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.sm, start = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { showAccountSheet = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = mc.textSecondary,
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
                    onManageAccountClick = {
                        showAccountSheet = false
                        onManageAccountClick()
                    },
                    onEditProfileClick = {
                        showAccountSheet = false
                        showProfileEdit = true
                    },
                    onFetchShareLink = viewModel::fetchShareLink,
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
                    playerName = uiState.playerName,
                    avatarUrl = uiState.avatarUrl,
                )
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }

    val toastState = rememberMagicToastState()
    val claimSuccessTemplate = stringResource(R.string.quests_claim_success)
    val claimFailedMessage = stringResource(R.string.quests_claim_failed)
    val badgeCapTemplate = stringResource(R.string.reward_badge_cap_reached)
    val storeUnavailableMessage = stringResource(R.string.profile_rate_app_unavailable)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ProfileViewModel.Event.QuestClaimed -> toastState.show(
                    message = String.format(claimSuccessTemplate, event.xpAwarded),
                    type = MagicToastType.SUCCESS,
                )
                ProfileViewModel.Event.QuestClaimFailed ->
                    toastState.show(message = claimFailedMessage, type = MagicToastType.ERROR)
                is ProfileViewModel.Event.BadgeCapReached -> toastState.show(
                    message = String.format(badgeCapTemplate, event.maxBadges),
                    type = MagicToastType.INFO,
                )
            }
        }
    }

    val groupedAchievements = remember(uiState.achievements) {
        groupAchievementsByCategory(uiState.achievements)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = mc.background,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                ProfileTopBar(onBack = onBack, onSettingsClick = onSettingsClick)
            },
        ) { padding ->
            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val isSignedIn = sessionState is SessionState.Authenticated

            // One list: the hero scrolls away with every tab's content (P-20).
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = spacing.xxl + navBarBottom),
            ) {
                item(key = "profile_hero") {
                    ProfileHeroSection(
                        name = uiState.playerName,
                        avatarUrl = uiState.avatarUrl,
                        gameTag = (sessionState as? SessionState.Authenticated)?.user?.gameTag,
                        progression = uiState.progression.takeIf { uiState.gamificationEnabled },
                        equipped = if (uiState.gamificationEnabled) uiState.equipped else EquippedCosmetics.NONE,
                        onEditClick = {
                            if (isSignedIn) showAccountSheet = true else showProfileEdit = true
                        },
                    )
                }

                if (uiState.gamificationEnabled) {
                    stickyHeader(key = "profile_tabs") {
                        ProfileTabRow(selectedTab = activeTab, onTabSelected = { selectedTab = it })
                    }
                }

                when (activeTab) {
                    ProfileTab.OVERVIEW -> overviewItems(
                        uiState = uiState,
                        isSignedIn = isSignedIn,
                        isSignedOut = sessionState is SessionState.Unauthenticated,
                        appUpdateState = appUpdateState,
                        onFriendsClick = onFriendsClick,
                        onStatsClick = onStatsClick,
                        onRetryStats = viewModel::retryStats,
                        onUpdateClick = viewModel::onUpdateClick,
                        onRateClick = {
                            if (!openStoreListing(context)) {
                                toastState.show(message = storeUnavailableMessage, type = MagicToastType.ERROR)
                            }
                        },
                        onFeedbackClick = { showFeedbackSheet = true },
                        onLoginClick = {
                            loginSheetInitialTab = 0
                            showLoginSheet = true
                        },
                    )

                    ProfileTab.ACHIEVEMENTS -> achievementsTabItems(groupedAchievements)

                    ProfileTab.QUESTS -> questsTabItems(
                        board = uiState.questBoard,
                        streak = uiState.streak,
                        claimingIds = uiState.claimingQuestIds,
                        onClaim = viewModel::claimQuest,
                    )

                    ProfileTab.REWARDS -> rewardsTabItems(
                        board = uiState.rewardsBoard,
                        onEquip = viewModel::onEquip,
                        onUnequip = viewModel::onUnequip,
                    )
                }
            }
        }
        MagicToastHost(toastState)
    }
}

/**
 * Opens the Play Store listing (store app first, then the web page). An explicit "Rate" tap must not
 * use the In-App Review flow: once its quota is spent it succeeds without showing anything (P-15).
 *
 * @return false when neither the store nor a browser could be opened.
 */
private fun openStoreListing(context: Context): Boolean {
    val packageName = context.packageName
    val targets = listOf(
        "market://details?id=$packageName",
        "https://play.google.com/store/apps/details?id=$packageName",
    )
    for (target in targets) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
            return true
        } catch (_: ActivityNotFoundException) {
            // Try the next target.
        }
    }
    recordNonFatal("profile_rate_store_unavailable")
    return false
}

// ── Top bar ─────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileTopBar(onBack: () -> Unit, onSettingsClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    Surface(color = mc.backgroundSecondary, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = MaterialTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = mc.textPrimary,
                )
            }
            Text(
                text = stringResource(R.string.profile_title),
                style = MaterialTheme.magicTypography.titleLarge,
                color = mc.textPrimary,
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = mc.textPrimary,
                )
            }
        }
    }
}

// ── Tab row ─────────────────────────────────────────────────────────────────────

/** The Profile tab selector, shown only when gamification is available. Stateless. */
@Composable
private fun ProfileTabRow(
    selectedTab: ProfileTab,
    onTabSelected: (ProfileTab) -> Unit,
) {
    val items = ProfileTab.entries.map { tab ->
        val labelRes = when (tab) {
            ProfileTab.OVERVIEW -> R.string.profile_tab_overview
            ProfileTab.ACHIEVEMENTS -> R.string.profile_tab_achievements
            ProfileTab.QUESTS -> R.string.profile_tab_quests
            ProfileTab.REWARDS -> R.string.profile_tab_rewards
        }
        ManaTabItem(
            label = stringResource(labelRes).uppercase(),
            selected = tab == selectedTab,
            onClick = { onTabSelected(tab) },
        )
    }
    ManaTabRow(items = items, modifier = Modifier.fillMaxWidth())
}

// ── Overview tab ────────────────────────────────────────────────────────────────

/** Emits the Overview tab (friends, KPIs, collection summary, app rows, footer) as keyed items. */
private fun LazyListScope.overviewItems(
    uiState: ProfileViewModel.UiState,
    isSignedIn: Boolean,
    isSignedOut: Boolean,
    appUpdateState: AppUpdateState,
    onFriendsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onRetryStats: () -> Unit,
    onUpdateClick: () -> Unit,
    onRateClick: () -> Unit,
    onFeedbackClick: () -> Unit,
    onLoginClick: () -> Unit,
) {
    if (isSignedIn) {
        item(key = "overview_friends") {
            FriendsSummaryRow(
                friendCount = uiState.friendCount,
                pendingCount = uiState.pendingFriendCount,
                onClick = onFriendsClick,
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.xs),
            )
        }
    }

    item(key = "overview_kpis") {
        ProfileKpiSection(
            uiState = uiState,
            onStatsClick = onStatsClick,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg),
        )
    }

    when {
        uiState.isLoading -> item(key = "overview_stats_loading") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.lg),
                contentAlignment = Alignment.Center,
            ) { MagicLoadingSpinner(size = MagicLoadingSize.Small) }
        }

        uiState.statsError -> item(key = "overview_stats_error") {
            InlineErrorState(
                message = stringResource(R.string.profile_stats_error),
                retryLabel = stringResource(R.string.action_retry),
                onRetry = onRetryStats,
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.sm),
            )
        }

        else -> uiState.collectionStats?.let { stats ->
            item(key = "overview_collection_summary") {
                CollectionSummarySection(
                    stats = stats,
                    currency = uiState.preferredCurrency,
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.sm),
                )
            }
        }
    }

    val updateLabelRes = when (appUpdateState) {
        is AppUpdateState.Available -> R.string.profile_update_available
        AppUpdateState.Downloading -> R.string.profile_update_downloading
        AppUpdateState.Downloaded -> R.string.profile_update_ready
        AppUpdateState.None, is AppUpdateState.Forced -> null
    }
    if (updateLabelRes != null) {
        item(key = "overview_update") {
            ProfileLinkRow(
                icon = Icons.Default.SystemUpdate,
                iconTint = MaterialTheme.magicColors.primaryAccent,
                label = stringResource(updateLabelRes),
                onClick = onUpdateClick,
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.xs),
            )
        }
    }

    item(key = "overview_rate") {
        ProfileLinkRow(
            icon = Icons.Default.Star,
            iconTint = MaterialTheme.magicColors.goldMtg,
            label = stringResource(R.string.profile_rate_app),
            onClick = onRateClick,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.xs),
        )
    }

    item(key = "overview_feedback") {
        ProfileLinkRow(
            icon = Icons.AutoMirrored.Filled.Send,
            iconTint = MaterialTheme.magicColors.primaryAccent,
            label = stringResource(R.string.feedback_title),
            onClick = onFeedbackClick,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.xs),
        )
    }

    if (isSignedOut) {
        item(key = "overview_login") {
            LoginCtaCard(
                onClick = onLoginClick,
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.sm),
            )
        }
    }

    item(key = "overview_footer") {
        AppInfoFooter(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.sm))
    }
}

// ── Login CTA Card ─────────────────────────────────────────────────────────────

/** Card shown to signed-out users to encourage sign-in. */
@Composable
private fun LoginCtaCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = mc.surface,
        tonalElevation = 2.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(mc.primaryAccent.copy(alpha = 0.08f), mc.background),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.auth_section_title),
                        style = ty.titleMedium,
                        color = mc.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.auth_section_subtitle),
                        style = ty.bodyMedium,
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }

                MagicCtaButton(
                    onClick = onClick,
                    text = stringResource(R.string.auth_cta_signin),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── Hero section ──────────────────────────────────────────────────────────────

/**
 * The profile hero: avatar art (or a gradient with the initial), name, equipped cosmetics, game tag
 * and the XP ring. Everything drawn over the photo uses scrim-safe ink so it reads on all 12 palettes.
 *
 * @param progression drives the XP ring; null hides it (gamification unavailable or not loaded).
 * @param equipped cosmetic overlays; [EquippedCosmetics.NONE] renders the plain hero.
 */
@Composable
private fun ProfileHeroSection(
    name: String,
    avatarUrl: String?,
    gameTag: String?,
    progression: PlayerProgression?,
    equipped: EquippedCosmetics,
    onEditClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing

    val equippedTitle = remember(equipped.titleId) { equipped.titleId?.let { UnlockableCatalog.byId(it) } }
    val equippedBadges = remember(equipped.badgeIds) { equipped.badgeIds.mapNotNull { UnlockableCatalog.byId(it) } }
    val equippedFrame = remember(equipped.avatarFrameId) { equipped.avatarFrameId?.let { UnlockableCatalog.byId(it) } }
    val equippedRing = remember(equipped.levelRingStyleId) { equipped.levelRingStyleId?.let { UnlockableCatalog.byId(it) } }

    var imageRatio by remember(avatarUrl) { mutableFloatStateOf(HERO_DEFAULT_RATIO) }
    var avatarFailed by remember(avatarUrl) { mutableStateOf(false) }
    val showAvatar = avatarUrl != null && !avatarFailed

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(spacing.sm),
    ) {
        val heroHeight = (maxWidth / imageRatio.coerceIn(HERO_MIN_RATIO, HERO_MAX_RATIO))
            .coerceAtMost(HERO_MAX_HEIGHT)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .clip(CardShape)
                .clickable(
                    onClickLabel = stringResource(R.string.profile_edit_title),
                    role = Role.Button,
                    onClick = onEditClick,
                ),
        ) {
            if (showAvatar) {
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
                        if (size.width > 0 && size.height > 0) imageRatio = size.width / size.height
                    },
                    onError = { avatarFailed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(mc.primaryAccent.copy(alpha = 0.3f), mc.background),
                            ),
                        ),
                ) {
                    ThemeBackground(modifier = Modifier.fillMaxSize())
                    Text(
                        text = name.take(1).uppercase().ifEmpty { "✦" },
                        style = MaterialTheme.magicTypography.lifeNumberMd,
                        color = mc.primaryAccent.copy(alpha = 0.3f),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            // Bottom-up scrim so the name reads over any artwork.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                mc.overlayScrim.copy(alpha = 0f),
                                mc.overlayScrim.copy(alpha = 0f),
                                mc.overlayScrim.copy(alpha = 0.5f),
                                mc.overlayScrim.copy(alpha = 0.85f),
                            ),
                        ),
                    ),
            )

            if (equippedFrame != null) {
                AvatarFrameRing(renderSpec = equippedFrame.renderSpec, modifier = Modifier.fillMaxSize())
            }

            val resolvedName = name.ifEmpty { stringResource(R.string.game_setup_default_player_name) }
            val badgeCount = equippedBadges.size.coerceAtMost(EquippedCosmetics.MAX_EQUIPPED_BADGES)
            val badgesEquippedText = if (badgeCount > 0) {
                pluralStringResource(R.plurals.profile_hero_badges_equipped, badgeCount, badgeCount)
            } else {
                null
            }
            val heroA11y = buildString {
                append(resolvedName)
                equippedTitle?.displayName?.let { append(", "); append(it) }
                badgesEquippedText?.let { append(", "); append(it) }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(spacing.md)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics { contentDescription = heroA11y },
                ) {
                    Text(
                        text = resolvedName,
                        style = MaterialTheme.magicTypography.displayMedium,
                        color = mc.onOverlayScrim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // Title cosmetics are tuned for surfaces, so they get a solid surface chip (P-06).
                    if (equippedTitle != null) {
                        Surface(
                            shape = ChipShape,
                            color = mc.surface,
                            modifier = Modifier.padding(top = spacing.xxs),
                        ) {
                            TitleText(
                                renderSpec = equippedTitle.renderSpec,
                                text = equippedTitle.displayName,
                                style = MaterialTheme.magicTypography.labelLarge,
                                modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
                            )
                        }
                    }

                    if (equippedBadges.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = spacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            equippedBadges
                                .take(EquippedCosmetics.MAX_EQUIPPED_BADGES)
                                .forEach { badge -> BadgeEmblem(renderSpec = badge.renderSpec, size = 22.dp) }
                        }
                    }
                }

                if (gameTag != null) {
                    Surface(shape = ChipShape, color = mc.primaryAccent) {
                        Text(
                            text = gameTag,
                            color = mc.onAccent,
                            style = MaterialTheme.magicTypography.labelSmall,
                            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
                        )
                    }
                }
            }

            if (progression != null) {
                val span = progression.xpForNextLevel
                val ringProgress = if (span > 0L) progression.xpIntoLevel.toFloat() / span.toFloat() else 0f
                ProfileLevelRing(
                    level = progression.level,
                    progress = ringProgress,
                    contentDescription = stringResource(
                        R.string.profile_level_ring_a11y,
                        progression.level,
                        progression.xpIntoLevel,
                        progression.xpForNextLevel,
                    ),
                    ringStyle = equippedRing?.renderSpec?.ringStyle,
                    ringRenderSpec = equippedRing?.renderSpec,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(spacing.md),
                )
            }
        }
    }
}

// ── KPI grid ──────────────────────────────────────────────────────────────────

@Composable
private fun ProfileKpiSection(
    uiState: ProfileViewModel.UiState,
    onStatsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = modifier
            .padding(top = spacing.sm)
            .clickable(
                onClickLabel = stringResource(R.string.profile_open_stats_a11y),
                role = Role.Button,
                onClick = onStatsClick,
            ),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        if (uiState.totalGames > 0) {
            SectionTitle(stringResource(R.string.profile_section_game_stats))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                KpiCell(
                    stringResource(R.string.profile_stat_games),
                    uiState.totalGames.toString(),
                    Modifier.weight(1f).fillMaxHeight(),
                )
                KpiCell(
                    stringResource(R.string.profile_stat_wins),
                    uiState.totalWins.toString(),
                    Modifier.weight(1f).fillMaxHeight(),
                )
                KpiCell(
                    stringResource(R.string.profile_stat_win_pct),
                    "${(uiState.winRate * 100).roundToInt()}%",
                    Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        val stats = uiState.collectionStats
        if (stats != null) {
            SectionTitle(stringResource(R.string.profile_section_collection_stats))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                KpiCell(
                    stringResource(R.string.profile_stat_unique_cards),
                    stats.uniqueCards.toString(),
                    Modifier.weight(1f).fillMaxHeight(),
                    accent = true,
                )
                ColorStatCard(
                    label = stringResource(R.string.profile_stat_fav_color),
                    colors = uiState.favouriteColor?.let { listOf(it) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                ColorStatCard(
                    label = stringResource(R.string.profile_stat_top_value),
                    colors = uiState.mostValuableColors,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
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
    val spacing = MaterialTheme.spacing
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(mc.surface)
            .padding(vertical = spacing.md, horizontal = spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xxs, Alignment.CenterVertically),
    ) {
        Text(value, style = MaterialTheme.magicTypography.titleLarge, color = if (accent) mc.goldMtg else mc.primaryAccent)
        Text(label, style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary, textAlign = TextAlign.Center)
    }
}

/**
 * A KPI cell showing colour symbols. A multicolour identity shows up to [MAX_TOP_VALUE_SYMBOLS]
 * individual symbols (Scryfall has no "M" symbol, P-16); null shows a dash.
 */
@Composable
private fun ColorStatCard(
    label: String,
    colors: List<String>?,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(mc.surface)
            .padding(vertical = spacing.md, horizontal = spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xxs, Alignment.CenterVertically),
    ) {
        when {
            colors.isNullOrEmpty() ->
                Text("—", style = MaterialTheme.magicTypography.titleLarge, color = mc.primaryAccent)
            colors.size == 1 -> ManaSymbolImage(token = colors.first(), size = 26.dp)
            else -> Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                colors.take(MAX_TOP_VALUE_SYMBOLS).forEach { ManaSymbolImage(token = it, size = 20.dp) }
                if (colors.size > MAX_TOP_VALUE_SYMBOLS) {
                    Text(
                        text = "+${colors.size - MAX_TOP_VALUE_SYMBOLS}",
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary,
                    )
                }
            }
        }
        Text(label, style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary, textAlign = TextAlign.Center)
    }
}

// ── Collection summary ────────────────────────────────────────────────────────

@Composable
private fun CollectionSummarySection(
    stats: CollectionStats,
    currency: PreferredCurrency,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        SectionTitle(stringResource(R.string.profile_collection_summary))
        Surface(shape = CardShape, color = mc.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(stringResource(R.string.profile_total_cards), style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
                    Text(stats.totalCards.toString(), style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.profile_est_value), style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
                    Text(
                        text = PriceFormatter.format(
                            amount = if (currency == PreferredCurrency.EUR) stats.totalValueEur else stats.totalValueUsd,
                            currency = currency,
                        ),
                        style = MaterialTheme.magicTypography.titleMedium,
                        color = mc.goldMtg,
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
    val versionType = remember { BuildConfig.BUILD_TYPE.replaceFirstChar { it.titlecase(Locale.ROOT) } }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.profile_version, BuildConfig.VERSION_NAME, versionType, BuildConfig.VERSION_CODE),
            style = MaterialTheme.magicTypography.labelSmall,
            color = mc.textDisabled,
        )
        Text(
            text = stringResource(R.string.profile_developed_by),
            style = MaterialTheme.magicTypography.labelSmall,
            color = mc.textDisabled,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Link rows ─────────────────────────────────────────────────────────────────

/** A tappable settings-style row (≥48dp): leading icon, label, optional trailing content, chevron. */
@Composable
private fun ProfileLinkRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = CardShape, color = mc.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(spacing.md))
            Text(
                text = label,
                style = MaterialTheme.magicTypography.bodyMedium,
                color = mc.textPrimary,
                modifier = Modifier.weight(1f),
            )
            trailing()
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
    val spacing = MaterialTheme.spacing
    ProfileLinkRow(
        icon = Icons.Default.Group,
        iconTint = mc.primaryAccent,
        label = stringResource(R.string.friends_title),
        onClick = onClick,
        modifier = modifier,
    ) {
        if (pendingCount > 0) {
            val pendingA11y = pluralStringResource(R.plurals.profile_pending_friend_requests, pendingCount, pendingCount)
            Surface(
                shape = ChipShape,
                color = mc.primaryAccent,
                modifier = Modifier.semantics { contentDescription = pendingA11y },
            ) {
                Text(
                    text = pendingCount.toString(),
                    color = mc.onAccent,
                    style = MaterialTheme.magicTypography.labelSmall,
                    modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
                )
            }
            Spacer(modifier = Modifier.width(spacing.sm))
        }
        Text(text = friendCount.toString(), style = MaterialTheme.magicTypography.labelMedium, color = mc.textSecondary)
        Spacer(modifier = Modifier.width(spacing.sm))
    }
}
