package com.mmg.magicfolder.core.data.local

import android.content.Context
import androidx.room.Room
import com.mmg.magicfolder.core.data.local.dao.*
import com.mmg.magicfolder.feature.news.data.local.NewsDao
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_11_12)
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
}
