package com.mmg.manahub.feature.addcard.di

import androidx.lifecycle.SavedStateHandle
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.feature.addcard.presentation.AddCardLaunchArgs
import com.mmg.manahub.feature.addcard.presentation.AddCardViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module of the AddCard feature. Every dependency is shared and resolved via `get()`
 * ([UserPreferencesRepository] and the repositories are bridged once in `coreBridgeKoinModule`).
 *
 * @return a Koin [Module] that provides the [AddCardViewModel] factory.
 */
fun addCardKoinModule(): Module = module {
    viewModel {
        // AddCard is a real nav destination: its query args arrive through the SavedStateHandle.
        val savedStateHandle: SavedStateHandle = get()
        AddCardViewModel(
            searchCards = get(),
            userPreferences = get(),
            buildScryfallQuery = get(),
            getSpotlightFeed = get(),
            queueRepository = get(),
            queueActions = get(),
            userCardRepository = get(),
            cardRepository = get(),
            deckRepository = get(),
            communityDecksRepository = get(),
            appScope = get(),
            launchArgs = AddCardLaunchArgs.from(
                multi = savedStateHandle.get<Boolean>(AddCardLaunchArgs.ARG_MULTI) ?: false,
                source = savedStateHandle.get<String>(AddCardLaunchArgs.ARG_SOURCE),
                sourceId = savedStateHandle.get<String>(AddCardLaunchArgs.ARG_SOURCE_ID),
            ),
        )
    }
}
