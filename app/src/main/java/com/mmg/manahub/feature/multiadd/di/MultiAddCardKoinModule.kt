package com.mmg.manahub.feature.multiadd.di

import com.mmg.manahub.feature.multiadd.presentation.MultiAddCardViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun massiveAddCardKoinModule(): Module = module {
    viewModel {
        MultiAddCardViewModel(
            searchCards = get(),
            buildScryfallQuery = get(),
            cardRepository = get(),
            userCardRepository = get(),
            commitScannedCards = get(),
            addToWishlist = get(),
            analyticsHelper = get(),
            userPreferences = get(),
            context = get()
        )
    }
}