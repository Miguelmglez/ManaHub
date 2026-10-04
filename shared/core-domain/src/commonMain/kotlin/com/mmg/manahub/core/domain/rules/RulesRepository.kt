package com.mmg.manahub.core.domain.rules

import com.mmg.manahub.core.model.rules.*

interface RulesSnapshotStore {
    suspend fun baseline(): RulesRawSnapshot
    suspend fun active(): RulesRawSnapshot?
    suspend fun edition(id: String): RulesRawSnapshot?
    suspend fun install(snapshot: RulesRawSnapshot)
}

interface RulesOfficialSource {
    suspend fun download(): RulesRawSnapshot
}

interface RulesRepository {
    suspend fun load(edition: String? = null): RulesEdition
    suspend fun search(edition: RulesEdition, query: String, offset: Int = 0): RulesSearchPage
    suspend fun update(): RulesUpdateResult
}

class LoadRulesUseCase(private val repository: RulesRepository) {
    suspend operator fun invoke(edition: String? = null) = repository.load(edition)
}

class SearchRulesUseCase(private val repository: RulesRepository) {
    suspend operator fun invoke(edition: RulesEdition, query: String, offset: Int = 0) =
        repository.search(edition, query, offset)
}

class UpdateRulesUseCase(private val repository: RulesRepository) {
    suspend operator fun invoke() = repository.update()
}
