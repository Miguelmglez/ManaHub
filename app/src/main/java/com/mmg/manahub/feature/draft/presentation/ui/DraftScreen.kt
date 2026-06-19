package com.mmg.manahub.feature.draft.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.DraftSetCard
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.draft.presentation.viewmodel.DraftViewModel

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
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Toolbar
            Text(
                text = stringResource(R.string.draft_title),
                style = typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
            )

            if (state.isStale) {
                InlineErrorState(
                    message = stringResource(R.string.draft_stale_data),
                    retryLabel = stringResource(R.string.draft_retry),
                    onRetry = { viewModel.loadSets(forceRefresh = true) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

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
                    FullErrorState(
                        message = state.error ?: "",
                        retryLabel = stringResource(R.string.draft_retry),
                        onRetry = { viewModel.loadSets(forceRefresh = true) },
                    )
                }
                state.filteredSets.isEmpty() && state.searchQuery.isNotBlank() -> {
                    EmptyState(title = stringResource(R.string.draft_no_sets))
                }
                else -> {
                    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp + navBarBottom),
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