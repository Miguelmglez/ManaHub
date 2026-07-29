package com.mmg.manahub.feature.decks.harness

import com.mmg.manahub.core.data.usecase.card.SuggestTagsUseCase
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardFace
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.core.tagging.createStrategyAnalyzer
import com.mmg.manahub.feature.decks.domain.engine.CommanderEligibility
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

// ═══════════════════════════════════════════════════════════════════════════════
//  Wizard Quality Campaign -- Phase H harness fixture loading.
//
//  Loads the real-collection fixtures under `testdata/wizard-harness/` (gitignored, see
//  docs/plans/wizard-quality-campaign.md) and maps them into the app's domain `Card` model
//  FAITHFULLY -- mirroring com.mmg.manahub.core.data.remote.mapper.CardDtoMapper.toDomain()'s
//  conventions (front-face fallback for DFC/split cards, D14 compact WUBRG `producedMana`,
//  oracle_id front-face fallback) -- then runs the PRODUCTION tagging pipeline
//  (SuggestTagsUseCase backed by the real TagDictionary via createStrategyAnalyzer()) over every
//  card so `Card.tags` is populated exactly like a real Scryfall-cache write, since the whole
//  wizard/Doctor scoring engine is tag-driven.
//
//  A local, harness-only @Serializable DTO family is used INSTEAD of the production `CardDto`
//  because the fixture is a deliberately SLIMMED Scryfall response (see fetch_cards.py) that omits
//  several fields CardDto declares non-null (set_name, released_at, scryfall_uri) -- reusing CardDto
//  verbatim would fail to deserialize. Every field this harness DOES have maps 1:1 onto the same
//  Scryfall JSON key (@SerialName) CardDto uses, so this stays a faithful subset, not a reinvention.
//
//  KNOWN FIXTURE LIMITATION: the downloaded fixture carries no `power`/`toughness` field at all
//  (root or per-face) -- confirmed absent across all 620 cards, a gap in fetch_cards.py's Scryfall
//  field selection, not a harness bug. This softens (never blocks) two TRACKED-only signals:
//  ArchetypeRoleClassifier.finisherMatcher/threatEarlyMatcher's structural big-creature/early-threat
//  heuristics fall back to their tag-only paths for every fixture card. None of the campaign's HARD
//  metrics (size/legality/determinism/lands/coherence-cuts/coherence-adds) depend on power/toughness.
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
private data class HarnessCollectionRowDto(
    @SerialName("scryfall_id") val scryfallId: String,
    val quantity: Int = 1,
    @SerialName("is_foil") val isFoil: Boolean = false,
    val language: String = "en",
)

@Serializable
private data class HarnessLegalitiesDto(
    val standard: String = "not_legal",
    val pioneer: String = "not_legal",
    val modern: String = "not_legal",
    val legacy: String = "not_legal",
    val vintage: String = "not_legal",
    val commander: String = "not_legal",
    val pauper: String = "not_legal",
)

@Serializable
private data class HarnessPricesDto(
    val usd: String? = null,
    @SerialName("usd_foil") val usdFoil: String? = null,
    val eur: String? = null,
    @SerialName("eur_foil") val eurFoil: String? = null,
)

@Serializable
private data class HarnessCardFaceDto(
    val name: String,
    @SerialName("oracle_id") val oracleId: String? = null,
    @SerialName("mana_cost") val manaCost: String? = null,
    @SerialName("type_line") val typeLine: String? = null,
    @SerialName("oracle_text") val oracleText: String? = null,
    val colors: List<String>? = null,
)

@Serializable
private data class HarnessCardDto(
    val id: String,
    @SerialName("oracle_id") val oracleId: String? = null,
    val name: String,
    val lang: String = "en",
    @SerialName("mana_cost") val manaCost: String? = null,
    val cmc: Double? = null,
    @SerialName("type_line") val typeLine: String? = null,
    @SerialName("oracle_text") val oracleText: String? = null,
    val colors: List<String>? = null,
    @SerialName("color_identity") val colorIdentity: List<String> = emptyList(),
    @SerialName("produced_mana") val producedMana: List<String>? = null,
    val keywords: List<String> = emptyList(),
    val legalities: HarnessLegalitiesDto = HarnessLegalitiesDto(),
    val rarity: String = "common",
    val set: String = "",
    @SerialName("collector_number") val collectorNumber: String = "",
    @SerialName("edhrec_rank") val edhrecRank: Int? = null,
    val prices: HarnessPricesDto = HarnessPricesDto(),
    val layout: String? = null,
    @SerialName("card_faces") val cardFaces: List<HarnessCardFaceDto>? = null,
)

/** Canonical WUBRG order -- mirrors CardDtoMapper's own private constant (D14). */
private val WUBRG_ORDER = listOf("W", "U", "B", "R", "G")

private fun List<String>?.toCompactWubrg(): String {
    if (isNullOrEmpty()) return ""
    val present = map { it.uppercase() }.toSet()
    return WUBRG_ORDER.filter { it in present }.joinToString("")
}

/** Mirrors CardDtoMapper.toDomain() field-for-field, adapted to this harness-local DTO's narrower
 * (slimmed-fixture) field set. Fields the fixture never carries (printedName/printedText/images/
 * flavorText/artist/relatedUris/purchaseUris/gameChanger/pennyRank/power/toughness/loyalty) are left
 * at their safe defaults -- none of them drive the wizard/Doctor scoring engine. */
private fun HarnessCardDto.toDomainCard(): Card {
    val front = cardFaces?.firstOrNull()
    return Card(
        scryfallId = id,
        name = name,
        printedName = null,
        manaCost = manaCost ?: front?.manaCost,
        cmc = cmc ?: 0.0,
        colors = colors ?: front?.colors ?: emptyList(),
        colorIdentity = colorIdentity,
        typeLine = typeLine ?: front?.typeLine ?: "",
        printedTypeLine = null,
        oracleText = oracleText ?: front?.oracleText,
        printedText = null,
        keywords = keywords,
        power = null,
        toughness = null,
        loyalty = null,
        setCode = set,
        setName = set.uppercase(),
        collectorNumber = collectorNumber,
        rarity = rarity,
        releasedAt = "2000-01-01",
        frameEffects = emptyList(),
        promoTypes = emptyList(),
        lang = lang,
        imageNormal = null,
        imageArtCrop = null,
        imageBackNormal = null,
        priceUsd = prices.usd?.toDoubleOrNull(),
        priceUsdFoil = prices.usdFoil?.toDoubleOrNull(),
        priceEur = prices.eur?.toDoubleOrNull(),
        priceEurFoil = prices.eurFoil?.toDoubleOrNull(),
        legalityStandard = legalities.standard,
        legalityPioneer = legalities.pioneer,
        legalityModern = legalities.modern,
        legalityCommander = legalities.commander,
        legalityLegacy = legalities.legacy,
        legalityVintage = legalities.vintage,
        legalityPauper = legalities.pauper,
        flavorText = null,
        artist = null,
        scryfallUri = "https://scryfall.com/card/$set/$collectorNumber",
        isStale = false,
        staleReason = null,
        cachedAt = 0L,
        tags = emptyList(),
        userTags = emptyList(),
        suggestedTags = emptyList(),
        relatedUris = emptyMap(),
        purchaseUris = emptyMap(),
        gameChanger = false,
        edhrecRank = edhrecRank,
        pennyRank = null,
        cardFaces = cardFaces?.map { face ->
            CardFace(
                name = face.name,
                printedName = null,
                manaCost = face.manaCost,
                typeLine = face.typeLine,
                oracleText = face.oracleText,
                power = null,
                toughness = null,
                loyalty = null,
                defense = null,
                flavorText = null,
                imageNormal = null,
                imageArtCrop = null,
            )
        },
        producedMana = producedMana.toCompactWubrg(),
        oracleId = oracleId ?: front?.oracleId ?: "",
    )
}

/** The loaded, tagged fixture data every matrix spec is built against. */
data class HarnessFixtures(
    /** One [UserCardWithCard] per `collection_raw.json` row that resolved to a known card (a row
     * whose `scryfall_id` is missing from `cards.json` is dropped and counted in [unresolvedRows] --
     * a fixture-integrity signal, not a build failure). */
    val collection: List<UserCardWithCard>,
    /** All 620 fixture cards (tagged), keyed by Scryfall id. */
    val cardsById: Map<String, Card>,
    /** All 620 fixture cards (tagged), keyed by exact name -- mirrors how [FakeCardRepository]
     * resolves gap-suggestion/basic-land lookups (`getCardByExactName`). Foil/regular printings of
     * the SAME name collapse onto one representative Card (arbitrary pick — every attribute this
     * engine reads is identical across printings). */
    val cardsByName: Map<String, Card>,
    /** Plains/Island/Swamp/Mountain/Forest/Wastes, tagged, keyed by name. */
    val basicsByName: Map<String, Card>,
    val unresolvedRows: Int,
) {
    /** Every OWNED card (one Card per distinct name), the pool [CollectionProfileUseCase]/the
     * wizard's Direction step would see. */
    val ownedCardsByName: List<Card> get() = cardsByName.values.toList()

    /** Commander-eligible cards in the owned pool (D-G, [CommanderEligibility]) -- the wizard's
     * commander-candidate pool. */
    val commanderCandidates: List<Card> get() = ownedCardsByName.filter(CommanderEligibility::isCommanderEligible)
}

/** Loads + tags the Wizard Quality Campaign fixtures. Pure, deterministic, no network -- every
 * lookup is a local file read + the offline tagging engine. */
object FixtureLoader {

    private val json = Json { ignoreUnknownKeys = true }
    private val suggestTagsUseCase = SuggestTagsUseCase(createStrategyAnalyzer())

    /** Walks up from the JVM working directory looking for `testdata/wizard-harness/` -- the exact
     * cwd Gradle uses for `:app` unit tests is not asserted anywhere else in this codebase, so this
     * is deliberately defensive rather than hardcoding one assumption. Returns null (never throws)
     * when not found -- callers use `org.junit.Assume` to SKIP (not fail) on a machine/CI run with
     * no fixtures checked out (the directory is gitignored, real user data). */
    fun locateHarnessDir(): File? {
        var dir = File(".").absoluteFile.normalize()
        repeat(6) {
            val candidate = File(dir, "testdata/wizard-harness")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile ?: return null
        }
        return null
    }

    fun load(harnessDir: File): HarnessFixtures {
        val collectionRows: List<HarnessCollectionRowDto> =
            json.decodeFromString(File(harnessDir, "collection_raw.json").readText())
        val cardDtos: List<HarnessCardDto> =
            json.decodeFromString(File(harnessDir, "cards.json").readText())
        val basicDtos: List<HarnessCardDto> =
            json.decodeFromString(File(harnessDir, "basic_lands.json").readText())

        val cardsById = cardDtos.associate { it.id to tagged(it.toDomainCard()) }
        // Multiple printings can share a name (e.g. an English + a foil row of the same card) --
        // when several fixture rows collapse onto one name, prefer the LOWEST collector number's
        // card deterministically (arbitrary-but-stable; every scoring-relevant attribute is
        // identical across printings of the same name per CollectionProfileUseCase's own KDoc).
        val cardsByName = cardDtos
            .groupBy { it.name }
            .mapValues { (_, dtos) -> tagged(dtos.minBy { it.collectorNumber }.toDomainCard()) }
        val basicsByName = basicDtos.associate { it.name to tagged(it.toDomainCard()) }

        var unresolved = 0
        val collection = collectionRows.mapNotNull { row ->
            val card = cardsById[row.scryfallId]
            if (card == null) {
                unresolved++
                null
            } else {
                UserCardWithCard(
                    userCard = UserCard(
                        id = "uc-${row.scryfallId}",
                        scryfallId = row.scryfallId,
                        quantity = row.quantity,
                        isFoil = row.isFoil,
                        language = row.language,
                    ),
                    card = card,
                )
            }
        }

        return HarnessFixtures(
            collection = collection,
            cardsById = cardsById,
            cardsByName = cardsByName,
            basicsByName = basicsByName,
            unresolvedRows = unresolved,
        )
    }

    /** Runs the SAME four-analyzer pipeline production runs when caching a freshly-fetched Scryfall
     * card (see CardRepositoryImpl's background scheduleTagResolution / ResolveCardStrategyTagsUseCase,
     * Deck Engine Unification plan D8/§5 Phase 5c) -- Keyword + TypeLine + Strategy (real TagDictionary)
     * + GameChanger, split into confirmed/suggested by the default thresholds. */
    private fun tagged(card: Card): Card {
        val result = suggestTagsUseCase(card)
        return card.copy(tags = result.confirmed, suggestedTags = result.suggested)
    }
}
