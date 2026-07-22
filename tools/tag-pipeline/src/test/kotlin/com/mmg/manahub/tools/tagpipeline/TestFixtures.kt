package com.mmg.manahub.tools.tagpipeline

import com.mmg.manahub.core.model.Card

/**
 * Minimal [Card] fixture builder — mirrors the `card(...)` helper in
 * `shared/core-domain/src/commonTest/.../engine/EngineFixtures.kt` (same defaults philosophy:
 * override only the fields a given test cares about). Not shared cross-module (this module isn't
 * KMP and can't consume another module's `commonTest` source set), so it's a small, deliberate
 * duplicate rather than a build-graph contortion for one helper function.
 */
fun testCard(
    oracleId: String = "oracle-0001",
    scryfallId: String = "scry-0001",
    name: String = "Test Card",
    typeLine: String = "Instant",
    oracleText: String? = null,
    keywords: List<String> = emptyList(),
    colors: List<String> = listOf("U"),
    colorIdentity: List<String> = listOf("U"),
    gameChanger: Boolean = false,
): Card = Card(
    scryfallId = scryfallId,
    name = name,
    printedName = null,
    manaCost = null,
    cmc = 2.0,
    colors = colors,
    colorIdentity = colorIdentity,
    typeLine = typeLine,
    printedTypeLine = null,
    oracleText = oracleText,
    printedText = null,
    keywords = keywords,
    power = null,
    toughness = null,
    loyalty = null,
    setCode = "TST",
    setName = "Test Set",
    collectorNumber = "1",
    rarity = "rare",
    releasedAt = "2024-01-01",
    frameEffects = emptyList(),
    promoTypes = emptyList(),
    lang = "en",
    imageNormal = null,
    imageArtCrop = null,
    imageBackNormal = null,
    priceUsd = null,
    priceUsdFoil = null,
    priceEur = null,
    priceEurFoil = null,
    legalityStandard = "legal",
    legalityPioneer = "legal",
    legalityModern = "legal",
    legalityCommander = "legal",
    flavorText = null,
    artist = null,
    scryfallUri = "https://scryfall.com/card/tst/1",
    gameChanger = gameChanger,
    oracleId = oracleId,
)
