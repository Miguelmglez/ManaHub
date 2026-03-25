package com.mmg.magicfolder.feature.game.model

data class Player(
    val id:               Int,
    val name:             String,
    val life:             Int,
    val poison:           Int                = 0,
    val experience:       Int                = 0,
    val energy:           Int                = 0,
    val commanderDamage:  Map<Int, Int>      = emptyMap(),  // sourceId → damage taken
    val customCounters:   List<CustomCounter> = emptyList(),
    val eliminated:       Boolean            = false,
    /** Index into MagicColors.playerColors (0–9). Resolved to colors in the UI layer. */
    val themeIndex:       Int                = 0,
)

data class CustomCounter(
    val id:    Long,
    val name:  String,
    val value: Int,
)

enum class CounterType { POISON, EXPERIENCE, ENERGY }
