package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.local.dao.StatsDao
import com.mmg.manahub.core.data.local.entity.projection.*
import com.mmg.manahub.core.domain.model.*
import com.mmg.manahub.core.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val statsDao: StatsDao,
    private val deckDao:  DeckDao,
) : StatsRepository {

    @Suppress("UNCHECKED_CAST")
    override fun observeCollectionStats(preferredCurrency: PreferredCurrency): Flow<CollectionStats> = combine(
        statsDao.observeTotals(),
        statsDao.observeTotalValueUsd(),
        statsDao.observeTotalValueEur(),
        statsDao.observeMostValuableCards(useEur = preferredCurrency == PreferredCurrency.EUR),
        statsDao.observeCountByColorIdentity(),
        statsDao.observeCountByRarity(),
        statsDao.observeCountByTypeLine(),
        statsDao.observeManaCurve(),
        statsDao.observeCountBySet(),
        deckDao.observeDeckCount(),
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

        CollectionStats(
            totalCards = totals.totalCards,
            uniqueCards = totals.uniqueCards,
            totalDecks = deckCount,
            totalValueUsd = valueUsd,
            totalValueEur = valueEur,
            mostValuableCards = topCards.map {
                CardValue(
                    scryfallId    = it.scryfallId,
                    name          = it.name,
                    priceUsd      = it.priceUsd,
                    priceEur      = it.priceEur,
                    isFoil        = it.isFoil,
                    imageArtCrop  = it.imageArtCrop,
                    colorIdentity = it.colorIdentity,
                )
            },
            byColor = colors.toColorMap(),
            byRarity = rarities.toRarityMap(),
            byType = types.toTypeMap(),
            cmcDistribution = curve.associate { it.cmc to it.count },
            bySet = sets.associate { it.setCode to it.count },
        )
    }

    private fun List<ColorCountProjection>.toColorMap(): Map<MtgColor, Int> {
        val result = mutableMapOf<MtgColor, Int>()
        for (row in this) {
            val parsed = row.colorIdentity
                .removeSurrounding("[", "]").split(",")
                .map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
            val color = when {
                parsed.isEmpty() -> MtgColor.COLORLESS
                else -> when (parsed.first()) {
                    "W" -> MtgColor.W; "U" -> MtgColor.U; "B" -> MtgColor.B
                    "R" -> MtgColor.R; "G" -> MtgColor.G
                    else -> MtgColor.COLORLESS
                }
            }
            result[color] = (result[color] ?: 0) + row.count
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
