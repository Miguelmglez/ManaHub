package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * A styled text field for entering a 6-digit numeric room code.
 *
 * - Uses a monospace font with wide letter spacing for clarity.
 * - Only allows numeric keyboard input.
 * - Shows a placeholder of "000000" in disabled text color.
 *
 * @param code Current text value.
 * @param onCodeChange Callback invoked on every text change.
 * @param modifier Optional modifier.
 * @param enabled Whether the field is interactable.
 * @param label The field label. Defaults to "Room Code".
 */
@Composable
fun RoomCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = stringResource(R.string.lobby_room_code_label),
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    OutlinedTextField(
        value = code,
        onValueChange = onCodeChange,
        label = { Text(text = label, style = ty.labelMedium) },
        placeholder = {
            Text(
                text = "000000",
                style = ty.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 8.sp,
                ),
                color = mc.textDisabled,
            )
        },
        textStyle = ty.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 8.sp,
            color = mc.primaryAccent,
        ),
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = mc.primaryAccent,
            unfocusedBorderColor = mc.surfaceVariant,
            cursorColor = mc.primaryAccent,
            focusedContainerColor = mc.surface,
            unfocusedContainerColor = mc.surface,
            focusedLabelColor = mc.primaryAccent,
            unfocusedLabelColor = mc.textSecondary,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    )
}
