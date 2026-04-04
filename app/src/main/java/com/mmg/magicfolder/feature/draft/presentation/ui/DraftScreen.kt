package com.mmg.magicfolder.feature.draft.presentation.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.mmg.magicfolder.R
import com.mmg.magicfolder.core.ui.theme.ThemeBackground
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography
import com.mmg.magicfolder.feature.draft.domain.model.DraftSet
import com.mmg.magicfolder.feature.draft.presentation.viewmodel.DraftViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DraftScreen(
    onSetClick: (setCode: String, setName: String, iconUri: String, releasedAt: String) -> Unit,
    viewModel: DraftViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Box(modifier = Modifier.fillMaxSize()) {
        ThemeBackground(modifier = Modifier.fillMaxSize())
        Column(modifier = Modifier.fillMaxSize()) {
            // Toolbar
            Text(
                text = stringResource(R.string.draft_title),
                style = typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
            )

            // Search bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = {
                    Text(
                        stringResource(R.string.draft_search_hint),
                        color = colors.textDisabled,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = colors.textDisabled)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primaryAccent,
                    unfocusedBorderColor = colors.surfaceVariant,
                    cursorColor = colors.primaryAccent,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                ),
            )

            when {
                state.isLoading && state.sets.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = colors.primaryAccent)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.draft_loading_sets),
                                color = colors.textSecondary,
                                style = typography.bodyMedium,
                            )
                        }
                    }
                }
                state.error != null && state.sets.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                state.error ?: "",
                                color = colors.lifeNegative,
                                style = typography.bodyMedium,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.loadSets(forceRefresh = true) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primaryAccent,
                                ),
                            ) {
                                Text(stringResource(R.string.draft_retry))
                            }
                        }
                    }
                }
                state.filteredSets.isEmpty() && state.searchQuery.isNotBlank() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.draft_no_sets),
                            color = colors.textSecondary,
                            style = typography.bodyMedium,
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.filteredSets, key = { it.id }) { set ->
                            DraftSetCard(
                                set = set,
                                onClick = {
                                    onSetClick(set.code, set.name, set.iconSvgUri, set.releasedAt)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftSetCard(
    set: DraftSet,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(set.iconSvgUri)
                    .decoderFactory(SvgDecoder.Factory())
                    .crossfade(true)
                    .build(),
                contentDescription = set.name,
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(colors.textPrimary),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = set.name,
                style = typography.labelLarge,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatReleaseDate(set.releasedAt),
                    style = typography.labelSmall,
                    color = colors.textSecondary,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.primaryAccent.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = set.code.uppercase(),
                        style = typography.labelSmall,
                        color = colors.primaryAccent,
                    )
                }
            }
        }
    }
}

private fun formatReleaseDate(dateStr: String): String {
    return try {
        val date = LocalDate.parse(dateStr)
        date.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))
    } catch (_: Exception) {
        dateStr
    }
}
