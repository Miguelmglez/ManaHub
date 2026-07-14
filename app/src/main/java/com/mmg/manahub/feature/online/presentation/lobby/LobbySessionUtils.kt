package com.mmg.manahub.feature.online.presentation.lobby

import com.mmg.manahub.core.online.domain.model.OnlineParticipant
import com.mmg.manahub.core.online.domain.model.OnlineSessionStatus
import com.mmg.manahub.core.online.domain.model.ParticipantStatus

/**
 * Shared lobby-state helpers used by both [LobbyHostViewModel] and [LobbyJoinViewModel].
 *
 * Extracted from the host ViewModel during the 2026-07-10 online-feature audit (findings
 * #3/#4/#5/#8/#9/#13): the join ViewModel previously replaced its participant list with a raw
 * HTTP snapshot instead of merging by id, and neither ViewModel shared a single definition of
 * "the session just ended" — leading to duplicated, drifting logic.
 */

/**
 * True when [this] represents a terminal session state: no further participant or game
 * activity is possible and the lobby should reset. Used by both poll loops and Realtime
 * handlers in both ViewModels so either delivery mechanism reacts identically.
 */
internal fun OnlineSessionStatus.isTerminal(): Boolean =
    this == OnlineSessionStatus.FINISHED || this == OnlineSessionStatus.ABANDONED

/**
 * Merges a freshly-fetched participant [snapshot] into the [current] list by id.
 *
 * The snapshot wins for participants present in it (updated ready-state, theme, etc.).
 * Participants present only in [current] — arrived via a fast Realtime event after the
 * snapshot was taken — are preserved for one poll cycle so a lagging HTTP snapshot never
 * erases a fast update (the CLAUDE.md-documented "merge by id, never replace" invariant).
 *
 * @param missingStreak Per-participant-id count of consecutive snapshots the id has been
 *   absent from (excluding ids present in [snapshot]). Passed in and returned so the caller
 *   can persist it across polls. A participant missing for [MAX_MISSING_STREAK] consecutive
 *   snapshots is dropped — this is the "two-strike" rule for a hard-deleted server row that
 *   never arrives with `status = LEFT` (a normal leave always does, and is filtered above).
 */
internal fun mergeParticipantsById(
    current: List<OnlineParticipant>,
    snapshot: List<OnlineParticipant>,
    missingStreak: Map<String, Int> = emptyMap(),
): Pair<List<OnlineParticipant>, Map<String, Int>> {
    val active = snapshot.filter { it.status != ParticipantStatus.LEFT }
    val snapshotIds = active.map { it.id }.toSet()
    val realtimeOnly = current.filter { it.id !in snapshotIds }

    val survivors = mutableListOf<OnlineParticipant>()
    val nextStreak = mutableMapOf<String, Int>()
    for (participant in realtimeOnly) {
        val streak = (missingStreak[participant.id] ?: 0) + 1
        if (streak < MAX_MISSING_STREAK) {
            survivors += participant
            nextStreak[participant.id] = streak
        }
    }

    val merged = (active + survivors).sortedBy { it.slotIndex }
    return merged to nextStreak
}

/** A participant missing from this many consecutive snapshots is dropped as a ghost. */
private const val MAX_MISSING_STREAK = 2
