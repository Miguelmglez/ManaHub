package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.feature.decks.presentation.engine.DeckEvaluation
import com.mmg.manahub.feature.decks.presentation.engine.DeckProfile
import com.mmg.manahub.feature.decks.presentation.engine.DeckRole
import com.mmg.manahub.feature.decks.presentation.engine.ManaColor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Builds an EXTERNAL (Scryfall) candidate pool of cards the deck does not yet own, targeted at the
 * deck's unfilled role gaps.
 *
 * ## Why no Room cache / migration
 * The plan originally called for caching the pool in Room with a TTL. That is intentionally
 * **deferred**: [CardRepository.searchWithRawQuery] already routes every call through the
 * rate-limited `ScryfallRequestQueue` AND is backed by an in-memory `TimedLruCache` with a 5-minute
 * TTL (`ScryfallCache.searches`). Re-querying the same role within that window is a cache hit, so a
 * third Room migration on this branch is unnecessary. Persistent pool caching can be revisited later.
 *
 * ## Query safety
 * Every query fragment is assembled from CONTROLLED inputs only — color symbols from
 * [ManaColor], a [DeckRole] enum value mapped to a fixed oracle/type allowlist, the deck format, and
 * a numeric price cap. **No user free text is ever interpolated** into a Scryfall query, so the
 * queries cannot be used to smuggle arbitrary search operators.
 *
 * ## Rate-limit discipline
 * Exactly ONE query is issued per role gap (capped by [MAX_QUERIES]), keeping the burst well within
 * the queue's ≤10 req/s budget. Results are merged, de-duplicated by `scryfallId` and sorted by
 * `edhrecRank` (nulls last, i.e. the most-played cards first).
 */
class CandidatePoolGenerator @Inject constructor(
    private val cardRepository: CardRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * @param profile the deck profile (supplies color identity + format).
     * @param evaluation the deck evaluation (its [DeckEvaluation.roleCoverage] supplies the gaps).
     * @param usdCap an optional LOOSE per-card USD cap appended as `usd<=` to pre-trim expensive
     *        results before the exact € filtering happens in [BudgetOptimizer]. Null = no pre-filter.
     * @param perRoleLimit maximum cards kept per role query after the Scryfall response is parsed.
     * @return external candidate cards (not de-duplicated against the collection here — the caller
     *         merges sources and resolves origin priority), sorted by EDHREC rank (nulls last).
     */
    suspend operator fun invoke(
        profile: DeckProfile,
        evaluation: DeckEvaluation,
        usdCap: Double? = null,
        perRoleLimit: Int = PER_ROLE_LIMIT,
    ): List<Card> = withContext(ioDispatcher) {
        // Only roles that are actually under-covered are worth querying for.
        val gapRoles = evaluation.roleCoverage
            .filter { it.gap > 0 }
            .map { it.role }
            .filter { it.queryFragment() != null }
            .distinct()
            .take(MAX_QUERIES)

        if (gapRoles.isEmpty()) return@withContext emptyList()

        val identityFragment = colorIdentityFragment(profile.colorIdentity)
        val legalFragment = legalityFragment(profile)
        val budgetFragment = usdCap?.let { "usd<=${formatCap(it)}" }

        val merged = LinkedHashMap<String, Card>()
        for (role in gapRoles) {
            val query = buildQuery(
                identityFragment = identityFragment,
                legalFragment = legalFragment,
                roleFragment = role.queryFragment()!!,
                budgetFragment = budgetFragment,
            )
            // Best-effort per role: a single failed/empty Scryfall query must not abort the others.
            val cards = runCatching { cardRepository.searchWithRawQuery(query) }
                .getOrDefault(emptyList())
                .take(perRoleLimit)
            cards.forEach { card -> merged.putIfAbsent(card.scryfallId, card) }
        }

        merged.values.sortedWith(
            // Lower EDHREC rank = more played → first. Unknown rank sorts last.
            compareBy(nullsLast()) { it.edhrecRank }
        )
    }

    /**
     * Assembles a single, well-formed Scryfall query string from controlled fragments. Order is
     * fixed (identity, legality, role, budget, exclude-basics) purely for readable/testable output.
     */
    private fun buildQuery(
        identityFragment: String?,
        legalFragment: String,
        roleFragment: String,
        budgetFragment: String?,
    ): String = buildList {
        identityFragment?.let { add(it) }
        add(legalFragment)
        add(roleFragment)
        budgetFragment?.let { add(it) }
        add(EXCLUDE_BASICS)
    }.joinToString(separator = " ")

    /**
     * `id<={WUBRG}` restricts results to cards WITHIN the commander color identity. For an empty
     * identity (no restriction) the fragment is omitted entirely.
     */
    private fun colorIdentityFragment(identity: Set<ManaColor>): String? {
        // ManaColor.C carries no WUBRG letter; only the five colors restrict identity.
        val symbols = identity.mapNotNull { it.wubrgSymbolOrNull() }.sorted().joinToString(separator = "")
        return if (symbols.isEmpty()) null else "id<=$symbols"
    }

    private fun legalityFragment(profile: DeckProfile): String = when (profile.format) {
        com.mmg.manahub.core.domain.model.DeckFormat.COMMANDER -> "legal:commander"
        com.mmg.manahub.core.domain.model.DeckFormat.STANDARD -> "legal:standard"
        // Draft/limited has no Scryfall legality token; fall back to a broad, always-true filter.
        com.mmg.manahub.core.domain.model.DeckFormat.DRAFT -> "legal:commander"
    }

    /** Renders a numeric cap without locale decimal separators (Scryfall expects a dot). */
    private fun formatCap(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.2f", value)

    private fun ManaColor.wubrgSymbolOrNull(): String? = when (this) {
        ManaColor.W, ManaColor.U, ManaColor.B, ManaColor.R, ManaColor.G -> symbol
        ManaColor.C -> null
    }

    private companion object {
        /** One query per gap role; hard cap keeps the burst small against the rate limiter. */
        const val MAX_QUERIES = 6
        const val PER_ROLE_LIMIT = 25
        const val EXCLUDE_BASICS = "-t:basic"
    }
}

/**
 * Maps a [DeckRole] to a fixed, allowlisted Scryfall oracle/type fragment. Returns null for roles
 * that have no meaningful generic query (LAND, FILLER, PAYOFF, SYNERGY, THREAT) — those are too
 * deck-specific to fetch generically and are better served by the collection/wishlist sources.
 *
 * The fragments below are CONSTANTS — never interpolate dynamic text here.
 */
internal fun DeckRole.queryFragment(): String? = when (this) {
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
