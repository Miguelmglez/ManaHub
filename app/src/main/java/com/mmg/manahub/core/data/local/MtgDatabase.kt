package com.mmg.manahub.core.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mmg.manahub.core.data.local.converter.RoomConverters
import com.mmg.manahub.core.data.local.dao.*
import com.mmg.manahub.core.data.local.entity.*
import com.mmg.manahub.feature.draft.data.local.DraftSetDao
import com.mmg.manahub.feature.draft.data.local.DraftSetEntity
import com.mmg.manahub.feature.news.data.local.ContentSourceEntity
import com.mmg.manahub.feature.news.data.local.NewsArticleEntity
import com.mmg.manahub.feature.news.data.local.NewsDao
import com.mmg.manahub.feature.news.data.local.NewsVideoEntity

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
    version = 23,
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

// ── Gap migrations (no schema change, preserve user data) ────────────────────
// Room requires a continuous migration path. Any gap causes fallbackToDestructiveMigration
// to silently wipe user data for anyone on that intermediate version.

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Gap: no schema changes between versions 7 and 8.
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Gap: no schema changes between versions 8 and 9.
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Gap: no schema changes between versions 9 and 10.
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Gap migration: version 10→11 had no schema changes recorded.
        // This entry exists solely so Room does not fall back to destructive
        // migration for users who happen to be on version 10 with real data.
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Gap: no schema changes between versions 15 and 16.
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Gap: no schema changes between versions 16 and 17.
    }
}

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Change the FK on user_cards.scryfall_id from CASCADE to RESTRICT.
        //
        // Why: CASCADE silently deletes user collection entries whenever the parent
        // CardEntity row is replaced (e.g. by an upsert using REPLACE strategy).
        // RESTRICT makes any attempt to delete a referenced card fail with a
        // SQLiteConstraintException, forcing the caller to handle the situation
        // explicitly instead of silently destroying user data.
        //
        // SQLite does not support ALTER TABLE ... DROP/ADD FOREIGN KEY, so we
        // must recreate the table using the standard 12-step SQLite procedure.

        // Step 1 — disable FK enforcement during the operation.
        database.execSQL("PRAGMA foreign_keys = OFF")

        // Step 2 — create the new table with RESTRICT.
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS user_cards_new (
                id                INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                scryfall_id       TEXT    NOT NULL,
                quantity          INTEGER NOT NULL DEFAULT 1,
                is_foil           INTEGER NOT NULL DEFAULT 0,
                is_alternative_art INTEGER NOT NULL DEFAULT 0,
                condition         TEXT    NOT NULL DEFAULT 'NM',
                language          TEXT    NOT NULL DEFAULT 'en',
                is_for_trade      INTEGER NOT NULL DEFAULT 0,
                is_in_wishlist    INTEGER NOT NULL DEFAULT 0,
                min_trade_value   REAL,
                notes             TEXT,
                acquired_at       INTEGER,
                added_at          INTEGER NOT NULL,
                FOREIGN KEY(scryfall_id) REFERENCES cards(scryfall_id) ON DELETE RESTRICT
            )
        """.trimIndent())

        // Step 3 — copy all existing rows.
        database.execSQL("""
            INSERT INTO user_cards_new (
                id, scryfall_id, quantity, is_foil, is_alternative_art,
                condition, language, is_for_trade, is_in_wishlist,
                min_trade_value, notes, acquired_at, added_at
            )
            SELECT
                id, scryfall_id, quantity, is_foil, is_alternative_art,
                condition, language, is_for_trade, is_in_wishlist,
                min_trade_value, notes, acquired_at, added_at
            FROM user_cards
        """.trimIndent())

        // Step 4 — drop the old table.
        database.execSQL("DROP TABLE user_cards")

        // Step 5 — rename the new table.
        database.execSQL("ALTER TABLE user_cards_new RENAME TO user_cards")

        // Step 6 — recreate all indices that were on the original table.
        database.execSQL("CREATE INDEX IF NOT EXISTS index_user_cards_scryfall_id ON user_cards(scryfall_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_user_cards_is_for_trade ON user_cards(is_for_trade)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_user_cards_is_in_wishlist ON user_cards(is_in_wishlist)")
        database.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS
            index_user_cards_scryfall_id_is_foil_condition_language_is_alternative_art_is_in_wishlist
            ON user_cards (scryfall_id, is_foil, condition, language, is_alternative_art, is_in_wishlist)
        """.trimIndent())

        // Step 7 — re-enable FK enforcement and verify integrity.
        database.execSQL("PRAGMA foreign_keys = ON")
        database.execSQL("PRAGMA foreign_key_check(user_cards)")
    }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add sync tracking columns. Existing rows default to PENDING_UPLOAD (1) so
        // the first sync push uploads the full local collection to Supabase.
        database.execSQL(
            "ALTER TABLE user_cards ADD COLUMN sync_status INTEGER NOT NULL DEFAULT 1"
        )
        database.execSQL(
            "ALTER TABLE user_cards ADD COLUMN remote_id TEXT"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_user_cards_sync_status ON user_cards(sync_status)"
        )
    }
}

val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // All existing decks default to PENDING_UPLOAD so the first sync push
        // uploads the full local deck catalogue to Supabase.
        database.execSQL(
            "ALTER TABLE decks ADD COLUMN sync_status INTEGER NOT NULL DEFAULT 1"
        )
        database.execSQL(
            "ALTER TABLE decks ADD COLUMN remote_id TEXT"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_decks_sync_status ON decks(sync_status)"
        )
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
