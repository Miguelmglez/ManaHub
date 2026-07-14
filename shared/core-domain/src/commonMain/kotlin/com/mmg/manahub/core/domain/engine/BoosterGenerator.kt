package com.mmg.manahub.core.domain.engine

import com.mmg.manahub.core.model.BoosterPack
import com.mmg.manahub.core.model.DraftConfig
import com.mmg.manahub.core.model.DraftableSet

interface BoosterGenerator {
    /**
     * Generates all packs needed for a full draft: [DraftConfig.packCount] × [DraftConfig.seatCount].
     * Pure; no IO. Card selection is weighted and without replacement per-pack.
     */
    fun generate(set: DraftableSet, config: DraftConfig): List<BoosterPack>
}
