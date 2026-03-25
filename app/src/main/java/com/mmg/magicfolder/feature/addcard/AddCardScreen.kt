package com.mmg.magicfolder.feature.addcard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.ui.components.ManaCostImages
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography

// ─────────────────────────────────────────────────────────────────────────────
//  AddCardScreen — tabbed entry point for adding cards to the collection.
//
//  Tab 0 "BUSCAR":  text search with debounce → results list → confirm sheet
//  Tab 1 "ESCANEAR": triggers navigation to the existing ScannerScreen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCardScreen(
    onBack:         () -> Unit,
    onScannerClick: () -> Unit,
    viewModel:      AddCardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors     = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add card", style = typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textPrimary,
                ),
            )
        },
        containerColor = colors.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Mode tabs ─────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = 0,  // always visually on "Buscar"
                containerColor   = colors.background,
                contentColor     = colors.primaryAccent,
            ) {
                Tab(
                    selected = true,
                    onClick  = { /* already here */ },
                    text     = {
                        Text("BUSCAR", style = typography.labelSmall, color = colors.primaryAccent)
                    },
                )
                Tab(
                    selected = false,
                    onClick  = onScannerClick,
                    text     = {
                        Text("ESCANEAR", style = typography.labelSmall, color = colors.textDisabled)
                    },
                )
            }

            // ── Search tab content ────────────────────────────────────────────
            SearchTabContent(
                uiState        = uiState,
                onQueryChange  = viewModel::onQueryChange,
                onCardSelected = viewModel::onCardSelected,
            )
        }

        // ── Error snackbar ────────────────────────────────────────────────────
        uiState.error?.let { err ->
            LaunchedEffect(err) { viewModel.onErrorDismissed() }
        }

        // ── Success feedback ──────────────────────────────────────────────────
        if (uiState.addedSuccessfully) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(1500)
                viewModel.onSuccessDismissed()
            }
        }
    }

    // ── Confirm sheet (shown after tapping a card result) ─────────────────────
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
}

// ─────────────────────────────────────────────────────────────────────────────
//  Search tab content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchTabContent(
    uiState:        AddCardUiState,
    onQueryChange:  (String) -> Unit,
    onCardSelected: (Card) -> Unit,
) {
    val colors        = MaterialTheme.magicColors
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search field
        OutlinedTextField(
            value         = uiState.query,
            onValueChange = onQueryChange,
            modifier      = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .focusRequester(focusRequester),
            placeholder   = {
                Text(
                    text  = "Nombre de la carta...",
                    style = MaterialTheme.magicTypography.bodyMedium,
                    color = colors.textDisabled,
                )
            },
            leadingIcon   = {
                Icon(Icons.Default.Search, contentDescription = null, tint = colors.textDisabled)
            },
            trailingIcon  = if (uiState.query.isNotEmpty()) {{
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Borrar", tint = colors.textSecondary)
                }
            }} else null,
            singleLine    = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedContainerColor   = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedTextColor        = colors.textPrimary,
                unfocusedTextColor      = colors.textPrimary,
                focusedBorderColor      = colors.primaryAccent,
                unfocusedBorderColor    = colors.primaryAccent.copy(alpha = 0.25f),
                cursorColor             = colors.primaryAccent,
            ),
            shape = RoundedCornerShape(12.dp),
        )

        // Loading
        if (uiState.isSearching) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color    = colors.primaryAccent,
            )
        }

        // Results
        LazyColumn(
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(uiState.results, key = { it.scryfallId }) { card ->
                CardSearchItem(card = card, onClick = { onCardSelected(card) })
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color     = MaterialTheme.magicColors.surfaceVariant,
                )
            }

            // Empty state (only after typing)
            if (uiState.results.isEmpty() && uiState.query.length >= 2 && !uiState.isSearching) {
                item {
                    Box(
                        modifier         = Modifier.fillParentMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text  = "No se encontraron cartas",
                            style = MaterialTheme.magicTypography.bodyMedium,
                            color = colors.textDisabled,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Individual card row in results
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CardSearchItem(card: Card, onClick: () -> Unit) {
    val colors = MaterialTheme.magicColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        // Art thumbnail
        AsyncImage(
            model              = card.imageArtCrop ?: card.imageNormal,
            contentDescription = card.name,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .size(width = 64.dp, height = 44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.surface),
        )

        // Name + mana cost
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text  = card.name,
                style = MaterialTheme.magicTypography.labelLarge,
                color = colors.textPrimary,
                maxLines = 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                card.manaCost?.let {
                    ManaCostImages(manaCost = it, symbolSize = 16.dp)
                }
                Text(
                    text  = card.setCode.uppercase(),
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = colors.textDisabled,
                )
            }
        }

        // Price
        card.priceUsd?.let {
            Text(
                text  = "$${String.format("%.2f", it)}",
                style = MaterialTheme.magicTypography.bodyMedium,
                color = colors.goldMtg,
            )
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
    val languages  = listOf("en", "ja", "de", "fr", "es", "pt", "it", "ko", "ru")
    var isFoil    by remember { mutableStateOf(false) }
    var condition by remember { mutableStateOf("NM") }
    var language  by remember { mutableStateOf("en") }
    var qty       by remember { mutableIntStateOf(1) }

    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = mc.backgroundSecondary,
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(card.name, style = ty.titleMedium, color = mc.textPrimary)
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
            HorizontalDivider(color = mc.surfaceVariant)

            // Foil toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Foil", Modifier.weight(1f), style = ty.bodyMedium, color = mc.textPrimary)
                Switch(
                    checked         = isFoil,
                    onCheckedChange = { isFoil = it },
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor  = mc.background,
                        checkedTrackColor  = mc.primaryAccent,
                        uncheckedThumbColor = mc.textDisabled,
                        uncheckedTrackColor = mc.surfaceVariant,
                    ),
                )
            }

            // Quantity stepper
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quantity", Modifier.weight(1f), style = ty.bodyMedium, color = mc.textPrimary)
                IconButton(onClick = { if (qty > 1) qty-- }) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = mc.textSecondary)
                }
                Text("$qty", style = ty.titleMedium, color = mc.primaryAccent)
                IconButton(onClick = { qty++ }) {
                    Icon(Icons.Default.Add, contentDescription = "Increase", tint = mc.textSecondary)
                }
            }

            // Condition chips
            Text("Condition", style = ty.labelLarge, color = mc.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                conditions.forEach { c ->
                    FilterChip(
                        selected = c == condition,
                        onClick  = { condition = c },
                        label    = { Text(c, style = ty.labelMedium) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor     = mc.primaryAccent.copy(alpha = 0.18f),
                            selectedLabelColor         = mc.primaryAccent,
                            containerColor             = mc.surface,
                            labelColor                 = mc.textSecondary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled          = true,
                            selected         = c == condition,
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
                    label         = { Text("Language", style = ty.labelMedium, color = mc.textSecondary) },
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier      = Modifier.menuAnchor().fillMaxWidth(),
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
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", style = ty.labelLarge, color = mc.textSecondary)
                }
                Button(
                    onClick = { onConfirm(isFoil, condition, language, qty) },
                    colors  = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                    shape   = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                ) {
                    Text("Add to collection", style = ty.labelLarge, color = mc.background)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
