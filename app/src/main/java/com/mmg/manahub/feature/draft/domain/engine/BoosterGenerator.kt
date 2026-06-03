package com.mmg.manahub.feature.draft.domain.engine

import com.mmg.manahub.feature.draft.domain.model.BoosterPack
import com.mmg.manahub.feature.draft.domain.model.DraftConfig
import com.mmg.manahub.feature.draft.domain.model.DraftableSet

interface BoosterGenerator {
    /**
     * Generates all packs needed for a full draft: [DraftConfig.packCount] × [DraftConfig.seatCount].
     * Pure; no IO. Card selection is weighted and without replacement per-pack.
     */
    fun generate(set: DraftableSet, config: DraftConfig): List<BoosterPack>
}
