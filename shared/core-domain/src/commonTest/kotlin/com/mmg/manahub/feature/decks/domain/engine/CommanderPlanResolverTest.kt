package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deck Wizard Commander v3 plan, Phase 1.1/1.3 gate.
 *
 * 1. [CommanderPlanResolver]'s resolved skeleton must be `==` the skeleton
 *    [AnalysisEngine.evaluate] would resolve for the SAME (format, archetype, posture, themes,
 *    identity) pin — table-driven over every catalog entry available in COMMANDER/COMMANDER_CASUAL,
 *    across representative identities. [AnalysisEngine.evaluate] itself is not called directly here
 *    (it needs a full mainboard + [DeckProfile] and does not expose the internal skeleton it
 *    resolves) — instead this test replicates [AnalysisEngine.evaluate]'s own skeleton-derivation
 *    call VERBATIM (`archetypeFormat = ArchetypeFormat.of(format)`, then
 *    `ArchetypeSkeletonResolver.resolveWithColor(format = archetypeFormat, archetype, posture,
 *    themes, identity = colorIdentity, deckFormat = format)`), which is exactly the call shape the
 *    plan's own verified facts describe (`AnalysisEngine.kt` L162/L190). Any future drift in either
 *    call site's argument construction fails this test.
 * 2. [CuratedStrategy.toPin] round-trips through persisted raw-string form back to [nearestFor]
 *    resolving the SAME catalog id, for every Commander-available entry (1.3).
 */
class CommanderPlanResolverTest {

    private val identities: List<Set<ManaColor>> = listOf(
        setOf(ManaColor.G), // mono
        setOf(ManaColor.U, ManaColor.B), // 2-color
        setOf(ManaColor.W, ManaColor.U, ManaColor.B), // 3-color
        setOf(ManaColor.W, ManaColor.U, ManaColor.B, ManaColor.R, ManaColor.G), // 5-color
    )

    private val commanderFormats = listOf(DeckFormat.COMMANDER, DeckFormat.COMMANDER_CASUAL)

    /** Mirrors [AnalysisEngine.evaluate]'s own skeleton derivation exactly — see this class's KDoc. */
    private fun analysisEngineSkeleton(
        format: DeckFormat,
        archetype: ArchetypeId?,
        posture: PostureId?,
        themes: List<ThemeId>,
        identity: Set<ManaColor>,
    ): ResolvedArchetypeSkeleton {
        val archetypeFormat = ArchetypeFormat.of(format) ?: error("expected a Commander-shaped format")
        return ArchetypeSkeletonResolver.resolveWithColor(
            format = archetypeFormat,
            archetype = archetype,
            posture = posture,
            themes = themes,
            identity = identity,
            deckFormat = format,
        )
    }

    private fun testCommander(identity: Set<ManaColor>): Card = card(
        id = "cmd-${identity.joinToString("") { it.symbol }}",
        name = "Test Commander",
        typeLine = "Legendary Creature — Human",
        cmc = 4.0,
        colors = identity.map { it.symbol },
        colorIdentity = identity.map { it.symbol },
    )

    @Test
    fun `resolver skeleton matches AnalysisEngine's own resolveWithColor call for every Commander catalog entry`() {
        commanderFormats.forEach { format ->
            val entries = CuratedStrategyCatalog.ALL.filter { it.availableIn(format) }
            assertTrue(entries.isNotEmpty(), "expected at least one catalog entry for $format")

            entries.forEach { strategy ->
                val tribe = if (strategy.requiresTribe) "tribe:elf" else null
                val pin = strategy.toPin(tribe)

                identities.forEach { identity ->
                    val plan = CommanderPlanResolver.resolve(
                        format = format,
                        commander = testCommander(identity),
                        pick = StrategyPick.Curated(strategy, tribe),
                        identity = identity,
                    )
                    val expected = analysisEngineSkeleton(format, pin.archetype, pin.posture, pin.themes, identity)
                    assertEquals(
                        expected,
                        plan.skeleton,
                        "skeleton mismatch for strategy '${strategy.id}' format=$format identity=$identity",
                    )
                }
            }
        }
    }

    @Test
    fun `Custom with no commander tag signal resolves the generic baseline skeleton, never a null plan`() {
        // testCommander() carries no tags -- CommanderArchetypeBias.commanderTagArchetype returns
        // null (F18's build hint deliberately has NO color-identity tier, see its own KDoc), so the
        // F18 build hint is byte-for-byte inert for every identity and Custom keeps its pre-F18
        // generic-baseline behavior (F2), regardless of how many colors the commander is in.
        commanderFormats.forEach { format ->
            identities.forEach { identity ->
                val plan = CommanderPlanResolver.resolve(
                    format = format,
                    commander = testCommander(identity),
                    pick = StrategyPick.Custom,
                    identity = identity,
                )
                assertNull(plan.skeleton.archetype, "no commander tag signal must resolve the generic baseline (archetype == null)")
                assertNotNull(plan.skeleton, "F2: Custom must never resolve to a null/absent skeleton")
                val expected = analysisEngineSkeleton(format, null, null, emptyList(), identity)
                assertEquals(expected, plan.skeleton)
            }
        }
    }

    @Test
    fun `F18 -- Custom biases the internal skeleton toward the commander's own tag-derived archetype`() {
        // E12 -- a Custom build MAY aim internally at what the commander actually does. A
        // real Edgar-Markov-shaped commander (Vampire tribal lord, AGGRO-tagged) must resolve
        // AGGRO, not the generic baseline and never MIDRANGE.
        val identity = setOf(ManaColor.B, ManaColor.R)
        val edgarLike = card(
            id = "cmd-edgar-like",
            name = "Test Vampire Lord",
            typeLine = "Legendary Creature — Vampire Knight",
            cmc = 4.0,
            colors = listOf("B", "R"),
            colorIdentity = listOf("B", "R"),
            tags = listOf(com.mmg.manahub.core.model.CardTag.AGGRO),
        )
        commanderFormats.forEach { format ->
            val plan = CommanderPlanResolver.resolve(
                format = format,
                commander = edgarLike,
                pick = StrategyPick.Custom,
                identity = identity,
            )
            assertEquals(ArchetypeId.AGGRO, plan.skeleton.archetype, "F18: Edgar-shaped Custom must resolve AGGRO")
            val expected = analysisEngineSkeleton(format, ArchetypeId.AGGRO, null, emptyList(), identity)
            assertEquals(expected, plan.skeleton)
        }
    }


    @Test
    fun `toPin round-trips through persisted raw strings back to the same catalog id via nearestFor`() {
        commanderFormats.forEach { format ->
            CuratedStrategyCatalog.ALL.filter { it.availableIn(format) }.forEach { strategy ->
                val tribe = if (strategy.requiresTribe) "tribe:elf" else null
                val pin = strategy.toPin(tribe)

                // Mirror Deck.archetypeOverride/themesOverride/tribeOverride/postureOverride's raw
                // persisted string form (CLAUDE.md: never .valueOf(), always parse via
                // entries.firstOrNull { it.name == raw }).
                val archetypeRaw: String? = pin.archetype?.name
                val themesRaw: List<String> = pin.themes.map { it.name }
                val postureRaw: String? = pin.posture?.name
                val tribeRaw: String? = pin.tribe

                val parsedArchetype = archetypeRaw?.let { raw -> ArchetypeId.entries.firstOrNull { it.name == raw } }
                val parsedThemes = themesRaw.mapNotNull { raw -> ThemeId.entries.firstOrNull { it.name == raw } }
                val parsedPosture = postureRaw?.let { raw -> PostureId.entries.firstOrNull { it.name == raw } }

                val resolved = CuratedStrategyCatalog.nearestFor(
                    archetype = parsedArchetype,
                    themes = parsedThemes,
                    format = format,
                    posture = parsedPosture,
                )
                assertEquals(
                    strategy.id,
                    resolved?.id,
                    "round-trip failed for '${strategy.id}' (format=$format, tribeRaw=$tribeRaw)",
                )
            }
        }
    }
}
