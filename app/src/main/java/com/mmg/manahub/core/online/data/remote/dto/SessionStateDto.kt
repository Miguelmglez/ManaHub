package com.mmg.manahub.core.online.data.remote.dto

import com.mmg.manahub.core.online.domain.model.SessionState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class SessionStateDto(
    @SerialName("session_id")           val sessionId: String,
    @SerialName("current_phase")        val currentPhase: String,
    @SerialName("active_player_slot")   val activePlayerSlot: Int,
    @SerialName("turn_number")          val turnNumber: Int,
    @SerialName("phase_stops_json")     val phaseStopsJson: JsonObject = JsonObject(emptyMap()),
    @SerialName("last_dice_result")     val lastDiceResult: Int? = null,
    @SerialName("last_coin_result")     val lastCoinResult: String? = null,
    @SerialName("updated_at")           val updatedAt: String,
) {
    fun toDomain() = SessionState(
        sessionId       = sessionId,
        currentPhase    = currentPhase,
        activePlayerSlot = activePlayerSlot,
        turnNumber      = turnNumber,
        phaseStops      = phaseStopsJson.entries.associate { (k, v) ->
            k to runCatching { v.jsonPrimitive.boolean }.getOrDefault(false)
        },
        lastDiceResult  = lastDiceResult,
        lastCoinResult  = lastCoinResult,
        updatedAt       = updatedAt,
    )
}
