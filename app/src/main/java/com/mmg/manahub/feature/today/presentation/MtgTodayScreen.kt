package com.mmg.manahub.feature.today.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ManaTabItem
import com.mmg.manahub.core.ui.components.ManaTabRow
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.BottomSheetShape
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.today.presentation.events.EventsTab
import com.mmg.manahub.feature.today.presentation.feed.FeedTab
import com.mmg.manahub.feature.today.presentation.feed.FeedViewModel
import com.mmg.manahub.feature.today.presentation.sources.AddSourceSheet
import com.mmg.manahub.feature.today.presentation.sources.SourcesTab
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

/** Today shell with Feed, Events and Trends, and source management in a modal sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MtgTodayScreen(
    initialTab: TodayTab,
    onBack: () -> Unit,
    onVideoClick: (videoId: String, title: String) -> Unit,
    feedViewModel: FeedViewModel = koinViewModel(),
) {
    val tabStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val context = LocalContext.current
    val toastState = rememberMagicToastState()
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
    var showSources by rememberSaveable { mutableStateOf(false) }
    var showSourceActions by remember { mutableStateOf(false) }
    var showAddSource by rememberSaveable { mutableStateOf(false) }
    val feedState by feedViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(selectedTab) { feedViewModel.onTabSelected(selectedTab.routeId) }

    Box(modifier = Modifier.fillMaxSize()) {
        ThemeBackground(modifier = Modifier.fillMaxSize())
        Column(modifier = Modifier.fillMaxSize()) {
            TodayTopBar(
                showSources = selectedTab == TodayTab.FEED,
                onBack = onBack,
                onSources = { feedViewModel.onSourcesOpened(); showSources = true },
            )
            ManaTabRow(
                items = TodayTab.entries.map { tab ->
                    ManaTabItem(
                        label = stringResource(tab.labelRes).uppercase(Locale.getDefault()),
                        selected = tab == selectedTab,
                        onClick = { selectedTab = tab },
                    )
                },
            )
            tabStateHolder.SaveableStateProvider(selectedTab.routeId) {
            when (selectedTab) {
                TodayTab.FEED -> FeedTab(
                    viewModel = feedViewModel,
                    toastState = toastState,
                    onVideoClick = onVideoClick,
                    onAddSource = { showAddSource = true },
                    onDiscoverSources = { feedViewModel.onSourcesOpened(); showSources = true },
                )
                TodayTab.EVENTS -> EventsTab(toastState = toastState)
                TodayTab.TRENDS -> EventsTab(toastState = toastState, trends = true)

            }
            }
        }

        if (showSources) {
            val sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = { it != SheetValue.Hidden }
            )
            ModalBottomSheet(
                onDismissRequest = { showSources = false },
                sheetState = sheetState,
                shape = BottomSheetShape,
                containerColor = MaterialTheme.magicColors.backgroundSecondary,
                contentWindowInsets = { WindowInsets(0) },
                dragHandle = null,
            ) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showSources = false }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.action_close),
                                    tint = MaterialTheme.magicColors.textSecondary
                                )
                            }
                            Text(
                                text = stringResource(R.string.today_manage_sources),
                                style = MaterialTheme.magicTypography.titleLarge,
                                color = MaterialTheme.magicColors.textPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                        SourcesTab(
                            toastState = toastState,
                            onDetailSheetVisibilityChanged = { showSourceActions = it },
                            onOpenInFeed = { id ->
                                feedViewModel.onSavedOnlyChanged(false)
                                feedViewModel.selectSource(id)
                                selectedTab = TodayTab.FEED
                                showSources = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            color = MaterialTheme.magicColors.backgroundSecondary,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = MaterialTheme.spacing.lg,
                                        vertical = MaterialTheme.spacing.md
                                    )
                                    .navigationBarsPadding()
                            ) {
                                MagicCtaButton(
                                    onClick = {
                                        showSources = false
                                        showAddSource = true
                                    },
                                    text = stringResource(R.string.today_sources_add),
                                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    if (!showSourceActions) MagicToastHost(state = toastState)
                }
            }
        }
        if (showAddSource) {
            AddSourceSheet(
                onDismiss = { showAddSource = false },
                onFollowed = { name ->
                    showAddSource = false
                    toastState.show(context.getString(R.string.today_followed_toast, name), MagicToastType.SUCCESS)
                },
            )
        }

        if (!showSources && !showSourceActions) MagicToastHost(state = toastState)
    }
}

@Composable
private fun TodayTopBar(showSources: Boolean, onBack: () -> Unit, onSources: () -> Unit) {
    val mc = MaterialTheme.magicColors
    Surface(color = mc.backgroundSecondary) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp).padding(horizontal = MaterialTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = mc.textPrimary)
            }
            Text(stringResource(R.string.today_title), style = MaterialTheme.magicTypography.titleLarge,
                color = mc.textPrimary, modifier = Modifier.weight(1f).padding(start = MaterialTheme.spacing.sm))
            if (showSources) IconButton(onClick = onSources) {
                Icon(Icons.Default.Settings, stringResource(R.string.today_manage_sources), tint = mc.textPrimary)
            }
        }
    }
}
