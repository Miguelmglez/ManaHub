package com.mmg.magicfolder.feature.survey

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.data.local.dao.SurveyAnswerDao
import com.mmg.magicfolder.core.data.local.entity.SurveyAnswerEntity
import com.mmg.magicfolder.core.domain.repository.GameSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UiState ───────────────────────────────────────────────────────────────────

data class SurveyUiState(
    val questions:     List<SurveyQuestion> = emptyList(),
    val currentIndex:  Int                  = 0,
    val answers:       Map<String, String>  = emptyMap(),   // questionKey → answerJson
    val isLoading:     Boolean              = true,
    val isComplete:    Boolean              = false,
) {
    val currentQuestion: SurveyQuestion? get() = questions.getOrNull(currentIndex)
    val progress: Float get() = if (questions.isEmpty()) 0f
        else currentIndex.toFloat() / questions.size
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class SurveyViewModel @Inject constructor(
    savedStateHandle:        SavedStateHandle,
    private val sessionRepo: GameSessionRepository,
    private val answerDao:   SurveyAnswerDao,
) : ViewModel() {

    val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: 0L

    private val _state = MutableStateFlow(SurveyUiState())
    val state: StateFlow<SurveyUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val session = sessionRepo.getSessionById(sessionId)
            if (session != null) {
                val questions = SurveyQuestionEngine.buildQuestions(session)
                _state.update { it.copy(questions = questions, isLoading = false) }
            } else {
                _state.update { it.copy(isLoading = false, isComplete = true) }
            }
        }
    }

    fun answerAndAdvance(questionKey: String, answerJson: String) {
        _state.update { it.copy(answers = it.answers + (questionKey to answerJson)) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                answerDao.insertAnswer(
                    SurveyAnswerEntity(
                        sessionId   = sessionId,
                        questionKey = questionKey,
                        answerJson  = answerJson,
                    )
                )
            }
        }
        advance()
    }

    fun skipQuestion() = advance()

    fun skipAll() {
        _state.update { it.copy(isComplete = true) }
    }

    private fun advance() {
        _state.update { s ->
            val next = s.currentIndex + 1
            if (next >= s.questions.size) s.copy(isComplete = true)
            else s.copy(currentIndex = next)
        }
    }
}
