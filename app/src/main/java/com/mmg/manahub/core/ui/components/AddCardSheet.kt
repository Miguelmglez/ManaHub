package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.MagicTheme
import com.mmg.manahub.core.ui.theme.coloredShadow
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
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
    extraContent: (@Composable () -> Unit)? = null,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    
    val conditions = CardConstants.conditions
    val languages = CardConstants.languages

    var isFoil by remember { mutableStateOf(initialFoil) }
    var isAlternativeArt by remember { mutableStateOf(initialAlternativeArt) }
    var condition by remember { mutableStateOf(initialCondition) }
    var language by remember { mutableStateOf(initialLanguage) }
    var qty by remember { mutableIntStateOf(initialQty) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    ) {
        if (closeButton) it != SheetValue.Hidden else true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.background,
        contentColor = mc.textPrimary,
        contentWindowInsets = { WindowInsets(0) },
        dragHandle = if (closeButton) null else {
            { BottomSheetDefaults.DragHandle(color = mc.textDisabled.copy(alpha = 0.4f)) }
        }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // Header Row
            if (closeButton) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.offset(x = (-12).dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = mc.textSecondary
                        )
                    }
                }
            }
            
            // ── Card Preview Section ──────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(mc.surface)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val displayName = cardName.substringBefore(" // ")
                    CardName(
                        name = displayName,
                        style = ty.displayMedium,
                        color = mc.primaryAccent,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if ((setCode != null) && (setName != null)) {
                            SetSymbol(
                                setCode = setCode,
                                rarity = CardRarity.fromString(rarity ?: "common"),
                                size = 20.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = setName,
                                style = ty.bodyLarge,
                                color = mc.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    manaCost?.let {
                        Spacer(Modifier.height(4.dp))
                        ManaCostImages(manaCost = it, symbolSize = 20.dp, spacing = 4.dp)
                    }
                }

                cardImage?.let {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .coloredShadow(
                                color = mc.primaryAccent.copy(alpha = 0.4f),
                                borderRadius = 16.dp,
                                blurRadius = 32.dp
                            )
                    ) {
                        AsyncImage(
                            model = cardImage,
                            contentDescription = cardName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .heightIn(max = 280.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, mc.primaryAccent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        )
                    }
                }
            }

            // ── Settings Section ──────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Toggles Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Foil toggle
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = if (isFoil) mc.primaryAccent.copy(alpha = 0.15f) else mc.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = if (isFoil) BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.5f)) else null,
                        onClick = { isFoil = !isFoil }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.addcard_confirm_foil),
                                Modifier.weight(1f),
                                style = ty.bodyLarge,
                                color = if (isFoil) mc.primaryAccent else mc.textPrimary
                            )
                            Switch(
                                checked = isFoil, 
                                onCheckedChange = { isFoil = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = mc.background,
                                    checkedTrackColor = mc.primaryAccent,
                                    uncheckedThumbColor = mc.textDisabled,
                                    uncheckedTrackColor = mc.surfaceVariant
                                ),
                                modifier = Modifier.scale(0.9f)
                            )
                        }
                    }

                    // Alternative art toggle
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = if (isAlternativeArt) mc.primaryAccent.copy(alpha = 0.15f) else mc.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = if (isAlternativeArt) BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.5f)) else null,
                        onClick = { isAlternativeArt = !isAlternativeArt }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.carddetail_alternative_art),
                                Modifier.weight(1f),
                                style = ty.bodyLarge,
                                color = if (isAlternativeArt) mc.primaryAccent else mc.textPrimary
                            )
                            Switch(
                                checked = isAlternativeArt, 
                                onCheckedChange = { isAlternativeArt = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = mc.background,
                                    checkedTrackColor = mc.primaryAccent,
                                    uncheckedThumbColor = mc.textDisabled,
                                    uncheckedTrackColor = mc.surfaceVariant
                                ),
                                modifier = Modifier.scale(0.9f)
                            )
                        }
                    }
                }

                // Dropdowns
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Condition dropdown
                    var condExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = condExpanded,
                        onExpandedChange = { condExpanded = it },
                        modifier = Modifier.weight(1.2f)
                    ) {
                        OutlinedTextField(
                            value = condition,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.addcard_confirm_condition), style = ty.labelMedium) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(condExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            textStyle = ty.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = mc.primaryAccent,
                                unfocusedBorderColor = mc.surfaceVariant,
                                focusedLabelColor = mc.primaryAccent,
                                unfocusedLabelColor = mc.textSecondary,
                                focusedTextColor = mc.textPrimary,
                                unfocusedTextColor = mc.textPrimary,
                            ),
                            shape = RoundedCornerShape(16.dp),
                        )
                        ExposedDropdownMenu(
                            expanded = condExpanded,
                            onDismissRequest = { condExpanded = false },
                            modifier = Modifier.background(mc.surface)
                        ) {
                            conditions.forEach { (code, resId) ->
                                DropdownMenuItem(
                                    text = { 
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "($code) ${stringResource(resId)}",
                                                style = ty.bodyLarge,
                                                color = if (condition == code) mc.primaryAccent else mc.textPrimary
                                            )
                                        }
                                    },
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
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = language.uppercase(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.addcard_confirm_language), style = ty.labelMedium) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(langExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            textStyle = ty.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = mc.primaryAccent,
                                unfocusedBorderColor = mc.surfaceVariant,
                                focusedLabelColor = mc.primaryAccent,
                                unfocusedLabelColor = mc.textSecondary,
                                focusedTextColor = mc.textPrimary,
                                unfocusedTextColor = mc.textPrimary,
                            ),
                            shape = RoundedCornerShape(16.dp),
                        )
                        ExposedDropdownMenu(
                            expanded = langExpanded,
                            onDismissRequest = { langExpanded = false },
                            modifier = Modifier.background(mc.surface)
                        ) {
                            languages.forEach { (lang, _) ->
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            lang.uppercase(),
                                            style = ty.bodyLarge,
                                            color = if (language == lang) mc.primaryAccent else mc.textPrimary
                                        ) 
                                    },
                                    onClick = { language = lang; langExpanded = false },
                                )
                            }
                        }
                    }
                }

                // Quantity stepper
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(mc.surface)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.addcard_confirm_quantity),
                        Modifier.weight(1f),
                        style = ty.bodyLarge,
                        color = mc.textPrimary
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        IconButton(
                            onClick = { if (qty > 1) qty-- },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = stringResource(R.string.action_remove),
                                modifier = Modifier.size(24.dp),
                                tint = if (qty > 1) mc.primaryAccent else mc.textDisabled
                            )
                        }
                        
                        Text(
                            qty.toString(), 
                            style = ty.displayMedium.copy(fontSize = 24.sp),
                            color = mc.primaryAccent,
                            modifier = Modifier.width(32.dp),
                            textAlign = TextAlign.Center
                        )
                        
                        IconButton(
                            onClick = { qty++ },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.action_add),
                                modifier = Modifier.size(24.dp),
                                tint = mc.primaryAccent
                            )
                        }
                    }
                }
            }

            extraContent?.invoke()

            // Confirm / Cancel
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { 
                    Text(
                        stringResource(R.string.action_cancel).uppercase(),
                        style = ty.labelMedium,
                        color = mc.textSecondary
                    ) 
                }
                
                Button(
                    onClick = {
                        onConfirm(
                            isFoil,
                            isAlternativeArt,
                            condition,
                            language,
                            qty
                        )
                    },
                    modifier = Modifier.weight(2f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = mc.primaryAccent,
                        contentColor = mc.background
                    )
                ) {
                    Text(
                        confirmButtonText.uppercase(),
                        style = ty.labelMedium
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}


