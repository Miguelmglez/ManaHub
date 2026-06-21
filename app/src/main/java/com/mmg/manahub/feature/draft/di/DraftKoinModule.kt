package com.mmg.manahub.feature.draft.di

import com.mmg.manahub.feature.draft.domain.engine.BotDrafter
import com.mmg.manahub.feature.draft.domain.usecase.AutoPickUseCase
import com.mmg.manahub.feature.draft.domain.usecase.CompleteDraftUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetDraftableSetsUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetDraftableSimSetUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetGuideUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetTierListUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetVideosUseCase
import com.mmg.manahub.feature.draft.domain.usecase.MakePickUseCase
import com.mmg.manahub.feature.draft.domain.usecase.ObserveDraftUseCase
import com.mmg.manahub.feature.draft.domain.usecase.StartDraftUseCase
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
 * `WeightedBoosterGenerator` — repositories and use cases) is still owned by the Hilt `DraftModule`,
 * which is **deliberately KEPT** (NOT converted/deleted): the Home Koin island bridges
 * `DraftRepository`/`DraftSimRepository` from the Hilt graph, so the whole Hilt sub-graph must stay
 * intact. `ManaHubApp` is the bridge: it `@Inject`s the already-constructed Hilt instances (the ten use
 * cases + the [BotDrafter]) and hands them to this module, which re-exposes them to Koin as `single { }`.
 *
 * The two repositories the VMs reach (transitively, through the use cases) plus [AnalyticsHelper] are
 * SHARED with the Home island, so they are NOT registered here — they live in `coreBridgeKoinModule`
 * (registering the same type in two loaded modules would throw `DefinitionOverrideException`). Draft does
 * not register them directly; the bridged use cases already hold their own Hilt-injected references, and
 * [DraftSimViewModel] resolves `AnalyticsHelper` + `DraftSimRepository` via `get()` from the core bridge.
 *
 * The `@DefaultDispatcher` qualified [kotlinx.coroutines.CoroutineDispatcher] that [DraftSimViewModel]
 * needs is supplied as `Dispatchers.Default` directly — the exact same singleton the Hilt
 * `@DefaultDispatcher` binding returns (the CommunityDecks/Survey precedent for `Dispatchers.IO`).
 *
 * @param startDraft Hilt-owned [StartDraftUseCase].
 * @param makePick Hilt-owned [MakePickUseCase].
 * @param autoPick Hilt-owned [AutoPickUseCase].
 * @param observeDraft Hilt-owned [ObserveDraftUseCase].
 * @param completeDraft Hilt-owned [CompleteDraftUseCase].
 * @param getDraftableSimSet Hilt-owned [GetDraftableSimSetUseCase].
 * @param getDraftableSets Hilt-owned [GetDraftableSetsUseCase].
 * @param getSetGuide Hilt-owned [GetSetGuideUseCase].
 * @param getSetTierList Hilt-owned [GetSetTierListUseCase].
 * @param getSetVideos Hilt-owned [GetSetVideosUseCase].
 * @param botDrafter Hilt-owned [BotDrafter] (archetype-aware, shared, stateless).
 * @return a Koin [Module] exposing the Draft-only bridged singletons and the three Draft VM factories.
 */
fun draftKoinModule(
    startDraft: StartDraftUseCase,
    makePick: MakePickUseCase,
    autoPick: AutoPickUseCase,
    observeDraft: ObserveDraftUseCase,
    completeDraft: CompleteDraftUseCase,
    getDraftableSimSet: GetDraftableSimSetUseCase,
    getDraftableSets: GetDraftableSetsUseCase,
    getSetGuide: GetSetGuideUseCase,
    getSetTierList: GetSetTierListUseCase,
    getSetVideos: GetSetVideosUseCase,
    botDrafter: BotDrafter,
): Module = module {
    // ── Hilt → Koin bridge: re-expose the Draft-only Hilt-owned singletons to Koin. ──
    // (DraftRepository, DraftSimRepository and AnalyticsHelper are shared with Home → bridged in
    //  coreBridgeKoinModule, not here, and resolved below via get().)
    single { startDraft }
    single { makePick }
    single { autoPick }
    single { observeDraft }
    single { completeDraft }
    single { getDraftableSimSet }
    single { getDraftableSets }
    single { getSetGuide }
    single { getSetTierList }
    single { getSetVideos }
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
