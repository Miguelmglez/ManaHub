package com.mmg.manahub.core.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.local.dao.StatsDao
import com.mmg.manahub.core.data.local.entity.projection.ArtistCountProjection
import com.mmg.manahub.core.data.local.entity.projection.CardValueProjection
import com.mmg.manahub.core.data.local.entity.projection.CmcCountProjection
import com.mmg.manahub.core.data.local.entity.projection.ColorCountProjection
import com.mmg.manahub.core.data.local.entity.projection.RarityCountProjection
import com.mmg.manahub.core.data.local.entity.projection.SetCountProjection
import com.mmg.manahub.core.data.local.entity.projection.SetValueProjection
import com.mmg.manahub.core.data.local.entity.projection.TagProjection
import com.mmg.manahub.core.data.local.entity.projection.TotalsProjection
import com.mmg.manahub.core.data.local.entity.projection.TypeCountProjection
import com.mmg.manahub.core.domain.model.CardType
import com.mmg.manahub.core.domain.model.CardValue
import com.mmg.manahub.core.domain.model.CollectionStats
import com.mmg.manahub.core.domain.model.MtgColor
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.domain.model.Rarity
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val statsDao: StatsDao,
    private val deckDao: DeckDao,
    private val authRepository: AuthRepository,
) : StatsRepository {

    private val gson = Gson()
    private val tagListType = object : TypeToken<List<TagRecord>>() {}.type
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
                statsDao.observeAllCollectionTags(colorCode, setFilter, userId)
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

                // Process tags to find strategy distribution
                val tagMap = mutableMapOf<String, Int>()
                allTags.forEach { tagProj ->
                    runCatching {
                        val rawTags = tagProj.tags ?: return@runCatching
                        val records: List<TagRecord> = gson.fromJson(rawTags, tagListType)
                        records.forEach { record ->
                            // Only count "strategy" or "synergy" tags for innovation
                            if (record.category.lowercase() in listOf("strategy", "synergy", "archetype")) {
                                tagMap[record.key] = (tagMap[record.key] ?: 0) + 1
                            }
                        }
                    }
                }

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
                    autoTagDistribution = tagMap.entries.sortedByDescending { it.value }.take(10).associate { it.key to it.value }
                )
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCollectionSetCodes(): Flow<List<String>> {
        return currentUserIdFlow.flatMapLatest { userId ->
            statsDao.observeCollectionSetCodes(userId)
        }
    }

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
}
