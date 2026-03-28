package com.mmg.magicfolder.core.domain.usecase.achievements

import com.mmg.magicfolder.core.domain.model.Achievement
import com.mmg.magicfolder.core.domain.model.AchievementId
import javax.inject.Inject
import javax.inject.Singleton

data class AchievementStats(
    val totalGames:          Int,
    val totalWins:           Int,
    val winStreak:           Int,
    val totalCards:          Int,
    val hasMythic:           Boolean,
    val deckCount:           Int,
    val surveyCount:         Int,
    val maxCardValueUsd:     Double,
    val avgWinTurn:          Double,
    val favoriteElimination: String,
    val distinctColorCount:  Int,
)

@Singleton
class CheckAchievementsUseCase @Inject constructor() {

    operator fun invoke(stats: AchievementStats): List<Achievement> = listOf(
        Achievement(
            id          = AchievementId.FIRST_WIN,
            title       = "First Victory",
            description = "Win your first game",
            icon        = "🏆",
            isUnlocked  = stats.totalWins >= 1,
            progress    = (stats.totalWins.coerceAtMost(1)).toFloat(),
        ),
        Achievement(
            id          = AchievementId.WIN_STREAK_3,
            title       = "On a Roll",
            description = "Win 3 games in a row",
            icon        = "🔥",
            isUnlocked  = stats.winStreak >= 3,
            progress    = (stats.winStreak / 3f).coerceAtMost(1f),
        ),
        Achievement(
            id          = AchievementId.WIN_STREAK_5,
            title       = "Unstoppable",
            description = "Win 5 games in a row",
            icon        = "⚡",
            isUnlocked  = stats.winStreak >= 5,
            progress    = (stats.winStreak / 5f).coerceAtMost(1f),
        ),
        Achievement(
            id          = AchievementId.GAMES_PLAYED_10,
            title       = "Getting Started",
            description = "Play 10 games",
            icon        = "🎮",
            isUnlocked  = stats.totalGames >= 10,
            progress    = (stats.totalGames / 10f).coerceAtMost(1f),
        ),
        Achievement(
            id          = AchievementId.GAMES_PLAYED_50,
            title       = "Seasoned Duelist",
            description = "Play 50 games",
            icon        = "⚔",
            isUnlocked  = stats.totalGames >= 50,
            progress    = (stats.totalGames / 50f).coerceAtMost(1f),
        ),
        Achievement(
            id          = AchievementId.GAMES_PLAYED_100,
            title       = "Veteran",
            description = "Play 100 games",
            icon        = "🎖",
            isUnlocked  = stats.totalGames >= 100,
            progress    = (stats.totalGames / 100f).coerceAtMost(1f),
        ),
        Achievement(
            id          = AchievementId.COLLECTOR_50,
            title       = "Budding Collector",
            description = "Add 50 cards to your collection",
            icon        = "📚",
            isUnlocked  = stats.totalCards >= 50,
            progress    = (stats.totalCards / 50f).coerceAtMost(1f),
        ),
        Achievement(
            id          = AchievementId.COLLECTOR_500,
            title       = "Devoted Collector",
            description = "Add 500 cards to your collection",
            icon        = "📖",
            isUnlocked  = stats.totalCards >= 500,
            progress    = (stats.totalCards / 500f).coerceAtMost(1f),
        ),
        Achievement(
            id          = AchievementId.MYTHIC_OWNER,
            title       = "Mythic Rarity",
            description = "Own at least one Mythic Rare",
            icon        = "🌟",
            isUnlocked  = stats.hasMythic,
            progress    = if (stats.hasMythic) 1f else 0f,
        ),
        Achievement(
            id          = AchievementId.DECK_BUILDER,
            title       = "Decksmith",
            description = "Build 3 decks",
            icon        = "🃏",
            isUnlocked  = stats.deckCount >= 3,
            progress    = (stats.deckCount / 3f).coerceAtMost(1f),
        ),
        Achievement(
            id          = AchievementId.SURVEY_VETERAN,
            title       = "Self-Aware",
            description = "Complete 5 post-game surveys",
            icon        = "📝",
            isUnlocked  = stats.surveyCount >= 5,
            progress    = (stats.surveyCount / 5f).coerceAtMost(1f),
        ),
        Achievement(
            id          = AchievementId.HIGH_VALUE_COLLECTION,
            title       = "High Roller",
            description = "Own a card worth over \$20",
            icon        = "💎",
            isUnlocked  = stats.maxCardValueUsd >= 20.0,
            progress    = (stats.maxCardValueUsd / 20.0).toFloat().coerceAtMost(1f),
        ),
        Achievement(
            id          = AchievementId.QUICK_VICTORY,
            title       = "Lightning Strike",
            description = "Win games before turn 8 on average",
            icon        = "⚡",
            isUnlocked  = stats.avgWinTurn in 1.0..8.0,
            progress    = if (stats.avgWinTurn > 0)
                (1f - (stats.avgWinTurn / 20.0).toFloat()).coerceIn(0f, 1f) else 0f,
        ),
        Achievement(
            id          = AchievementId.COMMANDER_KILLER,
            title       = "Commander Slayer",
            description = "Most eliminations via Commander damage",
            icon        = "👑",
            isUnlocked  = stats.favoriteElimination == "COMMANDER_DAMAGE",
            progress    = if (stats.favoriteElimination == "COMMANDER_DAMAGE") 1f else 0f,
        ),
        Achievement(
            id          = AchievementId.RAINBOW_COLLECTOR,
            title       = "Rainbow Mage",
            description = "Collect cards of all 5 colors",
            icon        = "🌈",
            isUnlocked  = stats.distinctColorCount >= 5,
            progress    = (stats.distinctColorCount / 5f).coerceAtMost(1f),
        ),
    )
}
