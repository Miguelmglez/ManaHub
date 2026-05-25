package com.mmg.manahub.core.online.domain.model

data class SessionPlayerState(
    val sessionId: String,
    val slotIndex: Int,
    val life: Int,
    val poison: Int,
    val energy: Int,
    val experience: Int,
    val commanderDamage: Map<String, Int>,
    val customCounters: Map<String, Int>,
    val pendingDefeat: Boolean,
    val defeated: Boolean,
    val hasPlayedLand: Boolean,
    val updatedAt: String,
)
