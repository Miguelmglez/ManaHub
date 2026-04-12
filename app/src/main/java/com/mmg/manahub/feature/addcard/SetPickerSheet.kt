package com.mmg.manahub.feature.addcard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.MagicSet
import com.mmg.manahub.core.domain.model.PLAYABLE_SET_TYPES
import com.mmg.manahub.core.domain.model.SetType
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetPickerSheet(
    selectedSetCodes: Set<String>,
    onToggleSet: (MagicSet) -> Unit,
    onDismiss: () -> Unit,
    viewModel: SetPickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = mc.backgroundSecondary,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding(),
        ) {
            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.advsearch_set_picker_title),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                )
                if (selectedSetCodes.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = mc.primaryAccent.copy(0.15f),
                    ) {
                        Text(
                            stringResource(
                                R.string.advsearch_set_selected_count,
                                selectedSetCodes.size,
                            ),
                            style = ty.labelSmall,
                            color = mc.primaryAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            // ── Search field ──
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = {
                    Text(
                        stringResource(R.string.advsearch_set_search_hint),
                        color = mc.textDisabled,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = mc.textDisabled)
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = mc.textDisabled)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = mc.primaryAccent,
                    unfocusedBorderColor = mc.primaryAccent.copy(alpha = 0.25f),
                    cursorColor = mc.primaryAccent,
                    focusedTextColor = mc.textPrimary,
                    unfocusedTextColor = mc.textPrimary,
                    focusedContainerColor = mc.surface,
                    unfocusedContainerColor = mc.surface,
                ),
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))

            // ── Type filter chips ──
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedTypes.isEmpty(),
                        onClick = viewModel::clearFilters,
                        label = {
                            Text(
                                stringResource(R.string.collection_filter_all),
                                style = ty.labelSmall,
                            )
                        },
                    )
                }
                items(PLAYABLE_SET_TYPES.toList()) { type ->
                    FilterChip(
                        selected = uiState.selectedTypes.contains(type),
                        onClick = { viewModel.toggleTypeFilter(type) },
                        label = {
                            Text(stringResource(type.labelRes()), style = ty.labelSmall)
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            HorizontalDivider(color = mc.surfaceVariant)

            // ── Content ──
            when {
                uiState.isLoading -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = mc.primaryAccent)
                    }
                }
                uiState.filteredSets.isEmpty() -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.advsearch_set_no_results),
                            color = mc.textDisabled,
                        )
                    }
                }
                else -> {
                    Text(
                        stringResource(R.string.advsearch_set_count, uiState.filteredSets.size),
                        style = ty.bodySmall,
                        color = mc.textDisabled,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        items(
                            items = uiState.filteredSets,
                            key = { it.code },
                        ) { set ->
                            SetPickerRow(
                                set = set,
                                isSelected = selectedSetCodes.contains(set.code),
                                onClick = { onToggleSet(set) },
                            )
                        }
                    }
                }
            }

            // ── Done button ──
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
            ) {
                Text(
                    if (selectedSetCodes.isEmpty())
                        stringResource(R.string.action_close)
                    else
                        stringResource(R.string.advsearch_set_done, selectedSetCodes.size),
                    style = ty.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun SetPickerRow(
    set: MagicSet,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) mc.primaryAccent.copy(0.1f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(set.iconSvgUri)
                    .decoderFactory(SvgDecoder.Factory())
                    .crossfade(true)
                    .build(),
                contentDescription = set.name,
                modifier = Modifier.size(28.dp),
                colorFilter = ColorFilter.tint(
                    if (isSelected) mc.primaryAccent else mc.textSecondary,
                ),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = set.name,
                    style = ty.bodyMedium,
                    color = if (isSelected) mc.primaryAccent else mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(set.code.uppercase(), style = ty.bodySmall, color = mc.textDisabled)
                    Text("·", color = mc.textDisabled)
                    Text(
                        stringResource(R.string.advsearch_set_card_count, set.cardCount),
                        style = ty.bodySmall,
                        color = mc.textDisabled,
                    )
                    set.releasedAt?.let { date ->
                        Text("·", color = mc.textDisabled)
                        Text(date.take(4), style = ty.bodySmall, color = mc.textDisabled)
                    }
                }
            }

            // Checkbox visual
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isSelected) mc.primaryAccent else mc.surfaceVariant,
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) mc.primaryAccent else mc.surfaceVariant,
                        shape = RoundedCornerShape(4.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun SetType.labelRes(): Int = when (this) {
    SetType.EXPANSION        -> R.string.set_type_expansion
    SetType.CORE             -> R.string.set_type_core
    SetType.MASTERS          -> R.string.set_type_masters
    SetType.DRAFT_INNOVATION -> R.string.set_type_draft_innovation
    SetType.COMMANDER        -> R.string.set_type_commander
    SetType.STARTER          -> R.string.set_type_starter
    SetType.FUNNY            -> R.string.set_type_funny
    else                     -> R.string.set_type_other
}
