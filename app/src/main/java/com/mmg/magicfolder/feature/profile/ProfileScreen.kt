package com.mmg.magicfolder.feature.profile

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mmg.magicfolder.R
import com.mmg.magicfolder.core.data.local.dao.DeckStatsRow
import com.mmg.magicfolder.core.data.local.entity.GameSessionWithPlayers
import com.mmg.magicfolder.core.domain.model.Achievement
import com.mmg.magicfolder.core.domain.model.AppLanguage
import com.mmg.magicfolder.core.domain.model.CardLanguage
import com.mmg.magicfolder.core.domain.model.CollectionStats
import com.mmg.magicfolder.core.domain.model.NewsLanguage
import com.mmg.magicfolder.core.domain.model.PreferredCurrency
import com.mmg.magicfolder.core.domain.model.UserPreferences
import com.mmg.magicfolder.core.ui.components.ManaColor
import com.mmg.magicfolder.core.ui.components.ManaSymbol
import com.mmg.magicfolder.core.ui.theme.AppTheme
import com.mmg.magicfolder.core.ui.theme.MarcellusFontFamily
import com.mmg.magicfolder.core.ui.theme.ThemeBackground
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
    val prefsState by viewModel.prefsState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val activity = LocalContext.current as? Activity
    var showAvatarPicker by remember { mutableStateOf(false) }

    // Recreate activity when app language changes
    LaunchedEffect(Unit) {
        viewModel.appLanguageChanged.collect { activity?.recreate() }
    }

    if (showAvatarPicker) {
        AvatarPickerSheet(onDismiss = { showAvatarPicker = false })
    }

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
                    name          = uiState.playerName,
                    playStyle     = "${uiState.playStyle.icon} ${uiState.playStyle.label}",
                    avatarUrl     = uiState.avatarUrl,
                    onAvatarClick = { showAvatarPicker = true },
                )
            }

            // ── KPI grid ──────────────────────────────────────────────────────
            item {
                ProfileKpiSection(
                    uiState           = uiState,
                    favouriteColor    = uiState.favouriteColor,
                    mostValuableColor = uiState.mostValuableColor,
                    modifier          = Modifier.padding(horizontal = 16.dp),
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
                    AchievementsSection(
                        achievements = uiState.achievements,
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

            // ── Preferences ───────────────────────────────────────────────────
            item {
                PreferencesSection(
                    prefs             = prefsState.userPreferences,
                    onAppLanguage     = viewModel::setAppLanguage,
                    onCardLanguage    = viewModel::setCardLanguage,
                    onNewsLanguages   = viewModel::setNewsLanguages,
                    onCurrency        = viewModel::setPreferredCurrency,
                    modifier          = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // ── Theme selector ────────────────────────────────────────────────
            item {
                ThemeSelectorSection(
                    currentTheme     = uiState.currentTheme,
                    onThemeSelected  = viewModel::selectTheme,
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
    name: String,
    playStyle: String?,
    avatarUrl: String?,
    onAvatarClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
    ) {
        // Background: planeswalker art or fallback gradient
        if (avatarUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                mc.primaryAccent.copy(alpha = 0.3f),
                                mc.background,
                            ),
                        ),
                    ),
            ) {
                ThemeBackground(modifier = Modifier.fillMaxSize())
                Text(
                    text = name.take(1).uppercase().ifEmpty { "✦" },
                    fontFamily = MarcellusFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 72.sp,
                    color = mc.primaryAccent.copy(alpha = 0.3f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        // Dark gradient overlay (bottom→top) for text legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
                ),
        )

        // Edit button — top-right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onAvatarClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.avatar_picker_edit_hint),
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp),
            )
        }

        // Name + play style badge — bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = name.ifBlank { stringResource(R.string.game_setup_default_player_name) },
                fontFamily = MarcellusFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = Color.White,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            /*if (playStyle != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = mc.primaryAccent.copy(alpha = 0.25f),
                    border = BorderStroke(0.5.dp, mc.primaryAccent.copy(alpha = 0.5f)),
                ) {
                    Text(
                        text = playStyle,
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.primaryAccent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }*/
        }
    }
}

// ── KPI grid ──────────────────────────────────────────────────────────────────

@Composable
private fun ProfileKpiSection(
    uiState:           ProfileViewModel.UiState,
    favouriteColor:    String?,
    mostValuableColor: String?,
    modifier:          Modifier = Modifier,
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
            KpiCell("Streak", uiState.currentStreak.toString(), Modifier.weight(1f), accent = true)
            ColorStatCard(label = "Fav. Color",  colorCode = favouriteColor,    modifier = Modifier.weight(1f))
            ColorStatCard(label = "Top Value",   colorCode = mostValuableColor, modifier = Modifier.weight(1f))
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

// ── Achievements section ──────────────────────────────────────────────────────

@Composable
private fun AchievementsSection(
    achievements: List<Achievement>,
    modifier:     Modifier = Modifier,
) {
    val mc       = MaterialTheme.magicColors
    val unlocked   = achievements.filter { it.isUnlocked }
    val inProgress = achievements.filter { !it.isUnlocked && (it.progress ?: 0f) > 0f }
    val locked     = achievements.filter { !it.isUnlocked && (it.progress ?: 0f) == 0f }

    var selectedAchievement by remember { mutableStateOf<Achievement?>(null) }
    var showLocked          by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Achievements", modifier = Modifier.padding(top = 8.dp))

        // Unlocked grid
        if (unlocked.isNotEmpty()) {
            Text("Unlocked (${unlocked.size})", style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
            unlocked.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { ach ->
                        AchievementBadge(
                            achievement = ach,
                            onClick     = { selectedAchievement = ach },
                            modifier    = Modifier.weight(1f),
                        )
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        // In-progress
        if (inProgress.isNotEmpty()) {
            Text("In Progress (${inProgress.size})", style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
            inProgress.forEach { ach ->
                AchievementProgressRow(
                    achievement = ach,
                    onClick     = { selectedAchievement = ach },
                )
            }
        }

        // Locked — collapsed
        if (locked.isNotEmpty()) {
            TextButton(
                onClick            = { showLocked = !showLocked },
                contentPadding     = PaddingValues(0.dp),
            ) {
                Text(
                    text  = if (showLocked) "Hide locked" else "${locked.size} locked achievements",
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textDisabled,
                )
            }
            if (showLocked) {
                locked.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { ach ->
                            AchievementBadge(
                                achievement = ach,
                                onClick     = { selectedAchievement = ach },
                                modifier    = Modifier.weight(1f),
                            )
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }

    selectedAchievement?.let { ach ->
        AchievementDetailDialog(
            achievement = ach,
            onDismiss   = { selectedAchievement = null },
        )
    }
}

@Composable
private fun AchievementBadge(
    achievement: Achievement,
    onClick:     () -> Unit,
    modifier:    Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Surface(
        onClick  = onClick,
        modifier = modifier,
        shape    = RoundedCornerShape(12.dp),
        color    = if (achievement.isUnlocked) mc.surface else mc.surface.copy(alpha = 0.4f),
        border   = if (achievement.isUnlocked)
            BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.4f))
        else null,
    ) {
        Column(
            modifier            = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text     = achievement.emoji,
                fontSize = 22.sp,
                color    = if (achievement.isUnlocked) Color.Unspecified else mc.textDisabled,
            )
            Text(
                text      = achievement.title,
                style     = MaterialTheme.magicTypography.labelSmall,
                color     = if (achievement.isUnlocked) mc.textPrimary else mc.textDisabled,
                textAlign = TextAlign.Center,
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AchievementProgressRow(
    achievement: Achievement,
    onClick:     () -> Unit,
    modifier:    Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Surface(
        onClick  = onClick,
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        color    = mc.surface,
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(achievement.emoji, fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(achievement.title, style = MaterialTheme.magicTypography.bodySmall, color = mc.textPrimary)
                    achievement.progressLabel?.let {
                        Text(it, style = MaterialTheme.magicTypography.labelSmall, color = mc.primaryAccent)
                    }
                }
                achievement.progress?.let { prog ->
                    LinearProgressIndicator(
                        progress   = { prog },
                        modifier   = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color      = mc.primaryAccent,
                        trackColor = mc.surfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AchievementDetailDialog(
    achievement: Achievement,
    onDismiss:   () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton    = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(achievement.emoji, fontSize = 28.sp)
                Text(achievement.title, style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = mc.primaryAccent.copy(alpha = 0.1f),
                ) {
                    Text(
                        text     = achievement.category.label,
                        style    = MaterialTheme.magicTypography.labelSmall,
                        color    = mc.primaryAccent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Text(achievement.description, style = MaterialTheme.magicTypography.bodySmall, color = mc.textSecondary)
                if (!achievement.isUnlocked) {
                    achievement.progress?.let { prog ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Progress", style = MaterialTheme.magicTypography.labelSmall, color = mc.textDisabled)
                                achievement.progressLabel?.let {
                                    Text(it, style = MaterialTheme.magicTypography.labelSmall, color = mc.primaryAccent)
                                }
                            }
                            LinearProgressIndicator(
                                progress   = { prog },
                                modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color      = mc.primaryAccent,
                                trackColor = mc.surfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        containerColor = mc.backgroundSecondary,
    )
}

// ── Color stat card ───────────────────────────────────────────────────────────

@Composable
private fun ColorStatCard(
    label:     String,
    colorCode: String?,
    modifier:  Modifier = Modifier,
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
        if (colorCode != null) {
            val manaColor = when (colorCode.uppercase()) {
                "W" -> ManaColor.W
                "U" -> ManaColor.U
                "B" -> ManaColor.B
                "R" -> ManaColor.R
                "G" -> ManaColor.G
                "C" -> ManaColor.C
                else -> null
            }
            if (manaColor != null) {
                ManaSymbol(color = manaColor, size = 26.dp)
            } else {
                // "M" multicolor — gold circle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(0xFFB8860B))
                        .border(1.5.dp, Color(0xFFDAA520), androidx.compose.foundation.shape.CircleShape),
                ) {
                    Text("✦", fontSize = 13.sp, color = Color.White, textAlign = TextAlign.Center)
                }
            }
        } else {
            Text("—", style = MaterialTheme.magicTypography.titleLarge, color = mc.primaryAccent)
        }
        Text(
            label,
            style     = MaterialTheme.magicTypography.labelSmall,
            color     = mc.textSecondary,
            textAlign = TextAlign.Center,
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

// ── Preferences section ───────────────────────────────────────────────────────

@Composable
private fun PreferencesSection(
    prefs:           UserPreferences,
    onAppLanguage:   (AppLanguage) -> Unit,
    onCardLanguage:  (CardLanguage) -> Unit,
    onNewsLanguages: (Set<NewsLanguage>) -> Unit,
    onCurrency:      (PreferredCurrency) -> Unit,
    modifier:        Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.preferences_title))
        Surface(shape = RoundedCornerShape(14.dp), color = mc.surface) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // App Language — single-select dropdown
                PreferenceDropdownRow(
                    label    = stringResource(R.string.pref_app_language),
                    selected = prefs.appLanguage.displayName,
                    options  = AppLanguage.entries.map { it.displayName },
                    onSelect = { idx -> onAppLanguage(AppLanguage.entries[idx]) },
                )
                HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))

                // Card Language — single-select dropdown
                PreferenceDropdownRow(
                    label    = stringResource(R.string.pref_card_language),
                    selected = prefs.cardLanguage.displayName,
                    options  = CardLanguage.entries.map { it.displayName },
                    onSelect = { idx -> onCardLanguage(CardLanguage.entries[idx]) },
                )
                HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))

                // News Language — multi-select checkboxes
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.pref_news_language),
                        style = MaterialTheme.magicTypography.bodySmall,
                        color = mc.textSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        NewsLanguage.entries.forEach { lang ->
                            val checked = lang in prefs.newsLanguages
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    val updated = if (checked) {
                                        prefs.newsLanguages - lang
                                    } else {
                                        prefs.newsLanguages + lang
                                    }
                                    if (updated.isNotEmpty()) onNewsLanguages(updated)
                                },
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = mc.primaryAccent,
                                        uncheckedColor = mc.textDisabled,
                                    ),
                                )
                                Text(
                                    text = lang.displayName,
                                    style = MaterialTheme.magicTypography.bodySmall,
                                    color = if (checked) mc.textPrimary else mc.textSecondary,
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))

                // Currency — radio buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.pref_currency),
                        style = MaterialTheme.magicTypography.bodySmall,
                        color = mc.textSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        PreferredCurrency.entries.forEach { currency ->
                            val selected = currency == prefs.preferredCurrency
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onCurrency(currency) },
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick  = null,
                                    colors   = RadioButtonDefaults.colors(
                                        selectedColor   = mc.primaryAccent,
                                        unselectedColor = mc.textDisabled,
                                    ),
                                )
                                Text(
                                    text  = currency.displayName,
                                    style = MaterialTheme.magicTypography.bodySmall,
                                    color = if (selected) mc.textPrimary else mc.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferenceDropdownRow(
    label:    String,
    selected: String,
    options:  List<String>,
    onSelect: (Int) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.magicTypography.bodySmall, color = mc.textSecondary)
        Box {
            Surface(
                onClick = { expanded = true },
                shape   = RoundedCornerShape(8.dp),
                color   = mc.surfaceVariant,
            ) {
                Text(
                    text     = selected,
                    style    = MaterialTheme.magicTypography.bodySmall,
                    color    = mc.primaryAccent,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            DropdownMenu(
                expanded         = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEachIndexed { idx, option ->
                    DropdownMenuItem(
                        text    = { Text(option) },
                        onClick = {
                            onSelect(idx)
                            expanded = false
                        },
                    )
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
private fun ThemeSelectorSection(
    currentTheme:    AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle(stringResource(R.string.profile_section_themes))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThemeTile(
                name          = "Neon Void",
                emoji         = "⚡",
                previewColors = listOf(Color(0xFF030508), Color(0xFFC77DFF), Color(0xFF4CC9F0)),
                isSelected    = currentTheme is AppTheme.NeonVoid,
                onClick       = { onThemeSelected(AppTheme.NeonVoid) },
                modifier      = Modifier.weight(1f),
            )
            ThemeTile(
                name          = "Grimoire",
                emoji         = "📜",
                previewColors = listOf(Color(0xFF1A1208), Color(0xFFC9A84C), Color(0xFF7AB648)),
                isSelected    = currentTheme is AppTheme.MedievalGrimoire,
                onClick       = { onThemeSelected(AppTheme.MedievalGrimoire) },
                modifier      = Modifier.weight(1f),
            )
            ThemeTile(
                name          = "Cosmos",
                emoji         = "✨",
                previewColors = listOf(Color(0xFF040812), Color(0xFF7B61FF), Color(0xFFFF61DC)),
                isSelected    = currentTheme is AppTheme.ArcaneCosmos,
                onClick       = { onThemeSelected(AppTheme.ArcaneCosmos) },
                modifier      = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ThemeTile(
    name:          String,
    emoji:         String,
    previewColors: List<Color>,
    isSelected:    Boolean,
    onClick:       () -> Unit,
    modifier:      Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape    = RoundedCornerShape(14.dp),
        color    = if (isSelected) mc.primaryAccent.copy(0.1f) else mc.surface,
        border   = BorderStroke(
            width = if (isSelected) 2.dp else 0.5.dp,
            color = if (isSelected) mc.primaryAccent else mc.surfaceVariant,
        ),
    ) {
        Column(
            modifier            = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                previewColors.forEachIndexed { index, color ->
                    Box(
                        modifier = Modifier
                            .size(if (index == 0) 28.dp else 18.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (index == 0)
                                    Modifier.border(
                                        1.5.dp,
                                        previewColors.getOrElse(1) { Color.White }.copy(0.5f),
                                        CircleShape,
                                    )
                                else Modifier
                            ),
                    )
                }
            }
            Text(
                text      = name,
                style     = MaterialTheme.magicTypography.labelSmall,
                color     = if (isSelected) mc.primaryAccent else mc.textSecondary,
                textAlign = TextAlign.Center,
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(mc.primaryAccent),
                )
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

private fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000L
    val hours   = minutes / 60
    val mins    = minutes % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
