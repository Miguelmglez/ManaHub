package com.mmg.manahub.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The JSONB `payload` column shape written by the offline `:tools:tag-pipeline` CLI (Deck Engine
 * Unification plan, RUN 5 / D5) — mirrors `CardStrategyTagsRow` in that module verbatim (see
 * `docs/plans/deck-engine-unification-progress.md`'s RUN 5 entries for the cross-check record).
 * Every field defaults so an unexpected/older pipeline payload shape never fails decoding outright
 * — a genuinely empty payload just yields zero tags, never a crash.
 */
@Serializable
data class CardStrategyTagsPayloadDto(
    val tags: List<String> = emptyList(),
    val tribes: List<String> = emptyList(),
    val themes: Map<String, Float> = emptyMap(),
    val archetypes: Map<String, Float> = emptyMap(),
    val sources: List<String> = emptyList(),
)

/** One row of the Supabase `card_strategy_tags` table (see `feedback_supabase_default_priv_table_grant`
 *  / `project_card_strategy_tags_pipeline` memory for the schema this mirrors). */
@Serializable
data class CardStrategyTagsRowDto(
    @SerialName("oracle_id") val oracleId: String,
    val payload: CardStrategyTagsPayloadDto,
    @SerialName("pipeline_version") val pipelineVersion: String,
    @SerialName("generated_at") val generatedAt: String,
)
