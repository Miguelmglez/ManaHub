package com.mmg.manahub.feature.today.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.ManaTabItem
import com.mmg.manahub.core.ui.components.ManaTabRow
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.today.presentation.events.EventsTab
import com.mmg.manahub.feature.today.presentation.feed.FeedTab
import com.mmg.manahub.feature.today.presentation.feed.FeedViewModel
import com.mmg.manahub.feature.today.presentation.saved.SavedTab
import com.mmg.manahub.feature.today.presentation.sources.AddSourceSheet
import com.mmg.manahub.feature.today.presentation.sources.SourcesTab
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

/**
 * MTG Today: Feed · Events · Saved · Sources. Cross-tab moves (a followed source → the Feed filtered by it,
 * "Discover sources" → Sources) and the Add Source sheet are hoisted here.
 */
@Composable
fun MtgTodayScreen(
    initialTab: TodayTab,
    onBack: () -> Unit,
    onVideoClick: (videoId: String, title: String) -> Unit,
    feedViewModel: FeedViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val toastState = rememberMagicToastState()
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
    var showAddSource by rememberSaveable { mutableStateOf(false) }
    val feedState by feedViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(selectedTab) { feedViewModel.onTabSelected(selectedTab.routeId) }

    Box(modifier = Modifier.fillMaxSize()) {
        ThemeBackground(modifier = Modifier.fillMaxSize())
        Column(modifier = Modifier.fillMaxSize()) {
            TodayTopBar(
                showSearchAction = selectedTab == TodayTab.FEED,
                searchActive = selectedTab == TodayTab.FEED && feedState.searchActive,
                searchQuery = feedState.searchQuery,
                onBack = onBack,
                onSearchActiveChanged = feedViewModel::onSearchActiveChanged,
                onSearchQueryChanged = feedViewModel::onSearchQueryChanged,
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
            when (selectedTab) {
                TodayTab.FEED -> FeedTab(
                    viewModel = feedViewModel,
                    toastState = toastState,
                    onVideoClick = onVideoClick,
                    onAddSource = { showAddSource = true },
                    onDiscoverSources = { selectedTab = TodayTab.SOURCES },
                )
                TodayTab.EVENTS -> EventsTab(toastState = toastState)
                TodayTab.SAVED -> SavedTab(toastState = toastState, onVideoClick = onVideoClick)
                TodayTab.SOURCES -> SourcesTab(
                    toastState = toastState,
                    onAddSource = { showAddSource = true },
                    onOpenInFeed = { sourceId ->
                        feedViewModel.selectSource(sourceId)
                        selectedTab = TodayTab.FEED
                    },
                )
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

        MagicToastHost(state = toastState)
    }
}

@Composable
private fun TodayTopBar(
    showSearchAction: Boolean,
    searchActive: Boolean,
    searchQuery: String,
    onBack: () -> Unit,
    onSearchActiveChanged: (Boolean) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    val focusRequester = remember { FocusRequester() }
    Surface(color = mc.backgroundSecondary) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = TopBarMinHeight)
                .padding(horizontal = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = mc.textPrimary)
            }
            if (searchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text(stringResource(R.string.today_search_hint), color = mc.textDisabled) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = mc.primaryAccent,
                        unfocusedBorderColor = mc.surfaceVariant,
                        cursorColor = mc.primaryAccent,
                        focusedTextColor = mc.textPrimary,
                        unfocusedTextColor = mc.textPrimary,
                    ),
                    shape = CardShape,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                IconButton(onClick = { onSearchActiveChanged(false) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.today_search_close), tint = mc.textSecondary)
                }
            } else {
                Text(
                    text = stringResource(R.string.today_title),
                    style = MaterialTheme.magicTypography.titleLarge,
                    color = mc.textPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = spacing.sm),
                )
                if (showSearchAction) {
                    IconButton(onClick = { onSearchActiveChanged(true) }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search), tint = mc.textSecondary)
                    }
                }
            }
        }
    }
}

private val TopBarMinHeight = 56.dp
