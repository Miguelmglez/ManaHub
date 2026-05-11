package com.mmg.manahub.feature.addcard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.CardRarity
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.SetSymbol
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.core.ui.components.search.AdvancedSearchSheet
import androidx.compose.animation.AnimatedVisibility

// ─────────────────────────────────────────────────────────────────────────────
//  AddCardScreen — tabbed entry point for adding cards to the collection.
//
//  Tab 0 "Search":  text search with debounce → results list → confirm sheet
//  Tab 1 "Scanner": navigates to the existing ScannerScreen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToCardDetail: (String) -> Unit,
    viewModel: AddCardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.addcard_tab_search),
        stringResource(R.string.addcard_tab_scanner),
    )
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Scaffold(
        topBar = {
            Surface(
                color = mc.backgroundSecondary,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = mc.textPrimary,
                        )
                    }
                    Text(
                        text = stringResource(R.string.addcard_title),
                        style = ty.titleLarge,
                        color = mc.textPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        containerColor = mc.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Tab row ───────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = mc.backgroundSecondary,
                contentColor = mc.primaryAccent,
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            selectedTab = index
                        },
                        text = {
                            Text(
                                title.uppercase(),
                                style = ty.labelLarge,
                                color = if (selectedTab == index) mc.primaryAccent else mc.textDisabled,
                            )
                        },
                    )
                }
            }

            // ── Tab content ───────────────────────────────────────────────────
            var showAdvancedSearch by remember { mutableStateOf(false) }

            when (selectedTab) {
                0 -> SearchTab(
                    uiState = uiState,
                    onQueryChange = viewModel::onQueryChange,
                    onClearFilters = viewModel::onClearFilters,
                    onCardSelected = { card -> onNavigateToCardDetail(card.scryfallId) },
                    onAdvancedSearch = { showAdvancedSearch = true },
                )

                1 -> ScannerTab(onNavigateToScanner = onNavigateToScanner)
            }

            if (showAdvancedSearch) {
                AdvancedSearchSheet(
                    onDismiss = { showAdvancedSearch = false },
                    onSearch = { query, rawQuery ->
                        viewModel.onAdvancedQuerySearch(query, rawQuery)
                        showAdvancedSearch = false
                    },
                )
            }
        }
    }

}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 0 — Text search
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchTab(
    uiState: AddCardUiState,
    onQueryChange: (String) -> Unit,
    onClearFilters: () -> Unit,
    onCardSelected: (Card) -> Unit,
    onAdvancedSearch: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val focusRequester = remember { FocusRequester() }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        // ── Search field + advanced search button ─────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(stringResource(R.string.addcard_search_hint), color = mc.textDisabled)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = mc.textDisabled)
                },
                trailingIcon = if (uiState.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(R.string.action_close),
                                tint = mc.textDisabled
                            )
                        }
                    }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onQueryChange(uiState.query) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = mc.primaryAccent,
                    unfocusedBorderColor = mc.primaryAccent.copy(alpha = 0.25f),
                    cursorColor = mc.primaryAccent,
                    focusedTextColor = mc.textPrimary,
                    unfocusedTextColor = mc.textPrimary,
                    focusedContainerColor = mc.surface,
                    unfocusedContainerColor = mc.surface,
                ),
                shape = RoundedCornerShape(12.dp),
            )
            BadgedBox(
                badge = {
                    if (uiState.activeFilterCount > 0) {
                        Badge(
                            containerColor = mc.primaryAccent,
                            contentColor = mc.background,
                        ) { Text("${uiState.activeFilterCount}") }
                    }
                }
            ) {
                IconButton(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        onAdvancedSearch()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(mc.primaryAccent.copy(alpha = 0.1f)),
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = stringResource(R.string.advsearch_button),
                        tint = mc.primaryAccent,
                    )
                }
            }
        }

        // ── Active filters indicator ─────────────────────────────────────────
        AnimatedVisibility(visible = uiState.activeFilterCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.collection_active_filters, uiState.activeFilterCount),
                    style = ty.bodySmall,
                    color = mc.primaryAccent,
                )
                TextButton(
                    onClick = onClearFilters,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(
                        stringResource(R.string.collection_clear_filters),
                        style = ty.labelSmall,
                        color = mc.lifeNegative,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Content states ────────────────────────────────────────────────────
        when {
            uiState.isSearching -> {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = mc.primaryAccent,
                )
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = mc.primaryAccent,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            uiState.query.length >= 2 && uiState.results.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.addcard_no_results),
                            style = ty.titleMedium,
                            color = mc.textSecondary,
                        )
                        Text(
                            stringResource(R.string.addcard_no_results_subtitle),
                            style = ty.bodySmall,
                            color = mc.textDisabled,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            uiState.query.length < 2 && uiState.results.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.addcard_search_min_chars),
                        style = ty.bodyMedium,
                        color = mc.textDisabled,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            else -> {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(uiState.results, key = { it.scryfallId }) { card ->
                        SearchResultItem(
                            card = card,
                            uiState = uiState,
                            onClick = {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                onCardSelected(card)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Search result row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchResultItem(
    card: Card,
    uiState: AddCardUiState,
    onClick: () -> Unit
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = mc.surface,
        border = BorderStroke(0.5.dp, mc.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Art thumbnail
            AsyncImage(
                model = card.imageNormal,
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 44.dp, height = 60.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(mc.surfaceVariant),
            )

            // Name / type / set
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val frontFace = card.cardFaces?.firstOrNull()
                CardName(
                    name = frontFace?.name ?: if (card.printedName.isNullOrEmpty()) card.name else card.printedName,
                    showFrontOnly = true,
                    style = ty.bodyMedium,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = frontFace?.typeLine ?: if (card.printedTypeLine.isNullOrEmpty()) card.typeLine else card.printedTypeLine,
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SetSymbol(
                        setCode = card.setCode,
                        rarity = CardRarity.fromString(card.rarity),
                        size = 14.dp,
                    )
                    Text(
                        text = card.setName,
                        style = ty.labelSmall,
                        color = mc.secondaryAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Mana cost + price
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                card.manaCost?.let {
                    ManaCostImages(manaCost = it, symbolSize = 14.dp)
                }
                val formattedPrice = PriceFormatter.formatFromScryfall(
                    priceUsd = card.priceUsd,
                    priceEur = card.priceEur,
                    preferredCurrency = uiState.preferredCurrency
                )
                if (formattedPrice != "—") {
                    Text(
                        text = formattedPrice,
                        style = ty.bodySmall,
                        color = mc.goldMtg,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 1 — Scanner entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScannerTab(onNavigateToScanner: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = mc.primaryAccent,
            )
            Text(
                stringResource(R.string.addcard_scanner_title),
                style = ty.titleMedium,
                color = mc.textPrimary,
            )
            Text(
                stringResource(R.string.addcard_scanner_subtitle),
                style = ty.bodySmall,
                color = mc.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onNavigateToScanner,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.addcard_scanner_button),
                    style = ty.labelLarge,
                    color = mc.background,
                )
            }
        }
    }
}

