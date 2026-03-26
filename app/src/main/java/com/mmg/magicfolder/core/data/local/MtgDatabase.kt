package com.mmg.magicfolder.core.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mmg.magicfolder.core.data.local.converter.RoomConverters
import com.mmg.magicfolder.core.data.local.dao.*
import com.mmg.magicfolder.core.data.local.entity.*

@Database(
    entities = [
        CardEntity::class,
        UserCardEntity::class,
        DeckEntity::class,
        DeckCardCrossRef::class,
        ManaSymbolEntity::class,
        GameSessionEntity::class,
        PlayerSessionEntity::class,
        SurveyAnswerEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class MtgDatabase : RoomDatabase() {
    abstract fun cardDao():           CardDao
    abstract fun userCardDao():       UserCardDao
    abstract fun deckDao():           DeckDao
    abstract fun statsDao():          StatsDao
    abstract fun manaSymbolDao():     ManaSymbolDao
    abstract fun gameSessionDao():    GameSessionDao
    abstract fun surveyAnswerDao():   SurveyAnswerDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE cards ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS mana_symbols (
                symbol      TEXT    NOT NULL PRIMARY KEY,
                svgUri      TEXT    NOT NULL,
                description TEXT    NOT NULL,
                isHybrid    INTEGER NOT NULL,
                isPhyrexian INTEGER NOT NULL,
                cachedAt    INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS game_sessions (
                id          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                playedAt    INTEGER NOT NULL,
                durationMs  INTEGER NOT NULL,
                mode        TEXT    NOT NULL,
                totalTurns  INTEGER NOT NULL,
                playerCount INTEGER NOT NULL,
                winnerId    INTEGER NOT NULL,
                winnerName  TEXT    NOT NULL,
                notes       TEXT    NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS player_sessions (
                id                      INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sessionId               INTEGER NOT NULL,
                playerId                INTEGER NOT NULL,
                playerName              TEXT    NOT NULL,
                finalLife               INTEGER NOT NULL,
                finalPoison             INTEGER NOT NULL,
                eliminationReason       TEXT,
                commanderDamageDealt    INTEGER NOT NULL DEFAULT 0,
                commanderDamageReceived INTEGER NOT NULL DEFAULT 0,
                deckId                  INTEGER,
                deckName                TEXT,
                opponentColors          TEXT    NOT NULL DEFAULT '',
                isWinner                INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(sessionId) REFERENCES game_sessions(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_player_sessions_sessionId
            ON player_sessions(sessionId)
            """.trimIndent()
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS survey_answers (
                id          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sessionId   INTEGER NOT NULL,
                questionKey TEXT    NOT NULL,
                answerJson  TEXT    NOT NULL,
                answeredAt  INTEGER NOT NULL,
                FOREIGN KEY(sessionId) REFERENCES game_sessions(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_survey_answers_sessionId
            ON survey_answers(sessionId)
            """.trimIndent()
        )
    }
}
