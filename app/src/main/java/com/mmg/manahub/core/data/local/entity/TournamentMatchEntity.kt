package com.mmg.manahub.core.data.local.entity

import androidx.room.*

@Entity(
    tableName = "tournament_matches",
    foreignKeys = [ForeignKey(
        entity         = TournamentEntity::class,
        parentColumns  = ["id"],
        childColumns   = ["tournamentId"],
        onDelete       = ForeignKey.CASCADE,
    )],
    indices = [Index("tournamentId")],
)
data class TournamentMatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "tournamentId")     val tournamentId:    Long,
    @ColumnInfo(name = "round")            val round:           Int,
    @ColumnInfo(name = "playerIds")        val playerIds:       String,          // JSON: [id1, id2]
    @ColumnInfo(name = "winnerId")         val winnerId:        Long?   = null,
    @ColumnInfo(name = "status")           val status:          String  = "PENDING",  // "PENDING"|"ACTIVE"|"FINISHED"
    @ColumnInfo(name = "gameSessionId")    val gameSessionId:   Long?   = null,
    @ColumnInfo(name = "scheduledOrder")   val scheduledOrder:  Int     = 0,
    @ColumnInfo(name = "finalLifeTotals")  val finalLifeTotals: String  = "",    // JSON: {id:life, ...}
)
