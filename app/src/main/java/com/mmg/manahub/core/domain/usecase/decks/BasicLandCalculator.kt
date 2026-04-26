package com.mmg.manahub.core.domain.usecase.decks

import com.mmg.manahub.core.domain.model.BasicLandDistribution
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DeckCard
import com.mmg.manahub.core.domain.model.DeckFormat
import kotlin.math.roundToInt

object BasicLandCalculator {

    val LAND_FOR_COLOR = mapOf(
        "W" to "Plains",
        "U" to "Island",
        "B" to "Swamp",
        "R" to "Mountain",
        "G" to "Forest",
    )

    fun calculate(
        mainboard: List<DeckCard>,
        nonBasicLands: List<DeckCard>,
        format: DeckFormat,
        commanderIdentity: Set<String>? = null,
    ): BasicLandDistribution {
        val nonBasicCount = nonBasicLands.sumOf { it.quantity }
        val basicSlotsAvailable = (format.targetLandCount - nonBasicCount).coerceAtLeast(0)

        if (basicSlotsAvailable == 0) return BasicLandDistribution()

        val colorWeights = mutableMapOf("W" to 0, "U" to 0, "B" to 0, "R" to 0, "G" to 0)

        mainboard.forEach { deckCard ->
            // In Commander, we ignore cards that don't fit the identity for land calculation purposes.
            // This treats them as user errors.
            val cardIdentity = deckCard.card.colorIdentity.toSet()
            if (commanderIdentity != null && !commanderIdentity.containsAll(cardIdentity)) {
                return@forEach
            }

            val manaCost = deckCard.card.manaCost ?: ""
            val qty = deckCard.quantity
            colorWeights["W"] = colorWeights["W"]!! + manaCost.count { it == 'W' } * qty
            colorWeights["U"] = colorWeights["U"]!! + manaCost.count { it == 'U' } * qty
            colorWeights["B"] = colorWeights["B"]!! + manaCost.count { it == 'B' } * qty
            colorWeights["R"] = colorWeights["R"]!! + manaCost.count { it == 'R' } * qty
            colorWeights["G"] = colorWeights["G"]!! + manaCost.count { it == 'G' } * qty
        }

        val totalWeight = colorWeights.values.sum()
        
        // If no weights (e.g. all colorless or all cards filtered out), 
        // we fallback to equal distribution if we have a commander identity, 
        // or return empty if no weights at all and no identity.
        if (totalWeight == 0) {
            return if (commanderIdentity != null && commanderIdentity.isNotEmpty()) {
                val allowedColors = commanderIdentity.filter { it in LAND_FOR_COLOR.keys }
                if (allowedColors.isEmpty()) return BasicLandDistribution()
                
                val perColor = basicSlotsAvailable / allowedColors.size
                val remainder = basicSlotsAvailable % allowedColors.size
                
                val allocated = mutableMapOf<String, Int>()
                allowedColors.forEachIndexed { i, color ->
                    allocated[color] = perColor + (if (i < remainder) 1 else 0)
                }
                BasicLandDistribution(
                    plains    = allocated["W"] ?: 0,
                    islands   = allocated["U"] ?: 0,
                    swamps    = allocated["B"] ?: 0,
                    mountains = allocated["R"] ?: 0,
                    forests   = allocated["G"] ?: 0,
                )
            } else {
                BasicLandDistribution()
            }
        }

        // Filter active colors to only those allowed by commander identity if provided
        val activeColors = colorWeights.filter { entry -> 
            entry.value > 0 && (commanderIdentity == null || commanderIdentity.contains(entry.key))
        }
        
        if (activeColors.isEmpty()) return BasicLandDistribution()

        val filteredTotalWeight = activeColors.values.sum()
        val allocated = mutableMapOf<String, Int>()
        var totalAllocated = 0

        activeColors.entries.forEachIndexed { i, (color, weight) ->
            val share = if (i == activeColors.size - 1) {
                basicSlotsAvailable - totalAllocated
            } else {
                (weight.toFloat() / filteredTotalWeight * basicSlotsAvailable)
                    .roundToInt()
                    .coerceAtLeast(1)
            }
            allocated[color] = share
            totalAllocated += share
        }

        return BasicLandDistribution(
            plains    = allocated["W"] ?: 0,
            islands   = allocated["U"] ?: 0,
            swamps    = allocated["B"] ?: 0,
            mountains = allocated["R"] ?: 0,
            forests   = allocated["G"] ?: 0,
        )
    }

    fun isLand(card: Card): Boolean =
        card.typeLine.contains("Land", ignoreCase = true)

    fun isBasicLand(card: Card): Boolean =
        card.typeLine.contains("Basic", ignoreCase = true) && isLand(card)

    fun getProducedColors(card: Card): Set<String> {
        val colors = mutableSetOf<String>()
        val line = card.typeLine
        if (line.contains("Plains",   ignoreCase = true)) colors.add("W")
        if (line.contains("Island",   ignoreCase = true)) colors.add("U")
        if (line.contains("Swamp",    ignoreCase = true)) colors.add("B")
        if (line.contains("Mountain", ignoreCase = true)) colors.add("R")
        if (line.contains("Forest",   ignoreCase = true)) colors.add("G")
        return colors
    }
}
