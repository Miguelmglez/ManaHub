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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.AddCardRow
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.SetSymbol
import com.mmg.manahub.core.ui.components.CardRarity
import com.mmg.manahub.core.ui.components.CardRow
import com.mmg.manahub.core.ui.components.search.AdvancedSearchSheet
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
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
    /**
     * Pre-decomposed structured filters — the Analysis tab's "Browse for &lt;Category&gt;" entry
     * point (Suggestions Tab UI Polish plan, W11/D8). Supersedes the Category Sections rework's
     * original `initialScryfallQuery: String?` (deleted — it had exactly one call site, this
     * same one), which used to dump the FULL curated Scryfall query string into the plain search
     * bar (`"function:spot-removal id<=WUBRG legal:commander"` visible as raw syntax) — the
     * user's own words: "esto es un error gravísimo." `null` (default) keeps every existing call
     * site's behavior unchanged.
     *
     * W11 bug-fix pass (2026-08-25): this no longer auto-pops [AdvancedSearchSheet] up on open —
     * an earlier version of this param did, which just relocated the "raw filters visible where
     * the user didn't ask for them" problem from the search bar to an uninvited sheet. Instead,
     * [onAdvancedSearch] is invoked once so the caller can run the ACTUAL search against the
     * translated filters directly (results already filtered, plain search bar left EMPTY); the
     * user can still open [AdvancedSearchSheet] manually via the Tune icon, at which point it
     * shows these SAME filters pre-selected (unchanged — still via [AdvancedSearchSheet]'s own
     * `initialAdvancedQuery` param / `AdvancedSearchViewModel.seedFrom`).
     */
    initialAdvancedQuery: AdvancedSearchQuery? = null,
    /**
     * Invoked once when [initialAdvancedQuery] is non-empty (W11 bug-fix pass) — the caller runs
     * the real search against the translated filters (e.g.
     * `DeckStudioViewModel::searchScryfallStructured`). No-op default keeps every existing call
     * site (none of which pass [initialAdvancedQuery] today besides the Analysis tab) unaffected.
     */
    onAdvancedSearch: (AdvancedSearchQuery) -> Unit = {},
    /**
     * [CardTag][com.mmg.manahub.core.model.CardTag] keys used to pre-filter the Collection tab on
     * open — the local-collection counterpart to [initialScryfallQuery]. Also selects the
     * Collection tab. Applying the filter is delegated to [onFilterCollectionByTags] (the sheet
     * has no direct access to the collection); an empty set (default) keeps every existing call
     * site's behavior unchanged.
     */
    initialCollectionTagKeys: Set<String> = emptySet(),
    /**
     * Invoked once when [initialCollectionTagKeys] is non-empty — mirrors [onScryfallSearch] but
     * for the Collection tab (e.g. `DeckStudioViewModel::searchCollectionByTags`). No-op default.
     */
    onFilterCollectionByTags: (Set<String>) -> Unit = {},
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

    // Tab indices shift by one when the Wishlist tab (Trades feature) is present. Computed early
    // (moved up from their original single call site below) so the preset-entry-point effects
    // below can reference them.
    val scryfallTabIndex = if (showWishlistTab) 2 else 1
    val collectionTabIndex = if (showWishlistTab) 1 else 0

    // W11 bug-fix pass — structured preset entry point (Suggestions Tab UI Polish plan): a caller
    // (the Analysis tab's "Browse for <Category>" button) opens this sheet with results ALREADY
    // filtered by the translated criteria and the plain search bar left EMPTY, instead of a raw
    // string in the search bar (the original bug) or an uninvited AdvancedSearchSheet popping up
    // on top (this param's own earlier, also-wrong behavior). Runs once per distinct preset value;
    // `null` (every pre-existing call site) is a no-op.
    LaunchedEffect(initialAdvancedQuery) {
        if (initialAdvancedQuery != null && !initialAdvancedQuery.isEmpty()) {
            selectedTab = scryfallTabIndex
            onAdvancedSearch(initialAdvancedQuery)
        }
    }
    LaunchedEffect(initialCollectionTagKeys) {
        if (initialCollectionTagKeys.isNotEmpty()) {
            selectedTab = collectionTabIndex
            onFilterCollectionByTags(initialCollectionTagKeys)
        }
    }

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
                selectedTab = scryfallTabIndex
                onScryfallSearch(rawQuery)
                forceHideKeyboard()
            },
            initialAdvancedQuery = initialAdvancedQuery,
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
        dragHandle = null,
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
                        if (isSearching) MagicLoadingSpinner(size = MagicLoadingSize.XSmall)
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
                        items(wishlistResults, key = { it.uniqueKey }) { row ->
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
                            items(offerResults, key = { "offer_${it.uniqueKey}" }) { row ->
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

            MagicCtaButton(
                onClick = {
                    forceHideKeyboard()
                    onConfirm()
                    onDismiss()
                },
                text = stringResource(R.string.action_confirm),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = MagicCtaColor.Accent
            )

            MagicCtaButton(
                onClick = {
                    forceHideKeyboard()
                    onCancel()
                    onDismiss()
                },
                text = stringResource(R.string.action_cancel),
                style = MagicCtaStyle.Ghost,
                color = MagicCtaColor.Neutral,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
/**
 * The card row shown by the Build tab's add-cards sheet AND (Deck Analysis Category Sections
 * rework, W6) the Analysis tab's category "Browse" entry point — same sheet, same row. Rewritten
 * on top of [CardListItem] (shared/core-ui) so mana cost renders here for free, per "el coste de
 * maná va a estar incluido ahora en todos las vistas". [CardListItem] owns the leading
 * image/name/set/type/mana-cost layout; the `-`/count/`+` controls and the commander/over-limit
 * icons stay bespoke trailing content beside it (no `trailingContent` slot exists on
 * [CardListItem] itself — adding one was out of this pass's scope since nothing else needs it yet).
 *
 * Two intentional visual deltas vs. the pre-W6 hand-rolled row, neither asked for by the plan but
 * unavoidable once routed through [CardListItem]'s fixed layout — flagged for the design reviewer:
 * 1. The leading thumbnail switches from [Card.imageArtCrop] (a 52×38 landscape crop) to
 *    [Card.imageNormal] in [CardListItem]'s fixed 56×80 portrait slot (`SmallCardShape`) — the same
 *    field/shape every other [CardListItem] consumer already uses; there is no portrait/landscape
 *    switch on the shared component.
 * 2. The current-commander name highlight (`color = mc.goldMtg`) is dropped — [CardListItem] does
 *    not expose a name-color override. The gold background tint + border (still applied via the
 *    outer [Surface], unchanged) plus the star icon on the trailing side keep the commander state
 *    visible.
 */
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
    val card = row.card

    Box {
        CardRow(
            card = card,
            isInCollection = row.isOwned,
            onClick = {
                onInteraction()
                onClick()
            },
            onRemove = {
                onInteraction()
                onRemove()
            },
            onAdd = {
                onInteraction()
                onAdd()
            },
            quantity = row.quantityInDeck,
            selected = isCurrentCommander,
            extraSupportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isTradeMode) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isFoil = row.wishlistEntry?.isFoil == true || row.offerEntry?.isFoil == true
                            val condition = row.wishlistEntry?.condition ?: row.offerEntry?.condition ?: "NM"
                            val language = row.wishlistEntry?.language ?: row.offerEntry?.language ?: "en"

                            LanguageBadge(langCode = language)
                            CopyBadge(label = condition)
                            if (isFoil) FoilBadge()
                        }
                    }

                    if (isTradeMode && !row.isOwned && row.availableQuantity == 0 && row.wishlistEntry == null && row.offerEntry == null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                imageVector = Icons.Default.PriorityHigh,
                                contentDescription = stringResource(R.string.trades_warning_not_in_list),
                                tint = mc.goldMtg,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.trades_warning_not_in_list),
                                style = MaterialTheme.magicTypography.labelSmall,
                                color = mc.goldMtg
                            )
                        }
                    }
                    
                    if (row.availableQuantity > 0) {
                        val overLimit = row.quantityInDeck > row.availableQuantity
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                                text = "Available: ${row.availableQuantity}",
                                style = MaterialTheme.magicTypography.labelSmall,
                                color = if (overLimit) mc.lifeNegative else mc.textSecondary
                            )
                        }
                    }
                }
            }
        )

        if (!isCommanderMode && isCurrentCommander) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = mc.goldMtg,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(18.dp)
            )
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
