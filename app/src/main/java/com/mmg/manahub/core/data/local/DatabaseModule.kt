package com.mmg.manahub.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mmg.manahub.core.data.local.dao.*
import com.mmg.manahub.core.data.local.paging.RemoteKeyDao
import com.mmg.manahub.feature.draft.data.local.DraftSetDao
import com.mmg.manahub.feature.friends.data.local.dao.FriendDao
import com.mmg.manahub.feature.news.data.local.NewsDao
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
    fun provideMtgDatabase(@ApplicationContext context: Context): MtgDatabase =
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
            // All migrations from v25 onward are explicit and data-safe.
            // fallbackToDestructiveMigration() is NOT called — any missing migration
            // will throw an IllegalStateException at startup rather than silently
            // deleting user data.
            .addMigrations(
                MIGRATION_25_26,
                MIGRATION_26_27,
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
}
