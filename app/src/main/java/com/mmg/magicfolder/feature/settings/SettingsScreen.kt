package com.mmg.magicfolder.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mmg.magicfolder.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack:    () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc       = MaterialTheme.magicColors

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text  = stringResource(R.string.settings_title),
                        style = MaterialTheme.magicTypography.titleLarge,
                        color = mc.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = mc.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.backgroundSecondary),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.settings_section_prices),
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
            )

            SettingsToggleItem(
                title    = stringResource(R.string.settings_auto_refresh),
                subtitle = stringResource(R.string.settings_auto_refresh_subtitle),
                checked  = uiState.autoRefreshPrices,
                onCheckedChange = viewModel::onAutoRefreshChanged,
            )
        }
    }
}

@Composable
private fun SettingsToggleItem(
    title:           String,
    subtitle:        String,
    checked:         Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title,    style = MaterialTheme.magicTypography.bodyLarge,  color = mc.textPrimary)
            Text(subtitle, style = MaterialTheme.magicTypography.bodySmall,  color = mc.textSecondary)
        }
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            colors          = SwitchDefaults.colors(checkedThumbColor = mc.primaryAccent),
        )
    }
}
