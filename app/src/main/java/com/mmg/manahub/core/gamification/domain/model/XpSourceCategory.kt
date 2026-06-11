package com.mmg.manahub.core.gamification.domain.model

/**
 * The category an XP grant belongs to. Used to bucket ledger rows so [com.mmg.manahub.core.gamification.engine.XpGranter]
 * can enforce per-category daily/weekly caps, and so the UI can break XP down by source.
 *
 * Stored in `xp_transactions.source_category` as the enum name (a stable string — never
 * persist ordinals).
 */
enum class XpSourceCategory {
    GAME,
    SURVEY,
    COLLECTION,
    DECK,
    TOURNAMENT,
    TRADE,
    SOCIAL,
    QUEST,
    DAILY_OPEN,
    ACHIEVEMENT,
}
