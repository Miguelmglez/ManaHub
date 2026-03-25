package com.mmg.magicfolder.feature.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography
import com.mmg.magicfolder.feature.decks.engine.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckBuilderScreen(
    onBack:      () -> Unit,
    viewModel:   DeckBuilderEngine = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val mc    = MaterialTheme.magicColors

    // Navigate away once deck is saved
    if (state.savedDeckId != null) {
        LaunchedEffect(Unit) { onBack() }
    }

    Scaffold(
        containerColor = mc.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (state.step) {
                            DeckBuilderStep.SETUP    -> "New Deck"
                            DeckBuilderStep.BUILDING -> "${state.mainboardCount} / ${state.targetSize}"
                            DeckBuilderStep.REVIEW   -> "Review Deck"
                        },
                        style = MaterialTheme.magicTypography.titleLarge,
                        color = mc.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.step == DeckBuilderStep.SETUP) onBack()
                        else viewModel.goBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = mc.textSecondary)
                    }
                },
                actions = {
                    if (state.step == DeckBuilderStep.BUILDING && state.mainboardCount > 0) {
                        TextButton(onClick = viewModel::goToReview) {
                            Text("Review", color = mc.primaryAccent,
                                style = MaterialTheme.magicTypography.labelLarge)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.backgroundSecondary),
            )
        },
    ) { padding ->
        when (state.step) {
            DeckBuilderStep.SETUP    -> SetupStep(
                state    = state,
                modifier = Modifier.padding(padding),
                onNameChange     = viewModel::setDeckName,
                onFormatSelect   = viewModel::setFormat,
                onColorToggle    = viewModel::toggleColor,
                onStrategySelect = viewModel::setSeedStrategy,
                onContinue       = viewModel::goToBuilding,
            )
            DeckBuilderStep.BUILDING -> BuildingStep(
                state    = state,
                modifier = Modifier.padding(padding),
                onDecide = viewModel::decide,
                onReview = viewModel::goToReview,
            )
            DeckBuilderStep.REVIEW   -> ReviewStep(
                state    = state,
                modifier = Modifier.padding(padding),
                onRemoveMain    = viewModel::removeFromMainboard,
                onRemoveSide    = viewModel::removeFromSideboard,
                onMoveToSide    = viewModel::moveToSideboard,
                onSave          = viewModel::saveDeck,
                onClearError    = viewModel::clearError,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Step 1 — Setup
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SetupStep(
    state:           DeckBuilderUiState,
    modifier:        Modifier,
    onNameChange:    (String) -> Unit,
    onFormatSelect:  (GameFormat) -> Unit,
    onColorToggle:   (ManaColor) -> Unit,
    onStrategySelect:(SeedStrategy) -> Unit,
    onContinue:      () -> Unit,
) {
    val mc = MaterialTheme.magicColors

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            // ── Deck name ─────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Deck Name")
                OutlinedTextField(
                    value         = state.deckName,
                    onValueChange = onNameChange,
                    placeholder   = { Text("e.g. Izzet Tempo", color = mc.textDisabled) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = mc.primaryAccent,
                        unfocusedBorderColor = mc.surfaceVariant,
                        focusedTextColor     = mc.textPrimary,
                        unfocusedTextColor   = mc.textPrimary,
                        cursorColor          = mc.primaryAccent,
                    ),
                    textStyle = MaterialTheme.magicTypography.bodyLarge,
                )
            }

            // ── Format ────────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Format")
                FormatGrid(selected = state.format, onSelect = onFormatSelect)
            }

            // ── Color identity ────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Color Identity  (optional)")
                ColorIdentityRow(selected = state.selectedColors, onToggle = onColorToggle)
            }

            // ── Seed strategy ─────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Strategy")
                StrategyGrid(selected = state.seedStrategy, onSelect = onStrategySelect)
            }
        }

        // ── Continue button ────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(mc.background)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Button(
                onClick  = onContinue,
                enabled  = state.canAdvanceSetup,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = mc.primaryAccent,
                    disabledContainerColor = mc.surfaceVariant,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    "Build Deck",
                    style = MaterialTheme.magicTypography.titleMedium,
                    color = if (state.canAdvanceSetup) mc.background else mc.textDisabled,
                )
            }
        }
    }
}

@Composable
private fun FormatGrid(selected: GameFormat, onSelect: (GameFormat) -> Unit) {
    val mc  = MaterialTheme.magicColors
    val row = GameFormat.entries.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        row.forEach { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chunk.forEach { fmt ->
                    val sel = fmt == selected
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) mc.primaryAccent.copy(alpha = 0.15f) else mc.surface)
                            .border(
                                width = if (sel) 1.5.dp else 0.5.dp,
                                color = if (sel) mc.primaryAccent else mc.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable { onSelect(fmt) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            text  = fmt.displayName,
                            style = MaterialTheme.magicTypography.labelMedium,
                            color = if (sel) mc.primaryAccent else mc.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorIdentityRow(
    selected: Set<ManaColor>,
    onToggle: (ManaColor) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ManaColor.entries.forEach { color ->
            val sel = color in selected
            val bg  = manaColorBg(color)
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (sel) bg else bg.copy(alpha = 0.25f))
                    .border(
                        width = if (sel) 2.dp else 0.dp,
                        color = if (sel) Color.White.copy(alpha = 0.6f) else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable { onToggle(color) },
            ) {
                Text(
                    text  = color.symbol,
                    style = MaterialTheme.magicTypography.titleMedium,
                    color = Color.White,
                )
            }
        }
    }
}

private fun manaColorBg(color: ManaColor): Color = when (color) {
    ManaColor.W -> Color(0xFFF5F0D0)
    ManaColor.U -> Color(0xFF0E68AB)
    ManaColor.B -> Color(0xFF21160A)
    ManaColor.R -> Color(0xFFD32029)
    ManaColor.G -> Color(0xFF00733E)
}

@Composable
private fun StrategyGrid(
    selected: SeedStrategy?,
    onSelect: (SeedStrategy) -> Unit,
) {
    val mc  = MaterialTheme.magicColors
    val rows = SeedStrategy.entries.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { strategy ->
                    val sel = strategy == selected
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) mc.primaryAccent.copy(alpha = 0.15f) else mc.surface)
                            .border(
                                width = if (sel) 1.5.dp else 0.5.dp,
                                color = if (sel) mc.primaryAccent else mc.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { onSelect(strategy) }
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(strategy.icon, style = MaterialTheme.magicTypography.titleLarge)
                        Text(
                            strategy.displayName,
                            style     = MaterialTheme.magicTypography.labelMedium,
                            color     = if (sel) mc.primaryAccent else mc.textPrimary,
                            textAlign = TextAlign.Center,
                            maxLines  = 1,
                        )
                    }
                }
                // Pad last row if fewer than 3
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Step 2 — Building
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun BuildingStep(
    state:    DeckBuilderUiState,
    modifier: Modifier,
    onDecide: (PathDecision) -> Unit,
    onReview: () -> Unit,
) {
    val mc = MaterialTheme.magicColors

    Column(
        modifier            = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Deck progress ──────────────────────────────────────────────────────
        DeckProgressBar(state = state)

        // ── Main content ───────────────────────────────────────────────────────
        when {
            state.isLoadingQueue -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = mc.primaryAccent)
                }
            }
            state.queueIsExhausted -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("All cards reviewed", style = MaterialTheme.magicTypography.titleMedium,
                            color = mc.textSecondary)
                        Text("${state.mainboardCount} cards in deck",
                            style = MaterialTheme.magicTypography.bodyMedium, color = mc.textDisabled)
                        Button(
                            onClick = onReview,
                            colors  = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                            shape   = RoundedCornerShape(8.dp),
                        ) {
                            Text("Review Deck", color = mc.background,
                                style = MaterialTheme.magicTypography.titleMedium)
                        }
                    }
                }
            }
            else -> {
                state.currentSuggestion?.let { suggestion ->
                    SuggestionCard(
                        suggestion = suggestion,
                        modifier   = Modifier.weight(1f),
                    )
                    DecisionRow(onDecide = onDecide)
                }
            }
        }
    }
}

@Composable
private fun DeckProgressBar(state: DeckBuilderUiState) {
    val mc = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier            = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "${state.mainboardCount} / ${state.targetSize} cards",
                style = MaterialTheme.magicTypography.labelLarge,
                color = mc.textSecondary,
            )
            Text(
                "${(state.progressFraction * 100).toInt()}%",
                style = MaterialTheme.magicTypography.labelLarge,
                color = mc.primaryAccent,
            )
        }
        LinearProgressIndicator(
            progress       = { state.progressFraction },
            modifier       = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color          = mc.primaryAccent,
            trackColor     = mc.surfaceVariant,
        )
    }
}

@Composable
private fun SuggestionCard(
    suggestion: CardSuggestion,
    modifier:   Modifier,
) {
    val mc   = MaterialTheme.magicColors
    val card = suggestion.card

    Card(
        modifier = modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = mc.surface),
        shape    = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Art
            AsyncImage(
                model              = card.imageNormal,
                contentDescription = card.name,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxWidth().weight(1f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            )

            // Info panel
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        card.name,
                        style    = MaterialTheme.magicTypography.titleMedium,
                        color    = mc.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "CMC ${card.cmc.toInt()}",
                        style = MaterialTheme.magicTypography.labelLarge,
                        color = mc.textSecondary,
                    )
                }

                // Score bar
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LinearProgressIndicator(
                        progress   = { suggestion.score },
                        modifier   = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color      = scoreColor(suggestion.score, mc.primaryAccent, mc.goldMtg, mc.lifeNegative),
                        trackColor = mc.surfaceVariant,
                    )
                    Text(
                        "${(suggestion.score * 100).toInt()}%",
                        style = MaterialTheme.magicTypography.labelMedium,
                        color = mc.textSecondary,
                    )
                }

                // Reason chips
                if (suggestion.reasons.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier              = Modifier.fillMaxWidth(),
                    ) {
                        suggestion.reasons.take(3).forEach { reason ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(mc.primaryAccent.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    reason,
                                    style    = MaterialTheme.magicTypography.labelSmall,
                                    color    = mc.primaryAccent,
                                    maxLines = 1,
                                )
                            }
                        }
                        if (suggestion.isOwned) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(mc.lifePositive.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    "Owned",
                                    style = MaterialTheme.magicTypography.labelSmall,
                                    color = mc.lifePositive,
                                )
                            }
                        }
                    }
                }

                // Card type line
                Text(
                    card.typeLine,
                    style    = MaterialTheme.magicTypography.bodySmall,
                    color    = mc.textDisabled,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DecisionRow(onDecide: (PathDecision) -> Unit) {
    val mc = MaterialTheme.magicColors
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Skip
        OutlinedButton(
            onClick  = { onDecide(PathDecision.SKIP) },
            modifier = Modifier.weight(1f).height(48.dp),
            border   = BorderStroke(1.dp, mc.surfaceVariant),
            shape    = RoundedCornerShape(8.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = null, tint = mc.textSecondary,
                modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Skip", style = MaterialTheme.magicTypography.labelLarge, color = mc.textSecondary)
        }

        // Add
        Button(
            onClick  = { onDecide(PathDecision.ADD) },
            modifier = Modifier.weight(2f).height(48.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
            shape    = RoundedCornerShape(8.dp),
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = mc.background,
                modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add to Deck", style = MaterialTheme.magicTypography.labelLarge, color = mc.background)
        }

        // Sideboard
        OutlinedButton(
            onClick  = { onDecide(PathDecision.SIDEBOARD) },
            modifier = Modifier.weight(1f).height(48.dp),
            border   = BorderStroke(1.dp, mc.secondaryAccent.copy(alpha = 0.5f)),
            shape    = RoundedCornerShape(8.dp),
        ) {
            Text("Side", style = MaterialTheme.magicTypography.labelLarge, color = mc.secondaryAccent)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Step 3 — Review
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ReviewStep(
    state:        DeckBuilderUiState,
    modifier:     Modifier,
    onRemoveMain: (String) -> Unit,
    onRemoveSide: (String) -> Unit,
    onMoveToSide: (String) -> Unit,
    onSave:       () -> Unit,
    onClearError: () -> Unit,
) {
    val mc = MaterialTheme.magicColors

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // ── Mainboard ──────────────────────────────────────────────────────
            item {
                SectionLabel("Mainboard  (${state.mainboardCount} cards)", Modifier.padding(bottom = 8.dp))
            }
            if (state.mainboard.isEmpty()) {
                item {
                    Text(
                        "No cards added yet.",
                        style    = MaterialTheme.magicTypography.bodyMedium,
                        color    = mc.textDisabled,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            } else {
                items(state.mainboard, key = { it.card.scryfallId }) { entry ->
                    DeckEntryRow(
                        entry          = entry,
                        onRemove       = { onRemoveMain(entry.card.scryfallId) },
                        onMoveToSide   = { onMoveToSide(entry.card.scryfallId) },
                        showMoveToSide = true,
                    )
                }
            }

            // ── Sideboard ──────────────────────────────────────────────────────
            if (state.sideboard.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    SectionLabel("Sideboard  (${state.sideboardCount} cards)", Modifier.padding(bottom = 8.dp))
                }
                items(state.sideboard, key = { "side_${it.card.scryfallId}" }) { entry ->
                    DeckEntryRow(
                        entry          = entry,
                        onRemove       = { onRemoveSide(entry.card.scryfallId) },
                        onMoveToSide   = {},
                        showMoveToSide = false,
                    )
                }
            }
        }

        // ── Save button ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(mc.background)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Button(
                onClick  = onSave,
                enabled  = state.mainboard.isNotEmpty() && !state.isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = mc.primaryAccent,
                    disabledContainerColor = mc.surfaceVariant,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color    = mc.background,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        "Save Deck",
                        style = MaterialTheme.magicTypography.titleMedium,
                        color = if (state.mainboard.isNotEmpty()) mc.background else mc.textDisabled,
                    )
                }
            }
        }

        // ── Error snackbar ─────────────────────────────────────────────────────
        state.error?.let { msg ->
            Snackbar(
                action = {
                    TextButton(onClick = onClearError) {
                        Text("Dismiss", color = mc.primaryAccent)
                    }
                },
                modifier         = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                containerColor   = mc.lifeNegative.copy(alpha = 0.9f),
                contentColor     = mc.textPrimary,
            ) {
                Text(msg, style = MaterialTheme.magicTypography.bodySmall)
            }
        }
    }
}

@Composable
private fun DeckEntryRow(
    entry:          DeckEntry,
    onRemove:       () -> Unit,
    onMoveToSide:   () -> Unit,
    showMoveToSide: Boolean,
) {
    val mc = MaterialTheme.magicColors
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(mc.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Small art thumbnail
        AsyncImage(
            model              = entry.card.imageArtCrop,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(mc.surfaceVariant),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.card.name,
                style    = MaterialTheme.magicTypography.bodyMedium,
                color    = mc.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "CMC ${entry.card.cmc.toInt()}",
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textDisabled,
                )
                if (entry.isOwned) {
                    Text("·", style = MaterialTheme.magicTypography.labelSmall, color = mc.textDisabled)
                    Text("Owned", style = MaterialTheme.magicTypography.labelSmall, color = mc.lifePositive)
                }
            }
        }

        if (showMoveToSide) {
            IconButton(onClick = onMoveToSide, modifier = Modifier.size(32.dp)) {
                Text("→SB", style = MaterialTheme.magicTypography.labelSmall, color = mc.secondaryAccent)
            }
        }

        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Remove",
                tint = mc.textDisabled, modifier = Modifier.size(16.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Shared helpers
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text     = text,
        style    = MaterialTheme.magicTypography.labelLarge,
        color    = MaterialTheme.magicColors.textSecondary,
        modifier = modifier,
    )
}

private fun scoreColor(score: Float, high: Color, mid: Color, low: Color): Color = when {
    score >= 0.7f -> high
    score >= 0.4f -> mid
    else          -> low
}
