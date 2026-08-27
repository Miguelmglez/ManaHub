package com.mmg.manahub.core.ui.components.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.mmg.manahub.R
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.ComparisonOperator
import com.mmg.manahub.core.model.SearchDirection
import com.mmg.manahub.core.model.SearchOrder
import com.mmg.manahub.core.tagging.label
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicFilterChip
import com.mmg.manahub.core.ui.components.ManaColorPicker
import com.mmg.manahub.core.ui.components.SectionHeader
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedSearchSheet(
    onDismiss: () -> Unit,
    onSearch: (advancedQuery: AdvancedSearchQuery, rawScryfall: String) -> Unit,
    isCollectionMode: Boolean = false,
    getAvailableTags: () -> Set<com.mmg.manahub.core.model.CardTag> = { emptySet() },
    /**
     * A pre-decomposed structured query to seed this sheet's fields with on open (Suggestions Tab
     * UI Polish plan, W11/D8 — Deck Analysis's "Browse for &lt;Category&gt;" entry point,
     * [com.mmg.manahub.core.ui.components.CardSearchSheet]'s own `initialAdvancedQuery` param).
     * `null` (default) keeps every existing call site unchanged. Applied via
     * [AdvancedSearchViewModel.seedFrom] in a one-shot [androidx.compose.runtime.LaunchedEffect]
     * below, keyed on this value's own identity/content — see that effect's comment for the known
     * caveat around a cached `koinViewModel()` instance across repeated fixed-position opens
     * (mirrors `feedback_koin_viewmodel_overlay_key_trap` memory's documented risk shape).
     */
    initialAdvancedQuery: AdvancedSearchQuery? = null,
    viewModel: AdvancedSearchViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val scope = rememberCoroutineScope()
    var canDismiss by remember { mutableStateOf(false) }

    // W11 — one-shot seed from a caller-supplied structured preset. Runs once per distinct
    // [initialAdvancedQuery] value (mirrors CardSearchSheet's own initialScryfallQuery/
    // initialCollectionTagKeys preset pattern) so the sheet opens ALREADY SHOWING the translated
    // filters instead of a blank form.
    LaunchedEffect(initialAdvancedQuery) {
        if (initialAdvancedQuery != null && !initialAdvancedQuery.isEmpty()) {
            viewModel.seedFrom(initialAdvancedQuery)
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = available
        }
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )

    fun handleDismiss() {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = mc.backgroundSecondary,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets(0) },
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding(),
        ) {
            // ── Header ──────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = ::handleDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = mc.textPrimary
                    )
                }
                Text(
                    stringResource(R.string.advsearch_title),
                    style = ty.titleLarge,
                    color = mc.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = viewModel::clearAll) {
                    Text(
                        stringResource(R.string.advsearch_clear),
                        color = mc.lifeNegative,
                        style = ty.titleMedium
                    )
                }
            }

            // ── Scrollable form ─────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .nestedScroll(nestedScrollConnection),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {

                // ── Name ──
                item {
                    SearchSection(
                        title = stringResource(R.string.advsearch_section_name),
                        icon = Icons.Default.Search
                    ) {
                        OutlinedTextField(
                            value = uiState.nameValue,
                            onValueChange = viewModel::setName,
                            placeholder = {
                                Text(
                                    stringResource(R.string.advsearch_name_hint),
                                    color = mc.textDisabled,
                                    style = ty.bodyLarge
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = magicOutlinedTextFieldColors(mc),
                            singleLine = true,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = uiState.nameExact,
                                onCheckedChange = viewModel::setNameExact,
                                colors = CheckboxDefaults.colors(checkedColor = mc.primaryAccent),
                            )
                            Text(
                                stringResource(R.string.advsearch_name_exact),
                                style = ty.bodyMedium,
                                color = mc.textSecondary,
                            )
                        }
                    }
                }

                // ── Oracle text ──
                item {
                    SearchSection(
                        title = stringResource(R.string.advsearch_section_oracle),
                        icon = Icons.Default.Description
                    ) {
                        OutlinedTextField(
                            value = uiState.oracleText,
                            onValueChange = viewModel::setOracleText,
                            placeholder = {
                                Text(
                                    stringResource(R.string.advsearch_oracle_hint),
                                    color = mc.textDisabled,
                                    style = ty.bodyLarge
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = magicOutlinedTextFieldColors(mc),
                            maxLines = 2,
                        )
                    }
                }

                // ── Card type ──
                item {
                    SearchSection(
                        title = stringResource(R.string.advsearch_section_type),
                        icon = Icons.Default.Style,
                        titleColor = mc.textPrimary,
                        iconColor = mc.primaryAccent,
                    ) {
                        var showTypePicker by remember { mutableStateOf(false) }

                        Surface(
                            onClick = { showTypePicker = true },
                            shape = RoundedCornerShape(12.dp),
                            color = mc.surface,
                            border = BorderStroke(
                                width = if (uiState.cardType.isNotEmpty()) 1.5.dp else 0.5.dp,
                                color = if (uiState.cardType.isNotEmpty()) mc.primaryAccent else mc.surfaceVariant,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = if (uiState.cardType.isNotEmpty()) mc.primaryAccent else mc.textDisabled,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = if (uiState.cardType.isEmpty())
                                        stringResource(R.string.advsearch_type_hint)
                                    else
                                        "${uiState.cardType.size} types selected",
                                    style = ty.bodyLarge,
                                    color = if (uiState.cardType.isNotEmpty()) mc.primaryAccent else mc.textDisabled,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = mc.textDisabled,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        if (uiState.cardType.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                uiState.cardType.forEach { type ->
                                    InputChip(
                                        selected = true,
                                        onClick = { viewModel.toggleCardType(type) },
                                        label = { Text(type, style = ty.labelMedium) },
                                        trailingIcon = {
                                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = uiState.cardTypeMatchAll,
                                onCheckedChange = viewModel::setCardTypeMatchAll,
                                colors = CheckboxDefaults.colors(checkedColor = mc.primaryAccent),
                            )
                            Text(
                                "Match exactly ALL selected types (AND)",
                                style = ty.bodyMedium,
                                color = mc.textSecondary,
                            )
                        }
                        // Suggestions Tab UI Polish plan (W11): backs SearchCriterion.CardType
                        // .exclude -- Deck Analysis Curve sections seed this (e.g. excluding lands
                        // from a mana-value bucket).
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = uiState.cardTypeExclude,
                                onCheckedChange = viewModel::setCardTypeExclude,
                                colors = CheckboxDefaults.colors(checkedColor = mc.primaryAccent),
                            )
                            Text(
                                "Exclude selected types instead",
                                style = ty.bodyMedium,
                                color = mc.textSecondary,
                            )
                        }

                        if (showTypePicker) {
                            CardTypePickerSheet(
                                selectedTypes = uiState.cardType,
                                onToggleType = viewModel::toggleCardType,
                                onDismiss = { showTypePicker = false },
                            )
                        }
                    }
                }

                // ── Card function ── (works in both Scryfall-search and Collection-filter modes,
                // same as Card type above — not gated by isCollectionMode)
                item {
                    SearchSection(
                        title = stringResource(R.string.advsearch_section_function),
                        icon = Icons.Default.Bolt,
                        titleColor = mc.textPrimary,
                        iconColor = mc.goldMtg,
                    ) {
                        var showFunctionPicker by remember { mutableStateOf(false) }

                        Surface(
                            onClick = { showFunctionPicker = true },
                            shape = RoundedCornerShape(12.dp),
                            color = mc.surface,
                            border = BorderStroke(
                                width = if (uiState.cardFunction.isNotEmpty()) 1.5.dp else 0.5.dp,
                                color = if (uiState.cardFunction.isNotEmpty()) mc.primaryAccent else mc.surfaceVariant,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = if (uiState.cardFunction.isNotEmpty()) mc.primaryAccent else mc.textDisabled,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = if (uiState.cardFunction.isEmpty())
                                        stringResource(R.string.advsearch_function_hint)
                                    else
                                        stringResource(R.string.advsearch_function_selected_count, uiState.cardFunction.size),
                                    style = ty.bodyLarge,
                                    color = if (uiState.cardFunction.isNotEmpty()) mc.primaryAccent else mc.textDisabled,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = mc.textDisabled,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        if (uiState.cardFunction.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                uiState.cardFunction.forEach { value ->
                                    val label = com.mmg.manahub.core.model.CardFunctionOption.allFunctions
                                        .find { it.scryfallValue == value }?.label ?: value
                                    InputChip(
                                        selected = true,
                                        onClick = { viewModel.toggleCardFunction(value) },
                                        label = { Text(label, style = ty.labelMedium) },
                                        trailingIcon = {
                                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = uiState.cardFunctionMatchAll,
                                onCheckedChange = viewModel::setCardFunctionMatchAll,
                                colors = CheckboxDefaults.colors(checkedColor = mc.primaryAccent),
                            )
                            Text(
                                "Match ALL selected functions (AND)",
                                style = ty.bodyMedium,
                                color = mc.textSecondary,
                            )
                        }

                        if (showFunctionPicker) {
                            CardFunctionPickerSheet(
                                selectedFunctions = uiState.cardFunction,
                                onToggleFunction = viewModel::toggleCardFunction,
                                onDismiss = { showFunctionPicker = false },
                            )
                        }
                    }
                }

                // ── Mana production (Suggestions Tab UI Polish plan, W11) ── seeded ONLY from
                // Deck Analysis's "Browse for X" translation -- no manual picker exists, this is
                // a read-only summary + a single clear action, rendered only while active.
                if (uiState.manaProduction != null) {
                    item {
                        val mp = uiState.manaProduction!!
                        SearchSection(
                            title = stringResource(R.string.advsearch_section_mana_production),
                            icon = Icons.Default.Bolt,
                        ) {
                            val minDistinctColors = mp.minDistinctColors
                            val summary = when {
                                mp.colors.isNotEmpty() -> stringResource(
                                    R.string.advsearch_mana_production_colors,
                                    mp.colors.sorted().joinToString(""),
                                )
                                minDistinctColors != null -> stringResource(
                                    R.string.advsearch_mana_production_threshold,
                                    minDistinctColors,
                                )
                                else -> ""
                            }
                            InputChip(
                                selected = true,
                                onClick = viewModel::clearManaProduction,
                                label = { Text(summary, style = ty.labelMedium) },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                                },
                            )
                        }
                    }
                }

                // ── Curated oracle match (Suggestions Tab UI Polish plan, W11) ── seeded ONLY
                // from Deck Analysis's TagDictionary-translated role fragments. Read-only (each
                // term is a curated compile-time constant, not user-editable) -- one clear action
                // removes the whole criterion. Still a real, structured AdvancedSearchSheet field,
                // never a raw string in CardSearchSheet's plain search bar (D8's own mandate).
                if (uiState.oracleTerms != null) {
                    item {
                        val terms = uiState.oracleTerms!!
                        SearchSection(
                            title = stringResource(R.string.advsearch_section_oracle_terms),
                            icon = Icons.Default.Description,
                        ) {
                            val allTerms = terms.allOf + terms.anyOfGroups.flatten() + terms.typeLineAnyOf
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                allTerms.forEach { term ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = mc.surfaceVariant,
                                    ) {
                                        Text(
                                            text = term,
                                            style = ty.labelMedium,
                                            color = mc.textPrimary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                            TextButton(onClick = viewModel::clearOracleTerms) {
                                Text(stringResource(R.string.advsearch_clear), color = mc.lifeNegative, style = ty.bodyMedium)
                            }
                        }
                    }
                }

                // ── Colors ──
                item {
                    SearchSection(
                        title = stringResource(R.string.advsearch_section_colors),
                        icon = Icons.Default.Palette
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                false to stringResource(R.string.advsearch_color_mode_color),
                                true to stringResource(R.string.advsearch_color_mode_identity)
                            ).forEach { (isIdentity, label) ->
                                MagicFilterChip(
                                    selected = uiState.useColorIdentity == isIdentity,
                                    onClick = { viewModel.setUseColorIdentity(isIdentity) },
                                    label = label,
                                )
                            }
                        }
                        ManaColorPicker(
                            selectedColors = uiState.selectedColors,
                            onToggleColor = viewModel::toggleColor,
                            modifier = Modifier.fillMaxWidth(),
                            colors = listOf("W", "U", "B", "R", "G", "C")
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = uiState.colorsExact,
                                onCheckedChange = viewModel::setColorsExact,
                                colors = CheckboxDefaults.colors(checkedColor = mc.primaryAccent),
                            )
                            Text(
                                stringResource(R.string.advsearch_colors_exact),
                                style = ty.bodyMedium,
                                color = mc.textSecondary,
                            )
                        }
                    }
                }

                // ── Mana value (CMC) ──
                item {
                    SearchSection(
                        title = stringResource(R.string.advsearch_section_mana),
                        icon = Icons.Default.FlashOn
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OperatorSelector(
                                selected = uiState.manaCostOp,
                                onSelect = { op -> viewModel.setManaCost(uiState.manaCostValue, op) },
                            )
                            OutlinedTextField(
                                value = uiState.manaCostValue,
                                onValueChange = { v -> viewModel.setManaCost(v, uiState.manaCostOp) },
                                placeholder = { Text(stringResource(R.string.advsearch_mana_hint), color = mc.textDisabled, style = ty.bodyLarge) },
                                modifier = Modifier.width(100.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = magicOutlinedTextFieldColors(mc),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                            )
                        }
                    }
                }

                // ── Rarity ──
                item {
                    SearchSection(
                        title = stringResource(R.string.advsearch_section_rarity),
                        icon = Icons.Default.Diamond
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf("common",
                                    "uncommon",
                                    "rare",
                                    "mythic",
                                ).forEach { rarity ->
                                    val isSelected = uiState.selectedRarity.contains(rarity)
                                    MagicFilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.updateRarity(rarity) },
                                        label = stringResource(when (rarity) {
                                            "common"   -> R.string.stats_rarity_common
                                            "uncommon" -> R.string.stats_rarity_uncommon
                                            "rare"     -> R.string.stats_rarity_rare
                                            else       -> R.string.stats_rarity_mythic
                                        }),
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Set / Collection ──
                item {
                    SearchSection(
                        title = stringResource(R.string.advsearch_section_set),
                        icon = Icons.Default.Layers
                    ) {
                        var showSetPicker by remember { mutableStateOf(false) }
                        val selectedSets = uiState.selectedSets

                        Surface(
                            onClick = { showSetPicker = true },
                            shape = RoundedCornerShape(12.dp),
                            color = mc.surface,
                            border = BorderStroke(
                                width = if (selectedSets.isNotEmpty()) 1.5.dp else 0.5.dp,
                                color = if (selectedSets.isNotEmpty()) mc.primaryAccent
                                        else mc.surfaceVariant,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = if (selectedSets.isNotEmpty()) mc.primaryAccent
                                           else mc.textDisabled,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = if (selectedSets.isEmpty())
                                        stringResource(R.string.advsearch_set_hint)
                                    else
                                        stringResource(
                                            R.string.advsearch_set_selected_count,
                                            selectedSets.size,
                                        ),
                                    style = ty.bodyLarge,
                                    color = if (selectedSets.isNotEmpty()) mc.primaryAccent
                                            else mc.textDisabled,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = mc.textDisabled,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        // Chips de sets seleccionados
                        if (selectedSets.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                selectedSets.forEach { set ->
                                    InputChip(
                                        selected = true,
                                        onClick = { viewModel.toggleSet(set) },
                                        label = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(set.iconSvgUri)
                                                        .decoderFactory(SvgDecoder.Factory())
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    colorFilter = ColorFilter.tint(mc.primaryAccent),
                                                )
                                                Text(
                                                    set.code.uppercase(),
                                                    style = ty.labelMedium,
                                                )
                                            }
                                        },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp),
                                            )
                                        },
                                    )
                                }
                                if (selectedSets.size > 1) {
                                    InputChip(
                                        selected = false,
                                        onClick = viewModel::clearSets,
                                        label = {
                                            Text(
                                                stringResource(R.string.advsearch_clear),
                                                style = ty.labelMedium,
                                                color = mc.lifeNegative,
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        if (showSetPicker) {
                            SetPickerSheet(
                                selectedSetCodes = selectedSets.map { it.code }.toSet(),
                                onToggleSet = viewModel::toggleSet,
                                onDismiss = { showSetPicker = false },
                            )
                        }
                    }
                }

                // ── Power / Toughness ──
                item {
                    SearchSection(
                        title = stringResource(R.string.advsearch_section_stats),
                        icon = Icons.Default.BarChart,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Power
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_battle),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = mc.textSecondary
                                )
                                OperatorSelector(
                                    selected = uiState.powerOp,
                                    onSelect = { op -> viewModel.setPower(uiState.powerValue, op) },
                                )
                                OutlinedTextField(
                                    value = uiState.powerValue,
                                    onValueChange = { v -> viewModel.setPower(v, uiState.powerOp) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = magicOutlinedTextFieldColors(mc),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    placeholder = {
                                        Text(
                                            stringResource(R.string.advsearch_power_hint),
                                            color = mc.textDisabled,
                                            style = ty.bodyLarge,
                                            maxLines = 1
                                        )
                                    },
                                )
                            }
                            // Toughness
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = mc.textSecondary
                                )
                                OperatorSelector(
                                    selected = uiState.toughnessOp,
                                    onSelect = { op -> viewModel.setToughness(uiState.toughnessValue, op) },
                                )
                                OutlinedTextField(
                                    value = uiState.toughnessValue,
                                    onValueChange = { v -> viewModel.setToughness(v, uiState.toughnessOp) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = magicOutlinedTextFieldColors(mc),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    placeholder = {
                                        Text(
                                            stringResource(R.string.advsearch_toughness_hint),
                                            color = mc.textDisabled,
                                            style = ty.bodyLarge,
                                            maxLines = 1
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                // ── Max price ──
                item {
                    SearchSection(
                        title = stringResource(R.string.advsearch_section_price),
                        icon = Icons.Default.MonetizationOn,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("≤", fontSize = 18.sp, color = mc.textSecondary)
                            OutlinedTextField(
                                value = uiState.priceMax,
                                onValueChange = { v -> viewModel.setPrice(v, uiState.priceCurrency) },
                                placeholder = { Text(stringResource(R.string.advsearch_price_hint), color = mc.textDisabled, style = ty.bodyLarge) },
                                modifier = Modifier.width(100.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = magicOutlinedTextFieldColors(mc),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    "eur" to stringResource(R.string.price_symbol_eur),
                                    "usd" to stringResource(R.string.price_symbol_usd)
                                ).forEach { (curr, symbol) ->
                                    MagicFilterChip(
                                        selected = uiState.priceCurrency == curr,
                                        onClick = { viewModel.setPrice(uiState.priceMax, curr) },
                                        label = symbol,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Format legality ──
                item {
                    SearchSection(
                        title = stringResource(R.string.advsearch_section_format),
                        icon = Icons.Default.Gavel,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(R.string.advsearch_format_legal),
                                style = ty.bodyMedium,
                                color = mc.textSecondary,
                            )
                            Switch(
                                checked = uiState.formatLegal,
                                onCheckedChange = { legal ->
                                    viewModel.updateLegalSwitch(legal)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = mc.lifePositive,
                                    uncheckedTrackColor = mc.lifeNegative,
                                ),
                            )
                            Text(
                                if (uiState.formatLegal)
                                    stringResource(R.string.advsearch_format_is_legal)
                                else
                                    stringResource(R.string.advsearch_format_is_banned),
                                style = ty.bodyMedium,
                                color = if (uiState.formatLegal) mc.lifePositive else mc.lifeNegative,
                            )
                        }
                        data class FormatOption(val scryfallValue: String, val labelRes: Int)
                        val formatOptions = listOf(
                            FormatOption("commander", R.string.format_commander),
                            FormatOption("standard",  R.string.format_standard),
                            FormatOption("modern",    R.string.format_modern),
                            FormatOption("legacy",    R.string.format_legacy),
                            FormatOption("vintage",   R.string.format_vintage),
                            FormatOption("pioneer",   R.string.format_pioneer),
                            FormatOption("pauper",    R.string.format_pauper),
                            FormatOption("historic",  R.string.format_historic),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            formatOptions.forEach { option ->
                                MagicFilterChip(
                                    selected = uiState.selectedFormat.contains(option.scryfallValue),
                                    onClick = {
                                       viewModel.updateFormat(option.scryfallValue)
                                    },
                                    label = stringResource(option.labelRes),
                                )
                            }
                        }
                    }
                }


                // ── Collection status (collection mode only) ──
                if (isCollectionMode) {
                    item {
                        SearchSection(
                            title = stringResource(R.string.advsearch_section_collection_status),
                            icon = Icons.Default.CollectionsBookmark
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(
                                    stringResource(R.string.advsearch_filter_wishlist) to (uiState.filterWishlist == true),
                                    stringResource(R.string.advsearch_filter_for_trade) to (uiState.filterForTrade == true),
                                ).forEachIndexed { index, (label, isSelected) ->
                                    MagicFilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (index == 0)
                                                viewModel.setFilterWishlist(if (isSelected) null else true)
                                            else
                                                viewModel.setFilterForTrade(if (isSelected) null else true)
                                        },
                                        label = label,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Tags (collection mode only) ──
                if (isCollectionMode) {
                    item {
                        SearchSection(
                            title = stringResource(R.string.advsearch_section_tags),
                            icon = Icons.Default.LocalOffer
                        ) {
                            var showTagPicker by remember { mutableStateOf(false) }

                            Surface(
                                onClick = { showTagPicker = true },
                                shape = RoundedCornerShape(12.dp),
                                color = mc.surface,
                                border = BorderStroke(
                                    width = if (uiState.filterTags.isNotEmpty()) 1.5.dp else 0.5.dp,
                                    color = if (uiState.filterTags.isNotEmpty()) mc.primaryAccent else mc.surfaceVariant,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = if (uiState.filterTags.isNotEmpty()) mc.primaryAccent else mc.textDisabled,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        text = if (uiState.filterTags.isEmpty())
                                            "Search by tags"
                                        else
                                            "${uiState.filterTags.size} tags selected",
                                        style = ty.bodyLarge,
                                        color = if (uiState.filterTags.isNotEmpty()) mc.primaryAccent else mc.textDisabled,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = mc.textDisabled,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            if (uiState.filterTags.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 8.dp),
                                ) {
                                    uiState.filterTags.forEach { key ->
                                        val tagLabel = getAvailableTags().find { it.key == key }?.label() ?: key
                                        InputChip(
                                            selected = true,
                                            onClick = { viewModel.toggleFilterTag(key) },
                                            label = { Text(tagLabel, style = ty.labelMedium) },
                                            trailingIcon = {
                                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                                            }
                                        )
                                    }
                                }
                            }

                            if (showTagPicker) {
                                TagPickerSheet(
                                    availableTags = getAvailableTags(),
                                    selectedTags = uiState.filterTags,
                                    onToggleTag = viewModel::toggleFilterTag,
                                    onDismiss = { showTagPicker = false },
                                )
                            }
                        }
                    }
                }

                // ── Sort ──
                if (!isCollectionMode) item {
                    SearchSection(
                        title = stringResource(R.string.advsearch_section_sort),
                        icon = Icons.Default.Sort,
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SearchOrder.entries.forEach { order ->
                                MagicFilterChip(
                                    selected = uiState.orderBy == order,
                                    onClick = { viewModel.setOrder(order, uiState.orderDirection) },
                                    label = stringResource(when(order) {
                                        SearchOrder.NAME -> R.string.advsearch_sort_name
                                        SearchOrder.CMC -> R.string.advsearch_sort_cmc
                                        SearchOrder.PRICE_EUR -> R.string.advsearch_sort_price_eur
                                        SearchOrder.PRICE_USD -> R.string.advsearch_sort_price_usd
                                        SearchOrder.RARITY -> R.string.advsearch_sort_rarity
                                        SearchOrder.RELEASED -> R.string.advsearch_sort_released
                                        SearchOrder.COLOR -> R.string.advsearch_sort_color
                                    }),
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                SearchDirection.ASC to stringResource(R.string.advsearch_dir_asc),
                                SearchDirection.DESC to stringResource(R.string.advsearch_dir_desc),
                            ).forEach { (dir, label) ->
                                MagicFilterChip(
                                    selected = uiState.orderDirection == dir,
                                    onClick = { viewModel.setOrder(uiState.orderBy, dir) },
                                    label = label,
                                )
                            }
                        }
                    }
                }
            }

            // ── Search / Apply button ───────────────────────────────────────────
            MagicCtaButton(
                text = stringResource(
                    if (isCollectionMode) R.string.advsearch_apply_button
                    else R.string.advsearch_search_button
                ),
                onClick = {
                    onSearch(uiState.currentQuery, uiState.builtQuery)
                    handleDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                icon = { Icon(Icons.Default.Search, contentDescription = null) },
                enabled = if (isCollectionMode) true else uiState.builtQuery.isNotBlank(),
            )
        }
    }
}

// ── Comparison operator selector ─────────────────────────────────────────────

@Composable
private fun OperatorSelector(
    selected: ComparisonOperator,
    onSelect: (ComparisonOperator) -> Unit,
    options: List<ComparisonOperator> = ComparisonOperator.entries,
) {
    var expanded by remember { mutableStateOf(false) }
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = mc.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.width(64.dp).height(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(selected.symbol, style = ty.titleMedium.copy(fontWeight = FontWeight.Bold), color = mc.primaryAccent)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = mc.surface,
            shape = RoundedCornerShape(12.dp)
        ) {
            options.forEach { op ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${op.symbol}  (${op.name.lowercase().replace('_', ' ')})",
                            style = ty.bodyMedium,
                            color = mc.textPrimary,
                        )
                    },
                    onClick = { onSelect(op); expanded = false },
                )
            }
        }
    }
}

// ── Helper: themed text field colors ─────────────────────────────────────────

@Composable
private fun magicOutlinedTextFieldColors(mc: com.mmg.manahub.core.ui.theme.MagicColors) =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = mc.primaryAccent,
        unfocusedBorderColor = mc.surfaceVariant,
        cursorColor = mc.primaryAccent,
        focusedTextColor = mc.textPrimary,
        unfocusedTextColor = mc.textPrimary,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardTypePickerSheet(
    selectedTypes: Set<String>,
    onToggleType: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Search query filters within each section; manual expand/collapse state is the source of
    // truth and is OR'd with "has a search match" while a query is active (restored as-is on clear).
    var searchQuery by remember { mutableStateOf("") }
    var manualExpandedSections by remember { mutableStateOf(setOf<com.mmg.manahub.core.model.CardTypeSection>()) }

    val query = searchQuery.trim()
    val isSearching = query.isNotBlank()

    val sectionRows = remember(query) {
        com.mmg.manahub.core.model.CardTypeSection.entries.mapNotNull { section ->
            val all = com.mmg.manahub.core.model.CardTypeOption.bySection[section].orEmpty()
            val visible = if (isSearching) {
                all.filter { option ->
                    option.label.contains(query, ignoreCase = true) ||
                        option.scryfallValue.contains(query, ignoreCase = true)
                }
            } else {
                all
            }
            if (isSearching && visible.isEmpty()) null else Triple(section, all.size, visible)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
        contentWindowInsets = { WindowInsets(0) },
        dragHandle = null,
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f).navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = mc.textPrimary)
                }
                Text(
                    "Card Types",
                    style = ty.titleLarge,
                    color = mc.textPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Search card types…",
                        color = mc.textDisabled,
                        style = ty.bodyLarge,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = mc.textDisabled)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = mc.textDisabled)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = magicOutlinedTextFieldColors(mc),
                singleLine = true,
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                sectionRows.forEach { (section, totalCount, options) ->
                    val isExpanded = isSearching || manualExpandedSections.contains(section)

                    item(key = "header:${section.name}") {
                        SectionHeader(
                            title = "${section.label} ($totalCount)",
                            expanded = isExpanded,
                            onToggle = {
                                manualExpandedSections = if (manualExpandedSections.contains(section)) {
                                    manualExpandedSections - section
                                } else {
                                    manualExpandedSections + section
                                }
                            },
                            titleColor = mc.textPrimary,
                        )
                    }

                    if (isExpanded) {
                        items(
                            count = options.size,
                            key = { i -> "${section.name}:${options[i].scryfallValue}" }
                        ) { index ->
                            val option = options[index]
                            val isSelected = selectedTypes.contains(option.scryfallValue)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable { onToggleType(option.scryfallValue) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onToggleType(option.scryfallValue) },
                                    colors = CheckboxDefaults.colors(checkedColor = mc.primaryAccent)
                                )
                                Text(
                                    option.label,
                                    style = ty.bodyLarge,
                                    color = mc.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardFunctionPickerSheet(
    selectedFunctions: Set<String>,
    onToggleFunction: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Search query filters within each section; manual expand/collapse state is the source of
    // truth and is OR'd with "has a search match" while a query is active (restored as-is on clear).
    var searchQuery by remember { mutableStateOf("") }
    var manualExpandedSections by remember { mutableStateOf(setOf<com.mmg.manahub.core.model.CardFunctionSection>()) }

    val query = searchQuery.trim()
    val isSearching = query.isNotBlank()

    val sectionRows = remember(query) {
        com.mmg.manahub.core.model.CardFunctionSection.entries.mapNotNull { section ->
            val all = com.mmg.manahub.core.model.CardFunctionOption.bySection[section].orEmpty()
            val visible = if (isSearching) {
                all.filter { option ->
                    option.label.contains(query, ignoreCase = true) ||
                        option.scryfallValue.contains(query, ignoreCase = true)
                }
            } else {
                all
            }
            if (isSearching && visible.isEmpty()) null else Triple(section, all.size, visible)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
        contentWindowInsets = { WindowInsets(0) },
        dragHandle = null,
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f).navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = mc.textPrimary)
                }
                Text(
                    "Card Function",
                    style = ty.titleLarge,
                    color = mc.textPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Search functions…",
                        color = mc.textDisabled,
                        style = ty.bodyLarge,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = mc.textDisabled)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = mc.textDisabled)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = magicOutlinedTextFieldColors(mc),
                singleLine = true,
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                sectionRows.forEach { (section, totalCount, options) ->
                    val isExpanded = isSearching || manualExpandedSections.contains(section)

                    item(key = "header:${section.name}") {
                        SectionHeader(
                            title = "${section.label} ($totalCount)",
                            expanded = isExpanded,
                            onToggle = {
                                manualExpandedSections = if (manualExpandedSections.contains(section)) {
                                    manualExpandedSections - section
                                } else {
                                    manualExpandedSections + section
                                }
                            },
                            titleColor = mc.textPrimary,
                        )
                    }

                    if (isExpanded) {
                        items(
                            count = options.size,
                            key = { i -> "${section.name}:${options[i].scryfallValue}" }
                        ) { index ->
                            val option = options[index]
                            val isSelected = selectedFunctions.contains(option.scryfallValue)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable { onToggleFunction(option.scryfallValue) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onToggleFunction(option.scryfallValue) },
                                    colors = CheckboxDefaults.colors(checkedColor = mc.primaryAccent)
                                )
                                Text(
                                    option.label,
                                    style = ty.bodyLarge,
                                    color = mc.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagPickerSheet(
    availableTags: Set<com.mmg.manahub.core.model.CardTag>,
    selectedTags: Set<String>,
    onToggleTag: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Convert to list for LazyColumn, sorted by label
    val tagList = remember(availableTags) { 
        availableTags.toList().sortedBy { it.label() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
        contentWindowInsets = { WindowInsets(0) },
        dragHandle = null,
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f).navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = mc.textPrimary)
                }
                Text(
                    "Collection Tags",
                    style = ty.titleLarge,
                    color = mc.textPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
            if (tagList.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No tags in your collection", style = ty.bodyLarge, color = mc.textSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        count = tagList.size,
                        key = { i -> tagList[i].key }
                    ) { index ->
                        val tag = tagList[index]
                        val isSelected = selectedTags.contains(tag.key)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleTag(tag.key) },
                                colors = CheckboxDefaults.colors(checkedColor = mc.primaryAccent)
                            )
                            Text(
                                tag.label(),
                                style = ty.bodyLarge,
                                color = mc.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
