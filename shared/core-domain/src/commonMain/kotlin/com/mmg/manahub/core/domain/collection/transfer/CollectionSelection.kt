package com.mmg.manahub.core.domain.collection.transfer

import com.mmg.manahub.core.model.*
import kotlinx.serialization.Serializable

enum class CollectionSelectionSort { NAME, PRICE, RARITY, DATE_ADDED }

/** One immutable selection definition is shared by Collection browsing and later export jobs. */
@Serializable
data class CollectionSelectionQuery(
    val source: CollectionSource,
    val search: String,
    val advanced: AdvancedSearchQuery?,
    val sort: CollectionSelectionSort,
    val ascending: Boolean,
    val grouping: CollectionGroupingMode,
)

data class CollectionSelectionSummary(val id: String,val groups: Long,val copies: Long,val missingMetadataRows: Long,val sections: List<CollectionSelectionSection>,val tags: Set<CardTag> = emptySet())
data class CollectionSelectionSection(val token: String,val groups: Long,val copies: Long)
data class CollectionSelectionGroup(val card: Card,val groupKey: String,val quantity: Long,val distinctCopies: Long,val hasFoil: Boolean,val latestAddedAt: Long,val section: String)

/** Full metadata is read only for a bounded immutable group page. */
interface CollectionSelectionRepository {
    suspend fun capture(owner: TransferOwner,query: CollectionSelectionQuery): CollectionSelectionSummary
    suspend fun page(owner: TransferOwner,id: String,after: Long): CollectionSelectionPage
    suspend fun discard(owner: TransferOwner,id: String)
}
data class CollectionSelectionPage(val groups: List<CollectionSelectionGroup>,val nextOrdinal: Long?) {
    init { require(groups.size<=50) }
}

/** Bucket evaluation reuses the existing grouping rules on one representative, never a global list. */
fun collectionSelectionSection(card: Card,mode: CollectionGroupingMode): String = if(mode==CollectionGroupingMode.NONE)"" else
    groupCollection(listOf(CollectionCardGroup(card,1,false,1,0L,"")),mode).single().labelToken

fun collectionSelectionSectionRank(token: String,mode: CollectionGroupingMode): Int = when(mode) {
    CollectionGroupingMode.TYPE -> listOf("Creatures","Instants","Sorceries","Enchantments","Artifacts","Planeswalkers","Lands","Other").indexOf(token)
    CollectionGroupingMode.COLOR -> listOf("W","U","B","R","G","Multicolor","Colorless","Land").indexOf(token)
    CollectionGroupingMode.CMC -> listOf("0","1","2","3","4","5","6","7+","Lands").indexOf(token)
    CollectionGroupingMode.RARITY -> when(token.lowercase()) { "mythic"->0;"rare"->1;"uncommon"->2;"common"->4;else->3 }
    else -> 0
}
