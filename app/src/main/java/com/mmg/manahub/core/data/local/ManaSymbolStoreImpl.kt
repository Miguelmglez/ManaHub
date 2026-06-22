package com.mmg.manahub.core.data.local

import com.mmg.manahub.core.data.cache.ManaSymbolRecord
import com.mmg.manahub.core.data.cache.ManaSymbolStore
import com.mmg.manahub.core.data.local.dao.ManaSymbolDao
import com.mmg.manahub.core.data.local.entity.ManaSymbolEntity

/**
 * Android [ManaSymbolStore] implementation backed by Room's [ManaSymbolDao].
 */
class ManaSymbolStoreImpl(private val dao: ManaSymbolDao) : ManaSymbolStore {

    override suspend fun count(): Int = dao.count()

    override suspend fun upsertAll(symbols: List<ManaSymbolRecord>) {
        dao.upsertAll(symbols.map { record ->
            ManaSymbolEntity(
                symbol = record.symbol,
                svgUri = record.svgUri,
                description = record.description,
                isHybrid = record.isHybrid,
                isPhyrexian = record.isPhyrexian,
            )
        })
    }
}
