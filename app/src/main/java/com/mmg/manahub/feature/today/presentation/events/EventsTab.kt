package com.mmg.manahub.feature.today.presentation.events

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.mmg.manahub.R
import com.mmg.manahub.core.model.DraftSet
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.DraftSetCard
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicFilterChip
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicToastState
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.NewsItemCard
import com.mmg.manahub.core.ui.components.SectionHeader
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.news.domain.source.CuratedTwitchChannelPages
import com.mmg.manahub.feature.news.domain.source.SourceIconExtractor
import com.mmg.manahub.feature.today.presentation.common.openInBrowser
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import org.koin.androidx.compose.koinViewModel
import java.net.URLEncoder

@Composable
fun EventsTab(
    toastState: MagicToastState,
    trends: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: EventsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(trends) {
        if (trends) viewModel.loadStreamImages()
    }
    val context = LocalContext.current
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    val toolbarColor = mc.primaryAccent.toArgb()
    val linkFailed = stringResource(R.string.today_link_open_failed)
    val open: (url: String, onOpened: () -> Unit) -> Unit = { url, onOpened ->
        if (openInBrowser(context, url, toolbarColor)) onOpened() else toastState.show(linkFailed, MagicToastType.ERROR)
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var locatorExpanded by rememberSaveable { mutableStateOf(true) }
    var releasesExpanded by rememberSaveable { mutableStateOf(true) }
    var coverageExpanded by rememberSaveable { mutableStateOf(true) }
    var streamsExpanded by rememberSaveable { mutableStateOf(true) }
    var metagameExpanded by rememberSaveable { mutableStateOf(true) }
    var limitedExpanded by rememberSaveable { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = spacing.md, bottom = spacing.lg + bottomInset),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        if (!trends) {
            item(key = "locator_title") {
                SectionHeader(
                    title = stringResource(R.string.today_events_locator_title),
                    expanded = locatorExpanded,
                    onToggle = { locatorExpanded = !locatorExpanded },
                    modifier = Modifier.padding(horizontal = spacing.lg),
                )
            }
            if (locatorExpanded) item(key = "locator") {
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

            item(key = "releases_title") {
                SectionHeader(
                    title = stringResource(R.string.today_events_releases_title),
                    expanded = releasesExpanded,
                    onToggle = { releasesExpanded = !releasesExpanded },
                    modifier = Modifier.padding(horizontal = spacing.lg),
                )
            }
            if (releasesExpanded) when {
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
                    DraftSetCard(
                        set = DraftSet(
                            release.set.code,
                            release.set.code,
                            release.set.name,
                            release.set.releasedAt.orEmpty(),
                            release.set.iconSvgUri,
                            "",
                            "",
                        ),
                        onClick = {
                            open(MetagameLinkCatalog.scryfallSetUrl(release.set.code)) {
                                viewModel.onReleaseOpened()
                            }
                        },
                        releaseDateLabel = formatReleaseDate(release.releaseDate),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg),
                    )
                }
            }

            item(key = "pro_tour_title") {
                SectionHeader(
                    title = stringResource(R.string.today_events_pro_tour_title),
                    expanded = coverageExpanded,
                    onToggle = { coverageExpanded = !coverageExpanded },
                    modifier = Modifier.padding(horizontal = spacing.lg),
                )
            }
            if (coverageExpanded) {
                item(key = "pro_tour") {
                    ProTourStrip(
                        items = state.proTourItems,
                        onOpen = { item -> open(item.url) { viewModel.onLinkOpened("pro_tour_article") } },
                    )
                }
                item(key = "pro_tour_more") {
                    MagicCtaButton(
                        text = stringResource(R.string.today_events_more_magicgg),
                        style = MagicCtaStyle.Outlined,
                        onClick = { open(MetagameLinkCatalog.MAGIC_GG_NEWS_URL) { viewModel.onLinkOpened("magicgg_news") } },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg),
                    )
                }
            }
        }
        if (trends) {
            item(key = "streams_title") {
                SectionHeader(
                    title = stringResource(R.string.today_streams_title),
                    expanded = streamsExpanded,
                    onToggle = { streamsExpanded = !streamsExpanded },
                    modifier = Modifier.padding(horizontal = spacing.lg),
                )
            }
            if (streamsExpanded) {
                items(streamChannels, key = { "stream_${it.id}" }) { channel ->
                    TrendLinkCard(
                        title = stringResource(channel.titleRes),
                        description = stringResource(channel.descriptionRes),
                        eyebrow = "TWITCH CHANNEL · ${channel.handle}",
                        providerUrl = null,
                        imageUrl = state.streamImageUrls[channel.id],
                        onClick = {
                            CuratedTwitchChannelPages.urlFor(channel.id)?.let { url ->
                                open(url) { viewModel.onTrendLinkOpened("stream_${channel.id}") }
                            }
                        },
                        modifier = Modifier.padding(horizontal = spacing.lg),
                    )
                }
            }

            item(key = "metagame_title") {
                SectionHeader(
                    title = stringResource(R.string.today_events_metagame_title),
                    expanded = metagameExpanded,
                    onToggle = { metagameExpanded = !metagameExpanded },
                    modifier = Modifier.padding(horizontal = spacing.lg),
                )
            }
            if (metagameExpanded) {
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
                    val url = link.urlFor(state.selectedFormat)
                    val title = stringResource(link.titleRes)
                    TrendLinkCard(
                        title = title,
                        providerUrl = url,
                        description = if (link.formatScoped) stringResource(link.descriptionRes, formatName) else stringResource(link.descriptionRes),
                        eyebrow = "METAGAME RESOURCE",
                        onClick = { open(url) { viewModel.onTrendLinkOpened(link.id) } },
                        modifier = Modifier.padding(horizontal = spacing.lg),
                    )
                }
            }

            item(key = "limited_title") {
                SectionHeader(
                    title = stringResource(R.string.today_events_limited_title),
                    expanded = limitedExpanded,
                    onToggle = { limitedExpanded = !limitedExpanded },
                    modifier = Modifier.padding(horizontal = spacing.lg),
                )
            }
            if (limitedExpanded) {
                state.latestLimitedSet?.let { set ->
                    item(key = "limited_17lands") {
                        val url = MetagameLinkCatalog.seventeenLandsRatingsUrl(set.code)
                        val title = stringResource(R.string.today_events_17lands_title)
                        TrendLinkCard(
                            title = title,
                            description = stringResource(R.string.today_events_17lands_desc, set.name),
                            eyebrow = "DRAFT DATA",
                            providerUrl = url,
                            onClick = {
                                open(url) { viewModel.onTrendLinkOpened("17lands_card_ratings") }
                            },
                            modifier = Modifier.padding(horizontal = spacing.lg),
                        )
                    }
                }
                items(MetagameLinkCatalog.limitedLinks, key = { it.id }) { link ->
                    val url = link.urlFor(state.selectedFormat)
                    val title = stringResource(link.titleRes)
                    TrendLinkCard(
                        title = title,
                        description = stringResource(link.descriptionRes),
                        eyebrow = "LIMITED RESOURCE",
                        providerUrl = url,
                        onClick = { open(url) { viewModel.onTrendLinkOpened(link.id) } },
                        modifier = Modifier.padding(horizontal = spacing.lg),
                    )
                }
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
            trailingIcon = {
                IconButton(onClick = onFindEvents, enabled = postalCode.isNotBlank()) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.today_events_find),
                        tint = mc.primaryAccent,
                    )
                }
            },
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
    }
}

@Composable
private fun TrendLinkCard(
    title: String,
    description: String,
    eyebrow: String,
    providerUrl: String?,
    imageUrl: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val a11y = stringResource(R.string.today_opens_in_browser_a11y, title)
    val iconUrl = remember(providerUrl, imageUrl) {
        imageUrl?.takeIf { it.isNotBlank() } ?: SourceIconExtractor.favicon(providerUrl)
    }
    val imagePainter = rememberAsyncImagePainter(model = iconUrl)
    val logoLoaded = imagePainter.state is AsyncImagePainter.State.Success

    Surface(
        color = mc.surface,
        shape = CardShape,
        border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.72f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = a11y },
    ) {
        Row(
            modifier = Modifier
                .background(Brush.horizontalGradient(listOf(mc.primaryAccent.copy(alpha = 0.10f), mc.surface)))
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(mc.surfaceVariant)
                    .border(1.dp, mc.primaryAccent.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (logoLoaded) {
                    Image(
                        painter = imagePainter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_counter),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(mc.primaryAccent),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = eyebrow,
                    style = ty.labelSmall,
                    color = mc.primaryAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = title,
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description,
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
    NewsItemCard(item = item, onClick = onClick, placeholderPainter = painterResource(Res.drawable.mtg_card_back),
        showDescription = false, modifier = Modifier.width(300.dp))
}

private data class StreamChannel(
    val id: String,
    val handle: String,
    val titleRes: Int,
    val descriptionRes: Int,
)

private val streamChannels = listOf(
    StreamChannel("magic", "@magic", R.string.today_events_twitch_title, R.string.today_events_twitch_desc),
    StreamChannel("mtgjp", "@mtgjp", R.string.today_stream_japanese_title, R.string.today_stream_japanese_description),
    StreamChannel("rcastiello", "@rcastiello", R.string.today_stream_spanish_title, R.string.today_stream_spanish_description),
    StreamChannel("jirock", "@jirock", R.string.today_stream_french_title, R.string.today_stream_french_description),
)
