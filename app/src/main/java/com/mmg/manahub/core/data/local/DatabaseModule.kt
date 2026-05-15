package com.mmg.manahub.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.runBlocking
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.local.dao.GameSessionDao
import com.mmg.manahub.core.data.local.dao.ManaSymbolDao
import com.mmg.manahub.core.data.local.dao.StatsDao
import com.mmg.manahub.core.data.local.dao.SurveyAnswerDao
import com.mmg.manahub.core.data.local.dao.TournamentDao
import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.paging.RemoteKeyDao
import com.mmg.manahub.feature.draft.data.local.DraftSetDao
import com.mmg.manahub.feature.friends.data.local.dao.FriendDao
import com.mmg.manahub.feature.news.data.local.NewsDao
import com.mmg.manahub.feature.trades.data.local.dao.LocalOpenForTradeDao
import com.mmg.manahub.feature.trades.data.local.dao.LocalWishlistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
    @Provides fun provideTournamentDao(db: MtgDatabase): TournamentDao = db.tournamentDao()
    @Provides fun provideNewsDao(db: MtgDatabase): NewsDao = db.newsDao()
    @Provides fun provideDraftSetDao(db: MtgDatabase): DraftSetDao = db.draftSetDao()
    @Provides fun provideFriendDao(db: MtgDatabase): FriendDao = db.friendDao()
    @Provides fun provideRemoteKeyDao(db: MtgDatabase): RemoteKeyDao = db.remoteKeyDao()
    @Provides fun provideLocalWishlistDao(db: MtgDatabase): LocalWishlistDao = db.localWishlistDao()
    @Provides fun provideLocalOpenForTradeDao(db: MtgDatabase): LocalOpenForTradeDao = db.localOpenForTradeDao()
}
