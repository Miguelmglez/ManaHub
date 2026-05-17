package com.mmg.manahub.feature.survey

import android.content.Context
import com.mmg.manahub.feature.survey.presentation.AnswerOption
import com.mmg.manahub.feature.survey.presentation.SurveyPanelId
import com.mmg.manahub.feature.survey.presentation.SurveyQuestionEngine
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SurveyQuestionEngineTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.getString(any()) } returns "stub"
        every { context.getString(any(), *anyVararg()) } returns "stub"
        every { context.resources } returns mockk(relaxed = true)
        every { context.createConfigurationContext(any()) } returns context
    }

    @Test
    fun `buildPanels with hasDeck=true returns 4 panels in expected order`() {
        val panels = SurveyQuestionEngine.buildPanels(won = true, context = context, hasDeck = true)
        assertEquals(4, panels.size)
        assertEquals(SurveyPanelId.MOOD, panels[0].id)
        assertEquals(SurveyPanelId.FUNDAMENTALS, panels[1].id)
        assertEquals(SurveyPanelId.CARD_IMPACT, panels[2].id)
        assertEquals(SurveyPanelId.SUMMARY, panels[3].id)
    }

    @Test
    fun `buildPanels with hasDeck=false omits CARD_IMPACT`() {
        val panels = SurveyQuestionEngine.buildPanels(won = true, context = context, hasDeck = false)
        assertEquals(3, panels.size)
        assertTrue(panels.none { it.id == SurveyPanelId.CARD_IMPACT })
        assertEquals(SurveyPanelId.SUMMARY, panels.last().id)
    }

    @Test
    fun `SUMMARY panel always has empty questions list`() {
        val withDeck = SurveyQuestionEngine.buildPanels(won = true, context = context, hasDeck = true)
        val noDeck = SurveyQuestionEngine.buildPanels(won = false, context = context, hasDeck = false)
        assertTrue(withDeck.first { it.id == SurveyPanelId.SUMMARY }.questions.isEmpty())
        assertTrue(noDeck.first { it.id == SurveyPanelId.SUMMARY }.questions.isEmpty())
    }

    @Test
    fun `MOOD panel contains decisive_moment and matchup_difficulty`() {
        val panels = SurveyQuestionEngine.buildPanels(won = true, context = context, hasDeck = true)
        val mood = panels.first { it.id == SurveyPanelId.MOOD }
        val ids = mood.questions.map { it.id }
        assertTrue("decisive_moment must be present", "decisive_moment" in ids)
        assertTrue("matchup_difficulty must be present", "matchup_difficulty" in ids)
    }

    @Test
    fun `FUNDAMENTALS panel contains hand_quality, mana_health and result_context`() {
        val panels = SurveyQuestionEngine.buildPanels(won = true, context = context, hasDeck = true)
        val fundamentals = panels.first { it.id == SurveyPanelId.FUNDAMENTALS }
        val ids = fundamentals.questions.map { it.id }
        assertTrue("hand_quality must be present", "hand_quality" in ids)
        assertTrue("mana_health must be present", "mana_health" in ids)
        assertTrue("result_context must be present", "result_context" in ids)
    }

    @Test
    fun `result_context options differ between win and loss`() {
        val winPanels = SurveyQuestionEngine.buildPanels(won = true, context = context, hasDeck = true)
        val lossPanels = SurveyQuestionEngine.buildPanels(won = false, context = context, hasDeck = true)
        val winQuestion = winPanels.first { it.id == SurveyPanelId.FUNDAMENTALS }
            .questions.first { it.id == "result_context" }
        val lossQuestion = lossPanels.first { it.id == SurveyPanelId.FUNDAMENTALS }
            .questions.first { it.id == "result_context" }
        val winIds = (winQuestion.answerOption as AnswerOption.SingleChoice).options.map { it.id }.toSet()
        val lossIds = (lossQuestion.answerOption as AnswerOption.SingleChoice).options.map { it.id }.toSet()
        assertNotEquals(winIds, lossIds)
        assertTrue("Win should include DOMINANT", "DOMINANT" in winIds)
        assertTrue("Loss should include OVERWHELMED", "OVERWHELMED" in lossIds)
    }

    @Test
    fun `CARD_IMPACT panel has exactly one question of type CardImpact`() {
        val panels = SurveyQuestionEngine.buildPanels(won = true, context = context, hasDeck = true)
        val impact = panels.first { it.id == SurveyPanelId.CARD_IMPACT }
        assertEquals(1, impact.questions.size)
        assertTrue(impact.questions.first().answerOption is AnswerOption.CardImpact)
    }

    @Test
    fun `decisive_moment is SingleChoice with 4 options`() {
        val panels = SurveyQuestionEngine.buildPanels(won = true, context = context, hasDeck = false)
        val decisive = panels.first { it.id == SurveyPanelId.MOOD }
            .questions.first { it.id == "decisive_moment" }
        val opt = decisive.answerOption as AnswerOption.SingleChoice
        assertEquals(4, opt.options.size)
        val ids = opt.options.map { it.id }.toSet()
        assertEquals(setOf("KEY_TURN", "TOP_DECK", "RIVAL_ERROR", "UNCLEAR"), ids)
    }

    @Test
    fun `mana_health is MultiChoice with 5 options`() {
        val panels = SurveyQuestionEngine.buildPanels(won = true, context = context, hasDeck = false)
        val mana = panels.first { it.id == SurveyPanelId.FUNDAMENTALS }
            .questions.first { it.id == "mana_health" }
        val opt = mana.answerOption as AnswerOption.MultiChoice
        assertEquals(5, opt.options.size)
    }

    @Test
    fun `hand_quality and matchup_difficulty are StarRating`() {
        val panels = SurveyQuestionEngine.buildPanels(won = true, context = context, hasDeck = false)
        val hand = panels.first { it.id == SurveyPanelId.FUNDAMENTALS }
            .questions.first { it.id == "hand_quality" }
        val matchup = panels.first { it.id == SurveyPanelId.MOOD }
            .questions.first { it.id == "matchup_difficulty" }
        assertTrue(hand.answerOption is AnswerOption.StarRating)
        assertTrue(matchup.answerOption is AnswerOption.StarRating)
    }
}
