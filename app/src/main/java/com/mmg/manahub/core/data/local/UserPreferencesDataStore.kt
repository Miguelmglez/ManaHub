package com.mmg.manahub.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mmg.manahub.core.domain.model.AppLanguage
import com.mmg.manahub.core.domain.model.CardLanguage
import com.mmg.manahub.core.domain.model.CollectionViewMode
import com.mmg.manahub.core.domain.model.NewsLanguage
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.domain.model.ScoreWeightOverrides
import com.mmg.manahub.core.domain.model.UserDefinedTag
import com.mmg.manahub.core.domain.model.UserPreferences
import com.mmg.manahub.core.domain.model.news.NewsFilterPrefs
import com.mmg.manahub.core.domain.model.news.SourceType
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.gamification.domain.model.EquippedCosmetics
import com.mmg.manahub.core.gamification.domain.model.EquippedCosmetics.Companion.MAX_EQUIPPED_BADGES
import com.mmg.manahub.core.ui.theme.AppTheme
import com.mmg.manahub.core.util.PriceFormatter.isEuropeanLocale
import com.mmg.manahub.feature.home.presentation.HomeWidgetType
import com.mmg.manahub.feature.home.presentation.QuickStartAction
import com.mmg.manahub.feature.home.presentation.WidgetInstance
import com.mmg.manahub.feature.home.presentation.WidgetSize
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

internal val Context.userPrefsDataStore by preferencesDataStore(name = "user_prefs")

private val KEY_APP_LANGUAGE      = stringPreferencesKey("app_language")
private val KEY_CARD_LANGUAGE     = stringPreferencesKey("card_language")
private val KEY_NEWS_LANGUAGES    = stringSetPreferencesKey("news_languages")
/** News filter: included [SourceType] names. Absent → both ARTICLE + VIDEO. */
private val KEY_NEWS_FILTER_TYPES      = stringSetPreferencesKey("news_filter_types")
/** News filter: explicit source-id allowlist. Absent/empty → null (= all enabled sources). */
private val KEY_NEWS_FILTER_SOURCE_IDS = stringSetPreferencesKey("news_filter_source_ids")
private val KEY_PREFERRED_CURRENCY = stringPreferencesKey("preferred_currency")
private val LAST_PRICE_REFRESH_KEY = longPreferencesKey("last_price_refresh")
private val AVATAR_URL_KEY         = stringPreferencesKey("avatar_url")
private val KEY_PLAYER_NAME = stringPreferencesKey("player_name")
private val KEY_APP_THEME   = stringPreferencesKey("app_theme")
private val KEY_TAG_AUTO_THRESHOLD    = floatPreferencesKey("tag_auto_threshold")
private val KEY_TAG_SUGGEST_THRESHOLD = floatPreferencesKey("tag_suggest_threshold")
private val KEY_TAG_OVERRIDES_JSON    = stringPreferencesKey("tag_dictionary_overrides")
private val KEY_USER_DEFINED_TAGS     = stringPreferencesKey("user_defined_tags")
private val KEY_COLLECTION_VIEW_MODE = stringPreferencesKey("collection_view_mode")

// ── Home dashboard ──────────────────────────────────────────────────────────
/** Comma-separated list of QuickStartAction.persistedId values; order matters. */
private val KEY_QUICK_START_ORDER     = stringPreferencesKey("quick_start_order")
/** Epoch millis of the last account-nudge dismissal (cooldown anchor). */
private val KEY_ACCOUNT_NUDGE_DISMISSED_AT = longPreferencesKey("account_nudge_dismissed_at")
/** Ordered "persistedId:SIZE_NAME" tokens joined by "," describing the dashboard layout. */
private val KEY_HOME_LAYOUT = stringPreferencesKey("home_widget_layout")
/** Whether the customization coach-mark has been dismissed. */
private val KEY_HOME_COACHMARK_SEEN = booleanPreferencesKey("home_coachmark_seen")
/**
 * Comma-separated set of step IDs that the user has explicitly skipped from the
 * First Steps carousel. Order is not significant; duplicates are not produced.
 */
private val KEY_FIRST_STEPS_SKIPPED = stringPreferencesKey("home_first_steps_skipped")

/** How long the account nudge stays suppressed after a dismissal. */
private const val ACCOUNT_NUDGE_COOLDOWN_MS = 48L * 60L * 60L * 1000L // 48 hours

// ── Feature flags ─────────────────────────────────────────────────────────
private val KEY_PUSH_NOTIFICATIONS_ENABLED = booleanPreferencesKey("push_notifications_enabled")
/**
 * Master gamification switch (XP, levels, achievements, quests). Default: DISABLED — the
 * gamification UI is hidden for this release (see docs/gamification-hidden-for-release.md).
 * The engine keeps recording progress silently while this is off, so re-enabling restores
 * the user's true state.
 */
private val KEY_GAMIFICATION_ENABLED = booleanPreferencesKey("gamification_enabled")
/** One-shot flag: true once the Family-A achievement backfill has run (ADR-002 §4). Default: false. */
private val KEY_GAMIFICATION_BACKFILL_DONE = booleanPreferencesKey("gamification_backfill_done")
/** Per-install random id seeding deterministic quest generation for guests (ADR-002 §9). Not ANDROID_ID. */
private val KEY_GAMIFICATION_DEVICE_ID = stringPreferencesKey("gamification_device_id")

// ── Gamification: equipped cosmetics (Phase 3, ADR-002 §10) ───────────────────
/** Equipped TITLE unlockable id (single); absent = none. */
private val KEY_EQUIPPED_TITLE = stringPreferencesKey("gamification_equipped_title")
/** Equipped BADGE unlockable ids (≤3), stored comma-separated in display order. */
private val KEY_EQUIPPED_BADGES = stringPreferencesKey("gamification_equipped_badges")
/** Equipped AVATAR_FRAME unlockable id (single); absent = none. */
private val KEY_EQUIPPED_AVATAR_FRAME = stringPreferencesKey("gamification_equipped_frame")
/** Equipped LEVEL_RING_STYLE unlockable id (single); absent = none. */
private val KEY_EQUIPPED_RING_STYLE = stringPreferencesKey("gamification_equipped_ring")
/**
 * Last player level for which the level-up celebration was shown (Phase 3). Sentinel
 * [LAST_CELEBRATED_LEVEL_UNINITIALIZED] means "never observed" — Chunk B seeds it to the current level
 * WITHOUT celebrating on first observation, so existing players don't get a spurious burst.
 */
private val KEY_LAST_CELEBRATED_LEVEL = intPreferencesKey("gamification_last_celebrated_level")
/** Sentinel meaning the level-up celebration baseline was never set (seed-without-celebrating). */
private const val LAST_CELEBRATED_LEVEL_UNINITIALIZED = -1

private val KEY_EMBEDDING_DB_VERSION = intPreferencesKey("hash_db_version")

// ── Deck Doctor: scoring-weight overrides (debug-only tuning) ─────────────────
// Seven independent nullable Float overrides for the engine ScoreWeights. Absent key = use the
// engine default for that weight. Stored as primitives so this core-layer store never imports the
// feature-layer ScoreWeights type (the feature maps the holder to ScoreWeights).
private val KEY_SCORE_WEIGHT_SYNERGY            = floatPreferencesKey("score_weight_synergy")
private val KEY_SCORE_WEIGHT_ROLE_NEED          = floatPreferencesKey("score_weight_role_need")
private val KEY_SCORE_WEIGHT_CURVE              = floatPreferencesKey("score_weight_curve")
private val KEY_SCORE_WEIGHT_POWER              = floatPreferencesKey("score_weight_power")
private val KEY_SCORE_WEIGHT_COLOR              = floatPreferencesKey("score_weight_color")
private val KEY_SCORE_WEIGHT_REDUNDANCY_PENALTY = floatPreferencesKey("score_weight_redundancy_penalty")
private val KEY_SCORE_WEIGHT_POWER_FLOOR        = floatPreferencesKey("score_weight_power_floor")

// ── Privacy settings — persisted locally so SettingsScreen renders without a network call ──
private val KEY_COLLECTION_PUBLIC  = booleanPreferencesKey("collection_public")
private val KEY_WISHLIST_PUBLIC    = booleanPreferencesKey("wishlist_public")
private val KEY_TRADE_LIST_PUBLIC  = booleanPreferencesKey("trade_list_public")

private data class UdtRecord(val k: String, val l: String, val c: String)
private val udtListType = object : TypeToken<List<UdtRecord>>() {}.type
private val gson = Gson()

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : UserPreferencesRepository {

    override val preferencesFlow: Flow<UserPreferences> = context.userPrefsDataStore.data
        .map { prefs ->
            val deviceLang = Locale.getDefault().language
            val defaultNewsLangs = buildSet {
                add("en")
                if (deviceLang == "es" || deviceLang == "de") add(deviceLang)
            }
            val defaultCurrency = if (isEuropeanLocale()) "EUR" else "USD"

            UserPreferences(
                appLanguage = AppLanguage.fromCode(
                    prefs[KEY_APP_LANGUAGE] ?: "en"
                ),
                cardLanguage = CardLanguage.fromCode(
                    prefs[KEY_CARD_LANGUAGE] ?: "en"
                ),
                newsLanguages = (prefs[KEY_NEWS_LANGUAGES] ?: defaultNewsLangs)
                    .mapNotNull { NewsLanguage.entries.find { l -> l.code == it } }
                    .toSet()
                    .ifEmpty { setOf(NewsLanguage.ENGLISH) },
                preferredCurrency = PreferredCurrency.fromCode(
                    prefs[KEY_PREFERRED_CURRENCY] ?: defaultCurrency
                ),
                collectionViewMode = CollectionViewMode.fromName(
                    prefs[KEY_COLLECTION_VIEW_MODE]
                )
            )
        }
        .catch { emit(defaultPreferences()) }

    override suspend fun setAppLanguage(language: AppLanguage) {
        context.userPrefsDataStore.edit { it[KEY_APP_LANGUAGE] = language.code }
        // Use commit() (blocking) instead of apply() so the write is guaranteed to
        // be on disk before SettingsViewModel emits appLanguageChanged and the
        // Activity restarts and reads the value in attachBaseContext.
        context.getSharedPreferences("user_prefs_lang_sync", Context.MODE_PRIVATE)
            .edit()
            .putString("app_language_sync", language.code)
            .commit()
    }

    override suspend fun setCardLanguage(language: CardLanguage) {
        context.userPrefsDataStore.edit { it[KEY_CARD_LANGUAGE] = language.code }
    }

    override suspend fun setNewsLanguages(languages: Set<NewsLanguage>) {
        context.userPrefsDataStore.edit { it[KEY_NEWS_LANGUAGES] = languages.map { l -> l.code }.toSet() }
    }

    // ── News feed filters (shared by NewsScreen + Home news widget) ───────────────
    //
    // The single persisted source of truth for the news filter selection. Languages
    // reuse the existing KEY_NEWS_LANGUAGES key (which stores long NewsLanguage codes
    // such as "en-GB"); the filter layer works in SHORT codes ("en"/"es"/"de") that
    // match ContentSource.language, so we project to the first two chars on read and
    // map short → NewsLanguage on write. Default is English-only.

    /**
     * Emits the persisted [NewsFilterPrefs]. Absent values fall back to
     * [NewsFilterPrefs.DEFAULT] (English-only, both content types, all enabled sources).
     */
    fun observeNewsFilters(): Flow<NewsFilterPrefs> =
        context.userPrefsDataStore.data
            .map { prefs ->
                val languages = prefs[KEY_NEWS_LANGUAGES]
                    ?.map { it.take(2) }
                    ?.toSet()
                    ?.ifEmpty { null }
                    ?: NewsFilterPrefs.DEFAULT.languages

                val types = prefs[KEY_NEWS_FILTER_TYPES]
                    ?.mapNotNull { name -> SourceType.entries.firstOrNull { it.name == name } }
                    ?.toSet()
                    ?.ifEmpty { null }
                    ?: NewsFilterPrefs.DEFAULT.types

                // An empty/absent source-id set means "all enabled sources" (null).
                val sourceIds = prefs[KEY_NEWS_FILTER_SOURCE_IDS]?.takeIf { it.isNotEmpty() }

                NewsFilterPrefs(languages = languages, types = types, sourceIds = sourceIds)
            }
            .catch { emit(NewsFilterPrefs.DEFAULT) }

    /**
     * Persists the full news filter selection. Languages are stored as long
     * [NewsLanguage] codes (resolved from the short codes), so [preferencesFlow]'s
     * `newsLanguages` and [setNewsLanguages] keep working unchanged.
     */
    suspend fun setNewsFilters(
        languages: Set<String>,
        types: Set<SourceType>,
        sourceIds: Set<String>?,
    ) {
        context.userPrefsDataStore.edit { prefs ->
            val longLangCodes = languages
                .mapNotNull { short -> NewsLanguage.entries.firstOrNull { it.code.take(2) == short }?.code }
                .toSet()
                .ifEmpty { setOf(NewsLanguage.ENGLISH.code) }
            prefs[KEY_NEWS_LANGUAGES] = longLangCodes
            prefs[KEY_NEWS_FILTER_TYPES] = types
                .ifEmpty { NewsFilterPrefs.DEFAULT.types }
                .map { it.name }
                .toSet()
            if (sourceIds.isNullOrEmpty()) {
                prefs.remove(KEY_NEWS_FILTER_SOURCE_IDS)
            } else {
                prefs[KEY_NEWS_FILTER_SOURCE_IDS] = sourceIds
            }
        }
    }

    /** Clears all persisted news filters back to [NewsFilterPrefs.DEFAULT] (English-only). */
    suspend fun resetNewsFilters() {
        context.userPrefsDataStore.edit { prefs ->
            prefs[KEY_NEWS_LANGUAGES] = setOf(NewsLanguage.ENGLISH.code)
            prefs.remove(KEY_NEWS_FILTER_TYPES)
            prefs.remove(KEY_NEWS_FILTER_SOURCE_IDS)
        }
    }

    override suspend fun setPreferredCurrency(currency: PreferredCurrency) {
        context.userPrefsDataStore.edit { it[KEY_PREFERRED_CURRENCY] = currency.code }
    }

    override val preferredCurrencyFlow: Flow<PreferredCurrency> = preferencesFlow
        .map { it.preferredCurrency }

    private fun defaultPreferences() = UserPreferences(
        appLanguage = AppLanguage.ENGLISH,
        cardLanguage = CardLanguage.ENGLISH,
        newsLanguages = setOf(NewsLanguage.ENGLISH),
        preferredCurrency = if (isEuropeanLocale()) PreferredCurrency.EUR else PreferredCurrency.USD,
        collectionViewMode = CollectionViewMode.GRID,
    )


    override val lastPriceRefreshFlow: Flow<Long?> = context.userPrefsDataStore.data
        .map { it[LAST_PRICE_REFRESH_KEY] }

    override suspend fun saveLastPriceRefresh(timestamp: Long) {
        context.userPrefsDataStore.edit { it[LAST_PRICE_REFRESH_KEY] = timestamp }
    }

    // ── Avatar URL ────────────────────────────────────────────────────────────

    val avatarUrlFlow: Flow<String?> = context.userPrefsDataStore.data
        .map { it[AVATAR_URL_KEY] }
        .catch { emit(null) }

    suspend fun saveAvatarUrl(url: String?) {
        context.userPrefsDataStore.edit { preferences ->
            if (url == null)
                preferences.remove(AVATAR_URL_KEY)
            else
                preferences[AVATAR_URL_KEY] = url
        }
    }
    val playerNameFlow: Flow<String> = context.userPrefsDataStore.data
        .map { prefs -> prefs[KEY_PLAYER_NAME] ?: "Wizard" }
        .catch { emit("Wizard") }

    val themeFlow: Flow<AppTheme> = context.userPrefsDataStore.data
        .map { prefs ->
            when (prefs[KEY_APP_THEME]) {
                "ARCANE_COSMOS"     -> AppTheme.ArcaneCosmos
                "NEON_VOID"         -> AppTheme.NeonVoid
                "MEDIEVAL_GRIMOIRE" -> AppTheme.MedievalGrimoire
                "FOREST_MURMUR"     -> AppTheme.ForestMurmur
                "ANCIENT_OAK"       -> AppTheme.AncientOak
                "HALLOWED_PRINT"    -> AppTheme.HallowedPrint
                
                "AZURE_FLUX"        -> AppTheme.AzureFlux
                "PLANAR_VEIL"       -> AppTheme.PlanarVeil
                "VENOM_SHADE"       -> AppTheme.VenomShade
                "GLACIAL_EDGE"      -> AppTheme.GlacialEdge
                "DUSK_EMBER"        -> AppTheme.DuskEmber
                "ONYX_NOIR"         -> AppTheme.OnyxNoir

                // ── v1 → v2 silent migrations ────────────────────────────────────
                "MYSTIC_ECHO"       -> AppTheme.PlanarVeil      // v4: merged
                "GILDED_SILVER"     -> AppTheme.AncientOak      // v4: replaced
                "OBSIDIAN_CHROME"   -> AppTheme.HallowedPrint   // replaced

                // ── v2 → v4 silent migrations ────────────────────────────────────
                "SHADOW_ESSENCE"    -> AppTheme.PlanarVeil
                "RELIQUARY"         -> AppTheme.AncientOak
                "PYROMANCER"        -> AppTheme.MedievalGrimoire
                "HYDROMANCY"        -> AppTheme.GlacialEdge

                else                -> AppTheme.NeonVoid
            }
        }
        .catch { emit(AppTheme.NeonVoid) }

    suspend fun savePlayerName(name: String) {
        context.userPrefsDataStore.edit { it[KEY_PLAYER_NAME] = name }
    }

    // ── Tag auto-tagger thresholds ────────────────────────────────────────────

    val tagAutoThresholdFlow: Flow<Float> = context.userPrefsDataStore.data
        .map { it[KEY_TAG_AUTO_THRESHOLD] ?: 0.90f }
        .catch { emit(0.90f) }

    val tagSuggestThresholdFlow: Flow<Float> = context.userPrefsDataStore.data
        .map { it[KEY_TAG_SUGGEST_THRESHOLD] ?: 0.60f }
        .catch { emit(0.60f) }

    suspend fun saveTagAutoThreshold(value: Float) {
        context.userPrefsDataStore.edit { it[KEY_TAG_AUTO_THRESHOLD] = value.coerceIn(0f, 1f) }
    }

    suspend fun saveTagSuggestThreshold(value: Float) {
        context.userPrefsDataStore.edit { it[KEY_TAG_SUGGEST_THRESHOLD] = value.coerceIn(0f, 1f) }
    }

    // ── Tag dictionary overrides (JSON blob) ─────────────────────────────────

    val tagDictionaryOverridesFlow: Flow<String> = context.userPrefsDataStore.data
        .map { it[KEY_TAG_OVERRIDES_JSON] ?: "[]" }
        .catch { emit("[]") }

    suspend fun saveTagDictionaryOverrides(json: String) {
        context.userPrefsDataStore.edit { it[KEY_TAG_OVERRIDES_JSON] = json }
    }

    // ── User-defined tags ─────────────────────────────────────────────────────

    override val userDefinedTagsFlow: Flow<List<UserDefinedTag>> = context.userPrefsDataStore.data
        .map { prefs ->
            val json = prefs[KEY_USER_DEFINED_TAGS] ?: "[]"
            runCatching<List<UserDefinedTag>> {
                val records: List<UdtRecord> = gson.fromJson(json, udtListType) ?: emptyList()
                records.map { UserDefinedTag(key = it.k, label = it.l, categoryKey = it.c) }
            }.getOrDefault(emptyList())
        }
        .catch { emit(emptyList()) }

    override val collectionViewModeFlow: Flow<CollectionViewMode> = context.userPrefsDataStore.data
        .map { prefs -> CollectionViewMode.fromName(prefs[KEY_COLLECTION_VIEW_MODE]) }
        .catch { emit(CollectionViewMode.GRID) }

    override suspend fun saveUserDefinedTag(tag: UserDefinedTag) {
        context.userPrefsDataStore.edit { prefs ->
            val json = prefs[KEY_USER_DEFINED_TAGS] ?: "[]"
            val records: MutableList<UdtRecord> = runCatching<List<UdtRecord>> {
                gson.fromJson(json, udtListType) ?: emptyList()
            }.getOrDefault(emptyList()).toMutableList()
            records.removeAll { it.k == tag.key }
            records.add(UdtRecord(k = tag.key, l = tag.label, c = tag.categoryKey))
            prefs[KEY_USER_DEFINED_TAGS] = gson.toJson(records)
        }
    }

    override suspend fun deleteUserDefinedTag(key: String) {
        context.userPrefsDataStore.edit { prefs ->
            val json = prefs[KEY_USER_DEFINED_TAGS] ?: "[]"
            val records: MutableList<UdtRecord> = runCatching<List<UdtRecord>> {
                gson.fromJson(json, udtListType) ?: emptyList()
            }.getOrDefault(emptyList()).toMutableList()
            records.removeAll { it.k == key }
            prefs[KEY_USER_DEFINED_TAGS] = gson.toJson(records)
        }
    }

    override suspend fun saveCollectionViewMode(mode: CollectionViewMode) {
        context.userPrefsDataStore.edit { it[KEY_COLLECTION_VIEW_MODE] = mode.name }
    }

    // ── Embedding database version ────────────────────────────────────────────

    /** Emits the locally stored version of the downloaded embedding DB (0 = bundled asset only). */
    val embeddingDbVersionFlow: Flow<Int> = context.userPrefsDataStore.data
        .map { it[KEY_EMBEDDING_DB_VERSION] ?: 0 }
        .catch { emit(0) }

    /** Returns the current embedding DB version synchronously (for use in Workers). */
    suspend fun getEmbeddingDbVersion(): Int =
        context.userPrefsDataStore.data.map { it[KEY_EMBEDDING_DB_VERSION] ?: 0 }.first()

    /** Persists the version number after a successful R2 download. */
    suspend fun saveEmbeddingDbVersion(version: Int) {
        context.userPrefsDataStore.edit { it[KEY_EMBEDDING_DB_VERSION] = version }
    }

    // ── Deck Doctor: scoring-weight overrides (debug-only tuning) ──────────────
    //
    // A debug-only entry point for tuning the Deck Doctor scoring weights. Each field is an
    // INDEPENDENT nullable Float; an absent key means "use the engine default for that weight".
    // With NO key set, [observeScoreWeightOverrides] emits [ScoreWeightOverrides.NONE], which the
    // feature/decks mapper resolves to exactly the default ScoreWeights() — i.e. zero behavior
    // change in production. There is intentionally no settings UI for the writer; it exists so a
    // debug build can experiment with weight tuning.

    /**
     * Emits the current Deck Doctor scoring-weight overrides. Every field is null unless a debug
     * build has explicitly set it, so the default emission is [ScoreWeightOverrides.NONE].
     */
    fun observeScoreWeightOverrides(): Flow<ScoreWeightOverrides> =
        context.userPrefsDataStore.data
            .map { prefs ->
                ScoreWeightOverrides(
                    synergy = prefs[KEY_SCORE_WEIGHT_SYNERGY],
                    roleNeed = prefs[KEY_SCORE_WEIGHT_ROLE_NEED],
                    curve = prefs[KEY_SCORE_WEIGHT_CURVE],
                    power = prefs[KEY_SCORE_WEIGHT_POWER],
                    color = prefs[KEY_SCORE_WEIGHT_COLOR],
                    redundancyPenalty = prefs[KEY_SCORE_WEIGHT_REDUNDANCY_PENALTY],
                    powerFloor = prefs[KEY_SCORE_WEIGHT_POWER_FLOOR],
                )
            }
            .catch { emit(ScoreWeightOverrides.NONE) }

    /**
     * DEBUG-ONLY tuning entry point. Persists the supplied [overrides]; a null field clears that
     * weight's override (falling back to the engine default). Not wired to any UI — intended to be
     * invoked from a debug build to experiment with scoring weights.
     */
    suspend fun setScoreWeightOverrides(overrides: ScoreWeightOverrides) {
        context.userPrefsDataStore.edit { prefs ->
            putOrRemoveFloat(prefs, KEY_SCORE_WEIGHT_SYNERGY, overrides.synergy)
            putOrRemoveFloat(prefs, KEY_SCORE_WEIGHT_ROLE_NEED, overrides.roleNeed)
            putOrRemoveFloat(prefs, KEY_SCORE_WEIGHT_CURVE, overrides.curve)
            putOrRemoveFloat(prefs, KEY_SCORE_WEIGHT_POWER, overrides.power)
            putOrRemoveFloat(prefs, KEY_SCORE_WEIGHT_COLOR, overrides.color)
            putOrRemoveFloat(prefs, KEY_SCORE_WEIGHT_REDUNDANCY_PENALTY, overrides.redundancyPenalty)
            putOrRemoveFloat(prefs, KEY_SCORE_WEIGHT_POWER_FLOOR, overrides.powerFloor)
        }
    }

    /** DEBUG-ONLY: clears every scoring-weight override so the engine defaults are used. */
    suspend fun clearScoreWeightOverrides() {
        context.userPrefsDataStore.edit { prefs ->
            prefs.remove(KEY_SCORE_WEIGHT_SYNERGY)
            prefs.remove(KEY_SCORE_WEIGHT_ROLE_NEED)
            prefs.remove(KEY_SCORE_WEIGHT_CURVE)
            prefs.remove(KEY_SCORE_WEIGHT_POWER)
            prefs.remove(KEY_SCORE_WEIGHT_COLOR)
            prefs.remove(KEY_SCORE_WEIGHT_REDUNDANCY_PENALTY)
            prefs.remove(KEY_SCORE_WEIGHT_POWER_FLOOR)
        }
    }

    /** Writes [value] under [key], or removes the key when [value] is null (clears the override). */
    private fun putOrRemoveFloat(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        key: androidx.datastore.preferences.core.Preferences.Key<Float>,
        value: Float?,
    ) {
        if (value == null) prefs.remove(key) else prefs[key] = value
    }

    // ── Feature flags ─────────────────────────────────────────────────────────

    /** Controls whether FCM push notifications are sent and displayed. Default: false (disabled until rollout). */
    val pushNotificationsEnabledFlow: Flow<Boolean> = context.userPrefsDataStore.data
        .map { prefs -> prefs[KEY_PUSH_NOTIFICATIONS_ENABLED] ?: false }
        .catch { emit(false) }

    suspend fun savePushNotificationsEnabled(enabled: Boolean) {
        context.userPrefsDataStore.edit { it[KEY_PUSH_NOTIFICATIONS_ENABLED] = enabled }
    }

    /**
     * Master gamification switch. Default: false (OFF) because the gamification UI is hidden
     * for this release (see docs/gamification-hidden-for-release.md). When false, all
     * gamification UI (XP, levels, achievements, quests) is hidden — the engine keeps recording
     * progress silently so re-enabling restores the user's true state (ADR-002 §"opt-out
     * first-class"). To restore the feature, flip the default back to true / emit(true).
     */
    val gamificationEnabledFlow: Flow<Boolean> = context.userPrefsDataStore.data
        .map { prefs -> prefs[KEY_GAMIFICATION_ENABLED] ?: false }
        .catch { emit(false) }

    /** Persists the master gamification switch. */
    suspend fun setGamificationEnabled(enabled: Boolean) {
        context.userPrefsDataStore.edit { it[KEY_GAMIFICATION_ENABLED] = enabled }
    }

    /**
     * One-shot guard for the Family-A achievement backfill (ADR-002 §4). Emits false until the
     * backfill has run, then true forever — so retroactive unlocks are computed exactly once.
     */
    val gamificationBackfillDoneFlow: Flow<Boolean> = context.userPrefsDataStore.data
        .map { prefs -> prefs[KEY_GAMIFICATION_BACKFILL_DONE] ?: false }
        .catch { emit(false) }

    /** Reads the backfill-done flag once (snapshot), for the app-start orchestrator. */
    suspend fun isGamificationBackfillDone(): Boolean =
        context.userPrefsDataStore.data.map { it[KEY_GAMIFICATION_BACKFILL_DONE] ?: false }.first()

    /** Marks the Family-A achievement backfill as complete (never re-runs after this). */
    suspend fun setGamificationBackfillDone() {
        context.userPrefsDataStore.edit { it[KEY_GAMIFICATION_BACKFILL_DONE] = true }
    }

    /**
     * Returns a stable, per-install device id used to seed deterministic quest generation for guests
     * (ADR-002 §9). Generated once (a random [java.util.UUID]) and persisted; NOT `ANDROID_ID` (which is
     * tied to the device/account and is a privacy concern). For signed-in users the authenticated user
     * id is used instead — this is only the guest fallback.
     */
    suspend fun getOrCreateGamificationDeviceId(): String {
        val existing = context.userPrefsDataStore.data
            .map { it[KEY_GAMIFICATION_DEVICE_ID] }
            .first()
        if (existing != null) return existing

        val generated = java.util.UUID.randomUUID().toString()
        context.userPrefsDataStore.edit { prefs ->
            // Re-check inside the edit transaction to avoid two concurrent callers minting two ids.
            val current = prefs[KEY_GAMIFICATION_DEVICE_ID]
            if (current == null) prefs[KEY_GAMIFICATION_DEVICE_ID] = generated
        }
        return context.userPrefsDataStore.data
            .map { it[KEY_GAMIFICATION_DEVICE_ID] ?: generated }
            .first()
    }

    // ── Gamification: equipped cosmetics (Phase 3, ADR-002 §10) ────────────────
    //
    // Equipped selection is a PRESENTATION choice persisted here (ownership lives in the
    // `entitlements` Room table). Singles are nullable strings; badges are an ORDERED comma-separated
    // list capped at MAX_EQUIPPED_BADGES on both read and write. Setting null/empty clears a slot.
    // The repository layer GUARDS equips against ownership; this DataStore only stores the choice.

    /** Sentinel for "the level-up celebration baseline has never been initialized" (see Phase-3 contract). */
    val lastCelebratedLevelUninitialized: Int get() = LAST_CELEBRATED_LEVEL_UNINITIALIZED

    /** Equipped TITLE id, or null when none is equipped. */
    val equippedTitleIdFlow: Flow<String?> = context.userPrefsDataStore.data
        .map { it[KEY_EQUIPPED_TITLE] }
        .catch { emit(null) }

    /** Equipped AVATAR_FRAME id, or null when none is equipped. */
    val equippedAvatarFrameIdFlow: Flow<String?> = context.userPrefsDataStore.data
        .map { it[KEY_EQUIPPED_AVATAR_FRAME] }
        .catch { emit(null) }

    /** Equipped LEVEL_RING_STYLE id, or null when none is equipped. */
    val equippedLevelRingStyleIdFlow: Flow<String?> = context.userPrefsDataStore.data
        .map { it[KEY_EQUIPPED_RING_STYLE] }
        .catch { emit(null) }

    /** Equipped BADGE ids in order (size 0..[MAX_EQUIPPED_BADGES]); blanks skipped, capped on read. */
    val equippedBadgeIdsFlow: Flow<List<String>> = context.userPrefsDataStore.data
        .map { prefs -> decodeBadgeIds(prefs[KEY_EQUIPPED_BADGES]) }
        .catch { emit(emptyList()) }

    /**
     * The full equipped-cosmetics selection (Phase 3). Chunk B's Profile hero renders the equipped
     * title/badges/frame/ring from this. Each underlying slot is independently `.catch`-isolated.
     */
    val equippedCosmeticsFlow: Flow<EquippedCosmetics> = context.userPrefsDataStore.data
        .map { prefs ->
            EquippedCosmetics(
                titleId = prefs[KEY_EQUIPPED_TITLE],
                badgeIds = decodeBadgeIds(prefs[KEY_EQUIPPED_BADGES]),
                avatarFrameId = prefs[KEY_EQUIPPED_AVATAR_FRAME],
                levelRingStyleId = prefs[KEY_EQUIPPED_RING_STYLE],
            )
        }
        .catch { emit(EquippedCosmetics.NONE) }

    /** Equips [id] as the title, or clears the slot when [id] is null/blank. */
    suspend fun setEquippedTitle(id: String?) {
        context.userPrefsDataStore.edit { prefs ->
            if (id.isNullOrBlank()) prefs.remove(KEY_EQUIPPED_TITLE) else prefs[KEY_EQUIPPED_TITLE] = id
        }
    }

    /** Equips [id] as the avatar frame, or clears the slot when [id] is null/blank. */
    suspend fun setEquippedAvatarFrame(id: String?) {
        context.userPrefsDataStore.edit { prefs ->
            if (id.isNullOrBlank()) prefs.remove(KEY_EQUIPPED_AVATAR_FRAME)
            else prefs[KEY_EQUIPPED_AVATAR_FRAME] = id
        }
    }

    /** Equips [id] as the level-ring style, or clears the slot when [id] is null/blank. */
    suspend fun setEquippedLevelRingStyle(id: String?) {
        context.userPrefsDataStore.edit { prefs ->
            if (id.isNullOrBlank()) prefs.remove(KEY_EQUIPPED_RING_STYLE)
            else prefs[KEY_EQUIPPED_RING_STYLE] = id
        }
    }

    /**
     * Sets the equipped badges to [ids], blanks dropped and capped at [MAX_EQUIPPED_BADGES]. An empty
     * result clears the slot.
     */
    suspend fun setEquippedBadges(ids: List<String>) {
        val sanitized = ids.map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_EQUIPPED_BADGES)
        context.userPrefsDataStore.edit { prefs ->
            if (sanitized.isEmpty()) prefs.remove(KEY_EQUIPPED_BADGES)
            else prefs[KEY_EQUIPPED_BADGES] = sanitized.joinToString(",")
        }
    }

    /**
     * Emits the last level for which the level-up celebration was shown, or
     * [LAST_CELEBRATED_LEVEL_UNINITIALIZED] when never observed (Phase 3 celebration contract).
     */
    val lastCelebratedLevelFlow: Flow<Int> = context.userPrefsDataStore.data
        .map { it[KEY_LAST_CELEBRATED_LEVEL] ?: LAST_CELEBRATED_LEVEL_UNINITIALIZED }
        .catch { emit(LAST_CELEBRATED_LEVEL_UNINITIALIZED) }

    /** One-shot snapshot read of [lastCelebratedLevelFlow] (for the celebration host's seed check). */
    suspend fun getLastCelebratedLevel(): Int = context.userPrefsDataStore.data
        .map { it[KEY_LAST_CELEBRATED_LEVEL] ?: LAST_CELEBRATED_LEVEL_UNINITIALIZED }
        .first()

    /** Persists [level] as the most recently celebrated level-up baseline. */
    suspend fun setLastCelebratedLevel(level: Int) {
        context.userPrefsDataStore.edit { it[KEY_LAST_CELEBRATED_LEVEL] = level }
    }

    /** Decodes the comma-separated badge string: trim, drop blanks, cap at [MAX_EQUIPPED_BADGES]. */
    private fun decodeBadgeIds(raw: String?): List<String> =
        raw?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.take(MAX_EQUIPPED_BADGES)
            ?: emptyList()

    // ── Privacy settings ──────────────────────────────────────────────────────
    //
    // These three flags mirror the user's own `user_profiles` visibility settings
    // stored in Supabase. They are cached here so SettingsScreen can render the
    // current state immediately without a network call. The repository is
    // responsible for keeping them in sync after a successful Supabase read/write.
    //
    // Defaults: collection private (false), wishlist and trade list public (true).
    // These match the Supabase column defaults in the `user_profiles` table.

    val collectionPublicFlow: Flow<Boolean> = context.userPrefsDataStore.data
        .map { prefs -> prefs[KEY_COLLECTION_PUBLIC] ?: false }
        .catch { emit(false) }

    val wishlistPublicFlow: Flow<Boolean> = context.userPrefsDataStore.data
        .map { prefs -> prefs[KEY_WISHLIST_PUBLIC] ?: true }
        .catch { emit(true) }

    val tradeListPublicFlow: Flow<Boolean> = context.userPrefsDataStore.data
        .map { prefs -> prefs[KEY_TRADE_LIST_PUBLIC] ?: true }
        .catch { emit(true) }

    suspend fun saveCollectionPublic(value: Boolean) {
        context.userPrefsDataStore.edit { it[KEY_COLLECTION_PUBLIC] = value }
    }

    suspend fun saveWishlistPublic(value: Boolean) {
        context.userPrefsDataStore.edit { it[KEY_WISHLIST_PUBLIC] = value }
    }

    suspend fun saveTradeListPublic(value: Boolean) {
        context.userPrefsDataStore.edit { it[KEY_TRADE_LIST_PUBLIC] = value }
    }

    suspend fun saveTheme(theme: AppTheme) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[KEY_APP_THEME] = when (theme) {
                AppTheme.NeonVoid         -> "NEON_VOID"
                AppTheme.MedievalGrimoire -> "MEDIEVAL_GRIMOIRE"
                AppTheme.ArcaneCosmos     -> "ARCANE_COSMOS"
                AppTheme.ForestMurmur     -> "FOREST_MURMUR"
                AppTheme.AncientOak       -> "ANCIENT_OAK"
                AppTheme.HallowedPrint    -> "HALLOWED_PRINT"
                AppTheme.AzureFlux        -> "AZURE_FLUX"
                AppTheme.PlanarVeil       -> "PLANAR_VEIL"
                AppTheme.VenomShade       -> "VENOM_SHADE"
                AppTheme.GlacialEdge      -> "GLACIAL_EDGE"
                AppTheme.DuskEmber        -> "DUSK_EMBER"
                AppTheme.OnyxNoir         -> "ONYX_NOIR"
            }
        }
    }

    // ── Home dashboard: Quick Start customization ──────────────────────────────
    //
    // Persisted as an ORDERED comma-separated string of persistedIds (not a
    // StringSet, which is unordered). On read, unknown/removed ids are dropped;
    // if fewer than four valid actions survive, we fall back to the defaults so
    // the grid always has exactly four useful shortcuts.

    /** Emits the user's ordered Quick Start actions.
     *
     * Valid ids from the stored string are preserved in order; unknown ids (e.g. from a
     * rolled-back app version) are dropped. If the result has fewer than 4 actions, it
     * is padded with items from [QuickStartAction.defaults] (in defaults order, skipping
     * duplicates) so the grid always shows exactly 4 shortcuts. */
    fun observeQuickStartActions(): Flow<List<QuickStartAction>> =
        context.userPrefsDataStore.data
            .map { prefs ->
                val raw = prefs[KEY_QUICK_START_ORDER]
                val parsed = raw
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.mapNotNull { QuickStartAction.fromPersistedId(it) }
                    ?: emptyList()
                if (parsed.size >= 4) {
                    parsed
                } else {
                    val result = parsed.toMutableList()
                    for (default in QuickStartAction.defaults) {
                        if (result.size >= 4) break
                        if (default !in result) result.add(default)
                    }
                    result
                }
            }
            .catch { emit(QuickStartAction.defaults) }

    /** Persists the chosen Quick Start actions as an ordered persistedId string. */
    suspend fun saveQuickStartActions(actions: List<QuickStartAction>) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[KEY_QUICK_START_ORDER] = actions.joinToString(",") { it.persistedId }
        }
    }

    // ── Home dashboard: account nudge dismissal cooldown ───────────────────────

    /** Records the current time as the most recent account-nudge dismissal. */
    suspend fun dismissAccountNudge() {
        context.userPrefsDataStore.edit {
            it[KEY_ACCOUNT_NUDGE_DISMISSED_AT] = System.currentTimeMillis()
        }
    }

    /** Emits true while the account nudge is still within its 48-hour cooldown. */
    fun isNudgeCoolingDown(): Flow<Boolean> =
        context.userPrefsDataStore.data
            .map { prefs ->
                val dismissedAt = prefs[KEY_ACCOUNT_NUDGE_DISMISSED_AT] ?: 0L
                System.currentTimeMillis() - dismissedAt < ACCOUNT_NUDGE_COOLDOWN_MS
            }
            .catch { emit(false) }

    // ── Home widget layout ────────────────────────────────────────────────────
    //
    // The dashboard layout is serialised as ordered "persistedId:SIZE_NAME" tokens
    // joined by ",". Unknown/removed widget ids and unknown size names are silently
    // skipped on decode, so the encoding is forward- and backward-compatible: an
    // older app reading a newer layout simply drops widget types it doesn't know.
    // An empty decode result falls back to the supplied default layout.

    /**
     * Emits the user's persisted dashboard layout, or [defaultLayout] when nothing
     * is stored or every stored token is unrecognisable.
     *
     * @param defaultLayout the auth-appropriate default, supplied by the caller so
     *  this DataStore stays unaware of which default applies.
     */
    fun homeLayoutFlow(defaultLayout: List<WidgetInstance>): Flow<List<WidgetInstance>> =
        context.userPrefsDataStore.data
            .map { prefs ->
                val raw = prefs[KEY_HOME_LAYOUT]
                val parsed = raw
                    ?.split(",")
                    ?.mapNotNull { token -> decodeWidgetToken(token) }
                    ?: emptyList()
                // Deduplicate by persistedId: a corrupt stored string could produce two
                // WidgetInstances with the same key, crashing the LazyVerticalGrid.
                parsed.distinctBy { it.type.persistedId }.ifEmpty { defaultLayout }
            }
            .catch { emit(defaultLayout) }

    /** Persists [layout] as an ordered "persistedId:SIZE_NAME" token string. */
    suspend fun saveHomeLayout(layout: List<WidgetInstance>) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[KEY_HOME_LAYOUT] = layout.joinToString(",") { instance ->
                "${instance.type.persistedId}:${instance.size.name}"
            }
        }
    }

    /** Emits whether the customization coach-mark has already been shown. */
    val homeCoachmarkSeenFlow: Flow<Boolean> = context.userPrefsDataStore.data
        .map { it[KEY_HOME_COACHMARK_SEEN] ?: false }
        .catch { emit(false) }

    /** Marks the customization coach-mark as seen so it never shows again. */
    suspend fun markHomeCoachmarkSeen() {
        context.userPrefsDataStore.edit { it[KEY_HOME_COACHMARK_SEEN] = true }
    }

    /** Decodes a single "persistedId:SIZE_NAME" token, or null when either half is unknown. */
    private fun decodeWidgetToken(token: String): WidgetInstance? {
        val parts = token.trim().split(":")
        if (parts.size != 2) return null
        val type = HomeWidgetType.fromPersistedId(parts[0].trim()) ?: return null
        val size = WidgetSize.entries.firstOrNull { it.name == parts[1].trim() } ?: return null
        return WidgetInstance(type = type, size = size)
    }

    // ── Home dashboard: First Steps carousel ─────────────────────────────────
    //
    // Skipped step IDs are stored as a comma-separated string (same pattern as
    // KEY_QUICK_START_ORDER). Using a plain String rather than StringSet preserves
    // compatibility and keeps encoding consistent with other Home keys in this file.

    /**
     * Emits the set of step IDs the user has explicitly skipped in the First Steps
     * carousel. An empty set means no steps have been skipped yet.
     */
    fun observeSkippedFirstSteps(): Flow<Set<String>> =
        context.userPrefsDataStore.data
            .map { prefs ->
                prefs[KEY_FIRST_STEPS_SKIPPED]
                    ?.split(",")
                    ?.filter { it.isNotBlank() }
                    ?.toSet()
                    ?: emptySet()
            }
            .catch { emit(emptySet()) }

    /**
     * Persists [stepId] as skipped. Idempotent: adding an already-present id is a no-op.
     */
    suspend fun skipFirstStep(stepId: String) {
        context.userPrefsDataStore.edit { prefs ->
            val current = prefs[KEY_FIRST_STEPS_SKIPPED]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toMutableSet()
                ?: mutableSetOf()
            current.add(stepId)
            prefs[KEY_FIRST_STEPS_SKIPPED] = current.joinToString(",")
        }
    }

}
