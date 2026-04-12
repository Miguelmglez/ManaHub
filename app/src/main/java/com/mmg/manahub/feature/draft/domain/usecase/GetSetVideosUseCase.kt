package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.feature.draft.domain.model.DraftVideo
import com.mmg.manahub.feature.draft.domain.repository.DraftRepository
import javax.inject.Inject

class GetSetVideosUseCase @Inject constructor(
    private val repository: DraftRepository,
) {
    suspend operator fun invoke(setCode: String, setName: String): DataResult<List<DraftVideo>> =
        repository.getSetVideos(setCode, setName)
}
