package com.mmg.manahub.core.online.data.remote.dto

import com.mmg.manahub.core.online.domain.model.SessionPlayerState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class SessionPlayerStateDto(
    @SerialName("session_id")                val sessionId: String,
    @SerialName("slot_index")                val slotIndex: Int,
    @SerialName("life")                      val life: Int,
    @SerialName("poison")                    val poison: Int,
    @SerialName("energy")                    val energy: Int,
    @SerialName("experience")                val experience: Int,
    @SerialName("commander_damage_json")     val commanderDamageJson: JsonObject = JsonObject(emptyMap()),
    @SerialName("custom_counters_json")      val customCountersJson: JsonObject = JsonObject(emptyMap()),
    @SerialName("pending_defeat")            val pendingDefeat: Boolean = false,
    @SerialName("defeated")                  val defeated: Boolean = false,
    @SerialName("has_played_land")           val hasPlayedLand: Boolean = false,
    @SerialName("updated_at")                val updatedAt: String,
) {
    fun toDomain() = SessionPlayerState(
        sessionId       = sessionId,
        slotIndex       = slotIndex,
        life            = life,
        poison          = poison,
        energy          = energy,
        experience      = experience,
        commanderDamage = commanderDamageJson.entries.associate { (k, v) ->
            k to runCatching { v.jsonPrimitive.int }.getOrDefault(0)
        },
        customCounters  = customCountersJson.entries.associate { (k, v) ->
            k to runCatching { v.jsonPrimitive.int }.getOrDefault(0)
        },
        pendingDefeat   = pendingDefeat,
        defeated        = defeated,
        hasPlayedLand   = hasPlayedLand,
        updatedAt       = updatedAt,
    )
}
