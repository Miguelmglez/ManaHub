package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.feature.decks.domain.engine.DeckEvaluation
import com.mmg.manahub.feature.decks.domain.engine.DeckProfile
import com.mmg.manahub.feature.decks.domain.engine.DeckRole
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.TribeDeriver
import com.mmg.manahub.feature.decks.domain.usecase.CandidatePoolGenerator.Companion.MAX_QUERIES
import com.mmg.manahub.feature.decks.domain.usecase.CandidatePoolGenerator.Companion.STRATEGY_OTAGS
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Builds an EXTERNAL (Scryfall) candidate pool of cards the deck does not yet own, targeted at the
 * deck's unfilled role gaps and its dominant strategy/tribe.
 *
 * ## Why no Room cache / migration
 * The plan originally called for caching the pool in Room with a TTL. That is intentionally
 * **deferred**: [CardRepository.searchWithRawQuery] already routes every call through the
 * rate-limited `ScryfallRequestQueue` AND is backed by an in-memory `TimedLruCache` with a 5-minute
 * TTL (`ScryfallCache.searches`). Re-querying the same role within that window is a cache hit, so a
 * third Room migration on this branch is unnecessary. Persistent pool caching can be revisited later.
 *
 * ## Oracle tags + substring fallback (plan E2)
 * The role queries now lead with Scryfall's curated **oracle tags** (`otag:removal`, `otag:ramp`,
 * `otag:board-wipe`, `otag:card-draw`, `otag:counterspell`, `otag:tutor`). Oracle tags have far
 * higher recall/precision than the old naive oracle substrings AND they are NOT part of Scryfall's
 * formally documented grammar — a server-side change could start rejecting them. So every otag query
 * is **defensive**: the primary `otag:` query is issued first, and ONLY if it errors or returns empty
 * does the role fall back to its legacy substring fragment ([DeckRole.fallbackQueryFragment]). The
 * substrings are demoted to a safety net, never deleted.
 *
 * ## Strategy & tribe queries (plan E3)
 * PAYOFF/SYNERGY/THREAT have no meaningful generic role query, so before Phase 6 they produced NO
 * external suggestions at all — exactly the deck-defining roles that most want them. We now derive up
 * to two extra queries from the deck profile:
 *  - the deck's top strategy fingerprint key → `otag:<strategy>` via a fixed [STRATEGY_OTAGS]
 *    allowlist (e.g. `lifegain` → `otag:lifegain`), and
 *  - the deck's dominant derived tribe (`tribe:<x>` fingerprint key, Phase 2) → `t:<tribe>` after
 *    sanitising the tribe token to letters only.
 *
 * ## Query safety (CLAUDE.md)
 * Every query fragment is assembled from CONTROLLED inputs only — color symbols from [ManaColor], a
 * [DeckRole] enum mapped to a fixed oracle/type allowlist, strategy keys mapped through the constant
 * [STRATEGY_OTAGS] allowlist, a tribe token sanitised to `[a-z]` only, the deck format, and a numeric
 * price cap. **No user free text is ever interpolated** into a Scryfall query, so the queries cannot
 * be used to smuggle arbitrary search operators.
 *
 * ## Rate-limit discipline
 * At most [MAX_QUERIES] role/strategy/tribe queries are issued in one burst (plus at most one fallback
 * re-query per role whose otag came back empty), keeping the burst well within the queue's ≤10 req/s
 * budget. Results are merged, de-duplicated by `scryfallId` and sorted by `edhrecRank` (nulls last).
 */
class CandidatePoolGenerator @Inject constructor(
    private val cardRepository: CardRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * @param profile the deck profile (supplies color identity + format + strategy/tribe fingerprint).
     * @param evaluation the deck evaluation (its [DeckEvaluation.roleCoverage] supplies the gaps).
     * @param usdCap an optional LOOSE per-card USD cap appended as `usd<=` to pre-trim expensive
     *        results before the exact € filtering happens in [BudgetOptimizer]. Null = no pre-filter.
     * @param perRoleLimit maximum cards kept per query after the Scryfall response is parsed.
     * @return external candidate cards (not de-duplicated against the collection here — the caller
     *         merges sources and resolves origin priority), sorted by EDHREC rank (nulls last).
     */
    suspend operator fun invoke(
        profile: DeckProfile,
        evaluation: DeckEvaluation,
        usdCap: Double? = null,
        perRoleLimit: Int = PER_ROLE_LIMIT,
    ): List<Card> = withContext(ioDispatcher) {
        // Only roles that are actually under-covered AND have a queryable fragment are worth a query.
        val gapRoles = evaluation.roleCoverage
            .filter { it.gap > 0 }
            .map { it.role }
            .filter { it.queryFragment() != null }
            .distinct()

        // Strategy/tribe queries cover the deck-defining roles (PAYOFF/SYNERGY/THREAT) that have no
        // generic role query (plan E3). Derived purely from the profile via fixed allowlists.
        val strategyFragment = strategyFragment(profile)
        val tribeFragment = tribeFragment(profile)
        val extraFragments = listOfNotNull(strategyFragment, tribeFragment)

        if (gapRoles.isEmpty() && extraFragments.isEmpty()) return@withContext emptyList()

        val identityFragment = colorIdentityFragment(profile.colorIdentity)
        val legalFragment = legalityFragment(profile)
        val budgetFragment = usdCap?.let { "usd<=${formatCap(it)}" }

        // Build the ordered list of role fragment plans (primary otag + substring fallback), capped.
        // Strategy/tribe queries have no fallback (the otag/type token IS the query), so they carry a
        // null fallback. The hard cap counts every distinct fragment plan, role + strategy + tribe.
        val plans = buildList {
            gapRoles.forEach { role -> add(RoleFragmentPlan(role.queryFragment()!!, role.fallbackQueryFragment())) }
            extraFragments.forEach { add(RoleFragmentPlan(it, fallback = null)) }
        }.take(MAX_QUERIES)

        val merged = LinkedHashMap<String, Card>()
        for (plan in plans) {
            // Primary query (otag / strategy / tribe). Best-effort: a single failed query must not
            // abort the others.
            val primaryQuery = buildQuery(
                identityFragment = identityFragment,
                legalFragment = legalFragment,
                roleFragment = plan.primary,
                budgetFragment = budgetFragment,
            )
            val primaryResult = runCatching { cardRepository.searchWithRawQuery(primaryQuery) }
            val primaryCards = primaryResult.getOrDefault(emptyList())

            // E2 fallback: when the otag query ERRORS or returns EMPTY, retry with the legacy
            // substring fragment (the tags are not in the formal grammar — be defensive). Strategy/
            // tribe plans carry no fallback, so they simply contribute their (possibly empty) result.
            val cards = if (primaryResult.isFailure || primaryCards.isEmpty()) {
                plan.fallback?.let { fb ->
                    val fallbackQuery = buildQuery(
                        identityFragment = identityFragment,
                        legalFragment = legalFragment,
                        roleFragment = fb,
                        budgetFragment = budgetFragment,
                    )
                    runCatching { cardRepository.searchWithRawQuery(fallbackQuery) }.getOrDefault(emptyList())
                } ?: primaryCards
            } else {
                primaryCards
            }

            cards.take(perRoleLimit).forEach { card -> merged.putIfAbsent(card.scryfallId, card) }
        }

        // D4 — pool ordering. Commander keeps the EDHREC pre-sort (a meaningful popularity signal
        // for EDH). Constructed formats DROP the EDH-centric pre-sort — it biases a Standard/Modern
        // pool toward Commander staples — and leave the pool in merge (query) order; the downstream
        // DeckScorer.rankAdds then sorts by FIT score, which is the format-appropriate ranking.
        val cards = merged.values.toList()
        if (profile.format == com.mmg.manahub.core.domain.model.DeckFormat.COMMANDER) {
            cards.sortedWith(
                // Lower EDHREC rank = more played → first. Unknown rank sorts last.
                compareBy(nullsLast()) { it.edhrecRank }
            )
        } else {
            cards
        }
    }

    /** A primary fragment with an optional legacy substring fallback (plan E2). */
    private data class RoleFragmentPlan(val primary: String, val fallback: String?)

    /**
     * Assembles a single, well-formed Scryfall query string from controlled fragments. Order is
     * fixed (identity, legality, role, budget, exclude-basics) purely for readable/testable output.
     */
    private fun buildQuery(
        identityFragment: String?,
        legalFragment: String?,
        roleFragment: String,
        budgetFragment: String?,
    ): String = buildList {
        identityFragment?.let { add(it) }
        legalFragment?.let { add(it) }
        add(roleFragment)
        budgetFragment?.let { add(it) }
        add(EXCLUDE_BASICS)
    }.joinToString(separator = " ")

    /**
     * The deck's top STRATEGY fingerprint key mapped to a fixed `otag:<strategy>` via [STRATEGY_OTAGS]
     * (plan E3). Returns null when the dominant non-tribe key is not in the allowlist (no guessing).
     *
     * The tribe keys (Phase 2 `tribe:<x>`) are intentionally EXCLUDED here — they have their own
     * `t:<tribe>` query in [tribeFragment]; the strategy query targets the *non-tribal* archetype.
     */
    private fun strategyFragment(profile: DeckProfile): String? {
        val topStrategyKey = profile.tagFingerprint
            .filterKeys { !it.startsWith(TribeDeriver.TRIBE_PREFIX) && it in STRATEGY_OTAGS }
            .maxByOrNull { it.value }
            ?.key
            ?: return null
        // STRATEGY_OTAGS values are constants — never interpolate dynamic text here.
        return STRATEGY_OTAGS[topStrategyKey]
    }

    /**
     * The deck's dominant derived tribe (highest-weight `tribe:<x>` fingerprint key, Phase 2) mapped
     * to a `t:<tribe>` type query (plan E3). The tribe token is sanitised to LETTERS ONLY before
     * interpolation so no operator characters can leak in (the subtype keys are already letters-only,
     * but the sanitisation is the explicit query-safety guard required by CLAUDE.md).
     */
    private fun tribeFragment(profile: DeckProfile): String? {
        val topTribeKey = profile.tagFingerprint
            .filterKeys { it.startsWith(TribeDeriver.TRIBE_PREFIX) }
            .maxByOrNull { it.value }
            ?.key
            ?: return null
        val tribe = topTribeKey.removePrefix(TribeDeriver.TRIBE_PREFIX).filter { it.isLetter() }.lowercase()
        return if (tribe.isEmpty()) null else "t:$tribe"
    }

    /**
     * `id<={WUBRG}` restricts results to cards WITHIN the commander color identity. For an empty
     * identity (no restriction) the fragment is omitted entirely.
     */
    private fun colorIdentityFragment(identity: Set<ManaColor>): String? {
        // ManaColor.C carries no WUBRG letter; only the five colors restrict identity.
        val symbols = identity.mapNotNull { it.wubrgSymbolOrNull() }.sorted().joinToString(separator = "")
        return if (symbols.isEmpty()) null else "id<=$symbols"
    }

    private fun legalityFragment(profile: DeckProfile): String? = when (profile.format) {
        com.mmg.manahub.core.domain.model.DeckFormat.COMMANDER -> "legal:commander"
       /* com.mmg.manahub.core.domain.model.DeckFormat.STANDARD -> "legal:standard"
        com.mmg.manahub.core.domain.model.DeckFormat.PIONEER -> "legal:pioneer"
        com.mmg.manahub.core.domain.model.DeckFormat.MODERN -> "legal:modern"
        com.mmg.manahub.core.domain.model.DeckFormat.LEGACY -> "legal:legacy"
        com.mmg.manahub.core.domain.model.DeckFormat.VINTAGE -> "legal:vintage"
        com.mmg.manahub.core.domain.model.DeckFormat.PAUPER -> "legal:pauper"*/
        // Casual and Draft/limited have no universal Scryfall legality token; omit the fragment.
        com.mmg.manahub.core.domain.model.DeckFormat.CASUAL,
        com.mmg.manahub.core.domain.model.DeckFormat.DRAFT -> null
    }

    /** Renders a numeric cap without locale decimal separators (Scryfall expects a dot). */
    private fun formatCap(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.2f", value)

    private fun ManaColor.wubrgSymbolOrNull(): String? = when (this) {
        ManaColor.W, ManaColor.U, ManaColor.B, ManaColor.R, ManaColor.G -> symbol
        ManaColor.C -> null
    }

    private companion object {
        /**
         * Hard cap on the number of distinct fragment plans (role + strategy + tribe) issued per
         * generation. Raised to 8 (plan E3) to make room for the strategy + tribe queries on top of
         * the role gaps — still a single burst well under the queue's 10 req/s budget even when each
         * plan also fires its substring fallback.
         */
        const val MAX_QUERIES = 8
        const val PER_ROLE_LIMIT = 25
        const val EXCLUDE_BASICS = "-t:basic"

        /**
         * Fixed allowlist mapping a STRATEGY fingerprint key (a [com.mmg.manahub.core.domain.model.CardTag]
         * STRATEGY key) to its Scryfall oracle tag. Only strategies with a well-known, verified oracle
         * tag are included — any other dominant strategy simply produces no extra query (we never guess
         * an `otag:` from an arbitrary key). CONSTANTS only — the query-safety guard depends on this map
         * being closed.
         */
        val STRATEGY_OTAGS: Map<String, String> = mapOf(
            "lifegain" to "otag:lifegain",
            "tokens" to "otag:tokens",
            "sacrifice" to "otag:sacrifice",
            "plus_counters" to "otag:plus-counters",
            "proliferate" to "otag:proliferate",
            "graveyard" to "otag:graveyard",
            "burn" to "otag:burn",
            "blink" to "otag:flicker",
        )
    }
}

/**
 * Maps a [DeckRole] to a fixed, allowlisted Scryfall query fragment. Leads with a curated **oracle
 * tag** (`otag:`) — far higher recall/precision than the old naive oracle substrings (plan E2).
 * Returns null for roles that have no meaningful generic query (LAND, FILLER, PAYOFF, SYNERGY,
 * THREAT) — those are too deck-specific to fetch generically and are served by the strategy/tribe
 * queries ([CandidatePoolGenerator.strategyFragment]/[CandidatePoolGenerator.tribeFragment]) plus the
 * collection/wishlist sources.
 *
 * The fragments below are CONSTANTS — never interpolate dynamic text here.
 */
internal fun DeckRole.queryFragment(): String? = when (this) {
    DeckRole.BOARD_WIPE -> "otag:board-wipe"
    DeckRole.SPOT_REMOVAL -> "otag:removal"
    DeckRole.CARD_ADVANTAGE -> "otag:card-advantage"
    DeckRole.RAMP -> "otag:ramp"
    DeckRole.INTERACTION -> "otag:counterspell"
    DeckRole.TUTOR -> "otag:tutor"
    DeckRole.PAYOFF,
    DeckRole.SYNERGY,
    DeckRole.THREAT,
    DeckRole.LAND,
    DeckRole.FILLER -> null
}

/**
 * Legacy oracle-substring fragment for a [DeckRole], used as the FALLBACK when the primary
 * [queryFragment] `otag:` query errors or returns empty (plan E2). Oracle tags are not part of
 * Scryfall's documented grammar, so this substring net catches the case where a tag is rejected or
 * renamed server-side. Mirrors the role set of [queryFragment]; CONSTANTS only.
 */
internal fun DeckRole.fallbackQueryFragment(): String? = when (this) {
    DeckRole.BOARD_WIPE -> "(o:\"destroy all\" OR o:\"each player sacrifices\")"
    DeckRole.SPOT_REMOVAL -> "(o:\"destroy target\" OR o:\"exile target\")"
    DeckRole.CARD_ADVANTAGE -> "o:\"draw a card\""
    DeckRole.RAMP -> "(o:\"add {\" OR o:\"search your library for a basic land\")"
    DeckRole.INTERACTION -> "o:\"counter target\""
    DeckRole.TUTOR -> "o:\"search your library for a card\""
    DeckRole.PAYOFF,
    DeckRole.SYNERGY,
    DeckRole.THREAT,
    DeckRole.LAND,
    DeckRole.FILLER -> null
}
