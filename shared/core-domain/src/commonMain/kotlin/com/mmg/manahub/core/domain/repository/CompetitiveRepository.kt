package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.LimitedRatingsSnapshot
import com.mmg.manahub.core.model.MetaSnapshot

/**
 * Contract for fetching MTG competitive-metagame data from the `manahub-competitive` Cloudflare
 * Worker — weekly constructed-format archetype/meta snapshots and 17lands Limited card ratings
 * (Competitive feature, Phase 3).
 *
 * Layering: Room/IndexedDB snapshot cache (24h freshness) -> Worker, degraded-never-dead (a
 * Worker failure with a stale cache present serves the stale snapshot flagged
 * [DataResult.Success.isStale] rather than erroring). Every method is a no-op short-circuit (a
 * [DataResult.Error]) when the Competitive feature flag (`competitiveEnabledFlow`, default OFF)
 * is disabled — callers should check the flag themselves before showing Competitive UI, but the
 * repository defends this invariant too so a misuse never fires a network call while the flag
 * is off.
 */
interface CompetitiveRepository {

    /**
     * Weekly metagame snapshot for one constructed format
     * (`standard`/`modern`/`pioneer`/`legacy`/`vintage`/`pauper`).
     *
     * [MetaSnapshot.archetypes] may legitimately be EMPTY (MTGO/TopDeck/Spicerack sources are
     * all in skip-mode until external registrations are complete) — this is a valid "no data
     * yet" state, never mapped to [DataResult.Error]. Callers should render
     * [MetaSnapshot.sources] to explain why.
     */
    suspend fun getWeeklyMeta(format: String): DataResult<MetaSnapshot>

    /**
     * 17lands Limited card-ratings snapshot for one expansion. [format] is a 17lands draft
     * format id (e.g. `"PremierDraft"`, `"TradDraft"`) and defaults to `"PremierDraft"`, matching
     * the Worker's own server-side default.
     */
    suspend fun getLimitedRatings(setCode: String, format: String = "PremierDraft"): DataResult<LimitedRatingsSnapshot>
}
