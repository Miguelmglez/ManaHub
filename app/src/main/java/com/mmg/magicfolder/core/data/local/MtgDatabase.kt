package com.mmg.magicfolder.core.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mmg.magicfolder.core.data.local.converter.RoomConverters
import com.mmg.magicfolder.core.data.local.dao.*
import com.mmg.magicfolder.core.data.local.entity.*
import com.mmg.magicfolder.feature.draft.data.local.DraftSetDao
import com.mmg.magicfolder.feature.draft.data.local.DraftSetEntity
import com.mmg.magicfolder.feature.news.data.local.ContentSourceEntity
import com.mmg.magicfolder.feature.news.data.local.NewsArticleEntity
import com.mmg.magicfolder.feature.news.data.local.NewsDao
import com.mmg.magicfolder.feature.news.data.local.NewsVideoEntity

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
        TournamentEntity::class,
        TournamentPlayerEntity::class,
        TournamentMatchEntity::class,
        NewsArticleEntity::class,
        NewsVideoEntity::class,
        ContentSourceEntity::class,
        DraftSetEntity::class,
    ],
    version = 20,
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
    abstract fun tournamentDao():     TournamentDao
    abstract fun newsDao():           NewsDao
    abstract fun draftSetDao():      DraftSetDao
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
                answeredAt  INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(sessionId) REFERENCES game_sessions(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_survey_answers_sessionId ON survey_answers(sessionId)"
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS tournaments (
                id               INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name             TEXT    NOT NULL,
                format           TEXT    NOT NULL,
                structure        TEXT    NOT NULL,
                status           TEXT    NOT NULL DEFAULT 'SETUP',
                matchesPerPairing INTEGER NOT NULL DEFAULT 1,
                isRandomPairings INTEGER NOT NULL DEFAULT 1,
                createdAt        INTEGER NOT NULL,
                finishedAt       INTEGER
            )
        """.trimIndent())
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS tournament_players (
                id            INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                tournamentId  INTEGER NOT NULL,
                playerName    TEXT    NOT NULL,
                playerColor   TEXT    NOT NULL,
                deckId        INTEGER,
                seed          INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(tournamentId) REFERENCES tournaments(id) ON DELETE CASCADE
            )
        """.trimIndent())
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS tournament_matches (
                id              INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                tournamentId    INTEGER NOT NULL,
                round           INTEGER NOT NULL,
                playerIds       TEXT    NOT NULL,
                winnerId        INTEGER,
                status          TEXT    NOT NULL DEFAULT 'PENDING',
                gameSessionId   INTEGER,
                scheduledOrder  INTEGER NOT NULL DEFAULT 0,
                finalLifeTotals TEXT    NOT NULL DEFAULT '',
                FOREIGN KEY(tournamentId) REFERENCES tournaments(id) ON DELETE CASCADE
            )
        """.trimIndent())
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_tm_tournamentId ON tournament_matches(tournamentId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_tp_tournamentId ON tournament_players(tournamentId)")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Drop old schema (questionKey / answerJson) and recreate with new columns
        database.execSQL("DROP TABLE IF EXISTS survey_answers")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS survey_answers (
                id            INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sessionId     INTEGER NOT NULL,
                questionId    TEXT    NOT NULL,
                questionType  TEXT    NOT NULL,
                answer        TEXT    NOT NULL,
                cardReference TEXT,
                answeredAt    INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(sessionId) REFERENCES game_sessions(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_survey_sessionId ON survey_answers(sessionId)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_survey_cardRef ON survey_answers(cardReference)"
        )
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE content_sources ADD COLUMN language TEXT NOT NULL DEFAULT 'en'"
        )
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE user_cards ADD COLUMN is_alternative_art INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "DROP INDEX IF EXISTS index_user_cards_scryfall_id_is_foil_condition_language"
        )
        database.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS
            index_user_cards_scryfall_id_is_foil_condition_language_is_alternative_art
            ON user_cards (scryfall_id, is_foil, condition, language, is_alternative_art)
        """.trimIndent())
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS draft_sets (
                id          TEXT    NOT NULL PRIMARY KEY,
                code        TEXT    NOT NULL,
                name        TEXT    NOT NULL,
                setType     TEXT    NOT NULL,
                releasedAt  TEXT    NOT NULL,
                iconSvgUri  TEXT    NOT NULL,
                cardCount   INTEGER NOT NULL,
                scryfallUri TEXT    NOT NULL,
                cachedAt    INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE cards ADD COLUMN suggested_tags TEXT NOT NULL DEFAULT '[]'"
        )
        // Old `tags` column stored enum names; new format is JSON objects.
        database.execSQL("UPDATE cards SET tags = '[]'")
    }
}

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE cards ADD COLUMN user_tags TEXT NOT NULL DEFAULT '[]'"
        )
    }
}

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Expand the unique constraint to include is_in_wishlist so that the same
        // card+attributes combination can exist as both a collection entry and a
        // wishlist entry simultaneously.
        database.execSQL(
            "DROP INDEX IF EXISTS index_user_cards_scryfall_id_is_foil_condition_language_is_alternative_art"
        )
        database.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS
            index_user_cards_scryfall_id_is_foil_condition_language_is_alternative_art_is_in_wishlist
            ON user_cards (scryfall_id, is_foil, condition, language, is_alternative_art, is_in_wishlist)
        """.trimIndent())
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS news_articles (
                id           TEXT NOT NULL PRIMARY KEY,
                title        TEXT NOT NULL,
                description  TEXT NOT NULL,
                image_url    TEXT,
                published_at INTEGER NOT NULL,
                source_name  TEXT NOT NULL,
                source_id    TEXT NOT NULL,
                url          TEXT NOT NULL,
                author       TEXT,
                fetched_at   INTEGER NOT NULL
            )
        """.trimIndent())

        database.execSQL("""
            CREATE TABLE IF NOT EXISTS news_videos (
                video_id     TEXT NOT NULL PRIMARY KEY,
                title        TEXT NOT NULL,
                description  TEXT NOT NULL,
                image_url    TEXT,
                published_at INTEGER NOT NULL,
                source_name  TEXT NOT NULL,
                source_id    TEXT NOT NULL,
                url          TEXT NOT NULL,
                channel_name TEXT NOT NULL,
                duration     TEXT,
                fetched_at   INTEGER NOT NULL
            )
        """.trimIndent())

        database.execSQL("""
            CREATE TABLE IF NOT EXISTS content_sources (
                id         TEXT    NOT NULL PRIMARY KEY,
                name       TEXT    NOT NULL,
                feed_url   TEXT    NOT NULL,
                type       TEXT    NOT NULL,
                is_enabled INTEGER NOT NULL DEFAULT 1,
                is_default INTEGER NOT NULL DEFAULT 1,
                icon_url   TEXT
            )
        """.trimIndent())
    }
}
