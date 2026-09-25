package com.mmg.manahub.core.model

/**
 * Colour-affinity rules shared by the owner's Profile and the stats snapshot friends see, so both
 * surfaces always name the same colours.
 */
object CollectionColorAffinity {

    private val WUBRG = listOf(MtgColor.W, MtgColor.U, MtgColor.B, MtgColor.R, MtgColor.G)

    /**
     * Counts copies per colour from `(color_identity JSON, count)` rows. A multicolour card counts
     * toward each of its colours; an empty identity counts as [MtgColor.COLORLESS].
     */
    fun countByColor(rows: List<Pair<String, Int>>): Map<MtgColor, Int> {
        val result = mutableMapOf<MtgColor, Int>()
        for ((identity, count) in rows) {
            val colors = parseIdentity(identity).mapNotNull { code -> WUBRG.firstOrNull { it.name == code } }
            if (colors.isEmpty()) {
                result[MtgColor.COLORLESS] = (result[MtgColor.COLORLESS] ?: 0) + count
            } else {
                colors.forEach { result[it] = (result[it] ?: 0) + count }
            }
        }
        return result
    }

    /** The coloured (non-colourless) colour with the most copies as its WUBRG code, ties in WUBRG order. */
    fun favouriteColorCode(byColor: Map<MtgColor, Int>): String? =
        WUBRG.filter { (byColor[it] ?: 0) > 0 }
            .maxByOrNull { byColor[it] ?: 0 }
            ?.name

    /** WUBRG codes of a `color_identity` JSON array; an empty identity reads as `["C"]`. */
    fun identityCodes(colorIdentityJson: String): List<String> =
        parseIdentity(colorIdentityJson).filter { code -> WUBRG.any { it.name == code } }.ifEmpty { listOf("C") }

    private fun parseIdentity(json: String): List<String> =
        json.trim().removeSurrounding("[", "]").split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
}
