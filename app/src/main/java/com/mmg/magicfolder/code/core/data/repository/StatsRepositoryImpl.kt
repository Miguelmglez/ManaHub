package com.mmg.magicfolder.code.core.data.repository


import com.mmg.magicfolder.code.core.data.local.dao.DeckDao
import com.mmg.magicfolder.code.core.data.local.dao.StatsDao
import com.mmg.magicfolder.code.core.data.local.entity.projection.*
import com.mmg.magicfolder.code.core.domain.model.*
import com.mmg.magicfolder.code.core.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val statsDao: StatsDao,
    private val deckDao:  DeckDao,
) : StatsRepository {

    override fun observeCollectionStats(): Flow = combine(
        statsDao.observeTotals(),
        statsDao.observeTotalValueUsd(),
        statsDao.observeTotalValueEur(),
        statsDao.observeMostValuableCards(),
        statsDao.observeCountByColorIdentity(),
        statsDao.observeCountByRarity(),
        statsDao.observeCountByTypeLine(),
        statsDao.observeManaCurve(),
        statsDao.observeCountBySet(),
        deckDao.observeDeckCount(),
    ) { totals, valueUsd, valueEur, topCards, colors, rarities, types, curve, sets, deckCount ->
        CollectionStats(
            totalCards        = totals.totalCards,
            uniqueCards       = totals.uniqueCards,
            totalDecks        = deckCount,
            totalValueUsd     = valueUsd,
            totalValueEur     = valueEur,
            mostValuableCards = topCards.map { CardValue(it.scryfallId, it.name, it.priceUsd, it.isFoil, it.imageArtCrop) },
            byColor           = colors.toColorMap(),
            byRarity          = rarities.toRarityMap(),
            byType            = types.toTypeMap(),
            cmcDistribution   = curve.associate { it.cmc to it.count },
            bySet             = sets.associate { it.setCode to it.count },
        )
    }

    private fun List.toColorMap(): Map {
        val result = mutableMapOf()
        for (row in this) {
            val parsed = row.colorIdentity
                .removeSurrounding("[", "]").split(",")
                .map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
            val color = when {
                parsed.isEmpty() -> MtgColor.COLORLESS
                parsed.size > 1  -> MtgColor.MULTICOLOR
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

    private fun List.toRarityMap(): Map =
        associate { row ->
            val r = when (row.rarity.lowercase()) {
                "common"   -> Rarity.COMMON;   "uncommon" -> Rarity.UNCOMMON
                "rare"     -> Rarity.RARE;     "mythic"   -> Rarity.MYTHIC
                else       -> Rarity.SPECIAL
            }
            r to row.count
        }

    private fun List.toTypeMap(): Map {
        val result = mutableMapOf()
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