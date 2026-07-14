package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.core.domain.repository.NewsRepository
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.news.RefreshResult
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

    var refreshAllResult: Result<RefreshResult> = Result.success(RefreshResult(0, 0, 0))
    var refreshSourceResult: Result<Unit> = Result.success(Unit)
    var addCustomSourceResult: Result<ContentSource> = Result.success(
        ContentSource(id = "id", name = "name", feedUrl = "https://example.com", type = SourceType.ARTICLE),
    )
    var validateFeedResult: Result<Int> = Result.success(1)
    var detectFeedLanguageResult: String? = null

    var lastRefreshAllForce: Boolean? = null
    var lastRefreshSourceId: String? = null
    var lastToggleSourceId: String? = null
    var lastToggleEnabled: Boolean? = null
    var lastAddCustomSourceArgs: AddCustomSourceArgs? = null
    var lastDeleteSourceId: String? = null
    var lastValidateFeedArgs: Pair<String, SourceType>? = null
    var lastDetectFeedLanguageUrl: String? = null

    data class AddCustomSourceArgs(val name: String, val feedUrl: String, val type: SourceType, val language: String)

    override fun observeNews(): Flow<List<NewsItem>> = newsFlow

    override fun observeSources(): Flow<List<ContentSource>> = sourcesFlow

    override suspend fun refreshAll(force: Boolean): Result<RefreshResult> {
        lastRefreshAllForce = force
        return refreshAllResult
    }

    override suspend fun refreshSource(sourceId: String): Result<Unit> {
        lastRefreshSourceId = sourceId
        return refreshSourceResult
    }

    override suspend fun toggleSource(sourceId: String, enabled: Boolean) {
        lastToggleSourceId = sourceId
        lastToggleEnabled = enabled
    }

    override suspend fun addCustomSource(
        name: String,
        feedUrl: String,
        type: SourceType,
        language: String,
    ): Result<ContentSource> {
        lastAddCustomSourceArgs = AddCustomSourceArgs(name, feedUrl, type, language)
        return addCustomSourceResult
    }

    override suspend fun deleteSource(sourceId: String) {
        lastDeleteSourceId = sourceId
    }

    override suspend fun validateFeed(feedUrl: String, type: SourceType): Result<Int> {
        lastValidateFeedArgs = feedUrl to type
        return validateFeedResult
    }

    override suspend fun detectFeedLanguage(feedUrl: String): String? {
        lastDetectFeedLanguageUrl = feedUrl
        return detectFeedLanguageResult
    }
}
