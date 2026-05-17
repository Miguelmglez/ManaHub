package com.mmg.manahub.feature.survey.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Style
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.ui.components.CardListItem
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

// ─────────────────────────────────────────────────────────────────────────────
//  SurveyScreen — Phase 2 multi-panel implementation
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SurveyScreen(
    onComplete: () -> Unit,
    viewModel: SurveyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val toastState = rememberMagicToastState()
    val savedMsg = stringResource(R.string.survey_saved_toast)

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) onComplete()
    }

    // Back-press handling: go to previous panel, or postpone on panel 0
    BackHandler {
        if (uiState.currentPanel > 0) {
            viewModel.setCurrentPanel(uiState.currentPanel - 1)
        } else {
            viewModel.postpone()
        }
    }

    if (uiState.isLoading || uiState.panels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.magicColors.primaryAccent)
        }
        return
    }

    val mc = MaterialTheme.magicColors

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets.statusBars,
            containerColor = mc.background,
            topBar = {
                SurveyTopBar(
                    panelCount = uiState.panels.size,
                    currentIndex = uiState.currentPanel,
                    reviewMode = uiState.surveyMode == SurveyMode.REVIEW,
                    onLater = { viewModel.postpone() },
                    onClose = {
                        if (uiState.surveyMode == SurveyMode.REVIEW) viewModel.dismissWithoutChanges()
                        else viewModel.postpone()
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                // Sticky game recap card
                uiState.recap?.let { recap ->
                    val selectedDeck = uiState.availableDecks.find { it.id == uiState.selectedDeckId }
                    GameRecapCard(
                        recap = recap,
                        deck = selectedDeck,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                // Animated panel content
                AnimatedContent(
                    targetState = uiState.currentPanel,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally(
                                animationSpec = tween(300),
                                initialOffsetX = { it },
                            ) togetherWith slideOutHorizontally(
                                animationSpec = tween(300),
                                targetOffsetX = { -it },
                            )
                        } else {
                            slideInHorizontally(
                                animationSpec = tween(300),
                                initialOffsetX = { -it },
                            ) togetherWith slideOutHorizontally(
                                animationSpec = tween(300),
                                targetOffsetX = { it },
                            )
                        }
                    },
                    label = "panel_transition",
                    modifier = Modifier.weight(1f),
                ) { panelIndex ->
                    val panel = uiState.panels.getOrNull(panelIndex)
                    when (panel?.id) {
                        SurveyPanelId.MOOD -> MoodPanel(
                            panel = panel,
                            answers = uiState.answers,
                            onAnswer = { qId, answer -> viewModel.setAnswer(qId, answer) },
                        )
                        SurveyPanelId.FUNDAMENTALS -> FundamentalsPanel(
                            panel = panel,
                            answers = uiState.answers,
                            onAnswer = { qId, answer -> viewModel.setAnswer(qId, answer) },
                        )
                        SurveyPanelId.CARD_IMPACT -> CardImpactPanel(
                            panel = panel,
                            uiState = uiState,
                            onSetCardImpact = { id, rating -> viewModel.setCardImpact(id, rating) },
                            onAddCard = { card -> viewModel.addExtraImpactCard(card) },
                            onSelectDeck = { deckId -> viewModel.selectDeck(deckId) },
                        )
                        SurveyPanelId.SUMMARY -> SummaryPanel(
                            uiState = uiState,
                            onNavigateToPanel = { idx -> viewModel.setCurrentPanel(idx) },
                            onNotesChange = { text -> viewModel.setFreeNotes(text) },
                        )
                        null -> Unit
                    }
                }

                // Bottom navigation row
                BottomNavRow(
                    currentPanel = uiState.currentPanel,
                    totalPanels = uiState.panels.size,
                    surveyMode = uiState.surveyMode,
                    onBack = { viewModel.setCurrentPanel(uiState.currentPanel - 1) },
                    onNext = { viewModel.setCurrentPanel(uiState.currentPanel + 1) },
                    onComplete = {
                        viewModel.complete()
                        toastState.show(savedMsg, MagicToastType.SUCCESS)
                    },
                )
            }
        }

        MagicToastHost(state = toastState)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SurveyTopBar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SurveyTopBar(
    panelCount: Int,
    currentIndex: Int,
    reviewMode: Boolean,
    onLater: () -> Unit,
    onClose: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Progress dots
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(panelCount) { idx ->
                Box(
                    modifier = Modifier
                        .size(if (idx == currentIndex) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (idx == currentIndex) mc.primaryAccent
                            else if (idx < currentIndex) mc.primaryAccent.copy(alpha = 0.5f)
                            else mc.surfaceVariant,
                        ),
                )
            }
            if (reviewMode) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = mc.secondaryAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.survey_reviewing_title),
                        style = ty.labelSmall,
                        color = mc.secondaryAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }

        // Later button (hidden in review mode)
        if (!reviewMode) {
            TextButton(onClick = onLater) {
                Text(
                    text = stringResource(R.string.survey_action_later),
                    color = mc.textSecondary,
                    style = ty.labelMedium,
                )
            }
        }

        // Close button
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = mc.textSecondary,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  GameRecapCard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GameRecapCard(
    recap: GameRecap,
    deck: Deck?,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = mc.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Result tag
                val resultText = if (recap.won) stringResource(R.string.survey_recap_win)
                else stringResource(R.string.survey_recap_loss)
                val resultColor = if (recap.won) mc.lifePositive else mc.lifeNegative

                Surface(
                    color = resultColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = resultText,
                        style = ty.labelMedium,
                        color = resultColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }

                // Game mode chip
                Surface(
                    color = mc.surface,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = recap.mode,
                        style = ty.labelSmall,
                        color = mc.textSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }

                Spacer(Modifier.weight(1f))

                // Turns + duration
                val durationMin = (recap.durationMs / 60_000).toInt()
                Text(
                    text = stringResource(R.string.survey_recap_turns, recap.totalTurns) +
                        " · " + stringResource(R.string.survey_duration_min, durationMin),
                    style = ty.labelSmall,
                    color = mc.textSecondary,
                )
            }

            // Opponents
            if (recap.opponentNames.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.survey_recap_vs, recap.opponentNames.joinToString(", ")),
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Commander damage note
            if (recap.commanderDamageWin) {
                Text(
                    text = stringResource(R.string.survey_recap_commander_kill),
                    style = ty.labelSmall,
                    color = mc.commanderAccent,
                )
            }

            // Associated deck name
            if (deck != null) {
                Text(
                    text = stringResource(R.string.survey_deck_associated, deck.name),
                    style = ty.labelSmall,
                    color = mc.goldMtg,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Panel: MOOD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MoodPanel(
    panel: SurveyPanel,
    answers: Map<String, String>,
    onAnswer: (String, String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = panel.title.uppercase(),
            style = ty.labelMedium,
            color = mc.primaryAccent,
            fontWeight = FontWeight.Bold,
        )

        panel.questions.forEach { question ->
            when (val opt = question.answerOption) {
                is AnswerOption.SingleChoice -> ChoiceChipsRow(
                    question = question,
                    currentValue = answers[question.id],
                    onSelect = { id -> onAnswer(question.id, id) },
                )
                is AnswerOption.StarRating -> StarRatingRow(
                    question = question,
                    current = answers[question.id]?.toIntOrNull() ?: 0,
                    maxStars = opt.maxStars,
                    onPick = { stars -> onAnswer(question.id, stars.toString()) },
                )
                else -> Unit
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Panel: FUNDAMENTALS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FundamentalsPanel(
    panel: SurveyPanel,
    answers: Map<String, String>,
    onAnswer: (String, String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = panel.title.uppercase(),
            style = ty.labelMedium,
            color = mc.primaryAccent,
            fontWeight = FontWeight.Bold,
        )

        panel.questions.forEach { question ->
            when (val opt = question.answerOption) {
                is AnswerOption.StarRating -> StarRatingRow(
                    question = question,
                    current = answers[question.id]?.toIntOrNull() ?: 0,
                    maxStars = opt.maxStars,
                    onPick = { stars -> onAnswer(question.id, stars.toString()) },
                )
                is AnswerOption.MultiChoice -> MultiChipsFlow(
                    question = question,
                    selectedSet = answers[question.id]
                        ?.split(",")
                        ?.filter { it.isNotBlank() }
                        ?.toSet()
                        ?: emptySet(),
                    onToggle = { choiceId ->
                        val current = answers[question.id]
                            ?.split(",")
                            ?.filter { it.isNotBlank() }
                            ?.toMutableSet()
                            ?: mutableSetOf()
                        if (choiceId in current) current.remove(choiceId)
                        else current.add(choiceId)
                        onAnswer(question.id, current.joinToString(","))
                    },
                )
                is AnswerOption.SingleChoice -> ChoiceChipsRow(
                    question = question,
                    currentValue = answers[question.id],
                    onSelect = { id -> onAnswer(question.id, id) },
                )
                else -> Unit
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Panel: CARD_IMPACT
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardImpactPanel(
    panel: SurveyPanel,
    uiState: SurveyUiState,
    onSetCardImpact: (String, CardImpactRating) -> Unit,
    onAddCard: (Card) -> Unit,
    onSelectDeck: (String?) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    var showDeckPicker by remember { mutableStateOf(false) }
    var showAddCardSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = panel.title.uppercase(),
            style = ty.labelMedium,
            color = mc.primaryAccent,
            fontWeight = FontWeight.Bold,
        )

        if (uiState.selectedDeckId == null) {
            // No deck selected — show deck picker prompt
            Surface(
                color = mc.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Style,
                        contentDescription = null,
                        tint = mc.textDisabled,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = stringResource(R.string.survey_no_deck_prompt),
                        style = ty.bodyMedium,
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(
                        onClick = { showDeckPicker = true },
                        border = BorderStroke(1.dp, mc.primaryAccent),
                    ) {
                        Text(
                            text = stringResource(R.string.survey_deck_picker_title),
                            color = mc.primaryAccent,
                        )
                    }
                }
            }

            // Static notice when user explicitly picks "no deck"
            Text(
                text = stringResource(R.string.survey_no_deck_notice),
                style = ty.labelSmall,
                color = mc.textDisabled,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // Deck is linked — show card impact carousel
            val question = panel.questions.firstOrNull()
            if (question != null) {
                Text(
                    text = question.text,
                    style = ty.bodyMedium,
                    color = mc.textPrimary,
                )
            }

            val allImpactCards = (uiState.suggestedImpactCards + uiState.extraImpactCards)
                .distinctBy { it.scryfallId }

            if (allImpactCards.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.survey_action_add_card),
                    subtitle = stringResource(R.string.survey_no_deck_notice),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(allImpactCards, key = { it.scryfallId }) { card ->
                        CardImpactCarouselItem(
                            card = card,
                            rating = uiState.cardImpactSelections[card.scryfallId],
                            onPick = { rating -> onSetCardImpact(card.scryfallId, rating) },
                        )
                    }
                }
            }

            // Add card from deck button
            val alreadySelected = allImpactCards.map { it.scryfallId }.toSet()
            val addableCards = uiState.deckCards.filter { it.scryfallId !in alreadySelected }

            OutlinedButton(
                onClick = { showAddCardSheet = true },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, mc.surfaceVariant),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = mc.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.survey_action_add_card),
                    color = mc.textSecondary,
                )
            }

            if (showAddCardSheet) {
                AddCardSheet(
                    deckCards = addableCards,
                    onPick = { card ->
                        onAddCard(card)
                        showAddCardSheet = false
                    },
                    onDismiss = { showAddCardSheet = false },
                )
            }
        }
    }

    if (showDeckPicker) {
        DeckPickerSheet(
            decks = uiState.availableDecks,
            currentId = uiState.selectedDeckId,
            onSelect = { deckId ->
                onSelectDeck(deckId)
                showDeckPicker = false
            },
            onDismiss = { showDeckPicker = false },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Panel: SUMMARY
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SummaryPanel(
    uiState: SurveyUiState,
    onNavigateToPanel: (Int) -> Unit,
    onNotesChange: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    // Count unanswered questions (excluding SUMMARY panel and CardImpact type)
    val skippedCount = uiState.panels
        .filter { it.id != SurveyPanelId.SUMMARY }
        .flatMap { it.questions }
        .count { q ->
            q.answerOption !is AnswerOption.CardImpact &&
                uiState.answers[q.id].isNullOrBlank()
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = uiState.panels.find { it.id == SurveyPanelId.SUMMARY }?.title?.uppercase() ?: "",
            style = ty.labelMedium,
            color = mc.primaryAccent,
            fontWeight = FontWeight.Bold,
        )

        // All answered questions grouped by panel
        uiState.panels
            .filter { it.id != SurveyPanelId.SUMMARY }
            .forEachIndexed { panelIndex, panel ->
                val answeredQuestions = panel.questions.filter { q ->
                    q.answerOption is AnswerOption.CardImpact ||
                        !uiState.answers[q.id].isNullOrBlank()
                }
                if (answeredQuestions.isEmpty()) return@forEachIndexed

                Text(
                    text = panel.title,
                    style = ty.labelSmall,
                    color = mc.textDisabled,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )

                answeredQuestions.forEach { question ->
                    SummaryAnswerRow(
                        question = question,
                        answers = uiState.answers,
                        cardImpactSelections = uiState.cardImpactSelections,
                        suggestedCards = uiState.suggestedImpactCards,
                        extraCards = uiState.extraImpactCards,
                        onClick = { onNavigateToPanel(panelIndex) },
                    )
                }
            }

        // Free notes field
        Text(
            text = stringResource(R.string.survey_q_free_text),
            style = ty.bodyMedium,
            color = mc.textPrimary,
        )

        OutlinedTextField(
            value = uiState.freeNotes,
            onValueChange = onNotesChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            placeholder = {
                Text(
                    text = stringResource(R.string.survey_free_text_hint),
                    style = ty.bodySmall,
                    color = mc.textDisabled,
                )
            },
            textStyle = ty.bodyMedium.copy(color = mc.textPrimary),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = mc.primaryAccent,
                unfocusedBorderColor = mc.surfaceVariant,
                cursorColor = mc.primaryAccent,
            ),
            maxLines = 6,
        )

        // Skipped count caption
        if (skippedCount > 0) {
            Text(
                text = stringResource(R.string.survey_skipped_count, skippedCount),
                style = ty.labelSmall,
                color = mc.textDisabled,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SummaryAnswerRow — single answered question in the SUMMARY panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SummaryAnswerRow(
    question: SurveyQuestion,
    answers: Map<String, String>,
    cardImpactSelections: Map<String, CardImpactRating>,
    suggestedCards: List<Card>,
    extraCards: List<Card>,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        onClick = onClick,
        color = mc.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = question.text,
                style = ty.bodySmall,
                color = mc.textSecondary,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            when (val opt = question.answerOption) {
                is AnswerOption.SingleChoice -> {
                    val choiceId = answers[question.id]
                    val label = opt.options.find { it.id == choiceId }?.label ?: choiceId ?: ""
                    SummaryChip(label = label)
                }
                is AnswerOption.MultiChoice -> {
                    val ids = answers[question.id]
                        ?.split(",")
                        ?.filter { it.isNotBlank() }
                        ?: emptyList()
                    val labels = ids.mapNotNull { id -> opt.options.find { it.id == id }?.label }
                    SummaryChip(label = labels.joinToString(", "))
                }
                is AnswerOption.StarRating -> {
                    val stars = answers[question.id]?.toIntOrNull() ?: 0
                    SummaryChip(label = "${"★".repeat(stars)}${opt.maxStars.let { if (stars < it) "☆".repeat(it - stars) else "" }}")
                }
                is AnswerOption.CardImpact -> {
                    val allCards = (suggestedCards + extraCards).distinctBy { it.scryfallId }
                    val rated = allCards.filter { cardImpactSelections.containsKey(it.scryfallId) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        rated.take(4).forEach { card ->
                            val rating = cardImpactSelections[card.scryfallId]
                            MiniCardThumb(card = card, rating = rating)
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun SummaryChip(label: String) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(
        color = mc.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = label,
            style = ty.labelSmall,
            color = mc.textPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MiniCardThumb(card: Card, rating: CardImpactRating?) {
    val mc = MaterialTheme.magicColors
    val badgeColor = when (rating) {
        CardImpactRating.KEY_CARD -> mc.goldMtg
        CardImpactRating.WEAK -> mc.lifeNegative
        else -> mc.textSecondary
    }
    Box(modifier = Modifier.size(width = 28.dp, height = 36.dp)) {
        AsyncImage(
            model = card.imageArtCrop,
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp)),
        )
        if (rating != null) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(badgeColor),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ChoiceChipsRow — single-choice FlowRow of FilterChips
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChipsRow(
    question: SurveyQuestion,
    currentValue: String?,
    onSelect: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = question.text,
            style = ty.bodyMedium,
            color = mc.textPrimary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val options = (question.answerOption as AnswerOption.SingleChoice).options
            options.forEach { choice ->
                val selected = choice.id == currentValue
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(choice.id) },
                    label = {
                        Text(
                            text = choice.label,
                            style = ty.labelMedium,
                        )
                    },
                    leadingIcon = if (choice.icon != null) ({
                        Icon(
                            imageVector = choice.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }) else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = mc.primaryAccent.copy(alpha = 0.2f),
                        selectedLabelColor = mc.primaryAccent,
                        selectedLeadingIconColor = mc.primaryAccent,
                        containerColor = mc.surface,
                        labelColor = mc.textSecondary,
                    ),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  MultiChipsFlow — multi-select FlowRow of FilterChips
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiChipsFlow(
    question: SurveyQuestion,
    selectedSet: Set<String>,
    onToggle: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = question.text,
            style = ty.bodyMedium,
            color = mc.textPrimary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val options = (question.answerOption as AnswerOption.MultiChoice).options
            options.forEach { choice ->
                val selected = choice.id in selectedSet
                FilterChip(
                    selected = selected,
                    onClick = { onToggle(choice.id) },
                    label = {
                        Text(
                            text = choice.label,
                            style = ty.labelMedium,
                        )
                    },
                    leadingIcon = if (choice.manaToken != null) ({
                        ManaSymbolImage(token = choice.manaToken, size = 16.dp)
                    }) else if (choice.icon != null) ({
                        Icon(
                            imageVector = choice.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }) else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = mc.secondaryAccent.copy(alpha = 0.2f),
                        selectedLabelColor = mc.secondaryAccent,
                        selectedLeadingIconColor = mc.secondaryAccent,
                        containerColor = mc.surface,
                        labelColor = mc.textSecondary,
                    ),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  StarRatingRow — 1-to-maxStars tap targets
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StarRatingRow(
    question: SurveyQuestion,
    current: Int,
    maxStars: Int,
    onPick: (Int) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = question.text,
            style = ty.bodyMedium,
            color = mc.textPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(maxStars) { idx ->
                val filled = idx < current
                Icon(
                    imageVector = if (filled) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = if (filled) mc.goldMtg else mc.textDisabled,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onPick(idx + 1) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  CardImpactCarouselItem — 130dp-wide card in the CARD_IMPACT panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CardImpactCarouselItem(
    card: Card,
    rating: CardImpactRating?,
    onPick: (CardImpactRating) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        color = mc.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .width(130.dp)
            .wrapContentHeight(),
        border = BorderStroke(
            1.dp,
            if (rating != null) mc.primaryAccent.copy(alpha = 0.4f) else mc.surfaceVariant,
        ),
    ) {
        Column {
            // Art crop image
            AsyncImage(
                model = card.imageArtCrop,
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
            )

            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CardName(
                    name = card.name,
                    showFrontOnly = true,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = mc.textPrimary,
                    style = ty.labelSmall,
                )

                if (!card.manaCost.isNullOrBlank()) {
                    ManaCostImages(
                        manaCost = card.manaCost,
                        symbolSize = 12.dp,
                        spacing = 1.dp,
                    )
                }

                // Rating chips: Key / Average / Weak
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    ImpactRatingChip(
                        label = stringResource(R.string.survey_a_card_key),
                        selected = rating == CardImpactRating.KEY_CARD,
                        color = mc.goldMtg,
                        onClick = { onPick(CardImpactRating.KEY_CARD) },
                        modifier = Modifier.weight(1f),
                    )
                    ImpactRatingChip(
                        label = stringResource(R.string.survey_a_card_average),
                        selected = rating == CardImpactRating.AVERAGE,
                        color = mc.textSecondary,
                        onClick = { onPick(CardImpactRating.AVERAGE) },
                        modifier = Modifier.weight(1f),
                    )
                    ImpactRatingChip(
                        label = stringResource(R.string.survey_a_card_weak),
                        selected = rating == CardImpactRating.WEAK,
                        color = mc.lifeNegative,
                        onClick = { onPick(CardImpactRating.WEAK) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImpactRatingChip(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        onClick = onClick,
        modifier = modifier,
        color = if (selected) color.copy(alpha = 0.2f) else mc.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(4.dp),
        border = if (selected) BorderStroke(1.dp, color) else null,
    ) {
        Text(
            text = label,
            style = ty.labelSmall.copy(fontSize = 8.sp),
            color = if (selected) color else mc.textDisabled,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp, horizontal = 2.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  DeckPickerSheet
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
                                .padding(horizontal = 16.dp, vertical = 14.dp),
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

                // "No deck" option
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
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  AddCardSheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCardSheet(
    deckCards: List<Card>,
    onPick: (Card) -> Unit,
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
                text = stringResource(R.string.survey_action_add_card),
                style = ty.titleMedium,
                color = mc.textPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            if (deckCards.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.state_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                )
            } else {
                LazyColumn {
                    items(deckCards, key = { it.scryfallId }) { card ->
                        CardListItem(
                            name = card.name,
                            imageUrl = card.imageArtCrop,
                            priceUsd = card.priceUsd,
                            priceEur = card.priceEur,
                            onClick = { onPick(card) },
                            setCode = card.setCode,
                            setName = card.setName,
                            rarity = card.rarity,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  BottomNavRow — Back / Next / Save CTA
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BottomNavRow(
    currentPanel: Int,
    totalPanels: Int,
    surveyMode: SurveyMode,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onComplete: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val isLastPanel = currentPanel == totalPanels - 1

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Back button — hidden on first panel
        if (currentPanel > 0) {
            OutlinedButton(
                onClick = onBack,
                border = BorderStroke(1.dp, mc.surfaceVariant),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.action_back),
                    color = mc.textSecondary,
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        // Next / Save button
        if (isLastPanel) {
            val ctaLabel = if (surveyMode == SurveyMode.REVIEW) {
                stringResource(R.string.survey_action_save_changes)
            } else {
                stringResource(R.string.survey_action_save_finish)
            }
            Button(
                onClick = onComplete,
                colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                modifier = Modifier.weight(2f),
            ) {
                Text(
                    text = ctaLabel,
                    color = mc.background,
                    style = ty.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                modifier = Modifier.weight(2f),
            ) {
                Text(
                    text = stringResource(R.string.action_next),
                    color = mc.background,
                    style = ty.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
