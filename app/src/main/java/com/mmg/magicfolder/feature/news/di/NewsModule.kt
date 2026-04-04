package com.mmg.magicfolder.feature.news.di

import com.mmg.magicfolder.feature.news.data.NewsRepositoryImpl
import com.mmg.magicfolder.feature.news.domain.repository.NewsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NewsModule {

    @Binds
    @Singleton
    abstract fun bindNewsRepository(impl: NewsRepositoryImpl): NewsRepository
}
