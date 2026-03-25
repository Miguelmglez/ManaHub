package com.mmg.magicfolder.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// ═══════════════════════════════════════════════════════════════════════════════
//  Corner radius tokens
//  Use these when constructing shapes in composables:
//    Card(shape = RoundedCornerShape(CardCornerRadius)) { … }
// ═══════════════════════════════════════════════════════════════════════════════

/** MTG card thumbnails, player life-counter cards, collection grid items. */
val CardCornerRadius         = 18.dp

/** Filter chips, badges, quantity indicators, mana pip containers. */
val ChipCornerRadius         = 8.dp

/** Primary and secondary action buttons. */
val ButtonCornerRadius       = 14.dp

/** Bottom sheets and modal panels. */
val BottomSheetCornerRadius  = 24.dp

// ── Convenience shapes ────────────────────────────────────────────────────────
// Pre-built for direct use in Modifier.clip() or shape parameters.

val CardShape         = RoundedCornerShape(CardCornerRadius)
val ChipShape         = RoundedCornerShape(ChipCornerRadius)
val ButtonShape       = RoundedCornerShape(ButtonCornerRadius)
val BottomSheetShape  = RoundedCornerShape(
    topStart = BottomSheetCornerRadius,
    topEnd   = BottomSheetCornerRadius,
    bottomStart = 0.dp,
    bottomEnd   = 0.dp,
)
