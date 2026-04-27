package com.mmg.manahub.feature.trades.data.repository

import com.mmg.manahub.feature.trades.data.remote.SharedListsRemoteDataSource
import com.mmg.manahub.feature.trades.domain.model.SharedList
import com.mmg.manahub.feature.trades.domain.model.SharedListResult
import com.mmg.manahub.feature.trades.domain.model.SharedListType
import com.mmg.manahub.feature.trades.domain.repository.SharedListsRepository
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
