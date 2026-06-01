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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.AppLanguage
import com.mmg.manahub.core.domain.model.CardLanguage
import com.mmg.manahub.core.domain.model.NewsLanguage
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.domain.model.UserPreferences
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.AppTheme
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onManageNewsSources: () -> Unit = {},
    onManageTagDictionary: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val prefsState by viewModel.prefsState.collectAsStateWithLifecycle()
    val pushEnabled by viewModel.pushNotificationsEnabled.collectAsStateWithLifecycle()
    val notificationPrefs by viewModel.notificationPrefs.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val activity = context as? Activity
    val privacyToastState = rememberMagicToastState()
    val privacyErrorMsg = stringResource(R.string.settings_privacy_error)

    // POST_NOTIFICATIONS is a runtime permission only on Android 13+ (API 33). On older
    // devices notifications are granted at install time, so the rationale banner is skipped.
    val notificationPermissionState: PermissionState? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            null
        }

    LaunchedEffect(Unit) {
        viewModel.appLanguageChanged.collect { activity?.recreate() }
    }

    LaunchedEffect(uiState.privacyToastMessage) {
        val msg = uiState.privacyToastMessage ?: return@LaunchedEffect
        privacyToastState.show(
            msg,
            if (uiState.privacyToastIsError) MagicToastType.ERROR else MagicToastType.SUCCESS,
        )
        viewModel.clearPrivacyToast()
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
                onNewsLanguages = viewModel::setNewsLanguages,
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

             Spacer(Modifier.height(16.dp))
             HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))
             Text(
                 stringResource(R.string.settings_section_news),
                 style = MaterialTheme.magicTypography.titleMedium,
                 color = mc.textPrimary,
                 modifier = Modifier.padding(horizontal = 16.dp),
             )

             Row(
                 modifier = Modifier
                     .fillMaxWidth()
                     .clickable(onClick = onManageNewsSources)
                     .padding(horizontal = 16.dp, vertical = 12.dp),
                 verticalAlignment = Alignment.CenterVertically,
                 horizontalArrangement = Arrangement.SpaceBetween,
             ) {
                 Column(modifier = Modifier.weight(1f)) {
                     Text(
                         stringResource(R.string.settings_manage_news_sources),
                         style = MaterialTheme.magicTypography.bodyMedium,
                         color = mc.textPrimary,
                     )
                     Text(
                         stringResource(R.string.settings_manage_news_sources_subtitle),
                         style = MaterialTheme.magicTypography.bodySmall,
                         color = mc.textSecondary,
                     )
                 }
                 Icon(
                     Icons.AutoMirrored.Filled.KeyboardArrowRight,
                     contentDescription = null,
                     tint = mc.textSecondary,
                 )
             }*/

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

            HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))
            PrivacySection(
                collectionPublic = uiState.collectionPublic,
                wishlistPublic = uiState.wishlistPublic,
                tradeListPublic = uiState.tradeListPublic,
                onCollectionPublicChange = { viewModel.setCollectionPublic(it, privacyErrorMsg) },
                onWishlistPublicChange = { viewModel.setWishlistPublic(it, privacyErrorMsg) },
                onTradeListPublicChange = { viewModel.setTradeListPublic(it, privacyErrorMsg) },
            )

            HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))
            NotificationsSection(
                pushEnabled = pushEnabled,
                prefs = notificationPrefs,
                permissionGranted = notificationPermissionState?.status?.isGranted ?: true,
                showRationale = notificationPermissionState?.status?.shouldShowRationale ?: false,
                onPushEnabledChange = viewModel::setPushNotificationsEnabled,
                onGroupChange = viewModel::setNotificationGroupEnabled,
                onRequestPermission = { notificationPermissionState?.launchPermissionRequest() },
                onOpenSystemSettings = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                },
            )

            HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))
            ThemeSelectorSection(
                currentTheme = uiState.currentTheme,
                onThemeSelected = viewModel::selectTheme,
            )
        }
        MagicToastHost(
            state = privacyToastState,
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
) {
    val mc = MaterialTheme.magicColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
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
        )
        SettingsToggleItem(
            title = stringResource(R.string.settings_privacy_wishlist),
            subtitle = stringResource(R.string.settings_privacy_wishlist_subtitle),
            checked = wishlistPublic,
            onCheckedChange = onWishlistPublicChange,
        )
        SettingsToggleItem(
            title = stringResource(R.string.settings_privacy_trade_list),
            subtitle = stringResource(R.string.settings_privacy_trade_list_subtitle),
            checked = tradeListPublic,
            onCheckedChange = onTradeListPublicChange,
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

        // Per-event-type toggles are only meaningful while the master switch is ON.
        if (pushEnabled) {
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
            onCheckedChange = onCheckedChange,
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

// ── Preferences section ───────────────────────────────────────────────────────

@Composable
private fun PreferencesSection(
    prefs: UserPreferences,
    onAppLanguage: (AppLanguage) -> Unit,
    onCardLanguage: (CardLanguage) -> Unit,
    onNewsLanguages: (Set<NewsLanguage>) -> Unit,
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
/*
            // News Language — multi-select checkboxes
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.pref_news_language),
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    NewsLanguage.entries.filter { it != NewsLanguage.GERMAN }.forEach { lang ->
                        val checked = lang in prefs.newsLanguages
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                val updated = if (checked) {
                                    prefs.newsLanguages - lang
                                } else {
                                    prefs.newsLanguages + lang
                                }
                                if (updated.isNotEmpty()) onNewsLanguages(updated)
                            },
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = mc.primaryAccent,
                                    uncheckedColor = mc.textDisabled,
                                ),
                            )
                            Text(
                                text = lang.displayName,
                                style = MaterialTheme.magicTypography.bodySmall,
                                color = if (checked) mc.textPrimary else mc.textSecondary,
                            )
                        }
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
                            modifier = Modifier.clickable { onCurrency(currency) },
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
            Triple(stringResource(R.string.theme_cosmos),         "✨", AppTheme.ArcaneCosmos),
            Triple(stringResource(R.string.theme_neon_void),      "⚡", AppTheme.NeonVoid),
            Triple(stringResource(R.string.theme_grimoire),       "📜", AppTheme.MedievalGrimoire),
            Triple(stringResource(R.string.theme_forest_murmur),  "🍃", AppTheme.ForestMurmur),
            Triple(stringResource(R.string.theme_ancient_oak),    "🪵", AppTheme.AncientOak),
            Triple(stringResource(R.string.theme_hallowed_print), "📖", AppTheme.HallowedPrint),
            Triple(stringResource(R.string.theme_azure_flux),     "🌐", AppTheme.AzureFlux),
            Triple(stringResource(R.string.theme_planar_veil),    "🌌", AppTheme.PlanarVeil),
            Triple(stringResource(R.string.theme_venom_shade),    "🧪", AppTheme.VenomShade),
            Triple(stringResource(R.string.theme_glacial_edge),   "❄️", AppTheme.GlacialEdge),
            Triple(stringResource(R.string.theme_dusk_ember),     "🌅", AppTheme.DuskEmber),
            Triple(stringResource(R.string.theme_onyx_noir),      "🍸", AppTheme.OnyxNoir),
        )

        themes.chunked(3).forEach { rowThemes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowThemes.forEach { (name, emoji, theme) ->
                    ThemeTile(
                        name = name,
                        emoji = emoji,
                        previewColors = getPreviewColorsForTheme(theme),
                        isSelected = currentTheme == theme,
                        onClick = { onThemeSelected(theme) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Rellenar con espacios vacíos si la fila no está completa para mantener el peso uniforme
                repeat(3 - rowThemes.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun getPreviewColorsForTheme(theme: AppTheme): List<Color> = when (theme) {
    AppTheme.NeonVoid         -> listOf(Color(0xFF14020D), Color(0xFFFF6AD5), Color(0xFF00E5FF))
    AppTheme.MedievalGrimoire -> listOf(Color(0xFF140202), Color(0xFFFF3131), Color(0xFFC9A55C))
    AppTheme.ArcaneCosmos     -> listOf(Color(0xFF010C14), Color(0xFF00F5FF), Color(0xFFFF7F50))
    AppTheme.ForestMurmur     -> listOf(Color(0xFF010A03), Color(0xFFF2FAEC), Color(0xFF2E7D32))
    AppTheme.AncientOak       -> listOf(Color(0xFF140D02), Color(0xFFE0B038), Color(0xFFC68A1F))
    AppTheme.HallowedPrint    -> listOf(Color(0xFFF5F1E5), Color(0xFF1F1B16), Color(0xFF7A1F2B))
    AppTheme.AzureFlux        -> listOf(Color(0xFF02061F), Color(0xFF3B82F6), Color(0xFFFF6AD5))
    AppTheme.PlanarVeil       -> listOf(Color(0xFF110524), Color(0xFF9B5DE5), Color(0xFFFFC857))
    AppTheme.VenomShade       -> listOf(Color(0xFF0B0814), Color(0xFFB4FF1A), Color(0xFFC24DFF))
    AppTheme.GlacialEdge      -> listOf(Color(0xFF050B1A), Color(0xFFB8E0FF), Color(0xFFC7A7FF))
    AppTheme.DuskEmber        -> listOf(Color(0xFF1F0A14), Color(0xFFFF9B6A), Color(0xFFE8B85F))
    AppTheme.OnyxNoir         -> listOf(Color(0xFF08080C), Color(0xFFD8D8E0), Color(0xFFE8C988))
}


@Composable
private fun ThemeTile(
    name: String,
    emoji: String,
    previewColors: List<Color>,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) mc.primaryAccent.copy(0.1f) else mc.surface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 0.5.dp,
            color = if (isSelected) mc.primaryAccent else mc.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center, // Center content vertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                previewColors.forEachIndexed { index, color ->
                    Box(
                        modifier = Modifier
                            .size(if (index == 0) 28.dp else 18.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (index == 0)
                                    Modifier.border(
                                        1.5.dp,
                                        previewColors.getOrElse(1) { Color.White }.copy(0.5f),
                                        CircleShape,
                                    )
                                else Modifier
                            ),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
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