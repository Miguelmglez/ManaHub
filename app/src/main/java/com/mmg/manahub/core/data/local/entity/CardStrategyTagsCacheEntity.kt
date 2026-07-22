package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache of a fetched Supabase `card_strategy_tags` row (Deck Engine Unification plan, D8,
 * §5 Phase 5c). Mirrors [ComboCacheEntity]'s "pure cache, store the raw payload verbatim" pattern
 * -- `oracle_id` is the card's oracle-wide identity (shared across every printing/language), NOT
 * a `scryfall_id`. Holds only transient, re-fetchable data, so it is safe to overwrite on conflict
 * and evict/refresh freely.
 */
@Entity(tableName = "card_strategy_tags_cache")
data class CardStrategyTagsCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "oracle_id") val oracleId: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "pipeline_version") val pipelineVersion: String,
    @ColumnInfo(name = "generated_at") val generatedAt: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)
