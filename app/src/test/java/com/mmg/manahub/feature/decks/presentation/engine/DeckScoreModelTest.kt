package com.mmg.manahub.feature.decks.presentation.engine

import com.mmg.manahub.core.domain.model.DeckFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure data / model classes:
 *   - NeutralPowerResolver
 *   - EdhrecPowerResolver
 *   - ScoreWeights.normalized()
 *   - DeckSkeletons
 *   - RoleCoverage
 *   - DeckRole.isFunctional
 */
class DeckScoreModelTest {

    // ── Group 1: NeutralPowerResolver ─────────────────────────────────────────

    @Test
    fun `NeutralPowerResolver given normal card returns normalized 0_5`() {
        // Arrange
        val c = card(id = "n1", gameChanger = false)

        // Act
        val result = NeutralPowerResolver.powerOf(c)

        // Assert
        assertEquals(0.5f, result.normalized, 0.001f)
        assertEquals(false, result.isGameChanger)
    }

    @Test
    fun `NeutralPowerResolver given gameChanger card returns normalized 0_85`() {
        // Arrange
        val c = card(id = "gc1", gameChanger = true)

        // Act
        val result = NeutralPowerResolver.powerOf(c)

        // Assert
        assertEquals(0.85f, result.normalized, 0.001f)
        assertEquals(true, result.isGameChanger)
    }

    // ── Group 2: EdhrecPowerResolver ──────────────────────────────────────────

    @Test
    fun `EdhrecPowerResolver given null rank returns 0_35`() {
        // Arrange
        val resolver = EdhrecPowerResolver { null }
        val c = card(id = "e1", gameChanger = false)

        // Act
        val result = resolver.powerOf(c)

        // Assert
        assertEquals(0.35f, result.normalized, 0.001f)
        assertEquals(false, result.isGameChanger)
    }

    @Test
    fun `EdhrecPowerResolver given rank 1 returns close to 1_0`() {
        // Arrange
        val resolver = EdhrecPowerResolver(maxRank = 30_000) { 1 }
        val c = card(id = "e2", gameChanger = false)

        // Act
        val result = resolver.powerOf(c)

        // Assert
        assertTrue("Expected near-1.0 for rank 1, got ${result.normalized}", result.normalized >= 0.99f)
    }

    @Test
    fun `EdhrecPowerResolver given rank equal to maxRank returns close to 0_0`() {
        // Arrange
        val resolver = EdhrecPowerResolver(maxRank = 30_000) { 30_000 }
        val c = card(id = "e3", gameChanger = false)

        // Act
        val result = resolver.powerOf(c)

        // Assert
        assertEquals(0f, result.normalized, 0.001f)
    }

    @Test
    fun `EdhrecPowerResolver given mid rank returns score between 0 and 1`() {
        // Arrange
        val resolver = EdhrecPowerResolver(maxRank = 30_000) { 5_000 }
        val c = card(id = "e4", gameChanger = false)

        // Act
        val result = resolver.powerOf(c)

        // Assert
        assertTrue(result.normalized in 0f..1f)
    }

    @Test
    fun `EdhrecPowerResolver given null rank but gameChanger applies 0_85 floor`() {
        // Arrange
        val resolver = EdhrecPowerResolver { null }
        val c = card(id = "gc-edhrec", gameChanger = true)

        // Act
        val result = resolver.powerOf(c)

        // Assert
        // null rank = base 0.35; gameChanger floor lifts it to 0.85
        assertEquals(0.85f, result.normalized, 0.001f)
        assertTrue(result.isGameChanger)
    }

    @Test
    fun `EdhrecPowerResolver given high rank but gameChanger floor is 0_85`() {
        // Arrange
        val resolver = EdhrecPowerResolver(maxRank = 30_000) { 29_000 }
        val c = card(id = "gc-high-rank", gameChanger = true)

        // Act
        val result = resolver.powerOf(c)

        // Assert
        // raw score near 0 for rank 29_000; floor should force 0.85
        assertEquals(0.85f, result.normalized, 0.001f)
    }

    @Test
    fun `EdhrecPowerResolver given rank below 1 is clamped to rank 1`() {
        // Arrange
        val resolver = EdhrecPowerResolver(maxRank = 30_000) { 0 }  // 0 coerced to 1
        val c = card(id = "e5")

        // Act
        val result = resolver.powerOf(c)

        // Assert
        assertTrue(result.normalized >= 0.99f)
    }

    @Test
    fun `EdhrecPowerResolver lower rank produces higher score than higher rank`() {
        // Arrange
        val resolver = EdhrecPowerResolver(maxRank = 30_000) { it.scryfallId.toInt() }
        val low  = card(id = "100")
        val high = card(id = "20000")

        // Act
        val lowScore  = resolver.powerOf(low).normalized
        val highScore = resolver.powerOf(high).normalized

        // Assert
        assertTrue("Low-rank card should score higher", lowScore > highScore)
    }

    // ── Group 3: ScoreWeights.normalized() ───────────────────────────────────

    @Test
    fun `ScoreWeights normalized returns weights that sum to 1 for positive components`() {
        // Arrange
        val w = ScoreWeights(synergy = 0.34f, roleNeed = 0.22f, curve = 0.14f, power = 0.20f, color = 0.10f)

        // Act
        val n = w.normalized()

        // Assert
        val sum = n.synergy + n.roleNeed + n.curve + n.power + n.color
        assertEquals(1.0f, sum, 0.001f)
    }

    @Test
    fun `ScoreWeights normalized preserves redundancyPenalty and powerFloor unchanged`() {
        // Arrange
        val w = ScoreWeights(redundancyPenalty = 0.25f, powerFloor = 0.18f)

        // Act
        val n = w.normalized()

        // Assert
        assertEquals(0.25f, n.redundancyPenalty, 0.001f)
        assertEquals(0.18f, n.powerFloor, 0.001f)
    }

    @Test
    fun `ScoreWeights normalized returns self when all positive weights are zero`() {
        // Arrange
        val w = ScoreWeights(synergy = 0f, roleNeed = 0f, curve = 0f, power = 0f, color = 0f)

        // Act
        val n = w.normalized()

        // Assert (should not throw, returns copy unchanged)
        assertEquals(0f, n.synergy, 0.001f)
    }

    // ── Group 4: DeckRole.isFunctional ────────────────────────────────────────

    @Test
    fun `DeckRole isFunctional is false for LAND`() {
        assertEquals(false, DeckRole.LAND.isFunctional)
    }

    @Test
    fun `DeckRole isFunctional is false for FILLER`() {
        assertEquals(false, DeckRole.FILLER.isFunctional)
    }

    @Test
    fun `DeckRole isFunctional is true for all other roles`() {
        val nonFunctional = setOf(DeckRole.LAND, DeckRole.FILLER)
        DeckRole.values().filterNot { it in nonFunctional }.forEach { role ->
            assertTrue("Expected $role to be functional", role.isFunctional)
        }
    }

    // ── Group 5: DeckSkeletons ────────────────────────────────────────────────

    @Test
    fun `DeckSkeletons Commander has LAND slot with ideal 37`() {
        // Arrange
        val skeleton = DeckSkeletons.forFormat(DeckFormat.COMMANDER)

        // Act
        val ideal = skeleton.idealFor(DeckRole.LAND)

        // Assert
        assertEquals(37, ideal)
    }

    @Test
    fun `DeckSkeletons Commander has RAMP slot with ideal 11`() {
        val skeleton = DeckSkeletons.forFormat(DeckFormat.COMMANDER)
        assertEquals(11, skeleton.idealFor(DeckRole.RAMP))
    }

    @Test
    fun `DeckSkeletons Standard has LAND slot with ideal 24`() {
        val skeleton = DeckSkeletons.forFormat(DeckFormat.STANDARD)
        assertEquals(24, skeleton.idealFor(DeckRole.LAND))
    }

    @Test
    fun `DeckSkeletons Draft has THREAT slot with ideal 15`() {
        val skeleton = DeckSkeletons.forFormat(DeckFormat.DRAFT)
        assertEquals(15, skeleton.idealFor(DeckRole.THREAT))
    }

    @Test
    fun `DeckSkeletons maxFor unknown role returns MAX_VALUE`() {
        val skeleton = DeckSkeletons.forFormat(DeckFormat.COMMANDER)
        // FILLER is not defined in Commander skeleton slots
        assertEquals(Int.MAX_VALUE, skeleton.maxFor(DeckRole.FILLER))
    }

    // ── Group 6: RoleCoverage ─────────────────────────────────────────────────

    @Test
    fun `RoleCoverage gap is zero when current meets ideal`() {
        val rc = RoleCoverage(DeckRole.RAMP, current = 11, ideal = 11)
        assertEquals(0, rc.gap)
    }

    @Test
    fun `RoleCoverage gap is clamped to zero when over-covered`() {
        val rc = RoleCoverage(DeckRole.RAMP, current = 15, ideal = 11)
        assertEquals(0, rc.gap)
    }

    @Test
    fun `RoleCoverage gap is correct when under-covered`() {
        val rc = RoleCoverage(DeckRole.RAMP, current = 5, ideal = 11)
        assertEquals(6, rc.gap)
    }

    @Test
    fun `RoleCoverage ratio is 1_0 when ideal is zero`() {
        val rc = RoleCoverage(DeckRole.SYNERGY, current = 0, ideal = 0)
        assertEquals(1f, rc.ratio, 0.001f)
    }

    @Test
    fun `RoleCoverage ratio is capped at 2_0 when heavily over-covered`() {
        val rc = RoleCoverage(DeckRole.RAMP, current = 100, ideal = 11)
        assertEquals(2f, rc.ratio, 0.001f)
    }
}
