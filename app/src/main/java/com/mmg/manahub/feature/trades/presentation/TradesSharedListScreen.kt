package com.mmg.manahub.feature.trades.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.trades.domain.model.SharedListResult

// ─────────────────────────────────────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Landing screen for shared trade-list deep links.
 *
 * Reached via [Screen.TradesSharedList] when a user taps a link of the form:
 * `https://trades.manahub.app/list/{shareId}`
 *
 * Phase 3 will render card images and enable "Add to my wishlist" actions.
 * In Phase 2, this screen renders raw card IDs from the resolved list.
 */
@Composable
fun TradesSharedListScreen(
    onBack: () -> Unit,
    viewModel: TradesSharedListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors

    Scaffold(
        containerColor = mc.background,
        topBar = {
            Surface(
                color    = mc.backgroundSecondary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint               = mc.textPrimary,
                        )
                    }
                    Text(
                        text  = stringResource(R.string.trades_tab_exploration),
                        style = MaterialTheme.magicTypography.titleLarge,
                        color = mc.textPrimary,
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier        = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = uiState) {
                SharedListUiState.Loading -> {
                    CircularProgressIndicator(color = mc.primaryAccent)
                }

                SharedListUiState.Private -> {
                    MessageWithBack(
                        message = stringResource(R.string.trades_shared_list_private),
                        onBack  = onBack,
                    )
                }

                SharedListUiState.NotFound -> {
                    MessageWithBack(
                        message = stringResource(R.string.trades_shared_list_not_found),
                        onBack  = onBack,
                    )
                }

                is SharedListUiState.Error -> {
                    MessageWithBack(
                        message = state.message ?: stringResource(R.string.error_unknown),
                        onBack  = onBack,
                    )
                }

                is SharedListUiState.Success -> {
                    SharedListContent(
                        result    = state.result,
                        modifier  = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Private helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MessageWithBack(
    message: String,
    onBack:  () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Column(
        modifier            = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text      = message,
            style     = MaterialTheme.magicTypography.bodyMedium,
            color     = mc.textSecondary,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onBack,
            colors  = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
        ) {
            Text(stringResource(R.string.action_back), color = mc.background)
        }
    }
}

@Composable
private fun SharedListContent(
    result:   SharedListResult.Ok,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Column(modifier = modifier) {
        Text(
            text     = "Shared list from ${result.userId}",
            style    = MaterialTheme.magicTypography.titleMedium,
            color    = mc.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Text(
            text     = result.listType.name,
            style    = MaterialTheme.magicTypography.labelSmall,
            color    = mc.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))

        // Phase 2: render raw map entries; Phase 3 will fetch card details.
        LazyColumn(
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(result.items) { itemMap ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = mc.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        itemMap.forEach { (key, value) ->
                            Text(
                                text  = "$key: ${value ?: "—"}",
                                style = MaterialTheme.magicTypography.bodySmall,
                                color = mc.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}
