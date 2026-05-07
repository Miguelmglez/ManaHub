package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.UserCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeSelectionSheet(
    userCards: List<UserCard>,
    onConfirm: (Map<String, Boolean>) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.carddetail_trade_sheet_title),
    subtitle: String = stringResource(R.string.carddetail_trade_sheet_subtitle),
) {
    val tradeState = remember(userCards) {
        mutableStateMapOf<String, Boolean>().also { map ->
            userCards.forEach { map[it.id] = it.isForTrade }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (userCards.isEmpty()) {
                Text(
                    text = stringResource(R.string.carddetail_no_copies_for_trade),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                userCards.forEach { uc ->
                    val checked = tradeState[uc.id] ?: false
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tradeState[uc.id] = !checked },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { tradeState[uc.id] = it },
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CopyBadge(label = uc.language.uppercase())
                                CopyBadge(label = uc.condition)
                                if (uc.isFoil) FoilBadge()
                                if (uc.isAlternativeArt) {
                                    CopyBadge(label = stringResource(R.string.carddetail_alternative_art_short))
                                }
                            }
                            Text(
                                text = "×${uc.quantity}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Confirm / Cancel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(
                    onClick = { onConfirm(tradeState.toMap()) },
                    enabled = userCards.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CopyBadge(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
