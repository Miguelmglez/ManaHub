package com.mmg.manahub.core.gamification.data.remote

import com.mmg.manahub.core.data.local.entity.AchievementProgressEntity
import com.mmg.manahub.core.data.local.entity.EntitlementEntity
import com.mmg.manahub.core.data.local.entity.PlayerProgressionEntity
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
 * client-sent user_id). The "changes-since" pull DTOs DO carry a nullable `user_id` so
 * `decodeList` succeeds on the server-returned rows (it is ignored locally — the device has exactly
 * one user's worth of gamification state).
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
 * Pull DTO for an `xp_transactions` row returned by `get_xp_transactions_changes_since`.
 * Includes the server-returned `user_id` (nullable, ignored locally) so `decodeList` succeeds.
 */
@Serializable
data class XpTransactionChangeDto(
    @SerialName("user_id") val userId: String? = null,
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
 * Maps a pulled change DTO to a local ledger entity. `id` is left at 0 so Room assigns a fresh local
 * autoincrement on insert; dedupe happens on the UNIQUE `idempotency_key`, not the id.
 */
fun XpTransactionChangeDto.toEntity(): XpTransactionEntity = XpTransactionEntity(
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

/** Maps a pulled achievement DTO to a local entity (drops the server-only updated_at/user_id). */
fun AchievementProgressDto.toEntity(): AchievementProgressEntity = AchievementProgressEntity(
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

/** Maps a pulled entitlement DTO to a local entity. */
fun EntitlementDto.toEntity(): EntitlementEntity = EntitlementEntity(
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

/** Maps a pulled streak DTO to a local entity. */
fun StreakDto.toEntity(): StreakEntity = StreakEntity(
    type = type,
    current = current,
    longest = longest,
    lastActiveDate = lastActiveDate,
    freezeTokens = freezeTokens,
)

// ── Player progression (pull-only) ───────────────────────────────────────────

/**
 * Pull-only DTO for a `player_progression` row returned by `get_progression_changes_since`.
 *
 * The client does NOT push progression and does NOT trust the remote level/total blindly: after
 * pulling the ledger it recomputes `total_xp = SUM(amount)` and `level = LevelCurve.levelForTotalXp`
 * locally (ADR-002 §11 — the ledger is the source of truth; progression is a denormalized cache). This
 * DTO exists only to satisfy the contract / allow future diagnostics; its values are informational.
 */
@Serializable
data class PlayerProgressionChangeDto(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("total_xp") val totalXp: Long,
    @SerialName("level") val level: Int,
    @SerialName("updated_at") val updatedAt: Long,
)

/** Maps a pulled progression DTO to the singleton local entity (informational; client recomputes). */
fun PlayerProgressionChangeDto.toEntity(): PlayerProgressionEntity = PlayerProgressionEntity(
    id = PlayerProgressionEntity.SINGLETON_ID,
    totalXp = totalXp,
    level = level,
    updatedAt = updatedAt,
)
