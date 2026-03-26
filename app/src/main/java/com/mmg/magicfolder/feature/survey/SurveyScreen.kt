package com.mmg.magicfolder.feature.survey

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography

// ─────────────────────────────────────────────────────────────────────────────
//  Entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SurveyScreen(
    onBack:    () -> Unit,
    onFinish:  () -> Unit,
    viewModel: SurveyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) onFinish()
    }

    Scaffold(
        topBar = {
            SurveyTopBar(
                currentIndex   = uiState.currentIndex,
                totalQuestions = uiState.questions.size,
                progress       = uiState.progress,
                onClose        = onBack,
                onSkipAll      = viewModel::skipAll,
            )
        },
        containerColor = mc.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color    = mc.goldMtg,
                    )
                }
                uiState.currentQuestion == null -> Unit  // handled by LaunchedEffect
                else -> {
                    AnimatedContent(
                        targetState  = uiState.currentIndex,
                        transitionSpec = {
                            (slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)))
                                .togetherWith(slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(300)))
                        },
                        label = "survey_question",
                    ) { index ->
                        val question = uiState.questions.getOrNull(index) ?: return@AnimatedContent
                        QuestionCard(
                            question   = question,
                            onAnswer   = { key, json -> viewModel.answerAndAdvance(key, json) },
                            onSkip     = viewModel::skipQuestion,
                            modifier   = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Top bar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SurveyTopBar(
    currentIndex:   Int,
    totalQuestions: Int,
    progress:       Float,
    onClose:        () -> Unit,
    onSkipAll:      () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Column {
        TopAppBar(
            title  = {
                Text(
                    text  = "Game Review",
                    style = MaterialTheme.magicTypography.titleMedium,
                    color = mc.textPrimary,
                )
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = mc.textSecondary)
                }
            },
            actions = {
                TextButton(onClick = onSkipAll) {
                    Text(
                        text  = "Skip all",
                        style = MaterialTheme.magicTypography.labelMedium,
                        color = mc.textSecondary,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.background),
        )
        if (totalQuestions > 0) {
            LinearProgressIndicator(
                progress        = { progress },
                modifier        = Modifier.fillMaxWidth(),
                color           = mc.goldMtg,
                trackColor      = mc.surface,
            )
            Text(
                text      = "${currentIndex + 1} / $totalQuestions",
                style     = MaterialTheme.magicTypography.labelSmall,
                color     = mc.textSecondary,
                textAlign = TextAlign.End,
                modifier  = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Question dispatcher
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuestionCard(
    question: SurveyQuestion,
    onAnswer: (key: String, json: String) -> Unit,
    onSkip:   () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier            = modifier,
        contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text  = question.prompt,
                style = MaterialTheme.magicTypography.displaySmall,
                color = MaterialTheme.magicColors.textPrimary,
            )
        }
        item {
            when (question) {
                is SurveyQuestion.SingleChoice -> SingleChoiceGrid(
                    question = question,
                    onAnswer = onAnswer,
                )
                is SurveyQuestion.MultiChoice  -> MultiChoiceList(
                    question = question,
                    onAnswer = onAnswer,
                )
                is SurveyQuestion.StarRating   -> StarRatingInput(
                    question = question,
                    onAnswer = onAnswer,
                )
                is SurveyQuestion.FreeText     -> FreeTextInput(
                    question = question,
                    onAnswer = onAnswer,
                )
            }
        }
        item {
            TextButton(
                onClick  = onSkip,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text  = "Skip this question",
                    style = MaterialTheme.magicTypography.labelMedium,
                    color = MaterialTheme.magicColors.textSecondary,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Single-choice 2-column grid
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SingleChoiceGrid(
    question: SurveyQuestion.SingleChoice,
    onAnswer: (key: String, json: String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    LazyVerticalGrid(
        columns             = GridCells.Fixed(2),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier            = Modifier.heightIn(max = 400.dp),
    ) {
        items(question.options) { option ->
            Surface(
                shape    = RoundedCornerShape(12.dp),
                color    = mc.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAnswer(question.key, option.id) },
            ) {
                Text(
                    text      = option.label,
                    style     = MaterialTheme.magicTypography.bodyLarge,
                    color     = mc.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Multi-choice list with checkboxes
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MultiChoiceList(
    question: SurveyQuestion.MultiChoice,
    onAnswer: (key: String, json: String) -> Unit,
) {
    val mc       = MaterialTheme.magicColors
    val selected = remember { mutableStateListOf<String>() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        question.options.forEach { option ->
            val isChecked = option.id in selected
            Surface(
                shape    = RoundedCornerShape(10.dp),
                color    = if (isChecked) mc.goldMtg.copy(alpha = 0.15f) else mc.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isChecked) 1.dp else 0.dp,
                        color = if (isChecked) mc.goldMtg else Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable {
                        if (isChecked) selected.remove(option.id) else selected.add(option.id)
                    },
            ) {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Checkbox(
                        checked         = isChecked,
                        onCheckedChange = null,
                        colors          = CheckboxDefaults.colors(
                            checkedColor   = mc.goldMtg,
                            uncheckedColor = mc.textSecondary,
                        ),
                    )
                    Text(
                        text  = option.label,
                        style = MaterialTheme.magicTypography.bodyLarge,
                        color = mc.textPrimary,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick  = {
                val json = "[${selected.joinToString(",") { "\"$it\"" }}]"
                onAnswer(question.key, json)
            },
            enabled  = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(containerColor = mc.goldMtg),
        ) {
            Text(
                text  = "Confirm",
                style = MaterialTheme.magicTypography.labelLarge,
                color = mc.background,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Star rating
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StarRatingInput(
    question: SurveyQuestion.StarRating,
    onAnswer: (key: String, json: String) -> Unit,
) {
    val mc      = MaterialTheme.magicColors
    var rating  by remember { mutableIntStateOf(0) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier              = Modifier.padding(vertical = 8.dp),
        ) {
            for (i in 1..question.maxStars) {
                val filled = i <= rating
                Icon(
                    imageVector         = if (filled) Icons.Default.Star else Icons.Outlined.StarOutline,
                    contentDescription  = "Star $i",
                    tint                = if (filled) mc.goldMtg else mc.textSecondary,
                    modifier            = Modifier
                        .size(44.dp)
                        .clickable { rating = i },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick  = { onAnswer(question.key, rating.toString()) },
            enabled  = rating > 0,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(containerColor = mc.goldMtg),
        ) {
            Text(
                text  = "Confirm",
                style = MaterialTheme.magicTypography.labelLarge,
                color = mc.background,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Free text
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FreeTextInput(
    question: SurveyQuestion.FreeText,
    onAnswer: (key: String, json: String) -> Unit,
) {
    val mc   = MaterialTheme.magicColors
    var text by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value         = text,
            onValueChange = { text = it },
            placeholder   = {
                Text(
                    text  = question.hint,
                    style = MaterialTheme.magicTypography.bodyMedium,
                    color = mc.textSecondary,
                )
            },
            modifier      = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            maxLines      = 6,
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = mc.goldMtg,
                unfocusedBorderColor = mc.textSecondary,
                focusedTextColor     = mc.textPrimary,
                unfocusedTextColor   = mc.textPrimary,
                cursorColor          = mc.goldMtg,
            ),
        )
        Button(
            onClick  = { onAnswer(question.key, "\"${text.replace("\"", "\\\"")}\"") },
            enabled  = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(containerColor = mc.goldMtg),
        ) {
            Text(
                text  = "Save note",
                style = MaterialTheme.magicTypography.labelLarge,
                color = mc.background,
            )
        }
    }
}
