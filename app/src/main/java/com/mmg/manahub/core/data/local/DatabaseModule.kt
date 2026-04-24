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
            .fallbackToDestructiveMigrationFrom(
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                21, 22, 23, 24
            )
            .addMigrations(MIGRATION_25_26, MIGRATION_26_27)
            .build()

    private val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!columnExists(db, "cards", "related_uris")) {
                db.execSQL("ALTER TABLE cards ADD COLUMN related_uris  TEXT    NOT NULL DEFAULT '{}'")
            }
            if (!columnExists(db, "cards", "purchase_uris")) {
                db.execSQL("ALTER TABLE cards ADD COLUMN purchase_uris TEXT    NOT NULL DEFAULT '{}'")
            }
            if (!columnExists(db, "cards", "game_changer")) {
                db.execSQL("ALTER TABLE cards ADD COLUMN game_changer  INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

    private val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!columnExists(db, "decks", "commander_card_id")) {
                db.execSQL("ALTER TABLE decks ADD COLUMN commander_card_id TEXT")
            }
        }
    }

    private fun columnExists(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
        db.query("PRAGMA table_info($tableName)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            if (nameIndex == -1) return false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == columnName) return true
            }
        }
        return false
    }

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
