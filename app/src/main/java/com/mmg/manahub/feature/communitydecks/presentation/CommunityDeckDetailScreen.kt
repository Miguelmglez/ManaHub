package com.mmg.manahub.feature.communitydecks.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.data.network.RateLimitExhaustedException
import com.mmg.manahub.core.model.CommunityDeck
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.components.rememberRateLimitCountdownSeconds
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.core.util.TimeAgoFormatter
import com.mmg.manahub.feature.communitydecks.presentation.components.CommunityDeckAttribution
import com.mmg.manahub.feature.communitydecks.presentation.components.communityDeckCardItems
import org.jetbrains.compose.resources.painterResource
import org.koin.androidx.compose.koinViewModel

/**
 * Community Deck detail screen.
 *
 * Shows a single Archidekt deck (header, attribution, disclaimer, card list) and a
 * resilient "Import Deck" action that creates a new local deck and navigates into it.
 *
 * @param onBack pops the back stack.
 * @param onNavigateToDeck opens the freshly-imported local deck (in Deck Studio).
 * @param onCardClick opens a single card's detail screen by its resolved Scryfall id.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun CommunityDeckDetailScreen(
    onBack: () -> Unit,
    onNavigateToDeck: (String) -> Unit,
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CommunityDeckDetailViewModel = koinViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val toastState = rememberMagicToastState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    LaunchedEffect(Unit) {
        FirebaseCrashlytics.getInstance().log("screen_viewed: community_deck_detail")
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CommunityDeckDetailEvent.NavigateToDeck -> onNavigateToDeck(event.deckId)
                is CommunityDeckDetailEvent.ShowImportResult -> {
                    when {
                        event.isError -> toastState.show(
                            context.getString(R.string.community_deck_import_failed),
                            MagicToastType.ERROR,
                        )
                        event.resolvedCount < event.totalCount -> toastState.show(
                            context.getString(
                                R.string.community_deck_import_partial,
                                event.resolvedCount,
                                event.totalCount,
                            ),
                            MagicToastType.SUCCESS,
                        )
                        else -> toastState.show(
                            context.getString(R.string.community_deck_import_success),
                            MagicToastType.SUCCESS,
                        )
                    }
                }
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        Scaffold(
            containerColor = mc.background,
            contentWindowInsets = WindowInsets.statusBars,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = (uiState as? CommunityDeckDetailUiState.Content)?.deck?.name
                                    ?: stringResource(R.string.community_deck_detail_title),
                                style = ty.titleMedium,
                                color = mc.textPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            (uiState as? CommunityDeckDetailUiState.Content)?.deck?.format?.let { fmt ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = mc.goldMtg.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = fmt.uppercase(),
                                        style = ty.labelSmall,
                                        color = mc.goldMtg,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                tint = mc.textSecondary,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.backgroundSecondary),
                )
            },
        ) { padding ->
            when (val state = uiState) {
                is CommunityDeckDetailUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = mc.primaryAccent)
                    }
                }

                is CommunityDeckDetailUiState.Error -> {
                    // WS2: Archidekt rate-limit exhausted -- disable retry with a live countdown
                    // instead of letting a rapid re-tap re-trigger the storm the queue is already
                    // recovering from.
                    val rateLimitRetryAfterMs = RateLimitExhaustedException.retryAfterMsOrNull(state.message)
                    if (rateLimitRetryAfterMs != null) {
                        val remainingSeconds = rememberRateLimitCountdownSeconds(rateLimitRetryAfterMs)
                        FullErrorState(
                            message = stringResource(R.string.error_rate_limited_message),
                            retryLabel = if (remainingSeconds > 0) {
                                stringResource(R.string.error_rate_limited_retry_countdown, remainingSeconds)
                            } else {
                                stringResource(R.string.retry)
                            },
                            onRetry = viewModel::loadDeck,
                            enabled = remainingSeconds <= 0,
                            modifier = Modifier.padding(padding),
                        )
                    } else {
                        FullErrorState(
                            message = state.message,
                            retryLabel = stringResource(R.string.retry),
                            onRetry = viewModel::loadDeck,
                            modifier = Modifier.padding(padding),
                        )
                    }
                }

                is CommunityDeckDetailUiState.Content -> {
                    CommunityDeckDetailContent(
                        deck = state.deck,
                        isImporting = state.isImporting,
                        importProgress = state.importProgress,
                        isStale = state.isStale,
                        commanderExpanded = state.commanderExpanded,
                        mainboardExpanded = state.mainboardExpanded,
                        sideboardExpanded = state.sideboardExpanded,
                        ownedCardIdentityKeys = state.ownedCardIdentityKeys,
                        onToggleCommander = viewModel::toggleCommander,
                        onToggleMainboard = viewModel::toggleMainboard,
                        onToggleSideboard = viewModel::toggleSideboard,
                        onImport = viewModel::importDeck,
                        onOpenSource = uriHandler::openUri,
                        onCardClick = onCardClick,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        contentPadding = padding,
                    )
                }
            }
        }
        MagicToastHost(toastState)
    }
}

/**
 * Stateless content for a loaded community deck: header, attribution, disclaimer,
 * card list, and a sticky import action.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CommunityDeckDetailContent(
    deck: CommunityDeck,
    isImporting: Boolean,
    importProgress: Pair<Int, Int>?,
    isStale: Boolean,
    commanderExpanded: Boolean,
    mainboardExpanded: Boolean,
    sideboardExpanded: Boolean,
    onToggleCommander: () -> Unit,
    onToggleMainboard: () -> Unit,
    onToggleSideboard: () -> Unit,
    onImport: () -> Unit,
    onOpenSource: (String) -> Unit,
    onCardClick: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    ownedCardIdentityKeys: Set<String> = emptySet(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        // Card list takes the available space; import bar pinned at the bottom.
        LazyColumn(modifier = Modifier.weight(1f)) {
            item(key = "header") {
                DeckHeaderArea(
                    deck = deck,
                    isStale = isStale,
                    ownedCardIdentityKeys = ownedCardIdentityKeys,
                )
            }

            item(key = "attribution") {
                CommunityDeckAttribution(
                    authorName = deck.owner.username,
                    sourceUrl = deck.sourceUrl,
                    onOpenSource = onOpenSource,
                )
            }

            item(key = "disclaimer") {
                Text(
                    text = stringResource(R.string.community_decks_disclaimer),
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textDisabled,
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
                )
            }

            if (deck.cards.isEmpty()) {
                item(key = "empty_cards") {
                    EmptyState(
                        title = stringResource(R.string.community_deck_empty_cards),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = spacing.xl),
                    )
                }
            } else {
                // Grouped card sections composed into THIS single lazy list (no nesting).
                communityDeckCardItems(
                    cards = deck.cards,
                    commanderExpanded = commanderExpanded,
                    mainboardExpanded = mainboardExpanded,
                    sideboardExpanded = sideboardExpanded,
                    onToggleCommander = onToggleCommander,
                    onToggleMainboard = onToggleMainboard,
                    onToggleSideboard = onToggleSideboard,
                    onCardClick = onCardClick,
                    ownedCardIdentityKeys = ownedCardIdentityKeys,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }

        ImportBar(
            isImporting = isImporting,
            importProgress = importProgress,
            enabled = deck.cards.isNotEmpty(),
            onImport = onImport,
        )
    }
}

@Composable
private fun DeckHeaderArea(
    deck: CommunityDeck,
    isStale: Boolean,
    ownedCardIdentityKeys: Set<String> = emptySet(),
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val preferredCurrency = LocalPreferredCurrency.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
    ) {
        if (isStale) {
            StaleBanner()
            Spacer(Modifier.height(spacing.sm))
        }

        // Image "Card" representation
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            shape = CardShape,
            border = BorderStroke(0.5.dp, mc.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (deck.featuredImageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(deck.featuredImageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Image(
                        painter = painterResource(Res.drawable.mtg_card_back),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        Spacer(Modifier.height(spacing.md))

        // Attributes row below the image
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = null,
                tint = mc.textDisabled,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(spacing.xs))
            Text(
                text = deck.viewCount.toString(),
                style = ty.bodyMedium,
                color = mc.textSecondary,
            )
            Spacer(Modifier.width(spacing.lg))
            deck.createdAt.toEpochMillisOrNull()?.let { millis ->
                Text(
                    text = TimeAgoFormatter.format(millis),
                    style = ty.bodyMedium,
                    color = mc.textDisabled,
                )
            }
        }

        // Deck cost + collection coverage row (UI addition, 2026-07-22). Mainboard + commander
        // only — mirrors the mainboard/sideboard split `CommunityDeckCardList.kt` already uses.
        val nonSideboardCards = deck.cards.filterNot { it.isSideboard }
        val totalPrice = nonSideboardCards.fold(null as Double?) { acc, card ->
            val (price, _) = PriceFormatter.selectPrice(card.priceUsd, card.priceEur, preferredCurrency)
            if (price == null) acc else (acc ?: 0.0) + price * card.quantity
        }
        // Quantity-weighted, not distinct-by-identity (bug fix, 2026-07-22): the deck's displayed
        // total elsewhere (e.g. "Mainboard (N)" in CommunityDeckCardList.kt) is a QUANTITY sum, so
        // owned+missing must sum to that same total or the two numbers never reconcile for any
        // deck with duplicate/basic-land quantities. "Owned" is still per-identity (owning ANY
        // copy/printing of a card counts, matching ScannerViewModel's `ownedCardIdentityKeys`
        // precedent) — only the bucket a unit of quantity is attributed to changed, not the
        // ownership test itself.
        val totalCardCount = nonSideboardCards.sumOf { it.quantity }
        val ownedCount = nonSideboardCards.sumOf { card ->
            if ((card.oracleId.ifBlank { card.name }) in ownedCardIdentityKeys) card.quantity else 0
        }
        val missingCount = totalCardCount - ownedCount

        if (totalCardCount > 0) {
            Spacer(Modifier.height(spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                totalPrice?.let { price ->
                    Text(
                        text = PriceFormatter.format(price, preferredCurrency),
                        style = ty.bodyMedium,
                        color = mc.goldMtg,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(spacing.lg))
                }
                Text(
                    text = stringResource(R.string.community_deck_owned_count, ownedCount),
                    style = ty.bodyMedium,
                    color = mc.textSecondary,
                )
                Spacer(Modifier.width(spacing.sm))
                Text(
                    text = stringResource(R.string.community_deck_missing_count, missingCount),
                    style = ty.bodyMedium,
                    color = mc.textDisabled,
                )
            }
        }
    }
}

/** Small "cached data" banner shown when the deck was served from a stale cache. */
@Composable
private fun StaleBanner() {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Text(
        text = stringResource(R.string.community_deck_error_network),
        style = ty.labelSmall,
        color = mc.goldMtg,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Bottom action bar: an import button that morphs into a progress indicator. */
@Composable
private fun ImportBar(
    isImporting: Boolean,
    importProgress: Pair<Int, Int>?,
    enabled: Boolean,
    onImport: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Surface(
        color = mc.background,
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
        ) {
            AnimatedVisibility(
                visible = isImporting,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
            ) {
                Column {
                    val processed = importProgress?.first ?: 0
                    val total = importProgress?.second ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.community_deck_import_progress, processed, total),
                            style = ty.labelLarge,
                            color = mc.primaryAccent,
                            fontWeight = FontWeight.Bold,
                        )
                        if (total > 0) {
                            Text(
                                text = "${(processed.toFloat() / total * 100).toInt()}%",
                                style = ty.labelLarge,
                                color = mc.textSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(spacing.sm))
                    if (total > 0) {
                        LinearProgressIndicator(
                            progress = { processed.toFloat() / total.toFloat() },
                            color = mc.primaryAccent,
                            trackColor = mc.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        )
                    } else {
                        LinearProgressIndicator(
                            color = mc.primaryAccent,
                            trackColor = mc.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        )
                    }
                }
            }

            MagicCtaButton(
                text = stringResource(R.string.community_deck_import),
                onClick = onImport,
                enabled = enabled,
                isLoading = isImporting,
                style = MagicCtaStyle.Filled,
                color = MagicCtaColor.Accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            )
        }
    }
}

/**
 * Best-effort parse of Archidekt's ISO-8601 timestamp into epoch millis.
 * Returns null on any unparseable value so the UI simply omits the date.
 */
private fun String.toEpochMillisOrNull(): Long? = try {
    if (isBlank()) null else java.time.Instant.parse(this).toEpochMilli()
} catch (_: Exception) {
    null
}
