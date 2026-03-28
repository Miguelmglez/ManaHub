package com.mmg.magicfolder.core.domain.usecase.achievements

import android.content.Context
import com.mmg.magicfolder.R
import com.mmg.magicfolder.core.domain.model.Achievement
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

@Singleton
class CheckAchievementsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    operator fun invoke(stats: AchievementStats): List<Achievement> = listOf(
        Achievement(
            id          = AchievementId.FIRST_WIN,
            title       = context.getString(R.string.achievement_first_blood),
            description = context.getString(R.string.achievement_first_blood_desc),
            icon        = "🏆",
            isUnlocked  = stats.totalWins >= 1,
            progress    = (stats.totalWins.coerceAtMost(1)).toFloat(),
        ),
        Achievement(
            id          = AchievementId.WIN_STREAK_3,
            title       = context.getString(R.string.achievement_on_fire),
            description = context.getString(R.string.achievement_on_fire_desc),
            icon        = "🔥",
            isUnlocked  = stats.winStreak >= 3,
            progress    = (stats.winStreak / 3f).coerceAtMost(1f),
        ),
        Achievement(
            id          = AchievementId.WIN_STREAK_5,
            title       = context.getString(R.string.achievement_champion),
            description = context.getString(R.string.achievement_champion_desc),
            icon        = "⚡",
            isUnlocked  = stats.winStreak >= 5,
            progress    = (stats.winStreak / 5f).coerceAtMost(1f),
        ),
        Achievement(
            id          = AchievementId.GAMES_PLAYED_10,
            title       = context.getString(R.string.achievement_collector_1),
            description = context.getString(R.string.achievement_collector_1_desc),
            icon        = "🎮",
            isUnlocked  = stats.totalGames >= 10,
            progress    = (stats.totalGames / 10f).coerceAtMost(1f),
        ),
        Achievement(
            id          = AchievementId.GAMES_PLAYED_50,
            title       = context.getString(R.string.achievement_collector_2),
            description = context.getString(R.string.achievement_collector_2_desc),
            icon        = "⚔",
            isUnlocked  = stats.totalGames >= 50,
            progress    = (stats.totalGames / 50f).coerceAtMost(1f),
        ),
        Achievement(
            id          = AchievementId.GAMES_PLAYED_100,
            title       = context.getString(R.string.achievement_collector_3),
            description = context.getString(R.string.achievement_collector_3_desc),
            icon        = "🎖",
            isUnlocked  = stats.totalGames >= 100,
            progress    = (stats.totalGames / 100f).coerceAtMost(1f),
        ),
        Achievement(
            id          = AchievementId.COLLECTOR_50,
            title       = context.getString(R.string.achievement_deck_builder),
            description = context.getString(R.string.achievement_deck_builder_desc),
            icon        = "📚",
            isUnlocked  = stats.totalCards >= 50,
            progress    = (stats.totalCards / 50f).coerceAtMost(1f),
        ),
        Achievement(
            id          = AchievementId.COLLECTOR_500,
            title       = context.getString(R.string.achievement_dominant),
            description = context.getString(R.string.achievement_dominant_desc),
            icon        = "📖",
            isUnlocked  = stats.totalCards >= 500,
            progress    = (stats.totalCards / 500f).coerceAtMost(1f),
        ),
        Achievement(
            id          = AchievementId.MYTHIC_OWNER,
            title       = context.getString(R.string.achievement_mythic_hunter),
            description = context.getString(R.string.achievement_mythic_hunter_desc),
            icon        = "🌟",
            isUnlocked  = stats.hasMythic,
            progress    = if (stats.hasMythic) 1f else 0f,
        ),
        Achievement(
            id          = AchievementId.DECK_BUILDER,
            title       = context.getString(R.string.achievement_deck_builder),
            description = context.getString(R.string.achievement_deck_builder_desc),
            icon        = "🃏",
            isUnlocked  = stats.deckCount >= 3,
            progress    = (stats.deckCount / 3f).coerceAtMost(1f),
        ),
        Achievement(
            id          = AchievementId.SURVEY_VETERAN,
            title       = context.getString(R.string.achievement_control_freak),
            description = context.getString(R.string.achievement_control_freak_desc),
            icon        = "📝",
            isUnlocked  = stats.surveyCount >= 5,
            progress    = (stats.surveyCount / 5f).coerceAtMost(1f),
        ),
        Achievement(
            id          = AchievementId.HIGH_VALUE_COLLECTION,
            title       = context.getString(R.string.achievement_high_roller),
            description = context.getString(R.string.achievement_high_roller_desc),
            icon        = "💎",
            isUnlocked  = stats.maxCardValueUsd >= 20.0,
            progress    = (stats.maxCardValueUsd / 20.0).toFloat().coerceAtMost(1f),
        ),
        Achievement(
            id          = AchievementId.QUICK_VICTORY,
            title       = context.getString(R.string.achievement_aggro_master),
            description = context.getString(R.string.achievement_aggro_master_desc),
            icon        = "⚡",
            isUnlocked  = stats.avgWinTurn in 1.0..8.0,
            progress    = if (stats.avgWinTurn > 0)
                (1f - (stats.avgWinTurn / 20.0).toFloat()).coerceIn(0f, 1f) else 0f,
        ),
        Achievement(
            id          = AchievementId.COMMANDER_KILLER,
            title       = context.getString(R.string.achievement_commander_wrath),
            description = context.getString(R.string.achievement_commander_wrath_desc),
            icon        = "👑",
            isUnlocked  = stats.favoriteElimination == "COMMANDER_DAMAGE",
            progress    = if (stats.favoriteElimination == "COMMANDER_DAMAGE") 1f else 0f,
        ),
        Achievement(
            id          = AchievementId.RAINBOW_COLLECTOR,
            title       = context.getString(R.string.achievement_five_colors),
            description = context.getString(R.string.achievement_five_colors_desc),
            icon        = "🌈",
            isUnlocked  = stats.distinctColorCount >= 5,
            progress    = (stats.distinctColorCount / 5f).coerceAtMost(1f),
        ),
    )
}
