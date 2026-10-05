package com.mmg.manahub.core.data.local.entity

import androidx.room.*

@Entity(tableName="collection_selection_queries",indices=[Index("owner_key","created_at")])
data class CollectionSelectionQueryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name="owner_key") val ownerKey: String,
    val source: String,
    val search: String,
    @ColumnInfo(name="advanced_query") val advancedQuery: String,
    val sort: String,
    val ascending: Boolean,
    val grouping: String,
    val phase: String,
    @ColumnInfo(name="created_at") val createdAt: Long,
    @ColumnInfo(name="missing_metadata") val missingMetadata: Long=0L,
)

@Entity(tableName="collection_selection_rows",primaryKeys=["query_id","source_id"],indices=[Index("query_id","matched","group_key","source_created_at","source_id"),Index("query_id","export_ordinal")])
data class CollectionSelectionRowEntity(
    @ColumnInfo(name="query_id") val queryId: String,
    @ColumnInfo(name="source_id") val sourceId: String,
    val quantity: Long,
    val foil: Boolean,
    val condition: String,
    val language: String,
    @ColumnInfo(name="source_created_at") val createdAt: Long,
    val wishlisted: Boolean,
    @ColumnInfo(name="for_trade") val forTrade: Boolean,
    val matched: Boolean=false,
    @ColumnInfo(name="group_key") val groupKey: String,
    @ColumnInfo(name="raw_foil") val rawFoil: Boolean?=foil,
    @ColumnInfo(name="raw_condition") val rawCondition: String?=condition,
    @ColumnInfo(name="raw_language") val rawLanguage: String?=language,
    @ColumnInfo(name="export_ordinal",defaultValue="0") val exportOrdinal: Long=0L,
    @Embedded(prefix="card_") val card: CardEntity,
)

@Entity(tableName="collection_selection_groups",primaryKeys=["query_id","group_key"],indices=[Index("query_id","ordinal")])
data class CollectionSelectionGroupEntity(
    @ColumnInfo(name="query_id") val queryId: String,
    @ColumnInfo(name="group_key") val groupKey: String,
    @ColumnInfo(name="representative_id") val representativeId: String,
    val quantity: Long,
    val foil: Boolean,
    @ColumnInfo(name="distinct_copies") val distinctCopies: Long,
    @ColumnInfo(name="latest_added_at") val latestAddedAt: Long,
    val name: String,
    val price: Double,
    val rarity: Int,
    val section: String="",
    @ColumnInfo(name="section_rank") val sectionRank: Int=0,
    @ColumnInfo(name="section_sort") val sectionSort: String="",
    val ordinal: Long=0L,
)
