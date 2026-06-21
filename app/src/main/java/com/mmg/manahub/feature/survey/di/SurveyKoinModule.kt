package com.mmg.manahub.feature.survey.di

import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.dao.SurveyCardImpactDao
import com.mmg.manahub.feature.survey.domain.usecase.CompleteSurveyUseCase
import com.mmg.manahub.feature.survey.presentation.SurveyViewModel
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. The post-game Survey feature is a "Koin island":
 * its single [SurveyViewModel] is resolved by Koin (`koinViewModel()`) while every other un-migrated
 * feature stays on Hilt.
 *
 * ## Survey-only bridged singletons (passed in, registered here)
 * Three of the ViewModel's eight Hilt-owned dependencies are consumed ONLY by this island, so they are
 * bridged in [surveyKoinModule] itself (re-exposed as `single { }` from the instances `ManaHubApp`
 * `@Inject`s and hands in):
 * - [SurveyCardImpactDao] — per-seat card-impact rows (Room/`DatabaseModule`).
 * - [CardDao] — resolves deck-card scryfall ids to domain cards (Room/`DatabaseModule`).
 * - [CompleteSurveyUseCase] — emits the idempotent `survey:{sessionId}` progression event on submit.
 *
 * ## Shared singletons (resolved via `get()`, NOT re-registered)
 * The other dependencies are already provided by loaded modules — a `single<T>` is resolvable via
 * `get()` from ANY loaded module, so re-registering would throw `DefinitionOverrideException`:
 * - `SurveyAnswerDao` — already a `single` in `profileKoinModule`.
 * - `GameSessionDao` — already a `single` in `statsKoinModule`.
 * - `DeckRepository`, `UserPreferencesRepository` — already in `coreBridgeKoinModule`.
 *
 * The application `Context` (was `@ApplicationContext`) is supplied by Koin's `androidContext()`, the IO
 * dispatcher (was `@IoDispatcher`) as `Dispatchers.IO` directly — the SAME singleton the Hilt binding
 * returned — and the nav-arg-carrying `SavedStateHandle` (`sessionId` / `mode`) via `get()`, so the
 * runtime behaviour is byte-for-byte identical to the previous Hilt resolution.
 *
 * @param surveyCardImpactDao the Hilt/Room-owned [SurveyCardImpactDao] singleton (this island only).
 * @param cardDao the Hilt/Room-owned [CardDao] singleton (this island only).
 * @param completeSurvey the Hilt-owned [CompleteSurveyUseCase] singleton (this island only).
 * @return a Koin [Module] providing the Survey-only bridged singletons and the [SurveyViewModel] factory.
 */
fun surveyKoinModule(
    surveyCardImpactDao: SurveyCardImpactDao,
    cardDao: CardDao,
    completeSurvey: CompleteSurveyUseCase,
): Module = module {
    // ── Hilt → Koin bridge: the Survey-only singletons. ──
    single { surveyCardImpactDao }
    single { cardDao }
    single { completeSurvey }

    // ── The Koin island: SurveyViewModel is now resolved by Koin, not Hilt. ──
    viewModel {
        SurveyViewModel(
            surveyAnswerDao = get(),
            surveyCardImpactDao = get(),
            gameSessionDao = get(),
            deckRepository = get(),
            cardDao = get(),
            userPreferencesRepository = get(),
            completeSurvey = get(),
            context = androidContext(),
            ioDispatcher = Dispatchers.IO,
            savedStateHandle = get(),
        )
    }
}
