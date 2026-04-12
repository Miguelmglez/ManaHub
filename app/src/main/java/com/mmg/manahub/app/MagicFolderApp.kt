package com.mmg.manahub.app

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.mmg.manahub.core.domain.usecase.symbols.SyncManaSymbolsUseCase
import com.mmg.manahub.core.tagging.TagDictionaryRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MagicFolderApp : Application() {

    @Inject
    lateinit var syncManaSymbols: SyncManaSymbolsUseCase

    @Inject
    lateinit var tagDictionaryRepo: TagDictionaryRepository

    override fun onCreate() {
        super.onCreate()

        // Register the SVG decoder so Coil can render Scryfall SVG symbol images.
        Coil.setImageLoader(ImageLoader.Builder(this).components { add(SvgDecoder.Factory()) }
            .build())

        CoroutineScope(Dispatchers.IO).launch {
            runCatching { syncManaSymbols() }
            // Apply any user overrides before card analyzers run for the first time.
            runCatching { tagDictionaryRepo.loadAndApply() }
        }
    }
}
