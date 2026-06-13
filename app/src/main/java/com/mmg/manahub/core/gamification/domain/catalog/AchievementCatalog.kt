package com.mmg.manahub.core.gamification.domain.catalog

import com.mmg.manahub.R
import com.mmg.manahub.core.gamification.domain.XpConfig
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.domain.model.AchievementCategory
import kotlin.reflect.KClass

/**
 * The immutable, code-side catalog of all achievements (ADR-002, Phase 1).
 *
 * ### Stable ids
 * The 15 ids migrated from the old `core.domain.model.AchievementId` enum KEEP their exact enum-name
 * strings (FIRST_WIN, WIN_STREAK_3, WIN_STREAK_5, GAMES_PLAYED_10/50/100, COLLECTOR_50/500,
 * MYTHIC_OWNER, DECK_BUILDER, SURVEY_VETERAN, HIGH_VALUE_COLLECTION, QUICK_VICTORY, COMMANDER_KILLER,
 * RAINBOW_COLLECTOR). These are persisted PKs in `achievement_progress` — NEVER rename them.
 *
 * ### Performance (ADR-002 §2/§5)
 * [defsByEventType] indexes the catalog by event class so an event only evaluates its registered
 * defs — never a full catalog scan. Built ONCE at class init.
 *
 * ### Families
 * - DERIVED (Family A) defs declare an [AchievementResolver]; their progress is re-queried from Room
 *   and supports retroactive unlocks + the one-shot backfill.
 * - COUNTER (Family B) defs (win streaks, daily streak, and the remote-backed social/tournament
 *   lines that have no clean local aggregate) advance only by event increments going forward.
 */
object AchievementCatalog {

    // Event-class shorthands for readability.
    private val GAME = ProgressionEvent.GameFinished::class
    private val SURVEY = ProgressionEvent.SurveyCompleted::class
    private val CARDS = ProgressionEvent.CardsAdded::class
    private val SCAN = ProgressionEvent.CardScanned::class
    private val DECK_CREATED = ProgressionEvent.DeckCreated::class
    private val TOURNEY = ProgressionEvent.TournamentCompleted::class
    private val TRADE = ProgressionEvent.TradeCompleted::class
    private val FRIEND = ProgressionEvent.FriendAdded::class
    private val APP_OPEN = ProgressionEvent.AppOpenedToday::class

    /** Any collection-touching event re-evaluates collection achievements. */
    private val COLLECTION_EVENTS: Set<KClass<out ProgressionEvent>> = setOf(CARDS, SCAN)

    /**
     * The full catalog. Order here is irrelevant (the UI sorts by [AchievementCategory.order] and
     * tier); [defsByEventType] is the hot path.
     */
    val all: List<AchievementDef> = buildList {

        // ── COLLECTION ──────────────────────────────────────────────────────────
        add(
            AchievementDef(
                id = "CARDS_OWNED", category = AchievementCategory.COLLECTION,
                titleRes = R.string.achievement_hoarder, descRes = R.string.achievement_hoarder_desc,
                emoji = "📦", // 📦
                tiers = listOf(
                    AchievementTier(100, XpConfig.achievementTier1),
                    AchievementTier(1_000, XpConfig.achievementTier2),
                    AchievementTier(5_000, XpConfig.achievementTier3),
                ),
                reactsTo = COLLECTION_EVENTS, family = Family.DERIVED,
                resolver = AchievementResolver.CARDS_OWNED,
            )
        )
        // COLLECTOR_50 / COLLECTOR_500 keep their stable ids and form the unique-cards line
        // (50/500) alongside a higher COMPLETIONIST 2,000 tier under a separate id.
        add(
            AchievementDef(
                id = "COLLECTOR_50", category = AchievementCategory.COLLECTION,
                titleRes = R.string.achievement_collector_1, descRes = R.string.achievement_collector_1_desc,
                emoji = "📚", // 📚
                tiers = listOf(AchievementTier(50, XpConfig.achievementTier1)),
                reactsTo = COLLECTION_EVENTS, family = Family.DERIVED,
                resolver = AchievementResolver.UNIQUE_CARDS,
            )
        )
        add(
            AchievementDef(
                id = "COLLECTOR_500", category = AchievementCategory.COLLECTION,
                titleRes = R.string.achievement_collector_3, descRes = R.string.achievement_collector_3_desc,
                emoji = "📖", // 📖
                tiers = listOf(AchievementTier(500, XpConfig.achievementTier2)),
                reactsTo = COLLECTION_EVENTS, family = Family.DERIVED,
                resolver = AchievementResolver.UNIQUE_CARDS,
            )
        )
        add(
            AchievementDef(
                id = "UNIQUE_CARDS_2000", category = AchievementCategory.COLLECTION,
                titleRes = R.string.achievement_completionist, descRes = R.string.achievement_completionist_desc,
                emoji = "🏛", // 🏛
                tiers = listOf(AchievementTier(2_000, XpConfig.achievementTier3)),
                reactsTo = COLLECTION_EVENTS, family = Family.DERIVED,
                resolver = AchievementResolver.UNIQUE_CARDS,
            )
        )
        add(
            AchievementDef(
                id = "FOIL_COLLECTOR", category = AchievementCategory.COLLECTION,
                titleRes = R.string.achievement_foil_fan, descRes = R.string.achievement_foil_fan_desc,
                emoji = "✨", // ✨
                tiers = listOf(
                    AchievementTier(10, XpConfig.achievementTier1),
                    AchievementTier(50, XpConfig.achievementTier2),
                ),
                reactsTo = COLLECTION_EVENTS, family = Family.DERIVED,
                resolver = AchievementResolver.FOIL_CARDS,
            )
        )
        add(
            AchievementDef(
                id = "RAINBOW_COLLECTOR", category = AchievementCategory.COLLECTION,
                titleRes = R.string.achievement_five_colors, descRes = R.string.achievement_five_colors_desc,
                emoji = "🌈", // 🌈
                tiers = listOf(AchievementTier(5, XpConfig.achievementOneShot)),
                reactsTo = COLLECTION_EVENTS, family = Family.DERIVED,
                resolver = AchievementResolver.COLORS_WITH_20_PLUS,
            )
        )
        add(
            AchievementDef(
                id = "MYTHIC_OWNER", category = AchievementCategory.COLLECTION,
                titleRes = R.string.achievement_mythic_hunter, descRes = R.string.achievement_mythic_hunter_desc,
                emoji = "🌟", // 🌟
                tiers = listOf(
                    AchievementTier(1, XpConfig.achievementTier1),
                    AchievementTier(10, XpConfig.achievementTier2),
                ),
                reactsTo = COLLECTION_EVENTS, family = Family.DERIVED,
                resolver = AchievementResolver.MYTHIC_CARDS,
            )
        )
        add(
            AchievementDef(
                id = "HIGH_VALUE_COLLECTION", category = AchievementCategory.COLLECTION,
                titleRes = R.string.achievement_high_roller, descRes = R.string.achievement_high_roller_desc,
                emoji = "💎", // 💎
                // Threshold in whole USD (resolver floors the max card price to an Int).
                tiers = listOf(AchievementTier(60, XpConfig.achievementOneShot)),
                reactsTo = COLLECTION_EVENTS, family = Family.DERIVED,
                resolver = AchievementResolver.MAX_CARD_VALUE_USD,
            )
        )

        // ── GAMES ────────────────────────────────────────────────────────────────
        // GAMES_PLAYED_10/50/100 keep their stable ids as single-tier entries; a 500 tier is added
        // under a separate id so no existing id is collapsed.
        add(gamesPlayed("GAMES_PLAYED_10", 10, XpConfig.achievementTier1))
        add(gamesPlayed("GAMES_PLAYED_50", 50, XpConfig.achievementTier2))
        add(gamesPlayed("GAMES_PLAYED_100", 100, XpConfig.achievementTier3))
        add(gamesPlayed("GAMES_PLAYED_500", 500, XpConfig.achievementTier3))

        // FIRST_WIN keeps its id as the 1-win entry; VICTOR is the higher tiered wins line.
        add(
            AchievementDef(
                id = "FIRST_WIN", category = AchievementCategory.GAMES,
                titleRes = R.string.achievement_first_blood, descRes = R.string.achievement_first_blood_desc,
                emoji = "⚔️", // ⚔️
                tiers = listOf(AchievementTier(1, XpConfig.achievementTier1)),
                reactsTo = setOf(GAME), family = Family.DERIVED,
                resolver = AchievementResolver.LOCAL_WINS,
            )
        )
        add(
            AchievementDef(
                id = "WINS_TIERED", category = AchievementCategory.GAMES,
                titleRes = R.string.achievement_victor, descRes = R.string.achievement_victor_desc,
                emoji = "🏆", // 🏆
                tiers = listOf(
                    AchievementTier(10, XpConfig.achievementTier1),
                    AchievementTier(50, XpConfig.achievementTier2),
                    AchievementTier(200, XpConfig.achievementTier3),
                ),
                reactsTo = setOf(GAME), family = Family.DERIVED,
                resolver = AchievementResolver.LOCAL_WINS,
            )
        )
        // Win streaks are temporal → Family B (cannot be re-derived from Room once broken).
        add(
            AchievementDef(
                id = "WIN_STREAK_3", category = AchievementCategory.GAMES,
                titleRes = R.string.achievement_on_fire, descRes = R.string.achievement_on_fire_desc,
                emoji = "🔥", // 🔥
                tiers = listOf(AchievementTier(3, XpConfig.achievementTier1)),
                reactsTo = setOf(GAME), family = Family.COUNTER,
            )
        )
        add(
            AchievementDef(
                id = "WIN_STREAK_5", category = AchievementCategory.GAMES,
                titleRes = R.string.achievement_on_fire, descRes = R.string.achievement_on_fire_desc,
                emoji = "🔥", // 🔥
                tiers = listOf(AchievementTier(5, XpConfig.achievementTier2)),
                reactsTo = setOf(GAME), family = Family.COUNTER,
            )
        )
        add(
            AchievementDef(
                id = "WIN_STREAK_10", category = AchievementCategory.GAMES,
                titleRes = R.string.achievement_on_fire, descRes = R.string.achievement_on_fire_desc,
                emoji = "🔥", // 🔥
                tiers = listOf(AchievementTier(10, XpConfig.achievementTier3)),
                reactsTo = setOf(GAME), family = Family.COUNTER,
            )
        )
        add(
            AchievementDef(
                id = "COMEBACK_WIN", category = AchievementCategory.GAMES,
                titleRes = R.string.achievement_comeback_kid, descRes = R.string.achievement_comeback_kid_desc,
                emoji = "🛡", // 🛡
                tiers = listOf(AchievementTier(1, XpConfig.achievementOneShot)),
                reactsTo = setOf(GAME), family = Family.DERIVED,
                resolver = AchievementResolver.COMEBACK_WINS,
            )
        )
        add(
            AchievementDef(
                id = "MARATHON", category = AchievementCategory.GAMES,
                titleRes = R.string.achievement_marathoner, descRes = R.string.achievement_marathoner_desc,
                emoji = "⏱", // ⏱
                tiers = listOf(AchievementTier(1, XpConfig.achievementOneShot)),
                reactsTo = setOf(GAME), family = Family.DERIVED,
                resolver = AchievementResolver.MARATHON_GAMES,
            )
        )
        add(
            AchievementDef(
                id = "COMMANDER_KILLER", category = AchievementCategory.GAMES,
                titleRes = R.string.achievement_commander_wrath, descRes = R.string.achievement_commander_wrath_desc,
                emoji = "👑", // 👑
                tiers = listOf(AchievementTier(10, XpConfig.achievementTier2)),
                reactsTo = setOf(GAME), family = Family.DERIVED,
                resolver = AchievementResolver.COMMANDER_WINS,
            )
        )
        add(
            AchievementDef(
                id = "MULTIPLAYER", category = AchievementCategory.GAMES,
                titleRes = R.string.achievement_full_house, descRes = R.string.achievement_full_house_desc,
                emoji = "🎲", // 🎲
                tiers = listOf(AchievementTier(1, XpConfig.achievementTier1)),
                reactsTo = setOf(GAME), family = Family.DERIVED,
                resolver = AchievementResolver.MULTIPLAYER_GAMES,
            )
        )
        add(
            AchievementDef(
                id = "QUICK_VICTORY", category = AchievementCategory.GAMES,
                titleRes = R.string.achievement_aggro_master, descRes = R.string.achievement_aggro_master_desc,
                emoji = "⚡", // ⚡
                tiers = listOf(AchievementTier(1, XpConfig.achievementTier1)),
                reactsTo = setOf(GAME), family = Family.DERIVED,
                resolver = AchievementResolver.QUICK_WINS,
            )
        )

        // ── DECKS ──────────────────────────────────────────────────────────────
        // DECK_BUILDER keeps its id as the "first deck" entry; a tiered Master Builder line adds 5/20.
        add(
            AchievementDef(
                id = "DECK_BUILDER", category = AchievementCategory.DECKS,
                titleRes = R.string.achievement_deck_builder, descRes = R.string.achievement_deck_builder_desc,
                emoji = "🛠️", // 🛠️
                tiers = listOf(AchievementTier(1, XpConfig.achievementTier1)),
                reactsTo = setOf(DECK_CREATED), family = Family.DERIVED,
                resolver = AchievementResolver.DECKS_BUILT,
            )
        )
        add(
            AchievementDef(
                id = "DECKS_TIERED", category = AchievementCategory.DECKS,
                titleRes = R.string.achievement_master_builder, descRes = R.string.achievement_master_builder_desc,
                emoji = "🔧", // 🔧
                tiers = listOf(
                    AchievementTier(5, XpConfig.achievementTier2),
                    AchievementTier(20, XpConfig.achievementTier3),
                ),
                reactsTo = setOf(DECK_CREATED), family = Family.DERIVED,
                resolver = AchievementResolver.DECKS_BUILT,
            )
        )
        add(
            AchievementDef(
                id = "FORMAT_EXPLORER", category = AchievementCategory.DECKS,
                titleRes = R.string.achievement_format_explorer, descRes = R.string.achievement_format_explorer_desc,
                emoji = "🗺", // 🗺
                tiers = listOf(AchievementTier(3, XpConfig.achievementTier2)),
                reactsTo = setOf(DECK_CREATED), family = Family.DERIVED,
                resolver = AchievementResolver.DISTINCT_DECK_FORMATS,
            )
        )

        // ── SURVEYS ──────────────────────────────────────────────────────────────
        // SURVEY_VETERAN keeps its id as the tiered surveys line (5/25/100).
        add(
            AchievementDef(
                id = "SURVEY_VETERAN", category = AchievementCategory.SURVEYS,
                titleRes = R.string.achievement_analyst, descRes = R.string.achievement_analyst_desc,
                emoji = "📊", // 📊
                tiers = listOf(
                    AchievementTier(5, XpConfig.achievementTier1),
                    AchievementTier(25, XpConfig.achievementTier2),
                    AchievementTier(100, XpConfig.achievementTier3),
                ),
                reactsTo = setOf(SURVEY), family = Family.DERIVED,
                resolver = AchievementResolver.SURVEYS_COMPLETED,
            )
        )

        // ── TOURNAMENTS ──────────────────────────────────────────────────────────
        // Tournaments are remote-backed with no clean local aggregate → Family B counters.
        add(
            AchievementDef(
                id = "TOURNAMENT_FIRST", category = AchievementCategory.TOURNAMENTS,
                titleRes = R.string.achievement_competitor, descRes = R.string.achievement_competitor_desc,
                emoji = "🎯", // 🎯
                tiers = listOf(AchievementTier(1, XpConfig.achievementTier1)),
                reactsTo = setOf(TOURNEY), family = Family.COUNTER,
            )
        )
        add(
            AchievementDef(
                id = "TOURNAMENT_5", category = AchievementCategory.TOURNAMENTS,
                titleRes = R.string.achievement_circuit_regular, descRes = R.string.achievement_circuit_regular_desc,
                emoji = "🏅", // 🏅
                tiers = listOf(AchievementTier(5, XpConfig.achievementTier2)),
                reactsTo = setOf(TOURNEY), family = Family.COUNTER,
            )
        )
        // NOTE: TournamentCompleted.isLocalWinner is currently HARD-CODED false (ADR-002 §"Phase 0
        // implementation outcomes" / memory project_gamification_phase0): tournaments have no local-seat
        // concept yet. This def is harmless but will NOT unlock until a tournament local-seat flag is
        // added. The evaluator only increments TOURNAMENT_WIN when event.isLocalWinner is true.
        add(
            AchievementDef(
                id = "TOURNAMENT_WIN", category = AchievementCategory.TOURNAMENTS,
                titleRes = R.string.achievement_tournament_champion, descRes = R.string.achievement_tournament_champion_desc,
                emoji = "👑", // 👑
                tiers = listOf(AchievementTier(1, XpConfig.achievementTier3)),
                reactsTo = setOf(TOURNEY), family = Family.COUNTER,
            )
        )

        // ── SOCIAL (account-gated, remote-backed → Family B) ──────────────────────
        add(
            AchievementDef(
                id = "FIRST_FRIEND", category = AchievementCategory.SOCIAL,
                titleRes = R.string.achievement_first_friend, descRes = R.string.achievement_first_friend_desc,
                emoji = "🤝", // 🤝
                tiers = listOf(AchievementTier(1, XpConfig.achievementTier1)),
                reactsTo = setOf(FRIEND), family = Family.COUNTER,
            )
        )
        add(
            AchievementDef(
                id = "FRIENDS_5", category = AchievementCategory.SOCIAL,
                titleRes = R.string.achievement_social_butterfly, descRes = R.string.achievement_social_butterfly_desc,
                emoji = "🦋", // 🦋
                tiers = listOf(AchievementTier(5, XpConfig.achievementTier2)),
                reactsTo = setOf(FRIEND), family = Family.COUNTER,
            )
        )
        add(
            AchievementDef(
                id = "FIRST_TRADE", category = AchievementCategory.SOCIAL,
                titleRes = R.string.achievement_first_trade, descRes = R.string.achievement_first_trade_desc,
                emoji = "🔄", // 🔄
                tiers = listOf(AchievementTier(1, XpConfig.achievementTier1)),
                reactsTo = setOf(TRADE), family = Family.COUNTER,
            )
        )
        add(
            AchievementDef(
                id = "TRADES_10", category = AchievementCategory.SOCIAL,
                titleRes = R.string.achievement_trade_baron, descRes = R.string.achievement_trade_baron_desc,
                emoji = "💰", // 💰
                tiers = listOf(AchievementTier(10, XpConfig.achievementTier3)),
                reactsTo = setOf(TRADE), family = Family.COUNTER,
            )
        )

        // ── DEDICATION (daily streak) ─────────────────────────────────────────────
        // Family B counters that read the StreakTracker. StreakTracker is a Phase-2 STUB; these
        // defs are defined now but their counter stays 0 until Phase 2 wires the daily streak — so
        // they will NOT advance in Phase 1. (Defined here so the catalog is complete + Chunk B can
        // render the section.) They react to APP_OPEN so they re-evaluate once Phase 2 ships.
        add(
            AchievementDef(
                id = "STREAK_3", category = AchievementCategory.DEDICATION,
                titleRes = R.string.achievement_dedicated, descRes = R.string.achievement_dedicated_desc,
                emoji = "📅", // 📅
                tiers = listOf(AchievementTier(3, XpConfig.achievementTier1)),
                reactsTo = setOf(APP_OPEN), family = Family.COUNTER,
            )
        )
        add(
            AchievementDef(
                id = "STREAK_7", category = AchievementCategory.DEDICATION,
                titleRes = R.string.achievement_dedicated, descRes = R.string.achievement_dedicated_desc,
                emoji = "📅", // 📅
                tiers = listOf(AchievementTier(7, XpConfig.achievementTier2)),
                reactsTo = setOf(APP_OPEN), family = Family.COUNTER,
            )
        )
        add(
            AchievementDef(
                id = "STREAK_30", category = AchievementCategory.DEDICATION,
                titleRes = R.string.achievement_dedicated, descRes = R.string.achievement_dedicated_desc,
                emoji = "📅", // 📅
                tiers = listOf(AchievementTier(30, XpConfig.achievementTier3)),
                reactsTo = setOf(APP_OPEN), family = Family.COUNTER,
            )
        )

        // ── SECRET ─────────────────────────────────────────────────────────────────
        add(
            AchievementDef(
                id = "SECRET_ONE_LIFE_WIN", category = AchievementCategory.GAMES,
                titleRes = R.string.achievement_secret_one_life, descRes = R.string.achievement_secret_one_life_desc,
                emoji = "💀", // 💀
                tiers = listOf(AchievementTier(1, XpConfig.achievementSecret)),
                reactsTo = setOf(GAME), family = Family.DERIVED,
                resolver = AchievementResolver.GAMES_ENDED_AT_ONE_LIFE,
                isSecret = true,
            )
        )
        // Second secret: derivable from the same COLORS_WITH_20_PLUS aggregate at the full rainbow (5).
        add(
            AchievementDef(
                id = "SECRET_PERFECT_RAINBOW", category = AchievementCategory.COLLECTION,
                titleRes = R.string.achievement_secret_perfect_rainbow,
                descRes = R.string.achievement_secret_perfect_rainbow_desc,
                emoji = "🔮", // 🔮
                tiers = listOf(AchievementTier(5, XpConfig.achievementSecret)),
                reactsTo = COLLECTION_EVENTS, family = Family.DERIVED,
                resolver = AchievementResolver.COLORS_WITH_20_PLUS,
                isSecret = true,
            )
        )
    }

    /**
     * Catalog indexed by event type (ADR-002 §2/§5). An event only evaluates the defs registered for
     * its class — built once, here. Defs that react to multiple events appear under each key.
     */
    val defsByEventType: Map<KClass<out ProgressionEvent>, List<AchievementDef>> =
        buildMap<KClass<out ProgressionEvent>, MutableList<AchievementDef>> {
            all.forEach { def ->
                def.reactsTo.forEach { eventClass ->
                    getOrPut(eventClass) { mutableListOf() }.add(def)
                }
            }
        }

    /** Looks up a def by id, or null if unknown (e.g. a row from an older/newer build). */
    fun byId(id: String): AchievementDef? = byIdMap[id]

    private val byIdMap: Map<String, AchievementDef> = all.associateBy { it.id }

    /** Shared builder for the (non-tiered, single-threshold) games-played stable entries. */
    private fun gamesPlayed(id: String, threshold: Int, xp: Int): AchievementDef = AchievementDef(
        id = id, category = AchievementCategory.GAMES,
        titleRes = R.string.achievement_seasoned, descRes = R.string.achievement_seasoned_desc,
        emoji = "🎮", // 🎮
        tiers = listOf(AchievementTier(threshold, xp)),
        reactsTo = setOf(GAME), family = Family.DERIVED,
        resolver = AchievementResolver.GAMES_PLAYED,
    )
}
