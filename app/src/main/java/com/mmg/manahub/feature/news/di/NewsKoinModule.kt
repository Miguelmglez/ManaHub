package com.mmg.manahub.feature.news.di

import com.mmg.manahub.core.data.local.dao.NewsDao
import com.mmg.manahub.core.domain.repository.NewsRepository
import com.mmg.manahub.feature.news.data.NewsRepositoryImpl
import com.mmg.manahub.feature.news.data.parser.RssFeedParser
import com.mmg.manahub.feature.news.data.parser.YouTubeRssFeedParser
import com.mmg.manahub.feature.news.data.remote.NewsFeedService
import com.mmg.manahub.feature.news.presentation.NewsSourcesSettingsViewModel
import com.mmg.manahub.feature.news.presentation.NewsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Hilt→Koin cutover batch 3. The News feature is a multi-ViewModel "Koin island":
 * BOTH [NewsViewModel] (feed + filters) and [NewsSourcesSettingsViewModel] (source management) are
 * resolved by Koin (`koinViewModel()`) while every other un-migrated feature stays on Hilt.
 *
 * ## `NewsRepository` is now natively Koin-built (the feature-private Hilt `NewsModule` was DELETED)
 * `NewsRepositoryImpl` and its three collaborators ([NewsFeedService], [RssFeedParser],
 * [YouTubeRssFeedParser]) had their `@Inject`/`@Singleton` annotations stripped — this batch confirmed
 * `NewsRepository` had NO other Hilt-only consumer (only `SharedDomainKoinModule`'s three News use
 * cases, which already resolved it via `get()`, never the forward-bridged instance directly).
 *
 * ## Bridge / shared singletons (all resolved via `get()`)
 * - `GetNewsFeedUseCase`, `RefreshNewsFeedUseCase`, `ManageSourcesUseCase` — natively Koin-built in
 *   `SharedDomainKoinModule` (batch 2).
 * - `UserPreferencesDataStore` — already in `coreBridgeKoinModule`.
 * - `OkHttpClient` — the app-wide client, promoted into `coreBridgeKoinModule` this batch (was only a
 *   Hilt `ManaHubApp` field used for the Coil image loader; now also feeds [NewsFeedService]).
 *
 * ## Bridged singleton (Room DAO stays Hilt/`DatabaseModule`-owned)
 * @param newsDao the Hilt/Room-owned [NewsDao] singleton (this island only).
 * @return a Koin [Module] providing the News data layer + both News ViewModel factories.
 */
fun newsKoinModule(
    newsDao: NewsDao,
): Module = module {
    // ── Hilt → Koin bridge: the Room-owned DAO (Room stays androidMain / Hilt/DatabaseModule). ──
    single { newsDao }

    // ── News data layer (natively Koin-built; the feature-private Hilt NewsModule was DELETED). ──
    single { NewsFeedService(client = get()) }
    single { RssFeedParser() }
    single { YouTubeRssFeedParser() }
    single<NewsRepository> {
        NewsRepositoryImpl(
            newsDao = get(),
            feedService = get(),
            rssParser = get(),
            ytParser = get(),
        )
    }

    viewModel {
        NewsViewModel(
            getNewsFeed = get(),
            refreshNewsFeed = get(),
            manageSources = get(),
            userPrefsDataStore = get(),
        )
    }
    viewModel {
        NewsSourcesSettingsViewModel(
            manageSources = get(),
        )
    }
}
