package com.mmg.magicfolder.feature.profile

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors

    Scaffold(
        containerColor = mc.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Profile",
                        style = MaterialTheme.magicTypography.titleLarge,
                        color = mc.textPrimary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.backgroundSecondary),
            )
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
            // ── Theme selector — hidden in v1, will be re-enabled in v2 ──────────
            // ThemeSection(selected = state.selectedTheme, onSelect = viewModel::selectTheme)

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
            .padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Gradient avatar
        Box(
            modifier = Modifier
                .size(88.dp)
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
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InsightCell("Surveys",    uiState.surveyCount.toString(),                Modifier.weight(1f))
                    InsightCell("Mana issues", uiState.manaIssueCount.toString(),            Modifier.weight(1f))
                    InsightCell("Hand rating", if (uiState.avgHandRating > 0)
                        "${"%.1f".format(uiState.avgHandRating)}/5" else "—",               Modifier.weight(1f))
                }
                if (uiState.favoriteWinStyle.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Win style: ",
                            style = MaterialTheme.magicTypography.bodySmall,
                            color = mc.textSecondary,
                        )
                        Text(
                            uiState.favoriteWinStyle.lowercase()
                                .replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.magicTypography.labelMedium,
                            color = mc.goldMtg,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightCell(label: String, value: String, modifier: Modifier) {
    val mc = MaterialTheme.magicColors
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = MaterialTheme.magicTypography.titleLarge, color = mc.primaryAccent)
        Text(
            label,
            style     = MaterialTheme.magicTypography.labelSmall,
            color     = mc.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Achievements section ──────────────────────────────────────────────────────

@Composable
private fun AchievementRow(
    achievements: List<Achievement>,
    modifier:     Modifier = Modifier,
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        achievements.forEach { achievement ->
            AchievementCard(achievement = achievement, modifier = Modifier.weight(1f))
        }
        // Pad empty slots if row has fewer than 3
        repeat(3 - achievements.size) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun AchievementCard(
    achievement: Achievement,
    modifier:    Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val glowAlpha = if (achievement.isUnlocked) {
        val infiniteTransition = rememberInfiniteTransition(label = "achievement_glow")
        infiniteTransition.animateFloat(
            initialValue  = 0.3f,
            targetValue   = 0.8f,
            animationSpec = infiniteRepeatable(
                animation  = tween(800),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "glow_alpha",
        ).value
    } else 0f
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (achievement.isUnlocked) mc.surface
                else mc.surface.copy(alpha = 0.4f)
            )
            .then(
                if (achievement.isUnlocked)
                    Modifier.border(1.dp, mc.primaryAccent.copy(alpha = glowAlpha), RoundedCornerShape(12.dp))
                else Modifier
            )
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text     = achievement.icon,
            fontSize = 22.sp,
            color    = if (achievement.isUnlocked) Color.Unspecified
                       else Color.Gray.copy(alpha = 0.4f),
        )
        Text(
            text      = achievement.title,
            style     = MaterialTheme.magicTypography.labelSmall,
            color     = if (achievement.isUnlocked) mc.textPrimary else mc.textDisabled,
            textAlign = TextAlign.Center,
            maxLines  = 2,
            overflow  = TextOverflow.Ellipsis,
        )
        if (!achievement.isUnlocked && achievement.progress > 0f) {
            LinearProgressIndicator(
                progress   = { achievement.progress },
                modifier   = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
                color      = mc.primaryAccent.copy(alpha = 0.5f),
                trackColor = mc.surfaceVariant,
            )
        } else if (achievement.isUnlocked) {
            Text("✓", style = MaterialTheme.magicTypography.labelSmall, color = mc.lifePositive)
        }
    }
}

// ── Recent games ──────────────────────────────────────────────────────────────

@Composable
private fun RecentGameRow(
    session:    GameSessionWithPlayers,
    playerName: String,
    modifier:   Modifier = Modifier,
) {
    val mc      = MaterialTheme.magicColors
    val s       = session.session
    val winner  = session.players.find { it.isWinner }
    val isWin   = winner?.playerName == playerName
    val players = session.players.joinToString(", ") { it.playerName }

    Surface(shape = RoundedCornerShape(10.dp), color = mc.surface, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isWin) mc.lifePositive.copy(alpha = 0.2f)
                        else mc.lifeNegative.copy(alpha = 0.2f),
            ) {
                Text(
                    text     = if (isWin) "W" else "L",
                    style    = MaterialTheme.magicTypography.labelMedium,
                    color    = if (isWin) mc.lifePositive else mc.lifeNegative,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = s.mode,
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.goldMtg,
                )
                Text(
                    text     = players,
                    style    = MaterialTheme.magicTypography.bodySmall,
                    color    = mc.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text  = formatDate(s.playedAt),
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textDisabled,
                )
                Text(
                    text  = formatDuration(s.durationMs),
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textDisabled,
                )
            }
        }
    }
}

// ── Collection summary ────────────────────────────────────────────────────────

@Composable
private fun CollectionSummarySection(
    stats:    CollectionStats,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Collection")
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCell("Cards",  stats.totalCards.toString(),                        Modifier.weight(1f))
            KpiCell("Unique", stats.uniqueCards.toString(),                       Modifier.weight(1f))
            KpiCell("Decks",  stats.totalDecks.toString(),                        Modifier.weight(1f))
            KpiCell("Value",  "$${String.format("%.0f", stats.totalValueUsd)}",   Modifier.weight(1f), accent = true)
        }
    }
}

// ── Theme + Language + Footer ─────────────────────────────────────────────────

@Composable
private fun ThemeSection(
    selected: AppTheme,
    onSelect: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Theme")
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppTheme.entries.forEach { theme ->
                ThemeTile(
                    theme    = theme,
                    selected = theme == selected,
                    onClick  = { onSelect(theme) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ThemeTile(
    theme:    AppTheme,
    selected: Boolean,
    onClick:  () -> Unit,
    modifier: Modifier,
) {
    val mc       = MaterialTheme.magicColors
    val isLocked = !theme.isUnlocked
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) mc.primaryAccent.copy(alpha = 0.12f) else mc.surface)
            .border(
                width = if (selected) 1.5.dp else 0.5.dp,
                color = if (selected) mc.primaryAccent else mc.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(enabled = !isLocked, onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(themeSwatchColor(theme, mc.primaryAccent, mc.surfaceVariant)),
        )
        Text(
            theme.displayName,
            style     = MaterialTheme.magicTypography.labelMedium,
            color     = if (selected) mc.primaryAccent else mc.textSecondary,
            textAlign = TextAlign.Center,
            maxLines  = 1,
        )
        if (isLocked) {
            Text("🔒", style = MaterialTheme.magicTypography.labelSmall)
        } else if (selected) {
            Text("Active", style = MaterialTheme.magicTypography.labelSmall, color = mc.primaryAccent)
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
    val languages = listOf("en" to "English", "es" to "Español", "de" to "Deutsch")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Card Language")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp),
        ) {
            languages.forEach { (code, label) ->
                val isSelected = code == selected
                Surface(
                    onClick = { onSelect(code) },
                    shape   = RoundedCornerShape(8.dp),
                    color   = if (isSelected) mc.primaryAccent.copy(alpha = 0.15f) else mc.surface,
                    border  = BorderStroke(
                        width = if (isSelected) 1.5.dp else 0.5.dp,
                        color = if (isSelected) mc.primaryAccent else mc.surfaceVariant,
                    ),
                ) {
                    Text(
                        text     = label,
                        style    = MaterialTheme.magicTypography.labelMedium,
                        color    = if (isSelected) mc.primaryAccent else mc.textSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(mc.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        InfoRow("App",     "ManaHub")
        InfoRow("Version", "1.0.0")
        InfoRow("Data",    "Scryfall API")
        InfoRow("Engine",  "ManaHub v1")
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val mc = MaterialTheme.magicColors
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.magicTypography.bodySmall, color = mc.textDisabled)
        Text(value, style = MaterialTheme.magicTypography.bodySmall, color = mc.textSecondary)
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text     = text,
        style    = MaterialTheme.magicTypography.labelLarge,
        color    = MaterialTheme.magicColors.textSecondary,
        modifier = modifier,
    )
}

private fun themeSwatchColor(
    theme:  AppTheme,
    active: androidx.compose.ui.graphics.Color,
    locked: androidx.compose.ui.graphics.Color,
): androidx.compose.ui.graphics.Color = when (theme) {
    AppTheme.NEON_VOID   -> active
    AppTheme.DAWN_REALM  -> locked
    AppTheme.ARCANE_GRAY -> locked
}

private fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000L
    val hours   = minutes / 60
    val mins    = minutes % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
