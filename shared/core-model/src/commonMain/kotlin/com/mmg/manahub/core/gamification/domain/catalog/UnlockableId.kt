package com.mmg.manahub.core.gamification.domain.catalog

/**
 * Reserved identifier for a Phase-3 cosmetic/unlockable granted by an achievement.
 *
 * RESERVED — UNUSED in Phase 1. [AchievementDef.unlocks] keeps this type as a forward extension
 * point (titles, badges, avatar frames, level-ring styles per ADR-002 §10). No entitlement-granting
 * logic exists yet; the list is always empty in the Phase-1 catalog. Do NOT add behavior here.
 *
 * @param value stable string id of the unlockable (will key the Phase-3 `entitlements` table).
 */
@kotlin.jvm.JvmInline
value class UnlockableId(val value: String)
