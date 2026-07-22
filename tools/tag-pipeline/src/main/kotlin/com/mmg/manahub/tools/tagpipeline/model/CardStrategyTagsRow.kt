package com.mmg.manahub.tools.tagpipeline.model

import kotlinx.serialization.Serializable

/**
 * One JSONL output row of the offline tag pipeline (Deck Engine Unification plan, RUN 5 / D5,
 * plan §5 Phase 5a). Mirrors the plan's own schema verbatim:
 * `{ oracle_id, tags, themes, archetypes, tribes, sources, generated_at, pipeline_version }`.
 *
 * This is the payload shape the parallel `backend-supabase-expert` agent's `card_strategy_tags`
 * table (plan §5 Phase 5b) is expected to store as JSONB, keyed by [oracleId] — cross-check the two
 * schemas before wiring the upload path (see `docs/plans/deck-engine-unification-progress.md`'s RUN
 * 5 entry).
 *
 * **Determinism (plan requirement — "given the same inputs, byte-identical output"):**
 *  - [tags]/[tribes]/[sources] are always emitted SORTED (never raw HashSet/collection iteration
 *    order, which is not guaranteed stable across JVM runs).
 *  - [themes]/[archetypes] are always built via [sortedMapOf]/`toSortedMap()`, never a plain
 *    `HashMap` — kotlinx.serialization serializes `Map` entries in iteration order, so an
 *    unsorted map would leak nondeterministic key order into the JSON.
 *  - [generatedAt] is computed ONCE per pipeline run (not per row via `Clock.System.now()` inside
 *    the per-card loop) and threaded through — otherwise every row would carry a different
 *    timestamp even when nothing about the card changed, defeating both determinism and the
 *    `--since` watermark's usefulness.
 */
@Serializable
data class CardStrategyTagsRow(
    val oracleId: String,
    /** Sorted, deduped [com.mmg.manahub.core.model.CardTag] keys — union of the production rule
     *  engine's auto-confirmed tags (via `SuggestTagsUseCase.confirmed`, ≥0.90 confidence, the same
     *  threshold the in-app auto-tag flow uses) and the curated Scryfall-Tagger-mapped tags
     *  ([com.mmg.manahub.tools.tagpipeline.mapping.TaggerTagMapping]). */
    val tags: List<String>,
    /** Sorted, deduped bare tribe words — [com.mmg.manahub.feature.decks.domain.engine.TribeDeriver
     *  .tribeKeys] with the `tribe:` prefix stripped (the prefix is a fingerprint-key convention
     *  internal to the app's scoring engine, not meaningful in a standalone bulk export). */
    val tribes: List<String>,
    /** [com.mmg.manahub.feature.decks.domain.engine.ThemeId.name] → EDHREC synergy weight. Empty
     *  when the card never appeared in any ingested EDHREC theme page (most cards — EDHREC only
     *  ranks the cards competitive-enough to be worth listing). */
    val themes: Map<String, Float> = emptyMap(),
    /** [com.mmg.manahub.feature.decks.domain.engine.ArchetypeId.name] → EDHREC synergy weight, via
     *  [com.mmg.manahub.feature.decks.domain.engine.EDHREC_SLUG_TO_ARCHETYPE_ID] (fixed 2026-07-21 —
     *  this field was hardcoded to `emptyMap()` for every row in the original RUN 5 pass; see that
     *  mapping file's KDoc for the root-cause writeup). Empty for
     *  [com.mmg.manahub.feature.decks.domain.engine.ArchetypeId.GENERIC] always (no EDHREC page
     *  exists for it) and for any card EDHREC never ranks under a specific archetype tag page —
     *  most cards, same as [themes].
     */
    val archetypes: Map<String, Float> = emptyMap(),
    /** Sorted subset of `{"rule_engine", "oracle_tags", "edhrec", "archidekt"}` — which stage(s)
     *  actually contributed data to this row. `"rule_engine"` is present on every row (it always
     *  runs); the others are present only when that oracle_id had a real hit in that data source. */
    val sources: List<String>,
    /** The single `CardTag` key derived from Archidekt's crowd-sourced "default category" for this
     *  card, when the Archidekt enrichment pass ran AND the card cleared its minimum-observation/
     *  minimum-share confidence bar (see `archidekt/ArchidektCategorySampler.kt`'s
     *  `resolveDominantCategories`). `null` when the enrichment pass was skipped (default — it is
     *  optional/best-effort, see `archidekt/ArchidektCategoryMapping.kt`'s KDoc) or the card never
     *  cleared the bar. Also folded (unioned) into [tags] when non-null, so a consumer that only
     *  reads [tags] still benefits without knowing this field exists; this field stays for
     *  transparency/debugging (so a reviewer can tell WHY a tag showed up). */
    val archidektCategory: String? = null,
    /** ISO-8601 instant, identical across every row emitted by one pipeline run. */
    val generatedAt: String,
    val pipelineVersion: Int,
)

/** Builds a [CardStrategyTagsRow], enforcing the determinism rules documented on the type. */
fun buildCardStrategyTagsRow(
    oracleId: String,
    tags: Set<String>,
    tribes: Set<String>,
    themes: Map<String, Float> = emptyMap(),
    archetypes: Map<String, Float> = emptyMap(),
    sources: Set<String>,
    archidektCategory: String? = null,
    generatedAt: String,
    pipelineVersion: Int,
): CardStrategyTagsRow = CardStrategyTagsRow(
    oracleId = oracleId,
    tags = tags.sorted(),
    tribes = tribes.sorted(),
    themes = themes.toSortedMap(),
    archetypes = archetypes.toSortedMap(),
    sources = sources.sorted(),
    archidektCategory = archidektCategory,
    generatedAt = generatedAt,
    pipelineVersion = pipelineVersion,
)
