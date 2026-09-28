package com.mmg.manahub.feature.decks.domain.usecase
// COMMENTS_REVIEWED: 2026-09-16

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckSlotEntry
import com.mmg.manahub.core.model.PreferredCurrency
import kotlin.test.Test
import kotlin.test.assertEquals

private fun valueCard(
    id: String,
    usd: Double? = null,
    eur: Double? = null,
    usdFoil: Double? = null,
    eurFoil: Double? = null,
) = Card(
    scryfallId = id,
    name = id,
    printedName = null,
    manaCost = null,
    cmc = 0.0,
    colors = emptyList(),
    colorIdentity = emptyList(),
    typeLine = "Card",
    printedTypeLine = null,
    oracleText = null,
    printedText = null,
    keywords = emptyList(),
    power = null,
    toughness = null,
    loyalty = null,
    setCode = "tst",
    setName = "Test Set",
    collectorNumber = "1",
    rarity = "common",
    releasedAt = "2026-01-01",
    frameEffects = emptyList(),
    promoTypes = emptyList(),
    lang = "en",
    imageNormal = null,
    imageArtCrop = null,
    imageBackNormal = null,
    priceUsd = usd,
    priceUsdFoil = usdFoil,
    priceEur = eur,
    priceEurFoil = eurFoil,
    legalityStandard = "not_legal",
    legalityPioneer = "not_legal",
    legalityModern = "not_legal",
    legalityCommander = "not_legal",
    flavorText = null,
    artist = null,
    scryfallUri = "https://example.invalid/$id",
)

private fun entry(
    id: String,
    quantity: Int = 1,
    sideboard: Boolean = false,
    card: Card? = valueCard(id, usd = 1.0, eur = 0.5),
) = DeckSlotEntry(id, quantity, sideboard, card)

class CalculateDeckValueSummaryUseCaseTest {

    private val useCase = CalculateDeckValueSummaryUseCase()

    @Test
    fun includesCommanderInMainboardAndSeparatesSideboard() {
        val result = useCase(
            listOf(
                entry("commander", card = valueCard("commander", usd = 10.0, eur = 8.0)),
                entry("main", quantity = 2, card = valueCard("main", usd = 3.0, eur = 2.0)),
                entry("side", sideboard = true, card = valueCard("side", usd = 4.0, eur = 1.5)),
            ),
            PreferredCurrency.EUR,
        )

        assertEquals(12.0, result.mainboard.knownTotal)
        assertEquals(3, result.mainboard.knownCopies)
        assertEquals(1.5, result.sideboard.knownTotal)
        assertEquals(1, result.sideboard.knownCopies)
    }

    @Test
    fun multipliesByQuantityAndSelectsCurrencyWithoutFallback() {
        val entries = listOf(
            entry("priced", quantity = 3, card = valueCard("priced", usd = 2.0, eur = 5.0)),
            entry("eur-missing", card = valueCard("eur-missing", usd = 7.0, eur = null)),
        )

        val eur = useCase(entries, PreferredCurrency.EUR).mainboard
        assertEquals(15.0, eur.knownTotal)
        assertEquals(3, eur.knownCopies)
        assertEquals(1, eur.missingPriceCopies)

        val usd = useCase(entries, PreferredCurrency.USD).mainboard
        assertEquals(13.0, usd.knownTotal)
        assertEquals(4, usd.knownCopies)
        assertEquals(0, usd.missingPriceCopies)
    }

    @Test
    fun countsMissingCardAndMissingSelectedPriceByQuantity() {
        val result = useCase(
            listOf(
                entry("missing-card", quantity = 2, card = null),
                entry("missing-price", quantity = 3, card = valueCard("missing-price", usd = null, eur = 1.0)),
            ),
            PreferredCurrency.USD,
        ).mainboard

        assertEquals(0.0, result.knownTotal)
        assertEquals(0, result.knownCopies)
        assertEquals(5, result.missingPriceCopies)
        assertEquals(false, result.isEmpty)
    }

    @Test
    fun zeroPriceIsKnownAndFoilPriceIsIgnored() {
        val result = useCase(
            listOf(entry("free", quantity = 2, card = valueCard("free", usd = 0.0, eur = 0.0, usdFoil = 99.0, eurFoil = 88.0))),
            PreferredCurrency.USD,
        ).mainboard

        assertEquals(0.0, result.knownTotal)
        assertEquals(2, result.knownCopies)
        assertEquals(0, result.missingPriceCopies)
    }

    @Test
    fun treatsNaNNormalPriceAsUnknown() {
        assertUnknownNormalPrice(Double.NaN)
    }

    @Test
    fun treatsPositiveInfinityNormalPriceAsUnknown() {
        assertUnknownNormalPrice(Double.POSITIVE_INFINITY)
    }

    @Test
    fun treatsNegativeInfinityNormalPriceAsUnknown() {
        assertUnknownNormalPrice(Double.NEGATIVE_INFINITY)
    }

    @Test
    fun treatsNegativeNormalPriceAsUnknown() {
        assertUnknownNormalPrice(-1.0)
    }

    @Test
    fun treatsNonFiniteAndNegativePricesAsUnknownByQuantity() {
        val result = useCase(
            listOf(
                entry("nan", quantity = 2, card = valueCard("nan", usd = Double.NaN)),
                entry("positive-infinity", quantity = 3, card = valueCard("positive-infinity", usd = Double.POSITIVE_INFINITY)),
                entry("negative-infinity", quantity = 4, card = valueCard("negative-infinity", usd = Double.NEGATIVE_INFINITY)),
                entry("negative", quantity = 5, card = valueCard("negative", usd = -1.0)),
                entry("valid", quantity = 2, card = valueCard("valid", usd = 3.0)),
            ),
            PreferredCurrency.USD,
        ).mainboard

        assertEquals(6.0, result.knownTotal)
        assertEquals(2, result.knownCopies)
        assertEquals(14, result.missingPriceCopies)
    }

    @Test
    fun distinguishesEmptyBoardFromNonEmptyUnknownBoard() {
        val empty = useCase(emptyList(), PreferredCurrency.EUR).mainboard
        assertEquals(0.0, empty.knownTotal)
        assertEquals(0, empty.knownCopies)
        assertEquals(0, empty.missingPriceCopies)
        assertEquals(true, empty.isEmpty)

        val unknown = useCase(listOf(entry("unknown", quantity = 4, card = null)), PreferredCurrency.EUR).mainboard
        assertEquals(0.0, unknown.knownTotal)
        assertEquals(0, unknown.knownCopies)
        assertEquals(4, unknown.missingPriceCopies)
        assertEquals(false, unknown.isEmpty)
    }

    private fun assertUnknownNormalPrice(price: Double) {
        val result = useCase(
            listOf(entry("invalid", quantity = 3, card = valueCard("invalid", usd = price))),
            PreferredCurrency.USD,
        ).mainboard

        assertEquals(0.0, result.knownTotal)
        assertEquals(0, result.knownCopies)
        assertEquals(3, result.missingPriceCopies)
    }
}
