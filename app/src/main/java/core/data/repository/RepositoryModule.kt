package core.data.repository


import core.domain.repository.CardRepository
import core.domain.repository.DeckRepository
import core.domain.repository.StatsRepository
import core.domain.repository.UserCardRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindCardRepository(impl: core.data.repository.CardRepositoryImpl): CardRepository

    @Binds @Singleton
    abstract fun bindUserCardRepository(impl: core.data.repository.UserCardRepositoryImpl): UserCardRepository

    @Binds @Singleton
    abstract fun bindDeckRepository(impl: core.data.repository.DeckRepositoryImpl): DeckRepository

    @Binds @Singleton
    abstract fun bindStatsRepository(impl: core.data.repository.StatsRepositoryImpl): StatsRepository
}