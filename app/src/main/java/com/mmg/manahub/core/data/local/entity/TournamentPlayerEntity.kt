package com.mmg.manahub.core.data.local.entity

import androidx.room.*

@Entity(
    tableName = "tournament_players",
    foreignKeys = [ForeignKey(
        entity         = TournamentEntity::class,
        parentColumns  = ["id"],
        childColumns   = ["tournamentId"],
        onDelete       = ForeignKey.CASCADE,
    )],
    indices = [Index("tournamentId")],
)
data class TournamentPlayerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "tournamentId")  val tournamentId: Long,
    @ColumnInfo(name = "playerName")    val playerName:   String,
    @ColumnInfo(name = "playerColor")   val playerColor:  String,  // hex e.g. "#E63946"
    @ColumnInfo(name = "deckId")        val deckId:       Long?   = null,
    @ColumnInfo(name = "seed")          val seed:         Int     = 0,
)
