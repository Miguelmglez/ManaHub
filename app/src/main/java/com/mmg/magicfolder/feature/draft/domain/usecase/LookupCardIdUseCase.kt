package com.mmg.magicfolder.feature.draft.domain.usecase

import com.mmg.magicfolder.core.domain.model.DataResult
import com.mmg.magicfolder.feature.draft.domain.repository.DraftRepository
import javax.inject.Inject

class LookupCardIdUseCase @Inject constructor(
    private val repository: DraftRepository,
) {
    suspend operator fun invoke(cardName: String, setCode: String): DataResult<String> =
        repository.resolveCardId(cardName, setCode)
}
