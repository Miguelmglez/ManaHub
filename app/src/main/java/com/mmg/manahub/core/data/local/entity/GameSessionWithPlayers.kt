package com.mmg.manahub.core.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class GameSessionWithPlayers(
    @Embedded val session: GameSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val players: List<PlayerSessionEntity>,
)
