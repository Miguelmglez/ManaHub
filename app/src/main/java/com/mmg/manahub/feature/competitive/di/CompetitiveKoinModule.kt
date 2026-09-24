package com.mmg.manahub.feature.competitive.di

import com.mmg.manahub.feature.competitive.presentation.CompetitiveViewModel
import com.mmg.manahub.feature.news.domain.usecase.GetProTourContentUseCase
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/** Koin module for the Competitive screen: the Pro Tour news filter + [CompetitiveViewModel]. */
fun competitiveKoinModule(): Module = module {
    single { GetProTourContentUseCase(repository = get()) }

    viewModel {
        CompetitiveViewModel(
            getProTourContent = get(),
            userPrefsDataStore = get(),
        )
    }
}
