package com.mmg.manahub.core.nearby.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sealed hierarchy of all messages exchanged between players via Nearby Connections.
 *
 * The [kotlinx.serialization] `type` field (controlled by [classDiscriminator]) is set to
 * `"type"` so that every serialized payload carries a discriminator key, e.g.:
 * `{"type":"life","slot":0,"life":20}`.
 *
 * All subtypes are [Serializable] and must be registered in the polymorphic serializer
 * configured inside [com.mmg.manahub.core.nearby.data.NearbySessionRepositoryImpl].
 */
@Serializable
sealed class NearbyGameMessage {

    /** Notifies all peers that a player's life total has changed. */
    @Serializable
    @SerialName("life")
    data class LifeChanged(val slot: Int, val life: Int) : NearbyGameMessage()

    /** Notifies all peers that a player's poison counter has changed. */
    @Serializable
    @SerialName("poison")
    data class PoisonChanged(val slot: Int, val poison: Int) : NearbyGameMessage()

    /** Notifies all peers that a player's experience counter has changed. */
    @Serializable
    @SerialName("experience")
    data class ExperienceChanged(val slot: Int, val experience: Int) : NearbyGameMessage()

    /** Notifies all peers that a player's energy counter has changed. */
    @Serializable
    @SerialName("energy")
    data class EnergyChanged(val slot: Int, val energy: Int) : NearbyGameMessage()

    /**
     * Notifies all peers that commander damage dealt from one player to another has changed.
     *
     * @param fromSlot The attacking commander's owner slot.
     * @param toSlot   The defending player's slot.
     * @param damage   The new cumulative commander damage value.
     */
    @Serializable
    @SerialName("commander_dmg")
    data class CommanderDamageChanged(
        val fromSlot: Int,
        val toSlot: Int,
        val damage: Int,
    ) : NearbyGameMessage()

    /** Notifies all peers that the active game phase has changed. */
    @Serializable
    @SerialName("phase")
    data class PhaseChanged(val phase: String) : NearbyGameMessage()

    /** Notifies all peers that the active turn has advanced. */
    @Serializable
    @SerialName("turn")
    data class TurnChanged(val turnNumber: Int, val activeSlot: Int) : NearbyGameMessage()

    /** Notifies all peers that a player has been defeated and confirmed their defeat. */
    @Serializable
    @SerialName("defeat")
    data class DefeatConfirmed(val slot: Int) : NearbyGameMessage()

    /** Notifies all peers that a previously confirmed defeat has been revoked (e.g. undo). */
    @Serializable
    @SerialName("defeat_revoked")
    data class DefeatRevoked(val slot: Int) : NearbyGameMessage()

    /** Notifies all peers that the active player has toggled their land-played status. */
    @Serializable
    @SerialName("land_toggled")
    data class LandToggled(val slot: Int, val played: Boolean) : NearbyGameMessage()

    /** Notifies all peers that the game has ended with a winner. */
    @Serializable
    @SerialName("game_finished")
    data class GameFinished(val winnerSlot: Int) : NearbyGameMessage()

    /**
     * Sent by the host to a newly connected client to bring it up to date with the full
     * game state at the moment of connection.
     */
    @Serializable
    @SerialName("full_sync")
    data class FullStateSync(
        val players: List<PlayerSnapshot>,
        val phase: String,
        val turnNumber: Int,
        val activeSlot: Int,
    ) : NearbyGameMessage()

    /**
     * Immutable snapshot of a single player's counters at a given instant.
     *
     * @param commanderDamage Map from attacking slot to cumulative damage dealt to this player.
     */
    @Serializable
    data class PlayerSnapshot(
        val slot: Int,
        val life: Int,
        val poison: Int,
        val experience: Int,
        val energy: Int,
        val defeated: Boolean,
        val commanderDamage: Map<Int, Int> = emptyMap(),
    )
}
