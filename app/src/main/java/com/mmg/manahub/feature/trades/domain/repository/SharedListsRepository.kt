package com.mmg.manahub.feature.trades.domain.repository

import com.mmg.manahub.feature.trades.domain.model.SharedList
import com.mmg.manahub.feature.trades.domain.model.SharedListResult
import com.mmg.manahub.feature.trades.domain.model.SharedListType

interface SharedListsRepository {
    suspend fun createSharedList(userId: String, listType: SharedListType): Result<SharedList>
    suspend fun resolveSharedList(shareId: String): Result<SharedListResult>
}
