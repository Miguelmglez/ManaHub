package com.mmg.manahub.feature.collection.presentation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.core.model.*
import com.mmg.manahub.core.ui.components.*
import com.mmg.manahub.core.ui.theme.*

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun PagedCollectionCards(
    cards: LazyPagingItems<CollectionSelectionGroup>,
    summary: CollectionSelectionSummary?,
    mode: CollectionViewMode,
    grouping: CollectionGroupingMode,
    gridState: LazyGridState,
    listState: LazyListState,
    topPadding: Dp,
    onCardClick: (String,String?)->Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val spacing=MaterialTheme.spacing
    val collapsed=remember(summary?.id) { mutableStateMapOf<String,Boolean>() }
    @Composable fun Header(group: CollectionSelectionGroup) {
        val totals=summary?.sections?.firstOrNull { it.token==group.section }
        SectionHeader(
            title=if(grouping==CollectionGroupingMode.SET)group.card.setName.ifBlank { group.section } else group.section,
            expanded=collapsed[group.section]!=true,
            onToggle={collapsed[group.section]=collapsed[group.section]!=true},
            trailing={ Text("${totals?.groups ?: 0L} cards · ${totals?.copies ?: 0L} copies",style=MaterialTheme.magicTypography.bodySmall,color=MaterialTheme.magicColors.textSecondary) },
        )
    }
    @Composable fun Card(group: CollectionSelectionGroup) {
        val display=CollectionCardGroup(group.card,1,group.hasFoil,1,group.latestAddedAt,group.groupKey)
        if(mode==CollectionViewMode.GRID)CardGridItem(display,{onCardClick(group.card.scryfallId,group.groupKey)},sharedTransitionScope=sharedTransitionScope,animatedVisibilityScope=animatedVisibilityScope,sharedTransitionKey=group.groupKey,quantity=group.quantity,variants=group.distinctCopies)
        else CardListItem(display,{onCardClick(group.card.scryfallId,group.groupKey)},sharedTransitionScope=sharedTransitionScope,animatedVisibilityScope=animatedVisibilityScope,sharedTransitionKey=group.groupKey,quantity=group.quantity,variants=group.distinctCopies)
    }
    if(mode==CollectionViewMode.GRID) {
        LazyVerticalGrid(GridCells.Fixed(3),Modifier.fillMaxSize(),state=gridState,contentPadding=PaddingValues(top=topPadding+spacing.sm,bottom=spacing.xxl),horizontalArrangement=Arrangement.spacedBy(spacing.sm),verticalArrangement=Arrangement.spacedBy(spacing.sm)) {
            for(index in 0 until cards.itemCount) {
                val group=cards.peek(index)
                val previous=if(index>0)cards.peek(index-1) else null
                if(group!=null && grouping!=CollectionGroupingMode.NONE && (previous==null || previous.section!=group.section))item(key="section:${group.section}:${group.groupKey}",span={GridItemSpan(maxLineSpan)}) { Header(group) }
                item(key=group?.groupKey ?: "loading:$index") {
                    val loaded=cards[index]
                    if(loaded!=null && collapsed[loaded.section]!=true)Card(loaded)
                }
            }
            if(cards.loadState.append is LoadState.Loading)item(span={GridItemSpan(maxLineSpan)}) { MagicProgressBar() }
            if(cards.loadState.append is LoadState.Error || cards.loadState.refresh is LoadState.Error)item(span={GridItemSpan(maxLineSpan)}) { InlineErrorState("Could not load this page.","Retry",cards::retry) }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(),state=listState,contentPadding=PaddingValues(top=topPadding+spacing.sm,bottom=spacing.xxl)) {
            items(cards.itemCount,key=cards.itemKey { it.groupKey }) { index ->
                val group=cards[index]
                val previous=if(index>0)cards.peek(index-1) else null
                if(group!=null) {
                    if(grouping!=CollectionGroupingMode.NONE && (previous==null || previous.section!=group.section))Header(group)
                    if(collapsed[group.section]!=true)Card(group)
                }
            }
            if(cards.loadState.append is LoadState.Loading)item { MagicProgressBar() }
            if(cards.loadState.append is LoadState.Error || cards.loadState.refresh is LoadState.Error)item { InlineErrorState("Could not load this page.","Retry",cards::retry) }
        }
    }
}
