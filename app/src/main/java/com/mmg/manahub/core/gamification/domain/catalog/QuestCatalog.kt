package com.mmg.manahub.core.gamification.domain.catalog

import androidx.annotation.StringRes
import com.mmg.manahub.R
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
 * Static definition of one quest template (ADR-002, Phase 2).
 *
 * The catalog ([QuestCatalog]) is a code-side list of these. A concrete
 * [com.mmg.manahub.core.data.local.entity.QuestInstanceEntity] is generated per period from a template
 * (id = `{templateId}:{periodKey}`).
 *
 * ### Pure int counters (deliberate simplification)
 * Every quest is a monotonic integer counter: [advance] returns the progress increment for an event
 * (0 when the event does not apply). There are intentionally NO derived/distinct-mode quests in v1 —
 * keeping the int-progress model clean. Conditional quests (win/multiplayer/featureKey) return 1 or 0;
 * count-based quests return the event's count.
 *
 * @param id STABLE template id (part of the persisted instance PK — NEVER rename).
 * @param period whether this quest rotates daily or weekly.
 * @param weightClass difficulty band, used by the generator's balance rules.
 * @param target value to complete the quest.
 * @param xpReward XP granted on claim (= [XpConfig.dailyQuestClaim] / [XpConfig.weeklyQuestClaim]).
 * @param titleRes / descRes English string resources.
 * @param emoji glyph for the UI.
 * @param reactsTo event classes that can advance this quest.
 * @param advance pure mapping from a matching event to its progress increment (>= 0).
 */
data class QuestTemplate(
    val id: String,
    val period: QuestPeriod,
    val weightClass: QuestWeightClass,
    val target: Int,
    val xpReward: Int,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val emoji: String,
    val reactsTo: Set<KClass<out ProgressionEvent>>,
    val advance: (ProgressionEvent) -> Int,
) {
    init {
        require(target > 0) { "Quest '$id' target must be positive" }
        require(reactsTo.isNotEmpty()) { "Quest '$id' must react to at least one event" }
    }
}

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
            titleRes = R.string.quest_daily_play_game_title, descRes = R.string.quest_daily_play_game_desc,
            emoji = "🎲", reactsTo = setOf(GAME),
            advance = { 1 },
        ),
        QuestTemplate(
            id = "daily_win_game", period = QuestPeriod.DAILY,
            weightClass = QuestWeightClass.STANDARD, target = 1, xpReward = XpConfig.dailyQuestClaim,
            titleRes = R.string.quest_daily_win_game_title, descRes = R.string.quest_daily_win_game_desc,
            emoji = "🏆", reactsTo = setOf(GAME),
            advance = { e -> if ((e as ProgressionEvent.GameFinished).isLocalWin) 1 else 0 },
        ),
        QuestTemplate(
            id = "daily_complete_survey", period = QuestPeriod.DAILY,
            weightClass = QuestWeightClass.ACCESSIBLE, target = 1, xpReward = XpConfig.dailyQuestClaim,
            titleRes = R.string.quest_daily_complete_survey_title, descRes = R.string.quest_daily_complete_survey_desc,
            emoji = "📊", reactsTo = setOf(SURVEY),
            advance = { 1 },
        ),
        QuestTemplate(
            id = "daily_add_cards", period = QuestPeriod.DAILY,
            weightClass = QuestWeightClass.ACCESSIBLE, target = 3, xpReward = XpConfig.dailyQuestClaim,
            titleRes = R.string.quest_daily_add_cards_title, descRes = R.string.quest_daily_add_cards_desc,
            emoji = "📥", reactsTo = setOf(CARDS),
            advance = { e -> (e as ProgressionEvent.CardsAdded).let { it.addedUnique + it.addedCopies } },
        ),
        QuestTemplate(
            id = "daily_scan_cards", period = QuestPeriod.DAILY,
            weightClass = QuestWeightClass.STANDARD, target = 2, xpReward = XpConfig.dailyQuestClaim,
            titleRes = R.string.quest_daily_scan_cards_title, descRes = R.string.quest_daily_scan_cards_desc,
            emoji = "📷", reactsTo = setOf(SCAN),
            advance = { e -> (e as ProgressionEvent.CardScanned).count },
        ),
        QuestTemplate(
            id = "daily_build_deck", period = QuestPeriod.DAILY,
            weightClass = QuestWeightClass.ACCESSIBLE, target = 1, xpReward = XpConfig.dailyQuestClaim,
            titleRes = R.string.quest_daily_build_deck_title, descRes = R.string.quest_daily_build_deck_desc,
            emoji = "🛠️", reactsTo = setOf(DECK_CREATED, DECK_SAVED),
            advance = { 1 },
        ),
        QuestTemplate(
            id = "daily_explore_deck_doctor", period = QuestPeriod.DAILY,
            weightClass = QuestWeightClass.EXPLORATION, target = 1, xpReward = XpConfig.dailyQuestClaim,
            titleRes = R.string.quest_daily_explore_deck_doctor_title,
            descRes = R.string.quest_daily_explore_deck_doctor_desc,
            emoji = "🩺", reactsTo = setOf(EXPLORE),
            advance = { e -> if ((e as ProgressionEvent.FeatureExplored).featureKey == "deck_doctor") 1 else 0 },
        ),
    )

    // ── Weekly templates (xpReward = weeklyQuestClaim) ───────────────────────────
    val weekly: List<QuestTemplate> = listOf(
        QuestTemplate(
            id = "weekly_win_games", period = QuestPeriod.WEEKLY,
            weightClass = QuestWeightClass.STANDARD, target = 5, xpReward = XpConfig.weeklyQuestClaim,
            titleRes = R.string.quest_weekly_win_games_title, descRes = R.string.quest_weekly_win_games_desc,
            emoji = "🔥", reactsTo = setOf(GAME),
            advance = { e -> if ((e as ProgressionEvent.GameFinished).isLocalWin) 1 else 0 },
        ),
        QuestTemplate(
            id = "weekly_play_games", period = QuestPeriod.WEEKLY,
            weightClass = QuestWeightClass.ACCESSIBLE, target = 7, xpReward = XpConfig.weeklyQuestClaim,
            titleRes = R.string.quest_weekly_play_games_title, descRes = R.string.quest_weekly_play_games_desc,
            emoji = "🎮", reactsTo = setOf(GAME),
            advance = { 1 },
        ),
        QuestTemplate(
            id = "weekly_complete_surveys", period = QuestPeriod.WEEKLY,
            weightClass = QuestWeightClass.ACCESSIBLE, target = 3, xpReward = XpConfig.weeklyQuestClaim,
            titleRes = R.string.quest_weekly_complete_surveys_title,
            descRes = R.string.quest_weekly_complete_surveys_desc,
            emoji = "📈", reactsTo = setOf(SURVEY),
            advance = { 1 },
        ),
        QuestTemplate(
            id = "weekly_add_cards", period = QuestPeriod.WEEKLY,
            weightClass = QuestWeightClass.ACCESSIBLE, target = 20, xpReward = XpConfig.weeklyQuestClaim,
            titleRes = R.string.quest_weekly_add_cards_title, descRes = R.string.quest_weekly_add_cards_desc,
            emoji = "📦", reactsTo = setOf(CARDS),
            advance = { e -> (e as ProgressionEvent.CardsAdded).let { it.addedUnique + it.addedCopies } },
        ),
        QuestTemplate(
            id = "weekly_complete_tournament", period = QuestPeriod.WEEKLY,
            weightClass = QuestWeightClass.EXPLORATION, target = 1, xpReward = XpConfig.weeklyQuestClaim,
            titleRes = R.string.quest_weekly_complete_tournament_title,
            descRes = R.string.quest_weekly_complete_tournament_desc,
            emoji = "🎯", reactsTo = setOf(TOURNEY),
            advance = { 1 },
        ),
        QuestTemplate(
            id = "weekly_multiplayer_game", period = QuestPeriod.WEEKLY,
            weightClass = QuestWeightClass.STANDARD, target = 1, xpReward = XpConfig.weeklyQuestClaim,
            titleRes = R.string.quest_weekly_multiplayer_game_title,
            descRes = R.string.quest_weekly_multiplayer_game_desc,
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
