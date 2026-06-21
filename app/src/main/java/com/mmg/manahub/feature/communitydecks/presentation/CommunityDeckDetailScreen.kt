package com.mmg.manahub.feature.communitydecks.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.TimeAgoFormatter
import com.mmg.manahub.feature.communitydecks.domain.model.CommunityDeck
import com.mmg.manahub.feature.communitydecks.presentation.components.CommunityDeckAttribution
import com.mmg.manahub.feature.communitydecks.presentation.components.communityDeckCardItems

/**
 * Community Deck detail screen.
 *
 * Shows a single Archidekt deck (header, attribution, disclaimer, card list) and a
 * resilient "Import Deck" action that creates a new local deck and navigates into it.
 *
 * @param onBack pops the back stack.
 * @param onNavigateToDeck opens the freshly-imported local deck (in Deck Studio).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityDeckDetailScreen(
    onBack: () -> Unit,
    onNavigateToDeck: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CommunityDeckDetailViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isEnabled by viewModel.isFeatureEnabled.collectAsStateWithLifecycle()
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
                        Text(
                            text = stringResource(R.string.community_deck_detail_title),
                            style = ty.titleLarge,
                            color = mc.textPrimary,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.community_deck_detail_title),
                                tint = mc.textSecondary,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.backgroundSecondary),
                )
            },
        ) { padding ->
            if (!isEnabled) {
                EmptyState(
                    title = stringResource(R.string.community_deck_feature_disabled),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            } else {
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
                        FullErrorState(
                            message = state.message,
                            retryLabel = stringResource(R.string.retry),
                            onRetry = viewModel::loadDeck,
                            modifier = Modifier.padding(padding),
                        )
                    }

                    is CommunityDeckDetailUiState.Content -> {
                        CommunityDeckDetailContent(
                            deck = state.deck,
                            isImporting = state.isImporting,
                            importProgress = state.importProgress,
                            isStale = state.isStale,
                            onImport = viewModel::importDeck,
                            onOpenSource = uriHandler::openUri,
                            contentPadding = padding,
                        )
                    }
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
@Composable
private fun CommunityDeckDetailContent(
    deck: CommunityDeck,
    isImporting: Boolean,
    importProgress: Pair<Int, Int>?,
    isStale: Boolean,
    onImport: () -> Unit,
    onOpenSource: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        // Card list takes the available space; import bar pinned at the bottom.
        LazyColumn(modifier = Modifier.weight(1f)) {
            item(key = "header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.lg, vertical = spacing.md),
                ) {
                    if (isStale) {
                        StaleBanner()
                        Spacer(Modifier.height(spacing.sm))
                    }

                    Text(
                        text = deck.name,
                        style = ty.titleLarge,
                        color = mc.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(spacing.xs))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.community_deck_format, deck.format),
                            style = ty.bodySmall,
                            color = mc.textSecondary,
                        )
                        Spacer(Modifier.width(spacing.md))
                        Text(
                            text = stringResource(R.string.community_deck_views, deck.viewCount),
                            style = ty.bodySmall,
                            color = mc.textSecondary,
                        )
                    }

                    deck.createdAt.toEpochMillisOrNull()?.let { millis ->
                        Spacer(Modifier.height(spacing.xxs))
                        Text(
                            text = TimeAgoFormatter.format(millis),
                            style = ty.bodySmall,
                            color = mc.textDisabled,
                        )
                    }
                }
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
                    style = ty.bodySmall,
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
                communityDeckCardItems(deck.cards)
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

/** Small "cached data" banner shown when the deck was served from a stale cache. */
@Composable
private fun StaleBanner() {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Text(
        text = stringResource(R.string.community_deck_error_network),
        style = ty.labelSmall,
        color = mc.goldMtg,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xxs),
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(spacing.lg),
    ) {
        if (isImporting) {
            val processed = importProgress?.first ?: 0
            val total = importProgress?.second ?: 0
            Text(
                text = stringResource(R.string.community_deck_import_progress, processed, total),
                style = ty.bodyMedium,
                color = mc.textSecondary,
            )
            Spacer(Modifier.height(spacing.sm))
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { processed.toFloat() / total.toFloat() },
                    color = mc.primaryAccent,
                    trackColor = mc.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(
                    color = mc.primaryAccent,
                    trackColor = mc.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Button(
                onClick = onImport,
                enabled = enabled,
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = mc.primaryAccent,
                    contentColor = mc.onAccent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(
                    text = stringResource(R.string.community_deck_import),
                    style = ty.labelLarge,
                )
            }
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
