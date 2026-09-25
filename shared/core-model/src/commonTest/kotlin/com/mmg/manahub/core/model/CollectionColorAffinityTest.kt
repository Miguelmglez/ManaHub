package com.mmg.manahub.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CollectionColorAffinityTest {

    @Test
    fun multicolourCardsCountTowardEachColourAndEmptyIdentityIsColourless() {
        val counts = CollectionColorAffinity.countByColor(
            listOf("""["W","U"]""" to 2, """["U"]""" to 3, "[]" to 4),
        )

        assertEquals(mapOf(MtgColor.W to 2, MtgColor.U to 5, MtgColor.COLORLESS to 4), counts)
    }

    @Test
    fun favouriteIgnoresColourlessAndBreaksTiesInWubrgOrder() {
        assertEquals("U", CollectionColorAffinity.favouriteColorCode(mapOf(MtgColor.U to 5, MtgColor.COLORLESS to 9)))
        assertEquals("W", CollectionColorAffinity.favouriteColorCode(mapOf(MtgColor.G to 3, MtgColor.W to 3)))
        assertNull(CollectionColorAffinity.favouriteColorCode(mapOf(MtgColor.COLORLESS to 9)))
    }

    @Test
    fun identityCodesReadColourlessAsC() {
        assertEquals(listOf("U", "B"), CollectionColorAffinity.identityCodes("""["U","B"]"""))
        assertEquals(listOf("C"), CollectionColorAffinity.identityCodes("[]"))
    }
}
