package com.mmg.manahub.util

import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.domain.model.TagCategory
import com.mmg.manahub.core.domain.model.UserCard
import com.mmg.manahub.core.domain.model.UserCardWithCard
import com.mmg.manahub.core.domain.model.UserPreferences
import com.mmg.manahub.core.domain.model.AppLanguage
import com.mmg.manahub.core.domain.model.CardLanguage
import com.mmg.manahub.core.domain.model.NewsLanguage

/**
 * Central place for test data builders.
 * All fakes use conservative defaults so each test only needs to override
 * the fields relevant to the scenario under test.
 */
object TestFixtures {

    // ── Timestamps ────────────────────────────────────────────────────────────

    /** A timestamp that is considered "fresh" by CachePolicy (< 24 h ago). */
    val FRESH_TIMESTAMP: Long
        get() = System.currentTimeMillis() - 1_000L   // 1 second ago

    /** A timestamp that is considered "stale" by CachePolicy (> 7 days ago). */
    val STALE_TIMESTAMP: Long
        get() = System.currentTimeMillis() - (8L * 24 * 60 * 60 * 1_000)  // 8 days ago

    /** A timestamp older than the 24-h fresh window but not yet stale. */
    val EXPIRED_BUT_NOT_STALE_TIMESTAMP: Long
        get() = System.currentTimeMillis() - (2L * 24 * 60 * 60 * 1_000) // 2 days ago

    // ── Cards ─────────────────────────────────────────────────────────────────

    fun buildCard(
        scryfallId:  String  = "test-scryfall-id-001",
        name:        String  = "Lightning Bolt",
        setCode:     String  = "lea",
        rarity:      String  = "common",
        cmc:         Double  = 1.0,
        colors:      List<String> = listOf("R"),
        colorIdentity: List<String> = listOf("R"),
        typeLine:    String  = "Instant",
        priceUsd:    Double? = 0.50,
        priceUsdFoil: Double? = 2.50,
        priceEur:    Double? = 0.40,
        priceEurFoil: Double? = 2.00,
        oracleText:  String? = "Lightning Bolt deals 3 damage to any target.",
        keywords:    List<String> = emptyList(),
        power:       String? = null,
        toughness:   String? = null,
        cachedAt:    Long    = FRESH_TIMESTAMP,
        isStale:     Boolean = false,
        tags:        List<CardTag> = emptyList(),
        userTags:    List<CardTag> = emptyList(),
        legalityStandard:  String = "not_legal",
        legalityPioneer:   String = "legal",
        legalityModern:    String = "legal",
        legalityCommander: String = "legal",
    ) = Card(
        scryfallId        = scryfallId,
        name              = name,
        printedName       = null,
        manaCost          = "{R}",
        cmc               = cmc,
        colors            = colors,
        colorIdentity     = colorIdentity,
        typeLine          = typeLine,
        printedTypeLine   = null,
        oracleText        = oracleText,
        printedText       = null,
        keywords          = keywords,
        power             = power,
        toughness         = toughness,
        loyalty           = null,
        setCode           = setCode,
        setName           = "Limited Edition Alpha",
        collectorNumber   = "161",
        rarity            = rarity,
        releasedAt        = "1993-08-05",
        frameEffects      = emptyList(),
        promoTypes        = emptyList(),
        lang              = "en",
        imageNormal       = "https://example.com/img.jpg",
        imageArtCrop      = null,
        imageBackNormal   = null,
        priceUsd          = priceUsd,
        priceUsdFoil      = priceUsdFoil,
        priceEur          = priceEur,
        priceEurFoil      = priceEurFoil,
        legalityStandard  = legalityStandard,
        legalityPioneer   = legalityPioneer,
        legalityModern    = legalityModern,
        legalityCommander = legalityCommander,
        flavorText        = null,
        artist            = "Christopher Rush",
        scryfallUri       = "https://scryfall.com/card/lea/161",
        isStale           = isStale,
        staleReason       = null,
        cachedAt          = cachedAt,
        tags              = tags,
        userTags          = userTags,
    )

    fun buildStaleCard(scryfallId: String = "test-scryfall-id-001") =
        buildCard(scryfallId = scryfallId, cachedAt = STALE_TIMESTAMP, isStale = true)

    fun buildExpiredCard(scryfallId: String = "test-scryfall-id-001") =
        buildCard(scryfallId = scryfallId, cachedAt = EXPIRED_BUT_NOT_STALE_TIMESTAMP)

    // ── UserCards ─────────────────────────────────────────────────────────────

    fun buildUserCard(
        id:               String  = "test-uuid-001",
        scryfallId:       String  = "test-scryfall-id-001",
        quantity:         Int     = 1,
        isFoil:           Boolean = false,
        isAlternativeArt: Boolean = false,
        condition:        String  = "NM",
        language:         String  = "en",
        isForTrade:       Boolean = false,
        isInWishlist:     Boolean = false,
        createdAt:        Long    = System.currentTimeMillis(),
    ) = UserCard(
        id               = id,
        scryfallId       = scryfallId,
        quantity         = quantity,
        isFoil           = isFoil,
        isAlternativeArt = isAlternativeArt,
        condition        = condition,
        language         = language,
        isForTrade       = isForTrade,
        isInWishlist     = isInWishlist,
        createdAt        = createdAt,
    )

    fun buildUserCardWithCard(
        userCard: UserCard = buildUserCard(),
        card:     Card     = buildCard(scryfallId = userCard.scryfallId),
    ) = UserCardWithCard(userCard = userCard, card = card)

    // ── UserPreferences ───────────────────────────────────────────────────────

    fun buildPreferences(
        cardLanguage:      CardLanguage      = CardLanguage.ENGLISH,
        preferredCurrency: PreferredCurrency = PreferredCurrency.EUR,
    ) = UserPreferences(
        appLanguage       = AppLanguage.ENGLISH,
        cardLanguage      = cardLanguage,
        newsLanguages     = setOf(NewsLanguage.ENGLISH),
        preferredCurrency = preferredCurrency,
    )

    // ── CardEntity (Room entity) ──────────────────────────────────────────────

    /**
     * Creates a real [CardEntity] for tests that need a concrete DB entity
     * rather than a mock. Avoids MockK limitations with final data classes.
     */
    fun buildCardEntity(
        scryfallId:    String  = "test-scryfall-id-001",
        name:          String  = "Lightning Bolt",
        cachedAt:      Long    = FRESH_TIMESTAMP,
        isStale:       Boolean = false,
        tags:          String  = "[]",
        userTags:      String  = "[]",
        suggestedTags: String  = "[]",
        priceUsd:      Double? = 0.50,
        rarity:        String  = "common",
    ) = CardEntity(
        scryfallId        = scryfallId,
        name              = name,
        printedName       = null,
        lang              = "en",
        manaCost          = "{R}",
        cmc               = 1.0,
        colors            = "[\"R\"]",
        colorIdentity     = "[\"R\"]",
        typeLine          = "Instant",
        printedTypeLine   = null,
        oracleText        = "Lightning Bolt deals 3 damage to any target.",
        printedText       = null,
        keywords          = "[]",
        power             = null,
        toughness         = null,
        loyalty           = null,
        setCode           = "lea",
        setName           = "Limited Edition Alpha",
        collectorNumber   = "161",
        rarity            = rarity,
        releasedAt        = "1993-08-05",
        frameEffects      = "[]",
        promoTypes        = "[]",
        imageNormal       = "https://example.com/img.jpg",
        imageArtCrop      = null,
        imageBackNormal   = null,
        priceUsd          = priceUsd,
        priceUsdFoil      = null,
        priceEur          = null,
        priceEurFoil      = null,
        legalityStandard  = "not_legal",
        legalityPioneer   = "legal",
        legalityModern    = "legal",
        legalityCommander = "legal",
        flavorText        = null,
        artist            = "Christopher Rush",
        scryfallUri       = "https://scryfall.com/card/lea/161",
        cachedAt          = cachedAt,
        isStale           = isStale,
        staleReason       = null,
        tags              = tags,
        userTags          = userTags,
        suggestedTags     = suggestedTags,
    )

    fun buildFreshCardEntity(scryfallId: String = "test-scryfall-id-001") =
        buildCardEntity(scryfallId = scryfallId, cachedAt = FRESH_TIMESTAMP)

    fun buildExpiredCardEntity(scryfallId: String = "test-scryfall-id-001") =
        buildCardEntity(scryfallId = scryfallId, cachedAt = EXPIRED_BUT_NOT_STALE_TIMESTAMP)

    fun buildStaleCardEntity(scryfallId: String = "test-scryfall-id-001") =
        buildCardEntity(scryfallId = scryfallId, cachedAt = STALE_TIMESTAMP, isStale = true)

    // ── Tags ──────────────────────────────────────────────────────────────────

    val TAG_REMOVAL  = CardTag("removal",    TagCategory.ROLE)
    val TAG_BURN     = CardTag("burn",        TagCategory.STRATEGY)
    val TAG_CUSTOM   = CardTag("my_tag",     TagCategory.CUSTOM)
}
