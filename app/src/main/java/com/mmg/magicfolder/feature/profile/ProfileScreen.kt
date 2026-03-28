package com.mmg.magicfolder.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mmg.magicfolder.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.magicfolder.core.data.local.dao.DeckStatsRow
import com.mmg.magicfolder.core.data.local.entity.GameSessionWithPlayers
import com.mmg.magicfolder.core.domain.model.Achievement
import com.mmg.magicfolder.core.domain.model.CollectionStats
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors

    Scaffold(
        containerColor = mc.background,
        topBar = {
            Surface(
                color    = mc.backgroundSecondary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text  = stringResource(R.string.profile_title),
                        style = MaterialTheme.magicTypography.titleLarge,
                        color = mc.textPrimary,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {

            // ── Hero ──────────────────────────────────────────────────────────
            item {
                ProfileHeroSection(
                    uiState      = uiState,
                    onNameSaved  = viewModel::savePlayerName,
                )
            }

            // ── KPI grid ──────────────────────────────────────────────────────
            item {
                ProfileKpiSection(
                    uiState  = uiState,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // ── Best deck ─────────────────────────────────────────────────────
            if (uiState.deckStats.isNotEmpty()) {
                item {
                    BestDeckSection(
                        deck     = uiState.deckStats.first(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // ── Survey insights ───────────────────────────────────────────────
            if (uiState.surveyCount > 0) {
                item {
                    SurveyInsightsSection(
                        uiState  = uiState,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // ── Achievements ──────────────────────────────────────────────────
            if (uiState.achievements.isNotEmpty()) {
                item {
                    SectionTitle(
                        text     = "Achievements",
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
                    )
                }
                val rows = uiState.achievements.chunked(3)
                items(rows) { row ->
                    AchievementRow(
                        achievements = row,
                        modifier     = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // ── Recent games ──────────────────────────────────────────────────
            if (uiState.recentSessions.isNotEmpty()) {
                item {
                    SectionTitle(
                        text     = "Recent Games",
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
                    )
                }
                items(uiState.recentSessions) { session ->
                    RecentGameRow(
                        session      = session,
                        playerName   = uiState.playerName,
                        modifier     = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                    )
                }
            }

            // ── Collection summary ────────────────────────────────────────────
            uiState.collectionStats?.let { stats ->
                item {
                    CollectionSummarySection(
                        stats    = stats,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            // ── Theme selector ────────────────────────────────────────────────
            item {
                ThemeSection(
                    selected = uiState.selectedTheme,
                    onSelect = viewModel::selectTheme,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // ── Language selector ─────────────────────────────────────────────
            item {
                LanguageSection(
                    selected = uiState.selectedLanguage,
                    onSelect = viewModel::selectLanguage,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // ── Footer ────────────────────────────────────────────────────────
            item {
                AppInfoFooter(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

// ── Hero section ──────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeroSection(
    uiState:    ProfileViewModel.UiState,
    onNameSaved: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    var editedName by remember(uiState.playerName) { mutableStateOf(uiState.playerName) }

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        mc.primaryAccent.copy(alpha = 0.08f),
                        mc.background,
                    )
                )
            )
            .padding(top = 8.dp, bottom = 24.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Gradient avatar
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            mc.primaryAccent.copy(alpha = 0.35f),
                            mc.goldMtg.copy(alpha = 0.15f),
                        )
                    )
                )
                .border(2.dp, mc.primaryAccent.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("✦", style = MaterialTheme.magicTypography.displayMedium, color = mc.primaryAccent)
        }

        // Inline editable name
        BasicTextField(
            value         = editedName,
            onValueChange = { editedName = it },
            singleLine    = true,
            textStyle     = MaterialTheme.magicTypography.titleLarge.copy(
                color     = mc.textPrimary,
                textAlign = TextAlign.Center,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (editedName.isNotBlank()) onNameSaved(editedName)
            }),
            decorationBox = { innerTextField ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    innerTextField()
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .width(160.dp)
                            .height(1.dp)
                            .background(mc.primaryAccent.copy(alpha = 0.4f))
                    )
                }
            },
            modifier = Modifier.widthIn(min = 80.dp, max = 220.dp),
        )

        // Play style badge
        Surface(
            shape  = RoundedCornerShape(20.dp),
            color  = mc.primaryAccent.copy(alpha = 0.1f),
            border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.3f)),
        ) {
            Row(
                modifier              = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(uiState.playStyle.icon, fontSize = 14.sp)
                Text(
                    uiState.playStyle.label,
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.primaryAccent,
                )
            }
        }
    }
}

// ── KPI grid ──────────────────────────────────────────────────────────────────

@Composable
private fun ProfileKpiSection(
    uiState:  ProfileViewModel.UiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle("Game Stats")
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCell("Games",  uiState.totalGames.toString(),               Modifier.weight(1f))
            KpiCell("Wins",   uiState.totalWins.toString(),                Modifier.weight(1f))
            KpiCell("Win %",  "${(uiState.winRate * 100).roundToInt()}%",  Modifier.weight(1f))
        }
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCell("Streak",    uiState.currentStreak.toString(),               Modifier.weight(1f), accent = true)
            KpiCell("Avg life",  uiState.avgLifeOnWin.roundToInt().toString(),   Modifier.weight(1f))
            KpiCell("Avg turn",  if (uiState.avgWinTurn > 0) "T${uiState.avgWinTurn.roundToInt()}" else "—",
                                                                                  Modifier.weight(1f))
        }
    }
}

@Composable
private fun KpiCell(
    label:    String,
    value:    String,
    modifier: Modifier,
    accent:   Boolean = false,
) {
    val mc = MaterialTheme.magicColors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(mc.surface)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.magicTypography.titleLarge,
            color = if (accent) mc.goldMtg else mc.primaryAccent,
        )
        Text(
            label,
            style     = MaterialTheme.magicTypography.labelSmall,
            color     = mc.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Best deck section ─────────────────────────────────────────────────────────

@Composable
private fun BestDeckSection(
    deck:     DeckStatsRow,
    modifier: Modifier = Modifier,
) {
    val mc      = MaterialTheme.magicColors
    val winRate = if (deck.totalGames > 0) deck.wins.toFloat() / deck.totalGames else 0f
    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle("Best Deck")
        Surface(shape = RoundedCornerShape(14.dp), color = mc.surface) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text     = deck.deckName ?: "Unknown Deck",
                        style    = MaterialTheme.magicTypography.bodyMedium,
                        color    = mc.textPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text  = "${deck.totalGames}G · ${(winRate * 100).roundToInt()}% WR",
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.primaryAccent,
                    )
                }
                LinearProgressIndicator(
                    progress   = { winRate },
                    modifier   = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color      = mc.primaryAccent,
                    trackColor = mc.surfaceVariant,
                )
            }
        }
    }
}

// ── Survey insights section ───────────────────────────────────────────────────

@Composable
private fun SurveyInsightsSection(
    uiState:  ProfileViewModel.UiState,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle("Survey Insights")
        Surface(shape = RoundedCornerShape(14.dp), color = mc.surface) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InsightRow("Hand Quality",   "${(uiState.avgHandRating * 20).roundToInt()}%",  (uiState.avgHandRating / 5f).toFloat())
                InsightRow("Mana Issues",  "${uiState.manaIssueCount} games",  (uiState.manaIssueCount.toFloat() / uiState.totalGames.coerceAtLeast(1)).coerceAtMost(1f))
            }
        }
    }
}

@Composable
private fun InsightRow(label: String, value: String, progress: Float) {
    val mc = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
            Text(value, style = MaterialTheme.magicTypography.labelSmall, color = mc.primaryAccent)
        }
        LinearProgressIndicator(
            progress   = { progress },
            modifier   = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color      = mc.primaryAccent,
            trackColor = mc.surfaceVariant,
        )
    }
}

// ── Achievements row ──────────────────────────────────────────────────────────

@Composable
private fun AchievementRow(
    achievements: List<Achievement>,
    modifier:     Modifier = Modifier,
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        achievements.forEach { ach ->
            AchievementCell(achievement = ach, modifier = Modifier.weight(1f))
        }
        repeat(3 - achievements.size) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun AchievementCell(
    achievement: Achievement,
    modifier:    Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val isUnlocked = achievement.isUnlocked
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isUnlocked) mc.surface else mc.surface.copy(alpha = 0.4f))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text     = achievement.icon,
            fontSize = 24.sp,
            color    = if (isUnlocked) Color.Unspecified else mc.textDisabled,
        )
        Text(
            text      = achievement.title,
            style     = MaterialTheme.magicTypography.labelSmall,
            color     = if (isUnlocked) mc.textPrimary else mc.textDisabled,
            textAlign = TextAlign.Center,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
        )
    }
}

// ── Recent games row ──────────────────────────────────────────────────────────

@Composable
private fun RecentGameRow(
    session:    GameSessionWithPlayers,
    playerName: String,
    modifier:   Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val dateStr    = dateFormat.format(Date(session.session.playedAt))

    val myPlayer = session.players.find { it.playerName == playerName }
    val isWin    = myPlayer?.isWinner == true

    Surface(
        shape  = RoundedCornerShape(12.dp),
        color  = mc.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = session.session.mode,
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textSecondary,
                )
                Text(
                    text  = dateStr,
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textDisabled,
                )
            }
            Text(
                text  = if (isWin) "WIN" else "LOSS",
                style = MaterialTheme.magicTypography.titleMedium,
                color = if (isWin) mc.primaryAccent else mc.textDisabled,
            )
        }
    }
}

// ── Collection summary ────────────────────────────────────────────────────────

@Composable
private fun CollectionSummarySection(
    stats:    CollectionStats,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle("Collection Summary")
        Surface(shape = RoundedCornerShape(14.dp), color = mc.surface) {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Total Cards", style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
                    Text(stats.totalCards.toString(), style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Est. Value", style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
                    Text("$${String.format("%.2f", stats.totalValueUsd)}", style = MaterialTheme.magicTypography.titleMedium, color = mc.goldMtg)
                }
            }
        }
    }
}

// ── Sections ──────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text  = text,
        style = MaterialTheme.magicTypography.labelLarge,
        color = MaterialTheme.magicColors.textSecondary,
        modifier = modifier,
    )
}

@Composable
private fun ThemeSection(
    selected: AppTheme,
    onSelect: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Theme")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTheme.entries.forEach { theme ->
                val isSelected = theme == selected
                Surface(
                    onClick = { onSelect(theme) },
                    shape   = RoundedCornerShape(10.dp),
                    color   = if (isSelected) mc.primaryAccent.copy(alpha = 0.15f) else mc.surface,
                    border  = if (isSelected) BorderStroke(1.dp, mc.primaryAccent) else null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text     = theme.displayName,
                        modifier = Modifier.padding(vertical = 10.dp),
                        textAlign = TextAlign.Center,
                        style    = MaterialTheme.magicTypography.bodySmall,
                        color    = if (isSelected) mc.primaryAccent else mc.textPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageSection(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Language")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("English", "Español").forEach { lang ->
                val code = if (lang == "English") "en" else "es"
                val isSelected = code == selected
                Surface(
                    onClick = { onSelect(code) },
                    shape   = RoundedCornerShape(10.dp),
                    color   = if (isSelected) mc.primaryAccent.copy(alpha = 0.15f) else mc.surface,
                    border  = if (isSelected) BorderStroke(1.dp, mc.primaryAccent) else null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text     = lang,
                        modifier = Modifier.padding(vertical = 10.dp),
                        textAlign = TextAlign.Center,
                        style    = MaterialTheme.magicTypography.bodySmall,
                        color    = if (isSelected) mc.primaryAccent else mc.textPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppInfoFooter(modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    Column(
        modifier            = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Magic Folder v1.0.4", style = MaterialTheme.magicTypography.labelSmall, color = mc.textDisabled)
        Text("Developed with ❤️ for the MTG community", style = MaterialTheme.magicTypography.labelSmall, color = mc.textDisabled, textAlign = TextAlign.Center)
    }
}
