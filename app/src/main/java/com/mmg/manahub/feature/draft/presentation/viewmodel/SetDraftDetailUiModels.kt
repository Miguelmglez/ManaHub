package com.mmg.manahub.feature.draft.presentation.viewmodel

// COMMENTS_REVIEWED: 2026-09-22

import androidx.compose.runtime.Immutable
import com.mmg.manahub.core.model.ArchetypeKeyCard
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.MechanicKeyCard
import com.mmg.manahub.core.model.SetDraftGuide
import com.mmg.manahub.core.model.SetTierList
import com.mmg.manahub.core.model.TierCard
import com.mmg.manahub.feature.draft.presentation.ui.DraftGuideRichTextParser
import com.mmg.manahub.feature.draft.presentation.ui.DraftGuideRichTextSegment

/** Top-level Guide sections; [id] is the stable expansion/list key. */
enum class GuideSection(val id: String) {
    OVERVIEW("overview"),
    MECHANICS("mechanics"),
    ARCHETYPES("archetypes"),
    COLOR_RANKING("colors"),
}

/** Guide editorial text parsed once, off the main thread. */
@Immutable
class GuideRichText internal constructor(
    internal val segments: List<DraftGuideRichTextSegment>,
)

/** A card row inside the Guide or Tier List, with a key unique across the whole list. */
@Immutable
data class GuideCardUi(
    val key: String,
    val card: Card,
)

/** The Overview section; always rendered flat. */
@Immutable
data class GuideOverviewUi(
    val summary: GuideRichText?,
    val formatSpeed: GuideRichText?,
    val keyNotes: List<GuideRichText>,
)

/** One Color Ranking sub-section. */
@Immutable
data class ColorRankingUi(
    val id: String,
    val rank: Int,
    val manaToken: String?,
    val title: GuideRichText,
    val note: GuideRichText?,
    val keyCommons: List<GuideCardUi> = emptyList(),
    val keyUncommons: List<GuideCardUi> = emptyList(),
)

/** One Mechanics sub-section. [isFlatExamples] swaps the "Overperformers" label for "Key Cards". */
@Immutable
data class MechanicUi(
    val id: String,
    val name: String,
    val summary: GuideRichText?,
    val performance: GuideRichText?,
    val overperformers: List<GuideCardUi>,
    val underperformers: List<GuideCardUi>,
    val isFlatExamples: Boolean,
)

/** One Archetypes sub-section. */
@Immutable
data class ArchetypeUi(
    val id: String,
    val name: String,
    val manaTokens: List<String>,
    val tier: String,
    val tierLetter: String,
    val difficulty: String,
    val winRate: Double?,
    val strategy: GuideRichText?,
    val notes: GuideRichText?,
    val keyCards: List<GuideCardUi>,
    val cardsToAvoid: List<GuideCardUi>,
)

/** Key cards grouped by rarity and color for the Color Ranking section. */
@Immutable
data class KeyColorCardGroupUi(
    val id: String,
    val label: String,
    val manaToken: String?,
    val cards: List<GuideCardUi>,
    val isUncommon: Boolean,
)

/** Render-ready Guide: rich text pre-parsed and every card pre-mapped. */
@Immutable
data class SetDraftGuideUiModel(
    val overview: GuideOverviewUi,
    val colorRanking: List<ColorRankingUi>,
    val mechanics: List<MechanicUi>,
    val archetypes: List<ArchetypeUi>,
    val unrankedKeyCardGroups: List<KeyColorCardGroupUi>,
)

/** One tier of the Tier List with its cards pre-mapped. */
@Immutable
data class TierGroupUi(
    val tier: String,
    val label: String,
    val description: GuideRichText?,
    val cards: List<TierCardUi>,
)

/** A tier-list card plus the fields the filter needs. */
@Immutable
data class TierCardUi(
    val key: String,
    val card: Card,
    val name: String,
    val colors: List<String>,
)

/** Pure builders for the Set Draft Detail UI models. Heavy: call off the main thread. */
object SetDraftDetailUiModelBuilder {

    private val MANA_TOKEN = Regex("\\{([WUBRGC])\\}")
    private val LEADING_MANA_TOKENS = Regex("^\\s*(?:\\{[WUBRGC]\\}\\s*)+")
    private val ANY_BRACED_TOKEN = Regex("\\{[^}]+\\}\\s*")

    /** Builds the Guide UI model; guide cards repeating across sections share one [Card]. */
    fun buildGuide(guide: SetDraftGuide, setCode: String): SetDraftGuideUiModel {
        val cards = HashMap<String, Card>()

        fun ArchetypeKeyCard.toUi(key: String) = GuideCardUi(
            key = "$key-$scryfallId",
            card = if (scryfallId.isBlank()) toCard(setCode) else cards.getOrPut(scryfallId) { toCard(setCode) },
        )

        fun MechanicKeyCard.toUi(key: String) = GuideCardUi(
            key = "$key-$scryfallId",
            card = if (scryfallId.isBlank()) toCard(setCode) else cards.getOrPut(scryfallId) { toCard(setCode) },
        )

        val overview = GuideOverviewUi(
            summary = guide.summary.toRichTextOrNull(),
            formatSpeed = guide.formatSpeed.toRichTextOrNull(),
            keyNotes = guide.keyGameplayNotes.filter { it.isNotBlank() }.map { it.toRichText() },
        )

        fun buildColorGroups(
            source: Map<String, List<ArchetypeKeyCard>>,
            prefix: String,
            isUncommon: Boolean,
        ) = source.entries
            .filter { (_, groupCards) -> groupCards.isNotEmpty() }
            .mapIndexed { index, (colorLabel, groupCards) ->
                KeyColorCardGroupUi(
                    id = "$prefix:$index",
                    label = colorLabel.replace(ANY_BRACED_TOKEN, "").trim().ifBlank { colorLabel },
                    manaToken = MANA_TOKEN.find(colorLabel)?.groupValues?.getOrNull(1),
                    cards = groupCards.mapIndexed { i, card -> card.toUi("$prefix-$index-$i") },
                    isUncommon = isUncommon,
                )
            }

        val allKeyCardGroups = buildColorGroups(guide.keyCommonsByColor, "commons", false) +
            buildColorGroups(guide.keyUncommonsByColor, "uncommons", true)
        val rankedManaTokens = guide.colorRanking.mapNotNull { MANA_TOKEN.find(it)?.groupValues?.getOrNull(1) }.toSet()
        val colorRanking = guide.colorRanking.mapIndexed { index, entry ->
            val manaToken = MANA_TOKEN.find(entry)?.groupValues?.getOrNull(1)
            val matchingGroups = if (manaToken == null) emptyList() else allKeyCardGroups.filter { it.manaToken == manaToken }
            ColorRankingUi(
                id = "colors:$index",
                rank = index + 1,
                manaToken = manaToken,
                title = entry.replace(LEADING_MANA_TOKENS, "").trim().toRichText(),
                note = guide.colorNotes[entry]?.toRichTextOrNull(),
                keyCommons = matchingGroups.filterNot { it.isUncommon }.flatMap { it.cards },
                keyUncommons = matchingGroups.filter { it.isUncommon }.flatMap { it.cards },
            )
        }
        val unrankedKeyCardGroups = allKeyCardGroups.filter { it.manaToken !in rankedManaTokens }

        val mechanics = guide.mechanics.mapIndexed { index, mechanic ->
            val id = "mechanics:$index"
            val examples = mechanic.keyExamples
            MechanicUi(
                id = id,
                name = mechanic.name,
                summary = mechanic.summary.toRichTextOrNull(),
                performance = mechanic.performance.toRichTextOrNull(),
                overperformers = examples?.overperformers.orEmpty()
                    .mapIndexed { i, card -> card.toUi("$id-over-$i") },
                underperformers = examples?.underperformers.orEmpty()
                    .mapIndexed { i, card -> card.toUi("$id-under-$i") },
                isFlatExamples = examples != null &&
                    examples.overperformers.isNotEmpty() &&
                    examples.underperformers.isEmpty(),
            )
        }

        val archetypes = guide.archetypes.mapIndexed { index, archetype ->
            val id = "archetypes:$index"
            ArchetypeUi(
                id = id,
                name = archetype.name,
                manaTokens = archetype.colorLetters
                    .ifEmpty { extractColorLetters(archetype.colors) }
                    .mapNotNull { colorToManaToken(it) },
                tier = archetype.tier,
                tierLetter = archetype.tier.take(1),
                difficulty = archetype.difficulty,
                winRate = archetype.archetypeWinRate,
                strategy = archetype.strategy.toRichTextOrNull(),
                notes = archetype.notes.toRichTextOrNull(),
                keyCards = archetype.keyCards.mapIndexed { i, card -> card.toUi("$id-key-$i") },
                cardsToAvoid = archetype.cardsToAvoid.mapIndexed { i, card -> card.toUi("$id-avoid-$i") },
            )
        }

        return SetDraftGuideUiModel(
            overview = overview,
            colorRanking = colorRanking,
            mechanics = mechanics,
            archetypes = archetypes,
            unrankedKeyCardGroups = unrankedKeyCardGroups,
        )
    }

    /** Builds the Tier List UI model. Keys are tier-prefixed since a card may sit in several tiers. */
    fun buildTierList(tierList: SetTierList, setCode: String): List<TierGroupUi> =
        tierList.tiers.mapIndexed { tierIndex, tier ->
            TierGroupUi(
                tier = tier.tier,
                label = tier.label,
                description = tier.description.toRichTextOrNull(),
                cards = tier.cards.mapIndexed { i, card ->
                    TierCardUi(
                        key = "tier-$tierIndex-$i-${card.scryfallId}",
                        card = card.toCard(setCode),
                        name = card.name,
                        colors = card.colors,
                    )
                },
            )
        }

    /** Filters tiers by colour (a colourless card counts as "C") and a case-insensitive name query. */
    fun filterTiers(
        tiers: List<TierGroupUi>,
        colorFilter: Set<String>,
        query: String,
    ): List<TierGroupUi> {
        if (colorFilter.isEmpty() && query.isEmpty()) return tiers
        return tiers.mapNotNull { tier ->
            val matching = tier.cards.filter { card ->
                val matchesColor = colorFilter.isEmpty() ||
                    card.colors.ifEmpty { listOf("C") }.any { it in colorFilter }
                val matchesQuery = query.isEmpty() || card.name.contains(query, ignoreCase = true)
                matchesColor && matchesQuery
            }
            if (matching.isEmpty()) null else tier.copy(cards = matching)
        }
    }

    private fun String.toRichText() = GuideRichText(DraftGuideRichTextParser.parse(this))

    private fun String.toRichTextOrNull(): GuideRichText? = takeIf { it.isNotBlank() }?.toRichText()

    private fun colorToManaToken(code: String): String? =
        code.uppercase().takeIf { it in setOf("W", "U", "B", "R", "G", "C") }

    // Handles both "BR" and "{B}{R}" forms, including "C" for colourless.
    private fun extractColorLetters(colorsStr: String): List<String> =
        if (colorsStr.contains("{")) {
            MANA_TOKEN.findAll(colorsStr).map { it.groupValues[1] }.toList()
        } else {
            colorsStr.filter { it in "WUBRGC" }.map { it.toString() }
        }

    private fun MechanicKeyCard.toCard(setCode: String): Card = guideCard(
        scryfallId, name, manaCost, cmc, colors, colorIdentity, typeLine, rarity, imageNormalUri, artCropUri, setCode,
    )

    private fun ArchetypeKeyCard.toCard(setCode: String): Card = guideCard(
        scryfallId, name, manaCost, cmc, colors, colorIdentity, typeLine, rarity, imageNormalUri, artCropUri, setCode,
    )

    // priceUsd carries the GIH win rate; CardRow renders it in the tier list.
    private fun TierCard.toCard(setCode: String): Card = guideCard(
        scryfallId, name, manaCost, cmc, colors, colorIdentity, typeLine, rarity, imageNormalUri, artCropUri, setCode,
    ).copy(oracleText = oracleText, priceUsd = stats?.gihWinRate)

    private fun guideCard(
        scryfallId: String,
        name: String,
        manaCost: String,
        cmc: Double?,
        colors: List<String>,
        colorIdentity: List<String>,
        typeLine: String,
        rarity: String,
        imageNormal: String,
        imageArtCrop: String,
        setCode: String,
    ): Card = Card(
        scryfallId = scryfallId,
        name = name,
        printedName = null,
        manaCost = manaCost,
        cmc = cmc ?: 0.0,
        colors = colors,
        colorIdentity = colorIdentity,
        typeLine = typeLine,
        printedTypeLine = null,
        oracleText = null,
        printedText = null,
        keywords = emptyList(),
        power = null,
        toughness = null,
        loyalty = null,
        setCode = setCode,
        setName = "",
        collectorNumber = "",
        rarity = rarity,
        releasedAt = "",
        frameEffects = emptyList(),
        promoTypes = emptyList(),
        lang = "en",
        imageNormal = imageNormal,
        imageArtCrop = imageArtCrop,
        imageBackNormal = null,
        priceUsd = null,
        priceUsdFoil = null,
        priceEur = null,
        priceEurFoil = null,
        legalityStandard = "legal",
        legalityPioneer = "legal",
        legalityModern = "legal",
        legalityCommander = "legal",
        flavorText = null,
        artist = null,
        scryfallUri = "",
    )
}
