package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.usecase.decks.GetDeckGameStatsUseCase
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.util.TimeAgoFormatter

/**
 * Collapsible card that shows per-deck game statistics on the deck detail screen.
 *
 * Renders nothing when [stats] is null or [stats.totalGames] == 0, so new decks with
 * no history show no extra UI at all.
 *
 * @param stats             Aggregated stats produced by [GetDeckGameStatsUseCase].
 * @param playerName        The app user's player name used to determine win/loss per session.
 * @param onCardClick       Called with the Scryfall ID when the user taps a card image.
 * @param onReviewSurvey    Called with the session ID when the user taps a session row.
 * @param onReplaceCard     Called with the [Card] domain model when the user taps "Replace".
 * @param modifier          Optional layout modifier.
 */
@Composable
fun DeckStatsCard(
    stats: GetDeckGameStatsUseCase.Result?,
    playerName: String,
    onCardClick: (scryfallId: String) -> Unit,
    onReviewSurvey: (sessionId: Long) -> Unit,
    onReplaceCard: (Card) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Do not render anything when there are no recorded games for this deck.
    if (stats == null || stats.totalGames == 0) return

    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    var expanded by remember { mutableStateOf(true) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "chevron_rotation",
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = mc.surface),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Header (always visible) ────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Insights,
                    contentDescription = null,
                    tint = mc.primaryAccent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.deck_stats_title),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = mc.textSecondary,
                    modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                )
            }

            // ── Sub-header KPI row (always visible) ───────────────────────────
            val winratePct = (stats.winrate * 100).toInt()
            val winrateColor = if (stats.winrate >= 0.5f) mc.goldMtg else mc.lifeNegative
            val durationFormatted = formatDurationMmSs(stats.avgDurationMs)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.deck_stats_winrate, winratePct),
                    style = ty.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = winrateColor,
                )
                Text(
                    text = stringResource(R.string.deck_stats_games_count, stats.totalGames),
                    style = ty.labelMedium,
                    color = mc.textSecondary,
                )
                if (stats.avgDurationMs > 0L) {
                    Text(
                        text = stringResource(R.string.deck_stats_avg_duration, durationFormatted),
                        style = ty.labelMedium,
                        color = mc.textSecondary,
                    )
                }
            }

            // ── Collapsible body ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                ) {

                    // Top cards section
                    if (stats.topCards.isNotEmpty()) {
                        SectionHeader(
                            title = stringResource(R.string.deck_stats_top_cards),
                            color = mc.goldMtg,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        CardScoreGrid(
                            items = stats.topCards,
                            onCardClick = onCardClick,
                            badgeColor = mc.goldMtg,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }

                    // Weakest cards section
                    if (stats.weakestCards.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SectionHeader(
                            title = stringResource(R.string.deck_stats_weakest_cards),
                            color = mc.lifeNegative,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        WeakCardGrid(
                            items = stats.weakestCards,
                            onCardClick = onCardClick,
                            onReplaceCard = onReplaceCard,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }

                    // Recent sessions section
                    if (stats.recentSessions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SectionHeader(
                            title = stringResource(R.string.deck_stats_recent_games),
                            color = mc.textSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        Column(modifier = Modifier.fillMaxWidth()) {
                            stats.recentSessions.forEach { session ->
                                SessionRow(
                                    session = session,
                                    playerName = playerName,
                                    onReviewSurvey = onReviewSurvey,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Private sub-composables ───────────────────────────────────────────────────

/** Small coloured label used as a section divider. */
@Composable
private fun SectionHeader(
    title: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val ty = MaterialTheme.magicTypography
    Text(
        text = title,
        style = ty.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = modifier,
    )
}

/**
 * 3-column fixed grid of card art thumbnails with an appearance badge overlay.
 * Used for the top-cards section.
 */
@Composable
private fun CardScoreGrid(
    items: List<GetDeckGameStatsUseCase.CardScore>,
    onCardClick: (String) -> Unit,
    badgeColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.height(148.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        userScrollEnabled = false,
    ) {
        items(items, key = { it.card.scryfallId }) { score ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onCardClick(score.card.scryfallId) },
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(score.card.imageArtCrop ?: score.card.imageNormal)
                        .crossfade(true)
                        .build(),
                    contentDescription = score.card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.3f)
                        .clip(RoundedCornerShape(6.dp)),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = score.card.name,
                    style = ty.labelSmall,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeColor.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = stringResource(R.string.deck_stats_card_appearances, score.appearances),
                        style = ty.labelSmall,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/**
 * 3-column grid of card art thumbnails with a "Replace" action below each card.
 * Used for the weakest-cards section.
 */
@Composable
private fun WeakCardGrid(
    items: List<GetDeckGameStatsUseCase.CardScore>,
    onCardClick: (String) -> Unit,
    onReplaceCard: (Card) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.height(172.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        userScrollEnabled = false,
    ) {
        items(items, key = { it.card.scryfallId }) { score ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(score.card.imageArtCrop ?: score.card.imageNormal)
                        .crossfade(true)
                        .build(),
                    contentDescription = score.card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.3f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onCardClick(score.card.scryfallId) },
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = score.card.name,
                    style = ty.labelSmall,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                TextButton(
                    onClick = { onReplaceCard(score.card) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 4.dp,
                        vertical = 0.dp,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.deck_stats_replace_button),
                        style = ty.labelSmall,
                        color = mc.lifeNegative,
                    )
                }
            }
        }
    }
}

/**
 * A single row representing one [com.mmg.manahub.core.data.local.dao.SessionSummary].
 * Shows relative date, win/loss badge, survey status chip, and a trailing icon.
 */
@Composable
private fun SessionRow(
    session: com.mmg.manahub.core.data.local.dao.SessionSummary,
    playerName: String,
    onReviewSurvey: (Long) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onReviewSurvey(session.id) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Relative date
        Text(
            text = TimeAgoFormatter.format(session.playedAt),
            style = ty.labelMedium,
            color = mc.textSecondary,
            modifier = Modifier.weight(1f),
        )

        // Win / loss badge: compare winner name against the app user's player name
        val isWin = session.winnerName == playerName
        val badgeColor = if (isWin) mc.lifePositive else mc.lifeNegative
        val badgeText = if (isWin) "W" else "L"
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = badgeColor.copy(alpha = 0.15f),
        ) {
            Text(
                text = badgeText,
                style = ty.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = badgeColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        // Survey status chip
        val (surveyLabel, surveyColor) = when (session.surveyStatus) {
            "COMPLETED" -> stringResource(R.string.survey_completed) to mc.primaryAccent
            "PARTIAL"   -> stringResource(R.string.survey_partial) to mc.goldMtg
            "SKIPPED"   -> stringResource(R.string.survey_skipped) to mc.textSecondary
            else        -> stringResource(R.string.survey_pending) to mc.lifeNegative
        }
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = surveyColor.copy(alpha = 0.12f),
        ) {
            Text(
                text = surveyLabel,
                style = ty.labelSmall,
                color = surveyColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        Icon(
            imageVector = Icons.Default.Visibility,
            contentDescription = null,
            tint = mc.textSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

// ── Utilities ─────────────────────────────────────────────────────────────────

/**
 * Formats a duration in milliseconds as "Mm Ss" (e.g. "32m 15s").
 * Returns "—" when [ms] <= 0.
 */
private fun formatDurationMmSs(ms: Long): String {
    if (ms <= 0L) return "—"
    val totalSeconds = ms / 1000L
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}m ${seconds}s"
}
