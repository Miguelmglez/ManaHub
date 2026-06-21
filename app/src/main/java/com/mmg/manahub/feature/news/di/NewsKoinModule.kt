package com.mmg.manahub.feature.news.di

import com.mmg.manahub.feature.news.presentation.NewsSourcesSettingsViewModel
import com.mmg.manahub.feature.news.presentation.NewsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. The News feature is a multi-ViewModel "Koin island":
 * BOTH [NewsViewModel] (feed + filters) and [NewsSourcesSettingsViewModel] (source management) are
 * resolved by Koin (`koinViewModel()`) while every other un-migrated feature stays on Hilt.
 *
 * ## Bridge / shared singletons (all resolved via `get()`)
 * This island bridges NOTHING of its own — every dependency of both ViewModels is already a `single`
 * in a loaded module (a `single<T>` is resolvable via `get()` from ANY loaded module), so
 * [newsKoinModule] takes no parameters and `ManaHubApp` needs no new `@Inject` field:
 * - `GetNewsFeedUseCase`, `RefreshNewsFeedUseCase`, `ManageSourcesUseCase` — already registered as
 *   `single` in `homeKoinModule` (the Home news widget shares them).
 * - `UserPreferencesDataStore` — already in `coreBridgeKoinModule`.
 *
 * ## Hilt `NewsModule` is KEPT (not converted/deleted)
 * The feature-private Hilt `NewsModule` (`@Binds NewsRepository`) MUST stay. Although `NewsRepository`
 * is consumed by no screen outside this feature, the three news use cases above are still
 * Hilt-constructed (`@Inject constructor`) for the HOME island bridge (`ManaHubApp` `@Inject`s them
 * from the Hilt graph and hands them to `homeKoinModule`). Those use cases depend on `NewsRepository`,
 * so deleting its only Hilt binding would break the Hilt graph — the same reason Friends KEPT its
 * `FriendModule`. When Home is later migrated off Hilt, `NewsModule` can be converted and deleted.
 *
 * @return a Koin [Module] providing both News ViewModel factories.
 */
fun newsKoinModule(): Module = module {
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
