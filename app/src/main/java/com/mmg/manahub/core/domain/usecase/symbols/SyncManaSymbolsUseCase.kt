package com.mmg.manahub.core.domain.usecase.symbols

import com.mmg.manahub.core.data.local.dao.ManaSymbolDao
import com.mmg.manahub.core.data.local.entity.ManaSymbolEntity
import com.mmg.manahub.core.data.remote.ScryfallApi
import com.mmg.manahub.core.network.ScryfallRequestQueue
import javax.inject.Inject

class SyncManaSymbolsUseCase @Inject constructor(
    private val api: ScryfallApi,
    private val dao: ManaSymbolDao,
    private val requestQueue: ScryfallRequestQueue,
) {
    /**
     * Downloads all mana symbols from Scryfall and caches them in Room.
     * Skips the network call if symbols are already present — they rarely change.
     */
    suspend operator fun invoke() {
        if (dao.count() > 0) return

        val response = requestQueue.execute { api.getAllSymbols() }

        val entities = response.data
            .filter { !it.funny }
            .map { dto ->
                ManaSymbolEntity(
                    symbol      = dto.symbol,
                    svgUri      = dto.svgUri,
                    description = dto.description,
                    isHybrid    = dto.hybrid,
                    isPhyrexian = dto.phyrexian,
                )
            }
        dao.upsertAll(entities)
    }
}
