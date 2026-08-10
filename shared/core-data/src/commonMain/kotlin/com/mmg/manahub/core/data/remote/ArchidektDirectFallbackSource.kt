package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.network.ArchidektRequestQueue
import com.mmg.manahub.core.model.AggregateCardEntry
import com.mmg.manahub.core.model.AggregateSource
import com.mmg.manahub.core.model.CommunityAggregate
import com.mmg.manahub.core.model.CommunityDeckSearchFilters
import com.mmg.manahub.core.model.SixtyDeckSummary

/** How many decks the degraded direct-Archidekt fallback samples (Phase 3.3: "reduced sample"). */
const val FALLBACK_SAMPLE_SIZE = 8

/**
 * A source [CommunityAggregateRepositoryImpl] can call when the `manahub-community` Worker is
 * unreachable, for the 60-card path only (Phase 3.3) — kept as a narrow functional interface
 * (rather than the repository depending on [ArchidektClient] directly) so the repository's own
 * cache/Worker/fallback DISPATCH logic can be unit tested with a trivial fake, independent of
 * Archidekt's wire protocol.
 */
fun interface SixtyFallbackFetcher {
    /** Returns null (never throws) when the fallback itself couldn't produce a sample. */
    suspend fun fetch(signatureCard: String, format: Int): CommunityAggregate.Sixty.Materialized?
}

/**
 * Real implementation of [SixtyFallbackFetcher]: fetches up to [FALLBACK_SAMPLE_SIZE] decks
 * matching the signature card directly from Archidekt and tallies per-card inclusion locally.
 * Degraded (no staple dampening against a global table, no color profile beyond this tiny
 * sample) but keeps the 60-card path "degraded, never dead" while the Worker is down.
 *
 * Per ADR-004 §5, Archidekt has no CORS grant for arbitrary origins — on the web target this
 * call fails the same way an unreachable Worker would (caught upstream, never a hard crash);
 * it is Android-reachable only in practice.
 */
class ArchidektDirectFallbackSource(
    private val archidektClient: ArchidektClient,
    private val archidektRequestQueue: ArchidektRequestQueue,
    private val crashReporter: CrashReporter,
    private val now: () -> Long,
) : SixtyFallbackFetcher {

    override suspend fun fetch(signatureCard: String, format: Int): CommunityAggregate.Sixty.Materialized? {
        return try {
            val search = archidektRequestQueue.execute {
                archidektClient.searchDecks(
                    CommunityDeckSearchFilters(
                        cardNames = listOf(signatureCard),
                        deckFormatId = format,
                        pageSize = FALLBACK_SAMPLE_SIZE,
                    ),
                )
            }
            val sanitized = search.results.filter { !it.private && !it.theorycrafted }.take(FALLBACK_SAMPLE_SIZE)
            if (sanitized.isEmpty()) return null

            val inclusionCounts = mutableMapOf<String, Int>()
            val deckSummaries = mutableListOf<SixtyDeckSummary>()
            var sampled = 0
            for (summary in sanitized) {
                val detail = try {
                    archidektRequestQueue.execute { archidektClient.getDeckById(summary.id) }
                } catch (e: Exception) {
                    continue
                }
                sampled += 1
                deckSummaries += SixtyDeckSummary(
                    archidektId = detail.id,
                    name = detail.name,
                    owner = detail.owner?.username ?: "Unknown",
                    viewCount = detail.viewCount,
                    colors = emptyMap(),
                    edhBracket = null,
                )
                val seen = mutableSetOf<String>()
                for (entry in detail.cards) {
                    val name = entry.card?.oracleCard?.name ?: continue
                    if (!seen.add(name)) continue
                    inclusionCounts[name] = (inclusionCounts[name] ?: 0) + 1
                }
            }
            if (sampled == 0) return null

            val cards = inclusionCounts.map { (name, count) ->
                AggregateCardEntry(
                    name = name,
                    scryfallUid = null,
                    inclusionPct = count.toFloat() / sampled,
                    synergy = 0f, // No global inclusion table client-side — synergy stays neutral.
                    category = "mainboard",
                    numDecks = count,
                )
            }
            CommunityAggregate.Sixty.Materialized(
                canonicalKey = CommunityAggregateKeys.buildCanonicalKey(listOf(signatureCard)),
                format = format,
                numDecksSampled = sampled,
                deckSummaries = deckSummaries,
                colorProfile = emptyMap(),
                cards = cards,
                cachedAt = now(),
                source = AggregateSource.DIRECT_FALLBACK,
            )
        } catch (e: Exception) {
            crashReporter.log("community_sixty_fallback")
            crashReporter.recordException(e)
            null
        }
    }
}
