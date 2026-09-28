package com.mmg.manahub.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the theme token contracts that separate call sites used to duplicate.
 *
 * GROUP 1 — one shared default theme
 * GROUP 2 — every theme has a stable persistKey and a resolvable palette
 * GROUP 3 — a player slot's ink follows its OWN background, not the app theme
 */
class AppThemeDefaultsTest {

    private val allThemes = listOf(
        AppTheme.NeonVoid, AppTheme.MedievalGrimoire, AppTheme.ArcaneCosmos, AppTheme.ForestMurmur,
        AppTheme.AncientOak, AppTheme.HallowedPrint, AppTheme.AzureFlux, AppTheme.PlanarVeil,
        AppTheme.VenomShade, AppTheme.GlacialEdge, AppTheme.DuskEmber, AppTheme.OnyxNoir,
    )

    private fun Color.approxLuminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

    @Test
    fun `the default theme is a single shared constant`() {
        assertEquals(AppTheme.ArcaneCosmos, AppTheme.Default)
    }

    @Test
    fun `every theme has a unique stable persist key`() {
        val keys = allThemes.map { it.persistKey }

        assertEquals(allThemes.size, keys.toSet().size)
        // An R8-renamed class name would leak a '$' and silently change the persisted value
        assertTrue("persistKey must never fall back to a class name", keys.none { it.contains('$') })
    }

    @Test
    fun `every theme resolves to a palette with player slots`() {
        allThemes.forEach { theme ->
            val colors = theme.colors()
            assertNotNull(colors)
            assertTrue("${theme.persistKey} must define player slots", colors.playerColors.isNotEmpty())
        }
    }

    @Test
    fun `a light player background gets dark ink and a dark one gets light ink`() {
        // HallowedPrint is the only light theme and its player cards are light too, so a
        // theme-derived textPrimary was ~1:1 on them.
        val lightSlot = AppTheme.HallowedPrint.colors().playerColors.first()
        val darkSlot = AppTheme.NeonVoid.colors().playerColors.first()

        assertTrue("Light slot needs dark ink", lightSlot.onBackground.approxLuminance() < 0.5f)
        assertTrue("Dark slot needs light ink", darkSlot.onBackground.approxLuminance() > 0.5f)
    }

    @Test
    fun `every player slot's ink contrasts with its own background`() {
        allThemes.forEach { theme ->
            theme.colors().playerColors.forEach { slot ->
                val backgroundIsLight = slot.background.approxLuminance() > 0.5f
                val inkIsLight = slot.onBackground.approxLuminance() > 0.5f
                assertTrue(
                    "${theme.persistKey}/${slot.name}: ink must oppose its background",
                    backgroundIsLight != inkIsLight,
                )
            }
        }
    }
}
