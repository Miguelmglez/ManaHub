package com.mmg.magicfolder.core.data.local

import android.content.Context
import androidx.room.Room
import com.mmg.magicfolder.core.data.local.dao.*
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
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideCardDao(db: MtgDatabase):     CardDao     = db.cardDao()
    @Provides fun provideUserCardDao(db: MtgDatabase): UserCardDao = db.userCardDao()
    @Provides fun provideDeckDao(db: MtgDatabase):     DeckDao     = db.deckDao()
    @Provides fun provideStatsDao(db: MtgDatabase):    StatsDao    = db.statsDao()
}
