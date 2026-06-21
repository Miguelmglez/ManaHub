package com.mmg.manahub.feature.communitydecks.di

import android.content.Context
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.data.local.dao.CommunityDeckCacheDao
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.feature.communitydecks.data.CommunityDecksRepositoryImpl
import com.mmg.manahub.feature.communitydecks.data.remote.ArchidektApi
import com.mmg.manahub.feature.communitydecks.data.remote.ArchidektRequestQueue
import com.mmg.manahub.feature.communitydecks.domain.repository.CommunityDecksRepository
import com.mmg.manahub.feature.communitydecks.domain.usecase.GetCommunityDeckUseCase
import com.mmg.manahub.feature.communitydecks.domain.usecase.ImportCommunityDeckUseCase
import com.mmg.manahub.feature.communitydecks.domain.usecase.SearchCommunityDecksUseCase
import com.mmg.manahub.feature.communitydecks.presentation.CommunityDeckDetailViewModel
import com.mmg.manahub.feature.communitydecks.presentation.CommunityDecksSearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. The Community Decks (Archidekt) feature is the seventh
 * "Koin island": its TWO ViewModels — [CommunityDecksSearchViewModel] (search / browse, both the
 * landing and the "decks containing a card" deep-link routes) and [CommunityDeckDetailViewModel]
 * (detail + import) — are resolved by Koin (`koinViewModel()`) while every other un-migrated feature
 * stays on Hilt. This continues the incremental, per-feature cutover proven by Spike D.
 *
 * ## Self-owned providers (the Hilt module replacement)
 * Unlike the earlier islands, the whole Community Decks DATA layer was Hilt-owned by a feature-private
 * `CommunityDecksModule` (Archidekt Retrofit/[ArchidektApi] + the `CommunityDecksRepository` `@Binds`).
 * Because none of those types is consumed by any Hilt feature, the Hilt module was DELETED and its
 * `@Provides` were ported here verbatim as Koin `single { }` (the Archidekt OkHttpClient is still built
 * FROM SCRATCH — not from the global client — so it keeps its dedicated HTTP cache + User-Agent + 5 MB
 * response guard, mirroring the old behaviour exactly). [ArchidektRequestQueue] (no deps) and the three
 * use cases are likewise provided here. The repository's IO dispatcher is `Dispatchers.IO` directly —
 * the exact same singleton instance the old `@IoDispatcher` Hilt binding returned, so no behaviour
 * changes and no named Koin qualifier is needed.
 *
 * ## Bridge / shared singletons (resolved via `get()`)
 * Three dependencies are owned by the Hilt object graph and bridged into Koin via `ManaHubApp`:
 * - [CommunityDeckCacheDao] — comes from the Room DB graph (`DatabaseModule`). It is consumed ONLY by
 *   this island, so it is bridged in [communityDecksKoinModule] itself (passed in as [cacheDao]).
 * - [DeckRepository] — already shared (Stats + Home) and bridged once in `coreBridgeKoinModule`;
 *   resolved below via `get()`, never re-registered (a second `single<DeckRepository>` across two loaded
 *   modules would throw `DefinitionOverrideException`).
 * - [CardRepository] — used by [ImportCommunityDeckUseCase]. It was previously a Home-only bridged
 *   singleton; since it is now shared with this island it was PROMOTED into `coreBridgeKoinModule` and
 *   Home was shrunk to resolve it via `get()`. Resolved below via `get()`.
 *
 * [UserPreferencesDataStore] (the feature-flag source for both ViewModels) is also already in
 * `coreBridgeKoinModule` (shared with Settings + Profile + Home) and resolved via `get()`.
 *
 * @param cacheDao the Hilt/Room-owned [CommunityDeckCacheDao] singleton (this island only).
 * @return a Koin [Module] providing the Community Decks data layer + both ViewModel factories.
 */
fun communityDecksKoinModule(
    cacheDao: CommunityDeckCacheDao,
): Module = module {

    // ── Hilt → Koin bridge: the Room-owned cache DAO, used only by this island. ──
    single { cacheDao }

    // ── Archidekt network stack (ported from the deleted Hilt CommunityDecksModule). ──
    single { provideArchidektApi(androidContext()) }
    single { ArchidektRequestQueue() }

    // ── Data layer. CommunityDeckCacheDao + DeckRepository + CardRepository resolve via get() ──
    // (cache DAO from this module; DeckRepository + CardRepository from coreBridgeKoinModule).
    single<CommunityDecksRepository> {
        CommunityDecksRepositoryImpl(
            api = get(),
            requestQueue = get(),
            cacheDao = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }

    // ── Domain use cases. ──
    single { SearchCommunityDecksUseCase(repository = get()) }
    single { GetCommunityDeckUseCase(repository = get()) }
    single { ImportCommunityDeckUseCase(deckRepository = get(), cardRepository = get()) }

    // ── The Koin island: both Community Decks ViewModels are now resolved by Koin, not Hilt. ──
    // Koin injects the SavedStateHandle (carrying the `cardName` / `archidektId` nav args) into each
    // `viewModel { }` factory, so the nav-arg behaviour is identical to the previous Hilt resolution.
    viewModel {
        CommunityDecksSearchViewModel(
            savedStateHandle = get(),
            searchCommunityDecks = get(),
            userPreferences = get(),
        )
    }
    viewModel {
        CommunityDeckDetailViewModel(
            savedStateHandle = get(),
            getCommunityDeck = get(),
            importCommunityDeck = get(),
            userPreferences = get(),
        )
    }
}

/** Hard cap on a single Archidekt response body (5 MB) to protect against OOM. */
private const val MAX_ARCHIDEKT_RESPONSE_BYTES = 5L * 1024 * 1024

/**
 * Builds the Archidekt [ArchidektApi].
 *
 * The OkHttpClient is built FROM SCRATCH (not `globalClient.newBuilder()`) — mirroring the Cloudflare
 * client in `DraftModule` and the deleted Hilt `CommunityDecksModule` — so it does not inherit the
 * app-wide network interceptor (which forces an aggressive `Cache-Control` that would conflict with this
 * dedicated HTTP cache), and so the User-Agent / timeouts / response-size guard are scoped to Archidekt
 * only.
 *
 * @param context the application [Context] (for the dedicated disk cache directory).
 * @return a configured [ArchidektApi].
 */
private fun provideArchidektApi(context: Context): ArchidektApi {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "ManaHub/1.0 Android (deck browser)")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        })
        .cache(Cache(File(context.cacheDir, "http_cache_archidekt"), 10L * 1024 * 1024))
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val contentLength = response.header("Content-Length")?.toLongOrNull()
            if (contentLength != null && contentLength > MAX_ARCHIDEKT_RESPONSE_BYTES) {
                response.close()
                throw IOException("Archidekt response too large: ${contentLength / 1024} KB")
            }
            response
        }
        .build()

    val retrofit = Retrofit.Builder()
        .baseUrl("https://archidekt.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    return retrofit.create(ArchidektApi::class.java)
}
