package com.mmg.manahub.core.data.remote.edhrec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * EDHREC theme/tag page response (`https://json.edhrec.com/pages/tags/{slug}.json` — unofficial,
 * undocumented, per Deck Engine Unification plan D6). **Verified against a real live response**
 * (2026-07-20, `curl https://json.edhrec.com/pages/tags/aristocrats.json` with a browser
 * User-Agent + Referer — EDHREC 403s requests that look like a bare CLI client). This is NOT a
 * guess: the URL pattern `pages/tags/{slug}` was confirmed live (an earlier guess at
 * `pages/themes/{slug}` consistently 403'd — that path does not exist).
 *
 * **Promoted from `:tools:tag-pipeline`'s `edhrec/EdhrecThemePageDto.kt` to this module (plan §8a
 * addendum, 2026-07-21)** so the app's on-device per-card EDHREC fallback
 * (`EdhrecCardTagEnrichmentSource`) can decode the SAME page shape and reuse [harvestCardSignals]
 * verbatim — zero drift between the offline pipeline's bulk harvest and the app's single-card
 * lookup. Pure `@Serializable` data classes + a pure function, no JVM-only API — safe for
 * `commonMain` (Android + wasmJs). `:tools:tag-pipeline`'s JVM-only `EdhrecThemeClient` (which uses
 * `java.net.http.HttpClient`, NOT promotable to `commonMain`) now imports this type directly via
 * its existing `implementation(project(":shared:core-data"))` dependency.
 *
 * Real cardlists observed on the `aristocrats` tag page: `newcommanders`, `topcommanders` (no
 * `synergy` field — commander picks, not synergy-ranked cards), `newcards`, `highsynergycards`,
 * `topcards`, `gamechangers`, `creatures`, `instants`, `sorceries`, `utilityartifacts`,
 * `enchantments`, `planeswalkers`, `utilitylands`, `manaartifacts`, `lands`, `battles` (per-type
 * breakdowns of the SAME theme, each independently synergy-ranked).
 *
 * **[HARVESTED_CARDLIST_TAGS] harvests every one of those per-type breakdown lists too** (fixed
 * 2026-07-21 — see `project_deck_engine_unification_edhrec_theme_archetype_fix` memory for the
 * full root-cause writeup). Any card that legitimately DOES appear in more than one harvested list
 * is not double-counted or weight-inflated — [harvestCardSignals] keeps only the SINGLE highest
 * weight per card name across every harvested list. `newcommanders`/`topcommanders` stay excluded
 * (no `synergy` field — they rank commanders that build the archetype, not cards that support it).
 */
@Serializable
data class EdhrecThemePageDto(
    @SerialName("header") val header: String? = null,
    @SerialName("container") val container: EdhrecContainerDto? = null,
)

@Serializable
data class EdhrecContainerDto(
    @SerialName("json_dict") val jsonDict: EdhrecJsonDictDto? = null,
)

@Serializable
data class EdhrecJsonDictDto(
    @SerialName("cardlists") val cardlists: List<EdhrecCardlistDto> = emptyList(),
)

@Serializable
data class EdhrecCardlistDto(
    @SerialName("tag") val tag: String? = null,
    @SerialName("cardviews") val cardviews: List<EdhrecCardviewDto> = emptyList(),
)

@Serializable
data class EdhrecCardviewDto(
    @SerialName("name") val name: String? = null,
    /** Absent on `newcommanders`/`topcommanders` cardviews (verified live — those lists rank
     *  commanders by play count, not synergy). Null is a legitimate, expected value here. */
    @SerialName("synergy") val synergy: Float? = null,
    @SerialName("num_decks") val numDecks: Int? = null,
    @SerialName("potential_decks") val potentialDecks: Int? = null,
)

/**
 * The cardlist `tag` values harvested from a theme page — every synergy-ranked list INCLUDING the
 * per-card-type breakdowns (`creatures`, `instants`, `lands`, etc.), excluding only the
 * commander-only lists (`newcommanders`/`topcommanders`, which carry no `synergy` value and are
 * about which commanders build the archetype, not which cards support it).
 */
val HARVESTED_CARDLIST_TAGS: Set<String> = setOf(
    "topcards", "highsynergycards", "gamechangers", "newcards",
    "creatures", "instants", "sorceries", "utilityartifacts", "enchantments",
    "planeswalkers", "utilitylands", "manaartifacts", "lands", "battles",
)

/**
 * One harvested (card name, weight) signal from a theme page. [weight] prefers [synergy] (EDHREC's
 * own staple-dampened score); falls back to `num_decks / potential_decks` (inclusion rate) when
 * `synergy` is absent/zero. Always non-negative — see [harvestCardSignals]'s KDoc for why a
 * negative [synergy] never reaches this type.
 */
data class EdhrecCardSignal(val cardName: String, val weight: Float)

/**
 * Extracts every (card, weight) signal from the [HARVESTED_CARDLIST_TAGS] cardlists of a theme
 * page. Pure — no I/O, unit-testable directly against a fixture [EdhrecThemePageDto]. When the same
 * card appears in more than one harvested cardlist, the HIGHEST weight wins.
 *
 * **Negative `synergy` is treated as "no evidence for this theme/archetype", not as a weight.**
 * EDHREC's `synergy` score is a signed staple-dampened metric — a real, meaningful NEGATIVE value
 * means the card is actually played LESS than the format baseline for that specific theme. Treating
 * that negative number as if it were a positive synergy weight — or silently falling back to
 * `inclusionRate` for it — would inject exactly the wrong signal. So a per-cardview entry whose
 * resolved weight is `<= 0f` is dropped from this list's contribution entirely.
 */
fun harvestCardSignals(page: EdhrecThemePageDto): List<EdhrecCardSignal> {
    val best = LinkedHashMap<String, Float>()
    page.container?.jsonDict?.cardlists.orEmpty()
        .filter { it.tag in HARVESTED_CARDLIST_TAGS }
        .forEach { list ->
            list.cardviews.forEach { view ->
                val name = view.name?.takeIf { it.isNotBlank() } ?: return@forEach
                val weight = view.synergy?.takeIf { it != 0f }
                    ?: inclusionRate(view.numDecks, view.potentialDecks)
                if (weight <= 0f) return@forEach
                val current = best[name]
                if (current == null || weight > current) best[name] = weight
            }
        }
    return best.map { (name, weight) -> EdhrecCardSignal(name, weight) }
}

private fun inclusionRate(numDecks: Int?, potentialDecks: Int?): Float {
    if (numDecks == null || potentialDecks == null || potentialDecks <= 0) return 0f
    return numDecks.toFloat() / potentialDecks.toFloat()
}
