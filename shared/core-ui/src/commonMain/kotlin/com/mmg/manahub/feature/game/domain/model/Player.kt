package com.mmg.manahub.feature.game.domain.model

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.theme.PlayerThemeColors

/**
 * Full player representation during a game session, combining game state (implements [PlayerState])
 * with display/theme data. Pure domain logic that only needs life/poison/commanderDamage can accept
 * a [PlayerState] reference, keeping core-domain free of core-ui imports.
 */
data class Player(
    val id:                          Int,
    val name:                        String,
    override val life:               Int,
    override val poison:             Int              = 0,
    val experience:                  Int              = 0,
    val energy:                      Int              = 0,
    override val commanderDamage:    Map<Int, Int>    = emptyMap(),  // sourceId → damage taken
    val customCounters:              List<CustomCounter> = emptyList(),
    val pendingDefeat:               Boolean          = false,
    val isSurviving:                 Boolean          = false,
    val defeated:                    Boolean          = false,
    val deckId:                      Long?            = null,
    val commander:                   Card?            = null,
    val theme:                       PlayerThemeColors,
    val gridPosition:                Int              = id,
    val rotation:                    Int              = 0,           // 0, 90, 180, 270
    val isAppUser:                   Boolean          = false,
) : PlayerState

data class CustomCounter(
    val id:      Long,
    val name:    String,
    val value:   Int,
    val iconKey: String = "",   // empty = default counter icon
)

enum class CounterType { POISON, EXPERIENCE, ENERGY }
