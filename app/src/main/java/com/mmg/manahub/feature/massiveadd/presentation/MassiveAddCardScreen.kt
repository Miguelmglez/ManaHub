package com.mmg.manahub.feature.massiveadd.presentation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.CardRarity
import com.mmg.manahub.core.ui.components.ConditionSelectorSheet
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.LanguageSelectorSheet
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.SetSymbol
import com.mmg.manahub.core.ui.components.search.SetPickerSheet
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.SmallCardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.CardConstants
import org.jetbrains.compose.resources.painterResource
import org.koin.androidx.compose.koinViewModel

@Composable
fun MassiveAddCardScreen(
    onBack: () -> Unit,
    viewModel: MassiveAddCardViewModel = koinViewModel(),
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onNavigateToCardDetail: (String) -> Unit,
    preselectedCards:List<Card> = emptyList()
    ) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(Unit) {
        if (preselectedCards.isNotEmpty()){
            viewModel.preLoadItems(cards = preselectedCards)
        }
    }
    Scaffold(
        topBar = {
            Surface(
                color = colors.backgroundSecondary, shadowElevation = 4.dp
            ) {
                Row(

                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back button",
                            tint = colors.textPrimary
                        )
                    }
                    Text(
                        text = "Add Multiple cards",
                        color = colors.textPrimary,
                        style = typography.titleLarge,
                        modifier = Modifier.padding()
                    )
                }
            }
        }
    ) { paddingValues ->
        if (uiState.showSetSelectionSheet) {
            SetPickerSheet(
                singleSelection = true,
                selectedSetCodes = setOfNotNull(uiState.selectedSet?.code),
                onToggleSet = {
                    viewModel.updateSelectedSet(it)
                    viewModel.showSetSelectionSheet(false)
                },
                onDismiss = { viewModel.showSetSelectionSheet(false) },
            )
        }

        if (uiState.showLanguagePicker) {
            LanguageSelectorSheet(
                selectedLanguage = uiState.defaultLanguage,
                onDismiss = { viewModel.showLanguagePicker(false) },
                onSelectLanguage = {
                    viewModel.updateDefaultLanguage(it)
                    viewModel.showLanguagePicker(false)
                })
        }

        if (uiState.showCardStatusPicker) {
            ConditionSelectorSheet(
                selectedCondition = uiState.defaultCardState,
                onDismiss = { viewModel.showCardStatusPicker(false) },
                onSelectCondition = {
                    viewModel.updateDefaultCardState(it)
                    viewModel.showCardStatusPicker(false)
                })
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (!uiState.hasPreloadedItems) {
                Surface(
                    color = colors.surface,
                    border = BorderStroke(
                        color = colors.textDisabled, width = 1.dp
                    ),
                    onClick = { viewModel.showSetSelectionSheet(true) },
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp)
                    ) {
                        Surface(
                            color = colors.background,
                            shape = CardShape,
                            border = BorderStroke(1.dp, colors.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Set / Variant", style = typography.labelSmall, color = colors.textSecondary)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        uiState.selectedSet?.let {
                                            SetSymbol(
                                                setCode = it.code,
                                                rarity = CardRarity.fromString("common"),
                                                size = 16.dp,
                                            )

                                            Text(
                                                text = it.name ?: "Select set",
                                                style = typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }

                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = colors.textDisabled,
                                )
                            }
                        }
                    }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = "Default values:",
                            style = typography.labelLarge,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            modifier = Modifier
                            .weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ){
                            SelectorCard(
                                label = "Condition",
                                value = uiState.defaultCardState,
                                icon = null, // Could add icon here if needed
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.showCardStatusPicker(true) }
                            )
                            // Language Selector
                            SelectorCard(
                                label = "Lang",
                                value = CardConstants.getFlag(uiState.defaultLanguage),
                                icon = null,
                                modifier = Modifier.weight(1f),
                                onClick = {  viewModel.showLanguagePicker(true) }
                            )

                            IconButton(onClick = {viewModel.updateLayout()}) {
                                Icon(
                                    imageVector = if (uiState.showAsGrid) Icons.Default.GridView else Icons.Default.List,
                                    tint = colors.textDisabled,
                                    contentDescription = "Layout selector",
                                )
                            }
                        }
                    }

                    if (uiState.loadedCards.isNotEmpty()){
/*
                        if (uiState.showAsGrid){
*/
                            GridLayout(
                                uiState.loadedCards,
                                contentPaddingBottom = navBarBottom,
                                onLoadNextPage = viewModel::loadNextPage,
                                addOne = viewModel::addCardOnList,
                                removeOne = viewModel::removeCardFromList,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onCardSelected = { card -> onNavigateToCardDetail(card.scryfallId) },
                                getCopiesFromCard = viewModel::getCopiesFromCard
                            )
                        /*} else {
                            ListLayout(uiState.loadedCards)
                        }*/

                        // Bulk Actions Footer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.backgroundSecondary)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MagicCtaButton(
                        onClick = viewModel::addAllToCollection,
                        text = stringResource(R.string.scanner_queue_add_all),
                        icon = @Composable { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    MagicCtaButton(
                        onClick = viewModel::addAllToWishlist,
                        text = stringResource(R.string.scanner_queue_add_all_wishlist),
                        icon = @Composable { Icon(Icons.Rounded.FavoriteBorder, null, modifier = Modifier.size(18.dp)) },
                        style = MagicCtaStyle.Outlined,
                        modifier = Modifier.fillMaxWidth()
                    )
                }


                    } else {
                        EmptyState(title= "No Cards")
                    }
                }
            }
        }
    }

@Composable
private fun GridLayout(
    cards:List<Card>,
    contentPaddingBottom: Dp,
    hasMore:Boolean = false,
    onLoadNextPage: () -> Unit,
    addOne: (Card) -> Unit,
    removeOne: (Card) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onCardSelected: (Card) -> Unit,
    getCopiesFromCard:(Card) ->Int,
    ){

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()


    // Clear focus when scrolling results
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }
    LazyVerticalGrid(
        modifier = Modifier,
        state = rememberLazyGridState(),
        columns = GridCells.Adaptive (minSize = 148.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp + contentPaddingBottom),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = cards, key = {it.scryfallId}, contentType = {"card"}) { card->
            AddMassOnGridItem(
                card = card,
                isOwned = true,
                isWishlisted = true,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                removeOne = removeOne,
                addOne = addOne,
                getCopiesFromCard = getCopiesFromCard,
                openCardDetail = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onCardSelected(card)
                }
            )
        }
        if (hasMore) {
            item(contentType = "pagination_footer") {
                LaunchedEffect(
                    true) {
                    onLoadNextPage()
                }
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp), contentAlignment = Alignment.Center) {
                    MagicLoadingSpinner(
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ListLayout(
    cards:List<Card>
){
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        items (cards.size, key = ({ "${cards.size}" })){
           // AddMassOnListItem(card)
        }
    }
}
@Composable
private fun AddMassOnGridItem(
    card:Card,
    isOwned: Boolean,
    isWishlisted:Boolean,
    openCardDetail:(String)->Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    getCopiesFromCard: (Card)-> Int,
    removeOne: (Card)-> Unit,
    addOne:(Card) ->Unit
) {
    var copies by remember { mutableIntStateOf(getCopiesFromCard(card)) }
    val typography = MaterialTheme.magicTypography
    val colors = MaterialTheme.magicColors
    Surface(
        shadowElevation = 2.dp,
        shape = SmallCardShape,
        border = if (copies > 1) BorderStroke(1.dp, color = colors.primaryAccent) else null,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .clip(SmallCardShape)
                .background(colors.surfaceVariant)
        ) {
            Surface(onClick = { openCardDetail(card.scryfallId) }) {
                AsyncImage(
                    contentDescription = card.name,
                    placeholder = painterResource(Res.drawable.mtg_card_back),
                    error = painterResource(Res.drawable.mtg_card_back),
                    fallback = painterResource(Res.drawable.mtg_card_back),
                    model = card.imageNormal,
                    modifier = Modifier
                        .aspectRatio(0.716f)
                        .clip(SmallCardShape)
                        .then(
                            if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "card-image-${card.scryfallId}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    clipInOverlayDuringTransition = OverlayClip(
                                        RoundedCornerShape(6.dp)
                                    ),
                                    renderInOverlayDuringTransition = true,
                                )
                            }
                        } else Modifier)
                        .background(colors.surfaceVariant),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (isOwned) {
                    Icon(
                        Icons.Default.CollectionsBookmark,
                        tint = colors.primaryAccent,
                        modifier = Modifier.size(24.dp),
                        contentDescription = "owned badge"
                    )
                }


                if (isWishlisted) {
                    Icon(
                        painter = painterResource(R.drawable.ic_heart),
                        tint = colors.secondaryAccent,
                        modifier = Modifier.size(24.dp),
                        contentDescription = "Wishlist Badge"
                    )
                }
            }

            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { removeOne(card) }, modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Remove",
                        modifier = Modifier.size(48.dp),
                        tint = if (copies > 1) colors.primaryAccent else colors.textDisabled
                    )
                }

                Text(
                    copies.toString(),
                    style = typography.titleMedium,
                    color = colors.secondaryAccent,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.Center
                )

                IconButton(
                    onClick = { addOne(card) }, modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add",
                        modifier = Modifier.size(48.dp),
                        tint = if (copies < 99) colors.primaryAccent else colors.textDisabled
                    )
                }
            }
        }
    }
}

@Composable
private fun AddMassOnListItem(
    card:Card,
    isOwned: Boolean,

    ){
    val colors = MaterialTheme.magicColors


}

@Composable
private fun SelectorCard(
    label: String,
    value: String,
    icon: @Composable (() -> Unit)?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Surface(
        modifier = modifier.height(56.dp),
        color = mc.surface,
        shape = CardShape,
        border = BorderStroke(1.dp, mc.surfaceVariant),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, style = ty.labelSmall, color = mc.textSecondary)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                if (icon != null) icon()
                Text(
                    value,
                    style = ty.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = mc.textPrimary
                )
            }
        }
    }

}