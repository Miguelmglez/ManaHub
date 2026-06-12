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
import com.mmg.manahub.core.domain.model.UserDefinedTag
import com.mmg.manahub.core.domain.model.UserPreferences
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.ui.theme.AppTheme
import com.mmg.manahub.feature.home.presentation.HomeWidgetType
import com.mmg.manahub.feature.home.presentation.QuickStartAction
import com.mmg.manahub.feature.home.presentation.WidgetInstance
import com.mmg.manahub.feature.home.presentation.WidgetSize
import com.mmg.manahub.core.util.PriceFormatter.isEuropeanLocale
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
/** Master gamification switch (XP, levels, achievements, quests). Default: enabled. */
private val KEY_GAMIFICATION_ENABLED = booleanPreferencesKey("gamification_enabled")
/** One-shot flag: true once the Family-A achievement backfill has run (ADR-002 §4). Default: false. */
private val KEY_GAMIFICATION_BACKFILL_DONE = booleanPreferencesKey("gamification_backfill_done")

private val KEY_EMBEDDING_DB_VERSION = intPreferencesKey("hash_db_version")

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

    // ── Feature flags ─────────────────────────────────────────────────────────

    /** Controls whether FCM push notifications are sent and displayed. Default: false (disabled until rollout). */
    val pushNotificationsEnabledFlow: Flow<Boolean> = context.userPrefsDataStore.data
        .map { prefs -> prefs[KEY_PUSH_NOTIFICATIONS_ENABLED] ?: false }
        .catch { emit(false) }

    suspend fun savePushNotificationsEnabled(enabled: Boolean) {
        context.userPrefsDataStore.edit { it[KEY_PUSH_NOTIFICATIONS_ENABLED] = enabled }
    }

    /**
     * Master gamification switch. Default: true (ON). When false, all gamification UI
     * (XP, levels, achievements, quests) is hidden — the engine keeps recording progress
     * silently so re-enabling restores the user's true state (ADR-002 §"opt-out first-class").
     */
    val gamificationEnabledFlow: Flow<Boolean> = context.userPrefsDataStore.data
        .map { prefs -> prefs[KEY_GAMIFICATION_ENABLED] ?: true }
        .catch { emit(true) }

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
