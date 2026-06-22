package com.mmg.manahub.core.data.usecase.symbols

import com.mmg.manahub.core.data.cache.ManaSymbolRecord
import com.mmg.manahub.core.data.cache.ManaSymbolStore
import com.mmg.manahub.core.data.network.ScryfallRequestQueue
import com.mmg.manahub.core.data.remote.ScryfallClient

/**
 * Downloads all mana symbols from Scryfall and caches them locally.
 * Skips the network call if symbols are already present — they rarely change.
 *
 * Lives in the data layer (`:shared:core-data`) rather than the domain layer because it
 * directly orchestrates data-layer types ([ScryfallClient], [ScryfallRequestQueue],
 * [ManaSymbolStore]) without going through a domain repository interface.
 */
class SyncManaSymbolsUseCase(
    private val api: ScryfallClient,
    private val store: ManaSymbolStore,
    private val requestQueue: ScryfallRequestQueue,
) {
    suspend operator fun invoke() {
        if (store.count() > 0) return

        val response = requestQueue.execute { api.getAllSymbols() }

        val records = response.data
            .filter { !it.funny }
            .map { dto ->
                ManaSymbolRecord(
                    symbol      = dto.symbol,
                    svgUri      = dto.svgUri,
                    description = dto.description,
                    isHybrid    = dto.hybrid,
                    isPhyrexian = dto.phyrexian,
                )
            }
        store.upsertAll(records)
    }
}
