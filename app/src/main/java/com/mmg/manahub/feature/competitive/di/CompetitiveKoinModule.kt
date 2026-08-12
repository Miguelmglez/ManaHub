package com.mmg.manahub.feature.competitive.di
import com.mmg.manahub.feature.competitive.presentation.CompetitiveViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

fun competitiveKoinModule (): Module = module {
 viewModel{
        CompetitiveViewModel()
    }
}