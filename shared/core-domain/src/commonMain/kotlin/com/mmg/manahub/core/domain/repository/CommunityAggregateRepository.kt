package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.model.CommunityAggregate
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.TrendingSnapshot

/**
 * Contract for fetching community deck aggregates (Commander via EDHREC, 60-card via
 * Archidekt) from the `manahub-community` Cloudflare Worker — Deck Doctor Motor B / Community
 * Hub data source (Phase 3/4 of `docs/claude-code-prompt-deck-doctor-community.md`; D3/D16).
 *
 * Layering (Phase 3.3): Room cache snapshot (7-day [com.mmg.manahub.core.data.repository.CachePolicy])
 * -> Worker -> a reduced-sample direct-Archidekt fallback for the 60-card path only (see
 * implementation KDoc for why the Commander path has no meaningful direct fallback).
 * Every method is a no-op short-circuit (a [DataResult.Error]) when the community engine
 * feature flag (D4: `communityEngineEnabledFlow`, default OFF) is disabled — callers should
 * check the flag themselves before showing Motor B UI, but the repository defends this
 * invariant too so a misuse never fires a network call while the flag is off.
 */
interface CommunityAggregateRepository {

    /**
     * Commander suggestion aggregate (EDHREC-backed per D16): per-card inclusion/synergy,
     * average type distribution + mana curve, theme tags, similar commanders, Game Changers
     * count.
     */
    suspend fun getCommanderAggregate(commanderName: String): DataResult<CommunityAggregate.Commander>

    /**
     * 60-card aggregate (Archidekt-backed per D16), keyed by the deck's 2-3 most distinctive
     * signature cards (lowest global frequency, deterministic ordering — callers should pick
     * these client-side, e.g. via the deck's own card-frequency data, before calling this).
     *
     * May return [CommunityAggregate.Sixty.Building] while the Worker's incremental sample
     * build is in progress — callers should re-poll with backoff, never treat this as an
     * error.
     */
    suspend fun getSixtyAggregate(
        signatureCards: List<String>,
        format: Int,
    ): DataResult<CommunityAggregate.Sixty>

    /** Pre-ranked "decks like yours" commander suggestions (client re-ranks per Phase 4). */
    suspend fun getSimilarDecks(commanderName: String, limit: Int = 10): DataResult<List<String>>

    /** Anonymous weekly trending cards/commanders. `week` defaults to the current ISO week. */
    suspend fun getTrending(week: String? = null): DataResult<TrendingSnapshot>
}
