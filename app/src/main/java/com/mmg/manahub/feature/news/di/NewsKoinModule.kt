package com.mmg.manahub.feature.news.di

import com.mmg.manahub.core.data.local.dao.NewsDao
import com.mmg.manahub.core.domain.repository.NewsRepository
import com.mmg.manahub.feature.news.data.NewsRepositoryImpl
import com.mmg.manahub.feature.news.data.parser.RssFeedParser
import com.mmg.manahub.feature.news.data.parser.YouTubeRssFeedParser
import com.mmg.manahub.feature.news.data.remote.NewsFeedService
import com.mmg.manahub.feature.today.presentation.feed.FeedViewModel
import com.mmg.manahub.feature.today.presentation.saved.SavedViewModel
import com.mmg.manahub.feature.today.presentation.sources.AddSourceViewModel
import com.mmg.manahub.feature.today.presentation.sources.SourcesViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for the News data layer and the MTG Today ViewModels.
 *
 * - The Room-owned [NewsDao] is bridged in from Hilt (`DatabaseModule`); Room stays androidMain.
 * - News/MTG Today use cases come from `SharedDomainKoinModule`; `UserPreferencesDataStore`,
 *   `OkHttpClient` and `CrashReporter` from `coreBridgeKoinModule`.
 * - `UserPreferencesDataStore` is consumed by [NewsRepositoryImpl] for the one-time legacy-filter
 *   → follow migration.
 */
fun newsKoinModule(
    newsDao: NewsDao,
): Module = module {
    single { newsDao }

    single { NewsFeedService(client = get()) }
    single { RssFeedParser() }
    single { YouTubeRssFeedParser() }
    single<NewsRepository> {
        NewsRepositoryImpl(
            newsDao = get(),
            feedService = get(),
            rssParser = get(),
            ytParser = get(),
            userPrefsDataStore = get(),
        )
    }

    viewModel {
        FeedViewModel(
            getNewsFeed = get(),
            refreshNewsFeed = get(),
            manageSources = get(),
            observeSavedItems = get(),
            toggleSavedItem = get(),
            crashReporter = get(),
            savedStateHandle = get(),
        )
    }
    viewModel {
        SavedViewModel(
            observeSavedItems = get(),
            manageSources = get(),
            toggleSavedItem = get(),
            crashReporter = get(),
            savedStateHandle = get(),
        )
    }
    viewModel { SourcesViewModel(manageSources = get(), crashReporter = get()) }
    viewModel { AddSourceViewModel(resolveSource = get(), followSource = get(), crashReporter = get()) }
}
