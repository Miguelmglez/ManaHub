package com.mmg.manahub.core.di

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.cache.CardStrategyTagsCache
import com.mmg.manahub.core.data.cache.CardStrategyTagsCacheImpl
import com.mmg.manahub.core.data.local.dao.CardStrategyTagsCacheDao
import com.mmg.manahub.core.data.remote.CardStrategyTagsRemoteDataSource
import com.mmg.manahub.core.data.remote.CardStrategyTagsRemoteDataSourceContract
import com.mmg.manahub.core.data.repository.CardStrategyTagsRepositoryImpl
import com.mmg.manahub.core.domain.repository.CardStrategyTagsRepository
import com.mmg.manahub.core.domain.usecase.card.HydrateCollectionStrategyTagsUseCase
import com.mmg.manahub.core.domain.usecase.card.RefreshCardStrategyTagsUseCase
import com.mmg.manahub.core.domain.usecase.card.ResolveCardStrategyTagsUseCase
import io.github.jan.supabase.SupabaseClient
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin module for the precomputed card-strategy-tags read path (Deck Engine Unification plan, D8,
 * §5 Phase 5c) — mirrors `commanderSpellbookKoinModule`'s structure (same Room-owned-DAO bridge
 * shape). This module ONLY serves Koin-native consumers (currently: [CardDetailViewModel] via
 * [RefreshCardStrategyTagsUseCase]). `:app`'s `CardRepositoryImpl` — the "card add" hot path — is
 * still eager-Hilt and CANNOT resolve through `GlobalContext.get()` (see
 * `SharedDomainUseCaseModule`'s KDoc for why); it gets its OWN independently-constructed
 * `CardStrategyTagsRepositoryImpl` instance from a residual `@Provides` there, sharing the SAME
 * underlying [CardStrategyTagsCacheDao] singleton (Room-scoped `@Singleton` via `DatabaseModule`)
 * so both graphs' caches stay coherent even though the repository objects themselves are distinct
 * — the exact same pattern already established for [com.mmg.manahub.core.domain.usecase.card
 * .ComputeCardTagsUseCase].
 *
 * @param cacheDao the Hilt/Room-owned [CardStrategyTagsCacheDao] singleton (this module only).
 * @param resolveCardStrategyTags the Hilt-owned [ResolveCardStrategyTagsUseCase] singleton, bridged
 *   in (rather than rebuilt Koin-side) so the bulk hydration path shares the exact instance — and
 *   therefore the exact `card_strategy_tags` repository/cache — the "card add" path already uses.
 */
fun cardStrategyTagsKoinModule(
    cacheDao: CardStrategyTagsCacheDao,
    resolveCardStrategyTags: ResolveCardStrategyTagsUseCase,
): Module = module {

    single { resolveCardStrategyTags }

    // ── Hilt → Koin bridge: the Room-owned cache DAO, used only by this module. ──
    single { cacheDao }

    single<CardStrategyTagsCache> { CardStrategyTagsCacheImpl(cacheDao = get()) }

    single<CardStrategyTagsRemoteDataSourceContract> {
        CardStrategyTagsRemoteDataSource(
            supabaseClient = get<SupabaseClient>(),
            dispatcherProvider = DispatcherProvider(),
        )
    }

    single<CardStrategyTagsRepository> {
        CardStrategyTagsRepositoryImpl(
            remote = get(),
            cache = get(),
            crashReporter = get(),
            dispatcherProvider = DispatcherProvider(),
            now = { System.currentTimeMillis() },
        )
    }

    single { RefreshCardStrategyTagsUseCase(cardRepository = get(), cardStrategyTagsRepository = get()) }

    // Bulk hydration (2026-09-07) — consumed by CardTagHydrationWorker. CardDao is bridged by
    // surveyKoinModule; UserPreferencesDataStore, CrashReporter and named("io") all come from
    // coreBridgeKoinModule.
    single {
        HydrateCollectionStrategyTagsUseCase(
            cardDao = get(),
            cardStrategyTagsRepository = get(),
            resolveCardStrategyTags = get(),
            userPreferences = get(),
            crashReporter = get(),
            ioDispatcher = get(named("io")),
        )
    }
}
