package com.mmg.manahub.feature.rules

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.model.rules.RulesDestination
import com.mmg.manahub.core.model.rules.RulesNode
import com.mmg.manahub.core.model.rules.RulesNodeKind
import com.mmg.manahub.core.model.rules.RulesUpdateResult
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.HexGridBackground
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaSize
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.OracleText
import com.mmg.manahub.core.ui.components.SectionHeader
import com.mmg.manahub.core.ui.theme.BottomSheetShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.rules.resources.Res
import com.mmg.manahub.feature.rules.resources.rules_back
import com.mmg.manahub.feature.rules.resources.rules_browse
import com.mmg.manahub.feature.rules.resources.rules_checking
import com.mmg.manahub.feature.rules.resources.rules_clear
import com.mmg.manahub.feature.rules.resources.rules_document
import com.mmg.manahub.feature.rules.resources.rules_effective
import com.mmg.manahub.feature.rules.resources.rules_error
import com.mmg.manahub.feature.rules.resources.rules_hint
import com.mmg.manahub.feature.rules.resources.rules_info
import com.mmg.manahub.feature.rules.resources.rules_loading
import com.mmg.manahub.feature.rules.resources.rules_missing
import com.mmg.manahub.feature.rules.resources.rules_missing_body
import com.mmg.manahub.feature.rules.resources.rules_next
import com.mmg.manahub.feature.rules.resources.rules_no_results
import com.mmg.manahub.feature.rules.resources.rules_official
import com.mmg.manahub.feature.rules.resources.rules_open_reference
import com.mmg.manahub.feature.rules.resources.rules_previous
import com.mmg.manahub.feature.rules.resources.rules_reload
import com.mmg.manahub.feature.rules.resources.rules_retry
import com.mmg.manahub.feature.rules.resources.rules_source
import com.mmg.manahub.feature.rules.resources.rules_title
import com.mmg.manahub.feature.rules.resources.rules_up_to_date
import com.mmg.manahub.feature.rules.resources.rules_update
import com.mmg.manahub.feature.rules.resources.rules_update_error
import com.mmg.manahub.feature.rules.resources.rules_updated
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    state: RulesUiState,
    onQuery: (String) -> Unit,
    onReference: (RulesDestination.Reference) -> Unit,
    onBrowse: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onUpdate: () -> Unit,
    onReload: () -> Unit,
    onPage: (Int) -> Unit,
) {
    val colors = MaterialTheme.magicColors
    val type = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    var showInfo by rememberSaveable { mutableStateOf(false) }
    val edition = state.edition
    val referenceTitles = remember(edition?.id) { edition?.nodes?.associate { it.id to it.title }.orEmpty() }
    val listState = rememberLazyListState()
    var positionedReference by rememberSaveable { mutableStateOf<String?>(null) }
    var publishedPage by remember { mutableStateOf<Pair<String, Int>?>(null) }

    LaunchedEffect(state.query, state.results.offset, state.searching, state.loading) {
        val page = state.query to state.results.offset
        if (!state.searching && !state.loading && publishedPage != page) {
            if (publishedPage != null) listState.scrollToItem(0)
            publishedPage = page
        }
    }

    val target = edition?.nodes?.firstOrNull { it.id == state.reference }
    val reader = state.reference != null
    val related = state.reference?.takeIf { it.startsWith("related:") }?.removePrefix("related:")?.split(',')
    val relatedMissing = related?.any { ref -> edition?.nodes?.none { it.id == ref } == true } == true
    val parent = target?.let { if (it.kind == RulesNodeKind.SUBRULE || it.kind == RulesNodeKind.RULE) it.id.substringBefore('.') else it.id }

    val visible = remember(edition?.id, state.reference, state.query, state.results) {
        when {
            edition == null -> emptyList()
            related != null -> edition.nodes.filter { it.id in related }
            reader && target == null -> emptyList()
            reader && target?.kind == RulesNodeKind.CHAPTER -> edition.nodes.filter { it.id == target.id || it.parentId == target.id }
            reader -> edition.nodes.filter { it.id == parent || it.parentId == parent || it.parentId?.substringBefore('.') == parent }
            state.query.isNotBlank() -> state.results.nodes
            else -> edition.nodes.filter { it.kind in setOf(RulesNodeKind.INTRODUCTION, RulesNodeKind.CHAPTER) || it.id == "glossary" }
        }
    }

    LaunchedEffect(edition?.id, state.reference) {
        val referenceKey = "${edition?.id}:${state.reference}"
        if (reader && target != null && positionedReference != referenceKey) {
            val index = visible.indexOfFirst { it.id == target.id }
            if (index >= 0) {
                listState.scrollToItem(index)
                positionedReference = referenceKey
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        HexGridBackground(
            modifier = Modifier.fillMaxSize(),
            color = colors.primaryAccent.copy(alpha = 0.05f)
        )

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(Res.string.rules_title),
                            style = type.titleLarge,
                            color = colors.textPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.rules_back),
                                tint = colors.primaryAccent
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showInfo = true },
                            enabled = edition != null
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(Res.string.rules_info),
                                tint = if (edition != null) colors.goldMtg else colors.textSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.backgroundSecondary.copy(alpha = 0.9f)
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding()
            ) {
                if (!reader && !state.failed) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQuery,
                        placeholder = {
                            Text(
                                text = stringResource(Res.string.rules_hint),
                                color = colors.textSecondary,
                                style = type.bodyLarge
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = colors.textSecondary
                            )
                        },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { onQuery("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = stringResource(Res.string.rules_clear),
                                        tint = colors.textSecondary
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primaryAccent,
                            unfocusedBorderColor = colors.surfaceVariant,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            cursorColor = colors.primaryAccent,
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.lg),
                    )
                }

                if (state.updateResult is RulesUpdateResult.Installed) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.lg, vertical = spacing.xs),
                        shape = CardShape,
                        color = colors.goldMtg.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, colors.goldMtg.copy(alpha = 0.4f)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(Res.string.rules_updated),
                                style = type.bodyMedium,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            MagicCtaButton(
                                onClick = onReload,
                                text = stringResource(Res.string.rules_reload),
                                style = MagicCtaStyle.Outlined,
                                color = MagicCtaColor.Gold,
                                size = MagicCtaSize.Compact,
                            )
                        }
                    }
                }

                when {
                    state.loading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(spacing.xl),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(spacing.md)
                            ) {
                                CircularProgressIndicator(color = colors.primaryAccent)
                                Text(
                                    text = stringResource(Res.string.rules_loading),
                                    style = type.bodyMedium,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                    state.failed -> {
                        FullErrorState(
                            message = stringResource(Res.string.rules_error),
                            retryLabel = stringResource(Res.string.rules_retry),
                            onRetry = onRetry
                        )
                    }
                    reader && target == null && (related == null || relatedMissing) -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(spacing.xl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(spacing.md)
                        ) {
                            Text(
                                text = stringResource(Res.string.rules_missing),
                                style = type.titleMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = stringResource(Res.string.rules_missing_body),
                                style = type.bodyMedium,
                                color = colors.textSecondary
                            )
                            MagicCtaButton(
                                onClick = onBrowse,
                                text = stringResource(Res.string.rules_browse),
                                style = MagicCtaStyle.Filled,
                                color = MagicCtaColor.Primary
                            )
                        }
                    }
                    state.searching -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(spacing.xl),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = colors.primaryAccent)
                        }
                    }
                    state.searchFailed -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(spacing.xl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(spacing.md)
                        ) {
                            Text(
                                text = stringResource(Res.string.rules_error),
                                style = type.bodyLarge,
                                color = colors.lifeNegative
                            )
                            MagicCtaButton(
                                onClick = { onQuery(state.query) },
                                text = stringResource(Res.string.rules_retry),
                                style = MagicCtaStyle.Outlined,
                                color = MagicCtaColor.Primary
                            )
                        }
                    }
                    !reader && state.query.isNotBlank() && visible.isEmpty() -> {
                        EmptyState(
                            title = stringResource(Res.string.rules_no_results),
                            actionLabel = stringResource(Res.string.rules_clear),
                            onAction = { onQuery("") }
                        )
                    }
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(spacing.md)
                    ) {
                        items(visible, key = { "${edition?.id}:${it.id}" }) { node ->
                            val selected = reader && node.id == state.reference
                            val isSubrule = node.kind == RulesNodeKind.SUBRULE
                            val clickable = !reader || related != null || node.kind == RulesNodeKind.SECTION || node.kind == RulesNodeKind.GLOSSARY

                            val cleanTitle = remember(node.id, node.title) {
                                val trimmedTitle = node.title.trimEnd('.')
                                if (trimmedTitle.equals(node.id, ignoreCase = true) || trimmedTitle.startsWith(node.id + " ", ignoreCase = true)) {
                                    trimmedTitle.removePrefix(node.id).trimStart('.', ' ')
                                } else {
                                    node.title
                                }
                            }

                            val cleanedParagraphs = remember(node.id, node.kind, node.paragraphs) {
                                node.paragraphs.mapNotNull { p ->
                                    val cleaned = cleanParagraph(p, node)
                                    if (cleaned.isNotBlank()) cleaned else null
                                }
                            }

                            val cardBorder = when {
                                selected -> BorderStroke(1.5.dp, colors.goldMtg)
                                isSubrule -> BorderStroke(1.dp, colors.surfaceVariant)
                                node.kind == RulesNodeKind.CHAPTER || node.kind == RulesNodeKind.SECTION -> BorderStroke(1.dp, colors.primaryAccent.copy(alpha = 0.4f))
                                else -> BorderStroke(1.dp, colors.surfaceVariant)
                            }

                            val cardBackground = when {
                                selected -> colors.surfaceVariant
                                node.kind == RulesNodeKind.CHAPTER || node.kind == RulesNodeKind.SECTION -> colors.backgroundSecondary
                                else -> colors.surface
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (isSubrule) Modifier.padding(start = spacing.lg) else Modifier)
                                    .then(
                                        if (clickable) {
                                            Modifier.clickable {
                                                onReference(RulesDestination.Reference(edition?.id, node.id))
                                            }
                                        } else Modifier
                                    ),
                                shape = CardShape,
                                color = cardBackground,
                                border = cardBorder,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(spacing.lg),
                                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                        modifier = Modifier.semantics { heading() }
                                    ) {
                                        RuleIdBadge(id = node.id, kind = node.kind)

                                        if (cleanTitle.isNotEmpty()) {
                                            Text(
                                                text = cleanTitle,
                                                style = type.titleMedium,
                                                color = if (selected) colors.goldMtg else colors.textPrimary,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }

                                    if (reader) {
                                        if (cleanedParagraphs.isNotEmpty()) {
                                            SelectionContainer {
                                                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                                    cleanedParagraphs.forEach { paragraph ->
                                                        OracleText(
                                                            text = paragraph,
                                                            style = type.bodyLarge.copy(color = colors.textPrimary)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (node.references.isNotEmpty()) {
                                            var expandedRefs by rememberSaveable(node.id) { mutableStateOf(false) }
                                            val displayRefs = if (expandedRefs) node.references else node.references.take(12)

                                            Spacer(Modifier.height(spacing.xs))

                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                                                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                                            ) {
                                                displayRefs.forEach { ref ->
                                                    val refLabel = if (ref.startsWith("glossary:")) {
                                                        referenceTitles[ref].orEmpty().ifEmpty { ref.removePrefix("glossary:") }
                                                    } else ref

                                                    Surface(
                                                        shape = MaterialTheme.shapes.extraSmall,
                                                        color = colors.secondaryAccent.copy(alpha = 0.12f),
                                                        border = BorderStroke(1.dp, colors.secondaryAccent.copy(alpha = 0.3f)),
                                                        modifier = Modifier.clickable {
                                                            onReference(RulesDestination.Reference(edition?.id, ref))
                                                        }
                                                    ) {
                                                        Text(
                                                            text = stringResource(Res.string.rules_open_reference, refLabel),
                                                            style = type.labelSmall,
                                                            color = colors.secondaryAccent,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            if (!expandedRefs && node.references.size > 12) {
                                                MagicCtaButton(
                                                    onClick = { expandedRefs = true },
                                                    text = stringResource(Res.string.rules_next),
                                                    style = MagicCtaStyle.Ghost,
                                                    color = MagicCtaColor.Primary,
                                                    size = MagicCtaSize.Compact,
                                                )
                                            }
                                        }
                                    } else if (state.query.isNotBlank()) {
                                        val previewText = cleanedParagraphs.firstOrNull().orEmpty().ifEmpty { node.paragraphs.firstOrNull().orEmpty() }
                                        if (previewText.isNotEmpty()) {
                                            OracleText(
                                                text = previewText.take(220),
                                                style = type.bodyMedium.copy(color = colors.textSecondary)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (!reader && state.query.isNotBlank()) {
                            item(key = "paging") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = spacing.md),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (state.results.offset > 0) {
                                        MagicCtaButton(
                                            onClick = { onPage((state.results.offset - 50).coerceAtLeast(0)) },
                                            text = stringResource(Res.string.rules_previous),
                                            style = MagicCtaStyle.Outlined,
                                            color = MagicCtaColor.Primary
                                        )
                                    } else {
                                        Spacer(Modifier.width(1.dp))
                                    }

                                    if (state.results.offset + 50 < state.results.total) {
                                        MagicCtaButton(
                                            onClick = { onPage(state.results.offset + 50) },
                                            text = stringResource(Res.string.rules_next),
                                            style = MagicCtaStyle.Outlined,
                                            color = MagicCtaColor.Primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInfo && edition != null) {
        val uriHandler = LocalUriHandler.current
        var noticesExpanded by rememberSaveable { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { showInfo = false },
            dragHandle = null,
            shape = BottomSheetShape,
            containerColor = colors.backgroundSecondary,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showInfo = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.rules_clear),
                            tint = colors.textPrimary
                        )
                    }
                    Spacer(Modifier.width(spacing.xs))
                    Text(
                        text = stringResource(Res.string.rules_document),
                        style = type.titleLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.surfaceVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        Text(
                            text = stringResource(Res.string.rules_document),
                            style = type.titleMedium,
                            color = colors.goldMtg
                        )
                        Text(
                            text = stringResource(Res.string.rules_effective, edition.manifest.effectiveDate),
                            style = type.bodyMedium,
                            color = colors.textPrimary
                        )
                        Text(
                            text = stringResource(Res.string.rules_source),
                            style = type.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }

                MagicCtaButton(
                    onClick = { uriHandler.openUri("https://magic.wizards.com/en/rules") },
                    text = stringResource(Res.string.rules_official),
                    style = MagicCtaStyle.Filled,
                    color = MagicCtaColor.Primary,
                    modifier = Modifier.fillMaxWidth(),
                )

                MagicCtaButton(
                    onClick = onUpdate,
                    text = stringResource(if (state.updating) Res.string.rules_checking else Res.string.rules_update),
                    enabled = !state.updating,
                    isLoading = state.updating,
                    style = MagicCtaStyle.Outlined,
                    color = MagicCtaColor.Gold,
                    modifier = Modifier.fillMaxWidth(),
                )

                val updateMessage = when (state.updateResult) {
                    is RulesUpdateResult.Installed -> Res.string.rules_updated
                    RulesUpdateResult.Unchanged -> Res.string.rules_up_to_date
                    is RulesUpdateResult.Failed -> Res.string.rules_update_error
                    null -> null
                }
                if (updateMessage != null) {
                    Text(
                        text = stringResource(updateMessage),
                        style = type.bodyMedium,
                        color = if (state.updateResult is RulesUpdateResult.Failed) colors.lifeNegative else colors.textSecondary,
                        modifier = Modifier.padding(horizontal = spacing.xs)
                    )
                }

                val noticeNodes = remember(edition.id) {
                    edition.nodes.lastOrNull { it.kind == RulesNodeKind.NOTICE }?.paragraphs.orEmpty()
                }
                if (noticeNodes.isNotEmpty()) {
                    SectionHeader(
                        title = "Credits & Notices",
                        expanded = noticesExpanded,
                        onToggle = { noticesExpanded = !noticesExpanded },
                        titleColor = colors.goldMtg,
                    )

                    if (noticesExpanded) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = CardShape,
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.surfaceVariant),
                        ) {
                            Column(
                                modifier = Modifier.padding(spacing.lg),
                                verticalArrangement = Arrangement.spacedBy(spacing.sm)
                            ) {
                                noticeNodes.forEach { paragraph ->
                                    SelectionContainer {
                                        Text(
                                            text = paragraph,
                                            style = type.bodyMedium,
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleIdBadge(
    id: String,
    kind: RulesNodeKind,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.magicColors
    val type = MaterialTheme.magicTypography
    val (containerColor, contentColor, labelText) = when (kind) {
        RulesNodeKind.CHAPTER -> Triple(
            colors.goldMtg.copy(alpha = 0.15f),
            colors.goldMtg,
            "Chapter $id"
        )
        RulesNodeKind.SECTION -> Triple(
            colors.primaryAccent.copy(alpha = 0.15f),
            colors.primaryAccent,
            "Section $id"
        )
        RulesNodeKind.SUBRULE -> Triple(
            colors.secondaryAccent.copy(alpha = 0.15f),
            colors.secondaryAccent,
            id
        )
        RulesNodeKind.GLOSSARY -> Triple(
            colors.goldMtg.copy(alpha = 0.15f),
            colors.goldMtg,
            "Glossary"
        )
        RulesNodeKind.INTRODUCTION -> Triple(
            colors.primaryAccent.copy(alpha = 0.15f),
            colors.primaryAccent,
            "Intro"
        )
        RulesNodeKind.NOTICE -> Triple(
            colors.goldMtg.copy(alpha = 0.15f),
            colors.goldMtg,
            "Notice"
        )
        else -> Triple(
            colors.primaryAccent.copy(alpha = 0.15f),
            colors.primaryAccent,
            id
        )
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.3f)),
    ) {
        Text(
            text = labelText,
            style = type.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private fun cleanParagraph(paragraph: String, node: RulesNode): String {
    val trimmed = paragraph.trim()
    if (trimmed.isEmpty()) return ""

    if (node.kind == RulesNodeKind.CHAPTER || node.kind == RulesNodeKind.SECTION) {
        val headerText = "${node.id}. ${node.title}"
        if (trimmed.equals(headerText, ignoreCase = true) || trimmed.equals(node.title, ignoreCase = true)) {
            return ""
        }
    }

    var result = trimmed
    val idPrefixRegex = Regex("^${Regex.escape(node.id)}[.\\s:]*")
    result = result.replace(idPrefixRegex, "").trimStart()

    if (node.kind == RulesNodeKind.GLOSSARY && result.startsWith(node.title, ignoreCase = true)) {
        result = result.removePrefix(node.title).trimStart('\r', '\n', ' ', ':', '.')
    }

    if (result.equals(node.title, ignoreCase = true)) {
        return ""
    }

    return result
}
