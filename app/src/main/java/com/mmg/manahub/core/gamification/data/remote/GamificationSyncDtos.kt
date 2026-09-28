package com.mmg.manahub.core.gamification.data.remote

import com.mmg.manahub.core.data.local.entity.AchievementProgressEntity
import com.mmg.manahub.core.data.local.entity.EntitlementEntity
import com.mmg.manahub.core.data.local.entity.StreakEntity
import com.mmg.manahub.core.data.local.entity.XpTransactionEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs + mappers for Gamification Phase 4 sync (ADR-002 §11).
 *
 * All timestamps are epoch-millis (`Long`) — the Supabase columns are `bigint`, returned as plain
 * integers (no `Instant` serialization). `user_id` is set SERVER-SIDE by every upsert/merge RPC via
 * `auth.uid()`; upload DTOs therefore OMIT it (mirrors how `batch_upsert_collection` ignores any
 * client-sent user_id). Pull DTOs mirror the keyset-paged `get_*_page` RPCs (G-01), whose rows carry
 * the server-assigned cursor (`server_seq` or `changed_at`) and no `user_id`.
 *
 * Progression is **monotonic** — these DTOs are merged server-side via GREATEST/earliest/union and
 * client-side the same way (see GamificationSyncManager). Never last-write-wins.
 */

// ── XP ledger ────────────────────────────────────────────────────────────────

/**
 * Upload DTO for an `xp_transactions` row. Carries the natural sync key fields ONLY — never the local
 * autoincrement `id` (which is device-local) nor `user_id` (server sets it). The server PK is
 * `(user_id, idempotency_key)` with `ON CONFLICT DO NOTHING`, so re-pushing the same key is a no-op.
 */
@Serializable
data class XpTransactionUploadDto(
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("amount") val amount: Int,
    @SerialName("source_category") val sourceCategory: String,
    @SerialName("source_ref") val sourceRef: String? = null,
    @SerialName("created_at") val createdAt: Long,
)

/**
 * Pull DTO for one `get_xp_transactions_page` row. [serverSeq] is the server-assigned, per-user
 * monotonic cursor: a row pushed late by another device still gets a larger value, so it is pulled.
 */
@Serializable
data class XpTransactionPageDto(
    @SerialName("server_seq") val serverSeq: Long,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("amount") val amount: Int,
    @SerialName("source_category") val sourceCategory: String,
    @SerialName("source_ref") val sourceRef: String? = null,
    @SerialName("created_at") val createdAt: Long,
)

/** Maps a local ledger entity to its upload DTO (drops the local autoincrement id). */
fun XpTransactionEntity.toUploadDto(): XpTransactionUploadDto = XpTransactionUploadDto(
    idempotencyKey = idempotencyKey,
    amount = amount,
    sourceCategory = sourceCategory,
    sourceRef = sourceRef,
    createdAt = createdAt,
)

/**
 * Maps a pulled ledger row to a local entity. `id` stays 0 so Room assigns a fresh local id; dedupe is
 * on the UNIQUE `idempotency_key`.
 */
fun XpTransactionPageDto.toEntity(): XpTransactionEntity = XpTransactionEntity(
    idempotencyKey = idempotencyKey,
    amount = amount,
    sourceCategory = sourceCategory,
    sourceRef = sourceRef,
    createdAt = createdAt,
)

// ── Achievement progress ─────────────────────────────────────────────────────

/**
 * Upload DTO for an `achievement_progress` row (also used as the pull DTO — the columns are identical
 * and `user_id` is absent on upload / nullable on pull). The server `merge_achievement_progress` takes
 * GREATEST(current_value, tier_reached) and the earliest-non-null unlocked_at/celebrated_at.
 */
@Serializable
data class AchievementProgressDto(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("achievement_id") val achievementId: String,
    @SerialName("current_value") val currentValue: Int,
    @SerialName("tier_reached") val tierReached: Int,
    @SerialName("unlocked_at") val unlockedAt: Long? = null,
    @SerialName("celebrated_at") val celebratedAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long,
)

/** Maps a local achievement entity to its upload DTO, stamping [updatedAt]. */
fun AchievementProgressEntity.toDto(updatedAt: Long): AchievementProgressDto = AchievementProgressDto(
    achievementId = achievementId,
    currentValue = currentValue,
    tierReached = tierReached,
    unlockedAt = unlockedAt,
    celebratedAt = celebratedAt,
    updatedAt = updatedAt,
)

/** Pull DTO for one `get_achievement_progress_page` row, ordered by `(changed_at, achievement_id)`. */
@Serializable
data class AchievementProgressPageDto(
    @SerialName("achievement_id") val achievementId: String,
    @SerialName("current_value") val currentValue: Int,
    @SerialName("tier_reached") val tierReached: Int,
    @SerialName("unlocked_at") val unlockedAt: Long? = null,
    @SerialName("celebrated_at") val celebratedAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("changed_at") val changedAt: Long,
)

/** Maps a pulled achievement row to a local entity (drops the server-only timestamps). */
fun AchievementProgressPageDto.toEntity(): AchievementProgressEntity = AchievementProgressEntity(
    achievementId = achievementId,
    currentValue = currentValue,
    tierReached = tierReached,
    unlockedAt = unlockedAt,
    celebratedAt = celebratedAt,
)

// ── Entitlements ─────────────────────────────────────────────────────────────

/**
 * Upload + pull DTO for an `entitlements` row. Server `merge_entitlements` is an insert-only union
 * keeping the earliest `unlocked_at`. `user_id` absent on upload / nullable on pull.
 */
@Serializable
data class EntitlementDto(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("unlockable_id") val unlockableId: String,
    @SerialName("unlocked_at") val unlockedAt: Long,
    @SerialName("source") val source: String,
    @SerialName("updated_at") val updatedAt: Long,
)

/** Maps a local entitlement entity to its upload DTO, stamping [updatedAt]. */
fun EntitlementEntity.toDto(updatedAt: Long): EntitlementDto = EntitlementDto(
    unlockableId = unlockableId,
    unlockedAt = unlockedAt,
    source = source,
    updatedAt = updatedAt,
)

/** Pull DTO for one `get_entitlements_page` row, ordered by `(changed_at, unlockable_id)`. */
@Serializable
data class EntitlementPageDto(
    @SerialName("unlockable_id") val unlockableId: String,
    @SerialName("unlocked_at") val unlockedAt: Long,
    @SerialName("source") val source: String,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("changed_at") val changedAt: Long,
)

/** Maps a pulled entitlement row to a local entity. */
fun EntitlementPageDto.toEntity(): EntitlementEntity = EntitlementEntity(
    unlockableId = unlockableId,
    unlockedAt = unlockedAt,
    source = source,
)

// ── Streaks ──────────────────────────────────────────────────────────────────

/**
 * Upload + pull DTO for a `streaks` row. Server `merge_streaks` takes GREATEST(longest) and the
 * latest `last_active_date` wins for current/freeze_tokens. `user_id` absent on upload / nullable on
 * pull.
 */
@Serializable
data class StreakDto(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("type") val type: String,
    @SerialName("current") val current: Int,
    @SerialName("longest") val longest: Int,
    @SerialName("last_active_date") val lastActiveDate: String,
    @SerialName("freeze_tokens") val freezeTokens: Int,
    @SerialName("updated_at") val updatedAt: Long,
)

/** Maps a local streak entity to its upload DTO, stamping [updatedAt]. */
fun StreakEntity.toDto(updatedAt: Long): StreakDto = StreakDto(
    type = type,
    current = current,
    longest = longest,
    lastActiveDate = lastActiveDate,
    freezeTokens = freezeTokens,
    updatedAt = updatedAt,
)

/** Pull DTO for one `get_streaks_page` row, ordered by `(changed_at, type)`. */
@Serializable
data class StreakPageDto(
    @SerialName("type") val type: String,
    @SerialName("current") val current: Int,
    @SerialName("longest") val longest: Int,
    @SerialName("last_active_date") val lastActiveDate: String,
    @SerialName("freeze_tokens") val freezeTokens: Int,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("changed_at") val changedAt: Long,
)

/** Maps a pulled streak row to a local entity. */
fun StreakPageDto.toEntity(): StreakEntity = StreakEntity(
    type = type,
    current = current,
    longest = longest,
    lastActiveDate = lastActiveDate,
    freezeTokens = freezeTokens,
)
