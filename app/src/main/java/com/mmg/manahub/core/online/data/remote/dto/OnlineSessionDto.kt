package com.mmg.manahub.core.online.data.remote.dto

import com.mmg.manahub.core.online.domain.model.OnlineSession
import com.mmg.manahub.core.online.domain.model.OnlineSessionStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OnlineSessionDto(
    @SerialName("id")                    val id: String,
    @SerialName("code")                  val code: String,
    @SerialName("host_user_id")          val hostUserId: String,
    @SerialName("game_mode")             val gameMode: String,
    @SerialName("player_count")          val playerCount: Int,
    @SerialName("layout_key")            val layoutKey: String? = null,
    @SerialName("status")                val status: String,
    @SerialName("tournament_id")         val tournamentId: String? = null,
    @SerialName("tournament_match_id")   val tournamentMatchId: String? = null,
    @SerialName("created_at")            val createdAt: String,
    @SerialName("started_at")            val startedAt: String? = null,
    @SerialName("finished_at")           val finishedAt: String? = null,
    @SerialName("last_activity_at")      val lastActivityAt: String,
) {
    fun toDomain() = OnlineSession(
        id                = id,
        code              = code,
        hostUserId        = hostUserId,
        gameMode          = gameMode,
        playerCount       = playerCount,
        layoutKey         = layoutKey,
        status            = runCatching { OnlineSessionStatus.valueOf(status) }.getOrDefault(OnlineSessionStatus.ABANDONED),
        tournamentId      = tournamentId,
        tournamentMatchId = tournamentMatchId,
        createdAt         = createdAt,
        startedAt         = startedAt,
        finishedAt        = finishedAt,
        lastActivityAt    = lastActivityAt,
    )
}
