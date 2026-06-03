package com.mmg.manahub.feature.game.domain.model

import androidx.annotation.StringRes
import com.mmg.manahub.R

enum class GameMode(
    val startingLife: Int,
    @StringRes val displayNameRes: Int,
) {
    STANDARD(20,  R.string.gamesetup_mode_standard),
    COMMANDER(40, R.string.gamesetup_mode_commander),
}
