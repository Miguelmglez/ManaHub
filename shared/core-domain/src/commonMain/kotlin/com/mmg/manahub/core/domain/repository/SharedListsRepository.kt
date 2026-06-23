package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.model.SharedList
import com.mmg.manahub.core.model.SharedListResult
import com.mmg.manahub.core.model.SharedListType

interface SharedListsRepository {
    suspend fun createSharedList(userId: String, listType: SharedListType): Result<SharedList>
    suspend fun resolveSharedList(shareId: String): Result<SharedListResult>
}
