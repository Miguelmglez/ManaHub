package com.mmg.manahub.feature.today.presentation.events

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.news.ReleaseStatus
import com.mmg.manahub.core.model.news.UpcomingRelease
import com.mmg.manahub.core.ui.components.CardRarity
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicFilterChip
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicToastState
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.SetSymbol
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.TimeAgoFormatter
import com.mmg.manahub.feature.today.presentation.common.openInBrowser
import kotlinx.datetime.LocalDate
import org.koin.androidx.compose.koinViewModel
import java.net.URLEncoder

@Composable
fun EventsTab(
    toastState: MagicToastState,
    modifier: Modifier = Modifier,
    viewModel: EventsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    val toolbarColor = mc.primaryAccent.toArgb()
    val linkFailed = stringResource(R.string.today_link_open_failed)
    val open: (url: String, onOpened: () -> Unit) -> Unit = { url, onOpened ->
        if (openInBrowser(context, url, toolbarColor)) onOpened() else toastState.show(linkFailed, MagicToastType.ERROR)
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = spacing.md, bottom = spacing.lg + bottomInset),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item(key = "releases_title") { SectionTitle(stringResource(R.string.today_events_releases_title)) }
        when {
            state.releasesLoading -> item(key = "releases_loading") {
                Row(modifier = Modifier.fillMaxWidth().padding(spacing.lg), horizontalArrangement = Arrangement.Center) {
                    MagicLoadingSpinner(size = MagicLoadingSize.Small)
                }
            }
            state.releasesFailed -> item(key = "releases_error") {
                InlineErrorState(
                    message = stringResource(R.string.today_events_releases_error),
                    retryLabel = stringResource(R.string.action_retry),
                    onRetry = viewModel::loadReleases,
                    modifier = Modifier.padding(horizontal = spacing.lg),
                )
            }
            state.releases.isEmpty() -> item(key = "releases_empty") {
                EmptyState(
                    title = stringResource(R.string.today_events_releases_empty),
                    icon = Icons.Default.EventBusy,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> items(state.releases, key = { "release_${it.set.code}" }) { release ->
                ReleaseRow(
                    release = release,
                    onClick = { open(MetagameLinkCatalog.scryfallSetUrl(release.set.code), viewModel::onReleaseOpened) },
                    modifier = Modifier.padding(horizontal = spacing.lg),
                )
            }
        }

        item(key = "locator_title") { SectionTitle(stringResource(R.string.today_events_locator_title)) }
        item(key = "locator") {
            EventLocator(
                postalCode = state.postalCode,
                onPostalCodeChanged = viewModel::onPostalCodeChanged,
                onFindEvents = {
                    val encoded = URLEncoder.encode(state.postalCode.trim(), "UTF-8")
                    open(MetagameLinkCatalog.eventLocatorUrl(encoded), viewModel::onLocatorOpened)
                },
                modifier = Modifier.padding(horizontal = spacing.lg),
            )
        }
        item(key = "twitch") {
            LinkRow(
                title = stringResource(R.string.today_events_twitch_title),
                description = stringResource(R.string.today_events_twitch_desc),
                icon = Icons.Default.LiveTv,
                onClick = { open(MetagameLinkCatalog.TWITCH_URL) { viewModel.onLinkOpened("twitch_magic") } },
                modifier = Modifier.padding(horizontal = spacing.lg),
            )
        }

        item(key = "pro_tour_title") { SectionTitle(stringResource(R.string.today_events_pro_tour_title)) }
        item(key = "pro_tour") {
            ProTourStrip(
                items = state.proTourItems,
                onOpen = { item -> open(item.url) { viewModel.onLinkOpened("pro_tour_article") } },
            )
        }
        item(key = "pro_tour_more") {
            LinkRow(
                title = stringResource(R.string.today_events_more_magicgg),
                description = null,
                icon = Icons.Default.Info,
                onClick = { open(MetagameLinkCatalog.MAGIC_GG_NEWS_URL) { viewModel.onLinkOpened("magicgg_news") } },
                modifier = Modifier.padding(horizontal = spacing.lg),
            )
        }

        item(key = "metagame_title") { SectionTitle(stringResource(R.string.today_events_metagame_title)) }
        item(key = "formats") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                contentPadding = PaddingValues(horizontal = spacing.lg),
            ) {
                items(MetagameFormat.entries, key = { it.id }) { format ->
                    MagicFilterChip(
                        selected = format == state.selectedFormat,
                        onClick = { viewModel.onFormatSelected(format) },
                        label = stringResource(format.labelRes),
                    )
                }
            }
        }
        items(MetagameLinkCatalog.links, key = { "link_${it.id}" }) { link ->
            val formatName = stringResource(state.selectedFormat.labelRes)
            LinkRow(
                title = stringResource(link.titleRes),
                description = if (link.formatScoped) stringResource(link.descriptionRes, formatName) else stringResource(link.descriptionRes),
                icon = Icons.AutoMirrored.Filled.ListAlt,
                onClick = { open(link.urlFor(state.selectedFormat)) { viewModel.onLinkOpened(link.id) } },
                modifier = Modifier.padding(horizontal = spacing.lg),
            )
        }

        state.latestLimitedSet?.let { set ->
            item(key = "limited_title") { SectionTitle(stringResource(R.string.today_events_limited_title)) }
            item(key = "limited_17lands") {
                LinkRow(
                    title = stringResource(R.string.today_events_17lands_title),
                    description = stringResource(R.string.today_events_17lands_desc, set.name),
                    icon = Icons.Default.Style,
                    onClick = {
                        open(MetagameLinkCatalog.seventeenLandsRatingsUrl(set.code)) { viewModel.onLinkOpened("17lands_card_ratings") }
                    },
                    modifier = Modifier.padding(horizontal = spacing.lg),
                )
            }
        }

        item(key = "attribution") {
            Text(
                text = stringResource(R.string.today_events_attribution),
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textDisabled,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val spacing = MaterialTheme.spacing
    Text(
        text = text,
        style = MaterialTheme.magicTypography.titleMedium,
        color = MaterialTheme.magicColors.textPrimary,
        modifier = Modifier.padding(start = spacing.lg, end = spacing.lg, top = spacing.md),
    )
}

@Composable
private fun ReleaseRow(release: UpcomingRelease, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val date = formatReleaseDate(release.releaseDate)
    val countdown = releaseCountdown(release)
    val description = stringResource(R.string.today_release_a11y, release.set.name, date, countdown)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinRowHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(SetBadgeSize)
                .background(mc.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SetSymbol(setCode = release.set.code, rarity = CardRarity.RARE, size = SetSymbolSize)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(release.set.name, style = ty.bodyLarge, color = mc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(date, style = ty.labelSmall, color = mc.textSecondary)
        }
        Surface(
            shape = CircleShape,
            color = if (release.status == ReleaseStatus.OUT_NOW) mc.lifePositive.copy(alpha = 0.15f) else mc.primaryAccent.copy(alpha = 0.12f),
        ) {
            Text(
                text = countdown,
                style = ty.labelMedium,
                color = if (release.status == ReleaseStatus.OUT_NOW) mc.lifePositive else mc.primaryAccent,
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
            )
        }
    }
}

@Composable
private fun releaseCountdown(release: UpcomingRelease): String = when {
    release.daysUntil == 0 -> stringResource(R.string.today_release_today)
    release.status == ReleaseStatus.OUT_NOW -> stringResource(R.string.today_release_out_now)
    release.daysUntil == 1 -> stringResource(R.string.today_release_tomorrow)
    else -> pluralStringResource(R.plurals.today_release_in_days, release.daysUntil, release.daysUntil)
}

// English-only app, so the month abbreviation is built from the enum name rather than a locale formatter.
private fun formatReleaseDate(date: LocalDate): String {
    val month = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "$month ${date.dayOfMonth}, ${date.year}"
}

@Composable
private fun EventLocator(
    postalCode: String,
    onPostalCodeChanged: (String) -> Unit,
    onFindEvents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        OutlinedTextField(
            value = postalCode,
            onValueChange = onPostalCodeChanged,
            label = { Text(stringResource(R.string.today_events_postal_code)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = mc.textSecondary) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = mc.textPrimary,
                unfocusedTextColor = mc.textPrimary,
                focusedBorderColor = mc.primaryAccent,
                unfocusedBorderColor = mc.surfaceVariant,
                focusedLabelColor = mc.primaryAccent,
                unfocusedLabelColor = mc.textSecondary,
                cursorColor = mc.primaryAccent,
            ),
            shape = CardShape,
            modifier = Modifier.fillMaxWidth(),
        )
        MagicCtaButton(
            onClick = onFindEvents,
            text = stringResource(R.string.today_events_find),
            enabled = postalCode.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LinkRow(
    title: String,
    description: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val a11y = stringResource(R.string.today_opens_in_browser_a11y, title)
    Surface(
        color = mc.surface,
        shape = CardShape,
        border = BorderStroke(1.dp, mc.surfaceVariant),
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = a11y },
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = MinRowHeight)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Icon(icon, contentDescription = null, tint = mc.primaryAccent)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = ty.titleMedium, color = mc.textPrimary)
                if (description != null) {
                    Text(description, style = ty.bodySmall, color = mc.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = mc.textDisabled)
        }
    }
}

@Composable
private fun ProTourStrip(items: List<NewsItem>?, onOpen: (NewsItem) -> Unit) {
    val spacing = MaterialTheme.spacing
    when {
        items == null -> Row(modifier = Modifier.fillMaxWidth().padding(spacing.md), horizontalArrangement = Arrangement.Center) {
            MagicLoadingSpinner(size = MagicLoadingSize.Small)
        }
        items.isEmpty() -> EmptyState(
            title = stringResource(R.string.today_events_pro_tour_empty),
            icon = Icons.Default.Info,
            compact = true,
            modifier = Modifier.fillMaxWidth(),
        )
        else -> LazyRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            contentPadding = PaddingValues(horizontal = spacing.lg),
        ) {
            items(items, key = { it.id }) { item -> ProTourCard(item = item, onClick = { onOpen(item) }) }
        }
    }
}

@Composable
private fun ProTourCard(item: NewsItem, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val a11y = stringResource(R.string.today_opens_in_browser_a11y, item.title)
    Surface(
        color = mc.surface,
        shape = CardShape,
        border = BorderStroke(1.dp, mc.surfaceVariant),
        modifier = Modifier
            .width(ProTourCardWidth)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = a11y },
    ) {
        Column {
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ProTourImageHeight),
                )
            }
            Column(modifier = Modifier.padding(spacing.md)) {
                Text(item.title, style = ty.bodyMedium, color = mc.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${item.sourceName} · ${TimeAgoFormatter.format(item.publishedAt)}",
                    style = ty.labelSmall,
                    color = mc.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = spacing.xs),
                )
            }
        }
    }
}

private val MinRowHeight = 56.dp
private val SetBadgeSize = 40.dp
private val SetSymbolSize = 24.dp
private val ProTourCardWidth = 220.dp
private val ProTourImageHeight = 110.dp
