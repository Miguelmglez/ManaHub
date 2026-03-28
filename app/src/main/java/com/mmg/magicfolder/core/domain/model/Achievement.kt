package com.mmg.magicfolder.core.domain.model

enum class AchievementId {
    FIRST_WIN,
    WIN_STREAK_3,
    WIN_STREAK_5,
    GAMES_PLAYED_10,
    GAMES_PLAYED_50,
    GAMES_PLAYED_100,
    COLLECTOR_50,
    COLLECTOR_500,
    MYTHIC_OWNER,
    DECK_BUILDER,
    SURVEY_VETERAN,
    HIGH_VALUE_COLLECTION,
    QUICK_VICTORY,
    COMMANDER_KILLER,
    RAINBOW_COLLECTOR,
}

data class Achievement(
    val id:          AchievementId,
    val title:       String,
    val description: String,
    val icon:        String,
    val isUnlocked:  Boolean,
    val progress:    Float = 0f,   // 0..1 for partial progress display
)
