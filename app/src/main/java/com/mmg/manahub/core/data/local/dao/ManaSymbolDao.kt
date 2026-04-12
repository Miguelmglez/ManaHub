package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mmg.manahub.core.data.local.entity.ManaSymbolEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ManaSymbolDao {

    @Upsert
    suspend fun upsertAll(symbols: List<ManaSymbolEntity>)

    @Query("SELECT * FROM mana_symbols")
    fun observeAll(): Flow<List<ManaSymbolEntity>>

    @Query("SELECT * FROM mana_symbols WHERE symbol = :symbol")
    suspend fun getBySymbol(symbol: String): ManaSymbolEntity?

    @Query("SELECT COUNT(*) FROM mana_symbols")
    suspend fun count(): Int
}
