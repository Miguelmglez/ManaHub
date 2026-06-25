package com.mmg.manahub.core.gamification.domain.catalog

import com.mmg.manahub.core.gamification.domain.QuestPeriod
import com.mmg.manahub.core.gamification.domain.QuestWeightClass
import com.mmg.manahub.core.gamification.domain.XpConfig
import com.mmg.manahub.core.gamification.domain.catalog.QuestCatalog.byId
import com.mmg.manahub.core.gamification.domain.catalog.QuestCatalog.daily
import com.mmg.manahub.core.gamification.domain.catalog.QuestCatalog.templatesByEventType
import com.mmg.manahub.core.gamification.domain.catalog.QuestCatalog.weekly
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import kotlin.reflect.KClass

/**
 * The immutable, code-side catalog of all quest templates (ADR-002, Phase 2).
 *
 * Indexed by event class ([templatesByEventType]) so an event only evaluates its registered templates,
 * never a full scan (ADR-002 §5). [daily] / [weekly] feed the deterministic generator; [byId] resolves
 * a template for UI metadata.
 */
object QuestCatalog {

    private val GAME = ProgressionEvent.GameFinished::class
    private val SURVEY = ProgressionEvent.SurveyCompleted::class
    private val CARDS = ProgressionEvent.CardsAdded::class
    private val SCAN = ProgressionEvent.CardScanned::class
    private val DECK_CREATED = ProgressionEvent.DeckCreated::class
    private val DECK_SAVED = ProgressionEvent.DeckSaved::class
    private val TOURNEY = ProgressionEvent.TournamentCompleted::class
    private val EXPLORE = ProgressionEvent.FeatureExplored::class

    // ── Daily templates (xpReward = dailyQuestClaim) ─────────────────────────────
    val daily: List<QuestTemplate> = listOf(
        QuestTemplate(
            id = "daily_play_game", period = QuestPeriod.DAILY,
            weightClass = QuestWeightClass.ACCESSIBLE, target = 1, xpReward = XpConfig.dailyQuestClaim,
            title = "Take a Seat", description = "Play a game today.",
            emoji = "🎲", reactsTo = setOf(GAME),
            advance = { 1 },
        ),
        QuestTemplate(
            id = "daily_win_game", period = QuestPeriod.DAILY,
            weightClass = QuestWeightClass.STANDARD, target = 1, xpReward = XpConfig.dailyQuestClaim,
            title = "Daily Victory", description = "Win a game today.",
            emoji = "🏆", reactsTo = setOf(GAME),
            advance = { e -> if ((e as ProgressionEvent.GameFinished).isLocalWin) 1 else 0 },
        ),
        QuestTemplate(
            id = "daily_complete_survey", period = QuestPeriod.DAILY,
            weightClass = QuestWeightClass.ACCESSIBLE, target = 1, xpReward = XpConfig.dailyQuestClaim,
            title = "Quick Recap", description = "Complete a post-game survey.",
            emoji = "📊", reactsTo = setOf(SURVEY),
            advance = { 1 },
        ),
        QuestTemplate(
            id = "daily_add_cards", period = QuestPeriod.DAILY,
            weightClass = QuestWeightClass.ACCESSIBLE, target = 3, xpReward = XpConfig.dailyQuestClaim,
            title = "Stock Up", description = "Add 3 cards to your collection.",
            emoji = "📥", reactsTo = setOf(CARDS),
            advance = { e -> (e as ProgressionEvent.CardsAdded).let { it.addedUnique + it.addedCopies } },
        ),
        QuestTemplate(
            id = "daily_scan_cards", period = QuestPeriod.DAILY,
            weightClass = QuestWeightClass.STANDARD, target = 2, xpReward = XpConfig.dailyQuestClaim,
            title = "Scan Patrol", description = "Scan 2 cards.",
            emoji = "📷", reactsTo = setOf(SCAN),
            advance = { e -> (e as ProgressionEvent.CardScanned).count },
        ),
        QuestTemplate(
            id = "daily_build_deck", period = QuestPeriod.DAILY,
            weightClass = QuestWeightClass.ACCESSIBLE, target = 1, xpReward = XpConfig.dailyQuestClaim,
            title = "Tinkerer", description = "Create or save a deck.",
            emoji = "🛠️", reactsTo = setOf(DECK_CREATED, DECK_SAVED),
            advance = { 1 },
        ),
        QuestTemplate(
            id = "daily_explore_deck_doctor", period = QuestPeriod.DAILY,
            weightClass = QuestWeightClass.EXPLORATION, target = 1, xpReward = XpConfig.dailyQuestClaim,
            title = "Doctor's Visit",
            description = "Run a deck through Deck Doctor.",
            emoji = "🩺", reactsTo = setOf(EXPLORE),
            advance = { e -> if ((e as ProgressionEvent.FeatureExplored).featureKey == "deck_doctor") 1 else 0 },
        ),
    )

    // ── Weekly templates (xpReward = weeklyQuestClaim) ───────────────────────────
    val weekly: List<QuestTemplate> = listOf(
        QuestTemplate(
            id = "weekly_win_games", period = QuestPeriod.WEEKLY,
            weightClass = QuestWeightClass.STANDARD, target = 5, xpReward = XpConfig.weeklyQuestClaim,
            title = "On a Roll", description = "Win 5 games this week.",
            emoji = "🔥", reactsTo = setOf(GAME),
            advance = { e -> if ((e as ProgressionEvent.GameFinished).isLocalWin) 1 else 0 },
        ),
        QuestTemplate(
            id = "weekly_play_games", period = QuestPeriod.WEEKLY,
            weightClass = QuestWeightClass.ACCESSIBLE, target = 7, xpReward = XpConfig.weeklyQuestClaim,
            title = "Regular Player", description = "Play 7 games this week.",
            emoji = "🎮", reactsTo = setOf(GAME),
            advance = { 1 },
        ),
        QuestTemplate(
            id = "weekly_complete_surveys", period = QuestPeriod.WEEKLY,
            weightClass = QuestWeightClass.ACCESSIBLE, target = 3, xpReward = XpConfig.weeklyQuestClaim,
            title = "Analyst",
            description = "Complete 3 post-game surveys this week.",
            emoji = "📈", reactsTo = setOf(SURVEY),
            advance = { 1 },
        ),
        QuestTemplate(
            id = "weekly_add_cards", period = QuestPeriod.WEEKLY,
            weightClass = QuestWeightClass.ACCESSIBLE, target = 20, xpReward = XpConfig.weeklyQuestClaim,
            title = "Collector's Week", description = "Add 20 cards to your collection this week.",
            emoji = "📦", reactsTo = setOf(CARDS),
            advance = { e -> (e as ProgressionEvent.CardsAdded).let { it.addedUnique + it.addedCopies } },
        ),
        QuestTemplate(
            id = "weekly_complete_tournament", period = QuestPeriod.WEEKLY,
            weightClass = QuestWeightClass.EXPLORATION, target = 1, xpReward = XpConfig.weeklyQuestClaim,
            title = "Main Event",
            description = "Finish a tournament this week.",
            emoji = "🎯", reactsTo = setOf(TOURNEY),
            advance = { 1 },
        ),
        QuestTemplate(
            id = "weekly_multiplayer_game", period = QuestPeriod.WEEKLY,
            weightClass = QuestWeightClass.STANDARD, target = 1, xpReward = XpConfig.weeklyQuestClaim,
            title = "Pod People",
            description = "Play a game with 4 or more players this week.",
            emoji = "👥", reactsTo = setOf(GAME),
            advance = { e -> if ((e as ProgressionEvent.GameFinished).playerCount >= 4) 1 else 0 },
        ),
    )

    /** Every template, both periods. */
    val all: List<QuestTemplate> = daily + weekly

    /**
     * Catalog indexed by event class (ADR-002 §5). An event evaluates only its registered templates.
     * A template reacting to multiple events appears under each key. Built once.
     */
    val templatesByEventType: Map<KClass<out ProgressionEvent>, List<QuestTemplate>> =
        buildMap<KClass<out ProgressionEvent>, MutableList<QuestTemplate>> {
            all.forEach { template ->
                template.reactsTo.forEach { eventClass ->
                    getOrPut(eventClass) { mutableListOf() }.add(template)
                }
            }
        }

    private val byIdMap: Map<String, QuestTemplate> = all.associateBy { it.id }

    /** All templates for a given [period]. */
    fun forPeriod(period: QuestPeriod): List<QuestTemplate> = when (period) {
        QuestPeriod.DAILY -> daily
        QuestPeriod.WEEKLY -> weekly
    }

    /** Looks up a template by id, or null if unknown (e.g. an instance from an older/newer build). */
    fun byId(id: String): QuestTemplate? = byIdMap[id]
}
