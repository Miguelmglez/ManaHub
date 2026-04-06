package com.mmg.magicfolder.feature.draft.domain.repository

import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.domain.model.DataResult
import com.mmg.magicfolder.feature.draft.domain.model.DraftSet
import com.mmg.magicfolder.feature.draft.domain.model.DraftVideo
import com.mmg.magicfolder.feature.draft.domain.model.SetDraftGuide
import com.mmg.magicfolder.feature.draft.domain.model.SetTierList

interface DraftRepository {
    suspend fun getDraftableSets(forceRefresh: Boolean = false): DataResult<List<DraftSet>>
    suspend fun getSetGuide(setCode: String): DataResult<SetDraftGuide>
    suspend fun getSetTierList(setCode: String): DataResult<SetTierList>
    suspend fun getSetCards(setCode: String, page: Int = 1): DataResult<List<Card>>
    suspend fun getSetVideos(setCode: String, setName: String): DataResult<List<DraftVideo>>
    suspend fun resolveCardId(cardName: String, setCode: String): DataResult<String>
    suspend fun getCardByName(name: String, setCode: String): DataResult<Card>
}
