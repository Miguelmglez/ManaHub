package com.mmg.magicfolder.core.domain.usecase.achievements

import android.content.Context
import com.mmg.magicfolder.R
import com.mmg.magicfolder.core.domain.model.Achievement
import com.mmg.magicfolder.core.domain.model.AchievementCategory
import com.mmg.magicfolder.core.domain.model.AchievementId
import com.mmg.magicfolder.core.domain.model.PreferredCurrency
import com.mmg.magicfolder.core.util.PriceFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class AchievementStats(
    val totalGames: Int = 0,
    val totalWins: Int = 0,
    val winStreak: Int = 0,
    val totalCards: Int = 0,
    val hasMythic: Boolean = false,
    val deckCount: Int = 0,
    val surveyCount: Int = 0,
    val maxCardValue: Double = 0.0,
    val avgWinTurn: Double = 0.0,
    val favoriteElimination: String = "",
    val distinctColorCount: Int = 0,
)

private val NOW get() = System.currentTimeMillis()

class CheckAchievementsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(stats: AchievementStats, currency: PreferredCurrency): List<Achievement> {
        val threshold = if (currency == PreferredCurrency.EUR) 50.0 else 60.0
        val thresholdString = PriceFormatter.format(threshold, currency)

        return listOf(
            Achievement(
                id            = AchievementId.FIRST_WIN,
                title         = context.getString(R.string.achievement_first_blood),
                description   = context.getString(R.string.achievement_first_blood_desc),
                emoji         = "⚔️",
                unlockedAt    = if (stats.totalWins >= 1) NOW else null,
                category      = AchievementCategory.GAMES,
            ),
            Achievement(
                id            = AchievementId.WIN_STREAK_5,
                title         = context.getString(R.string.achievement_on_fire),
                description   = context.getString(R.string.achievement_on_fire_desc),
                emoji         = "🔥",
                unlockedAt    = if (stats.winStreak >= 5) NOW else null,
                progress      = (stats.winStreak / 5f).coerceAtMost(1f),
                progressLabel = "${stats.winStreak.coerceAtMost(5)}/5 wins",
                category      = AchievementCategory.GAMES,
            ),
            Achievement(
                id            = AchievementId.COLLECTOR_50,
                title         = context.getString(R.string.achievement_collector_1),
                description   = context.getString(R.string.achievement_collector_1_desc),
                emoji         = "📚",
                unlockedAt    = if (stats.totalCards >= 50) NOW else null,
                progress      = (stats.totalCards / 50f).coerceAtMost(1f),
                progressLabel = "${stats.totalCards.coerceAtMost(50)}/50 cards",
                category      = AchievementCategory.COLLECTION,
            ),
            Achievement(
                id            = AchievementId.COLLECTOR_500,
                title         = context.getString(R.string.achievement_collector_3),
                description   = context.getString(R.string.achievement_collector_3_desc),
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
                emoji         = "🛠️",
                unlockedAt    = if (stats.deckCount >= 1) NOW else null,
                category      = AchievementCategory.DECKS,
            ),
            Achievement(
                id            = AchievementId.HIGH_VALUE_COLLECTION,
                title         = context.getString(R.string.achievement_high_roller),
                description   = context.getString(R.string.achievement_high_roller_desc, thresholdString),
                emoji         = "💎",
                unlockedAt    = if (stats.maxCardValue >= threshold) NOW else null,
                progress      = (stats.maxCardValue / threshold).toFloat().coerceAtMost(1f),
                progressLabel = "Most valuable: ${PriceFormatter.format(stats.maxCardValue, currency)} / $thresholdString",
                category      = AchievementCategory.COLLECTION,
            ),
            Achievement(
                id            = AchievementId.QUICK_VICTORY,
                title         = context.getString(R.string.achievement_aggro_master),
                description   = context.getString(R.string.achievement_aggro_master_desc),
                emoji         = "⚡",
                unlockedAt    = if (stats.avgWinTurn in 1.0..8.0 && stats.totalWins >= 5) NOW else null,
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
}
