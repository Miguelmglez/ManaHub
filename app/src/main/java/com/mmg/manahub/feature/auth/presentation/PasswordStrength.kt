package com.mmg.manahub.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.MagicTypography
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * Immutable snapshot of which sign-up password requirements are currently satisfied.
 * Mirrors the server-side rules enforced by [AuthViewModel.isPasswordStrong].
 *
 * Extracted out of `LoginSheet.kt` (originally private there) so [UpdatePasswordScreen] can share
 * the EXACT same rules/UI as sign-up rather than re-implementing a parallel copy — see
 * `LoginSheet.kt`'s import of this file.
 */
internal data class PasswordStrength(
    val hasMinLength: Boolean,
    val hasLowercase: Boolean,
    val hasUppercase: Boolean,
    val hasDigit: Boolean,
    val hasSymbol: Boolean,
) {
    val allMet: Boolean
        get() = hasMinLength && hasLowercase && hasUppercase && hasDigit && hasSymbol

    companion object {
        fun from(password: String) = PasswordStrength(
            hasMinLength = password.length >= 8,
            hasLowercase = password.any { it.isLowerCase() },
            hasUppercase = password.any { it.isUpperCase() },
            hasDigit     = password.any { it.isDigit() },
            hasSymbol    = password.any { !it.isLetterOrDigit() },
        )
    }
}

/** Stateless checklist of the five [PasswordStrength] requirements, each with a live check/unchecked glyph. */
@Composable
internal fun PasswordStrengthIndicator(strength: PasswordStrength) {
    val ty = MaterialTheme.magicTypography
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        RequirementRow(stringResource(R.string.auth_password_requirement_length),   strength.hasMinLength, ty)
        RequirementRow(stringResource(R.string.auth_password_requirement_uppercase), strength.hasUppercase, ty)
        RequirementRow(stringResource(R.string.auth_password_requirement_lowercase), strength.hasLowercase, ty)
        RequirementRow(stringResource(R.string.auth_password_requirement_digit),     strength.hasDigit, ty)
        RequirementRow(stringResource(R.string.auth_password_requirement_symbol),    strength.hasSymbol, ty)
    }
}

@Composable
internal fun RequirementRow(label: String, met: Boolean, ty: MagicTypography) {
    val mc = MaterialTheme.magicColors
    val color = if (met) mc.lifePositive else mc.textDisabled
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = if (met) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = color,
            style = ty.labelSmall,
        )
    }
}
