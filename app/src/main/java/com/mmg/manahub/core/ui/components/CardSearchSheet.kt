package com.mmg.manahub.core.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.AddCardRow
import com.mmg.manahub.core.ui.components.search.AdvancedSearchSheet
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.components.CardRarity
import com.mmg.manahub.core.ui.components.SetSymbol
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardSearchSheet(
    query: String,
    offerResults: List<AddCardRow>,
    addCardsResults: List<AddCardRow>,
    scryfallResults: List<AddCardRow>,
    isSearchingCards: Boolean,
    isSearchingScryfall: Boolean,
    wishlistResults: List<AddCardRow> = emptyList(),
    isSearchingWishlist: Boolean = false,
    isCommanderMode: Boolean = false,
    isCurrentCommander: (String) -> Boolean = { false },
    onQueryChange: (String) -> Unit,
    onScryfallSearch: (String) -> Unit,
    onAdd: (AddCardRow) -> Unit,
    onRemove: (AddCardRow) -> Unit,
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
    onCardClick: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = if (isCommanderMode) "Choose Commander" else stringResource(R.string.deckbuilder_add_cards),
    friendName: String? = null,
    /** Label for the Wishlist tab (first tab). Caller provides side-specific text. */
    wishlistTabLabel: String = "Wishlist",
    /** Label for the Offer tab (second tab). Caller provides side-specific text. */
    offerTabLabel: String = "Offer",
    /** Label for the All Cards tab (third tab). Caller provides side-specific text. */
    allCardsTabLabel: String = "All Cards",
    /** Section header rendered above [offerResults] inside the Offer tab. */
    offerSectionLabel: String = stringResource(R.string.trades_search_section_offers),
    /** Section header rendered above [addCardsResults] inside the Offer tab. */
    collectionSectionLabel: String = stringResource(R.string.trades_search_section_collection),
    /**
     * When false (default), renders 2 tabs: Collection ([addCardsResults]) + All Cards ([scryfallResults]).
     * When true, renders 3 tabs including the Wishlist tab — used by the Trades feature.
     */
    showWishlistTab: Boolean = false,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    
    val sheetFocusRequester = remember { FocusRequester() }
    val textFieldFocusRequester = remember { FocusRequester() }
    val lazyListState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )
    
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAdvancedSearch by remember { mutableStateOf(false) }

    val displayTitle = if (friendName != null) {
        "Search cards from $friendName"
    } else {
        title
    }

    val forceHideKeyboard = {
        // 1. Move focus to the sheet container (stealing it from TextField)
        sheetFocusRequester.requestFocus()
        
        // 2. Direct SoftwareKeyboardController call
        keyboardController?.hide()
        
        // 3. Native WindowInsets call (Brute force)
        val activity = view.context.findActivity()
        if (activity != null) {
            WindowInsetsControllerCompat(activity.window, view).hide(WindowInsetsCompat.Type.ime())
        }
        
        // 4. FocusManager fallback
        focusManager.clearFocus(force = true)
    }

    DisposableEffect(Unit) {
        onDispose {
            forceHideKeyboard()
        }
    }

    if (showAdvancedSearch) {
        AdvancedSearchSheet(
            onDismiss = { showAdvancedSearch = false },
            onSearch = { _, rawQuery ->
                showAdvancedSearch = false
                selectedTab = if (showWishlistTab) 2 else 1
                onScryfallSearch(rawQuery)
                forceHideKeyboard()
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = mc.textDisabled) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .padding(bottom = 16.dp)
                .focusRequester(sheetFocusRequester)
                .focusable()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = mc.textPrimary
                    )
                }
                Text(
                    text = displayTitle,
                    style = ty.titleLarge,
                    color = mc.textPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        val isCollectionTab = if (showWishlistTab) selectedTab == 1 else selectedTab == 0
                        val isScryfallTab = if (showWishlistTab) selectedTab == 2 else selectedTab == 1
                        when {
                            isCollectionTab -> onQueryChange(it)
                            isScryfallTab -> onScryfallSearch(it)
                            else -> onQueryChange(it)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(textFieldFocusRequester),
                    placeholder = { Text(stringResource(R.string.deckbuilder_add_cards_search_hint), color = mc.textDisabled, style = ty.bodyMedium) },
                    leadingIcon = {
                        val isSearching = if (showWishlistTab) {
                            when (selectedTab) {
                                0 -> isSearchingWishlist
                                1 -> isSearchingCards
                                else -> isSearchingScryfall
                            }
                        } else {
                            when (selectedTab) {
                                0 -> isSearchingCards
                                else -> isSearchingScryfall
                            }
                        }
                        if (isSearching) CircularProgressIndicator(Modifier.size(20.dp), color = mc.primaryAccent, strokeWidth = 2.dp)
                        else Icon(Icons.Default.Search, null, tint = mc.textSecondary)
                    },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { 
                            onQueryChange("")
                            onScryfallSearch("")
                            forceHideKeyboard()
                        }) { Icon(Icons.Default.Clear, null, tint = mc.textSecondary) } }
                    } else null,
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = mc.primaryAccent, unfocusedBorderColor = mc.surfaceVariant, cursorColor = mc.primaryAccent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { forceHideKeyboard() })
                )
                IconButton(onClick = { 
                    forceHideKeyboard()
                    showAdvancedSearch = true 
                }, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(mc.surface)) {
                    Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.advsearch_title), tint = mc.primaryAccent)
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = mc.backgroundSecondary,
                contentColor = mc.primaryAccent,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (showWishlistTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            forceHideKeyboard()
                        },
                        text = {
                            Text(
                                text = wishlistTabLabel.uppercase(Locale.getDefault()),
                                style = ty.labelLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    )
                }
                Tab(
                    selected = selectedTab == (if (showWishlistTab) 1 else 0),
                    onClick = {
                        selectedTab = if (showWishlistTab) 1 else 0
                        forceHideKeyboard()
                    },
                    text = {
                        Text(
                            text = offerTabLabel.uppercase(Locale.getDefault()),
                            style = ty.labelLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                )
                Tab(
                    selected = selectedTab == (if (showWishlistTab) 2 else 1),
                    onClick = {
                        selectedTab = if (showWishlistTab) 2 else 1
                        forceHideKeyboard()
                    },
                    text = {
                        Text(
                            text = allCardsTabLabel.uppercase(Locale.getDefault()),
                            style = ty.labelLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                )
            }

            val scryfallTabIndex = if (showWishlistTab) 2 else 1
            val collectionTabIndex = if (showWishlistTab) 1 else 0

            val results = when {
                showWishlistTab && selectedTab == 0 -> wishlistResults
                selectedTab == collectionTabIndex -> offerResults + addCardsResults
                else -> scryfallResults
            }
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.any { it.pressed }) {
                                    forceHideKeyboard()
                                }
                            }
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }

                when {
                    showWishlistTab && selectedTab == 0 -> {
                        items(wishlistResults) { row ->
                            AddCardSheetRow(
                                row = row,
                                isCommanderMode = isCommanderMode,
                                isCurrentCommander = isCurrentCommander(row.card.scryfallId),
                                isTradeMode = showWishlistTab,
                                onAdd = { onAdd(row) },
                                onRemove = { onRemove(row) },
                                onClick = { onCardClick(row.card.scryfallId) },
                                onInteraction = { forceHideKeyboard() }
                            )
                        }
                    }
                    selectedTab == collectionTabIndex -> {
                        if (offerResults.isNotEmpty()) {
                            item(key = "offers_header") {
                                Text(
                                    text = offerSectionLabel.uppercase(),
                                    style = ty.labelSmall,
                                    color = mc.textSecondary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(offerResults) { row ->
                                AddCardSheetRow(
                                    row = row,
                                    isCommanderMode = isCommanderMode,
                                    isCurrentCommander = isCurrentCommander(row.card.scryfallId),
                                    isTradeMode = showWishlistTab,
                                    onAdd = { onAdd(row) },
                                    onRemove = { onRemove(row) },
                                    onClick = { onCardClick(row.card.scryfallId) },
                                    onInteraction = { forceHideKeyboard() }
                                )
                            }
                        }

                        if (addCardsResults.isNotEmpty()) {
                            if (offerResults.isNotEmpty()) {
                                item(key = "collection_header") {
                                    Text(
                                        text = collectionSectionLabel.uppercase(),
                                        style = ty.labelSmall,
                                        color = mc.textSecondary,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                            items(addCardsResults, key = { "coll_${it.uniqueKey}" }) { row ->
                                AddCardSheetRow(
                                    row = row,
                                    isCommanderMode = isCommanderMode,
                                    isCurrentCommander = isCurrentCommander(row.card.scryfallId),
                                    isTradeMode = showWishlistTab,
                                    onAdd = { onAdd(row) },
                                    onRemove = { onRemove(row) },
                                    onClick = { onCardClick(row.card.scryfallId) },
                                    onInteraction = { forceHideKeyboard() }
                                )
                            }
                        }
                    }
                    else -> {
                        items(scryfallResults, key = { it.uniqueKey }) { row ->
                            AddCardSheetRow(
                                row = row,
                                isCommanderMode = isCommanderMode,
                                isCurrentCommander = isCurrentCommander(row.card.scryfallId),
                                isTradeMode = showWishlistTab,
                                onAdd = { onAdd(row) },
                                onRemove = { onRemove(row) },
                                onClick = { onCardClick(row.card.scryfallId) },
                                onInteraction = { forceHideKeyboard() }
                            )
                        }
                    }
                }

                if (results.isEmpty()) {
                    val isSearching = when {
                        showWishlistTab && selectedTab == 0 -> isSearchingWishlist
                        selectedTab == collectionTabIndex -> isSearchingCards
                        else -> isSearchingScryfall
                    }
                    if (!isSearching) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (selectedTab == scryfallTabIndex && query.isBlank())
                                        stringResource(R.string.deckbuilder_add_cards_search_hint)
                                    else
                                        stringResource(R.string.deckbuilder_no_cards),
                                    style = ty.bodyMedium,
                                    color = mc.textDisabled,
                                )
                            }
                        }
                    }
                }
            }

            Button(onClick = {
                forceHideKeyboard()
                onConfirm()
                onDismiss()
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent), shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.action_confirm), style = ty.titleMedium, color = mc.background)
            }
            
            TextButton(onClick = {
                forceHideKeyboard()
                onCancel()
                onDismiss()
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp)) {
                Text(stringResource(R.string.action_cancel), style = ty.titleMedium, color = mc.textSecondary)
            }
        }
    }
}

@Composable
private fun AddCardSheetRow(
    row: AddCardRow,
    isCommanderMode: Boolean = false,
    isCurrentCommander: Boolean = false,
    isTradeMode: Boolean = false,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit,
    onInteraction: () -> Unit
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val card = row.card

    Surface(
        onClick = {
            onInteraction()
            onClick()
        },
        shape = RoundedCornerShape(8.dp),
        color = when {
            isCurrentCommander -> mc.goldMtg.copy(alpha = 0.1f)
            row.quantityInDeck > 0 -> mc.primaryAccent.copy(alpha = 0.05f)
            else -> mc.surface
        },
        border = when {
            isCurrentCommander -> BorderStroke(1.dp, mc.goldMtg.copy(alpha = 0.4f))
            row.quantityInDeck > 0 -> BorderStroke(0.5.dp, mc.primaryAccent.copy(alpha = 0.3f))
            else -> null
        }
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(model = card.imageArtCrop, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 52.dp, height = 38.dp).clip(RoundedCornerShape(4.dp)))
            Column(modifier = Modifier.weight(1f)) {
                CardName(
                    name          = card.name,
                    showFrontOnly = true,
                    style         = ty.bodyMedium,
                    color         = if (isCurrentCommander) mc.goldMtg else mc.textPrimary,
                    maxLines      = 1,
                    overflow      = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SetSymbol(
                        setCode = card.setCode,
                        rarity = CardRarity.fromString(card.rarity),
                        size = 13.dp,
                    )
                    Text(
                        text = card.setName,
                        style = ty.labelSmall,
                        color = mc.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(
                    text = card.typeLine,
                    style = ty.labelSmall,
                    color = mc.textDisabled,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isTradeMode) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        val isFoil = row.wishlistEntry?.isFoil == true || row.offerEntry?.isFoil == true
                        val condition = row.wishlistEntry?.condition ?: row.offerEntry?.condition ?: "NM"
                        val language = row.wishlistEntry?.language ?: row.offerEntry?.language ?: "en"
                        val isAltArt = row.wishlistEntry?.isAltArt == true || row.offerEntry?.isAltArt == true

                        LanguageBadge(langCode = language)
                        CopyBadge(label = condition)
                        if (isFoil) FoilBadge()
                        if (isAltArt) AltArtBadge()
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!isCommanderMode && isCurrentCommander) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = mc.goldMtg,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    if (isTradeMode && !row.isOwned && row.availableQuantity == 0 && row.wishlistEntry == null && row.offerEntry == null) {
                        Icon(
                            imageVector = Icons.Default.PriorityHigh,
                            contentDescription = stringResource(com.mmg.manahub.R.string.trades_warning_not_in_list),
                            tint = mc.goldMtg,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (row.availableQuantity > 0) {
                        val overLimit = row.quantityInDeck > row.availableQuantity
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (overLimit) {
                                Icon(
                                    imageVector = Icons.Default.PriorityHigh,
                                    contentDescription = "Over limit",
                                    tint = mc.lifeNegative,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "${row.availableQuantity}",
                                style = ty.labelSmall,
                                color = if (overLimit) mc.lifeNegative else mc.textSecondary
                            )
                        }
                    }

                    if (row.quantityInDeck > 0 && !isCommanderMode) {
                        IconButton(onClick = {
                            onInteraction()
                            onRemove()
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Remove, null, tint = mc.textSecondary, modifier = Modifier.size(16.dp))
                        }
                        Text("${row.quantityInDeck}", style = ty.labelMedium, color = mc.primaryAccent)
                    }

                    val icon = if (isCommanderMode) {
                        if (isCurrentCommander) Icons.Default.Check else Icons.Default.Add
                    } else Icons.Default.Add

                    IconButton(
                        onClick = {
                            onInteraction()
                            onAdd()
                        },
                        enabled = !(isCommanderMode && isCurrentCommander),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(icon, null, tint = if (isCurrentCommander) mc.goldMtg else mc.primaryAccent, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
