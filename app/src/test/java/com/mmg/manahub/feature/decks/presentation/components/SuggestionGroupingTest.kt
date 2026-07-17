package com.mmg.manahub.feature.decks.presentation.components

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.fixedPower
import com.mmg.manahub.feature.decks.domain.usecase.AddOrigin
import com.mmg.manahub.feature.decks.domain.usecase.AddSuggestion
import com.mmg.manahub.feature.decks.domain.usecase.CommunityAddSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deck Builder v2 (plan §3.8) -- [SuggestionGrouping] presentation-side category buckets, shared
 * by the Deck Studio Suggestions tab (Motor A/B) and the wizard Result screen (owned entries).
 */
class SuggestionGroupingTest {

    private val scorer = DeckScorer(RoleClassifier(), fixedPower(normalized = 0.5f))

    private fun profileFor(cards: List<Card>) = scorer.profile(
        mainboard = cards.map { DeckEntry(card = it, quantity = 1, isOwned = true) },
        format = DeckFormat.CASUAL,
        colorIdentity = emptySet(),
        seedTags = emptyList(),
    )

    @Test
    fun `groupDeckEntries buckets by resolved category, largest group first`() {
        val tokensEntries = (1..3).map {
            DeckEntry(card = card(id = "t$it", name = "Tokens $it", tags = listOf(CardTag.TOKENS)), quantity = 1, isOwned = true)
        }
        val lifegainEntries = listOf(
            DeckEntry(card = card(id = "l1", name = "Lifegain 1", tags = listOf(CardTag.LIFEGAIN)), quantity = 1, isOwned = true),
        )

        val grouped = SuggestionGrouping.groupDeckEntries(tokensEntries + lifegainEntries)

        assertEquals(2, grouped.size)
        assertEquals("tokens", grouped.first().first.id)
        assertEquals(3, grouped.first().second.size)
        assertEquals("lifegain", grouped.last().first.id)
        assertEquals(1, grouped.last().second.size)
    }

    @Test
    fun `groupAddSuggestions buckets Motor A suggestions by the same resolver`() {
        val tokenCard = card(id = "tok-1", name = "Tok", tags = listOf(CardTag.TOKENS))
        val profile = profileFor(listOf(tokenCard))
        val fit = scorer.fit(tokenCard, profile, isOwned = true)
        val suggestion = AddSuggestion(fit = fit, origin = AddOrigin.COLLECTION)

        val grouped = SuggestionGrouping.groupAddSuggestions(listOf(suggestion))

        assertEquals(1, grouped.size)
        assertEquals("tokens", grouped.first().first.id)
        assertEquals(listOf(suggestion), grouped.first().second)
    }

    @Test
    fun `groupCommunityAddSuggestions buckets Motor B suggestions by the same resolver`() {
        val tokenCard = card(id = "tok-2", name = "Tok2", tags = listOf(CardTag.TOKENS))
        val suggestion = CommunityAddSuggestion(
            card = tokenCard,
            inclusionPct = 0.5f,
            synergy = 0.5f,
            ownedInCollection = false,
            fillsGapRoles = emptySet(),
        )

        val grouped = SuggestionGrouping.groupCommunityAddSuggestions(listOf(suggestion))

        assertEquals(1, grouped.size)
        assertEquals("tokens", grouped.first().first.id)
    }

    @Test
    fun `an empty input groups to an empty list, never a crash`() {
        assertTrue(SuggestionGrouping.groupDeckEntries(emptyList()).isEmpty())
        assertTrue(SuggestionGrouping.groupAddSuggestions(emptyList()).isEmpty())
        assertTrue(SuggestionGrouping.groupCommunityAddSuggestions(emptyList()).isEmpty())
    }
}
