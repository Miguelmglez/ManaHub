package com.mmg.magicfolder.feature.addcard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmg.magicfolder.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.ui.components.CardRarity
import com.mmg.magicfolder.core.ui.components.ManaCostImages
import com.mmg.magicfolder.core.ui.components.SetSymbol
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography

// ─────────────────────────────────────────────────────────────────────────────
//  AddCardScreen — tabbed entry point for adding cards to the collection.
//
//  Tab 0 "Search":  text search with debounce → results list → confirm sheet
//  Tab 1 "Scanner": navigates to the existing ScannerScreen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCardScreen(
    onNavigateBack:      () -> Unit,
    onNavigateToScanner: () -> Unit,
    viewModel:           AddCardViewModel = hiltViewModel(),
) {
    val uiState     by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs         = listOf("Search", "Scanner")
    val mc           = MaterialTheme.magicColors
    val ty           = MaterialTheme.magicTypography

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
                        text = "Add Card",
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
                containerColor   = mc.backgroundSecondary,
                contentColor     = mc.primaryAccent,
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
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
            when (selectedTab) {
                0 -> SearchTab(
                    uiState        = uiState,
                    onQueryChange  = viewModel::onQueryChange,
                    onCardSelected = viewModel::onCardSelected,
                )
                1 -> ScannerTab(onNavigateToScanner = onNavigateToScanner)
            }
        }
    }

    // ── Confirm sheet (overlays full screen) ──────────────────────────────────
    if (uiState.showConfirmSheet && uiState.selectedCard != null) {
        AddCardConfirmSheet(
            card      = uiState.selectedCard!!,
            onConfirm = { isFoil, condition, language, qty ->
                viewModel.onConfirmAdd(
                    scryfallId = uiState.selectedCard!!.scryfallId,
                    isFoil     = isFoil,
                    condition  = condition,
                    language   = language,
                    quantity   = qty,
                )
            },
            onDismiss = viewModel::onDismissConfirmSheet,
        )
    }

    // ── Side effects ──────────────────────────────────────────────────────────
    if (uiState.addedSuccessfully) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1500)
            viewModel.onSuccessDismissed()
        }
    }
    uiState.error?.let { err ->
        LaunchedEffect(err) { viewModel.onErrorDismissed() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 0 — Text search
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchTab(
    uiState:        AddCardUiState,
    onQueryChange:  (String) -> Unit,
    onCardSelected: (Card) -> Unit,
) {
    val mc             = MaterialTheme.magicColors
    val ty             = MaterialTheme.magicTypography
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        // ── Search field ──────────────────────────────────────────────────────
        OutlinedTextField(
            value         = uiState.query,
            onValueChange = onQueryChange,
            modifier      = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = {
                Text(stringResource(R.string.addcard_search_hint), color = mc.textDisabled)
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = mc.textDisabled)
            },
            trailingIcon = if (uiState.query.isNotEmpty()) {{
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_close), tint = mc.textDisabled)
                }
            }} else null,
            singleLine      = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onQueryChange(uiState.query) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = mc.primaryAccent,
                unfocusedBorderColor    = mc.primaryAccent.copy(alpha = 0.25f),
                cursorColor             = mc.primaryAccent,
                focusedTextColor        = mc.textPrimary,
                unfocusedTextColor      = mc.textPrimary,
                focusedContainerColor   = mc.surface,
                unfocusedContainerColor = mc.surface,
            ),
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(8.dp))

        // ── Content states ────────────────────────────────────────────────────
        when {
            uiState.isSearching -> {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color    = mc.primaryAccent,
                )
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color       = mc.primaryAccent,
                        modifier    = Modifier.size(32.dp),
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
                            "No cards found",
                            style = ty.titleMedium,
                            color = mc.textSecondary,
                        )
                        Text(
                            "Try another spelling or search in another language",
                            style     = ty.bodySmall,
                            color     = mc.textDisabled,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            uiState.query.length < 2 && uiState.results.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Type at least 2 characters to search",
                        style     = ty.bodyMedium,
                        color     = mc.textDisabled,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding      = PaddingValues(vertical = 4.dp),
                ) {
                    items(uiState.results, key = { it.scryfallId }) { card ->
                        SearchResultItem(card = card, onClick = { onCardSelected(card) })
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
private fun SearchResultItem(card: Card, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(12.dp),
        color   = mc.surface,
        border  = BorderStroke(0.5.dp, mc.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier              = Modifier.padding(10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Art thumbnail
            AsyncImage(
                model              = card.imageNormal,
                contentDescription = card.name,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
                    .size(width = 44.dp, height = 60.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(mc.surfaceVariant),
            )

            // Name / type / set
            Column(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text     = card.printedName,
                    style    = ty.bodyMedium,
                    color    = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text     = card.typeLine,
                    style    = ty.bodySmall,
                    color    = mc.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    SetSymbol(
                        setCode = card.setCode,
                        rarity  = CardRarity.fromString(card.rarity),
                        size    = 14.dp,
                    )
                    Text(
                        text  = card.setCode.uppercase(),
                        style = ty.labelSmall,
                        color = mc.textDisabled,
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
                val price = card.priceEur ?: card.priceUsd
                if (price != null && price > 0) {
                    Text(
                        text  = if (card.priceEur != null)
                            "€${"%.2f".format(price)}"
                        else
                            "${"$"}${"%.2f".format(price)}",
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
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier            = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector       = Icons.Default.QrCodeScanner,
                contentDescription = null,
                modifier          = Modifier.size(64.dp),
                tint              = mc.primaryAccent,
            )
            Text(
                "Scan card barcode",
                style = ty.titleMedium,
                color = mc.textPrimary,
            )
            Text(
                "Point the camera at the barcode\non the back of the card",
                style     = ty.bodySmall,
                color     = mc.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick  = onNavigateToScanner,
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Open Scanner",
                    style = ty.labelLarge,
                    color = mc.background,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Confirm sheet — qty / foil / condition / language before adding
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCardConfirmSheet(
    card:      Card,
    onConfirm: (isFoil: Boolean, condition: String, language: String, qty: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val conditions = listOf("NM", "LP", "MP", "HP", "DMG")
    val languages  = listOf("en", "es", "de")
    var isFoil    by remember { mutableStateOf(false) }
    var condition by remember { mutableStateOf("NM") }
    var language  by remember { mutableStateOf("en") }
    var qty       by remember { mutableIntStateOf(1) }
    val uriHandler = LocalUriHandler.current
    var showBackFace by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = mc.backgroundSecondary,
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.85f)) {
            // Header (Sticky)
            Text(
                text     = card.name,
                style    = ty.titleMedium,
                color    = mc.textPrimary,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp)
            )

            // Scrollable Content
            Column(
                modifier            = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text  = "${card.setName} · ${card.rarity}",
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                )
                card.priceUsd?.let {
                    Text(
                        text  = "Market price: $${String.format("%.2f", it)}",
                        style = ty.bodyMedium,
                        color = mc.goldMtg,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .then(
                            if (card.imageBackNormal != null)
                                Modifier.clickable { showBackFace = !showBackFace }
                            else Modifier
                        ),
                    contentAlignment  = Alignment.Center,
                ) {
                    val imageUrl = if (showBackFace && card.imageBackNormal != null)
                        card.imageBackNormal else card.imageNormal

                    SubcomposeAsyncImage(
                        model              = imageUrl,
                        contentDescription = card.name,
                        contentScale       = ContentScale.Fit,
                        loading            = {
                            CircularProgressIndicator(
                                color       = mc.primaryAccent,
                                modifier    = Modifier.size(32.dp),
                                strokeWidth = 2.dp
                            )
                        },
                        modifier           = Modifier.fillMaxSize(),
                    )

                    // DFC flip hint
                    if (card.imageBackNormal != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            color    = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape    = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text     = if (showBackFace) "Tap to see front" else "Tap to flip",
                                style    = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(color = mc.surfaceVariant)

                // Foil toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.addcard_confirm_foil), Modifier.weight(1f), style = ty.bodyMedium, color = mc.textPrimary)
                    Switch(
                        checked         = isFoil,
                        onCheckedChange = { isFoil = it },
                        colors          = SwitchDefaults.colors(
                            checkedThumbColor   = mc.background,
                            checkedTrackColor   = mc.primaryAccent,
                            uncheckedThumbColor = mc.textDisabled,
                            uncheckedTrackColor = mc.surfaceVariant,
                        ),
                    )
                }

                // Quantity stepper
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.addcard_confirm_quantity), Modifier.weight(1f), style = ty.bodyMedium, color = mc.textPrimary)
                    IconButton(onClick = { if (qty > 1) qty-- }) {
                        Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.action_remove), tint = mc.textSecondary)
                    }
                    Text("$qty", style = ty.titleMedium, color = mc.primaryAccent)
                    IconButton(onClick = { qty++ }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add), tint = mc.textSecondary)
                    }
                }

                // Condition chips
                Text(stringResource(R.string.addcard_confirm_condition), style = ty.labelLarge, color = mc.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    conditions.forEach { c ->
                        FilterChip(
                            selected = c == condition,
                            onClick  = { condition = c },
                            label    = { Text(c, style = ty.labelMedium) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = mc.primaryAccent.copy(alpha = 0.18f),
                                selectedLabelColor     = mc.primaryAccent,
                                containerColor         = mc.surface,
                                labelColor             = mc.textSecondary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled             = true,
                                selected            = c == condition,
                                selectedBorderColor = mc.primaryAccent,
                                borderColor         = mc.surfaceVariant,
                            ),
                        )
                    }
                }

                // Language dropdown
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value         = language.uppercase(),
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text(stringResource(R.string.addcard_confirm_language), style = ty.labelLarge, color = mc.textSecondary) },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier      = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        textStyle     = ty.bodyMedium,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedTextColor     = mc.textPrimary,
                            unfocusedTextColor   = mc.textPrimary,
                            focusedBorderColor   = mc.primaryAccent,
                            unfocusedBorderColor = mc.surfaceVariant,
                        ),
                    )
                    ExposedDropdownMenu(
                        expanded         = expanded,
                        onDismissRequest = { expanded = false },
                        containerColor   = mc.surface,
                    ) {
                        languages.forEach { lang ->
                            DropdownMenuItem(
                                text    = { Text(lang.uppercase(), style = ty.bodyMedium, color = mc.textPrimary) },
                                onClick = { language = lang; expanded = false },
                            )
                        }
                    }
                }

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onDismiss,
                        modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_cancel), style = ty.labelLarge, color = mc.textSecondary)
                    }
                    Button(
                        onClick = { onConfirm(isFoil, condition, language, qty) },
                        colors  = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                        shape   = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.addcard_confirm_button), style = ty.labelLarge, color = mc.background)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
