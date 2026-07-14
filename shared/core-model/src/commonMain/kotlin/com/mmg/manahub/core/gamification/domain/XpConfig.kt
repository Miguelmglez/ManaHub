package com.mmg.manahub.core.gamification.domain

import com.mmg.manahub.core.gamification.domain.XpConfig.collectionDailyCapXp
import com.mmg.manahub.core.gamification.domain.XpConfig.deckCreated
import com.mmg.manahub.core.gamification.domain.XpConfig.friendAdded
import com.mmg.manahub.core.gamification.domain.XpConfig.gameLogged
import com.mmg.manahub.core.gamification.domain.XpConfig.tournamentCompleted


/**
 * The single source of truth for every XP value and cap (ADR-002 §6).
 *
 * All tuning happens here. Caps are enforced by [com.mmg.manahub.core.gamification.engine.XpGranter]
 * by summing the relevant window of the XP ledger BEFORE granting — the constants here only
 * declare the limits, they do not enforce them.
 */
object XpConfig {

    // ── Game ─────────────────────────────────────────────────────────────────────
    /** XP for logging a finished game, regardless of outcome. */
    const val gameLogged: Int = 20

    /** Additional XP when the local seat won. Stacks on top of [gameLogged]. */
    const val localWin: Int = 30

    // ── Survey ───────────────────────────────────────────────────────────────────
    /** XP for completing a post-game survey. */
    const val surveyCompleted: Int = 25

    // ── Collection (shared daily cap) ─────────────────────────────────────────────
    /** XP per new unique card added to the collection. */
    const val newUniqueCard: Int = 5

    /** XP per additional copy of an already-owned card. */
    const val additionalCopy: Int = 2

    /** XP per card recognised by the scanner. Counts toward [collectionDailyCapXp]. */
    const val cardScanned: Int = 3

    /** Shared per-local-day XP ceiling for ALL collection-category grants (adds + scans). */
    const val collectionDailyCapXp: Int = 100

    // ── Deck ─────────────────────────────────────────────────────────────────────
    /** XP for creating a new deck. */
    const val deckCreated: Int = 40

    /** Max number of distinct decks that earn [deckCreated] XP per local day. */
    const val maxRewardedDecksPerDay: Int = 3

    // ── Tournament ───────────────────────────────────────────────────────────────
    /** XP for completing a tournament. */
    const val tournamentCompleted: Int = 100

    /** Additional XP when the local player won the tournament. Stacks on [tournamentCompleted]. */
    const val tournamentWon: Int = 75

    // ── Trade ────────────────────────────────────────────────────────────────────
    /** XP for completing a trade. */
    const val tradeCompleted: Int = 50

    // ── Social ───────────────────────────────────────────────────────────────────
    /** XP for adding a friend. */
    const val friendAdded: Int = 30

    /** Max number of distinct friend-adds that earn [friendAdded] XP per local week. */
    const val maxRewardedFriendsPerWeek: Int = 5

    // ── Quests (Phase 2) ─────────────────────────────────────────────────────────
    /** XP for claiming a completed daily quest. */
    const val dailyQuestClaim: Int = 50

    /** XP for claiming a completed weekly quest. */
    const val weeklyQuestClaim: Int = 200

    // ── Daily open ───────────────────────────────────────────────────────────────
    /** XP for the first app open of a local day. */
    const val dailyFirstOpen: Int = 10

    // ── Achievement tier rewards ──────────────────────────────────────────────────
    //
    // Per-tier XP for an unlocked achievement tier, granted ONCE via the ledger key
    // `achievement:{id}:tier:{n}`. The catalog ([AchievementCatalog]) assigns one of these to each
    // tier by difficulty band so all reward magnitudes stay tunable in this one object (ADR-002 §6).
    /** Tier-1 (entry) achievement reward. */
    const val achievementTier1: Int = 50

    /** Tier-2 (intermediate) achievement reward. */
    const val achievementTier2: Int = 150

    /** Tier-3 (mastery) achievement reward. */
    const val achievementTier3: Int = 400

    /** Reward for a one-off (single-tier) achievement of notable difficulty. */
    const val achievementOneShot: Int = 100

    /** Reward for a secret achievement unlock. */
    const val achievementSecret: Int = 200
}
