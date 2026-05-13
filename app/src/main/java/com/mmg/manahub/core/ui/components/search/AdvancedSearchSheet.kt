package com.mmg.manahub.core.ui.components.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.AdvancedSearchQuery
import com.mmg.manahub.core.domain.model.ComparisonOperator
import com.mmg.manahub.core.domain.model.SearchDirection
import com.mmg.manahub.core.domain.model.SearchOrder
import com.mmg.manahub.core.ui.components.ManaColorPicker
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedSearchSheet(
    onDismiss: () -> Unit,
    onSearch: (advancedQuery: AdvancedSearchQuery, rawScryfall: String) -> Unit,
    isCollectionMode: Boolean = false,
    viewModel: AdvancedSearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val scope = rememberCoroutineScope()
    var canDismiss by remember { mutableStateOf(false) }

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
        confirmValueChange = { newValue ->
            newValue != SheetValue.Hidden || canDismiss
        }
    )

    fun handleDismiss() {
        if (canDismiss) return
        canDismiss = true
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
                    .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
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
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = viewModel::clearAll) {
                    Text(
                        stringResource(R.string.advsearch_clear),
                        color = mc.lifeNegative,
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
                                style = ty.bodySmall,
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
                        icon = Icons.Default.Style
                    ) {
                        data class TypeOption(val scryfallValue: String, val labelRes: Int)
                        val typeOptions = listOf(
                            TypeOption("Creature",     R.string.cardtype_creature),
                            TypeOption("Instant",      R.string.cardtype_instant),
                            TypeOption("Sorcery",      R.string.cardtype_sorcery),
                            TypeOption("Enchantment",  R.string.cardtype_enchantment),
                            TypeOption("Artifact",     R.string.cardtype_artifact),
                            TypeOption("Planeswalker", R.string.cardtype_planeswalker),
                            TypeOption("Land",         R.string.cardtype_land),
                            TypeOption("Legendary",    R.string.cardtype_legendary),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            typeOptions.forEach { option ->
                                val isSelected = uiState.cardType.contains(option.scryfallValue, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        val new = if (isSelected)
                                            uiState.cardType.replace(option.scryfallValue, "", ignoreCase = true).trim()
                                        else
                                            "${uiState.cardType} ${option.scryfallValue}".trim()
                                        viewModel.setCardType(new)
                                    },
                                    label = { Text(stringResource(option.labelRes), style = ty.labelSmall) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = uiState.cardType,
                            onValueChange = viewModel::setCardType,
                            placeholder = {
                                Text(
                                    stringResource(R.string.advsearch_type_hint),
                                    color = mc.textDisabled,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = magicOutlinedTextFieldColors(mc),
                            singleLine = true,
                        )
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
                                FilterChip(
                                    selected = uiState.useColorIdentity == isIdentity,
                                    onClick = { viewModel.setUseColorIdentity(isIdentity) },
                                    label = { Text(label, style = ty.labelSmall) },
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
                                style = ty.bodySmall,
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
                                placeholder = { Text(stringResource(R.string.advsearch_mana_hint), color = mc.textDisabled) },
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    stringResource(R.string.advsearch_rarity_operator),
                                    style = ty.bodySmall,
                                    color = mc.textSecondary,
                                )
                                OperatorSelector(
                                    selected = uiState.rarityOp,
                                    onSelect = { op -> viewModel.setRarity(uiState.selectedRarity, op) },
                                    options = listOf(
                                        ComparisonOperator.EQUAL,
                                        ComparisonOperator.GREATER_OR_EQUAL,
                                        ComparisonOperator.LESS_OR_EQUAL,
                                    ),
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(
                                    "common"   to Pair("●", Color(0xFFAAAAAA)),
                                    "uncommon" to Pair("◆", Color(0xFFB0C4DE)),
                                    "rare"     to Pair("◈", Color(0xFFC9A84C)),
                                    "mythic"   to Pair("✦", Color(0xFFE8A030)),
                                ).forEach { (rarity, symbolColor) ->
                                    val (symbol, color) = symbolColor
                                    val isSelected = uiState.selectedRarity == rarity
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.setRarity(
                                                if (isSelected) "" else rarity,
                                                uiState.rarityOp,
                                            )
                                        },
                                        label = {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(symbol, color = color, fontSize = 14.sp)
                                                Text(
                                                    stringResource(when (rarity) {
                                                        "common"   -> R.string.stats_rarity_common
                                                        "uncommon" -> R.string.stats_rarity_uncommon
                                                        "rare"     -> R.string.stats_rarity_rare
                                                        else       -> R.string.stats_rarity_mythic
                                                    }),
                                                    style = ty.labelSmall,
                                                )
                                            }
                                        },
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
                                    style = ty.bodyMedium,
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
                                                    style = ty.labelSmall,
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
                                                style = ty.labelSmall,
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
                        collapsedByDefault = true,
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
                                            fontSize = 12.sp,
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
                                            fontSize = 12.sp,
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
                        collapsedByDefault = true,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("≤", fontSize = 18.sp, color = mc.textSecondary)
                            OutlinedTextField(
                                value = uiState.priceMax,
                                onValueChange = { v -> viewModel.setPrice(v, uiState.priceCurrency) },
                                placeholder = { Text(stringResource(R.string.advsearch_price_hint), color = mc.textDisabled) },
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
                                    FilterChip(
                                        selected = uiState.priceCurrency == curr,
                                        onClick = { viewModel.setPrice(uiState.priceMax, curr) },
                                        label = { Text(symbol, style = ty.labelMedium) },
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
                        collapsedByDefault = true,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(R.string.advsearch_format_legal),
                                style = ty.bodySmall,
                                color = mc.textSecondary,
                            )
                            Switch(
                                checked = uiState.formatLegal,
                                onCheckedChange = { legal ->
                                    viewModel.setFormat(uiState.selectedFormat, legal)
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
                                style = ty.bodySmall,
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
                                FilterChip(
                                    selected = uiState.selectedFormat == option.scryfallValue,
                                    onClick = {
                                        viewModel.setFormat(
                                            if (uiState.selectedFormat == option.scryfallValue) "" else option.scryfallValue,
                                            uiState.formatLegal,
                                        )
                                    },
                                    label = {
                                        Text(
                                            stringResource(option.labelRes),
                                            style = ty.labelSmall,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                // ── Keywords ──
                item {
                    SearchSection(
                        title = stringResource(R.string.advsearch_section_keyword),
                        icon = Icons.Default.VpnKey,
                        collapsedByDefault = true,
                    ) {
                        data class KeywordOption(val scryfallValue: String, val labelRes: Int)
                        val keywordOptions = listOf(
                            KeywordOption("Flying",       R.string.keyword_flying),
                            KeywordOption("Haste",        R.string.keyword_haste),
                            KeywordOption("Trample",      R.string.keyword_trample),
                            KeywordOption("Lifelink",     R.string.keyword_lifelink),
                            KeywordOption("Deathtouch",   R.string.keyword_deathtouch),
                            KeywordOption("Vigilance",    R.string.keyword_vigilance),
                            KeywordOption("Flash",        R.string.keyword_flash),
                            KeywordOption("Reach",        R.string.keyword_reach),
                            KeywordOption("First Strike", R.string.keyword_first_strike),
                            KeywordOption("Hexproof",     R.string.keyword_hexproof),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            keywordOptions.forEach { option ->
                                val isSelected = uiState.keyword.contains(option.scryfallValue, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setKeyword(if (isSelected) "" else option.scryfallValue) },
                                    label = { Text(stringResource(option.labelRes), style = ty.labelSmall) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = uiState.keyword,
                            onValueChange = viewModel::setKeyword,
                            placeholder = {
                                Text(
                                    stringResource(R.string.advsearch_keyword_hint),
                                    color = mc.textDisabled,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = magicOutlinedTextFieldColors(mc),
                            singleLine = true,
                        )
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
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (index == 0)
                                                viewModel.setFilterWishlist(if (isSelected) null else true)
                                            else
                                                viewModel.setFilterForTrade(if (isSelected) null else true)
                                        },
                                        label = { Text(label, style = ty.labelSmall) },
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
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                com.mmg.manahub.core.domain.model.CardTag.canonical.forEach { tag ->
                                    val isSelected = uiState.filterTags.contains(tag.key)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.toggleFilterTag(tag.key) },
                                        label = { Text(tag.label, style = ty.labelSmall) },
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Sort ──
                if (!isCollectionMode) item {
                    SearchSection(
                        title = stringResource(R.string.advsearch_section_sort),
                        icon = Icons.Default.Sort,
                        collapsedByDefault = true,
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SearchOrder.entries.forEach { order ->
                                FilterChip(
                                    selected = uiState.orderBy == order,
                                    onClick = { viewModel.setOrder(order, uiState.orderDirection) },
                                    label = {
                                        Text(
                                            stringResource(when(order) {
                                                SearchOrder.NAME -> R.string.advsearch_sort_name
                                                SearchOrder.CMC -> R.string.advsearch_sort_cmc
                                                SearchOrder.PRICE -> R.string.advsearch_sort_price
                                                SearchOrder.RARITY -> R.string.advsearch_sort_rarity
                                                SearchOrder.RELEASED -> R.string.advsearch_sort_released
                                                SearchOrder.COLOR -> R.string.advsearch_sort_color
                                            }),
                                            style = ty.labelSmall,
                                        )
                                    },
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                SearchDirection.ASC to stringResource(R.string.advsearch_dir_asc),
                                SearchDirection.DESC to stringResource(R.string.advsearch_dir_desc),
                            ).forEach { (dir, label) ->
                                FilterChip(
                                    selected = uiState.orderDirection == dir,
                                    onClick = { viewModel.setOrder(uiState.orderBy, dir) },
                                    label = { Text(label, style = ty.labelSmall) },
                                )
                            }
                        }
                    }
                }
            }

            // ── Search / Apply button ───────────────────────────────────────────
            Button(
                onClick = {
                    onSearch(uiState.currentQuery, uiState.builtQuery)
                    handleDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                enabled = if (isCollectionMode) true
                          else uiState.builtQuery.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = mc.primaryAccent,
                    disabledContainerColor = mc.surfaceVariant,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (isCollectionMode) R.string.advsearch_apply_button
                        else R.string.advsearch_search_button
                    ),
                    style = ty.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

// ── Collapsible section ──────────────────────────────────────────────────────

@Composable
fun SearchSection(
    title: String,
    icon: ImageVector? = null,
    collapsedByDefault: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(!collapsedByDefault) }
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = mc.surface,
        border = BorderStroke(0.5.dp, mc.surfaceVariant.copy(alpha = 0.5f)),
        shadowElevation = if (expanded) 1.dp else 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (expanded) mc.primaryAccent else mc.textSecondary
                    )
                }
                Text(
                    title,
                    style = ty.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (expanded) mc.textPrimary else mc.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = mc.textDisabled,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
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
