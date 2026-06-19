package com.mmg.manahub.core.gamification.domain

import com.mmg.manahub.core.data.local.entity.QuestInstanceEntity
import com.mmg.manahub.core.gamification.domain.QuestGenerator.MAX_EXPLORATION
import com.mmg.manahub.core.gamification.domain.QuestGenerator.MIN_ACCESSIBLE
import com.mmg.manahub.core.gamification.domain.QuestGenerator.QUESTS_PER_PERIOD
import com.mmg.manahub.core.gamification.domain.catalog.QuestCatalog
import com.mmg.manahub.core.gamification.domain.catalog.QuestTemplate
import kotlin.random.Random

/**
 * Deterministic, seeded selection of a period's quest set (ADR-002 §9).
 *
 * PURE: no Android, no DAO, no clock. Given the same `(stableId, periodKey)` it always returns the
 * identical ordered list — the property that lets two devices on one account generate the same quests
 * with zero sync coordination (which is why quests are not synced, ADR-002 §11).
 *
 * The seed is an explicit FNV-1a 64-bit hash of `"$stableId|$periodKey"` — NEVER `String.hashCode()`
 * (which is unspecified across JVMs and could differ between devices/runtimes).
 */
object QuestGenerator {

    /** How many quests are selected per period. */
    const val QUESTS_PER_PERIOD: Int = 3

    /** Minimum number of ACCESSIBLE quests in a generated period. */
    private const val MIN_ACCESSIBLE: Int = 2

    /** Maximum number of EXPLORATION quests in a generated period. */
    private const val MAX_EXPLORATION: Int = 1

    private const val FNV_OFFSET_BASIS: Long = -3750763034362895579L // 14695981039346656037 unsigned
    private const val FNV_PRIME: Long = 1099511628211L

    /**
     * Explicit FNV-1a 64-bit hash of [s]'s UTF-8 bytes. Stable across runtimes (unlike
     * [String.hashCode]). Overflow wraps via two's-complement [Long] arithmetic, which is exactly the
     * 64-bit modular behaviour FNV-1a specifies.
     */
    fun fnv1a64(s: String): Long {
        var hash = FNV_OFFSET_BASIS
        for (b in s.toByteArray(Charsets.UTF_8)) {
            hash = hash xor (b.toLong() and 0xFF)
            hash *= FNV_PRIME
        }
        return hash
    }

    /**
     * Deterministically selects [QUESTS_PER_PERIOD] quests for ([period], [periodKey]) and builds their
     * [QuestInstanceEntity] rows (all `progress = 0`, `status = "ACTIVE"`).
     *
     * Selection honours: at least [MIN_ACCESSIBLE] ACCESSIBLE quests and at most [MAX_EXPLORATION]
     * EXPLORATION quests. Templates whose id is in [previousPeriodTemplateIds] are DE-PRIORITISED
     * (placed after fresh templates in the shuffled candidate order) so the same quest rarely repeats
     * back-to-back — but they are still eligible as a fallback when the fresh pool cannot satisfy the
     * count/balance constraints.
     *
     * @param stableId the authenticated user id or the persisted device id (ADR-002 §9).
     * @param period DAILY or WEEKLY.
     * @param periodKey the concrete period key (e.g. `"2026-06-11"` or `"2026-W24"`).
     * @param expiresAt epoch-millis when the generated instances expire.
     * @param previousPeriodTemplateIds template ids that were active in the immediately prior period.
     * @return exactly [QUESTS_PER_PERIOD] instances (fewer only if the catalog itself has fewer
     *   templates for the period, which never happens in v1).
     */
    fun generateInstances(
        stableId: String,
        period: QuestPeriod,
        periodKey: String,
        expiresAt: Long,
        previousPeriodTemplateIds: Set<String>,
    ): List<QuestInstanceEntity> {
        val pool = QuestCatalog.forPeriod(period)
        val random = Random(fnv1a64("$stableId|$periodKey"))

        // Deterministic shuffle of the whole pool, THEN stable-partition fresh-before-repeated so the
        // generator prefers quests that were not active last period without ever excluding them.
        val shuffled = pool.shuffled(random)
        val ordered = shuffled.sortedBy { if (it.id in previousPeriodTemplateIds) 1 else 0 }

        val selected = select(ordered)

        return selected.map { template ->
            QuestInstanceEntity(
                id = "${template.id}:$periodKey",
                templateId = template.id,
                period = period.name,
                periodKey = periodKey,
                target = template.target,
                progress = 0,
                status = STATUS_ACTIVE,
                expiresAt = expiresAt,
                xpReward = template.xpReward,
                tokenReward = 0,
            )
        }
    }

    /**
     * Greedily walks [ordered] (already deterministically shuffled + fresh-first) picking quests while
     * respecting the balance constraints, then back-fills to reach [QUESTS_PER_PERIOD] from the
     * remainder if the constrained pass came up short.
     */
    private fun select(ordered: List<QuestTemplate>): List<QuestTemplate> {
        val chosen = mutableListOf<QuestTemplate>()
        var explorationCount = 0

        // Pass 1: pick while never exceeding the EXPLORATION cap; defer over-cap exploration quests.
        for (template in ordered) {
            if (chosen.size >= QUESTS_PER_PERIOD) break
            if (template.weightClass == QuestWeightClass.EXPLORATION && explorationCount >= MAX_EXPLORATION) continue
            chosen.add(template)
            if (template.weightClass == QuestWeightClass.EXPLORATION) explorationCount++
        }

        // Pass 2: ensure the ACCESSIBLE floor. If short, swap the most-replaceable non-accessible
        // picks for accessible candidates not yet chosen (preserving deterministic order).
        ensureAccessibleFloor(chosen, ordered)

        // Pass 3: if still short of the count (only possible with a tiny catalog), back-fill from the
        // remainder ignoring the exploration cap as a last resort. Never happens with the v1 catalog.
        if (chosen.size < QUESTS_PER_PERIOD) {
            for (template in ordered) {
                if (chosen.size >= QUESTS_PER_PERIOD) break
                if (template !in chosen) chosen.add(template)
            }
        }

        return chosen.take(QUESTS_PER_PERIOD)
    }

    /**
     * Mutates [chosen] in place so it contains at least [MIN_ACCESSIBLE] ACCESSIBLE quests, drawing
     * replacements deterministically from [ordered]. Replaces non-accessible picks (from the end of the
     * chosen list, i.e. the lowest-priority ones) with the next available accessible candidate.
     */
    private fun ensureAccessibleFloor(chosen: MutableList<QuestTemplate>, ordered: List<QuestTemplate>) {
        fun accessibleCount() = chosen.count { it.weightClass == QuestWeightClass.ACCESSIBLE }

        val accessibleCandidates = ordered.filter {
            it.weightClass == QuestWeightClass.ACCESSIBLE && it !in chosen
        }.toMutableList()

        var candidateIndex = 0
        // Replace from the tail so we drop the least-prioritised non-accessible picks first.
        var replaceIndex = chosen.lastIndex
        while (accessibleCount() < MIN_ACCESSIBLE &&
            candidateIndex < accessibleCandidates.size &&
            replaceIndex >= 0
        ) {
            val victim = chosen[replaceIndex]
            if (victim.weightClass != QuestWeightClass.ACCESSIBLE) {
                chosen[replaceIndex] = accessibleCandidates[candidateIndex]
                candidateIndex++
            }
            replaceIndex--
        }
    }

    private const val STATUS_ACTIVE = "ACTIVE"
}
