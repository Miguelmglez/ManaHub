package com.mmg.manahub.feature.massiveadd.di

import com.mmg.manahub.feature.massiveadd.presentation.MassiveAddCardViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun massiveAddCardKoinModule(): Module = module {
    viewModel{
        MassiveAddCardViewModel(
            searchCards = get(),
            buildScryfallQuery = get(),
            addToCollection = get(),
            analyticsHelper = get(),
            addToWishlistUseCase =get(),
            wishlistRepo = get(),
            userCardRepository = get()
        )
    }
}