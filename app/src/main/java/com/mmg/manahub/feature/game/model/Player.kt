package com.mmg.manahub.feature.game.model

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.ui.theme.PlayerThemeColors

data class Player(
    val id:               Int,
    val name:             String,
    val life:             Int,
    val poison:           Int                 = 0,
    val experience:       Int                 = 0,
    val energy:           Int                 = 0,
    val commanderDamage:  Map<Int, Int>       = emptyMap(),  // sourceId → damage taken
    val customCounters:   List<CustomCounter> = emptyList(),
    val pendingDefeat:    Boolean             = false,
    val isSurviving:      Boolean             = false,
    val defeated:         Boolean             = false,
    val deckId:           Long?               = null,
    val commander:        Card?               = null,
    val theme:            PlayerThemeColors,
    val gridPosition:     Int                 = id,
    val rotation:         Int                 = 0,           // 0, 90, 180, 270
    val isAppUser:        Boolean             = false,
)

data class CustomCounter(
    val id:      Long,
    val name:    String,
    val value:   Int,
    val iconKey: String = "",   // empty = default counter icon
)

enum class CounterType { POISON, EXPERIENCE, ENERGY }
