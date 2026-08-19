package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.SuggestedTag

// ─────────────────────────────────────────────────────────────────────────────
//  Shared commonTest fixtures for the deck-scoring engine multiplatform tests
//  (KMP migration remediation P1.4). Mirrors the JVM-only
//  `app/src/test/.../decks/domain/engine/EngineFixtures.kt` used by the
//  existing MockK-based `:app` suite — kept intentionally minimal here
//  (no PowerResolver/minimalProfile helpers) since the commonTest suite only
//  exercises the pure ManaBaseAnalyzer/TribeDeriver units so far. All helpers
//  are plain functions with no platform (Android/browser) dependency, so this
//  file compiles and runs on every KMP target (JVM host + wasmJs).
// ─────────────────────────────────────────────────────────────────────────────

/** Minimal Card with sensible defaults; override only the fields under test. */
fun card(
    id: String = "scry-0001",
    name: String = "Test Card",
    typeLine: String = "Instant",
    cmc: Double = 2.0,
    colors: List<String> = listOf("U"),
    colorIdentity: List<String> = listOf("U"),
    oracleText: String? = null,
    power: String? = null,
    toughness: String? = null,
    tags: List<CardTag> = emptyList(),
    userTags: List<CardTag> = emptyList(),
    suggestedTags: List<SuggestedTag> = emptyList(),
    manaCost: String? = null,
    rarity: String = "rare",
    /** D14 -- WUBRG-subset production string (e.g. "WU" for a dual land). Deck Wizard & Engine
     * Rework plan WS9.4 -- lets fixture lands exercise [ArchetypeRoleClassifier]'s
     * `manaFixMatcher`/[BuildDeckFromTemplateUseCase]'s fixing-land filter without needing every
     * existing call site to opt in (defaults to "" -- no production, matching [Card]'s own default). */
    producedMana: String = "",
    /** Deck Analysis Engine v2 Phase 2 -- per-format legality overrides for the P5
     * legality/construction pillar's golden tests (e.g. a banned-in-Commander card). Every legality
     * field defaults to "legal" (matching every pre-Phase-2 call site's expectation); only the
     * fields a test cares about need overriding. */
    legalityCommander: String = "legal",
    legalityStandard: String = "legal",
    legalityPioneer: String = "legal",
    legalityModern: String = "legal",
    legalityLegacy: String = "legal",
    legalityVintage: String = "legal",
    legalityPauper: String = "legal",
): Card = Card(
    scryfallId = id,
    name = name,
    printedName = null,
    manaCost = manaCost,
    cmc = cmc,
    colors = colors,
    colorIdentity = colorIdentity,
    typeLine = typeLine,
    printedTypeLine = null,
    oracleText = oracleText,
    printedText = null,
    keywords = emptyList(),
    power = power,
    toughness = toughness,
    loyalty = null,
    setCode = "TST",
    setName = "Test Set",
    collectorNumber = "1",
    rarity = rarity,
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
    legalityStandard = legalityStandard,
    legalityPioneer = legalityPioneer,
    legalityModern = legalityModern,
    legalityCommander = legalityCommander,
    legalityLegacy = legalityLegacy,
    legalityVintage = legalityVintage,
    legalityPauper = legalityPauper,
    flavorText = null,
    artist = null,
    scryfallUri = "https://scryfall.com/card/tst/1",
    tags = tags,
    userTags = userTags,
    suggestedTags = suggestedTags,
    producedMana = producedMana,
)

/** DeckEntry shortcut. */
fun entry(card: Card, quantity: Int = 1, isOwned: Boolean = true): DeckEntry =
    DeckEntry(card = card, quantity = quantity, isOwned = isOwned)
