package com.mmg.manahub.web.di

import com.mmg.manahub.core.common.KeyValueStore
import com.mmg.manahub.core.common.LocalStorageKeyValueStore
import com.mmg.manahub.web.theme.ThemeShowcaseViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Root Koin module for `:webApp`. Registers only what W1 needs — the [KeyValueStore] wasmJs
 * actual and the showcase screen's ViewModel. Supabase/Ktor wiring is web roadmap W2's job; do not
 * add it here ahead of that slice.
 */
val webAppKoinModule = module {
    single<KeyValueStore> { LocalStorageKeyValueStore() }

    viewModel { ThemeShowcaseViewModel(get()) }
}
