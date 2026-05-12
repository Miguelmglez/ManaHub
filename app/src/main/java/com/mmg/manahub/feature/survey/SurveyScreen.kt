package com.mmg.manahub.feature.survey

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.game.model.GameResult
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
//  Entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SurveyScreen(
    sessionId:  Long,
    gameResult: GameResult,
    onComplete: () -> Unit,
    viewModel:  SurveyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    LaunchedEffect(Unit) {
        viewModel.initWithResult(gameResult)
    }

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) onComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(mc.background),
    ) {
        // ── Top bar (Compact) ────────────────────────────────────────────────
        Surface(
            color = mc.background,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text  = stringResource(R.string.survey_title),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                )
                IconButton(onClick = { viewModel.skipAll() }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.survey_skip_all_description),
                        tint               = mc.textSecondary,
                    )
                }
            }
        }

        // ── Progress bar ──────────────────────────────────────────────────────
        val animatedProgress by animateFloatAsState(
            targetValue   = viewModel.progress,
            animationSpec = tween(400),
            label         = "survey_progress",
        )
        LinearProgressIndicator(
            progress   = { animatedProgress },
            modifier   = Modifier.fillMaxWidth().height(3.dp),
            color      = mc.primaryAccent,
            trackColor = mc.surfaceVariant,
        )
        if (uiState.questions.isNotEmpty()) {
            Text(
                text      = "${uiState.currentIndex + 1} / ${uiState.questions.size}",
                modifier  = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                textAlign = TextAlign.End,
                style     = ty.labelLarge,
                color     = mc.textDisabled,
            )
        }

        // ── Current question with slide animation ─────────────────────────────
        val question = viewModel.currentQuestion
        if (question != null) {
            AnimatedContent(
                targetState  = question,
                transitionSpec = {
                    (slideInHorizontally(tween(300)) { it } + fadeIn(tween(200)))
                        .togetherWith(slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(200)))
                },
                label = "survey_question",
            ) { q ->
                QuestionContent(
                    question = q,
                    onAnswer = { answer -> viewModel.answerAndAdvance(q.id, answer) },
                    onSkip   = { viewModel.skipQuestion() },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Question content dispatcher
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuestionContent(
    question: SurveyQuestion,
    onAnswer: (String) -> Unit,
    onSkip:   () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // Context badge
        question.contextBadge?.let { badge ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(mc.goldMtg.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    text  = badge,
                    style = ty.labelLarge,
                    color = mc.goldMtg,
                )
            }
        }

        Text(
            text  = question.text,
            style = ty.titleMedium,
            color = mc.textPrimary,
        )

        when (val opt = question.answerOption) {
            is AnswerOption.SingleChoice -> SingleChoiceGrid(opt.options, onAnswer)
            is AnswerOption.MultiChoice  -> MultiChoiceList(
                choices   = opt.options,
                onConfirm = { ids -> onAnswer(ids.joinToString(",")) },
                onSkip    = onSkip,
            )
            is AnswerOption.StarRating   -> StarRatingInput(opt.maxStars, onAnswer, onSkip)
            is AnswerOption.FreeText     -> FreeTextInput(onAnswer, onSkip)
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Single-choice 2-column grid
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SingleChoiceGrid(
    choices:  List<SurveyChoice>,
    onSelect: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    LazyVerticalGrid(
        columns               = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement   = Arrangement.spacedBy(10.dp),
        modifier              = Modifier.heightIn(max = 420.dp),
    ) {
        items(choices) { choice ->
            Surface(
                onClick = { onSelect(choice.id) },
                shape   = RoundedCornerShape(16.dp),
                color   = mc.surface,
                border  = BorderStroke(1.dp, mc.surfaceVariant),
                modifier = Modifier.fillMaxWidth().aspectRatio(1.2f),
            ) {
                Column(
                    modifier            = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (choice.emoji.isNotEmpty()) {
                        Text(text = choice.emoji, fontSize = 32.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        text      = choice.label,
                        style     = ty.bodyMedium,
                        color     = mc.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Multi-choice list with checkboxes
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MultiChoiceList(
    choices:   List<SurveyChoice>,
    onConfirm: (List<String>) -> Unit,
    onSkip:    () -> Unit,
) {
    val mc       = MaterialTheme.magicColors
    val ty       = MaterialTheme.magicTypography
    var selected by remember { mutableStateOf(setOf<String>()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { choice ->
            val isSelected = choice.id in selected
            Surface(
                onClick = {
                    selected = if (isSelected) selected - choice.id else selected + choice.id
                },
                shape  = RoundedCornerShape(12.dp),
                color  = if (isSelected) mc.primaryAccent.copy(alpha = 0.12f) else mc.surface,
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) mc.primaryAccent.copy(alpha = 0.6f) else mc.surfaceVariant,
                ),
            ) {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (choice.emoji.isNotEmpty()) Text(text = choice.emoji, fontSize = 20.sp)
                    Text(
                        text     = choice.label,
                        style    = ty.bodyMedium,
                        color    = mc.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint               = mc.primaryAccent,
                            modifier           = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick  = onSkip,
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(12.dp),
            ) { Text(stringResource(R.string.action_skip)) }
            Button(
                onClick  = { onConfirm(selected.toList()) },
                modifier = Modifier.weight(2f),
                shape    = RoundedCornerShape(12.dp),
                enabled  = selected.isNotEmpty(),
                colors   = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
            ) { Text(stringResource(R.string.survey_confirm_button)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Star rating
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StarRatingInput(
    maxStars:  Int,
    onConfirm: (String) -> Unit,
    onSkip:    () -> Unit,
) {
    val mc     = MaterialTheme.magicColors
    val ty     = MaterialTheme.magicTypography
    var rating by remember { mutableIntStateOf(0) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(maxStars) { index ->
                Text(
                    text     = if (index < rating) "\u2605" else "\u2606",
                    fontSize = 40.sp,
                    color    = if (index < rating) mc.goldMtg else mc.textDisabled,
                    modifier = Modifier.clickable { rating = index + 1 },
                )
            }
        }

        if (rating > 0) {
            Text(
                text  = when (rating) {
                    1 -> "Very poor hand"
                    2 -> "Weak hand"
                    3 -> "Playable hand"
                    4 -> "Good hand"
                    else -> "Perfect hand"
                },
                style = ty.bodySmall,
                color = mc.textSecondary,
            )
        }

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick  = onSkip,
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(12.dp),
            ) { Text(stringResource(R.string.action_skip)) }
            Button(
                onClick  = { onConfirm("$rating") },
                modifier = Modifier.weight(2f),
                shape    = RoundedCornerShape(12.dp),
                enabled  = rating > 0,
                colors   = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
            ) { Text(stringResource(R.string.survey_next_button)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Free text
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FreeTextInput(
    onConfirm: (String) -> Unit,
    onSkip:    () -> Unit,
) {
    val mc            = MaterialTheme.magicColors
    val ty            = MaterialTheme.magicTypography
    var text          by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(300)
        runCatching { focusRequester.requestFocus() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value         = text,
            onValueChange = { text = it },
            modifier      = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .focusRequester(focusRequester),
            placeholder   = {
                Text(
                    "Optional notes about this game...",
                    color = mc.textDisabled,
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirm(text) }),
            shape  = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = mc.primaryAccent,
                unfocusedBorderColor = mc.surfaceVariant,
                cursorColor          = mc.primaryAccent,
                focusedTextColor     = mc.textPrimary,
                unfocusedTextColor   = mc.textPrimary,
            ),
        )

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick  = onSkip,
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(12.dp),
            ) { Text(stringResource(R.string.action_skip)) }
            Button(
                onClick  = { onConfirm(text) },
                modifier = Modifier.weight(2f),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
            ) { Text(if (text.isBlank()) stringResource(R.string.action_skip) else stringResource(R.string.survey_save_button)) }
        }
    }
}
