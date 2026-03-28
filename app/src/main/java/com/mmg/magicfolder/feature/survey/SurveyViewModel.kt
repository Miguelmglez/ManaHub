package com.mmg.magicfolder.feature.survey

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.data.local.dao.SurveyAnswerDao
import com.mmg.magicfolder.core.data.local.entity.SurveyAnswerEntity
import com.mmg.magicfolder.feature.game.model.GameResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UiState ───────────────────────────────────────────────────────────────────

data class SurveyUiState(
    val questions:    List<SurveyQuestion> = emptyList(),
    val currentIndex: Int                  = 0,
    val answers:      Map<String, String>  = emptyMap(),
    val isComplete:   Boolean              = false,
    val isSaving:     Boolean              = false,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class SurveyViewModel @Inject constructor(
    private val surveyAnswerDao: SurveyAnswerDao,
    savedStateHandle:            SavedStateHandle,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: 0L

    private val _uiState = MutableStateFlow(SurveyUiState())
    val uiState: StateFlow<SurveyUiState> = _uiState.asStateFlow()

    val progress: Float
        get() {
            val total = _uiState.value.questions.size
            return if (total == 0) 0f
                   else _uiState.value.currentIndex.toFloat() / total
        }

    val currentQuestion: SurveyQuestion?
        get() = _uiState.value.questions.getOrNull(_uiState.value.currentIndex)

    fun initWithResult(result: GameResult) {
        if (_uiState.value.questions.isNotEmpty()) return
        _uiState.update { it.copy(questions = SurveyQuestionEngine.buildQuestions(result, context)) }
    }

    fun answerAndAdvance(questionId: String, answer: String) {
        _uiState.update { state ->
            val newAnswers = state.answers + (questionId to answer)
            val nextIndex  = state.currentIndex + 1
            val complete   = nextIndex >= state.questions.size
            state.copy(answers = newAnswers, currentIndex = nextIndex, isComplete = complete)
        }
        if (_uiState.value.isComplete) persistAnswers()
    }

    fun skipQuestion() {
        _uiState.update { state ->
            val nextIndex = state.currentIndex + 1
            state.copy(currentIndex = nextIndex, isComplete = nextIndex >= state.questions.size)
        }
        if (_uiState.value.isComplete) persistAnswers()
    }

    fun skipAll() {
        persistAnswers()
        _uiState.update { it.copy(isComplete = true) }
    }

    private fun persistAnswers() {
        viewModelScope.launch(Dispatchers.IO) {
            val entities = _uiState.value.answers.map { (qId, answer) ->
                val question = _uiState.value.questions.find { it.id == qId }
                SurveyAnswerEntity(
                    sessionId     = sessionId,
                    questionId    = qId,
                    questionType  = question?.type ?: "UNKNOWN",
                    answer        = answer,
                    cardReference = question?.cardReference,
                )
            }
            if (entities.isNotEmpty()) {
                runCatching { surveyAnswerDao.insertAnswers(entities) }
            }
        }
    }
}
