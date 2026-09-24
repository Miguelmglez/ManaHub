package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.core.domain.repository.NewsRepository
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.news.RefreshResult
import com.mmg.manahub.core.model.news.ResolvedSource
import com.mmg.manahub.core.model.news.SavedNewsItem
import com.mmg.manahub.core.model.news.SourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hand-written fake [NewsRepository] for `commonTest` — no MockK in `commonTest` (JVM-only, would
 * break the wasmJs target; see `project_kmp_commontest_infra` memory). Records every call's
 * arguments so use-case delegation tests can assert on them, and lets each canned return value be
 * configured per-test.
 */
class FakeNewsRepository : NewsRepository {

    val newsFlow = MutableStateFlow<List<NewsItem>>(emptyList())
    val sourcesFlow = MutableStateFlow<List<ContentSource>>(emptyList())
    val savedFlow = MutableStateFlow<List<SavedNewsItem>>(emptyList())
    val savedIdsFlow = MutableStateFlow<Set<String>>(emptySet())

    var refreshAllResult: Result<RefreshResult> = Result.success(RefreshResult(0, 0, 0))
    var refreshSourceResult: Result<Unit> = Result.success(Unit)
    var resolveSourceResult: Result<ResolvedSource>? = null
    var followResolvedSourceResult: Result<ContentSource> = Result.success(
        ContentSource(id = "followed", name = "name", feedUrl = "https://example.com/feed", type = SourceType.ARTICLE),
    )

    var lastRefreshAllForce: Boolean? = null
    var lastRefreshSourceId: String? = null
    var lastFollowedSourceId: String? = null
    var lastFollowed: Boolean? = null
    var lastDeleteSourceId: String? = null
    val savedItems = mutableListOf<NewsItem>()
    val unsavedIds = mutableListOf<String>()
    var lastResolveInput: String? = null
    var lastFollowArgs: FollowArgs? = null

    data class FollowArgs(val source: ResolvedSource, val name: String, val language: String)

    override fun observeNews(): Flow<List<NewsItem>> = newsFlow

    override fun observeSources(): Flow<List<ContentSource>> = sourcesFlow

    override fun observeSaved(): Flow<List<SavedNewsItem>> = savedFlow

    override fun observeSavedIds(): Flow<Set<String>> = savedIdsFlow

    override suspend fun refreshAll(force: Boolean): Result<RefreshResult> {
        lastRefreshAllForce = force
        return refreshAllResult
    }

    override suspend fun refreshSource(sourceId: String): Result<Unit> {
        lastRefreshSourceId = sourceId
        return refreshSourceResult
    }

    override suspend fun setSourceFollowed(sourceId: String, followed: Boolean) {
        lastFollowedSourceId = sourceId
        lastFollowed = followed
    }

    override suspend fun deleteSource(sourceId: String) {
        lastDeleteSourceId = sourceId
    }

    override suspend fun save(item: NewsItem) {
        savedItems += item
    }

    override suspend fun unsave(itemId: String) {
        unsavedIds += itemId
    }

    override suspend fun resolveSource(input: String): Result<ResolvedSource> {
        lastResolveInput = input
        return resolveSourceResult ?: error("resolveSourceResult not configured")
    }

    override suspend fun followResolvedSource(source: ResolvedSource, name: String, language: String): Result<ContentSource> {
        lastFollowArgs = FollowArgs(source, name, language)
        return followResolvedSourceResult
    }
}
