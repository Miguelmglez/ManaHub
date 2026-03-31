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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
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
                        val commonTypes = listOf(
                            "Creature", "Instant", "Sorcery",
                            "Enchantment", "Artifact", "Planeswalker", "Land", "Legendary",
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            commonTypes.forEach { type ->
                                val isSelected = uiState.cardType.contains(type, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        val new = if (isSelected)
                                            uiState.cardType.replace(type, "", ignoreCase = true).trim()
                                        else
                                            "${uiState.cardType} $type".trim()
                                        viewModel.setCardType(new)
                                    },
                                    label = { Text(type, style = ty.labelSmall) },
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
                            listOf("W", "U", "B", "R", "G", "C", "M").forEach { color ->
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
                        OperatorSelector(
                            selected = uiState.rarityOp,
                            onSelect = { op -> viewModel.setRarity(uiState.selectedRarity, op) },
                            options = listOf(
                                ComparisonOperator.EQUAL,
                                ComparisonOperator.GREATER_OR_EQUAL,
                                ComparisonOperator.LESS_OR_EQUAL,
                            ),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "common" to "●",
                                "uncommon" to "◆",
                                "rare" to "◈",
                                "mythic" to "✦",
                            ).forEach { (rarity, symbol) ->
                                val isSelected = uiState.selectedRarity == rarity
                                val color = when (rarity) {
                                    "common" -> Color(0xFFAAAAAA)
                                    "uncommon" -> Color(0xFFB0C4DE)
                                    "rare" -> Color(0xFFC9A84C)
                                    "mythic" -> Color(0xFFE8A030)
                                    else -> Color.White
                                }
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
                                                rarity.replaceFirstChar { it.uppercase() },
                                                style = ty.labelSmall,
                                            )
                                        }
                                    },
                                )
                            }
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
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(
                                "commander", "standard", "modern",
                                "legacy", "vintage", "pioneer", "pauper", "historic",
                            ).forEach { format ->
                                FilterChip(
                                    selected = uiState.selectedFormat == format,
                                    onClick = {
                                        viewModel.setFormat(
                                            if (uiState.selectedFormat == format) "" else format,
                                            uiState.formatLegal,
                                        )
                                    },
                                    label = {
                                        Text(
                                            format.replaceFirstChar { it.uppercase() },
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
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(
                                "Flying", "Haste", "Trample", "Lifelink",
                                "Deathtouch", "Vigilance", "Flash",
                                "Reach", "First Strike", "Hexproof",
                            ).forEach { kw ->
                                val isSelected = uiState.keyword.contains(kw, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setKeyword(if (isSelected) "" else kw) },
                                    label = { Text(kw, style = ty.labelSmall) },
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
