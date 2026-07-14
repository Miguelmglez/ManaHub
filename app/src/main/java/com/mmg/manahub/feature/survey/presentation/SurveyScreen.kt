package com.mmg.manahub.feature.survey.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

// ─────────────────────────────────────────────────────────────────────────────
//  SurveyScreen — Phase 3 single-scroll redesign
//
//  One continuous LazyColumn with up to ten thematic sections. Every answer
//  mutates the ViewModel immediately (debounced DRAFT autosave) and a progress
//  bar in the bottom bar reflects [SurveyUiState.completionFraction]. The whole
//  form can be saved as a draft ("Save & exit") or submitted ("Submit review").
// ─────────────────────────────────────────────────────────────────────────────

private val ScreenPadding = 16.dp
private val SectionGap = 16.dp
private val ContentGap = 8.dp

@Composable
fun SurveyScreen(
    onComplete: () -> Unit,
    viewModel: SurveyViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recap = uiState.recap

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) onComplete()
    }

    // Back-press saves the in-progress draft, then pops.
    BackHandler {
        viewModel.postpone()
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.magicColors.primaryAccent)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SurveyBackground()

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                SurveyTopBar(onBack = { viewModel.postpone() })
            },
            bottomBar = {
                SurveyBottomBar(
                    uiState = uiState,
                    onSaveExit = { viewModel.postpone() },
                    onSubmit = { viewModel.complete() },
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    top = innerPadding.calculateTopPadding() + ContentGap,
                    bottom = innerPadding.calculateBottomPadding() + SectionGap,
                ),
                verticalArrangement = Arrangement.spacedBy(SectionGap),
            ) {
                recap?.let { r ->
                    item(key = "context_strip") {
                        ContextStrip(
                            recap = r,
                            deck = uiState.availableDecks.find { it.id == uiState.selectedDeckId },
                        )
                    }
                }

                item(key = "result") {
                    SurveySection(title = stringResource(R.string.survey_section_result)) {
                        ResultRow(uiState.resultAnswer, viewModel::setResult)
                    }
                }

                item(key = "elimination") {
                    AnimatedVisibility(
                        visible = (uiState.resultAnswer == "WIN" || uiState.resultAnswer == "LOSE"),
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut(),
                    ) {
                        SurveySection(title = stringResource(R.string.survey_section_elimination)) {
                            EliminationRow(
                                result = uiState.resultAnswer,
                                selected = uiState.eliminationCause,
                                onSelect = viewModel::setEliminationCause,
                            )
                        }
                    }
                }

                item(key = "opening_hand") {
                    SurveySection(title = stringResource(R.string.survey_section_opening_hand)) {
                        HandQualityRow(uiState.handQualityRating, viewModel::setHandQuality)
                        Spacer(Modifier.height(ContentGap))
                        MulliganRow(uiState.mulligansCount, viewModel::setMulligan)
                    }
                }

                item(key = "mana_flow") {
                    SurveySection(title = stringResource(R.string.survey_section_mana_flow)) {
                        ManaFlowRow(uiState.manaFlowAnswer, viewModel::setManaFlow)
                    }
                }

                item(key = "card_impact") {
                    CardImpactSection(uiState, viewModel)
                }

                if (recap?.opponents?.isNotEmpty() == true) {
                    item(key = "opponents") {
                        SurveySection(title = stringResource(R.string.survey_section_opponents)) {
                            recap.opponents.forEach { (playerId, name) ->
                                OpponentArchetypeRow(
                                    opponentName = name,
                                    selected = uiState.opponentArchetypes[playerId],
                                    onSelect = { archetype ->
                                        viewModel.setOpponentArchetype(playerId, archetype)
                                    },
                                )
                            }
                        }
                    }
                }

                item(key = "commander") {
                    AnimatedVisibility(
                        visible = recap?.mode == "COMMANDER",
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut(),
                    ) {
                        SurveySection(title = stringResource(R.string.survey_section_commander)) {
                            CommanderRow(uiState.commanderCarriedAnswer, viewModel::setCommanderCarried)
                        }
                    }
                }

                item(key = "sideboard") {
                    AnimatedVisibility(
                        visible = uiState.hasSideboard,
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut(),
                    ) {
                        SurveySection(title = stringResource(R.string.survey_section_sideboard)) {
                            SideboardRow(uiState.sideboardSwingAnswer, viewModel::setSideboardSwing)
                        }
                    }
                }

                item(key = "rating") {
                    SurveySection(title = stringResource(R.string.survey_section_rating)) {
                        StarRatingRow(
                            rating = uiState.answers["game_rating"]?.toIntOrNull() ?: 0,
                            onRate = { viewModel.setAnswer("game_rating", it.toString()) },
                        )
                    }
                }

                item(key = "notes") {
                    SurveySection(title = stringResource(R.string.survey_section_notes)) {
                        val mcInner = MaterialTheme.magicColors
                        OutlinedTextField(
                            value = uiState.freeNotes,
                            onValueChange = viewModel::setFreeNotes,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.survey_free_text_hint),
                                    style = MaterialTheme.magicTypography.bodySmall,
                                    color = mcInner.textDisabled,
                                )
                            },
                            textStyle = MaterialTheme.magicTypography.bodyMedium.copy(color = mcInner.textPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = mcInner.primaryAccent,
                                unfocusedBorderColor = mcInner.surfaceVariant,
                                cursorColor = mcInner.primaryAccent,
                            ),
                            maxLines = 6,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SurveyBackground
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SurveyBackground() {
    val mc = MaterialTheme.magicColors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(mc.background)
    ) {
        // Subtle gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            mc.backgroundSecondary,
                            mc.background,
                        )
                    )
                )
        )

        // Accent blur in the corner
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .graphicsLayer(translationX = 100f, translationY = -100f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            mc.primaryAccent.copy(alpha = 0.08f),
                            Color.Transparent,
                        )
                    )
                )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SurveyTopBar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SurveyTopBar(onBack: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = ContentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.survey_title),
            style = ty.titleLarge,
            color = mc.textPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = mc.textSecondary,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ContextStrip — horizontally scrollable chips summarising the finished game
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ContextStrip(recap: GameRecap, deck: Deck?) {
    val durationSec = (recap.durationMs / 1_000L)
    val durationLabel = "${durationSec / 60}m ${durationSec % 60}s"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ContentGap),
    ) {
        ContextChip(recap.mode)
        ContextChip(stringResource(R.string.survey_context_players, recap.playerCount))
        ContextChip(durationLabel)
        ContextChip(stringResource(R.string.survey_context_turns, recap.totalTurns))
        deck?.let { ContextChip(it.name) }
    }
}

@Composable
private fun ContextChip(label: String) {
    val mc = MaterialTheme.magicColors
    AssistChip(
        onClick = {},
        enabled = false,
        label = {
            Text(
                text = label,
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textSecondary,
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = mc.surface,
            disabledLabelColor = mc.textSecondary,
        ),
        border = AssistChipDefaults.assistChipBorder(enabled = false),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  SurveySection — titled card wrapper for one section of the form
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SurveySection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = mc.surface.copy(alpha = 0.7f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(ContentGap),
        ) {
            Text(
                text = title.uppercase(),
                style = ty.labelSmall,
                color = mc.primaryAccent,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
            )
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ResultRow — Win / Draw / Lose selection
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResultRow(selected: String?, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ContentGap),
    ) {
        ResultChip(
            label = stringResource(R.string.survey_result_win),
            selected = selected == "WIN",
            onClick = { onSelect("WIN") },
            modifier = Modifier.weight(1f),
        )
        ResultChip(
            label = stringResource(R.string.survey_result_draw),
            selected = selected == "DRAW",
            onClick = { onSelect("DRAW") },
            modifier = Modifier.weight(1f),
        )
        ResultChip(
            label = stringResource(R.string.survey_result_lose),
            selected = selected == "LOSE",
            onClick = { onSelect("LOSE") },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ResultChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    val scale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "result_chip_scale",
    )

    val containerColor by animateColorAsState(
        targetValue = if (selected) mc.primaryAccent else mc.surface.copy(alpha = 0.5f),
        label = "result_chip_bg",
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) mc.background else mc.textSecondary,
        label = "result_chip_text",
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale),
        color = containerColor,
        shape = RoundedCornerShape(10.dp),
        border = if (selected) null else BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                style = ty.labelMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  EliminationRow — context-sensitive set of win/loss causes
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EliminationRow(
    result: String?,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    val options: List<Pair<String, String>> = when (result) {
        "WIN" -> listOf(
            "COMBAT" to stringResource(R.string.survey_win_combat),
            "COMBO" to stringResource(R.string.survey_win_combo),
            "COMMANDER" to stringResource(R.string.survey_win_commander),
            "MILL" to stringResource(R.string.survey_win_mill),
            "POISON" to stringResource(R.string.survey_win_poison),
            "ALT" to stringResource(R.string.survey_win_alt),
        )
        "LOSE" -> listOf(
            "NO_STABILIZE" to stringResource(R.string.survey_lose_no_stabilize),
            "MANA" to stringResource(R.string.survey_lose_mana),
            "WIPE" to stringResource(R.string.survey_lose_wipe),
            "OVERWHELMED" to stringResource(R.string.survey_lose_overwhelmed),
            "CONCEDED" to stringResource(R.string.survey_lose_conceded),
        )
        else -> emptyList()
    }
    if (options.isEmpty()) return

    ChipFlowRow(
        options = options,
        selected = selected,
        onSelect = onSelect,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  HandQualityRow — 4-segment single choice (Unkeepable/Risky/Solid/Perfect)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HandQualityRow(selected: Int?, onSelect: (Int) -> Unit) {
    val mc = MaterialTheme.magicColors
    val options = listOf(
        1 to stringResource(R.string.survey_hand_unkeepable),
        2 to stringResource(R.string.survey_hand_risky),
        3 to stringResource(R.string.survey_hand_solid),
        4 to stringResource(R.string.survey_hand_perfect),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = mc.primaryAccent.copy(alpha = 0.2f),
                    activeContentColor = mc.primaryAccent,
                    inactiveContainerColor = mc.surfaceVariant,
                    inactiveContentColor = mc.textSecondary,
                ),
                label = { Text(label, style = MaterialTheme.magicTypography.labelSmall) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  MulliganRow — stepper bounded to 0..6
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MulliganRow(count: Int, onChange: (Int) -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.survey_mulligan_label),
            style = ty.bodyMedium,
            color = mc.textPrimary,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { if (count > 0) onChange(count - 1) },
            enabled = count > 0,
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "-",
                tint = if (count > 0) mc.primaryAccent else mc.textDisabled,
            )
        }
        Text(
            text = count.toString(),
            style = ty.titleMedium,
            color = mc.textPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp),
        )
        IconButton(
            onClick = { if (count < 6) onChange(count + 1) },
            enabled = count < 6,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "+",
                tint = if (count < 6) mc.primaryAccent else mc.textDisabled,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ManaFlowRow — Screwed / Tight / Smooth / Flooded
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManaFlowRow(selected: String?, onSelect: (String) -> Unit) {
    ChipFlowRow(
        options = listOf(
            "SCREWED" to stringResource(R.string.survey_mana_screwed),
            "TIGHT" to stringResource(R.string.survey_mana_tight),
            "SMOOTH" to stringResource(R.string.survey_mana_smooth),
            "FLOODED" to stringResource(R.string.survey_mana_flooded),
        ),
        selected = selected,
        onSelect = onSelect,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  CardImpactSection — collapsible deck-card MVP/DEAD tagging
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardImpactSection(uiState: SurveyUiState, viewModel: SurveyViewModel) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    var expanded by remember { mutableStateOf(false) }
    var showDeckPicker by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "card_impact_chevron",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = mc.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(ContentGap + 4.dp),
            verticalArrangement = Arrangement.spacedBy(ContentGap),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.survey_section_card_impact),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = mc.textSecondary,
                    modifier = Modifier.rotate(chevronRotation),
                )
            }

            AnimatedVisibility(visible = expanded) {
                if (!uiState.deckPickedForSeat) {
                    OutlinedButton(
                        onClick = { showDeckPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, mc.primaryAccent),
                    ) {
                        Text(
                            text = stringResource(R.string.survey_pick_deck),
                            color = mc.primaryAccent,
                        )
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(ContentGap),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        itemsIndexed(
                            items = uiState.deckCards,
                            key = { idx, _ -> idx },
                        ) { _, card ->
                            val ratingKey = "${uiState.localPlayerSessionId}:${card.scryfallId}"
                            CardImpactItem(
                                card = card,
                                impact = uiState.cardImpactRatings[ratingKey],
                                onCycle = { next ->
                                    viewModel.setCardImpact(
                                        uiState.localPlayerSessionId,
                                        card.scryfallId,
                                        next,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeckPicker) {
        DeckPickerSheet(
            decks = uiState.availableDecks,
            currentId = uiState.selectedDeckId,
            onSelect = { deckId ->
                viewModel.setDeckForSession(deckId)
                showDeckPicker = false
            },
            onDismiss = { showDeckPicker = false },
        )
    }
}

@Composable
private fun CardImpactItem(
    card: Card,
    impact: String?,
    onCycle: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    // neutral → MVP → DEAD → neutral
    val next = when (impact) {
        null -> "MVP"
        "MVP" -> "DEAD"
        else -> "DEAD"
    }
    val borderColor by animateColorAsState(
        targetValue = when (impact) {
            "MVP" -> mc.goldMtg
            "DEAD" -> mc.lifeNegative
            else -> Color.Transparent
        },
        label = "card_impact_border"
    )

    val scale by animateFloatAsState(
        targetValue = if (impact != null) 1.05f else 1f,
        label = "card_impact_scale"
    )

    Box(
        modifier = Modifier
            .width(96.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable { onCycle(next) },
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = 2.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                AsyncImage(
                    model = card.imageArtCrop ?: card.imageNormal,
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                
                AnimatedContent(
                    targetState = impact,
                    transitionSpec = {
                        (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
                    },
                    label = "impact_badge_anim"
                ) { targetImpact ->
                    when (targetImpact) {
                        "MVP" -> ImpactBadge(
                            icon = Icons.Default.Star,
                            tint = mc.goldMtg,
                        )
                        "DEAD" -> ImpactBadge(
                            icon = Icons.Default.Close,
                            tint = mc.lifeNegative,
                        )
                        else -> Unit
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = card.name,
                style = MaterialTheme.magicTypography.labelSmall,
                color = if (impact != null) mc.textPrimary else mc.textSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ImpactBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.TopEnd,
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(bottomStart = 8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .padding(3.dp)
                    .size(16.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  OpponentArchetypeRow — Aggro / Midrange / Control / Combo / Ramp
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpponentArchetypeRow(
    opponentName: String,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(ContentGap)) {
        Text(
            text = opponentName,
            style = ty.titleMedium,
            color = mc.textPrimary,
        )
        ChipFlowRow(
            options = listOf(
                "AGGRO" to stringResource(R.string.survey_archetype_aggro),
                "MIDRANGE" to stringResource(R.string.survey_archetype_midrange),
                "CONTROL" to stringResource(R.string.survey_archetype_control),
                "COMBO" to stringResource(R.string.survey_archetype_combo),
                "RAMP" to stringResource(R.string.survey_archetype_ramp),
            ),
            selected = selected,
            onSelect = onSelect,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  CommanderRow — Yes / Partially / No segmented choice
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommanderRow(selected: String?, onSelect: (String) -> Unit) {
    val mc = MaterialTheme.magicColors
    val options = listOf(
        "YES" to stringResource(R.string.survey_commander_carried_yes),
        "PARTIAL" to stringResource(R.string.survey_commander_carried_partial),
        "NO" to stringResource(R.string.survey_commander_carried_no),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = mc.primaryAccent.copy(alpha = 0.2f),
                    activeContentColor = mc.primaryAccent,
                    inactiveContainerColor = mc.surfaceVariant,
                    inactiveContentColor = mc.textSecondary,
                ),
                label = { Text(label, style = MaterialTheme.magicTypography.labelSmall) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SideboardRow — Yes, it swung it / No change
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SideboardRow(selected: String?, onSelect: (String) -> Unit) {
    ChipFlowRow(
        options = listOf(
            "YES" to stringResource(R.string.survey_sideboard_yes),
            "NO" to stringResource(R.string.survey_sideboard_no),
        ),
        selected = selected,
        onSelect = onSelect,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  StarRatingRow — 5 tappable stars
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StarRatingRow(rating: Int, onRate: (Int) -> Unit) {
    val mc = MaterialTheme.magicColors
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(5) { idx ->
            val starIndex = idx + 1
            val filled = starIndex <= rating

            val starScale by animateFloatAsState(
                targetValue = if (filled) 1.2f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "star_scale"
            )

            val starColor by animateColorAsState(
                targetValue = if (filled) mc.goldMtg else mc.textDisabled.copy(alpha = 0.3f),
                label = "star_color"
            )

            Icon(
                imageVector = if (filled) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = starColor,
                modifier = Modifier
                    .size(36.dp)
                    .graphicsLayer(scaleX = starScale, scaleY = starScale)
                    .clickable { onRate(starIndex) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shared chip flow used by several single-choice sections
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlowRow(
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ContentGap),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = selected == value
            val chipAlpha by animateFloatAsState(if (isSelected) 1f else 0.7f, label = "chip_alpha")
            val chipScale by animateFloatAsState(if (isSelected) 1.05f else 1f, label = "chip_scale")

            FilterChip(
                selected = isSelected,
                onClick = { onSelect(value) },
                label = { Text(label, style = ty.labelMedium) },
                modifier = Modifier
                    .graphicsLayer(scaleX = chipScale, scaleY = chipScale)
                    .alpha(chipAlpha),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = mc.primaryAccent.copy(alpha = 0.2f),
                    selectedLabelColor = mc.primaryAccent,
                    selectedLeadingIconColor = mc.primaryAccent,
                    containerColor = mc.surfaceVariant.copy(alpha = 0.4f),
                    labelColor = mc.textSecondary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = mc.surfaceVariant.copy(alpha = 0.5f),
                    selectedBorderColor = mc.primaryAccent.copy(alpha = 0.5f),
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  DeckPickerSheet — bottom sheet to associate a deck with this session
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckPickerSheet(
    decks: List<Deck>,
    currentId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = mc.textDisabled)
        },
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.survey_deck_picker_title),
                style = ty.titleMedium,
                color = mc.textPrimary,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 12.dp),
            )

            LazyColumn {
                items(decks, key = { it.id }) { deck ->
                    val selected = deck.id == currentId
                    Surface(
                        onClick = { onSelect(deck.id) },
                        color = if (selected) mc.primaryAccent.copy(alpha = 0.1f) else Color.Transparent,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ScreenPadding, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = deck.name,
                                style = ty.bodyMedium,
                                color = if (selected) mc.primaryAccent else mc.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            if (deck.format.isNotBlank()) {
                                Text(
                                    text = deck.format,
                                    style = ty.labelSmall,
                                    color = mc.textDisabled,
                                )
                            }
                        }
                    }
                }

                item {
                    Surface(
                        onClick = { onSelect(null) },
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.survey_deck_picker_none),
                            style = ty.bodyMedium,
                            color = mc.textSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ScreenPadding, vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SurveyBottomBar — completion progress + Save & exit / Submit review
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SurveyBottomBar(
    uiState: SurveyUiState,
    onSaveExit: () -> Unit,
    onSubmit: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    val animatedProgress by animateFloatAsState(
        targetValue = uiState.completionFraction,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "completion_progress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        mc.background.copy(alpha = 0.95f)
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(mc.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(mc.secondaryAccent, mc.primaryAccent)
                        )
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(ContentGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onSaveExit,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = mc.textSecondary)
            ) {
                Text(
                    text = stringResource(R.string.survey_save_exit),
                )
            }
            Button(
                onClick = onSubmit,
                enabled = uiState.resultAnswer != null,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = mc.primaryAccent,
                    disabledContainerColor = mc.surfaceVariant.copy(alpha = 0.3f)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.survey_submit),
                    color = if (uiState.resultAnswer != null) mc.background else mc.textDisabled,
                    style = ty.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
