package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.model.CardTag

/**
 * Outcome of a [CardStrategyTagsRepository.getStrategyTags] lookup.
 *
 * Deliberately a 3-way sealed result rather than a boolean/nullable: [NotFound] (this card has no
 * row in the precomputed table yet — a normal, expected outcome for very new cards the offline
 * pipeline has not processed) and [Error] (a genuine fetch/decode failure) are both "no data", but
 * callers may want to treat them differently for telemetry even though both currently fall back to
 * on-device analysis the same way.
 */
sealed class CardStrategyTagsResult {

    /**
     * A precomputed row was found (from cache or a fresh remote fetch).
     *
     * @param tags auto-confirmed [CardTag]s, resolved from the payload's raw string keys via the
     *   local [com.mmg.manahub.core.data.tagging.TagDictionary] (an unresolvable key — a taxonomy
     *   drift between the offline pipeline and the app's current dictionary — is silently dropped
     *   rather than guessed).
     * @param tribes bare tribe words (Deck Engine Unification plan D5/Phase 2 tribal granularity) —
     *   NOT persisted as [CardTag]s (mirrors the app's own rule that `tribe:` fingerprint keys are
     *   runtime-only); exposed here only for callers that want the raw signal.
     * @param isStale true when this [Found] came from an expired cache entry served as a
     *   last-resort fallback after a remote fetch failure.
     */
    data class Found(
        val tags: List<CardTag>,
        val tribes: List<String> = emptyList(),
        val isStale: Boolean = false,
    ) : CardStrategyTagsResult()

    /** No row exists for this `oracle_id` yet (pipeline has not processed this card). */
    object NotFound : CardStrategyTagsResult()

    /** A genuine fetch/decode failure with no usable cache to fall back to. */
    data class Error(val message: String) : CardStrategyTagsResult()
}

/**
 * A device-computed fallback result, ready to be pushed back to the precomputed `card_strategy_tags`
 * table (Deck Engine Unification plan §8a addendum — "strict fallback + device write-back").
 * Mirrors the shape of the offline pipeline's own payload (`tags`/`themes`/`archetypes`/`tribes`)
 * so a device-submitted row is structurally identical to a pipeline-submitted one, just narrower
 * in practice (a single card's on-device analysis can never match the pipeline's bulk EDHREC
 * crawl breadth).
 *
 * @param tags [com.mmg.manahub.core.model.CardTag.key] strings this device confirmed for the card
 *   (on-device rule engine, plus any EDHREC-promoted suggestions).
 * @param themes [com.mmg.manahub.feature.decks.domain.engine.ThemeId.name] → EDHREC synergy
 *   weight, for the (small, capped) set of themes the on-device EDHREC shortlist check confirmed.
 *   Empty when the EDHREC check found nothing (still worth writing back — the rule-engine tags
 *   alone are already better than no row at all).
 * @param archetypes always empty from the device — [com.mmg.manahub.feature.decks.domain.engine
 *   .ArchetypeId] is a whole-deck classification in this codebase, not a per-card concept the
 *   on-device fallback can compute in isolation (see `EdhrecSlugMapping.kt`'s KDoc). Kept as a
 *   field (not dropped) purely to match the payload shape the offline pipeline also writes.
 * @param tribes always empty from the device — no deck context is available at single-card cache
 *   time (tribal identity is derived per-DECK, at runtime, never persisted — see the Phase 2
 *   tribal-granularity decision).
 */
data class CardStrategyTagsSubmission(
    val tags: List<String>,
    val themes: Map<String, Float> = emptyMap(),
    val archetypes: Map<String, Float> = emptyMap(),
    val tribes: List<String> = emptyList(),
)

/**
 * Repository for the Supabase `card_strategy_tags` table (Deck Engine Unification plan, D8,
 * §5 Phase 5c) — the offline tag pipeline's (RUN 5) bulk-precomputed strategy tags, keyed by a
 * card's oracle-wide identity ([com.mmg.manahub.core.model.Card.oracleId]), NOT its per-printing
 * `scryfallId` — tags are an oracle-wide property, shared by every printing/language of a card.
 *
 * Implementations are cache-first (Room on Android, per [com.mmg.manahub.core.data.cache
 * .CardStrategyTagsCache]) so a repeat lookup for the same card never re-hits the network. This
 * is the STRICT primary source (plan §8a addendum): on-device analysis at the call sites that
 * consume this repository only runs on a genuine miss ([CardStrategyTagsResult.NotFound]/
 * [CardStrategyTagsResult.Error]) — this repository never invents tags of its own.
 */
interface CardStrategyTagsRepository {
    suspend fun getStrategyTags(oracleId: String): CardStrategyTagsResult

    /**
     * Pushes a device-computed [submission] back to the precomputed table (plan §8a addendum),
     * so it converges toward completeness from real usage between offline pipeline runs. Fire-
     * and-forget from the caller's perspective: implementations MUST NEVER throw — a failure
     * (offline, rate-limited, RPC rejection) is an expected degraded path, logged internally, and
     * simply means "this device's contribution didn't land this time," never a caller-visible error.
     * A no-op when [oracleId] is blank.
     */
    suspend fun submitStrategyTags(oracleId: String, submission: CardStrategyTagsSubmission)
}
