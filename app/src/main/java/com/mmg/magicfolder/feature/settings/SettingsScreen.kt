package com.mmg.magicfolder.feature.settings

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.magicfolder.R
import com.mmg.magicfolder.core.domain.model.AppLanguage
import com.mmg.magicfolder.core.domain.model.CardLanguage
import com.mmg.magicfolder.core.domain.model.NewsLanguage
import com.mmg.magicfolder.core.domain.model.PreferredCurrency
import com.mmg.magicfolder.core.domain.model.UserPreferences
import com.mmg.magicfolder.core.ui.theme.AppTheme
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onManageNewsSources: () -> Unit = {},
    onManageTagDictionary: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val prefsState by viewModel.prefsState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val activity = LocalContext.current as? Activity

    LaunchedEffect(Unit) {
        viewModel.appLanguageChanged.collect { activity?.recreate() }
    }
    Scaffold(
        topBar = {
            Surface(color = mc.backgroundSecondary) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = mc.textPrimary,
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.magicTypography.titleLarge,
                        color = mc.textPrimary,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            PreferencesSection(
                prefs = prefsState.userPreferences,
                onAppLanguage = viewModel::setAppLanguage,
                onCardLanguage = viewModel::setCardLanguage,
                onNewsLanguages = viewModel::setNewsLanguages,
                onCurrency = viewModel::setPreferredCurrency,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))
            Text(
                stringResource(R.string.settings_section_prices),
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            SettingsToggleItem(
                title = stringResource(R.string.settings_auto_refresh),
                subtitle = stringResource(R.string.settings_auto_refresh_subtitle),
                checked = uiState.autoRefreshPrices,
                onCheckedChange = viewModel::onAutoRefreshChanged,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))
            Text(
                stringResource(R.string.settings_section_news),
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onManageNewsSources)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_manage_news_sources),
                        style = MaterialTheme.magicTypography.bodyMedium,
                        color = mc.textPrimary,
                    )
                    Text(
                        stringResource(R.string.settings_manage_news_sources_subtitle),
                        style = MaterialTheme.magicTypography.bodySmall,
                        color = mc.textSecondary,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = mc.textSecondary,
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onManageTagDictionary)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Diccionario de etiquetas",
                        style = MaterialTheme.magicTypography.bodyMedium,
                        color = mc.textPrimary,
                    )
                    Text(
                        "Edita las traducciones, los patrones de detección y los umbrales del auto-tagger.",
                        style = MaterialTheme.magicTypography.bodySmall,
                        color = mc.textSecondary,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = mc.textSecondary,
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))
            ThemeSelectorSection(
                currentTheme = uiState.currentTheme,
                onThemeSelected = viewModel::selectTheme,
            )
        }
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.magicTypography.bodyMedium, color = mc.textPrimary)
            Text(
                subtitle,
                style = MaterialTheme.magicTypography.bodySmall,
                color = mc.textSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = mc.surface,
                checkedTrackColor = mc.primaryAccent,
                checkedIconColor = mc.primaryAccent,
            ),
        )
    }
}

// ── Preferences section ───────────────────────────────────────────────────────

@Composable
private fun PreferencesSection(
    prefs: UserPreferences,
    onAppLanguage: (AppLanguage) -> Unit,
    onCardLanguage: (CardLanguage) -> Unit,
    onNewsLanguages: (Set<NewsLanguage>) -> Unit,
    onCurrency: (PreferredCurrency) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.preferences_title),
            style = MaterialTheme.magicTypography.titleMedium,
            color = mc.textPrimary,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // App Language — single-select dropdown

            /*Text(stringResource(R.string.pref_app_language), style = MaterialTheme.magicTypography.bodySmall, color = mc.textSecondary)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AppLanguage.entries.forEach { language ->
                    val selected = language.displayName == prefs.appLanguage.displayName
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onAppLanguage(AppLanguage.fromCode(language.code) )},
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = mc.primaryAccent,
                                unselectedColor = mc.textDisabled,
                            ),
                        )
                        Text(
                            text = language.displayName,
                            style = MaterialTheme.magicTypography.bodySmall,
                            color = if (selected) mc.textPrimary else mc.textSecondary,
                        )
                    }
                }
            }

            HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))*/
            // Card Language — single-select dropdown

            Text(
                stringResource(R.string.pref_card_language),
                style = MaterialTheme.magicTypography.bodySmall,
                color = mc.textPrimary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CardLanguage.entries.forEach { language ->
                    val selected = language.displayName == prefs.cardLanguage.displayName
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            onCardLanguage(
                                CardLanguage.fromCode(
                                    language.code
                                )
                            )
                        },
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = mc.primaryAccent,
                                unselectedColor = mc.textDisabled,
                            ),
                        )
                        Text(
                            text = language.displayName,
                            style = MaterialTheme.magicTypography.bodySmall,
                            color = if (selected) mc.textPrimary else mc.textSecondary,
                        )
                    }
                }
            }

            // News Language — multi-select checkboxes
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.pref_news_language),
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textPrimary,
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

            // Currency — radio buttons
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.pref_currency),
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textPrimary,
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
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = mc.primaryAccent,
                                    unselectedColor = mc.textDisabled,
                                ),
                            )
                            Text(
                                text = currency.displayName,
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


// ── Sections ──────────────────────────────────────────────────────────────────


@Composable
private fun ThemeSelectorSection(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.profile_section_themes),
            style = MaterialTheme.magicTypography.titleMedium,
            color = mc.textPrimary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThemeTile(
                name = "Neon Void",
                emoji = "⚡",
                previewColors = listOf(Color(0xFF030508), Color(0xFFC77DFF), Color(0xFF4CC9F0)),
                isSelected = currentTheme is AppTheme.NeonVoid,
                onClick = { onThemeSelected(AppTheme.NeonVoid) },
                modifier = Modifier.weight(1f),
            )
            ThemeTile(
                name = "Grimoire",
                emoji = "📜",
                previewColors = listOf(Color(0xFF1A1208), Color(0xFFC9A84C), Color(0xFF7AB648)),
                isSelected = currentTheme is AppTheme.MedievalGrimoire,
                onClick = { onThemeSelected(AppTheme.MedievalGrimoire) },
                modifier = Modifier.weight(1f),
            )
            ThemeTile(
                name = "Cosmos",
                emoji = "✨",
                previewColors = listOf(Color(0xFF040812), Color(0xFF7B61FF), Color(0xFFFF61DC)),
                isSelected = currentTheme is AppTheme.ArcaneCosmos,
                onClick = { onThemeSelected(AppTheme.ArcaneCosmos) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ThemeTile(
    name: String,
    emoji: String,
    previewColors: List<Color>,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) mc.primaryAccent.copy(0.1f) else mc.surface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 0.5.dp,
            color = if (isSelected) mc.primaryAccent else mc.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
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
                text = name,
                style = MaterialTheme.magicTypography.labelSmall,
                color = if (isSelected) mc.primaryAccent else mc.textSecondary,
                textAlign = TextAlign.Center,
            )

        }
    }
}