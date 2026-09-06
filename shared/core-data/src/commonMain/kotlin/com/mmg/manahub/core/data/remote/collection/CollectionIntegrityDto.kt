package com.mmg.manahub.core.data.remote.collection

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO mirroring the single-row result of the `get_collection_integrity()` Supabase RPC.
 *
 * Collection sync data-loss fix (`linear-moseying-yeti` plan), Phase 6: drives
 * [com.mmg.manahub.core.sync.SyncManager]'s post-sync integrity self-check, which detects a
 * client/server row-count disagreement and triggers a full re-pull without any manual
 * intervention — this is what makes an already-damaged install (or any future truncation-shaped
 * bug) self-heal instead of requiring a one-off SQL recovery per affected user.
 *
 * @property totalRows Row count for `auth.uid()` INCLUDING soft-deleted tombstones — compare
 *   this against a local count that ALSO includes tombstones
 *   ([com.mmg.manahub.core.data.local.dao.UserCardCollectionDao.getTotalRowCountForUser]).
 * @property liveRows Row count excluding tombstones (`is_deleted = false`).
 * @property liveQuantity Summed `quantity` across live rows.
 * @property maxUpdatedAt Highest `updated_at` across all rows for this user, `null` when the
 *   user owns zero rows.
 */
@Serializable
data class CollectionIntegrityDto(
    @SerialName("total_rows") val totalRows: Long,
    @SerialName("live_rows") val liveRows: Long,
    @SerialName("live_quantity") val liveQuantity: Long,
    @SerialName("max_updated_at") val maxUpdatedAt: Long?,
)
