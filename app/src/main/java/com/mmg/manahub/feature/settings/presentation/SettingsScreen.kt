package com.mmg.manahub.feature.settings.presentation

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.mmg.manahub.R
import com.mmg.manahub.core.model.AppLanguage
import com.mmg.manahub.core.model.CardLanguage
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.UserPreferences
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import android.content.pm.PackageManager
import com.mmg.manahub.core.util.recordSafeNonFatal
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.ui.theme.colors
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.AppTheme
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.voice.domain.VoiceLanguage
import com.mmg.manahub.core.voice.domain.VoiceModelState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onManageTagDictionary: () -> Unit = {},
    onManageAccount: () -> Unit = {},
    // KMP migration — Phase 0 Spike D: Settings is the first "Koin island". This ViewModel is
    // resolved by Koin (koinViewModel()) while every other screen still uses hiltViewModel().
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val isAuthenticated by viewModel.isAuthenticatedFlow.collectAsStateWithLifecycle()
    val prefsState by viewModel.prefsState.collectAsStateWithLifecycle()
    val pushEnabled by viewModel.pushNotificationsEnabled.collectAsStateWithLifecycle()
    val gamificationEnabled by viewModel.gamificationEnabled.collectAsStateWithLifecycle()
    val gamificationSettingsVisible by viewModel.gamificationSettingsVisible.collectAsStateWithLifecycle()
    val notificationPrefs by viewModel.notificationPrefs.collectAsStateWithLifecycle()
    val voiceModelStates by viewModel.voiceModelStates.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val activity = context as? Activity
    val toastState = rememberMagicToastState()
    val privacyErrorMsg = stringResource(R.string.settings_privacy_error)
    val notificationErrorMsg = stringResource(R.string.settings_notification_prefs_error)
    val systemSettingsUnavailableMsg = stringResource(R.string.settings_system_settings_unavailable)
    val signInRequiredMsg = stringResource(R.string.settings_sign_in_required)

    // POST_NOTIFICATIONS is a runtime permission only on Android 13+ (API 33). On older
    // devices notifications are granted at install time, so the rationale banner is skipped.
    val notificationPermissionState: PermissionState? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            null
        }

    // The user can grant the permission in system settings (deep link above) and come back:
    // Accompanist's snapshot is taken at composition, so re-read the real OS state on resume.
    var permissionGrantedOnResume by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionGrantedOnResume = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        viewModel.appLanguageChanged.collect { activity?.recreate() }
    }

    LaunchedEffect(uiState.toastMessage) {
        val toast = uiState.toastMessage ?: return@LaunchedEffect
        val msg = when (toast) {
            SettingsToast.PRIVACY_SAVE_FAILED      -> privacyErrorMsg
            SettingsToast.NOTIFICATION_SAVE_FAILED -> notificationErrorMsg
            SettingsToast.SIGN_IN_REQUIRED         -> signInRequiredMsg
        }
        toastState.show(msg, if (uiState.toastIsError) MagicToastType.ERROR else MagicToastType.SUCCESS)
        viewModel.clearToast()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Surface(color = mc.backgroundSecondary) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
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
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.magicTypography.titleLarge,
                        color = mc.textPrimary,
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            PreferencesSection(
                prefs = prefsState.userPreferences,
                onAppLanguage = viewModel::setAppLanguage,
                onCardLanguage = viewModel::setCardLanguage,
                onCurrency = viewModel::setPreferredCurrency,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            /* HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))
             Text(
                 stringResource(R.string.settings_section_prices),
                 style = MaterialTheme.magicTypography.titleMedium,
                 color = mc.textPrimary,
                 modifier = Modifier.padding(horizontal = 16.dp),
             )

             SettingsToggleItem(
                 title = stringResource(R.string.settings_auto_refresh),
                 subtitle = stringResource(R.string.settings_auto_refresh_subtitle),
                 checked = uiState.autoRefreshPrices,
                 onCheckedChange = viewModel::onAutoRefreshChanged,
             )

*/

            HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Anonymous/guest sessions (auto-signed-in for Online Sessions) must never
                    // reach Account Management — its Google identity-link action IS Supabase's
                    // anonymous-to-permanent conversion primitive and bypasses the server-side
                    // profile-creation trigger when done in place. Disabled rather than hidden so
                    // the row still communicates why (mirrors TagDictionaryScreen's read-only-row
                    // pattern: `.clickable(enabled = ...)`).
                    .clickable(enabled = isAuthenticated, onClick = onManageAccount)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_manage_account),
                        style = MaterialTheme.magicTypography.bodyMedium,
                        color = if (isAuthenticated) mc.textPrimary else mc.textDisabled,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(
                            if (isAuthenticated) {
                                R.string.settings_manage_account_subtitle
                            } else {
                                R.string.settings_manage_account_signin_required
                            },
                        ),
                        style = MaterialTheme.magicTypography.bodySmall,
                        color = mc.textSecondary,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (isAuthenticated) mc.textSecondary else mc.textDisabled,
                )
            }

            HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onManageTagDictionary)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_tag_dictionary),
                        style = MaterialTheme.magicTypography.bodyMedium,
                        color = mc.textPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_tag_dictionary_subtitle),
                        style = MaterialTheme.magicTypography.bodySmall,
                        color = mc.textSecondary,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = mc.textSecondary,
                )
            }

            // Privacy flags live on the user's own `user_profiles` row: nothing to edit without an account
            if (isAuthenticated) {
                HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))
                PrivacySection(
                    collectionPublic = uiState.collectionPublic,
                    wishlistPublic = uiState.wishlistPublic,
                    tradeListPublic = uiState.tradeListPublic,
                    pendingKeys = uiState.pendingPrivacyKeys,
                    onCollectionPublicChange = viewModel::setCollectionPublic,
                    onWishlistPublicChange = viewModel::setWishlistPublic,
                    onTradeListPublicChange = viewModel::setTradeListPublic,
                )
            }

            HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))
            NotificationsSection(
                pushEnabled = pushEnabled,
                showEventToggles = isAuthenticated,
                prefs = notificationPrefs,
                permissionGranted = permissionGrantedOnResume ||
                    (notificationPermissionState?.status?.isGranted ?: true),
                showRationale = notificationPermissionState?.status?.shouldShowRationale ?: false,
                onPushEnabledChange = viewModel::setPushNotificationsEnabled,
                onGroupChange = viewModel::setNotificationGroupEnabled,
                onRequestPermission = { notificationPermissionState?.launchPermissionRequest() },
                onOpenSystemSettings = {
                    // Not every ROM ships this activity; an unguarded start crashes the app
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        )
                    }.onFailure { e ->
                        recordSafeNonFatal("settings_open_notification_settings_failed", e)
                        toastState.show(systemSettingsUnavailableMsg, MagicToastType.ERROR)
                    }
                },
            )

            // Shown only while FeatureFlags.Gamification.ENABLED and not killed; see docs/hidden-features/.
            if (gamificationSettingsVisible) {
                HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))
                GamificationSection(
                    enabled = gamificationEnabled,
                    onEnabledChange = viewModel::setGamificationEnabled,
                )
            }

            HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))
            VoiceRecognitionSection(
                voiceModelStates = voiceModelStates,
                onDownloadVoiceModel = viewModel::downloadVoiceModel,
                onDeleteVoiceModel = viewModel::deleteVoiceModel,
            )

            HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))
            ThemeSelectorSection(
                currentTheme = uiState.currentTheme,
                onThemeSelected = viewModel::selectTheme,
            )
        }
        MagicToastHost(
            state = toastState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        } // Box
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val mc = MaterialTheme.magicColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.magicTypography.bodyMedium, color = mc.textPrimary)
            Text(
                subtitle,
                style = MaterialTheme.magicTypography.bodySmall,
                color = mc.textSecondary
            )
        }
        // The whole Row owns the toggle semantics, so the Switch must not be a second a11y node
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = mc.surface,
                checkedTrackColor = mc.primaryAccent,
                checkedIconColor = mc.primaryAccent,
            ),
        )
    }
}

// ── Privacy section ───────────────────────────────────────────────────────────

/**
 * Displays three privacy toggle rows: collection, wishlist, and trade list visibility.
 * Each toggle is stateless — it receives the current value and delegates changes upward.
 */
@Composable
private fun PrivacySection(
    collectionPublic: Boolean,
    wishlistPublic: Boolean,
    tradeListPublic: Boolean,
    pendingKeys: Set<String>,
    onCollectionPublicChange: (Boolean) -> Unit,
    onWishlistPublicChange: (Boolean) -> Unit,
    onTradeListPublicChange: (Boolean) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_section_privacy),
            style = MaterialTheme.magicTypography.titleMedium,
            color = mc.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        SettingsToggleItem(
            title = stringResource(R.string.settings_privacy_collection),
            subtitle = stringResource(R.string.settings_privacy_collection_subtitle),
            checked = collectionPublic,
            onCheckedChange = onCollectionPublicChange,
            enabled = "collection_public" !in pendingKeys,
        )
        SettingsToggleItem(
            title = stringResource(R.string.settings_privacy_wishlist),
            subtitle = stringResource(R.string.settings_privacy_wishlist_subtitle),
            checked = wishlistPublic,
            onCheckedChange = onWishlistPublicChange,
            enabled = "wishlist_public" !in pendingKeys,
        )
        SettingsToggleItem(
            title = stringResource(R.string.settings_privacy_trade_list),
            subtitle = stringResource(R.string.settings_privacy_trade_list_subtitle),
            checked = tradeListPublic,
            onCheckedChange = onTradeListPublicChange,
            enabled = "trade_list_public" !in pendingKeys,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_privacy_voice_title),
            style = MaterialTheme.magicTypography.labelMedium,
            color = mc.textPrimary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = stringResource(R.string.settings_privacy_voice_subtitle),
            style = MaterialTheme.magicTypography.bodySmall,
            color = mc.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

// ── Notifications section ───────────────────────────────────────────────────────

/**
 * Logical groupings of backend event types surfaced as a single toggle each. Toggling a group
 * applies the same value to every event in [eventTypes]. A group is considered ON only when all
 * of its events are enabled (key missing = enabled, opt-out model).
 */
private object NotificationGroups {
    val TRADE_PROPOSALS = listOf("trade_proposed", "trade_countered")
    val TRADE_UPDATES = listOf(
        "trade_accepted",
        "trade_declined",
        "trade_edited",
        "trade_cancelled",
        "trade_revoked",
        "trade_completed",
    )
    val FRIENDS = listOf("friend_request", "friend_accepted", "friend_invite_joined")
}

/**
 * A group toggle is ON when every event it controls is enabled. A missing key defaults to
 * enabled, so an untouched preference map yields all groups ON.
 */
private fun Map<String, Boolean>.isGroupEnabled(eventTypes: List<String>): Boolean =
    eventTypes.all { this[it] ?: true }

/**
 * Notifications preferences block: a master push toggle, per-group event toggles (shown only
 * when the master is ON), a permission rationale banner (Android 13+), and a deep link into the
 * system notification settings. Stateless — all values are hoisted to the ViewModel/permission state.
 */
@Composable
private fun NotificationsSection(
    pushEnabled: Boolean,
    showEventToggles: Boolean,
    prefs: Map<String, Boolean>,
    permissionGranted: Boolean,
    showRationale: Boolean,
    onPushEnabledChange: (Boolean) -> Unit,
    onGroupChange: (List<String>, Boolean) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_section_notifications),
            style = MaterialTheme.magicTypography.titleMedium,
            color = mc.textPrimary,
        )
        Spacer(Modifier.height(4.dp))

        // Permission rationale banner — only relevant when the OS permission is missing.
        if (!permissionGranted) {
            NotificationPermissionBanner(
                // shouldShowRationale becomes true after a first denial; once permanently denied
                // it stays false, so we route the user to the system settings instead.
                permanentlyDenied = !showRationale,
                onRequestPermission = onRequestPermission,
                onOpenSettings = onOpenSystemSettings,
            )
            Spacer(Modifier.height(8.dp))
        }

        NotificationToggleRow(
            icon = Icons.Filled.NotificationsActive,
            title = stringResource(R.string.settings_push_master),
            subtitle = stringResource(R.string.settings_push_master_subtitle),
            checked = pushEnabled,
            onCheckedChange = onPushEnabledChange,
        )

        // Per-event-type toggles are only meaningful while the master switch is ON, and they are
        // stored on the user's backend row, so they need a real account.
        if (pushEnabled && showEventToggles) {
            NotificationToggleRow(
                icon = Icons.Filled.SwapHoriz,
                title = stringResource(R.string.settings_push_trade_proposals),
                subtitle = stringResource(R.string.settings_push_trade_proposals_subtitle),
                checked = prefs.isGroupEnabled(NotificationGroups.TRADE_PROPOSALS),
                onCheckedChange = { onGroupChange(NotificationGroups.TRADE_PROPOSALS, it) },
            )
            NotificationToggleRow(
                icon = Icons.Filled.SwapHoriz,
                title = stringResource(R.string.settings_push_trade_updates),
                subtitle = stringResource(R.string.settings_push_trade_updates_subtitle),
                checked = prefs.isGroupEnabled(NotificationGroups.TRADE_UPDATES),
                onCheckedChange = { onGroupChange(NotificationGroups.TRADE_UPDATES, it) },
            )
            NotificationToggleRow(
                icon = Icons.Filled.People,
                title = stringResource(R.string.settings_push_friend_requests),
                subtitle = stringResource(R.string.settings_push_friend_requests_subtitle),
                checked = prefs.isGroupEnabled(NotificationGroups.FRIENDS),
                onCheckedChange = { onGroupChange(NotificationGroups.FRIENDS, it) },
            )
        }

        // System notification settings deep link.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenSystemSettings)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = mc.textSecondary,
                )
                Column {
                    Text(
                        stringResource(R.string.settings_push_open_system_settings),
                        style = MaterialTheme.magicTypography.bodyMedium,
                        color = mc.textPrimary,
                    )
                    Text(
                        stringResource(R.string.settings_push_open_system_settings_subtitle),
                        style = MaterialTheme.magicTypography.bodySmall,
                        color = mc.textSecondary,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = mc.textSecondary,
            )
        }
    }
}

/**
 * A single notification preference row: leading icon + title/subtitle + trailing [Switch].
 *
 * @param permanentlyDenied unused here; kept distinct from the banner composable for clarity.
 */
@Composable
private fun NotificationToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = mc.textSecondary)
            Column {
                Text(title, style = MaterialTheme.magicTypography.bodyMedium, color = mc.textPrimary)
                Text(
                    subtitle,
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textSecondary,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = mc.surface,
                checkedTrackColor = mc.primaryAccent,
                checkedIconColor = mc.primaryAccent,
            ),
        )
    }
}

/**
 * Inline banner prompting the user to grant the POST_NOTIFICATIONS permission. When the permission
 * is [permanentlyDenied] the action routes to the system settings instead of re-prompting.
 */
@Composable
private fun NotificationPermissionBanner(
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = mc.primaryAccent.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = mc.primaryAccent,
            )
            Text(
                text = stringResource(R.string.settings_push_permission_rationale),
                style = MaterialTheme.magicTypography.bodySmall,
                color = mc.textPrimary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = if (permanentlyDenied) onOpenSettings else onRequestPermission) {
                Text(
                    text = stringResource(
                        if (permanentlyDenied) {
                            R.string.settings_push_open_settings
                        } else {
                            R.string.settings_push_allow
                        },
                    ),
                    style = MaterialTheme.magicTypography.labelLarge,
                    color = mc.primaryAccent,
                )
            }
        }
    }
}

// ── Gamification section ────────────────────────────────────────────────────────

/**
 * Master gamification toggle. A single switch row that controls whether all gamification
 * UI (XP, levels, achievements, quests) is shown. Default is ON; turning it off hides the
 * Profile XP ring and (in later phases) the achievements/quests tabs. The engine keeps
 * recording progress silently, so re-enabling restores the user's true state.
 *
 * Stateless — the value and change handler are hoisted to the ViewModel.
 */
@Composable
private fun GamificationSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_section_gamification),
            style = MaterialTheme.magicTypography.titleMedium,
            color = mc.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        SettingsToggleItem(
            title = stringResource(R.string.settings_gamification_master),
            subtitle = stringResource(R.string.settings_gamification_master_subtitle),
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
    }
}

// ── Voice recognition section ───────────────────────────────────────────────────

/**
 * Lists every supported voice-recognition language with a state-appropriate trailing control:
 * a download button, a progress bar while downloading, an "Installed" badge + delete button when
 * ready, or a retry button on error. Stateless — all values are hoisted to the ViewModel.
 *
 * @param voiceModelStates Per-language model state.
 * @param onDownloadVoiceModel Called to (re)download a language's model.
 * @param onDeleteVoiceModel Called to remove a downloaded language's model.
 */
@Composable
private fun VoiceRecognitionSection(
    voiceModelStates: Map<VoiceLanguage, VoiceModelState>,
    onDownloadVoiceModel: (VoiceLanguage) -> Unit,
    onDeleteVoiceModel: (VoiceLanguage) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_voice_section_title),
            style = ty.titleMedium,
            color = mc.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        VoiceLanguage.entries.forEach { language ->
            val state = voiceModelStates[language] ?: VoiceModelState.NotDownloaded
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = language.displayFlag, style = ty.titleLarge)
                Text(
                    text = language.displayName,
                    style = ty.bodyMedium,
                    color = mc.textPrimary,
                    modifier = Modifier.weight(1f),
                )

                when (state) {
                    is VoiceModelState.NotDownloaded -> TextButton(onClick = { onDownloadVoiceModel(language) }) {
                        Text(
                            text = stringResource(R.string.voice_model_download),
                            style = ty.labelMedium,
                            color = mc.primaryAccent,
                        )
                    }
                    is VoiceModelState.Downloading -> LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.width(80.dp),
                        color = mc.primaryAccent,
                        trackColor = mc.surfaceVariant,
                    )
                    is VoiceModelState.Ready -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.voice_model_installed),
                            style = ty.labelSmall,
                            color = mc.primaryAccent,
                        )
                        IconButton(onClick = { onDeleteVoiceModel(language) }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.voice_model_delete),
                                tint = mc.textSecondary,
                            )
                        }
                    }
                    is VoiceModelState.Error -> TextButton(onClick = { onDownloadVoiceModel(language) }) {
                        Text(
                            text = stringResource(R.string.voice_model_retry),
                            style = ty.labelMedium,
                            color = mc.lifeNegative,
                        )
                    }
                }
            }
        }
    }
}

// ── Preferences section ───────────────────────────────────────────────────────

@Composable
private fun PreferencesSection(
    prefs: UserPreferences,
    onAppLanguage: (AppLanguage) -> Unit,
    onCardLanguage: (CardLanguage) -> Unit,
    onCurrency: (PreferredCurrency) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.preferences_title),
            style = MaterialTheme.magicTypography.titleMedium,
            color = mc.textPrimary,
        )
        Spacer(Modifier.height(4.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // App Language — single-select dropdown

            /*Text(stringResource(R.string.pref_app_language), style = MaterialTheme.magicTypography.bodySmall, color = mc.textSecondary)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AppLanguage.entries.forEach { language ->
                    val selected = language.displayName == prefs.appLanguage.displayName
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onAppLanguage(AppLanguage.fromCode(language.code) )},
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = mc.primaryAccent,
                                unselectedColor = mc.textDisabled,
                            ),
                        )
                        Text(
                            text = language.displayName,
                            style = MaterialTheme.magicTypography.bodySmall,
                            color = if (selected) mc.textPrimary else mc.textSecondary,
                        )
                    }
                }
            }

            HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))
            // Card Language — single-select dropdown

            Text(
                stringResource(R.string.pref_card_language),
                style = MaterialTheme.magicTypography.bodySmall,
                color = mc.textPrimary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CardLanguage.entries.filter { it != CardLanguage.GERMAN }.forEach { language ->
                    val selected = language.displayName == prefs.cardLanguage.displayName
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            onCardLanguage(
                                CardLanguage.fromCode(
                                    language.code
                                )
                            )
                        },
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = mc.primaryAccent,
                                unselectedColor = mc.textDisabled,
                            ),
                        )
                        Text(
                            text = language.displayName,
                            style = MaterialTheme.magicTypography.bodySmall,
                            color = if (selected) mc.textPrimary else mc.textSecondary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }*/


            // Currency — radio buttons
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.pref_currency),
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PreferredCurrency.entries.forEach { currency ->
                        val selected = currency == prefs.preferredCurrency
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { onCurrency(currency) },
                                ),
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = mc.primaryAccent,
                                    unselectedColor = mc.textDisabled,
                                ),
                            )
                            Text(
                                text = currency.displayName,
                                style = MaterialTheme.magicTypography.bodySmall,
                                color = if (selected) mc.textPrimary else mc.textSecondary,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


// ── Sections ──────────────────────────────────────────────────────────────────


@Composable
private fun ThemeSelectorSection(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.profile_section_themes),
            style = MaterialTheme.magicTypography.titleMedium,
            color = mc.textPrimary,
        )

        // Organizamos los temas en filas de 3 para evitar desbordamientos
        val themes = listOf(
            stringResource(R.string.theme_cosmos)         to AppTheme.ArcaneCosmos,
            stringResource(R.string.theme_neon_void)      to AppTheme.NeonVoid,
            stringResource(R.string.theme_grimoire)       to AppTheme.MedievalGrimoire,
            stringResource(R.string.theme_forest_murmur)  to AppTheme.ForestMurmur,
            stringResource(R.string.theme_ancient_oak)    to AppTheme.AncientOak,
            stringResource(R.string.theme_hallowed_print) to AppTheme.HallowedPrint,
            stringResource(R.string.theme_azure_flux)     to AppTheme.AzureFlux,
            stringResource(R.string.theme_planar_veil)    to AppTheme.PlanarVeil,
            stringResource(R.string.theme_venom_shade)    to AppTheme.VenomShade,
            stringResource(R.string.theme_glacial_edge)   to AppTheme.GlacialEdge,
            stringResource(R.string.theme_dusk_ember)     to AppTheme.DuskEmber,
            stringResource(R.string.theme_onyx_noir)      to AppTheme.OnyxNoir,
        )

        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            themes.chunked(3).forEach { rowThemes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
                ) {
                    rowThemes.forEach { (name, theme) ->
                        ThemeTile(
                            name = name,
                            previewColors = previewColorsFor(theme),
                            isSelected = currentTheme == theme,
                            onClick = { onThemeSelected(theme) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Keep the last row's tiles the same width as a full row's
                    repeat(3 - rowThemes.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Preview swatches read straight off the theme's own palette, so they can never drift from it. */
private fun previewColorsFor(theme: AppTheme): List<Color> = theme.colors().let { c ->
    listOf(c.background, c.primaryAccent, c.secondaryAccent)
}


@Composable
private fun ThemeTile(
    name: String,
    previewColors: List<Color>,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Surface(
        onClick = onClick,
        modifier = modifier.semantics {
            selected = isSelected
            role = Role.RadioButton
        },
        shape = CardShape,
        color = if (isSelected) mc.primaryAccent.copy(0.1f) else mc.surface,
        // An unselected tile's border must stay visible on HallowedPrint AND on the darkest palettes
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) mc.primaryAccent else mc.textDisabled,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(MaterialTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                previewColors.forEachIndexed { index, color ->
                    Box(
                        modifier = Modifier
                            .size(if (index == 0) 28.dp else 18.dp)
                            .clip(CircleShape)
                            .background(color)
                            // Every dot needs its own ring: a near-surface swatch is invisible without it
                            .border(1.dp, mc.textDisabled, CircleShape),
                    )
                }
            }
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.sm))
            Text(
                text = name,
                style = MaterialTheme.magicTypography.labelSmall,
                color = if (isSelected) mc.primaryAccent else mc.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

        }
    }
}