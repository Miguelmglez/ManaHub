package com.mmg.manahub.feature.today.presentation.sources

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.ui.components.AvatarImage
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicToastState
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.SectionHeader
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.TimeAgoFormatter
import com.mmg.manahub.feature.today.presentation.common.languageLabelRes
import com.mmg.manahub.feature.today.presentation.common.openSiteLabelRes
import com.mmg.manahub.feature.today.presentation.common.openSourceSite
import com.mmg.manahub.feature.today.presentation.common.sourceInitials
import com.mmg.manahub.feature.today.presentation.common.sourceKindAndLanguage
import org.koin.androidx.compose.koinViewModel

@Composable
fun SourcesTab(
    toastState: MagicToastState,
    onAddSource: () -> Unit,
    onOpenInFeed: (sourceId: String) -> Unit,
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
    var collapsedLanguages by rememberSaveable { mutableStateOf(emptySet<String>()) }

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

    val openSite: (ContentSource) -> Unit = { source ->
        val opened = source.siteUrl?.let { openSourceSite(context, it, toolbarColor) } == true
        if (opened) viewModel.onSourceSiteOpened() else toastState.show(linkFailed, MagicToastType.ERROR)
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = spacing.lg, top = spacing.md, end = spacing.lg, bottom = spacing.lg + bottomInset),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item(key = "add_source") {
            MagicCtaButton(
                onClick = onAddSource,
                text = stringResource(R.string.today_sources_add),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
                onOpenInFeed = { onOpenInFeed(source.id) },
                onOpenSite = { openSite(source) },
                onUnfollow = { viewModel.unfollow(source) },
                onDelete = { pendingDelete = source },
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
                    DiscoverRow(source = source, onFollow = { viewModel.follow(source) }, modifier = Modifier.animateItem())
                }
            }
        }
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
    onOpenInFeed: () -> Unit,
    onOpenSite: () -> Unit,
    onUnfollow: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val updated = if (source.lastFetchedAt > 0L) {
        stringResource(R.string.today_sources_updated, TimeAgoFormatter.format(source.lastFetchedAt))
    } else {
        stringResource(R.string.today_sources_not_updated)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinRowHeight)
            .clickable(
                onClickLabel = stringResource(R.string.today_sources_open_in_feed_a11y, source.name),
                role = Role.Button,
                onClick = onOpenInFeed,
            )
            .padding(vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        AvatarImage(avatarUrl = source.iconUrl, initials = sourceInitials(source.name), size = ROW_AVATAR_SIZE)
        Column(modifier = Modifier.weight(1f)) {
            Text(source.name, style = ty.bodyLarge, color = mc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = stringResource(R.string.today_source_subtitle, sourceKindAndLanguage(source), updated),
                style = ty.bodySmall,
                color = mc.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (source.siteUrl != null) {
            IconButton(onClick = onOpenSite) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = stringResource(openSiteLabelRes(source.type)),
                    tint = mc.textSecondary,
                )
            }
        }
        if (source.isDefault) {
            MagicCtaButton(
                onClick = onUnfollow,
                text = stringResource(R.string.today_source_unfollow),
                style = MagicCtaStyle.Ghost,
                color = MagicCtaColor.Neutral,
            )
        } else {
            MagicCtaButton(
                onClick = onDelete,
                text = stringResource(R.string.today_sources_delete),
                style = MagicCtaStyle.Ghost,
                color = MagicCtaColor.Error,
            )
        }
    }
}

@Composable
private fun DiscoverRow(source: ContentSource, onFollow: () -> Unit, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        AvatarImage(avatarUrl = source.iconUrl, initials = sourceInitials(source.name), size = ROW_AVATAR_SIZE)
        Column(modifier = Modifier.weight(1f)) {
            Text(source.name, style = ty.bodyLarge, color = mc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sourceKindAndLanguage(source), style = ty.bodySmall, color = mc.textSecondary)
        }
        MagicCtaButton(
            onClick = onFollow,
            text = stringResource(R.string.today_sources_follow),
            style = MagicCtaStyle.Outlined,
            color = MagicCtaColor.Primary,
        )
    }
}

private const val ROW_AVATAR_SIZE = 40
private val MinRowHeight = 56.dp
