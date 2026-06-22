package com.mmg.manahub.core.domain.repository

import androidx.paging.PagingData
import com.mmg.manahub.core.data.local.dao.UserCardWithCard
import kotlinx.coroutines.flow.Flow

/**
 * Android-only paging surface for the user-card collection.
 *
 * Kept in `:app` (NOT in the shared `:shared:core-domain` `UserCardRepository`) because
 * `androidx.paging.PagingData` has no Kotlin/Wasm target and the page is backed by the Room
 * DAO projection [com.mmg.manahub.core.data.local.dao.UserCardWithCard] via
 * `CollectionRemoteMediator`. Splitting this method off the shared interface lets the
 * platform-agnostic [UserCardRepository] live in `commonMain` without dragging Android paging
 * (or Room) into shared code, while the Android Collection UI can still drive incremental Room
 * paging through this interface directly.
 *
 * `UserCardRepositoryImpl` implements BOTH [UserCardRepository] (shared) and this interface.
 * The web data source (later phase) implements only [UserCardRepository] and provides its own
 * paged fetch behind the common `Page` model.
 */
interface CollectionPagerSource {

    /**
     * Returns a [Flow] of [PagingData] backed by `CollectionRemoteMediator`.
     * The mediator loads pages from Supabase and caches them in Room automatically.
     */
    fun getCollectionPager(userId: String?): Flow<PagingData<UserCardWithCard>>
}
