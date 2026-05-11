package com.mmg.manahub.feature.tournament

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mmg.manahub.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.core.ui.theme.PlayerThemeColors
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentSetupScreen(
    onNavigateBack:      () -> Unit,
    onTournamentCreated: (Long) -> Unit,
    viewModel:           TournamentSetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope   = rememberCoroutineScope()
    val mc      = MaterialTheme.magicColors

    Scaffold(
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.backgroundSecondary),
            )
        },
        containerColor = mc.background,
    ) { padding ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
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
                    shape         = RoundedCornerShape(12.dp),
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = mc.primaryAccent,
                        unfocusedBorderColor = mc.surfaceVariant,
                        focusedTextColor     = mc.textPrimary,
                        unfocusedTextColor   = mc.textPrimary,
                        cursorColor          = mc.primaryAccent,
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    IconButton(
                        onClick = {
                            viewModel.onMatchesPerPairingChange(uiState.matchesPerPairing - 1)
                        }
                    ) {
                        Text("−", fontSize = 22.sp, color = mc.textPrimary)
                    }
                    Text(
                        text  = "${uiState.matchesPerPairing}",
                        style = MaterialTheme.magicTypography.displayMedium,
                        color = mc.primaryAccent,
                    )
                    IconButton(
                        onClick = {
                            viewModel.onMatchesPerPairingChange(uiState.matchesPerPairing + 1)
                        }
                    ) {
                        Text("+", fontSize = 22.sp, color = mc.textPrimary)
                    }
                    val label = if (uiState.matchesPerPairing == 1) stringResource(R.string.tournament_matches_per_pairing_single)
                                else stringResource(R.string.tournament_matches_per_pairing_multi, uiState.matchesPerPairing)
                    Text(
                        text  = label,
                        style = MaterialTheme.magicTypography.bodySmall,
                        color = mc.textSecondary,
                    )
                }
            }

            // ── Pairings toggle ────────────────────────────────────────────────
            item {
                SectionLabel(stringResource(R.string.tournament_pairings_label))
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(mc.surface)
                        .padding(16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
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
                        ),
                    )
                }
            }

            // ── Players header ─────────────────────────────────────────────────
            item {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    SectionLabel(stringResource(R.string.tournament_players_label, uiState.players.size))
                    TextButton(onClick = { viewModel.addPlayer() }) {
                        Text(
                            stringResource(R.string.tournament_add_player),
                            color = mc.primaryAccent,
                            style = MaterialTheme.magicTypography.labelMedium,
                        )
                    }
                }
            }

            // ── Player rows ────────────────────────────────────────────────────
            itemsIndexed(uiState.players) { index, config ->
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
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick  = {
                        scope.launch {
                            val id = viewModel.createTournament()
                            onTournamentCreated(id)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                    enabled  = !uiState.isCreating,
                ) {
                    if (uiState.isCreating) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(20.dp),
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
        }
    }
}

// ── Auxiliary composables ──────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.magicTypography.labelMedium,
        color    = MaterialTheme.magicColors.textSecondary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
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
        shape    = RoundedCornerShape(10.dp),
        color    = if (selected) mc.primaryAccent.copy(alpha = 0.15f) else mc.surface,
        border   = BorderStroke(
            width = if (selected) 1.5.dp else 0.5.dp,
            color = if (selected) mc.primaryAccent.copy(alpha = 0.7f) else mc.surfaceVariant,
        ),
    ) {
        Text(
            text      = label,
            style     = MaterialTheme.magicTypography.labelMedium,
            color     = if (selected) mc.primaryAccent else mc.textSecondary,
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
        shape    = RoundedCornerShape(12.dp),
        color    = if (selected) mc.primaryAccent.copy(alpha = 0.1f) else mc.surface,
        border   = BorderStroke(
            width = if (selected) 1.5.dp else 0.5.dp,
            color = if (selected) mc.primaryAccent.copy(alpha = 0.6f) else mc.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier              = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(icon, fontSize = 24.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = title,
                    style = MaterialTheme.magicTypography.bodyMedium,
                    color = if (selected) mc.primaryAccent else mc.textPrimary,
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
                    modifier           = Modifier.size(20.dp),
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Color dot / picker trigger
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(config.theme.accent)
                .border(1.5.dp, config.theme.accent.copy(alpha = 0.5f), CircleShape)
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
            shape         = RoundedCornerShape(10.dp),
            singleLine    = true,
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = mc.primaryAccent,
                unfocusedBorderColor = mc.surfaceVariant,
                focusedTextColor     = mc.textPrimary,
                unfocusedTextColor   = mc.textPrimary,
                cursorColor          = mc.primaryAccent,
            ),
        )

        // Remove button
        if (onRemove != null) {
            TextButton(
                onClick          = onRemove,
                contentPadding   = PaddingValues(horizontal = 8.dp),
            ) {
                Text("−", fontSize = 20.sp, color = mc.lifeNegative)
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
