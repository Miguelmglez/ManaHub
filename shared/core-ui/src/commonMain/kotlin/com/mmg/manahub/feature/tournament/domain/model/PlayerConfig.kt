package com.mmg.manahub.feature.tournament.domain.model

import com.mmg.manahub.core.ui.theme.PlayerThemeColors

data class PlayerConfig(
    val id:    Int,
    val name:  String,
    val theme: PlayerThemeColors,
)
