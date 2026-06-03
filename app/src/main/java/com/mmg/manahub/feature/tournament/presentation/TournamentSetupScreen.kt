package com.mmg.manahub.feature.tournament.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.HexGridBackground
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.core.ui.theme.PlayerThemeColors
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.tournament.domain.model.PlayerConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentSetupScreen(
    onNavigateBack:      () -> Unit,
    onTournamentCreated: (Long) -> Unit,
    viewModel:           TournamentSetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc      = MaterialTheme.magicColors

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { id -> onTournamentCreated(id) }
    }

    Box(modifier = Modifier.fillMaxSize().background(mc.background)) {
        HexGridBackground(modifier = Modifier.fillMaxSize(), color = mc.primaryAccent.copy(alpha = 0.05f))

        Scaffold(
            contentWindowInsets = WindowInsets.statusBars,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.tournament_setup_title),
                            style = MaterialTheme.magicTypography.titleMedium,
                            color = mc.textPrimary,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                tint = mc.textPrimary,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            containerColor = Color.Transparent,
        ) { padding ->
            LazyColumn(
                modifier            = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding      = PaddingValues(vertical = 16.dp),
            ) {

                // ── Tournament name ────────────────────────────────────────────────
                item {
                    SectionLabel(stringResource(R.string.tournament_name_label))
                    OutlinedTextField(
                        value         = uiState.name,
                        onValueChange = viewModel::onNameChange,
                        modifier      = Modifier.fillMaxWidth(),
                        placeholder   = {
                            Text(
                                stringResource(R.string.tournament_name_hint),
                                style = MaterialTheme.magicTypography.bodySmall,
                                color = mc.textDisabled,
                            )
                        },
                        shape         = RoundedCornerShape(14.dp),
                        singleLine    = true,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = mc.primaryAccent,
                            unfocusedBorderColor = mc.surfaceVariant,
                            focusedTextColor     = mc.textPrimary,
                            unfocusedTextColor   = mc.textPrimary,
                            cursorColor          = mc.primaryAccent,
                            focusedContainerColor = mc.surface.copy(alpha = 0.5f),
                            unfocusedContainerColor = mc.surface.copy(alpha = 0.3f),
                        ),
                    )
                }

                // ── Format ─────────────────────────────────────────────────────────
                item {
                    SectionLabel(stringResource(R.string.tournament_format_label))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("COMMANDER", "STANDARD", "DRAFT").forEach { fmt ->
                            val label = when (fmt) {
                                "COMMANDER" -> stringResource(R.string.format_commander)
                                "STANDARD"  -> stringResource(R.string.format_standard)
                                "DRAFT"     -> stringResource(R.string.format_draft)
                                else        -> fmt.lowercase().replaceFirstChar { it.uppercase() }
                            }
                            FormatChip(
                                label    = label,
                                selected = uiState.format == fmt,
                                onClick  = { viewModel.onFormatChange(fmt) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // ── Structure ──────────────────────────────────────────────────────
                item {
                    SectionLabel(stringResource(R.string.tournament_structure_label))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StructureOption(
                            title       = stringResource(R.string.tournament_structure_round_robin),
                            description = stringResource(R.string.tournament_structure_round_robin_desc),
                            icon        = "⟳",
                            selected    = uiState.structure == "ROUND_ROBIN",
                            onClick     = { viewModel.onStructureChange("ROUND_ROBIN") },
                        )
                        StructureOption(
                            title       = stringResource(R.string.tournament_structure_swiss),
                            description = stringResource(R.string.tournament_structure_swiss_desc),
                            icon        = "♟",
                            selected    = uiState.structure == "SWISS",
                            onClick     = { viewModel.onStructureChange("SWISS") },
                        )
                        StructureOption(
                            title       = stringResource(R.string.tournament_structure_elimination),
                            description = stringResource(R.string.tournament_structure_elimination_desc),
                            icon        = "⚔",
                            selected    = uiState.structure == "SINGLE_ELIM",
                            onClick     = { viewModel.onStructureChange("SINGLE_ELIM") },
                        )
                    }
                }

                // ── Matches per pairing ────────────────────────────────────────────
                item {
                    SectionLabel(stringResource(R.string.tournament_matches_per_pairing))
                    Surface(
                        color  = mc.surface.copy(alpha = 0.4f),
                        shape  = RoundedCornerShape(16.dp),
                        border = BorderStroke(0.5.dp, mc.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier              = Modifier.padding(16.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            IconButton(
                                onClick = {
                                    viewModel.onMatchesPerPairingChange(uiState.matchesPerPairing - 1)
                                }
                            ) {
                                Text("−", fontSize = 28.sp, color = mc.primaryAccent)
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier            = Modifier.padding(horizontal = 24.dp)
                            ) {
                                Text(
                                    text  = "${uiState.matchesPerPairing}",
                                    style = MaterialTheme.magicTypography.displayMedium,
                                    color = mc.textPrimary,
                                )
                                val label = if (uiState.matchesPerPairing == 1) stringResource(R.string.tournament_matches_per_pairing_single)
                                else stringResource(R.string.tournament_matches_per_pairing_multi, uiState.matchesPerPairing)
                                Text(
                                    text  = label,
                                    style = MaterialTheme.magicTypography.labelSmall,
                                    color = mc.textSecondary,
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.onMatchesPerPairingChange(uiState.matchesPerPairing + 1)
                                }
                            ) {
                                Text("+", fontSize = 28.sp, color = mc.primaryAccent)
                            }
                        }
                    }
                }

                // ── Pairings toggle ────────────────────────────────────────────────
                item {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(mc.surface.copy(alpha = 0.4f))
                            .border(0.5.dp, mc.surfaceVariant, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text  = if (uiState.isRandomPairings) stringResource(R.string.tournament_pairings_random) else stringResource(R.string.tournament_pairings_manual),
                                style = MaterialTheme.magicTypography.bodyMedium,
                                color = mc.textPrimary,
                            )
                            Text(
                                text  = if (uiState.isRandomPairings) stringResource(R.string.tournament_pairings_random_desc)
                                        else stringResource(R.string.tournament_pairings_manual_desc),
                                style = MaterialTheme.magicTypography.bodySmall,
                                color = mc.textSecondary,
                            )
                        }
                        Switch(
                            checked         = uiState.isRandomPairings,
                            onCheckedChange = viewModel::onRandomPairingsChange,
                            colors          = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = mc.primaryAccent,
                                uncheckedBorderColor = Color.Transparent,
                            ),
                        )
                    }
                }

                // ── Players header ─────────────────────────────────────────────────
                item {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.Bottom,
                    ) {
                        SectionLabel(stringResource(R.string.tournament_players_label, uiState.players.size))
                        TextButton(
                            onClick          = { viewModel.addPlayer() },
                            modifier         = Modifier.padding(bottom = 4.dp),
                            contentPadding   = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                stringResource(R.string.tournament_add_player),
                                color = mc.primaryAccent,
                                style = MaterialTheme.magicTypography.labelLarge,
                            )
                        }
                    }
                    HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))
                }

                // ── Player rows ────────────────────────────────────────────────────
                itemsIndexed(uiState.players, key = { _, p -> p.id }) { index, config ->
                    PlayerConfigRow(
                        config       = config,
                        usedThemes   = uiState.players.filter { it.id != config.id }.map { it.theme },
                        onNameChange = { name -> viewModel.updatePlayerName(index, name) },
                        onColorChange = { theme -> viewModel.updatePlayerTheme(index, theme) },
                        onRemove     = if (uiState.players.size > 2) ({ viewModel.removePlayer(index) }) else null,
                    )
                }

                // ── Create button ──────────────────────────────────────────────────
                item {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick  = { viewModel.createTournament() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape    = RoundedCornerShape(16.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = mc.primaryAccent,
                            disabledContainerColor = mc.primaryAccent.copy(alpha = 0.5f)
                        ),
                        enabled  = !uiState.isCreating && uiState.name.isNotBlank(),
                    ) {
                        if (uiState.isCreating) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(24.dp),
                                color       = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                stringResource(R.string.tournament_create_button),
                                style = MaterialTheme.magicTypography.labelLarge,
                            )
                        }
                    }
                }

                if (uiState.error != null) {
                    item {
                        Surface(
                            color = mc.lifeNegative.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text     = uiState.error!!,
                                style    = MaterialTheme.magicTypography.bodySmall,
                                color    = mc.lifeNegative,
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Auxiliary composables ──────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Column {
        Text(
            text     = text.uppercase(),
            style    = MaterialTheme.magicTypography.labelLarge,
            color    = MaterialTheme.magicColors.primaryAccent,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun FormatChip(
    label:    String,
    selected: Boolean,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Surface(
        onClick  = onClick,
        modifier = modifier,
        shape    = RoundedCornerShape(12.dp),
        color    = if (selected) mc.primaryAccent.copy(alpha = 0.15f) else mc.surface.copy(alpha = 0.3f),
        border   = BorderStroke(
            width = if (selected) 2.dp else 0.5.dp,
            color = if (selected) mc.primaryAccent else mc.surfaceVariant,
        ),
    ) {
        Text(
            text      = label,
            style     = MaterialTheme.magicTypography.labelMedium,
            color     = if (selected) mc.textPrimary else mc.textSecondary,
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun StructureOption(
    title:       String,
    description: String,
    icon:        String,
    selected:    Boolean,
    onClick:     () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(16.dp),
        color    = if (selected) mc.primaryAccent.copy(alpha = 0.1f) else mc.surface.copy(alpha = 0.3f),
        border   = BorderStroke(
            width = if (selected) 2.dp else 0.5.dp,
            color = if (selected) mc.primaryAccent else mc.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (selected) mc.primaryAccent.copy(alpha = 0.2f) else mc.surfaceVariant.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 24.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = title,
                    style = MaterialTheme.magicTypography.bodyLarge,
                    color = if (selected) mc.textPrimary else mc.textPrimary,
                )
                Text(
                    text  = description,
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textSecondary,
                )
            }
            if (selected) {
                Icon(
                    imageVector        = Icons.Default.Check,
                    contentDescription = null,
                    tint               = mc.primaryAccent,
                    modifier           = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun PlayerConfigRow(
    config:       PlayerConfig,
    usedThemes:   List<PlayerThemeColors>,
    onNameChange: (String) -> Unit,
    onColorChange: (PlayerThemeColors) -> Unit,
    onRemove:     (() -> Unit)?,
) {
    val mc            = MaterialTheme.magicColors
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Color dot / picker trigger
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(config.theme.accent)
                .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                .clickable { showPicker = true },
        )

        // Name field
        OutlinedTextField(
            value         = config.name,
            onValueChange = onNameChange,
            placeholder   = {
                Text(
                    stringResource(R.string.gamesetup_player_name_hint, config.id + 1),
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textDisabled,
                )
            },
            modifier      = Modifier.weight(1f),
            shape         = RoundedCornerShape(12.dp),
            singleLine    = true,
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = mc.primaryAccent,
                unfocusedBorderColor = mc.surfaceVariant,
                focusedTextColor     = mc.textPrimary,
                unfocusedTextColor   = mc.textPrimary,
                cursorColor          = mc.primaryAccent,
                focusedContainerColor = mc.surface.copy(alpha = 0.5f),
                unfocusedContainerColor = mc.surface.copy(alpha = 0.2f),
            ),
        )

        // Remove button
        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Text("✕", fontSize = 18.sp, color = mc.lifeNegative.copy(alpha = 0.7f))
            }
        }
    }

    // Color picker dropdown
    if (showPicker) {
        ColorPickerDialog(
            current    = config.theme,
            usedThemes = usedThemes,
            onSelect   = { onColorChange(it); showPicker = false },
            onDismiss  = { showPicker = false },
        )
    }
}

@Composable
private fun ColorPickerDialog(
    current:    PlayerThemeColors,
    usedThemes: List<PlayerThemeColors>,
    onSelect:   (PlayerThemeColors) -> Unit,
    onDismiss:  () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = mc.surface,
        title            = {
            Text(stringResource(R.string.gamesetup_choose_color), style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerTheme.ALL.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { theme ->
                            val taken = theme in usedThemes && theme != current
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (taken) theme.accent.copy(alpha = 0.25f)
                                        else theme.accent
                                    )
                                    .border(
                                        width = if (theme == current) 3.dp else 1.dp,
                                        color = if (theme == current) mc.textPrimary
                                                else theme.accent.copy(alpha = 0.4f),
                                        shape = CircleShape,
                                    )
                                    .clickable(enabled = !taken) { onSelect(theme) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}
