package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DraftVideo
import com.mmg.manahub.core.domain.repository.DraftRepository

class GetSetVideosUseCase(
    private val repository: DraftRepository,
) {
    suspend operator fun invoke(setCode: String, setName: String): DataResult<List<DraftVideo>> =
        repository.getSetVideos(setCode, setName)
}
