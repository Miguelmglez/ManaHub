package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.core.domain.model.DeckFormat
import com.mmg.manahub.core.domain.model.SuggestedTag

// ─────────────────────────────────────────────────────────────────────────────
//  Shared test fixtures for the deck-scoring engine test suite.
//  All helpers are plain functions (not class members) so any test file can
//  import just the symbols it needs.
// ─────────────────────────────────────────────────────────────────────────────

// ── Card fixture builder ──────────────────────────────────────────────────────

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
    legalityStandard: String = "legal",
    legalityCommander: String = "legal",
    legalityPioneer: String = "legal",
    legalityModern: String = "legal",
    legalityLegacy: String = "legal",
    legalityVintage: String = "legal",
    legalityPauper: String = "legal",
    gameChanger: Boolean = false,
    manaCost: String? = null,
): Card = Card(
    scryfallId        = id,
    name              = name,
    printedName       = null,
    manaCost          = manaCost,
    cmc               = cmc,
    colors            = colors,
    colorIdentity     = colorIdentity,
    typeLine          = typeLine,
    printedTypeLine   = null,
    oracleText        = oracleText,
    printedText       = null,
    keywords          = emptyList(),
    power             = power,
    toughness         = toughness,
    loyalty           = null,
    setCode           = "TST",
    setName           = "Test Set",
    collectorNumber   = "1",
    rarity            = "rare",
    releasedAt        = "2024-01-01",
    frameEffects      = emptyList(),
    promoTypes        = emptyList(),
    lang              = "en",
    imageNormal       = null,
    imageArtCrop      = null,
    imageBackNormal   = null,
    priceUsd          = null,
    priceUsdFoil      = null,
    priceEur          = null,
    priceEurFoil      = null,
    legalityStandard  = legalityStandard,
    legalityPioneer   = legalityPioneer,
    legalityModern    = legalityModern,
    legalityCommander = legalityCommander,
    legalityLegacy    = legalityLegacy,
    legalityVintage   = legalityVintage,
    legalityPauper    = legalityPauper,
    flavorText        = null,
    artist            = null,
    scryfallUri       = "https://scryfall.com/card/tst/1",
    tags              = tags,
    userTags          = userTags,
    suggestedTags     = suggestedTags,
    gameChanger       = gameChanger,
)

/** Shortcut for a land card. */
fun landCard(
    id: String = "land-0001",
    name: String = "Island",
    colors: List<String> = listOf("U"),
    colorIdentity: List<String> = listOf("U"),
): Card = card(id = id, name = name, typeLine = "Basic Land — Island",
    colors = colors, colorIdentity = colorIdentity, cmc = 0.0)

// ── DeckEntry shortcut ────────────────────────────────────────────────────────

fun entry(card: Card, quantity: Int = 1, isOwned: Boolean = true): DeckEntry =
    DeckEntry(card = card, quantity = quantity, isOwned = isOwned)

// ── Profile builder helper ────────────────────────────────────────────────────

/**
 * Builds a minimal Commander DeckProfile without running `DeckScorer.profile()`.
 * Useful when you want to inject specific roleCounts or tagFingerprint directly.
 */
fun minimalProfile(
    format: DeckFormat = DeckFormat.COMMANDER,
    colorIdentity: Set<ManaColor> = setOf(ManaColor.U, ManaColor.G),
    seedTags: List<CardTag> = emptyList(),
    tagFingerprint: Map<String, Float> = emptyMap(),
    roleCounts: Map<DeckRole, Int> = emptyMap(),
    avgCmc: Double = 2.5,
    nonLandCount: Int = 30,
    curveHistogram: Map<Int, Int> = emptyMap(),
): DeckProfile = DeckProfile(
    format          = format,
    colorIdentity   = colorIdentity,
    seedTags        = seedTags,
    tagFingerprint  = tagFingerprint,
    // DeckProfile.roleCounts is Float-based (quantity × confidence) since RoleClassifier v2.
    // The fixture keeps the ergonomic Int map and widens it to Float here.
    roleCounts      = roleCounts.mapValues { it.value.toFloat() },
    skeleton        = DeckSkeletons.forFormat(format),
    avgCmc          = avgCmc,
    curveHistogram  = curveHistogram,
    nonLandCount    = nonLandCount,
)

// ── PowerResolver helpers ─────────────────────────────────────────────────────

/** Returns a constant power for every card (deterministic tests). */
fun fixedPower(normalized: Float, isGameChanger: Boolean = false): PowerResolver =
    PowerResolver { CardPower(normalized, isGameChanger) }

/** Maps scryfallId → normalized power; defaults to 0.5 for unknown cards. */
fun mappedPower(vararg pairs: Pair<String, Float>): PowerResolver {
    val map = mapOf(*pairs)
    return PowerResolver { card -> CardPower(map[card.scryfallId] ?: 0.5f, card.gameChanger) }
}

// ── Common tag constants (aliases kept short for test readability) ─────────────

val tagManaRock    = CardTag.MANA_ROCK
val tagManaDork    = CardTag.MANA_DORK
val tagRamp        = CardTag.RAMP
val tagDraw        = CardTag.DRAW_ENGINE
val tagRemoval     = CardTag.REMOVAL
val tagWrath       = CardTag.WRATH
val tagCounterspell = CardTag.COUNTERSPELL
val tagProtection  = CardTag.PROTECTION
val tagStax        = CardTag.STAX
val tagTutor       = CardTag.TUTOR
val tagWinCon      = CardTag.WIN_CON
val tagCombo       = CardTag.COMBO
val tagInfinite    = CardTag.INFINITE
val tagTokens      = CardTag.TOKENS
val tagTribal      = CardTag.TRIBAL           // STRATEGY category
val tagPlusCounters = CardTag.PLUS_COUNTERS   // STRATEGY category

fun suggestedTag(tag: CardTag, confidence: Float = 0.9f): SuggestedTag =
    SuggestedTag(tag = tag, confidence = confidence)
