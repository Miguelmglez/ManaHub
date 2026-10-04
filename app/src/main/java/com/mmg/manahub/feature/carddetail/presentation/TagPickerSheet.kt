package com.mmg.manahub.feature.carddetail.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.model.TagDictionaryEntry
import com.mmg.manahub.core.model.UserDefinedTag
import com.mmg.manahub.core.tagging.label
import com.mmg.manahub.core.ui.components.CardTagChip
import com.mmg.manahub.core.ui.components.CopyBadge
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaSize
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.SectionHeader
import com.mmg.manahub.core.ui.components.chipTonalColors
import com.mmg.manahub.core.ui.theme.BottomSheetShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Catalog tag management with persistent, independent selections. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TagPickerSheet(
    cardAutoTags: List<CardTag>,
    currentUserTags: List<CardTag>,
    suggestedTags: List<CardTag>,
    catalogEntries: List<TagDictionaryEntry>,
    userDefinedTags: List<UserDefinedTag>,
    catalogLoading: Boolean,
    catalogError: Boolean,
    onRetryCatalog: () -> Unit,
    onAddUserTag: (CardTag) -> Unit,
    onRemoveUserTag: (CardTag) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(query) { listState.scrollToItem(0) }
    val colors = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing

    val defaultExpandedKeys = remember(currentUserTags, suggestedTags) {
        buildSet {
            if (currentUserTags.isNotEmpty()) add("SELECTED")
            if (suggestedTags.isNotEmpty()) add("SUGGESTED")
            add("AUTO")
        }
    }

    val setSaver = Saver<MutableState<Set<String>>, List<String>>(
        save = { state -> state.value.toList() },
        restore = { list -> mutableStateOf(list.toSet()) },
    )
    var expandedSections by rememberSaveable(saver = setSaver) {
        mutableStateOf(defaultExpandedKeys)
    }

    val toggleSection: (String) -> Unit = { key ->
        expandedSections = if (key in expandedSections) {
            expandedSections - key
        } else {
            expandedSections + key
        }
    }

    val autoKeys = remember(cardAutoTags) { cardAutoTags.map { it.key }.toSet() }
    val selectedKeys = remember(currentUserTags) { currentUserTags.map { it.key }.toSet() }
    val catalog = remember(catalogEntries) { catalogEntries.map { CardTag(it.key, it.category) }.distinctBy { it.key } }
    val labels = remember(catalogEntries, userDefinedTags) {
        userDefinedTags.associate { it.key to it.label } +
            catalogEntries.associate { it.key to (it.labels["en"] ?: CardTag(it.key, it.category).displayLabel) }
    }
    val sortedCatalog = remember(catalog, labels) {
        catalog.sortedBy { labels.getValue(it.key).lowercase() }
    }
    val selectedTags = remember(currentUserTags) { currentUserTags.distinctBy { it.key } }
    val measuredTags = remember(sortedCatalog, selectedTags) {
        (sortedCatalog + selectedTags).distinctBy { it.key }
    }
    val measuredLabels = remember(measuredTags, labels) {
        measuredTags.associate { it.key to (labels[it.key] ?: it.label()) }
    }
    val catalogByKey = remember(catalog) { catalog.associateBy { it.key } }
    val searchQuery = query.trim()
    val available = remember(sortedCatalog, autoKeys, searchQuery, labels) {
        sortedCatalog.filter { tag ->
            tag.key !in autoKeys && (searchQuery.isEmpty() ||
                labels.getValue(tag.key).contains(searchQuery, ignoreCase = true) ||
                tag.key.contains(searchQuery, ignoreCase = true))
        }
            .groupBy { it.category }
    }
    val suggestions = remember(suggestedTags, catalogByKey, autoKeys, searchQuery, labels) {
        suggestedTags.mapNotNull { suggestion -> catalogByKey[suggestion.key] }
            .distinctBy { it.key }
            .filter {
                it.key !in autoKeys && (searchQuery.isEmpty() ||
                    labels.getValue(it.key).contains(searchQuery, ignoreCase = true))
            }
    }
    val toggle: (CardTag) -> Unit = { tag ->
        if (tag.key in selectedKeys) onRemoveUserTag(tag) else onAddUserTag(tag)
    }

    val stopNestedSheetDrag = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return if (available.y < 0f) {
                    Offset(0f, available.y)
                } else {
                    Offset.Zero
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = BottomSheetShape,
        containerColor = colors.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .imePadding()
        ) {
            Column(
                Modifier.padding(horizontal = spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            stringResource(R.string.action_done),
                            tint = colors.textSecondary
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.carddetail_tags_picker_title),
                            style = MaterialTheme.magicTypography.titleLarge,
                            color = colors.textPrimary
                        )
                        Text(
                            stringResource(R.string.carddetail_tags_picker_subtitle),
                            style = MaterialTheme.magicTypography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.carddetail_tags_picker_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    stringResource(R.string.carddetail_tags_picker_clear_search)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = CardShape,
                    textStyle = MaterialTheme.magicTypography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(spacing.xs))

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val density = LocalDensity.current
                val layoutDirection = LocalLayoutDirection.current
                val fontFamilyResolver = LocalFontFamilyResolver.current
                val chipTextStyle = MaterialTheme.magicTypography.labelSmall
                val resolvedTypeface = fontFamilyResolver.resolve(
                    chipTextStyle.fontFamily,
                    chipTextStyle.fontWeight ?: FontWeight.Normal,
                    chipTextStyle.fontStyle ?: FontStyle.Normal,
                    chipTextStyle.fontSynthesis ?: FontSynthesis.All,
                ).value
                val rowWidthPx = (constraints.maxWidth - with(density) {
                    spacing.lg.roundToPx() * 2 + spacing.md.roundToPx() * 2
                }).coerceAtLeast(1)
                val gapPx = with(density) { spacing.sm.roundToPx() }
                val minimumChipWidthPx = with(density) { 48.dp.roundToPx() }.coerceAtMost(rowWidthPx)
                val chipChromePx = with(density) {
                    10.dp.roundToPx() * 2 + 14.dp.roundToPx() + 4.dp.roundToPx()
                }
                val measurementKey = remember(measuredTags, measuredLabels, density, layoutDirection,
                    fontFamilyResolver, chipTextStyle, resolvedTypeface, rowWidthPx) { Any() }
                val measuredWidths by produceState<Pair<Any, Map<String, Int>>?>(
                    initialValue = null,
                    key1 = measurementKey,
                ) {
                    value = measurementKey to withContext(Dispatchers.Default) {
                        val measurer = TextMeasurer(fontFamilyResolver, density, layoutDirection, cacheSize = 0)
                        measuredTags.associate { tag ->
                            ensureActive()
                            val textWidth = measurer.measure(
                                text = AnnotatedString(measuredLabels.getValue(tag.key)),
                                style = chipTextStyle,
                                maxLines = 1,
                                softWrap = false,
                            ).size.width
                            tag.key to (textWidth + chipChromePx)
                                .coerceIn(minimumChipWidthPx, rowWidthPx)
                        }
                    }
                }
                val chipWidths = measuredWidths?.takeIf { it.first === measurementKey }?.second
                val catalogRows = remember(available, chipWidths, rowWidthPx, gapPx) {
                    chipWidths?.let { widths ->
                        available.mapValues { (_, tags) -> packTagRows(tags, widths, rowWidthPx, gapPx) }
                    }.orEmpty()
                }
                val selectedRows = remember(selectedTags, chipWidths, rowWidthPx, gapPx) {
                    chipWidths?.let { packTagRows(selectedTags, it, rowWidthPx, gapPx) }.orEmpty()
                }
                val suggestedRows = remember(suggestions, chipWidths, rowWidthPx, gapPx) {
                    chipWidths?.let { packTagRows(suggestions, it, rowWidthPx, gapPx) }.orEmpty()
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().nestedScroll(stopNestedSheetDrag),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
                    verticalArrangement = Arrangement.Top,
                ) {
                    if (query.isBlank()) {
                        val expanded = "SELECTED" in expandedSections
                        item(key = "section_selected", contentType = "summary_header") {
                            TagCategorySlice(first = true, last = !expanded || selectedRows.isEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                    SectionHeader(
                                        title = stringResource(R.string.carddetail_tags_picker_selected, currentUserTags.size)
                                            .split(" ·").firstOrNull() ?: "Selected tags",
                                        expanded = expanded,
                                        onToggle = { toggleSection("SELECTED") },
                                        icon = Icons.Default.Bookmark,
                                        titleColor = colors.textPrimary,
                                        iconColor = colors.primaryAccent,
                                        trailing = { CopyBadge(label = currentUserTags.size.toString()) },
                                    )
                                    if (expanded && currentUserTags.isEmpty()) {
                                        Text(
                                            stringResource(R.string.carddetail_tags_picker_selected_empty),
                                            style = MaterialTheme.magicTypography.bodySmall,
                                            color = colors.textSecondary,
                                        )
                                    }
                                }
                            }
                        }
                        val widths = chipWidths
                        if (expanded && widths != null) {
                            tagChoiceRows("selected", selectedRows, widths, measuredLabels, selectedKeys, onRemoveUserTag)
                        }
                    }

                    if (query.isBlank() || suggestions.isNotEmpty()) {
                        val expanded = "SUGGESTED" in expandedSections
                        item(key = "section_suggested", contentType = "summary_header") {
                            TagCategorySlice(first = true, last = !expanded || suggestedRows.isEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                    SectionHeader(
                                        title = stringResource(R.string.carddetail_tags_picker_suggested),
                                        expanded = expanded,
                                        onToggle = { toggleSection("SUGGESTED") },
                                        icon = Icons.Default.AutoAwesome,
                                        titleColor = colors.textPrimary,
                                        iconColor = colors.primaryAccent,
                                        trailing = { CopyBadge(label = suggestions.size.toString()) },
                                    )
                                    if (expanded && suggestions.isEmpty()) {
                                        Text(
                                            stringResource(
                                                if (query.isBlank()) R.string.carddetail_tags_picker_suggested_empty
                                                else R.string.carddetail_tags_picker_no_matches
                                            ),
                                            style = MaterialTheme.magicTypography.bodySmall,
                                            color = colors.textSecondary,
                                        )
                                    }
                                }
                            }
                        }
                        val widths = chipWidths
                        if (expanded && widths != null) {
                            tagChoiceRows("suggested", suggestedRows, widths, measuredLabels, selectedKeys, toggle)
                        }
                    }
                    if (query.isBlank()) {
                        spacedItem(key = "section_auto") {
                            val expanded = "AUTO" in expandedSections
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = CardShape,
                                color = colors.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, colors.surfaceVariant),
                            ) {
                                Column(
                                    modifier = Modifier.padding(spacing.md),
                                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                                ) {
                                    SectionHeader(
                                        title = stringResource(R.string.carddetail_tags_picker_auto),
                                        expanded = expanded,
                                        onToggle = { toggleSection("AUTO") },
                                        icon = Icons.Default.Lock,
                                        titleColor = colors.textPrimary,
                                        iconColor = colors.primaryAccent,
                                        trailing = { CopyBadge(label = cardAutoTags.size.toString()) }
                                    )
                                    if (expanded) {
                                        if (cardAutoTags.isEmpty()) {
                                            Text(
                                                stringResource(R.string.carddetail_tags_auto_empty),
                                                style = MaterialTheme.magicTypography.bodySmall,
                                                color = colors.textSecondary,
                                                modifier = Modifier.padding(vertical = spacing.xs)
                                            )
                                        } else {
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                cardAutoTags.distinctBy { it.key }.forEach { tag ->
                                                    CardTagChip(
                                                        label = labels[tag.key] ?: tag.label(),
                                                        category = tag.category
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    spacedItem(key = "catalog_header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = spacing.xs)
                        ) {
                            Text(
                                stringResource(R.string.carddetail_tags_picker_catalog),
                                style = MaterialTheme.magicTypography.titleMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                stringResource(
                                    R.string.carddetail_tags_picker_results,
                                    available.values.sumOf { it.size }
                                ),
                                style = MaterialTheme.magicTypography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }

                    if (catalogLoading || chipWidths == null) {
                        spacedItem(key = "catalog_loading") {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = colors.primaryAccent
                            )
                        }
                    }

                    if (catalogError) {
                        spacedItem(key = "catalog_error") {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                InlineErrorState(
                                    message = stringResource(R.string.carddetail_tags_picker_error),
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                                TextButton(onClick = onRetryCatalog, enabled = !catalogLoading) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }
                        }
                    }

                    if (available.isEmpty() && !catalogLoading && !catalogError) {
                        spacedItem(key = "catalog_empty") {
                            EmptyState(
                                title = stringResource(R.string.carddetail_tags_picker_no_matches),
                                icon = Icons.Default.Search,
                                modifier = Modifier.fillMaxWidth(),
                                compact = true
                            )
                        }
                    }

                    available.forEach { (category, tags) ->
                        val categoryKey = "CAT_${category.name}"
                        val expanded = (categoryKey in expandedSections) || query.isNotBlank()
                        val rows = catalogRows[category].orEmpty()
                        item(key = "category_header_${category.name}", contentType = "category_header") {
                            TagCategorySlice(first = true, last = !expanded || rows.isEmpty()) {
                                SectionHeader(
                                    title = category.displayLabel,
                                    expanded = expanded,
                                    onToggle = { toggleSection(categoryKey) },
                                    icon = categoryIcon(category),
                                    titleColor = colors.textPrimary,
                                    iconColor = colors.primaryAccent,
                                    trailing = { CopyBadge(label = tags.size.toString()) }
                                )
                            }
                        }
                        val measuredChipWidths = chipWidths
                        if (expanded && measuredChipWidths != null) {
                            itemsIndexed(
                                items = rows,
                                key = { _, row -> "category_row_${category.name}_${row.first().key}" },
                                contentType = { _, _ -> "catalog_tag_row" },
                            ) { index, row ->
                                TagCategorySlice(first = false, last = index == rows.lastIndex) {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                                    ) {
                                        row.forEach { tag ->
                                            TagSelectionChip(
                                                tag = tag,
                                                label = labels.getValue(tag.key),
                                                selected = tag.key in selectedKeys,
                                                onToggle = { toggle(tag) },
                                                modifier = Modifier.width(with(density) {
                                                    measuredChipWidths.getValue(tag.key).toDp()
                                                })
                                                    .heightIn(min = 48.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            MagicCtaButton(
                onClick = onDismiss,
                text = stringResource(R.string.action_done),
                style = MagicCtaStyle.Filled,
                color = MagicCtaColor.Primary,
                size = MagicCtaSize.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg, vertical = spacing.sm),
            )
        }
    }
}

private fun LazyListScope.spacedItem(key: String, content: @Composable () -> Unit) {
    item(key = key, contentType = key) {
        Box(Modifier.padding(bottom = MaterialTheme.spacing.md)) {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.tagChoiceRows(
    keyPrefix: String,
    rows: List<List<CardTag>>,
    widths: Map<String, Int>,
    labels: Map<String, String>,
    selectedKeys: Set<String>,
    onToggle: (CardTag) -> Unit,
) {
    itemsIndexed(
        items = rows,
        key = { _, row -> "${keyPrefix}_row_${row.first().key}" },
        contentType = { _, _ -> "catalog_tag_row" },
    ) { index, row ->
        val spacing = MaterialTheme.spacing
        val density = LocalDensity.current
        TagCategorySlice(first = false, last = index == rows.lastIndex) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                row.forEach { tag ->
                    TagSelectionChip(
                        tag = tag,
                        label = labels.getValue(tag.key),
                        selected = tag.key in selectedKeys,
                        onToggle = { onToggle(tag) },
                        modifier = Modifier.width(with(density) { widths.getValue(tag.key).toDp() }),
                    )
                }
            }
        }
    }
}

internal fun packTagRows(
    tags: List<CardTag>,
    widths: Map<String, Int>,
    availableWidth: Int,
    gap: Int,
): List<List<CardTag>> {
    require(availableWidth > 0 && gap >= 0)
    val rows = mutableListOf<List<CardTag>>()
    var row = mutableListOf<CardTag>()
    var usedWidth = 0
    tags.forEach { tag ->
        val chipWidth = widths.getValue(tag.key).coerceIn(1, availableWidth)
        val remainingWidth = availableWidth - usedWidth
        if (row.isNotEmpty() && (gap > remainingWidth || chipWidth > remainingWidth - gap)) {
            rows.add(row)
            row = mutableListOf()
            usedWidth = 0
        }
        if (row.isNotEmpty()) usedWidth += gap
        row.add(tag)
        usedWidth += chipWidth
    }
    if (row.isNotEmpty()) rows.add(row)
    return rows
}

@Composable
private fun TagCategorySlice(first: Boolean, last: Boolean, content: @Composable () -> Unit) {
    val spacing = MaterialTheme.spacing
    val squareCorner = CornerSize(0.dp)
    val shape = CardShape.copy(
        topStart = if (first) CardShape.topStart else squareCorner,
        topEnd = if (first) CardShape.topEnd else squareCorner,
        bottomStart = if (last) CardShape.bottomStart else squareCorner,
        bottomEnd = if (last) CardShape.bottomEnd else squareCorner,
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (last) spacing.md else 0.dp),
        shape = shape,
        color = MaterialTheme.magicColors.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Box(
            Modifier.padding(
                start = spacing.md,
                end = spacing.md,
                top = if (first) spacing.md else 0.dp,
                bottom = if (last) spacing.md else spacing.sm,
            )
        ) {
            content()
        }
    }
}

private fun categoryIcon(category: TagCategory): ImageVector = when (category) {
    TagCategory.ARCHETYPE -> Icons.Default.Category
    TagCategory.STRATEGY  -> Icons.Default.LocalOffer
    TagCategory.ROLE      -> Icons.Default.Tag
    TagCategory.TRIBAL    -> Icons.Default.LocalOffer
    TagCategory.KEYWORD   -> Icons.Default.LocalOffer
    TagCategory.TYPE      -> Icons.Default.Category
    TagCategory.CUSTOM    -> Icons.Default.Tag
}

@Composable
private fun TagSelectionChip(
    tag: CardTag,
    label: String,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(
        if (selected) R.string.carddetail_tags_remove_description
        else R.string.carddetail_tags_picker_add_description,
        label
    )
    val chipColors = tag.category.chipTonalColors(MaterialTheme.magicColors)
    CardTagChip(
        label = label,
        category = tag.category,
        modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics {
            contentDescription = description
            this.selected = selected
        },
        onClick = onToggle,
        leading = {
            Icon(
                imageVector = if (selected) Icons.Default.Close else Icons.Default.Add,
                contentDescription = null,
                tint = chipColors.content,
                modifier = Modifier.size(14.dp)
            )
        },
    )
}
