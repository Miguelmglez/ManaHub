package com.mmg.manahub.core.data.local

import android.content.Context
import androidx.room.Room
import com.mmg.manahub.core.data.local.dao.*
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
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                // Gap migrations — no schema change, but required for a continuous path
                // so users on intermediate versions don't lose data via destructive migration.
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18,
                MIGRATION_18_19,
                MIGRATION_19_20,
                MIGRATION_20_21,
                MIGRATION_21_22,
                MIGRATION_22_23,
                MIGRATION_23_24,
                MIGRATION_24_25,
            )
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideCardDao(db: MtgDatabase):         CardDao         = db.cardDao()
    @Provides fun provideUserCardDao(db: MtgDatabase):     UserCardDao     = db.userCardDao()
    @Provides fun provideDeckDao(db: MtgDatabase):         DeckDao         = db.deckDao()
    @Provides fun provideStatsDao(db: MtgDatabase):        StatsDao        = db.statsDao()
    @Provides fun provideManaSymbolDao(db: MtgDatabase):   ManaSymbolDao   = db.manaSymbolDao()
    @Provides fun provideGameSessionDao(db: MtgDatabase):  GameSessionDao  = db.gameSessionDao()
    @Provides fun provideSurveyAnswerDao(db: MtgDatabase): SurveyAnswerDao = db.surveyAnswerDao()
    @Provides fun provideTournamentDao(db: MtgDatabase):   TournamentDao   = db.tournamentDao()
    @Provides fun provideNewsDao(db: MtgDatabase):         NewsDao         = db.newsDao()
    @Provides fun provideDraftSetDao(db: MtgDatabase):   DraftSetDao     = db.draftSetDao()
    @Provides fun provideFriendDao(db: MtgDatabase):     FriendDao       = db.friendDao()
}
