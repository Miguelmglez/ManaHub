package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.mmg.manahub.core.util.CardConstants

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddCardSheet(
    cardName: String,
    onConfirm: (isFoil: Boolean, isAlternativeArt: Boolean, condition: String, language: String, qty: Int) -> Unit,
    onDismiss: () -> Unit,
    manaCost: String?,
    cardImage: String?,
    closeButton: Boolean = false,
    initialFoil: Boolean = false,
    initialCondition: String = "NM",
    initialLanguage: String = "en",
    initialQty: Int = 1,
    initialAlternativeArt: Boolean = false,
    confirmButtonText: String = stringResource(R.string.scanner_add_to_collection),
    setCode: String? = null,
    setName: String? = null,
    rarity: String? = null,
) {
    val conditions = CardConstants.conditions
    val languages = CardConstants.languages

    var isFoil by remember { mutableStateOf(initialFoil) }
    var isAlternativeArt by remember { mutableStateOf(initialAlternativeArt) }
    var condition by remember { mutableStateOf(initialCondition) }
    var language by remember { mutableStateOf(initialLanguage) }
    var qty by remember { mutableIntStateOf(initialQty) }

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

            // Header Row
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
            }
            
            // Card Info - Centered
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val displayName = cardName.substringBefore(" // ")
                CardName(
                    name = displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.magicColors.primaryAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                

                if (setCode != null && setName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SetSymbol(
                            setCode = setCode,
                            rarity = CardRarity.fromString(rarity ?: "common"),
                            size = 14.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = setName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.magicColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Card Image - Centered and small size
            cardImage?.let {
                AsyncImage(
                    model = cardImage,
                    contentDescription = cardName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .heightIn(max = 220.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(MaterialTheme.shapes.medium)
                )
            }

            Spacer(Modifier.height(4.dp))
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

            // Condition dropdown
            var condExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = condExpanded,
                onExpandedChange = { condExpanded = it },
            ) {
                OutlinedTextField(
                    value = "(${condition}) ${stringResource(conditions.find { it.first == condition }?.second ?: R.string.card_condition_nm)}",
                    onValueChange = {},
                    readOnly = true,
                    label = {
                        Text(
                            stringResource(R.string.addcard_confirm_condition),
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(condExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = condExpanded,
                    onDismissRequest = { condExpanded = false },
                ) {
                    conditions.forEach { (code, resId) ->
                        DropdownMenuItem(
                            text = { Text("(${code}) ${stringResource(resId)}") },
                            onClick = { condition = code; condExpanded = false },
                        )
                    }
                }
            }

            // Language dropdown
            var langExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = langExpanded,
                onExpandedChange = { langExpanded = it },
            ) {
                OutlinedTextField(
                    value = "(${language.uppercase()}) ${CardConstants.getLanguageName(language)}",
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
                    languages.forEach { (lang, _) ->
                        DropdownMenuItem(
                            text = { Text("(${lang.uppercase()}) ${CardConstants.getLanguageName(lang)}") },
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
                    Text(confirmButtonText)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}


