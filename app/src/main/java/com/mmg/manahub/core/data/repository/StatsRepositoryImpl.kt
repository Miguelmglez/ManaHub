package com.mmg.manahub.core.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.local.dao.StatsDao
import com.mmg.manahub.core.data.local.entity.projection.ArtistCountProjection
import com.mmg.manahub.core.data.local.entity.projection.CardValueProjection
import com.mmg.manahub.core.data.local.entity.projection.CmcCountProjection
import com.mmg.manahub.core.data.local.entity.projection.ColorCountProjection
import com.mmg.manahub.core.data.local.entity.projection.DecadeCountProjection
import com.mmg.manahub.core.data.local.entity.projection.DuplicateCardProjection
import com.mmg.manahub.core.data.local.entity.projection.FormatCoverageProjection
import com.mmg.manahub.core.data.local.entity.projection.KeywordsProjection
import com.mmg.manahub.core.data.local.entity.projection.RarityCountProjection
import com.mmg.manahub.core.data.local.entity.projection.SetCountProjection
import com.mmg.manahub.core.data.local.entity.projection.SetValueProjection
import com.mmg.manahub.core.data.local.entity.projection.TagProjection
import com.mmg.manahub.core.data.local.entity.projection.TotalsProjection
import com.mmg.manahub.core.data.local.entity.projection.TypeCountProjection
import com.mmg.manahub.core.data.local.entity.projection.UniqueCardPriceProjection
import com.mmg.manahub.core.data.local.entity.projection.VariantCardProjection
import com.mmg.manahub.core.model.CardType
import com.mmg.manahub.core.model.CardValue
import com.mmg.manahub.core.model.CollectionStats
import com.mmg.manahub.core.model.MtgColor
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.Rarity
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.util.recordSafeNonFatal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class StatsRepositoryImpl(
    private val statsDao: StatsDao,
    private val deckDao: DeckDao,
    private val authRepository: AuthRepository,
    private val dispatcherProvider: DispatcherProvider,
) : StatsRepository {

    private val gson = Gson()
    private val tagListType = object : TypeToken<List<TagRecord>>() {}.type
    private val keywordListType = object : TypeToken<List<String>>() {}.type
    private data class TagRecord(val key: String, val category: String)

    /**
     * Emits the current authenticated user's ID, or null for unauthenticated/guest sessions.
     * All stats queries re-subscribe automatically when the session changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentUserIdFlow = authRepository.sessionState.map { state ->
        (state as? SessionState.Authenticated)?.user?.id
    }

    @Suppress("UNCHECKED_CAST")
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCollectionStats(
        preferredCurrency: PreferredCurrency,
        colorFilter: MtgColor?,
        setFilter: String?
    ): Flow<CollectionStats> {
        val colorCode = when (colorFilter) {
            null -> null
            MtgColor.COLORLESS -> "[]"   // colorless cards have color_identity stored as '[]'
            else -> colorFilter.name.take(1) // W, U, B, R, G
        }
        val useEur = preferredCurrency == PreferredCurrency.EUR

        return currentUserIdFlow.flatMapLatest { userId ->
            // Hall of Fame enrichment — cards illustrated by the CURRENT top artist. Chained off
            // observeTopArtist via its own flatMapLatest (a second, independent subscription is
            // cheap for a Room live query) rather than re-deriving inside the big combine() below,
            // since it needs its own nested re-subscription whenever the top artist changes.
            val artistCardsFlow = statsDao.observeTopArtist(colorCode, setFilter, userId)
                .flatMapLatest { artistProj ->
                    val artist = artistProj?.artist
                    if (artist.isNullOrBlank()) flowOf(emptyList())
                    else statsDao.observeCardsByArtist(artist, colorCode, setFilter, userId, limit = ARTIST_GALLERY_LIMIT)
                }

            combine(
                statsDao.observeTotals(colorCode, setFilter, userId),
                statsDao.observeTotalValueUsd(colorCode, setFilter, userId),
                statsDao.observeTotalValueEur(colorCode, setFilter, userId),
                statsDao.observeMostValuableCards(limit = 10, useEur = useEur, colorFilter = colorCode, setFilter = setFilter, userId = userId),
                statsDao.observeCountByColorIdentity(colorCode, setFilter, userId),
                statsDao.observeCountByRarity(colorCode, setFilter, userId),
                statsDao.observeCountByTypeLine(colorCode, setFilter, userId),
                statsDao.observeManaCurve(colorCode, setFilter, userId),
                statsDao.observeCountBySet(colorCode, setFilter, userId),
                deckDao.observeDeckCount(),
                // Innovative stats
                statsDao.observeTotalFoil(colorCode, setFilter, userId),
                statsDao.observeTotalFullArt(colorCode, setFilter, userId),
                statsDao.observeTopArtist(colorCode, setFilter, userId),
                statsDao.observeAvgManaValue(colorCode, setFilter, userId),
                statsDao.observeAvgPower(colorCode, setFilter, userId),
                statsDao.observeAvgToughness(colorCode, setFilter, userId),
                statsDao.observeOldestCard(colorCode, setFilter, userId),
                statsDao.observeNewestCard(colorCode, setFilter, userId),
                // New set and tag stats
                statsDao.observeTopSetByCount(colorCode, setFilter, userId),
                statsDao.observeTopSetByValue(colorCode, setFilter, useEur, userId),
                statsDao.observeAllCollectionTags(colorCode, setFilter, userId),
                // Phase 2 (2026-07 stats expansion)
                statsDao.observeUniqueCardPrices(colorCode, setFilter, userId),
                statsDao.observeTotalFoilValueUsd(colorCode, setFilter, userId),
                statsDao.observeTotalFoilValueEur(colorCode, setFilter, userId),
                statsDao.observeMostDuplicatedCard(colorCode, setFilter, userId),
                statsDao.observeFormatCoverage(colorCode, setFilter, userId),
                statsDao.observeAllCollectionKeywords(colorCode, setFilter, userId),
                // Hall of Fame enrichment (2026-07 stats expansion)
                statsDao.observeMostVariantsCard(colorCode, setFilter, userId),
                artistCardsFlow,
                statsDao.observeCountByDecade(colorCode, setFilter, userId),
            ) { args: Array<Any?> ->
                val totals    = args[0] as TotalsProjection
                val valueUsd  = args[1] as Double
                val valueEur  = args[2] as Double
                val topCards  = args[3] as List<CardValueProjection>
                val colors    = args[4] as List<ColorCountProjection>
                val rarities  = args[5] as List<RarityCountProjection>
                val types     = args[6] as List<TypeCountProjection>
                val curve     = args[7] as List<CmcCountProjection>
                val sets      = args[8] as List<SetCountProjection>
                val deckCount = args[9] as Int

                val totalFoil    = args[10] as Int
                val totalFullArt = args[11] as Int
                val artistProj   = args[12] as ArtistCountProjection?
                val avgManaValue = args[13] as Double?
                val avgPower     = args[14] as Double?
                val avgToughness = args[15] as Double?
                val oldest       = args[16] as CardValueProjection?
                val newest       = args[17] as CardValueProjection?

                val topSetCount  = args[18] as SetCountProjection?
                val topSetValue  = args[19] as SetValueProjection?
                val allTags      = args[20] as List<TagProjection>

                val uniqueCardPrices  = args[21] as List<UniqueCardPriceProjection>
                val foilValueUsd      = args[22] as Double
                val foilValueEur      = args[23] as Double
                val duplicateProj     = args[24] as DuplicateCardProjection?
                val formatCoverageProj = args[25] as FormatCoverageProjection
                val keywordProjs      = args[26] as List<KeywordsProjection>

                val variantsProj      = args[27] as VariantCardProjection?
                val artistCards       = args[28] as List<CardValueProjection>
                val decadeRows        = args[29] as List<DecadeCountProjection>

                // Process tags to find strategy distribution
                val tagMap = mutableMapOf<String, Int>()
                var tagParseFailures = 0
                allTags.forEach { tagProj ->
                    val rawTags = tagProj.tags ?: return@forEach
                    try {
                        val records: List<TagRecord> = gson.fromJson(rawTags, tagListType)
                        records.forEach { record ->
                            // Only count "strategy" or "synergy" tags for innovation
                            val cat = record.category.lowercase()
                            if (cat == "strategy" || cat == "synergy" || cat == "archetype") {
                                tagMap[record.key] = (tagMap[record.key] ?: 0) + 1
                            }
                        }
                    } catch (e: Exception) {
                        tagParseFailures++
                    }
                }
                if (tagParseFailures > 0) {
                    recordSafeNonFatal("stats_tag_parse_batch", RuntimeException("Failed to parse tags for $tagParseFailures cards"))
                }

                // Distinct-card avg/median value (active currency), unique cards priced > 0.
                val activePrices = uniqueCardPrices
                    .map { if (useEur) it.priceEur else it.priceUsd }
                    .filter { it > 0.0 }
                    .sorted()
                val avgCardValue = if (activePrices.isNotEmpty()) activePrices.average() else 0.0
                val medianCardValue = if (activePrices.isNotEmpty()) {
                    val n = activePrices.size
                    if (n % 2 == 1) activePrices[n / 2] else (activePrices[n / 2 - 1] + activePrices[n / 2]) / 2.0
                } else 0.0

                val activeTotalValue = if (useEur) valueEur else valueUsd
                val activeFoilValue  = if (useEur) foilValueEur else foilValueUsd
                // Ratios come from separate Room Flows combined via combine(...), which can emit
                // out of sync during a refreshPrices() call — coerce to guard against a transient
                // >100% reading (see CLAUDE.md-linked review findings, 2026-07-23 Stats expansion).
                val foilValueSharePercent = if (activeTotalValue > 0.0)
                    (activeFoilValue / activeTotalValue).toFloat().coerceIn(0f, 1f) else 0f

                val top10Sum = topCards.take(10).sumOf { if (useEur) it.priceEur else it.priceUsd }
                val valueConcentrationTop10Percent = if (activeTotalValue > 0.0)
                    (top10Sum / activeTotalValue).toFloat().coerceIn(0f, 1f) else 0f

                val formatCoverage = mapOf(
                    "Commander" to formatCoverageProj.commanderCount,
                    "Modern"    to formatCoverageProj.modernCount,
                    "Standard"  to formatCoverageProj.standardCount,
                )

                val keywordMap = mutableMapOf<String, Int>()
                var keywordParseFailures = 0
                keywordProjs.forEach { proj ->
                    val raw = proj.keywords ?: return@forEach
                    try {
                        val keywords: List<String> = gson.fromJson(raw, keywordListType)
                        keywords.forEach { kw ->
                            if (kw.isNotBlank()) keywordMap[kw] = (keywordMap[kw] ?: 0) + 1
                        }
                    } catch (e: Exception) {
                        keywordParseFailures++
                    }
                }
                if (keywordParseFailures > 0) {
                    recordSafeNonFatal("stats_keyword_parse_batch", RuntimeException("Failed to parse keywords for $keywordParseFailures cards"))
                }
                val keywordDistribution = keywordMap.entries
                    .sortedByDescending { it.value }.take(10).associate { it.key to it.value }

                CollectionStats(
                    totalCards = totals.totalCards,
                    uniqueCards = totals.uniqueCards,
                    totalDecks = deckCount,
                    totalValueUsd = valueUsd,
                    totalValueEur = valueEur,
                    mostValuableCards = topCards.map { it.toDomain() },
                    byColor = colors.toColorMap(),
                    byRarity = rarities.toRarityMap(),
                    byType = types.toTypeMap(),
                    cmcDistribution = curve.associate { it.cmc to it.count },
                    bySet = sets.associate { it.setCode to it.count },
                    // Innovative Stats
                    totalFoil      = totalFoil,
                    totalFullArt   = totalFullArt,
                    topArtist      = artistProj?.artist,
                    topArtistCount = artistProj?.count ?: 0,
                    avgManaValue   = avgManaValue ?: 0.0,
                    avgPower       = avgPower,
                    avgToughness   = avgToughness,
                    oldestCard     = oldest?.toDomain(),
                    newestCard     = newest?.toDomain(),
                    // Set Stats
                    topSetByCount  = topSetCount?.let { it.setCode to it.count },
                    topSetByValue  = topSetValue?.let { it.setCode to it.totalValue },
                    // AutoTags Stats
                    autoTagDistribution = tagMap.entries.sortedByDescending { it.value }.take(10).associate { it.key to it.value },
                    // Phase 2 (2026-07 stats expansion)
                    valueConcentrationTop10Percent = valueConcentrationTop10Percent,
                    avgCardValue          = avgCardValue,
                    medianCardValue       = medianCardValue,
                    foilValueSharePercent = foilValueSharePercent,
                    mostDuplicatedCard      = duplicateProj?.toDomain(),
                    mostDuplicatedCardCount = duplicateProj?.totalQuantity ?: 0,
                    formatCoverage      = formatCoverage,
                    keywordDistribution = keywordDistribution,
                    // Hall of Fame enrichment (2026-07 stats expansion)
                    mostVariantsCard  = variantsProj?.toDomain(),
                    mostVariantsCount = variantsProj?.variantCount ?: 0,
                    topArtistCards    = artistCards.map { it.toDomain() },
                    decadeDistribution = decadeRows.associate { "${it.decade}s" to it.count },
                )
            }
        }
            // The 30-way combine() above runs Gson parsing + list processing over the whole
            // filtered collection on every emission — move it off the caller's (previously Main)
            // dispatcher, and drop re-emissions where nothing actually changed (CollectionStats is
            // an all-value-type data class, so structural equality is cheap and correct here).
            // Contributing factor to a production OOM alongside the DAO GROUP BY fix above and the
            // RefreshCollectionPricesUseCase batching fix (see feedback_stats_room_invalidation_oom).
            .flowOn(dispatcherProvider.io)
            .distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCollectionSetCodes(): Flow<List<String>> {
        return currentUserIdFlow.flatMapLatest { userId ->
            statsDao.observeCollectionSetCodes(userId)
        }
    }

    /**
     * Distinct owned card count per set, global (no color/set filter — see [StatsDao
     * .observeDistinctOwnedCountBySet]). Joined by the ViewModel against Scryfall set metadata
     * (card_count) — this repository has no Scryfall dependency.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDistinctOwnedCountBySet(): Flow<Map<String, Int>> {
        return currentUserIdFlow.flatMapLatest { userId ->
            statsDao.observeDistinctOwnedCountBySet(userId)
                .map { rows -> rows.associate { it.setCode to it.count } }
        }
    }

    private fun DuplicateCardProjection.toDomain() = CardValue(
        scryfallId    = scryfallId,
        name          = name,
        priceUsd      = priceUsd,
        priceEur      = priceEur,
        isFoil        = isFoil,
        imageArtCrop  = imageArtCrop,
        colorIdentity = colorIdentity,
        setCode       = setCode,
        setName       = setName,
        rarity        = rarity,
    )

    private fun CardValueProjection.toDomain() = CardValue(
        scryfallId    = scryfallId,
        name          = name,
        priceUsd      = priceUsd,
        priceEur      = priceEur,
        isFoil        = isFoil,
        imageArtCrop  = imageArtCrop,
        colorIdentity = colorIdentity,
        setCode       = setCode,
        setName       = setName,
        rarity        = rarity,
        imageNormal   = imageNormal,
    )

    private fun VariantCardProjection.toDomain() = CardValue(
        scryfallId    = scryfallId,
        name          = name,
        priceUsd      = priceUsd,
        priceEur      = priceEur,
        isFoil        = isFoil,
        imageArtCrop  = imageArtCrop,
        colorIdentity = colorIdentity,
        setCode       = setCode,
        setName       = setName,
        rarity        = rarity,
    )

    private fun List<ColorCountProjection>.toColorMap(): Map<MtgColor, Int> {
        val result = mutableMapOf<MtgColor, Int>()
        for (row in this) {
            val parsed = row.colorIdentity
                .removeSurrounding("[", "]").split(",")
                .map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
            if (parsed.isEmpty()) {
                result[MtgColor.COLORLESS] = (result[MtgColor.COLORLESS] ?: 0) + row.count
            } else {
                // Multi-color cards (e.g. W/U) count toward each color they contain.
                for (colorStr in parsed) {
                    val color = when (colorStr) {
                        "W" -> MtgColor.W; "U" -> MtgColor.U; "B" -> MtgColor.B
                        "R" -> MtgColor.R; "G" -> MtgColor.G
                        else -> null
                    }
                    color?.let { result[it] = (result[it] ?: 0) + row.count }
                }
            }
        }
        return result
    }

    private fun List<RarityCountProjection>.toRarityMap(): Map<Rarity, Int> =
        associate { row ->
            val r = when (row.rarity.lowercase()) {
                "common"   -> Rarity.COMMON;   "uncommon" -> Rarity.UNCOMMON
                "rare"     -> Rarity.RARE;     "mythic"   -> Rarity.MYTHIC
                else       -> Rarity.SPECIAL
            }
            r to row.count
        }

    private fun List<TypeCountProjection>.toTypeMap(): Map<CardType, Int> {
        val result = mutableMapOf<CardType, Int>()
        for (row in this) {
            val t = when {
                "Creature"     in row.typeLine -> CardType.CREATURE
                "Instant"      in row.typeLine -> CardType.INSTANT
                "Sorcery"      in row.typeLine -> CardType.SORCERY
                "Enchantment"  in row.typeLine -> CardType.ENCHANTMENT
                "Artifact"     in row.typeLine -> CardType.ARTIFACT
                "Planeswalker" in row.typeLine -> CardType.PLANESWALKER
                "Land"         in row.typeLine -> CardType.LAND
                "Battle"       in row.typeLine -> CardType.BATTLE
                else                           -> CardType.OTHER
            }
            result[t] = (result[t] ?: 0) + row.count
        }
        return result
    }

    private companion object {
        /** Cap on the Top Artist gallery row (Hall of Fame enrichment, 2026-07 stats expansion). */
        const val ARTIST_GALLERY_LIMIT = 15
    }
}
