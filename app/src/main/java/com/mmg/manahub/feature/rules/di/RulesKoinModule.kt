package com.mmg.manahub.feature.rules.di

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.rules.RulesRepositoryImpl
import com.mmg.manahub.core.domain.rules.*
import com.mmg.manahub.feature.rules.data.*
import com.mmg.manahub.feature.rules.presentation.RulesViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val rulesAndroidModule = module {
    single<RulesSnapshotStore> { AndroidRulesSnapshotStore(androidContext()) }
    single<RulesOfficialSource> { AndroidRulesOfficialSource(get()) }
    single<RulesRepository> { RulesRepositoryImpl(get(), get(), DispatcherProvider(), get()) }
    viewModel { RulesViewModel(get(), get(), get(), get(), get()) }
}
