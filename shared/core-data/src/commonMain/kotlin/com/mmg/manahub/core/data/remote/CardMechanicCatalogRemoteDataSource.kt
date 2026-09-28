package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.data.remote.dto.CardMechanicCatalogDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order

interface CardMechanicCatalogRemoteDataSourceContract {
    suspend fun getActivePage(from: Long, pageSize: Long): List<CardMechanicCatalogDto>
}

class CardMechanicCatalogRemoteDataSource(
    private val supabaseClient: SupabaseClient,
) : CardMechanicCatalogRemoteDataSourceContract {
    override suspend fun getActivePage(from: Long, pageSize: Long): List<CardMechanicCatalogDto> {
        require(from >= 0 && pageSize in 1..500)
        return supabaseClient.postgrest["card_mechanic_catalog"]
            .select {
                filter { eq("review_status", "active") }
                order("key", Order.ASCENDING)
                range(from, from + pageSize - 1)
            }
            .decodeList<CardMechanicCatalogDto>()
    }
}
