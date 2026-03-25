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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mmg.magicfolder.core.domain.model.CollectionStats
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography

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
            ProfileHeader()

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

            // ── Theme selector ─────────────────────────────────────────────────
            ThemeSection(
                selected = state.selectedTheme,
                onSelect = viewModel::selectTheme,
            )

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

@Composable
private fun ProfileHeader() {
    val mc = MaterialTheme.magicColors
    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Avatar circle
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(mc.primaryAccent.copy(alpha = 0.15f))
                .border(2.dp, mc.primaryAccent.copy(alpha = 0.5f), CircleShape),
        ) {
            Text(
                "✦",
                style = MaterialTheme.magicTypography.displayMedium,
                color = mc.primaryAccent,
            )
        }
        Text(
            "Æther Tracker",
            style     = MaterialTheme.magicTypography.titleLarge,
            color     = mc.goldMtg,
            textAlign = TextAlign.Center,
        )
        Text(
            "Commander",
            style     = MaterialTheme.magicTypography.labelLarge,
            color     = mc.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

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
                StatCell("Cards", stats.totalCards.toString(), Modifier.weight(1f))
                StatCell("Unique", stats.uniqueCards.toString(), Modifier.weight(1f))
                StatCell("Decks", stats.totalDecks.toString(), Modifier.weight(1f))
                StatCell(
                    label = "Value",
                    value = "$${String.format("%.0f", stats.totalValueUsd)}",
                    modifier = Modifier.weight(1f),
                    valueColor = true,
                )
            }
        }
    }
}

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

@Composable
private fun ThemeSection(
    selected: AppTheme,
    onSelect: (AppTheme) -> Unit,
) {
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
    val mc        = MaterialTheme.magicColors
    val isLocked  = !theme.isUnlocked
    val borderCol = when {
        selected -> mc.primaryAccent
        isLocked -> mc.surfaceVariant
        else     -> mc.surfaceVariant
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) mc.primaryAccent.copy(alpha = 0.12f)
                else mc.surface
            )
            .border(
                width = if (selected) 1.5.dp else 0.5.dp,
                color = borderCol,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(enabled = !isLocked, onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Swatch
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
            Text(
                "🔒",
                style = MaterialTheme.magicTypography.labelSmall,
            )
        } else if (selected) {
            Text(
                "Active",
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.primaryAccent,
            )
        }
    }
}

@Composable
private fun LanguageSection(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val languages = listOf(
        "en" to "English",
        "es" to "Español",
        "de" to "Deutsch",
        "fr" to "Français",
        "it" to "Italiano",
        "pt" to "Português",
        "ja" to "日本語",
        "ko" to "한국어",
        "ru" to "Русский",
        "zhs" to "简体中文",
        "zht" to "繁體中文",
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
        InfoRow("App", "MagicFolder")
        InfoRow("Version", "1.0.0")
        InfoRow("Data", "Scryfall API")
        InfoRow("Engine", "Æther Tracker v1")
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

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.magicTypography.labelLarge,
        color = MaterialTheme.magicColors.textSecondary,
    )
}

private fun themeSwatchColor(
    theme:    AppTheme,
    active:   androidx.compose.ui.graphics.Color,
    locked:   androidx.compose.ui.graphics.Color,
): androidx.compose.ui.graphics.Color = when (theme) {
    AppTheme.NEON_VOID   -> active
    AppTheme.DAWN_REALM  -> locked  // placeholder — will get real color when unlocked
    AppTheme.ARCANE_GRAY -> locked
}
