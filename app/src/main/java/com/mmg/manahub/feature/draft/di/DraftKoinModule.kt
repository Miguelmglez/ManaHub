package com.mmg.manahub.feature.draft.di

import com.mmg.manahub.core.domain.engine.BotDrafter
import com.mmg.manahub.feature.draft.presentation.viewmodel.DraftSimViewModel
import com.mmg.manahub.feature.draft.presentation.viewmodel.DraftViewModel
import com.mmg.manahub.feature.draft.presentation.viewmodel.SetDraftDetailViewModel
import kotlinx.coroutines.Dispatchers
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. The Draft feature is the thirteenth "Koin island" and the
 * FOURTH multi-ViewModel island: [DraftViewModel] (set list), [SetDraftDetailViewModel] (guide/tier/videos)
 * and [DraftSimViewModel] (the setup → drafting → result simulator flow) are all resolved by Koin
 * (`koinViewModel()`) while every other feature stays on Hilt.
 *
 * ## Bridge pattern (same as the earlier islands)
 * The Draft data/domain graph (Cloudflare Worker + YouTube Retrofit clients, the draft engine —
 * [com.mmg.manahub.feature.draft.data.engine.ArchetypeAwareBotDrafter] / `DefaultDraftEngine` /
 * `WeightedBoosterGenerator` — repositories and `DraftSimRepositoryImpl`) is still owned by the Hilt
 * `DraftModule`, which is **deliberately KEPT** (NOT converted/deleted): `DraftSimRepositoryImpl`
 * still needs a few of these use cases built by Hilt (see `SharedDomainUseCaseModule`'s KDoc), so the
 * whole Hilt sub-graph must stay intact. `ManaHubApp` is the bridge for the one Draft-only singleton
 * that isn't (yet) natively Koin-built: it `@Inject`s the already-constructed Hilt [BotDrafter] and
 * hands it to this module, which re-exposes it to Koin as `single { }`.
 *
 * All ten Draft use cases (KMP migration batch 2) are now natively Koin-built in
 * `SharedDomainKoinModule` — resolved below via `get()`, not registered here anymore. (Two of them,
 * `GetDraftableSetsUseCase`/`GetSetTierListUseCase`, ALSO have a separate Hilt-built copy in the
 * residual `SharedDomainUseCaseModule` for `DraftSimRepositoryImpl`'s sake — both copies wrap the same
 * shared `DraftRepository` singleton, so there is no behaviour divergence.)
 *
 * The two repositories the VMs reach (transitively, through the use cases) plus [AnalyticsHelper] are
 * SHARED with the Home island, so they are NOT registered here — they live in `coreBridgeKoinModule`
 * (registering the same type in two loaded modules would throw `DefinitionOverrideException`). Draft does
 * not register them directly; the bridged use cases already hold their own Koin-resolved references, and
 * [DraftSimViewModel] resolves `AnalyticsHelper` + `DraftSimRepository` via `get()` from the core bridge.
 *
 * The `@DefaultDispatcher` qualified [kotlinx.coroutines.CoroutineDispatcher] that [DraftSimViewModel]
 * needs is supplied as `Dispatchers.Default` directly — the exact same singleton the Hilt
 * `@DefaultDispatcher` binding returns (the CommunityDecks/Survey precedent for `Dispatchers.IO`).
 *
 * @param botDrafter Hilt-owned [BotDrafter] (archetype-aware, shared, stateless).
 * @return a Koin [Module] exposing the Draft-only bridged singleton and the three Draft VM factories.
 */
fun draftKoinModule(
    botDrafter: BotDrafter,
): Module = module {
    // ── Hilt → Koin bridge: re-expose the Draft-only Hilt-owned singleton to Koin. ──
    // (DraftRepository, DraftSimRepository and AnalyticsHelper are shared with Home → bridged in
    //  coreBridgeKoinModule; the ten Draft use cases are singles in SharedDomainKoinModule. All
    //  resolved below via get().)
    single { botDrafter }

    // ── The Koin island: all three Draft ViewModels are now resolved by Koin, not Hilt. ──
    viewModel {
        DraftViewModel(
            getDraftableSetsUseCase = get(),
        )
    }

    viewModel {
        SetDraftDetailViewModel(
            savedStateHandle = get(),
            getSetGuideUseCase = get(),
            getSetTierListUseCase = get(),
            getSetVideosUseCase = get(),
            getDraftableSetsUseCase = get(),
        )
    }

    viewModel {
        DraftSimViewModel(
            savedStateHandle = get(),
            startDraft = get(),
            makePick = get(),
            autoPick = get(),
            observeDraft = get(),
            completeDraft = get(),
            getDraftableSimSet = get(),
            analytics = get(),
            botDrafter = get(),
            draftSimRepository = get(),
            defaultDispatcher = Dispatchers.Default,
        )
    }
}
