package com.mmg.manahub.feature.draft.data.engine

import com.mmg.manahub.feature.draft.domain.engine.BoosterGenerator
import com.mmg.manahub.feature.draft.domain.model.BoosterCardEntry
import com.mmg.manahub.feature.draft.domain.model.BoosterPack
import com.mmg.manahub.feature.draft.domain.model.BoosterSheet
import com.mmg.manahub.feature.draft.domain.model.BoosterVariant
import com.mmg.manahub.feature.draft.domain.model.DraftCard
import com.mmg.manahub.feature.draft.domain.model.DraftConfig
import com.mmg.manahub.feature.draft.domain.model.DraftableSet
import java.util.UUID
import kotlin.random.Random

class WeightedBoosterGenerator(
    private val random: Random = Random.Default,
) : BoosterGenerator {

    override fun generate(set: DraftableSet, config: DraftConfig): List<BoosterPack> {
        val total = config.packCount * config.seatCount
        return List(total) { index ->
            val seatIndex = index / config.packCount
            buildPack(set, seatIndex)
        }
    }

    private fun buildPack(set: DraftableSet, seatIndex: Int): BoosterPack {
        val variant = pickVariant(set.booster.boosters)
        val cards = mutableListOf<DraftCard>()

        for ((sheetName, count) in variant.contents) {
            val sheet = set.booster.sheets[sheetName] ?: continue
            val picked = pickFromSheet(sheet, count, set)
            cards += picked
        }

        return BoosterPack(id = UUID.randomUUID().toString(), cards = cards)
    }

    private fun pickVariant(variants: List<BoosterVariant>): BoosterVariant {
        val totalWeight = variants.sumOf { it.weight }
        var roll = random.nextInt(totalWeight)
        for (v in variants) {
            roll -= v.weight
            if (roll < 0) return v
        }
        return variants.last()
    }

    private fun pickFromSheet(
        sheet: BoosterSheet,
        count: Int,
        set: DraftableSet,
    ): List<DraftCard> {
        if (sheet.cards.isEmpty()) return emptyList()

        val actualCount = minOf(count, sheet.cards.size)
        val selected = weightedSampleWithoutReplacement(sheet.cards, actualCount)

        val result = selected.map { entry -> toDraftCard(entry, sheet.foil, set) }

        return if (sheet.balanceColors) balanceColors(result, sheet, set) else result
    }

    private fun weightedSampleWithoutReplacement(
        entries: List<BoosterCardEntry>,
        count: Int,
    ): List<BoosterCardEntry> {
        val pool = entries.toMutableList()
        val result = mutableListOf<BoosterCardEntry>()
        repeat(count) {
            if (pool.isEmpty()) return@repeat
            val totalWeight = pool.sumOf { it.weight }
            var roll = random.nextInt(totalWeight)
            val idx = pool.indexOfFirst { entry -> roll -= entry.weight; roll < 0 }
                .takeIf { it >= 0 } ?: (pool.size - 1)
            result += pool[idx]
            pool.removeAt(idx)
        }
        return result
    }

    private fun toDraftCard(entry: BoosterCardEntry, foil: Boolean, set: DraftableSet): DraftCard {
        val card = set.cards.firstOrNull { it.scryfallId == entry.id }
            ?: return DraftCard(
                card = set.cards.first(),
                pickOrderRank = null,
                tierRating = null,
                isFoil = foil,
            ).let {
                // Fallback: card id not in pool; skip by returning a placeholder resolved later
                it.copy()
            }
        val tier = set.ratings[entry.id]
        return DraftCard(
            card = card,
            pickOrderRank = tier?.pickOrderRank,
            tierRating = tier?.tierRating,
            isFoil = foil,
        )
    }

    private fun balanceColors(
        picked: List<DraftCard>,
        sheet: BoosterSheet,
        set: DraftableSet,
    ): List<DraftCard> {
        val allColors = setOf("W", "U", "B", "R", "G")
        val sheetColors = sheet.cards
            .mapNotNull { entry -> set.cards.firstOrNull { it.scryfallId == entry.id } }
            .flatMap { it.colors }
            .filter { it in allColors }
            .toSet()

        val pickedColors = picked.flatMap { it.card.colors }.filter { it in allColors }.toSet()
        val missingColors = sheetColors - pickedColors
        if (missingColors.isEmpty()) return picked

        val result = picked.toMutableList()
        for (missing in missingColors) {
            val mostRepColor = result
                .flatMap { dc -> dc.card.colors.filter { it in allColors }.map { c -> c to dc } }
                .groupBy({ it.first }, { it.second })
                .maxByOrNull { it.value.size }
                ?.value?.lastOrNull() ?: continue

            val replacement = sheet.cards
                .mapNotNull { entry -> set.cards.firstOrNull { it.scryfallId == entry.id } }
                .firstOrNull { missing in it.colors }
                ?: continue

            val swapIdx = result.indexOf(mostRepColor)
            if (swapIdx >= 0) {
                val tier = set.ratings[replacement.scryfallId]
                result[swapIdx] = DraftCard(
                    card = replacement,
                    pickOrderRank = tier?.pickOrderRank,
                    tierRating = tier?.tierRating,
                    isFoil = sheet.foil,
                )
            }
        }
        return result
    }
}
