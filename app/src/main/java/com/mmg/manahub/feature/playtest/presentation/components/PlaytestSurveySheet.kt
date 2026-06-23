package com.mmg.manahub.feature.playtest.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.model.PlaytestSurveyAnswers
import com.mmg.manahub.feature.playtest.presentation.PlaytestSurveyEngine
import com.mmg.manahub.feature.playtest.presentation.PlaytestSurveyPanel
import com.mmg.manahub.feature.playtest.presentation.PlaytestSurveyQuestion
import com.mmg.manahub.feature.survey.presentation.AnswerOption

/**
 * Full-screen survey bottom sheet for deck playtesting.
 *
 * Paginated by panel (progress dots + AnimatedContent). All answers are collected
 * locally and only emitted when the user finishes or skips.
 *
 * @param handCards The final kept hand (for CardImpact carousel).
 * @param onFinish Called with the collected answers when the user finishes.
 * @param onDismiss Called when the user skips the entire survey.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaytestSurveySheet(
    handCards: List<Card>,
    onFinish: (answers: PlaytestSurveyAnswers, types: Map<String, String>, cardRefs: Map<String, String?>) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    // Build panels with context available in composable context.
    // We build them in a remember block to avoid rebuilding on recomposition.
    val builtPanels = rememberBuiltPanels(handCards)

    val answers = remember { mutableStateMapOf<String, String>() }
    var currentPanelIndex by remember { mutableStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = mc.backgroundSecondary,
        modifier         = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            // Header row.
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text     = stringResource(R.string.playtest_survey_title),
                    style    = ty.titleMedium,
                    color    = mc.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint               = mc.textDisabled,
                    )
                }
            }

            // Progress dots.
            if (builtPanels.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    builtPanels.forEachIndexed { index, _ ->
                        Box(
                            modifier = Modifier
                                .size(if (index == currentPanelIndex) 10.dp else 7.dp)
                                .background(
                                    color  = if (index == currentPanelIndex) mc.primaryAccent else mc.textDisabled,
                                    shape  = CircleShape,
                                ),
                        )
                    }
                }
            }

            // Paginated panel content.
            AnimatedContent(
                targetState = currentPanelIndex,
                transitionSpec = {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                },
                modifier = Modifier.weight(1f),
                label    = "SurveyPanelTransition",
            ) { panelIndex ->
                val panel = builtPanels.getOrNull(panelIndex) ?: return@AnimatedContent

                LazyColumn(
                    modifier        = Modifier.fillMaxSize(),
                    contentPadding  = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Text(
                            text  = panel.title,
                            style = ty.titleMedium,
                            color = mc.textPrimary,
                        )
                    }
                    items(panel.questions) { question ->
                        SurveyQuestionWidget(
                            question   = question,
                            handCards  = handCards,
                            answers    = answers,
                            onAnswer   = { qId, value -> answers[qId] = value },
                        )
                    }
                }
            }

            // Navigation buttons.
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (currentPanelIndex > 0) {
                    OutlinedButton(
                        onClick = { currentPanelIndex-- },
                        shape   = RoundedCornerShape(10.dp),
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = mc.primaryAccent),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.playtest_survey_back), style = ty.labelLarge)
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                val isLast = currentPanelIndex == builtPanels.lastIndex
                Button(
                    onClick  = {
                        if (isLast) {
                            val types = builtPanels
                                .flatMap { it.questions }
                                .associate { it.id to it.type }
                            val cardRefs = builtPanels
                                .flatMap { it.questions }
                                .associate { it.id to it.cardReference }
                            onFinish(answers.toMap(), types, cardRefs)
                        } else {
                            currentPanelIndex++
                        }
                    },
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text  = if (isLast) stringResource(R.string.playtest_survey_finish)
                                else stringResource(R.string.playtest_survey_next),
                        style = ty.labelLarge,
                        color = mc.background,
                    )
                }
            }
        }
    }
}

/** Builds survey panels inside a composable context (needs LocalContext). */
@Composable
private fun rememberBuiltPanels(handCards: List<Card>): List<PlaytestSurveyPanel> {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(handCards) { PlaytestSurveyEngine.buildPanels(context, handCards) }
}

// ── Per-question widget ───────────────────────────────────────────────────────

@Composable
private fun SurveyQuestionWidget(
    question: PlaytestSurveyQuestion,
    handCards: List<Card>,
    answers: Map<String, String>,
    onAnswer: (questionId: String, value: String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text  = question.text,
            style = ty.bodyMedium,
            color = mc.textPrimary,
        )

        when (val option = question.answerOption) {
            is AnswerOption.SingleChoice -> {
                val selected = answers[question.id]
                option.options.forEach { choice ->
                    FilterChip(
                        selected = choice.id == selected,
                        onClick  = { onAnswer(question.id, choice.id) },
                        label    = {
                            Text(choice.label, style = ty.labelSmall)
                        },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = mc.primaryAccent.copy(alpha = 0.2f),
                            selectedLabelColor     = mc.primaryAccent,
                            containerColor         = mc.surface,
                            labelColor             = mc.textSecondary,
                        ),
                    )
                }
            }

            is AnswerOption.MultiChoice -> {
                val selectedIds = answers[question.id]
                    ?.split(",")
                    ?.filter { it.isNotBlank() }
                    ?.toSet()
                    ?: emptySet()

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    option.options.forEach { choice ->
                        FilterChip(
                            selected = choice.id in selectedIds,
                            onClick  = {
                                val updated = selectedIds.toMutableSet()
                                if (choice.id in updated) updated.remove(choice.id) else updated.add(choice.id)
                                onAnswer(question.id, updated.joinToString(","))
                            },
                            label = { Text(choice.label, style = ty.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = mc.primaryAccent.copy(alpha = 0.2f),
                                selectedLabelColor     = mc.primaryAccent,
                                containerColor         = mc.surface,
                                labelColor             = mc.textSecondary,
                            ),
                        )
                    }
                }
            }

            is AnswerOption.StarRating -> {
                val rating = answers[question.id]?.toIntOrNull() ?: 0
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (star in 1..option.maxStars) {
                        Icon(
                            imageVector        = if (star <= rating) Icons.Default.Star else Icons.Outlined.StarOutline,
                            contentDescription = null,
                            tint               = if (star <= rating) mc.primaryAccent else mc.textDisabled,
                            modifier           = Modifier
                                .size(32.dp)
                                .clickable { onAnswer(question.id, star.toString()) },
                        )
                    }
                }
            }

            AnswerOption.FreeText -> {
                var textState by remember { mutableStateOf(TextFieldValue(answers[question.id] ?: "")) }
                Surface(
                    color  = mc.surface,
                    shape  = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                ) {
                    BasicTextField(
                        value         = textState,
                        onValueChange = { textState = it; onAnswer(question.id, it.text) },
                        textStyle     = ty.bodyMedium.copy(color = mc.textPrimary),
                        modifier      = Modifier.padding(12.dp),
                    )
                }
            }

            AnswerOption.CardImpact -> {
                // Carousel of hand cards with KEY / AVERAGE / WEAK rating chips.
                // Key by index so that duplicate copies of the same card (same scryfallId)
                // do not produce an IllegalArgumentException from Compose.
                // The answer map key also includes the index to keep ratings independent
                // per copy; scryfallId is preserved in the question's cardReference field.
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(handCards, key = { index, _ -> index }) { index, card ->
                        val cardQuestionId = "${question.id}:${card.scryfallId}:$index"
                        val cardAnswer = answers[cardQuestionId]
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier            = Modifier.width(80.dp),
                        ) {
                            AsyncImage(
                                model             = card.imageNormal,
                                contentDescription = card.name,
                                contentScale      = ContentScale.Crop,
                                modifier          = Modifier
                                    .width(80.dp)
                                    .aspectRatio(63f / 88f)
                                    .clip(RoundedCornerShape(6.dp)),
                            )
                            listOf("KEY", "AVG", "WEAK").forEach { impact ->
                                FilterChip(
                                    selected = cardAnswer == impact,
                                    onClick  = { onAnswer(cardQuestionId, impact) },
                                    label    = { Text(impact, style = ty.labelSmall) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors   = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = mc.primaryAccent.copy(alpha = 0.2f),
                                        selectedLabelColor     = mc.primaryAccent,
                                        containerColor         = mc.surface,
                                        labelColor             = mc.textSecondary,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
