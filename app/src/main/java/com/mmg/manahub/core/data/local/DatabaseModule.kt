package com.mmg.manahub.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.local.dao.DraftSessionDao
import com.mmg.manahub.core.data.local.dao.GameSessionDao
import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.dao.GamificationStatsDao
import com.mmg.manahub.core.data.local.dao.ManaSymbolDao
import com.mmg.manahub.core.data.local.dao.PlaytestDao
import com.mmg.manahub.core.data.local.dao.StatsDao
import com.mmg.manahub.core.data.local.dao.SurveyAnswerDao
import com.mmg.manahub.core.data.local.dao.SurveyCardImpactDao
import com.mmg.manahub.core.data.local.dao.TournamentDao
import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.paging.RemoteKeyDao
import com.mmg.manahub.feature.draft.data.local.DraftSetDao
import com.mmg.manahub.feature.friends.data.local.dao.FriendDao
import com.mmg.manahub.feature.news.data.local.NewsDao
import com.mmg.manahub.feature.trades.data.local.dao.LocalOpenForTradeDao
import com.mmg.manahub.feature.trades.data.local.dao.LocalWishlistDao
import com.mmg.manahub.feature.trades.data.local.dao.TradeCollectionSyncDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideMtgDatabase(
        @ApplicationContext context: Context,
        syncPrefs: SyncPreferencesStore,
    ): MtgDatabase =
        Room.databaseBuilder(context, MtgDatabase::class.java, "mtg_collection.db")
            // Versions 1–24 are dev/beta only. The 24→25 migration is destructive for
            // `decks` and `user_cards` by design (PK type changed from INTEGER to TEXT UUID).
            // No production user data existed before v25.
            // dropAllTables = true: when falling back, drop and recreate the entire schema
            // (not just the migrated tables) to guarantee a clean slate.
            .fallbackToDestructiveMigrationFrom(
                dropAllTables = true,
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                21, 22, 23, 24
            )
            // When Room wipes the database via destructive migration, DataStore survives
            // the wipe and retains the old sync watermark. A stale watermark causes
            // getChangesSince() to return 0 rows (all Supabase data pre-dates it),
            // leaving the user's cloud collection invisible until the watermark is reset.
            // Clearing all watermarks here ensures the next sync performs a full pull.
            .addCallback(object : RoomDatabase.Callback() {
                override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                    runBlocking { syncPrefs.clearAllWatermarks() }
                }
            })
            // All migrations from v25 onward are explicit and data-safe.
            // fallbackToDestructiveMigration() is NOT called — any missing migration
            // will throw an IllegalStateException at startup rather than silently
            // deleting user data.
            .addMigrations(
                MIGRATION_25_26,
                MIGRATION_26_27,
                MIGRATION_27_28,
                MIGRATION_28_29,
                MIGRATION_29_30,
                MIGRATION_30_31,
                MIGRATION_31_32,
                MIGRATION_32_33,
                MIGRATION_33_34,
                MIGRATION_34_35,
                MIGRATION_35_36,
                // v36 → v37 lives as a top-level `val` in Migration_36_37.kt so the
                // instrumented MigrationTestHelper test can reference it directly.
                MIGRATION_36_37,
                // v37 → v38 lives as a top-level `val` in Migration_37_38.kt (same
                // reason). Adds nullable edhrec_rank / penny_rank to `cards`.
                MIGRATION_37_38,
                // v38 → v39 lives as a top-level `val` in Migration_38_39.kt (same
                // reason). Additive: creates the 6 gamification tables (ADR-002 §8).
                MIGRATION_38_39,
                // v39 → v40 lives as a top-level `val` in Migration_39_40.kt (same
                // reason). Additive: adds legality_legacy/vintage/pauper to `cards`
                // (Deck Doctor Phase 4, D2).
                MIGRATION_39_40,
            )
            .build()

    // -------------------------------------------------------------------------
    // v25 → v26
    // Adds three new columns to `cards`:
    //   - related_uris  (JSON map, default '{}')
    //   - purchase_uris (JSON map, default '{}')
    //   - game_changer  (Boolean as INTEGER, default 0)
    // All three are nullable-safe: NOT NULL with a DEFAULT means existing rows
    // get the default value automatically — no data loss possible.
    // -------------------------------------------------------------------------
    private val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!columnExists(db, "cards", "related_uris")) {
                db.execSQL("ALTER TABLE cards ADD COLUMN related_uris  TEXT NOT NULL DEFAULT '{}'")
            }
            if (!columnExists(db, "cards", "purchase_uris")) {
                db.execSQL("ALTER TABLE cards ADD COLUMN purchase_uris TEXT NOT NULL DEFAULT '{}'")
            }
            if (!columnExists(db, "cards", "game_changer")) {
                db.execSQL("ALTER TABLE cards ADD COLUMN game_changer  INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

    // -------------------------------------------------------------------------
    // v26 → v27
    // Adds `commander_card_id` (nullable TEXT) to `decks`.
    // Existing decks get NULL, which is correct — only Commander-format decks
    // will have this set explicitly by the user.
    // -------------------------------------------------------------------------
    private val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!columnExists(db, "decks", "commander_card_id")) {
                db.execSQL("ALTER TABLE decks ADD COLUMN commander_card_id TEXT")
            }
        }
    }

    // -------------------------------------------------------------------------
    // v27 → v28
    // Creates two new tables for offline (guest) trade list support:
    //   - local_wishlists: cards the user wants to acquire via trade
    //   - local_open_for_trade: cards the user is willing to trade away
    // Both tables carry a `synced` flag so rows can be migrated to Supabase
    // when the user logs in for the first time.
    // Indexes are created explicitly because Room's @Index generates them only
    // during CREATE TABLE from scratch — this migration must add them manually.
    // -------------------------------------------------------------------------
    private val MIGRATION_27_28 = object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS local_wishlists (
                    id TEXT NOT NULL PRIMARY KEY,
                    scryfall_id TEXT NOT NULL,
                    match_any_variant INTEGER NOT NULL DEFAULT 1,
                    is_foil INTEGER,
                    condition TEXT,
                    language TEXT,
                    is_alt_art INTEGER,
                    synced INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS local_open_for_trade (
                    id TEXT NOT NULL PRIMARY KEY,
                    local_collection_id TEXT NOT NULL,
                    scryfall_id TEXT NOT NULL,
                    synced INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_local_wishlists_scryfall_id ON local_wishlists(scryfall_id)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_local_open_for_trade_collection_id ON local_open_for_trade(local_collection_id)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_local_open_for_trade_scryfall_id ON local_open_for_trade(scryfall_id)"
            )
        }
    }

    // -------------------------------------------------------------------------
    // v28 → v29
    // Adds `card_faces` (nullable TEXT) to `cards`.
    // Stores a JSON array of CardFace objects for double-faced / multi-face cards.
    // Single-faced cards get NULL, which is the correct default — no data loss.
    // The guard via columnExists() makes the migration idempotent.
    // -------------------------------------------------------------------------
    private val MIGRATION_28_29 = object : Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!columnExists(db, "cards", "card_faces")) {
                db.execSQL("ALTER TABLE cards ADD COLUMN card_faces TEXT")
            }
        }
    }

    // -------------------------------------------------------------------------
    // v29 → v30
    // Adds denormalized columns to `local_open_for_trade` so that TradesScreen
    // can display card variant details (foil, condition, language, alt art)
    // without a JOIN to user_card_collection.  Also adds `quantity` to support
    // partial trade offers (e.g. offering 1 of 3 identical copies).
    // All columns use NOT NULL with a DEFAULT so existing rows are safe.
    // -------------------------------------------------------------------------
    private val MIGRATION_29_30 = object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!columnExists(db, "local_open_for_trade", "quantity")) {
                db.execSQL("ALTER TABLE local_open_for_trade ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1")
            }
            if (!columnExists(db, "local_open_for_trade", "is_foil")) {
                db.execSQL("ALTER TABLE local_open_for_trade ADD COLUMN is_foil INTEGER NOT NULL DEFAULT 0")
            }
            if (!columnExists(db, "local_open_for_trade", "condition")) {
                db.execSQL("ALTER TABLE local_open_for_trade ADD COLUMN condition TEXT NOT NULL DEFAULT 'NM'")
            }
            if (!columnExists(db, "local_open_for_trade", "language")) {
                db.execSQL("ALTER TABLE local_open_for_trade ADD COLUMN language TEXT NOT NULL DEFAULT 'en'")
            }
            if (!columnExists(db, "local_open_for_trade", "is_alt_art")) {
                db.execSQL("ALTER TABLE local_open_for_trade ADD COLUMN is_alt_art INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

    // -------------------------------------------------------------------------
    // v30 → v31
    // Adds `quantity` to `local_wishlists` to support grouping entries with
    // identical attributes.
    // -------------------------------------------------------------------------
    private val MIGRATION_30_31 = object : Migration(30, 31) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!columnExists(db, "local_wishlists", "quantity")) {
                db.execSQL("ALTER TABLE local_wishlists ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1")
            }
        }
    }

    // -------------------------------------------------------------------------
    // v31 → v32
    // Creates the `outgoing_friend_requests` table to cache friend requests sent
    // by the current user that are still PENDING (friendships where user_id_1 = me
    // AND status = 'PENDING' on Supabase).
    //
    // Kept as a separate table from `friend_requests` (incoming) because the column
    // semantics differ: outgoing uses `to_*` columns, incoming uses `from_*`. A
    // single table with an `is_outgoing` flag would require nullable columns and a
    // filter predicate on every reactive query — a clean split is cheaper and clearer.
    //
    // Index on `to_user_id`: lets the repository quickly check whether a specific
    // user already has a pending request from us (prevents duplicate send attempts).
    // -------------------------------------------------------------------------
    private val MIGRATION_31_32 = object : Migration(31, 32) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `outgoing_friend_requests` (`id` TEXT NOT NULL, `to_user_id` TEXT NOT NULL, `to_nickname` TEXT NOT NULL, `to_game_tag` TEXT NOT NULL, `to_avatar_url` TEXT, `created_at` INTEGER NOT NULL, `cached_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            // OutgoingFriendRequestEntity has no @Index declaration, so Room 2.8 expects
            // indices = emptySet() and fails if any index exists. An earlier version of this
            // migration accidentally created this index; drop it so devices that already ran
            // that version also pass schema validation on the next startup.
            db.execSQL("DROP INDEX IF EXISTS `idx_outgoing_requests_to_user_id`")
        }
    }

    // -------------------------------------------------------------------------
    // v32 → v33
    // Recreates the `draft_sets` table to match the new Cloudflare-based schema.
    //
    // Columns removed: setType, cardCount, scryfallUri (not present in the new
    //   Cloudflare sets-index.json source).
    // Columns added: guideVersion TEXT NOT NULL DEFAULT '', tierListVersion TEXT NOT NULL DEFAULT ''
    //   (used for client-side cache invalidation per set).
    //
    // SQLite does not support DROP COLUMN, so we use the recommended 12-step table
    // recreation pattern:
    //   1. Create a new table with the target schema.
    //   2. Copy compatible columns from the old table.
    //   3. Drop the old table.
    //   4. Rename the new table.
    //
    // draft_sets has no referencing foreign keys, so no cascade or constraint
    // changes are needed. The table is a pure cache — data loss is acceptable
    // and the next app launch will re-populate it from Cloudflare.
    // -------------------------------------------------------------------------
    private val MIGRATION_32_33 = object : Migration(32, 33) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Step 1: create the new table with the target schema
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `draft_sets_new` (
                    `id` TEXT NOT NULL,
                    `code` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `releasedAt` TEXT NOT NULL,
                    `iconSvgUri` TEXT NOT NULL,
                    `guideVersion` TEXT NOT NULL DEFAULT '',
                    `tierListVersion` TEXT NOT NULL DEFAULT '',
                    `cachedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            // Step 2: copy rows that exist in both schemas, using empty strings for new columns
            db.execSQL(
                """
                INSERT INTO `draft_sets_new` (`id`, `code`, `name`, `releasedAt`, `iconSvgUri`, `guideVersion`, `tierListVersion`, `cachedAt`)
                SELECT `id`, `code`, `name`, `releasedAt`, `iconSvgUri`, '', '', `cachedAt`
                FROM `draft_sets`
                """.trimIndent()
            )
            // Step 3: drop old table
            db.execSQL("DROP TABLE IF EXISTS `draft_sets`")
            // Step 4: rename new table to target name
            db.execSQL("ALTER TABLE `draft_sets_new` RENAME TO `draft_sets`")
        }
    }

    // -------------------------------------------------------------------------
    // v33 → v34
    // Survey & game-stats overhaul:
    //   - game_sessions gains surveyStatus (TEXT, default 'PENDING'), surveyCompletedAt
    //     (nullable INTEGER) and deckId (nullable TEXT, UUID).
    //   - survey_answers gains deckId (nullable TEXT, UUID) and updatedAt (NOT NULL,
    //     default = answeredAt) plus an index on deckId for per-deck queries.
    //
    // Legacy rows: existing surveys that contain at least one answer row are bumped
    // to surveyStatus = 'COMPLETED' so the new UI shows them as reviewable. The
    // deckId backfill is intentionally left NULL — pre-migration games did not track
    // the app user's deck UUID (player_sessions.deckId is a legacy Long that no
    // longer maps to anything), so we let the user re-associate via the survey
    // edit flow rather than guess.
    // -------------------------------------------------------------------------
    private val MIGRATION_33_34 = object : Migration(33, 34) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!columnExists(db, "game_sessions", "surveyStatus")) {
                db.execSQL("ALTER TABLE game_sessions ADD COLUMN surveyStatus TEXT NOT NULL DEFAULT 'PENDING'")
            }
            if (!columnExists(db, "game_sessions", "surveyCompletedAt")) {
                db.execSQL("ALTER TABLE game_sessions ADD COLUMN surveyCompletedAt INTEGER")
            }
            if (!columnExists(db, "game_sessions", "deckId")) {
                db.execSQL("ALTER TABLE game_sessions ADD COLUMN deckId TEXT")
            }
            if (!columnExists(db, "survey_answers", "deckId")) {
                db.execSQL("ALTER TABLE survey_answers ADD COLUMN deckId TEXT")
            }
            if (!columnExists(db, "survey_answers", "updatedAt")) {
                db.execSQL("ALTER TABLE survey_answers ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE survey_answers SET updatedAt = MAX(answeredAt, 1) WHERE updatedAt = 0")
            }
            db.execSQL("CREATE INDEX IF NOT EXISTS index_survey_answers_deckId ON survey_answers(deckId)")

            // Mark pre-migration surveys as COMPLETED if they have any answer rows.
            db.execSQL(
                """
                UPDATE game_sessions
                SET surveyStatus = 'COMPLETED',
                    surveyCompletedAt = playedAt
                WHERE id IN (SELECT DISTINCT sessionId FROM survey_answers)
                """.trimIndent()
            )
        }
    }

    // -------------------------------------------------------------------------
    // v34 → v35
    // Creates the `trade_collection_sync` table.
    //
    // Purpose: tracks per-user, per-proposal collection sync state so the UI
    // can show "Collection updated" instead of the "Update Collection" button
    // after the user has already tapped it, even across process kills.
    //
    // Composite primary key (proposal_id, user_id): one record per user per
    // proposal. An IGNORE conflict strategy in the DAO prevents double-inserts.
    // -------------------------------------------------------------------------
    private val MIGRATION_34_35 = object : Migration(34, 35) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS trade_collection_sync (
                    proposal_id TEXT NOT NULL,
                    user_id     TEXT NOT NULL,
                    synced_at   INTEGER NOT NULL,
                    PRIMARY KEY(proposal_id, user_id)
                )
                """.trimIndent()
            )
        }
    }

    // -------------------------------------------------------------------------
    // v35 → v36  — combined migration (Deck Playtest tables + stats/collection rebuild)
    //
    // Deck Playtest (additive, no existing tables touched):
    //   playtest_sessions      — one row per saved test; deck_id is plain TEXT (no FK)
    //                            because decks are soft-deleted and history must survive.
    //   playtest_card_stats    — per-card copy counts (opening hand + mulligan bottoms).
    //                            Counts not booleans: a hand can hold 2–4 copies of the
    //                            same card. FK to playtest_sessions CASCADE.
    //   playtest_survey_answers — optional survey for a saved test. FK CASCADE.
    //
    // Per-seat stats model (ADR-001) + collection cleanup:
    //   player_sessions: deckId (legacy INTEGER) retyped to deck_id TEXT (UUID);
    //     new columns is_local, archetype, linked_profile_tag via 12-step recreation.
    //   survey_card_impacts: new per-seat MVP/DEAD card ratings table.
    //   survey_answers: new `status` column (DRAFT/COMPLETED lifecycle).
    //   user_card_collection: drop is_alternative_art (variant picker replaced it).
    //   local_wishlists: drop is_alt_art.
    //   local_open_for_trade: drop is_alt_art.
    //
    // Tables with column drops use the 12-step SQLite table-recreation pattern.
    // All indices are created explicitly (Room generates them only on fresh installs).
    // -------------------------------------------------------------------------
    private val MIGRATION_35_36 = object : Migration(35, 36) {
        override fun migrate(db: SupportSQLiteDatabase) {

            // ── playtest_sessions ────────────────────────────────────────────────
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playtest_sessions` (
                    `id`             INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `deck_id`        TEXT NOT NULL,
                    `format`         TEXT NOT NULL,
                    `draw_count`     INTEGER NOT NULL,
                    `mulligans_used` INTEGER NOT NULL,
                    `library_size`   INTEGER NOT NULL,
                    `on_the_play`    INTEGER NOT NULL,
                    `started_at`     INTEGER NOT NULL,
                    `saved_at`       INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playtest_sessions_deck_id` ON `playtest_sessions`(`deck_id`)"
            )

            // ── playtest_card_stats ──────────────────────────────────────────────
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playtest_card_stats` (
                    `id`                          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `playtest_session_id`         INTEGER NOT NULL,
                    `scryfall_id`                 TEXT NOT NULL,
                    `copies_in_opening_hand`      INTEGER NOT NULL,
                    `copies_bottomed_on_mulligan` INTEGER NOT NULL,
                    FOREIGN KEY(`playtest_session_id`) REFERENCES `playtest_sessions`(`id`) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playtest_card_stats_session_id` ON `playtest_card_stats`(`playtest_session_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playtest_card_stats_scryfall_id` ON `playtest_card_stats`(`scryfall_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playtest_card_stats_session_card` ON `playtest_card_stats`(`playtest_session_id`, `scryfall_id`)"
            )

            // ── playtest_survey_answers ──────────────────────────────────────────
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playtest_survey_answers` (
                    `id`                  INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `playtest_session_id` INTEGER NOT NULL,
                    `question_id`         TEXT NOT NULL,
                    `question_type`       TEXT NOT NULL,
                    `answer`              TEXT NOT NULL,
                    `card_reference`      TEXT,
                    `deck_id`             TEXT,
                    `answered_at`         INTEGER NOT NULL,
                    `updated_at`          INTEGER NOT NULL,
                    FOREIGN KEY(`playtest_session_id`) REFERENCES `playtest_sessions`(`id`) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playtest_survey_answers_session_id` ON `playtest_survey_answers`(`playtest_session_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playtest_survey_answers_card_reference` ON `playtest_survey_answers`(`card_reference`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playtest_survey_answers_deck_id` ON `playtest_survey_answers`(`deck_id`)"
            )

            // ── player_sessions: rebuild to retype deckId → deck_id TEXT and add columns ──
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `player_sessions_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `sessionId` INTEGER NOT NULL,
                    `playerId` INTEGER NOT NULL,
                    `playerName` TEXT NOT NULL,
                    `finalLife` INTEGER NOT NULL,
                    `finalPoison` INTEGER NOT NULL,
                    `eliminationReason` TEXT,
                    `commanderDamageDealt` INTEGER NOT NULL,
                    `commanderDamageReceived` INTEGER NOT NULL,
                    `deck_id` TEXT,
                    `deckName` TEXT,
                    `opponentColors` TEXT NOT NULL,
                    `isWinner` INTEGER NOT NULL,
                    `is_local` INTEGER NOT NULL DEFAULT 0,
                    `archetype` TEXT,
                    `linked_profile_tag` TEXT,
                    FOREIGN KEY(`sessionId`) REFERENCES `game_sessions`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `player_sessions_new` (
                    `id`, `sessionId`, `playerId`, `playerName`, `finalLife`, `finalPoison`,
                    `eliminationReason`, `commanderDamageDealt`, `commanderDamageReceived`,
                    `deck_id`, `deckName`, `opponentColors`, `isWinner`, `is_local`,
                    `archetype`, `linked_profile_tag`
                )
                SELECT
                    `id`, `sessionId`, `playerId`, `playerName`, `finalLife`, `finalPoison`,
                    `eliminationReason`, `commanderDamageDealt`, `commanderDamageReceived`,
                    NULL, `deckName`, `opponentColors`, `isWinner`, 0,
                    NULL, NULL
                FROM `player_sessions`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `player_sessions`")
            db.execSQL("ALTER TABLE `player_sessions_new` RENAME TO `player_sessions`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_player_sessions_sessionId` ON `player_sessions` (`sessionId`)"
            )

            // ── survey_card_impacts: new per-seat card-impact table ──────────────
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `survey_card_impacts` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `session_id` INTEGER NOT NULL,
                    `player_session_id` INTEGER NOT NULL,
                    `card_id` TEXT NOT NULL,
                    `impact` TEXT NOT NULL,
                    FOREIGN KEY(`session_id`) REFERENCES `game_sessions`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`player_session_id`) REFERENCES `player_sessions`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`card_id`) REFERENCES `cards`(`scryfall_id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_survey_card_impacts_session_id` ON `survey_card_impacts` (`session_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_survey_card_impacts_player_session_id` ON `survey_card_impacts` (`player_session_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_survey_card_impacts_card_id` ON `survey_card_impacts` (`card_id`)")

            // ── survey_answers: add lifecycle status column ──────────────────────
            if (!columnExists(db, "survey_answers", "status")) {
                db.execSQL(
                    "ALTER TABLE `survey_answers` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'DRAFT'"
                )
            }

            // ── user_card_collection: drop is_alternative_art (variant picker replaced it) ──
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `user_card_collection_new` (
                    `id` TEXT NOT NULL,
                    `user_id` TEXT,
                    `scryfall_id` TEXT NOT NULL,
                    `quantity` INTEGER NOT NULL,
                    `is_foil` INTEGER NOT NULL,
                    `condition` TEXT NOT NULL,
                    `language` TEXT NOT NULL,
                    `is_for_trade` INTEGER NOT NULL,
                    `is_deleted` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`scryfall_id`) REFERENCES `cards`(`scryfall_id`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `user_card_collection_new` (
                    `id`, `user_id`, `scryfall_id`, `quantity`, `is_foil`, `condition`,
                    `language`, `is_for_trade`, `is_deleted`, `updated_at`, `created_at`
                )
                SELECT `id`, `user_id`, `scryfall_id`, `quantity`, `is_foil`, `condition`,
                       `language`, `is_for_trade`, `is_deleted`, `updated_at`, `created_at`
                FROM `user_card_collection`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `user_card_collection`")
            db.execSQL("ALTER TABLE `user_card_collection_new` RENAME TO `user_card_collection`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_card_collection_scryfall_id` ON `user_card_collection` (`scryfall_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_card_collection_user_id` ON `user_card_collection` (`user_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_card_collection_updated_at` ON `user_card_collection` (`updated_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_card_collection_is_deleted` ON `user_card_collection` (`is_deleted`)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_user_card_collection_user_id_scryfall_id_is_foil_condition_language` " +
                    "ON `user_card_collection` (`user_id`, `scryfall_id`, `is_foil`, `condition`, `language`)"
            )

            // ── local_wishlists: drop is_alt_art ─────────────────────────────────
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `local_wishlists_new` (
                    `id` TEXT NOT NULL,
                    `scryfall_id` TEXT NOT NULL,
                    `quantity` INTEGER NOT NULL,
                    `match_any_variant` INTEGER NOT NULL,
                    `is_foil` INTEGER,
                    `condition` TEXT,
                    `language` TEXT,
                    `synced` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `local_wishlists_new` (
                    `id`, `scryfall_id`, `quantity`, `match_any_variant`, `is_foil`,
                    `condition`, `language`, `synced`, `created_at`
                )
                SELECT `id`, `scryfall_id`, `quantity`, `match_any_variant`, `is_foil`,
                       `condition`, `language`, `synced`, `created_at`
                FROM `local_wishlists`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `local_wishlists`")
            db.execSQL("ALTER TABLE `local_wishlists_new` RENAME TO `local_wishlists`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_wishlists_scryfall_id` ON `local_wishlists` (`scryfall_id`)")

            // ── local_open_for_trade: drop is_alt_art ────────────────────────────
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `local_open_for_trade_new` (
                    `id` TEXT NOT NULL,
                    `local_collection_id` TEXT NOT NULL,
                    `scryfall_id` TEXT NOT NULL,
                    `quantity` INTEGER NOT NULL,
                    `is_foil` INTEGER NOT NULL,
                    `condition` TEXT NOT NULL,
                    `language` TEXT NOT NULL,
                    `synced` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `local_open_for_trade_new` (
                    `id`, `local_collection_id`, `scryfall_id`, `quantity`, `is_foil`,
                    `condition`, `language`, `synced`, `created_at`
                )
                SELECT `id`, `local_collection_id`, `scryfall_id`, `quantity`, `is_foil`,
                       `condition`, `language`, `synced`, `created_at`
                FROM `local_open_for_trade`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `local_open_for_trade`")
            db.execSQL("ALTER TABLE `local_open_for_trade_new` RENAME TO `local_open_for_trade`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_open_for_trade_local_collection_id` ON `local_open_for_trade` (`local_collection_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_open_for_trade_scryfall_id` ON `local_open_for_trade` (`scryfall_id`)")
        }
    }

    // -------------------------------------------------------------------------
    // Helper: checks whether a column already exists in a table.
    // Used as a guard so migrations are idempotent — safe to run more than once
    // (e.g. if the app crashes mid-migration and Room retries on next launch).
    // -------------------------------------------------------------------------
    private fun columnExists(
        db: SupportSQLiteDatabase,
        tableName: String,
        columnName: String,
    ): Boolean {
        db.query("PRAGMA table_info($tableName)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            if (nameIndex == -1) return false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == columnName) return true
            }
        }
        return false
    }

    // DAO providers — one per abstract method in MtgDatabase
    @Provides fun provideCardDao(db: MtgDatabase): CardDao = db.cardDao()
    @Provides fun provideUserCardCollectionDao(db: MtgDatabase): UserCardCollectionDao = db.userCardCollectionDao()
    @Provides fun provideDeckDao(db: MtgDatabase): DeckDao = db.deckDao()
    @Provides fun provideStatsDao(db: MtgDatabase): StatsDao = db.statsDao()
    @Provides fun provideManaSymbolDao(db: MtgDatabase): ManaSymbolDao = db.manaSymbolDao()
    @Provides fun provideGameSessionDao(db: MtgDatabase): GameSessionDao = db.gameSessionDao()
    @Provides fun provideSurveyAnswerDao(db: MtgDatabase): SurveyAnswerDao = db.surveyAnswerDao()
    @Provides fun provideSurveyCardImpactDao(db: MtgDatabase): SurveyCardImpactDao = db.surveyCardImpactDao()
    @Provides fun provideTournamentDao(db: MtgDatabase): TournamentDao = db.tournamentDao()
    @Provides fun provideNewsDao(db: MtgDatabase): NewsDao = db.newsDao()
    @Provides fun provideDraftSetDao(db: MtgDatabase): DraftSetDao = db.draftSetDao()
    @Provides fun provideFriendDao(db: MtgDatabase): FriendDao = db.friendDao()
    @Provides fun provideRemoteKeyDao(db: MtgDatabase): RemoteKeyDao = db.remoteKeyDao()
    @Provides fun provideLocalWishlistDao(db: MtgDatabase): LocalWishlistDao = db.localWishlistDao()
    @Provides fun provideLocalOpenForTradeDao(db: MtgDatabase): LocalOpenForTradeDao = db.localOpenForTradeDao()
    @Provides fun provideTradeCollectionSyncDao(db: MtgDatabase): TradeCollectionSyncDao = db.tradeCollectionSyncDao()
    @Provides fun providePlaytestDao(db: MtgDatabase): PlaytestDao = db.playtestDao()
    @Provides fun provideDraftSessionDao(db: MtgDatabase): DraftSessionDao = db.draftSessionDao()
    @Provides fun provideGamificationDao(db: MtgDatabase): GamificationDao = db.gamificationDao()
    @Provides fun provideGamificationStatsDao(db: MtgDatabase): GamificationStatsDao = db.gamificationStatsDao()
}
