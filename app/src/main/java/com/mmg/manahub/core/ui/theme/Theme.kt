package com.mmg.manahub.core.ui.theme

import androidx.compose.runtime.Composable

/**
 * Legacy alias kept for source compatibility during the migration period.
 * New code should call [MagicTheme] directly.
 */
@Composable
fun MtgCollectionTheme(content: @Composable () -> Unit) =
    MagicTheme(content = content)
