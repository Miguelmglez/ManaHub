package com.mmg.manahub.core.gamification.domain.model

/**
 * Top-level grouping for achievements in the catalog and UI (ADR-002, Phase 1).
 *
 * Richer than the old 3-value `core.domain.model.AchievementCategory` (GAMES/COLLECTION/DECKS):
 * the Phase-1 catalog spans surveys, tournaments, social and dedication lines as well. The enum
 * name is a stable code id; [order] drives the display order of category sections in Chunk B's
 * Achievements tab.
 *
 * @param order ascending display order (lower = shown first).
 */
enum class AchievementCategory(val order: Int) {
    COLLECTION(0),
    GAMES(1),
    DECKS(2),
    SURVEYS(3),
    TOURNAMENTS(4),
    SOCIAL(5),
    DEDICATION(6),
}
