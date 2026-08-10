package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.common.KeyValueStore
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.model.AppLanguage
import com.mmg.manahub.core.model.CardLanguage
import com.mmg.manahub.core.model.CollectionGroupingMode
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.model.NewsLanguage
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.UserDefinedTag
import com.mmg.manahub.core.model.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Web [UserPreferencesRepository] implementation (web roadmap W3a), backed by the wasmJs
 * [KeyValueStore] actual (real `window.localStorage` — see `LocalStorageKeyValueStore` in
 * `shared/core-common`).
 *
 * This is deliberately NOT a port of Android's `UserPreferencesDataStore` — that file is a
 * 1008-line god-object serving dozens of preference flows well beyond this narrow interface
 * (gamification flags, home widget layout, quick-start actions, etc.). This class implements
 * ONLY the [UserPreferencesRepository] contract, written fresh for the web target.
 *
 * **Reactivity pattern**: [KeyValueStore] has no built-in reactive-flow support (no Room/DataStore
 * `Flow`, and `localStorage`'s own `storage` DOM event only fires in OTHER browser tabs, never the
 * tab that performed the write). Every exposed [Flow] here is therefore backed by an in-memory
 * [MutableStateFlow] cache: hydrated from [keyValueStore] once at construction, and kept in sync on
 * every write made through THIS instance. Because this repository is registered as a single Koin
 * `single`, "this instance" is the app's entire write path for these preferences, so the cache never
 * drifts from persisted state for any write originating in the same session — the one case NOT
 * covered is a change written from a different browser tab, an accepted limitation shared with
 * `localStorage` itself. Future `wasmJsMain` repository slices needing the same shape should reuse
 * this pattern (see `project_kmp_spike_findings.md`).
 *
 * **Encoding**: values are string-encoded the same conceptual way Android's DataStore-backed
 * implementation does (enum `.code`/`.name`, `Long.toString()`, JSON for structured/collection
 * values) via `kotlinx.serialization`, so the persisted shape is easy to reason about across
 * platforms — this is fresh code, not a wire-format port, so there is no cross-platform storage
 * compatibility requirement (Android and Web each persist to their own local store).
 */
class WebUserPreferencesRepository(
    private val keyValueStore: KeyValueStore,
) : UserPreferencesRepository {

    /** One-shot hydration scope — see the [Dispatchers.Unconfined] note on [hydrate] below. */
    private val hydrationScope = CoroutineScope(Job())
    private val json = Json { ignoreUnknownKeys = true }

    private val _preferences = MutableStateFlow(defaultPreferences())
    override val preferencesFlow: Flow<UserPreferences> = _preferences.asStateFlow()

    override val preferredCurrencyFlow: Flow<PreferredCurrency> =
        _preferences.asStateFlow().map { it.preferredCurrency }

    override val collectionViewModeFlow: Flow<CollectionViewMode> =
        _preferences.asStateFlow().map { it.collectionViewMode }

    private val _lastPriceRefresh = MutableStateFlow<Long?>(null)
    override val lastPriceRefreshFlow: Flow<Long?> = _lastPriceRefresh.asStateFlow()

    private val _userDefinedTags = MutableStateFlow<List<UserDefinedTag>>(emptyList())
    override val userDefinedTagsFlow: Flow<List<UserDefinedTag>> = _userDefinedTags.asStateFlow()

    private val _collectionGroupingMode = MutableStateFlow(CollectionGroupingMode.NONE)
    override val collectionGroupingModeFlow: Flow<CollectionGroupingMode> = _collectionGroupingMode.asStateFlow()

    init {
        // LocalStorageKeyValueStore's suspend signatures delegate directly to synchronous
        // `localStorage` calls with no real suspension point, so a Dispatchers.Unconfined
        // coroutine runs this hydration to completion EAGERLY, inline with construction — callers
        // never observe the default seed value when a persisted one already exists. If a future
        // KeyValueStore actual performs genuine async I/O (e.g. an IndexedDB-backed store), this
        // degrades gracefully to eventually-consistent (default seed, then a follow-up emission)
        // rather than synchronous — an accepted trade-off documented here, not a latent bug.
        hydrationScope.launch(Dispatchers.Unconfined) { hydrate() }
    }

    private suspend fun hydrate() {
        _preferences.value = readPreferences()
        _lastPriceRefresh.value = keyValueStore.getString(KEY_LAST_PRICE_REFRESH)?.toLongOrNull()
        _userDefinedTags.value = readUserDefinedTags()
        _collectionGroupingMode.value =
            CollectionGroupingMode.fromName(keyValueStore.getString(KEY_COLLECTION_GROUPING_MODE))
    }

    private suspend fun readPreferences(): UserPreferences {
        val newsLanguages = keyValueStore.getString(KEY_NEWS_LANGUAGES)
            ?.let { encoded -> runCatching { json.decodeFromString<List<String>>(encoded) }.getOrNull() }
            ?.mapNotNull { code -> NewsLanguage.entries.find { it.code == code } }
            ?.toSet()
            ?.ifEmpty { null }
            ?: setOf(NewsLanguage.ENGLISH)

        return UserPreferences(
            appLanguage = AppLanguage.fromCode(
                keyValueStore.getString(KEY_APP_LANGUAGE) ?: AppLanguage.ENGLISH.code
            ),
            cardLanguage = CardLanguage.fromCode(
                keyValueStore.getString(KEY_CARD_LANGUAGE) ?: CardLanguage.ENGLISH.code
            ),
            newsLanguages = newsLanguages,
            preferredCurrency = PreferredCurrency.fromCode(
                keyValueStore.getString(KEY_PREFERRED_CURRENCY) ?: PreferredCurrency.EUR.code
            ),
            collectionViewMode = CollectionViewMode.fromName(
                keyValueStore.getString(KEY_COLLECTION_VIEW_MODE)
            ),
        )
    }

    private suspend fun readUserDefinedTags(): List<UserDefinedTag> {
        val raw = keyValueStore.getString(KEY_USER_DEFINED_TAGS) ?: return emptyList()
        return runCatching { json.decodeFromString<List<UdtRecord>>(raw) }
            .getOrDefault(emptyList())
            .map { UserDefinedTag(key = it.k, label = it.l, categoryKey = it.c) }
    }

    override suspend fun setAppLanguage(language: AppLanguage) {
        keyValueStore.putString(KEY_APP_LANGUAGE, language.code)
        _preferences.update { it.copy(appLanguage = language) }
    }

    override suspend fun setCardLanguage(language: CardLanguage) {
        keyValueStore.putString(KEY_CARD_LANGUAGE, language.code)
        _preferences.update { it.copy(cardLanguage = language) }
    }

    override suspend fun setNewsLanguages(languages: Set<NewsLanguage>) {
        keyValueStore.putString(KEY_NEWS_LANGUAGES, json.encodeToString(languages.map { it.code }))
        _preferences.update { it.copy(newsLanguages = languages) }
    }

    override suspend fun setPreferredCurrency(currency: PreferredCurrency) {
        keyValueStore.putString(KEY_PREFERRED_CURRENCY, currency.code)
        _preferences.update { it.copy(preferredCurrency = currency) }
    }

    override suspend fun saveLastPriceRefresh(timestamp: Long) {
        keyValueStore.putString(KEY_LAST_PRICE_REFRESH, timestamp.toString())
        _lastPriceRefresh.value = timestamp
    }

    override suspend fun saveUserDefinedTag(tag: UserDefinedTag) {
        val updated = _userDefinedTags.value.filterNot { it.key == tag.key } + tag
        persistUserDefinedTags(updated)
    }

    override suspend fun deleteUserDefinedTag(key: String) {
        val updated = _userDefinedTags.value.filterNot { it.key == key }
        persistUserDefinedTags(updated)
    }

    private suspend fun persistUserDefinedTags(tags: List<UserDefinedTag>) {
        val records = tags.map { UdtRecord(k = it.key, l = it.label, c = it.categoryKey) }
        keyValueStore.putString(KEY_USER_DEFINED_TAGS, json.encodeToString(records))
        _userDefinedTags.value = tags
    }

    override suspend fun saveCollectionViewMode(mode: CollectionViewMode) {
        keyValueStore.putString(KEY_COLLECTION_VIEW_MODE, mode.name)
        _preferences.update { it.copy(collectionViewMode = mode) }
    }

    override suspend fun saveCollectionGroupingMode(mode: CollectionGroupingMode) {
        keyValueStore.putString(KEY_COLLECTION_GROUPING_MODE, mode.name)
        _collectionGroupingMode.value = mode
    }

    private fun defaultPreferences() = UserPreferences(
        appLanguage = AppLanguage.ENGLISH,
        cardLanguage = CardLanguage.ENGLISH,
        newsLanguages = setOf(NewsLanguage.ENGLISH),
        preferredCurrency = PreferredCurrency.EUR,
        collectionViewMode = CollectionViewMode.GRID,
    )

    /** Compact JSON record for a [UserDefinedTag] — short field names mirror Android's own
     * `UdtRecord` convention (`k`/`l`/`c`) purely for familiarity; there is no shared wire format
     * between platforms since each persists to its own local store. */
    @Serializable
    private data class UdtRecord(val k: String, val l: String, val c: String)

    private companion object {
        const val KEY_APP_LANGUAGE = "app_language"
        const val KEY_CARD_LANGUAGE = "card_language"
        const val KEY_NEWS_LANGUAGES = "news_languages"
        const val KEY_PREFERRED_CURRENCY = "preferred_currency"
        const val KEY_COLLECTION_VIEW_MODE = "collection_view_mode"
        const val KEY_COLLECTION_GROUPING_MODE = "collection_grouping_mode"
        const val KEY_LAST_PRICE_REFRESH = "last_price_refresh"
        const val KEY_USER_DEFINED_TAGS = "user_defined_tags"
    }
}
