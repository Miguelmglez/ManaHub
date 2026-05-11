package com.mmg.manahub.core.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.AddCardRow
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.addcard.AdvancedSearchSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardSearchSheet(
    query: String,
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
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onCardClick: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = if (isCommanderMode) "Choose Commander" else stringResource(R.string.deckbuilder_add_cards),
    friendName: String? = null
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    
    val sheetFocusRequester = remember { FocusRequester() }
    val textFieldFocusRequester = remember { FocusRequester() }
    val lazyListState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
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
                selectedTab = 1
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
            Text(displayTitle, style = ty.titleMedium, color = mc.textPrimary, modifier = Modifier.padding(16.dp))

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { if (selectedTab == 0) onQueryChange(it) else onScryfallSearch(it) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(textFieldFocusRequester),
                    placeholder = { Text(stringResource(R.string.deckbuilder_add_cards_search_hint), color = mc.textDisabled) },
                    leadingIcon = {
                        val isSearching = when (selectedTab) {
                            0 -> isSearchingWishlist
                            1 -> isSearchingCards
                            else -> isSearchingScryfall
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

            TabRow(selectedTabIndex = selectedTab, containerColor = mc.backgroundSecondary, contentColor = mc.primaryAccent) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { 
                        selectedTab = 0
                        forceHideKeyboard()
                    },
                    text = { Text("Wishlist", style = ty.labelLarge) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { 
                        selectedTab = 1
                        forceHideKeyboard()
                    },
                    text = { Text("Offer", style = ty.labelLarge) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { 
                        selectedTab = 2
                        forceHideKeyboard()
                    },
                    text = { Text("All Cards", style = ty.labelLarge) }
                )
            }

            val results = when (selectedTab) {
                0 -> wishlistResults
                1 -> addCardsResults
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
                
                items(results, key = { it.card.scryfallId }) { row ->
                    AddCardSheetRow(
                        row = row,
                        isCommanderMode = isCommanderMode,
                        isCurrentCommander = isCurrentCommander(row.card.scryfallId),
                        onAdd = { onAdd(row.card.scryfallId) },
                        onRemove = { onRemove(row.card.scryfallId) },
                        onClick = { onCardClick(row.card.scryfallId) },
                        onInteraction = { forceHideKeyboard() }
                    )
                }

                if (results.isEmpty()) {
                    val isSearching = when (selectedTab) {
                        0 -> isSearchingWishlist
                        1 -> isSearchingCards
                        else -> isSearchingScryfall
                    }
                    if (!isSearching) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (selectedTab == 2 && query.isBlank())
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
                onDismiss()
            }, modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent), shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.deckbuilder_add_cards_done), style = ty.titleMedium, color = mc.background)
            }
        }
    }
}

@Composable
private fun AddCardSheetRow(
    row: AddCardRow,
    isCommanderMode: Boolean = false,
    isCurrentCommander: Boolean = false,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit,
    onInteraction: () -> Unit
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val card = row.card
    val addEnabled = true 

    Surface(
        onClick = {
            onInteraction()
            onClick()
        },
        shape = RoundedCornerShape(8.dp), 
        color = if (isCurrentCommander) mc.goldMtg.copy(alpha = 0.1f) else mc.surface,
        border = if (isCurrentCommander) BorderStroke(1.dp, mc.goldMtg.copy(alpha = 0.4f)) else null
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
                Text(card.typeLine, style = ty.bodySmall, color = mc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

                IconButton(onClick = {
                    onInteraction()
                    onAdd()
                }, enabled = addEnabled || isCommanderMode, modifier = Modifier.size(32.dp)) {
                    Icon(icon, null, tint = if (isCurrentCommander) mc.goldMtg else if (addEnabled || isCommanderMode) mc.primaryAccent else mc.textDisabled, modifier = Modifier.size(16.dp))
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
