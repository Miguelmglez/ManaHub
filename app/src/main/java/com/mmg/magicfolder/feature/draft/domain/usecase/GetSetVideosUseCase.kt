package com.mmg.magicfolder.feature.draft.domain.usecase

import com.mmg.magicfolder.core.domain.model.DataResult
import com.mmg.magicfolder.feature.draft.domain.model.DraftVideo
import com.mmg.magicfolder.feature.draft.domain.repository.DraftRepository
import javax.inject.Inject

class GetSetVideosUseCase @Inject constructor(
    private val repository: DraftRepository,
) {
    suspend operator fun invoke(setCode: String, setName: String): DataResult<List<DraftVideo>> =
        repository.getSetVideos(setCode, setName)
}
