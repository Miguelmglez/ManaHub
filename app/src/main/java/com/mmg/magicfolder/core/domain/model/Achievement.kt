package com.mmg.magicfolder.core.domain.model

enum class AchievementCategory(val label: String) {
    GAMES("Games"),
    COLLECTION("Collection"),
    DECKS("Decks"),
}

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
    val id:            AchievementId,
    val title:         String,
    val description:   String,
    val emoji:         String,
    val unlockedAt:    Long?                 = null,
    val progress:      Float?                = null,   // null = binary achievement
    val progressLabel: String?               = null,
    val category:      AchievementCategory   = AchievementCategory.GAMES,
) {
    val isUnlocked: Boolean get() = unlockedAt != null
}
