package com.mmg.magicfolder.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mmg.magicfolder.core.data.local.dao.DeckStatsRow
import com.mmg.magicfolder.core.data.local.entity.GameSessionWithPlayers
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
    val state by viewModel.state.collectAsState()
    val mc    = MaterialTheme.magicColors

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            // ── Avatar + title ─────────────────────────────────────────────────
            ProfileHeader(
                playerName  = state.playerName,
                onNameSaved = viewModel::savePlayerName,
            )

            // ── Collection stats ───────────────────────────────────────────────
            if (state.isLoading) {
                Box(
                    modifier         = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = mc.primaryAccent, modifier = Modifier.size(28.dp))
                }
            } else {
                StatsSection(stats = state.stats)
            }

            // ── Game stats ─────────────────────────────────────────────────────
            if (state.totalGames > 0) {
                GameStatsSection(state = state)
            }

            // ── Recent games ───────────────────────────────────────────────────
            if (state.recentSessions.isNotEmpty()) {
                RecentGamesSection(sessions = state.recentSessions)
            }

            // ── Deck performance ───────────────────────────────────────────────
            if (state.deckStats.isNotEmpty()) {
                DeckPerformanceSection(deckStats = state.deckStats)
            }

            // ── Theme selector — hidden in v1, will be re-enabled in v2 ──────────
            // ThemeSection(selected = state.selectedTheme, onSelect = viewModel::selectTheme)

            // ── Language selector ──────────────────────────────────────────────
            LanguageSection(
                selected = state.selectedLanguage,
                onSelect = viewModel::selectLanguage,
            )

            // ── App info ───────────────────────────────────────────────────────
            AppInfoFooter()

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Profile header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeader(
    playerName:  String,
    onNameSaved: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    var editedName by remember(playerName) { mutableStateOf(playerName) }

    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(mc.primaryAccent.copy(alpha = 0.15f))
                .border(2.dp, mc.primaryAccent.copy(alpha = 0.5f), CircleShape),
        ) {
            Text(
                "\u2726",
                style = MaterialTheme.magicTypography.displayMedium,
                color = mc.primaryAccent,
            )
        }
        Text(
            "\u00c6ther Tracker",
            style     = MaterialTheme.magicTypography.titleLarge,
            color     = mc.goldMtg,
            textAlign = TextAlign.Center,
        )
        OutlinedTextField(
            value         = editedName,
            onValueChange = { editedName = it },
            singleLine    = true,
            label         = {
                Text(
                    "Your name",
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textSecondary,
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { if (editedName.isNotBlank()) onNameSaved(editedName) }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = mc.primaryAccent,
                unfocusedBorderColor = mc.textSecondary,
                focusedTextColor     = mc.textPrimary,
                unfocusedTextColor   = mc.textPrimary,
                cursorColor          = mc.primaryAccent,
            ),
            modifier = Modifier.width(220.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Collection stats
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatsSection(stats: CollectionStats?) {
    val mc = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Collection")
        if (stats == null) {
            Text(
                "No collection data yet.",
                style = MaterialTheme.magicTypography.bodyMedium,
                color = mc.textDisabled,
            )
        } else {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCell("Cards",  stats.totalCards.toString(),                     Modifier.weight(1f))
                StatCell("Unique", stats.uniqueCards.toString(),                    Modifier.weight(1f))
                StatCell("Decks",  stats.totalDecks.toString(),                     Modifier.weight(1f))
                StatCell("Value",  "$${String.format("%.0f", stats.totalValueUsd)}", Modifier.weight(1f), valueColor = true)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Game stats
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GameStatsSection(state: ProfileUiState) {
    val mc = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Game Stats")
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCell("Games",     state.totalGames.toString(),                    Modifier.weight(1f))
            StatCell("Wins",      state.totalWins.toString(),                     Modifier.weight(1f))
            StatCell("Win %",     "${(state.winRate * 100).roundToInt()}%",        Modifier.weight(1f))
        }
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCell(
                label    = "Avg life\non win",
                value    = state.avgLifeOnWin.roundToInt().toString(),
                modifier = Modifier.weight(1f),
                valueColor = true,
            )
            StatCell(
                label    = "Avg life\non loss",
                value    = state.avgLifeOnLoss.roundToInt().toString(),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Recent games
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecentGamesSection(sessions: List<GameSessionWithPlayers>) {
    val mc = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Recent Games")
        sessions.forEach { session ->
            RecentGameRow(session = session)
        }
    }
}

@Composable
private fun RecentGameRow(session: GameSessionWithPlayers) {
    val mc      = MaterialTheme.magicColors
    val s       = session.session
    val winner  = session.players.find { it.isWinner }
    val isWin   = winner?.playerName == "Player 1"
    val players = session.players.joinToString(", ") { it.playerName }

    Surface(shape = RoundedCornerShape(8.dp), color = mc.surface) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // W/L badge
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
                    text     = s.mode,
                    style    = MaterialTheme.magicTypography.labelSmall,
                    color    = mc.goldMtg,
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

// ─────────────────────────────────────────────────────────────────────────────
//  Deck performance
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DeckPerformanceSection(deckStats: List<DeckStatsRow>) {
    val mc = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Deck Performance")
        deckStats.forEach { row ->
            DeckStatRow(row = row)
        }
    }
}

@Composable
private fun DeckStatRow(row: DeckStatsRow) {
    val mc      = MaterialTheme.magicColors
    val winRate = if (row.totalGames > 0) row.wins.toFloat() / row.totalGames else 0f

    Surface(shape = RoundedCornerShape(8.dp), color = mc.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text     = row.deckName ?: "Unknown Deck",
                    style    = MaterialTheme.magicTypography.bodyMedium,
                    color    = mc.textPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text  = "${row.totalGames} games  •  ${(winRate * 100).roundToInt()}%",
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textSecondary,
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

// ─────────────────────────────────────────────────────────────────────────────
//  Common cells
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatCell(
    label:      String,
    value:      String,
    modifier:   Modifier,
    valueColor: Boolean = false,
) {
    val mc = MaterialTheme.magicColors
    Column(
        modifier            = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(mc.surface)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.magicTypography.titleLarge,
            color = if (valueColor) mc.goldMtg else mc.primaryAccent,
        )
        Text(
            label,
            style     = MaterialTheme.magicTypography.labelSmall,
            color     = mc.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Theme + Language + Footer (unchanged)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThemeSection(selected: AppTheme, onSelect: (AppTheme) -> Unit) {
    val mc = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            Text("\uD83D\uDD12", style = MaterialTheme.magicTypography.labelSmall)
        } else if (selected) {
            Text("Active", style = MaterialTheme.magicTypography.labelSmall, color = mc.primaryAccent)
        }
    }
}

@Composable
private fun LanguageSection(selected: String, onSelect: (String) -> Unit) {
    val mc = MaterialTheme.magicColors
    val languages = listOf(
        "en" to "English",
        "es" to "Español",
        "de" to "Deutsch",
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
private fun AppInfoFooter() {
    val mc = MaterialTheme.magicColors
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(mc.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        InfoRow("App",    "MagicFolder")
        InfoRow("Version","1.0.0")
        InfoRow("Data",   "Scryfall API")
        InfoRow("Engine", "\u00c6ther Tracker v1")
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

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.magicTypography.labelLarge,
        color = MaterialTheme.magicColors.textSecondary,
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

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
}
