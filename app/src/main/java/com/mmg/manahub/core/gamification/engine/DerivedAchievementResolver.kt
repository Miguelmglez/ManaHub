package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.data.local.dao.GamificationStatsDao
import com.mmg.manahub.core.gamification.domain.catalog.AchievementResolver
import kotlin.math.floor

/**
 * The single mapping from a DERIVED [AchievementResolver] to its Room aggregate (drift audit G-23),
 * shared by [AchievementEvaluator] and [AchievementBackfill] so the live and retroactive paths can
 * never disagree on a threshold.
 */
class DerivedAchievementResolver(private val statsDao: GamificationStatsDao) {

    /** Returns the current aggregate value for [resolver]. */
    suspend fun resolve(resolver: AchievementResolver): Int = when (resolver) {
        AchievementResolver.CARDS_OWNED -> statsDao.totalCardsOwned()
        AchievementResolver.UNIQUE_CARDS -> statsDao.uniqueCardsOwned()
        AchievementResolver.FOIL_CARDS -> statsDao.foilCardsOwned()
        AchievementResolver.COLORS_WITH_20_PLUS -> colorsWithAtLeast(RAINBOW_SECRET_MIN_PER_COLOR)
        AchievementResolver.COLORS_WITH_ANY -> colorsWithAtLeast(1)
        AchievementResolver.MYTHIC_CARDS -> statsDao.mythicCardsOwned()
        AchievementResolver.MAX_CARD_VALUE_USD -> floor(statsDao.maxCardValueUsd()).toInt()
        AchievementResolver.GAMES_PLAYED -> statsDao.totalGames()
        AchievementResolver.LOCAL_WINS -> statsDao.localWins()
        AchievementResolver.QUICK_WINS -> statsDao.quickLocalWins(QUICK_WIN_MAX_TURNS)
        AchievementResolver.COMEBACK_WINS -> statsDao.comebackLocalWins(COMEBACK_MAX_LIFE)
        AchievementResolver.MARATHON_GAMES -> statsDao.marathonGames(MARATHON_MIN_MS)
        AchievementResolver.COMMANDER_WINS -> statsDao.commanderLocalWins()
        AchievementResolver.MULTIPLAYER_GAMES -> statsDao.multiplayerGames(MULTIPLAYER_MIN_PLAYERS)
        AchievementResolver.DECKS_BUILT -> statsDao.decksBuilt()
        AchievementResolver.DISTINCT_DECK_FORMATS -> statsDao.distinctDeckFormats()
        AchievementResolver.SURVEYS_COMPLETED -> statsDao.surveysCompleted()
        AchievementResolver.GAMES_ENDED_AT_ONE_LIFE -> statsDao.localWinsAtExactLife(ONE_LIFE)
        AchievementResolver.PUZZLES_SOLVED -> statsDao.puzzlesSolved()
        AchievementResolver.FRIENDS_COUNT -> statsDao.friendsCount()
    }

    private suspend fun colorsWithAtLeast(minCards: Int): Int =
        WUBRG.count { color -> statsDao.ownedCountForColor(color) >= minCards }

    /** Thresholds the catalog copy describes; change them together with `AchievementCatalog`. */
    companion object {
        /** The five MTG color tokens used by the rainbow resolvers. */
        val WUBRG: List<String> = listOf("W", "U", "B", "R", "G")

        /** "Win a game in less than 8 turns". */
        const val QUICK_WIN_MAX_TURNS = 7

        /** "Win a game from 5 or less life". */
        const val COMEBACK_MAX_LIFE = 5

        /** "Play a game lasting 90 minutes or more". */
        const val MARATHON_MIN_MS = 90L * 60_000L

        /** "Play a game with 4 or more players". */
        const val MULTIPLAYER_MIN_PLAYERS = 4

        /** "Win a game with exactly 1 life remaining". */
        const val ONE_LIFE = 1

        /** "Own 20+ cards in each of the 5 colors". */
        const val RAINBOW_SECRET_MIN_PER_COLOR = 20
    }
}
