package com.mmg.magicfolder.core.domain.usecase.achievements

import android.content.Context
import com.mmg.magicfolder.R
import com.mmg.magicfolder.core.domain.model.Achievement
import com.mmg.magicfolder.core.domain.model.AchievementCategory
import com.mmg.magicfolder.core.domain.model.AchievementId
import dagger.hilt.android.qualifiers.ApplicationContext
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

private val NOW get() = System.currentTimeMillis()

@Singleton
class CheckAchievementsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    operator fun invoke(stats: AchievementStats): List<Achievement> = listOf(
        Achievement(
            id            = AchievementId.FIRST_WIN,
            title         = context.getString(R.string.achievement_first_blood),
            description   = context.getString(R.string.achievement_first_blood_desc),
            emoji         = "🏆",
            unlockedAt    = if (stats.totalWins >= 1) NOW else null,
            category      = AchievementCategory.GAMES,
        ),
        Achievement(
            id            = AchievementId.WIN_STREAK_3,
            title         = context.getString(R.string.achievement_on_fire),
            description   = context.getString(R.string.achievement_on_fire_desc),
            emoji         = "🔥",
            unlockedAt    = if (stats.winStreak >= 3) NOW else null,
            progress      = (stats.winStreak / 3f).coerceAtMost(1f),
            progressLabel = "${stats.winStreak.coerceAtMost(3)}/3 streak",
            category      = AchievementCategory.GAMES,
        ),
        Achievement(
            id            = AchievementId.WIN_STREAK_5,
            title         = context.getString(R.string.achievement_champion),
            description   = context.getString(R.string.achievement_champion_desc),
            emoji         = "⚡",
            unlockedAt    = if (stats.winStreak >= 5) NOW else null,
            progress      = (stats.winStreak / 5f).coerceAtMost(1f),
            progressLabel = "${stats.winStreak.coerceAtMost(5)}/5 streak",
            category      = AchievementCategory.GAMES,
        ),
        Achievement(
            id            = AchievementId.GAMES_PLAYED_10,
            title         = context.getString(R.string.achievement_collector_1),
            description   = context.getString(R.string.achievement_collector_1_desc),
            emoji         = "🎮",
            unlockedAt    = if (stats.totalGames >= 10) NOW else null,
            progress      = (stats.totalGames / 10f).coerceAtMost(1f),
            progressLabel = "${stats.totalGames.coerceAtMost(10)}/10 games",
            category      = AchievementCategory.GAMES,
        ),
        Achievement(
            id            = AchievementId.GAMES_PLAYED_50,
            title         = context.getString(R.string.achievement_collector_2),
            description   = context.getString(R.string.achievement_collector_2_desc),
            emoji         = "⚔",
            unlockedAt    = if (stats.totalGames >= 50) NOW else null,
            progress      = (stats.totalGames / 50f).coerceAtMost(1f),
            progressLabel = "${stats.totalGames.coerceAtMost(50)}/50 games",
            category      = AchievementCategory.GAMES,
        ),
        Achievement(
            id            = AchievementId.GAMES_PLAYED_100,
            title         = context.getString(R.string.achievement_collector_3),
            description   = context.getString(R.string.achievement_collector_3_desc),
            emoji         = "🎖",
            unlockedAt    = if (stats.totalGames >= 100) NOW else null,
            progress      = (stats.totalGames / 100f).coerceAtMost(1f),
            progressLabel = "${stats.totalGames.coerceAtMost(100)}/100 games",
            category      = AchievementCategory.GAMES,
        ),
        Achievement(
            id            = AchievementId.COLLECTOR_50,
            title         = context.getString(R.string.achievement_deck_builder),
            description   = context.getString(R.string.achievement_deck_builder_desc),
            emoji         = "📚",
            unlockedAt    = if (stats.totalCards >= 50) NOW else null,
            progress      = (stats.totalCards / 50f).coerceAtMost(1f),
            progressLabel = "${stats.totalCards.coerceAtMost(50)}/50 cards",
            category      = AchievementCategory.COLLECTION,
        ),
        Achievement(
            id            = AchievementId.COLLECTOR_500,
            title         = context.getString(R.string.achievement_dominant),
            description   = context.getString(R.string.achievement_dominant_desc),
            emoji         = "📖",
            unlockedAt    = if (stats.totalCards >= 500) NOW else null,
            progress      = (stats.totalCards / 500f).coerceAtMost(1f),
            progressLabel = "${stats.totalCards.coerceAtMost(500)}/500 cards",
            category      = AchievementCategory.COLLECTION,
        ),
        Achievement(
            id            = AchievementId.MYTHIC_OWNER,
            title         = context.getString(R.string.achievement_mythic_hunter),
            description   = context.getString(R.string.achievement_mythic_hunter_desc),
            emoji         = "🌟",
            unlockedAt    = if (stats.hasMythic) NOW else null,
            category      = AchievementCategory.COLLECTION,
        ),
        Achievement(
            id            = AchievementId.DECK_BUILDER,
            title         = context.getString(R.string.achievement_deck_builder),
            description   = context.getString(R.string.achievement_deck_builder_desc),
            emoji         = "🃏",
            unlockedAt    = if (stats.deckCount >= 3) NOW else null,
            progress      = (stats.deckCount / 3f).coerceAtMost(1f),
            progressLabel = "${stats.deckCount.coerceAtMost(3)}/3 decks",
            category      = AchievementCategory.DECKS,
        ),
        Achievement(
            id            = AchievementId.SURVEY_VETERAN,
            title         = context.getString(R.string.achievement_control_freak),
            description   = context.getString(R.string.achievement_control_freak_desc),
            emoji         = "📝",
            unlockedAt    = if (stats.surveyCount >= 5) NOW else null,
            progress      = (stats.surveyCount / 5f).coerceAtMost(1f),
            progressLabel = "${stats.surveyCount.coerceAtMost(5)}/5 surveys",
            category      = AchievementCategory.DECKS,
        ),
        Achievement(
            id            = AchievementId.HIGH_VALUE_COLLECTION,
            title         = context.getString(R.string.achievement_high_roller),
            description   = context.getString(R.string.achievement_high_roller_desc),
            emoji         = "💎",
            unlockedAt    = if (stats.maxCardValueUsd >= 20.0) NOW else null,
            progress      = (stats.maxCardValueUsd / 20.0).toFloat().coerceAtMost(1f),
            progressLabel = "$${stats.maxCardValueUsd.toInt()}/$20",
            category      = AchievementCategory.COLLECTION,
        ),
        Achievement(
            id            = AchievementId.QUICK_VICTORY,
            title         = context.getString(R.string.achievement_aggro_master),
            description   = context.getString(R.string.achievement_aggro_master_desc),
            emoji         = "⚡",
            unlockedAt    = if (stats.avgWinTurn in 1.0..8.0) NOW else null,
            category      = AchievementCategory.GAMES,
        ),
        Achievement(
            id            = AchievementId.COMMANDER_KILLER,
            title         = context.getString(R.string.achievement_commander_wrath),
            description   = context.getString(R.string.achievement_commander_wrath_desc),
            emoji         = "👑",
            unlockedAt    = if (stats.favoriteElimination == "COMMANDER_DAMAGE") NOW else null,
            category      = AchievementCategory.GAMES,
        ),
        Achievement(
            id            = AchievementId.RAINBOW_COLLECTOR,
            title         = context.getString(R.string.achievement_five_colors),
            description   = context.getString(R.string.achievement_five_colors_desc),
            emoji         = "🌈",
            unlockedAt    = if (stats.distinctColorCount >= 5) NOW else null,
            progress      = (stats.distinctColorCount / 5f).coerceAtMost(1f),
            progressLabel = "${stats.distinctColorCount.coerceAtMost(5)}/5 colors",
            category      = AchievementCategory.COLLECTION,
        ),
    )
}
