package com.mmg.magicfolder.code.core.data.repository


import com.mmg.magicfolder.code.core.domain.repository.CardRepository
import com.mmg.magicfolder.code.core.domain.repository.DeckRepository
import com.mmg.magicfolder.code.core.domain.repository.StatsRepository
import com.mmg.magicfolder.code.core.domain.repository.UserCardRepository
import com.mmg.magicfolder.core.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindCardRepository(impl: CardRepositoryImpl): CardRepository

    @Binds @Singleton
    abstract fun bindUserCardRepository(impl: UserCardRepositoryImpl): UserCardRepository

    @Binds @Singleton
    abstract fun bindDeckRepository(impl: DeckRepositoryImpl): DeckRepository

    @Binds @Singleton
    abstract fun bindStatsRepository(impl: StatsRepositoryImpl): StatsRepository
}