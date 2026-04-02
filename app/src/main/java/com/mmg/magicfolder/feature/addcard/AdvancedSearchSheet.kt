package com.mmg.magicfolder.feature.addcard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.mmg.magicfolder.R
import com.mmg.magicfolder.core.domain.model.AdvancedSearchQuery
import com.mmg.magicfolder.core.domain.model.ComparisonOperator
import com.mmg.magicfolder.core.domain.model.SearchDirection
import com.mmg.magicfolder.core.domain.model.SearchOrder
import com.mmg.magicfolder.core.ui.components.ManaSymbolImage
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedSearchSheet(
    onDismiss: () -> Unit,
    onSearch: (advancedQuery: AdvancedSearchQuery, rawScryfall: String) -> Unit,
    viewModel: AdvancedSearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = mc.backgroundSecondary,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentWindowInsets = { WindowInsets(0) }
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.advsearch_title),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                )
                TextButton(onClick = viewModel::clearAll) {
                    Text(
                        stringResource(R.string.advsearch_clear),
                        color = mc.lifeNegative,
                    )
                }
            }

            // ── Real-time query preview ─────────────────────────────────────────
            AnimatedVisibility(visible = uiState.builtQuery.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = mc.surface,
                    border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.3f)),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("⚙", fontSize = 14.sp, color = mc.primaryAccent)
                        Text(
                            text = uiState.builtQuery,
                            style = ty.bodySmall,
                            color = mc.primaryAccent,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ── Scrollable form ─────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {

                // ── Name ──
                item {
                    SearchSection(title = stringResource(R.string.advsearch_section_name)) {
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
                            shape = RoundedCornerShape(10.dp),
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
                    SearchSection(title = stringResource(R.string.advsearch_section_oracle)) {
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
                            shape = RoundedCornerShape(10.dp),
                            colors = magicOutlinedTextFieldColors(mc),
                            maxLines = 2,
                        )
                    }
                }

                // ── Card type ──
                item {
                    SearchSection(title = stringResource(R.string.advsearch_section_type)) {
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
                            shape = RoundedCornerShape(10.dp),
                            colors = magicOutlinedTextFieldColors(mc),
                            singleLine = true,
                        )
                    }
                }

                // ── Colors ──
                item {
                    SearchSection(title = stringResource(R.string.advsearch_section_colors)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(false to "Color", true to "Identity").forEach { (isIdentity, label) ->
                                FilterChip(
                                    selected = uiState.useColorIdentity == isIdentity,
                                    onClick = { viewModel.setUseColorIdentity(isIdentity) },
                                    label = { Text(label, style = ty.labelSmall) },
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            listOf("W", "U", "B", "R", "G", "C").forEach { color ->
                                val isSelected = uiState.selectedColors.contains(color)
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected)
                                                mc.primaryAccent.copy(alpha = 0.2f)
                                            else Color.Transparent,
                                        )
                                        .border(
                                            width = if (isSelected) 2.dp else 0.dp,
                                            color = mc.primaryAccent,
                                            shape = CircleShape,
                                        )
                                        .clickable { viewModel.toggleColor(color) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    ManaSymbolImage(token = color, size = 32.dp)
                                }
                            }
                        }
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
                    SearchSection(title = stringResource(R.string.advsearch_section_mana)) {
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
                                placeholder = { Text("0..15", color = mc.textDisabled) },
                                modifier = Modifier.width(80.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = magicOutlinedTextFieldColors(mc),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                            )
                            Text("CMC", style = ty.bodySmall, color = mc.textDisabled)
                        }
                    }
                }

                // ── Rarity ──
                item {
                    SearchSection(title = stringResource(R.string.advsearch_section_rarity)) {
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
                    SearchSection(title = stringResource(R.string.advsearch_section_set)) {
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
                        collapsedByDefault = true,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(R.string.advsearch_power),
                                style = ty.bodySmall,
                                color = mc.textSecondary,
                                modifier = Modifier.width(60.dp),
                            )
                            OperatorSelector(
                                selected = uiState.powerOp,
                                onSelect = { op -> viewModel.setPower(uiState.powerValue, op) },
                            )
                            OutlinedTextField(
                                value = uiState.powerValue,
                                onValueChange = { v -> viewModel.setPower(v, uiState.powerOp) },
                                modifier = Modifier.width(70.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = magicOutlinedTextFieldColors(mc),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                placeholder = { Text("*", color = mc.textDisabled) },
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(R.string.advsearch_toughness),
                                style = ty.bodySmall,
                                color = mc.textSecondary,
                                modifier = Modifier.width(60.dp),
                            )
                            OperatorSelector(
                                selected = uiState.toughnessOp,
                                onSelect = { op -> viewModel.setToughness(uiState.toughnessValue, op) },
                            )
                            OutlinedTextField(
                                value = uiState.toughnessValue,
                                onValueChange = { v -> viewModel.setToughness(v, uiState.toughnessOp) },
                                modifier = Modifier.width(70.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = magicOutlinedTextFieldColors(mc),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                placeholder = { Text("*", color = mc.textDisabled) },
                            )
                        }
                    }
                }

                // ── Max price ──
                item {
                    SearchSection(
                        title = stringResource(R.string.advsearch_section_price),
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
                                placeholder = { Text("0.00", color = mc.textDisabled) },
                                modifier = Modifier.width(100.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = magicOutlinedTextFieldColors(mc),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("eur" to "€", "usd" to "$").forEach { (curr, symbol) ->
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
                            shape = RoundedCornerShape(10.dp),
                            colors = magicOutlinedTextFieldColors(mc),
                            singleLine = true,
                        )
                    }
                }

                // ── Sort ──
                item {
                    SearchSection(
                        title = stringResource(R.string.advsearch_section_sort),
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
                                            order.name.lowercase().replaceFirstChar { it.uppercase() },
                                            style = ty.labelSmall,
                                        )
                                    },
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                SearchDirection.ASC to "↑ ASC",
                                SearchDirection.DESC to "↓ DESC",
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

            // ── Search button ───────────────────────────────────────────────────
            Button(
                onClick = {
                    onSearch(uiState.currentQuery, uiState.builtQuery)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(52.dp),
                enabled = uiState.builtQuery.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = mc.primaryAccent,
                    disabledContainerColor = mc.surfaceVariant,
                ),
            ) {
                Text(
                    stringResource(R.string.advsearch_search_button),
                    style = ty.labelLarge,
                )
            }
        }
    }
}

// ── Collapsible section ──────────────────────────────────────────────────────

@Composable
private fun SearchSection(
    title: String,
    collapsedByDefault: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(!collapsedByDefault) }
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = ty.labelMedium, color = mc.textSecondary)
            Text(
                if (expanded) "▲" else "▼",
                fontSize = 10.sp,
                color = mc.textDisabled,
            )
        }
        HorizontalDivider(color = mc.surfaceVariant, thickness = 0.5.dp)
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
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
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.width(64.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text(selected.symbol, style = ty.bodyMedium, color = mc.textPrimary)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = mc.surface,
        ) {
            options.forEach { op ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${op.symbol}  (${op.name.lowercase().replace('_', ' ')})",
                            style = ty.bodySmall,
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
private fun magicOutlinedTextFieldColors(mc: com.mmg.magicfolder.core.ui.theme.MagicColors) =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = mc.primaryAccent,
        unfocusedBorderColor = mc.primaryAccent.copy(alpha = 0.25f),
        cursorColor = mc.primaryAccent,
        focusedTextColor = mc.textPrimary,
        unfocusedTextColor = mc.textPrimary,
        focusedContainerColor = mc.surface,
        unfocusedContainerColor = mc.surface,
    )
