package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.model.ArchidektTrendingDeck
import kotlinx.coroutines.flow.Flow

/**
 * Contract for the Home dashboard's "Popular on Archidekt" community deck slides
 * (Home feature overhaul Phase 1.2.c).
 *
 * Implementations MUST reuse the existing Archidekt network stack
 * (`com.mmg.manahub.core.data.remote.ArchidektClient`) rather than a parallel client, cache the
 * last successful payload with a 12-24h TTL, and fetch lazily (never at app startup). A failed
 * fetch degrades to the cached payload or an empty list — never an inline error tile.
 */
interface ArchidektTrendingRepository {
    /** Emits up to 3 popular Commander decks, or an empty list when no data is available. */
    fun observeTrendingDecks(): Flow<List<ArchidektTrendingDeck>>
}
