package com.mmg.manahub.core.data.local

import android.content.Context
import androidx.room.Room
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
            // All versions 1–24 are incompatible with the v25 schema (table renames,
            // PK type changes from Long to String UUID, new entities). Destructive migration
            // wipes local data and lets it rebuild from the server sync on next login.
            // Do NOT remove this without a proper migration path for production builds.
            .fallbackToDestructiveMigrationFrom(
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                21, 22, 23, 24
            )
            .build()

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
