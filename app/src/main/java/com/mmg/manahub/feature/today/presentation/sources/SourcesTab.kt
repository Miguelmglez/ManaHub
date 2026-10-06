package com.mmg.manahub.feature.today.presentation.sources

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.ui.components.MagicActionRow
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaSize
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastState
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.SectionHeader
import com.mmg.manahub.core.ui.theme.BottomSheetShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.today.presentation.common.SourceAvatar
import com.mmg.manahub.feature.today.presentation.common.languageLabelRes
import com.mmg.manahub.feature.today.presentation.common.openSiteLabelRes
import com.mmg.manahub.feature.today.presentation.common.openSourceSite
import com.mmg.manahub.feature.today.presentation.common.sourceKindAndLanguage
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesTab(
    toastState: MagicToastState,
    onOpenInFeed: (sourceId: String) -> Unit,
    onDetailSheetVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SourcesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val toolbarColor = mc.primaryAccent.toArgb()
    val linkFailed = stringResource(R.string.today_link_open_failed)
    var pendingDelete by remember { mutableStateOf<ContentSource?>(null) }
    var selectedSource by remember { mutableStateOf<ContentSource?>(null) }
    var collapsedLanguages by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val currentVisibilityChanged by rememberUpdatedState(onDetailSheetVisibilityChanged)

    DisposableEffect(Unit) {
        onDispose { currentVisibilityChanged(false) }
    }

    val showSourceActions: (ContentSource) -> Unit = { source ->
        selectedSource = source
        onDetailSheetVisibilityChanged(true)
    }
    val hideSourceActions: () -> Unit = {
        selectedSource = null
        onDetailSheetVisibilityChanged(false)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SourcesEvent.Followed ->
                    toastState.show(context.getString(R.string.today_followed_toast, event.sourceName), MagicToastType.SUCCESS)
                is SourcesEvent.Unfollowed ->
                    toastState.show(context.getString(R.string.today_unfollowed_toast, event.sourceName), MagicToastType.INFO)
                is SourcesEvent.Deleted ->
                    toastState.show(context.getString(R.string.today_deleted_toast, event.sourceName), MagicToastType.INFO)
                SourcesEvent.ActionFailed ->
                    toastState.show(context.getString(R.string.today_action_failed), MagicToastType.ERROR)
            }
        }
    }

    val openSite: (ContentSource) -> Boolean = { source ->
        val opened = source.siteUrl?.let { openSourceSite(context, it, toolbarColor) } == true
        if (opened) viewModel.onSourceSiteOpened() else toastState.show(linkFailed, MagicToastType.ERROR)
        opened
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = spacing.lg, top = spacing.md, end = spacing.lg, bottom = spacing.lg + bottomInset),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        if (state.isLoading) {
            item(key = "loading") {
                Row(modifier = Modifier.fillMaxWidth().padding(spacing.xl), horizontalArrangement = Arrangement.Center) {
                    MagicLoadingSpinner(size = MagicLoadingSize.Medium)
                }
            }
            return@LazyColumn
        }

        item(key = "following_header") {
            Text(
                text = stringResource(R.string.today_sources_following, state.following.size),
                style = ty.titleMedium,
                color = mc.textPrimary,
                modifier = Modifier.padding(top = spacing.md),
            )
        }
        if (state.following.isEmpty()) {
            item(key = "following_empty") {
                Text(stringResource(R.string.today_sources_following_empty), style = ty.bodyMedium, color = mc.textSecondary)
            }
        }
        items(state.following, key = { "following_${it.id}" }) { source ->
            FollowingRow(
                source = source,
                onDetails = { showSourceActions(source) },
                onUnfollow = { viewModel.unfollow(source) },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "discover_header") {
            Text(
                text = stringResource(R.string.today_sources_discover),
                style = ty.titleMedium,
                color = mc.textPrimary,
                modifier = Modifier.padding(top = spacing.lg),
            )
        }
        if (state.discover.isEmpty()) {
            item(key = "discover_empty") {
                Text(stringResource(R.string.today_sources_discover_empty), style = ty.bodyMedium, color = mc.textSecondary)
            }
        }
        state.discover.forEach { group ->
            val expanded = group.language !in collapsedLanguages
            item(key = "discover_group_${group.language}") {
                SectionHeader(
                    title = stringResource(languageLabelRes(group.language)),
                    expanded = expanded,
                    onToggle = {
                        collapsedLanguages = if (expanded) collapsedLanguages + group.language else collapsedLanguages - group.language
                    },
                    icon = Icons.Default.Language,
                )
            }
            if (expanded) {
                items(group.sources, key = { "discover_${it.id}" }) { source ->
                    DiscoverRow(
                        source = source,
                        onDetails = { showSourceActions(source) },
                        onFollow = { viewModel.follow(source) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    selectedSource?.let { selected ->
        val currentSource = state.following.firstOrNull { it.id == selected.id }
            ?: state.discover.asSequence().flatMap { it.sources.asSequence() }
                .firstOrNull { it.id == selected.id }
            ?: selected
        SourceActionsSheet(
            source = currentSource,
            isFollowing = state.following.any { it.id == selected.id },
            toastState = toastState,
            onDismiss = hideSourceActions,
            onOpenSite = {
                if (openSite(currentSource)) hideSourceActions()
            },
            onOpenInFeed = {
                hideSourceActions()
                onOpenInFeed(currentSource.id)
            },
            onDelete = {
                pendingDelete = currentSource
                hideSourceActions()
            },
        )
    }

    pendingDelete?.let { source ->
        MagicAlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = stringResource(R.string.today_sources_delete_title, source.name),
            text = stringResource(R.string.today_sources_delete_text),
            confirmLabel = stringResource(R.string.today_sources_delete),
            onConfirm = {
                pendingDelete = null
                viewModel.delete(source)
            },
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = { pendingDelete = null },
            confirmColor = MagicCtaColor.Error,
        )
    }
}

@Composable
private fun FollowingRow(
    source: ContentSource,
    onDetails: () -> Unit,
    onUnfollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SourceRow(
        source = source,
        actionLabel = stringResource(R.string.today_source_unfollow),
        actionIcon = Icons.Default.BookmarkRemove,
        actionStyle = MagicCtaStyle.Outlined,
        onDetails = onDetails,
        onAction = onUnfollow,
        modifier = modifier,
    )
}

@Composable
private fun DiscoverRow(
    source: ContentSource,
    onDetails: () -> Unit,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SourceRow(
        source = source,
        actionLabel = stringResource(R.string.today_sources_follow),
        actionIcon = Icons.Default.Add,
        actionStyle = MagicCtaStyle.Filled,
        onDetails = onDetails,
        onAction = onFollow,
        modifier = modifier,
    )
}

@Composable
private fun SourceRow(
    source: ContentSource,
    actionLabel: String,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    actionStyle: MagicCtaStyle,
    onDetails: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val detailsDescription = stringResource(R.string.today_source_options_a11y, source.name)

    Surface(
        shape = CardShape,
        color = mc.surface,
        border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.55f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onDetails)
                .semantics { contentDescription = detailsDescription }
                .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                SourceAvatar(source)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        source.name,
                        style = ty.titleMedium,
                        color = mc.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        sourceKindAndLanguage(source),
                        style = ty.bodySmall,
                        color = mc.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(Modifier.fillMaxWidth()) {
                MagicCtaButton(
                    onClick = onAction,
                    text = actionLabel,
                    icon = { Icon(actionIcon, contentDescription = null) },
                    style = actionStyle,
                    color = MagicCtaColor.Primary,
                    size = MagicCtaSize.Normal,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceActionsSheet(
    source: ContentSource,
    isFollowing: Boolean,
    toastState: MagicToastState,
    onDismiss: () -> Unit,
    onOpenSite: () -> Unit,
    onOpenInFeed: () -> Unit,
    onDelete: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden },
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = BottomSheetShape,
        containerColor = mc.backgroundSecondary,
        contentWindowInsets = { WindowInsets(0) },
        dragHandle = null,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.92f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(spacing.xxl + spacing.lg)
                        .padding(horizontal = spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = mc.textSecondary,
                        )
                    }
                    Text(
                        text = stringResource(R.string.today_source_actions_title),
                        style = ty.titleLarge,
                        color = mc.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg),
                    shape = CardShape,
                    color = mc.surface,
                    border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.55f)),
                ) {
                    Row(
                        modifier = Modifier.padding(spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        SourceAvatar(source)
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            Text(source.name, style = ty.titleMedium, color = mc.textPrimary)
                            Text(sourceKindAndLanguage(source), style = ty.bodySmall, color = mc.textSecondary)
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    if (source.siteUrl != null) {
                        MagicActionRow(
                            icon = Icons.Default.Language,
                            title = stringResource(openSiteLabelRes(source.type)),
                            subtitle = stringResource(R.string.today_source_open_site_desc),
                            onClick = onOpenSite,
                            accentColor = mc.secondaryAccent,
                        )
                    }
                    if (isFollowing) {
                        MagicActionRow(
                            icon = Icons.Default.Visibility,
                            title = stringResource(R.string.today_view_feed),
                            subtitle = stringResource(R.string.today_source_view_feed_desc),
                            onClick = onOpenInFeed,
                            accentColor = mc.goldMtg,
                        )
                    }
                    if (isFollowing && !source.isDefault) {
                        MagicActionRow(
                            icon = Icons.Default.DeleteOutline,
                            title = stringResource(R.string.today_sources_delete),
                            subtitle = stringResource(R.string.today_sources_delete_text),
                            onClick = onDelete,
                            accentColor = mc.lifeNegative,
                        )
                    }
                }
            }
            MagicToastHost(state = toastState, modifier = Modifier.matchParentSize())
        }
    }
}
