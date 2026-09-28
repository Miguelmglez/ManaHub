package com.mmg.manahub.feature.tagdictionary.di

import com.mmg.manahub.core.tagging.TagDictionaryRepository
import com.mmg.manahub.core.tagging.CardMechanicCatalogRepository
import com.mmg.manahub.core.tagging.CardMechanicCatalogRehydrator
import com.mmg.manahub.core.data.remote.CardMechanicCatalogRemoteDataSource
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.feature.tagdictionary.presentation.TagDictionaryViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. The Tag Dictionary feature is the fifth "Koin island"
 * (after Settings, Stats, Profile and Home): [TagDictionaryViewModel] is resolved by Koin
 * (`koinViewModel()`) while every other feature stays on Hilt. A small, self-contained leaf — a single
 * ViewModel over the tagging repo with no platform-specific dependencies.
 *
 * ## Bridge pattern (same as the earlier islands)
 * [TagDictionaryViewModel] depends on two singletons still owned by the Hilt object graph. Rather than
 * re-providing them in Koin — which would risk duplicate construction / divergent state — `ManaHubApp`
 * is the bridge: it `@Inject`s the already-constructed Hilt instances and passes the island-only one
 * ([TagDictionaryRepository]) into [tagDictionaryKoinModule], which re-exposes it to Koin as `single { }`.
 *
 * The second dependency, `UserPreferencesDataStore`, is SHARED with other islands (Settings + Profile +
 * Home), so it is NOT registered here — it is bridged exactly once in `coreBridgeKoinModule` (registering
 * the same type in two loaded modules would throw `DefinitionOverrideException`) and resolved below via
 * `get()`.
 *
 * As features migrate, the `single { hiltInstance }` here is replaced by a real Koin provider and the
 * matching Hilt `@Provides`/`@Binds` is deleted — so the bridge shrinks to nothing without ever leaving
 * the app uncompilable between commits.
 *
 * @param tagDictionaryRepository the Hilt-owned [TagDictionaryRepository] singleton (Tag Dictionary only).
 * @return a Koin [Module] that provides the bridged singleton and the [TagDictionaryViewModel] factory.
 */
fun tagDictionaryKoinModule(
    tagDictionaryRepository: TagDictionaryRepository,
): Module = module {
    // ── Hilt → Koin bridge: re-expose the Tag-Dictionary-only Hilt-owned singleton to Koin. ──
    // (UserPreferencesDataStore is shared → bridged in coreBridgeKoinModule, not here, and resolved
    //  below via get().)
    single { tagDictionaryRepository }
    single { CardMechanicCatalogRepository(get(), CardMechanicCatalogRemoteDataSource(get())) }
    single {
        val authRepository = get<AuthRepository>()
        CardMechanicCatalogRehydrator(get(), get()) {
            (authRepository.sessionState.value as? SessionState.Authenticated)?.user?.id
        }
    }

    // ── The Koin island: TagDictionaryViewModel is now resolved by Koin, not Hilt. ──
    viewModel {
        TagDictionaryViewModel(
            dictionaryRepo = get(),
            prefs = get(),
            mechanicCatalog = get(),
        )
    }
}
