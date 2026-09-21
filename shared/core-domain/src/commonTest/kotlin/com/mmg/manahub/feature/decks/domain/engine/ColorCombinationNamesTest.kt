package com.mmg.manahub.feature.decks.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorCombinationNamesTest {

    /** All 32 subsets of {W,U,B,R,G} -- power set via bitmask over the 5 WUBRG colors. */
    private val wubrgSubsets: List<Set<ManaColor>> = run {
        val colors = listOf(ManaColor.W, ManaColor.U, ManaColor.B, ManaColor.R, ManaColor.G)
        (0 until 32).map { mask -> colors.filterIndexed { i, _ -> (mask shr i) and 1 == 1 }.toSet() }
    }

    @Test
    fun `every WUBRG subset resolves to a name`() {
        wubrgSubsets.forEach { subset ->
            val name = ColorCombinationNames.nameFor(subset)
            assertTrue(name.isNotBlank(), "no name resolved for $subset")
        }
    }

    @Test
    fun `all 32 WUBRG subset names are distinct`() {
        val names = wubrgSubsets.map { ColorCombinationNames.nameFor(it) }
        assertEquals(32, names.toSet().size, "expected 32 distinct names, got ${names.toSet()}")
    }

    @Test
    fun `ManaColor C is ignored when forming the lookup key`() {
        val withoutC = setOf(ManaColor.W, ManaColor.U)
        val withC = setOf(ManaColor.W, ManaColor.U, ManaColor.C)
        assertEquals(ColorCombinationNames.nameFor(withoutC), ColorCombinationNames.nameFor(withC))
    }

    @Test
    fun `colorless identity (empty set) is Colorless`() {
        assertEquals("Colorless", ColorCombinationNames.nameFor(emptySet()))
        assertEquals("Colorless", ColorCombinationNames.nameFor(setOf(ManaColor.C)))
    }

    @Test
    fun `five-color identity is Five-Color`() {
        assertEquals(
            "Five-Color",
            ColorCombinationNames.nameFor(setOf(ManaColor.W, ManaColor.U, ManaColor.B, ManaColor.R, ManaColor.G)),
        )
    }

    @Test
    fun `named examples resolve correctly`() {
        assertEquals("Mono-Red", ColorCombinationNames.nameFor(setOf(ManaColor.R)))
        assertEquals("Azorius", ColorCombinationNames.nameFor(setOf(ManaColor.W, ManaColor.U)))
        assertEquals("Jund", ColorCombinationNames.nameFor(setOf(ManaColor.B, ManaColor.R, ManaColor.G)))
        assertEquals("Mardu", ColorCombinationNames.nameFor(setOf(ManaColor.R, ManaColor.W, ManaColor.B)))
        assertEquals("Yore-Tiller", ColorCombinationNames.nameFor(setOf(ManaColor.W, ManaColor.U, ManaColor.B, ManaColor.R)))
        assertEquals("Glint-Eye", ColorCombinationNames.nameFor(setOf(ManaColor.U, ManaColor.B, ManaColor.R, ManaColor.G)))
        assertEquals("Dune-Brood", ColorCombinationNames.nameFor(setOf(ManaColor.B, ManaColor.R, ManaColor.G, ManaColor.W)))
        assertEquals("Ink-Treader", ColorCombinationNames.nameFor(setOf(ManaColor.R, ManaColor.G, ManaColor.W, ManaColor.U)))
        assertEquals("Witch-Maw", ColorCombinationNames.nameFor(setOf(ManaColor.G, ManaColor.W, ManaColor.U, ManaColor.B)))
    }
}
