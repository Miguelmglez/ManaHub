package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.magicColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddToCollectionSheet(
    cardName: String,
    onConfirm: (isFoil: Boolean, isAlternativeArt: Boolean, condition: String, language: String, qty: Int) -> Unit,
    onDismiss: () -> Unit,
    manaCost: String?,
    cardImage: String?,
    closeButton: Boolean = false,
) {
    val conditions = listOf("Near Mint", "Slightly Played", "Played", "Heavily Played", "Damaged")
    val languages = listOf(
        "en" to "🇺🇸",
        "es" to "🇪🇸",
        "de" to "🇩🇪",
        "fr" to "🇫🇷",
        "it" to "🇮🇹",
        "pt" to "🇵🇹",
        "ja" to "🇯🇵",
        "ko" to "🇰🇷",
        "ru" to "🇷🇺"
    )

    var isFoil by remember { mutableStateOf(false) }
    var isAlternativeArt by remember { mutableStateOf(false) }
    var condition by remember { mutableStateOf("NM") }
    var language by remember { mutableStateOf("en") }
    var qty by remember { mutableIntStateOf(1) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = {
            if (closeButton) it != SheetValue.Hidden else true
        }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets(0) },
        dragHandle = if (closeButton) null else {
            { BottomSheetDefaults.DragHandle() }
        }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (closeButton) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.offset(x = (-12).dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.carddetail_add_copy),
                    modifier = Modifier
                        .weight(1f)
                        .offset(x = if (closeButton) (-16).dp else 0.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = cardName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.magicColors.primaryAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                manaCost?.let {
                    ManaCostImages(manaCost = it, symbolSize = 16.dp)
                }
            }
            cardImage?.let {
                AsyncImage(
                    model = cardImage,
                    contentDescription = cardName,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth(1f)
                        .align(Alignment.CenterHorizontally)
                        .clip(MaterialTheme.shapes.medium)
                )
            }
            // Foil toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.addcard_confirm_foil),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(checked = isFoil, onCheckedChange = { isFoil = it })
            }

            // Alternative art toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.carddetail_alternative_art),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(checked = isAlternativeArt, onCheckedChange = { isAlternativeArt = it })
            }

            // Quantity stepper
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.addcard_confirm_quantity),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(onClick = { if (qty > 1) qty-- }) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = stringResource(R.string.action_remove)
                    )
                }
                Text("$qty", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { qty++ }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_add)
                    )
                }
            }

            // Condition chips
            Text(
                stringResource(R.string.addcard_confirm_condition),
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                conditions.forEach { c ->
                    FilterChip(
                        selected = c == condition,
                        onClick = { condition = c },
                        label = { Text(c) },
                    )
                }
            }

            // Language dropdown
            var langExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = langExpanded,
                onExpandedChange = { langExpanded = it },
            ) {
                OutlinedTextField(
                    value = "${languages.find { it.first == language }?.second ?: ""} ${language.uppercase()}",
                    onValueChange = {},
                    readOnly = true,
                    label = {
                        Text(
                            stringResource(R.string.addcard_confirm_language),
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(langExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = langExpanded,
                    onDismissRequest = { langExpanded = false },
                ) {
                    languages.forEach { (lang, flag) ->
                        DropdownMenuItem(
                            text = { Text("$flag ${lang.uppercase()}") },
                            onClick = { language = lang; langExpanded = false },
                        )
                    }
                }
            }

            // Confirm / Cancel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = {
                    onConfirm(
                        isFoil,
                        isAlternativeArt,
                        condition,
                        language,
                        qty
                    )
                }) {
                    Text(stringResource(R.string.action_add))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
