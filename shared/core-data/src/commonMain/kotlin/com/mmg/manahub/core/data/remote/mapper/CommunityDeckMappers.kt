package com.mmg.manahub.core.data.remote.mapper

import com.mmg.manahub.core.data.remote.dto.ArchidektDeckDetailDto
import com.mmg.manahub.core.data.remote.dto.ArchidektDeckSummaryDto
import com.mmg.manahub.core.data.remote.dto.ArchidektOracleCardDto
import com.mmg.manahub.core.data.remote.dto.ArchidektSearchResultDto
import com.mmg.manahub.core.model.ArchidektFormat
import com.mmg.manahub.core.model.CommunityDeck
import com.mmg.manahub.core.model.CommunityDeckCard
import com.mmg.manahub.core.model.CommunityDeckOwner
import com.mmg.manahub.core.model.CommunityDeckSearchResult
import com.mmg.manahub.core.model.CommunityDeckSummary

/**
 * Maps a fetched Archidekt deck detail DTO to the clean [CommunityDeck] domain model.
 *
 * Only card entries that carry a resolvable oracle card are kept; entries with
 * null [card] or null [oracleCard] are silently filtered out.
 *
 * Entries carrying AT LEAST ONE category flagged `includedInDeck = false` on
 * [ArchidektDeckDetailDto.categories] are marked [CommunityDeckCard.excludedFromDeckCount] = true
 * (bug fix, 2026-07-22, refined 2026-07-22, corrected 2026-07-22 to an ANY-match — see memory
 * `feedback_community_deck_import_reliability_and_atomicity`): Archidekt lets a user flag ANY
 * category (Maybeboard, or an arbitrary custom name like "Bench"/"Cut"/"The Cranberries" — verified
 * live against a 150-deck sample, see `ArchidektCategoryMapping.kt`) as not counted toward the
 * deck, but entries tagged with such a category still appear in the flat [cards] array.
 *
 * The match is deliberately ANY-of, not ALL-of: Archidekt's auto-categorization feature stamps a
 * second, functional/type category (e.g. "Land", "Recursion", "Potion Ingredients") onto most
 * cards regardless of Maybeboard status, so a genuinely-excluded entry routinely carries BOTH the
 * excluded category and an ordinary one (verified live against archidekt.com/decks/1585124 — 15 of
 * 16 Maybeboard entries also carried a second, non-excluded category; only 1 was Maybeboard-only).
 * An ALL-of match almost never fires against real data and was the actual bug: excluded entries
 * with a co-assigned ordinary category kept leaking into the mainboard count.
 *
 * These entries are KEPT (not dropped) — an earlier version of this fix dropped them entirely to
 * fix an over-count bug, but that silently discarded real cards the user expected to see (e.g. a
 * deck's Maybeboard). They are instead recategorized as sideboard-like via
 * [CommunityDeckCard.excludedFromDeckCount] → [CommunityDeckCard.isSideboard], so every consumer
 * that already excludes sideboard cards from the mainboard/deck-size count (display AND import)
 * automatically excludes these too, while the cards themselves remain visible and movable in the
 * Sideboard section instead of vanishing.
 *
 * IMPORTANT: never build a hardcoded allowlist of excluded-category names (`"Maybeboard"`,
 * `"Bench"`, ...) — Archidekt lets users name a category anything, so a fixed list will always miss
 * the next custom name. The `includedInDeck` FLAG on [ArchidektDeckDetailDto.categories] is the only
 * reliable signal.
 */
fun ArchidektDeckDetailDto.toDomain(): CommunityDeck {
    val excludedCategoryNames = categories.filter { !it.includedInDeck }.map { it.name }.toSet()

    val mappedCards = cards
        // Keep only entries that carry a resolvable oracle card.
        .filter { it.card?.oracleCard != null }
        .map { entry ->
            val cardDto = entry.card!!
            val oracle = cardDto.oracleCard!!
            val entryCategories = entry.categories ?: emptyList()
            // Excluded-from-deck (bug fix, 2026-07-22, corrected to ANY-of same day): at least one
            // category this entry carries is flagged `includedInDeck = false`. An entry with no
            // categories is never excluded this way (nothing to exclude it). `any` on an empty
            // list is already `false`, so no separate `isNotEmpty()` guard is needed.
            val excludedFromDeckCount = entryCategories.any { it in excludedCategoryNames }
            CommunityDeckCard(
                name = oracle.name,
                quantity = entry.quantity,
                categories = entryCategories,
                oracleId = oracle.uid,
                // `cardDto.uid` is the Scryfall PRINTING uuid (distinct from `oracle.uid`,
                // the oracle-card uuid shared across printings) — verified live 2026-07-13.
                scryfallId = cardDto.uid,
                manaCost = oracle.manaCost,
                typeLine = buildTypeLine(oracle),
                cmc = oracle.cmc,
                rarity = cardDto.rarity,
                setCode = cardDto.edition?.editioncode ?: "",
                setName = cardDto.edition?.editionname ?: "",
                priceUsd = cardDto.prices?.tcg,
                priceEur = cardDto.prices?.cm,
                excludedFromDeckCount = excludedFromDeckCount,
            )
        }

    val mappedOwner = owner
        ?.let { CommunityDeckOwner(id = it.id, username = it.username, avatarUrl = it.avatar) }
        ?: CommunityDeckOwner(id = 0, username = "Unknown", avatarUrl = "")

    return CommunityDeck(
        archidektId = id,
        name = name,
        description = description,
        format = ArchidektFormat.toManaHubFormat(deckFormat),
        owner = mappedOwner,
        viewCount = viewCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
        featuredImageUrl = customFeatured.takeIf { it.isNotBlank() } ?: featured.takeIf { it.isNotBlank() },
        cards = mappedCards,
        sourceUrl = "https://archidekt.com/decks/$id",
    )
}

/** Maps a single Archidekt search-result entry to the lightweight [CommunityDeckSummary]. */
fun ArchidektDeckSummaryDto.toDomain(): CommunityDeckSummary = CommunityDeckSummary(
    archidektId = id,
    name = name,
    size = size,
    format = ArchidektFormat.toManaHubFormat(deckFormat),
    owner = owner
        ?.let { CommunityDeckOwner(id = it.id, username = it.username, avatarUrl = it.avatar) }
        ?: CommunityDeckOwner(id = 0, username = "Unknown", avatarUrl = ""),
    viewCount = viewCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    colorIdentity = colors.filterValues { it > 0 }.keys.map { colorKey ->
        when (colorKey.lowercase()) {
            "white" -> "W"
            "blue" -> "U"
            "black" -> "B"
            "red" -> "R"
            "green" -> "G"
            else -> colorKey.uppercase() // Fallback to raw if already a letter
        }
    },
    // A user-picked `customFeatured` wins over the automatic `featured` crop when present.
    featuredImageUrl = customFeatured.takeIf { it.isNotBlank() } ?: featured.takeIf { it.isNotBlank() },
)

/** Maps a paged Archidekt search response to the domain [CommunityDeckSearchResult]. */
fun ArchidektSearchResultDto.toDomain(): CommunityDeckSearchResult = CommunityDeckSearchResult(
    totalCount = count,
    hasMore = next != null,
    decks = results.map { it.toDomain() },
)

/**
 * Composes the canonical MTG type line ("Supertype Type — Subtype") from Archidekt's oracle
 * card fields. The em-dash + subtype segment is only appended when [ArchidektOracleCardDto.subTypes]
 * is non-empty (e.g. instants/sorceries have no subtypes to show).
 */
private fun buildTypeLine(oracle: ArchidektOracleCardDto): String {
    val mainPart = (oracle.superTypes + oracle.types).joinToString(" ")
    return if (oracle.subTypes.isNotEmpty()) {
        "$mainPart — ${oracle.subTypes.joinToString(" ")}"
    } else {
        mainPart
    }
}
