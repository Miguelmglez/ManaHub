package com.mmg.manahub.feature.trades.data.repository

import com.mmg.manahub.core.data.remote.trades.SharedListsRemoteDataSource
import com.mmg.manahub.core.model.SharedList
import com.mmg.manahub.core.model.SharedListResult
import com.mmg.manahub.core.model.SharedListType
import com.mmg.manahub.core.domain.repository.SharedListsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedListsRepositoryImpl @Inject constructor(
    private val remote: SharedListsRemoteDataSource,
) : SharedListsRepository {

    override suspend fun createSharedList(userId: String, listType: SharedListType): Result<SharedList> =
        remote.createSharedList(userId, listType)

    override suspend fun resolveSharedList(shareId: String): Result<SharedListResult> =
        remote.resolveSharedList(shareId)
}
