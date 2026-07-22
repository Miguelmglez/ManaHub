package com.mmg.manahub.tools.tagpipeline.pipeline

import com.mmg.manahub.core.data.remote.dto.CardDto
import com.mmg.manahub.core.data.remote.dto.LegalitiesDto
import com.mmg.manahub.core.data.remote.dto.PricesDto
import com.mmg.manahub.tools.tagpipeline.scryfall.OracleTagDto
import com.mmg.manahub.tools.tagpipeline.scryfall.TaggingDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end pipeline test (plan's own "validate wiring with a small smoke-test slice" — the
 * fixture-based equivalent, requiring zero network access so it runs identically in CI and offline).
 * Exercises the FULL merge: rule engine + curated Tagger mapping + EDHREC theme index + `--since`
 * filtering, over hand-built [CardDto] fixtures (proving [com.mmg.manahub.core.data.remote.mapper
 * .toDomain] — the same mapper the live app uses — round-trips correctly too).
 */
class TagPipelineTest {

    private fun legalities() = LegalitiesDto(
        standard = "legal", pioneer = "legal", modern = "legal",
        legacy = "legal", vintage = "legal", commander = "legal", pauper = "legal",
    )

    private fun cardDto(
        id: String,
        oracleId: String,
        name: String,
        typeLine: String = "Instant",
        oracleText: String? = null,
        colorIdentity: List<String> = listOf("W"),
    ) = CardDto(
        id = id,
        oracleId = oracleId,
        name = name,
        lang = "en",
        colorIdentity = colorIdentity,
        typeLine = typeLine,
        oracleText = oracleText,
        keywords = emptyList(),
        setCode = "tst",
        setName = "Test Set",
        collectorNumber = "1",
        rarity = "rare",
        releasedAt = "2024-01-01",
        prices = PricesDto(),
        legalities = legalities(),
        scryfallUri = "https://scryfall.com/card/tst/1",
    )

    @Test
    fun `merges rule-engine tags, curated Tagger tags, and EDHREC themes for one card`() {
        val swords = cardDto(
            id = "scry-1", oracleId = "oracle-1", name = "Swords to Plowshares",
            oracleText = "Exile target creature. Its controller gains life equal to its power.",
        )
        val pipeline = TagPipeline()
        val rows = pipeline.buildRows(
            cardDtos = sequenceOf(swords),
            oracleTagRows = sequenceOf(
                OracleTagDto(slug = "removal", type = "oracle", taggings = listOf(TaggingDto("oracle-1"))),
            ),
            themeIndexByCardName = mapOf("Swords to Plowshares" to mapOf("VOLTRON" to 0.4f)),
            generatedAt = "2026-07-20T00:00:00Z",
            pipelineVersion = 1,
        ).toList()

        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals("oracle-1", row.oracleId)
        assertTrue("removal" in row.tags, "rule engine should tag Swords to Plowshares as removal")
        assertEquals(mapOf("VOLTRON" to 0.4f), row.themes)
        assertEquals(setOf("rule_engine", "oracle_tags", "edhrec"), row.sources.toSet())
        assertEquals(1, row.pipelineVersion)
        assertEquals("2026-07-20T00:00:00Z", row.generatedAt)
    }

    @Test
    fun `a card with no Tagger or EDHREC hit still gets rule-engine tags and sources is just rule_engine`() {
        val wrath = cardDto(
            id = "scry-2", oracleId = "oracle-2", name = "Wrath of God", typeLine = "Sorcery",
            oracleText = "Destroy all creatures. They can't be regenerated.",
        )
        val pipeline = TagPipeline()
        val row = pipeline.buildRows(
            cardDtos = sequenceOf(wrath),
            oracleTagRows = emptySequence(),
            themeIndexByCardName = emptyMap(),
            generatedAt = "2026-07-20T00:00:00Z",
        ).single()

        assertTrue("board_wipe" in row.tags)
        assertEquals(listOf("rule_engine"), row.sources)
        assertTrue(row.themes.isEmpty())
    }

    @Test
    fun `a card with a blank oracle_id is skipped, never crashes the run`() {
        val noOracleId = cardDto(id = "scry-3", oracleId = "", name = "Weird Card")
        val pipeline = TagPipeline()
        val rows = pipeline.buildRows(
            cardDtos = sequenceOf(noOracleId),
            oracleTagRows = emptySequence(),
            themeIndexByCardName = emptyMap(),
            generatedAt = "2026-07-20T00:00:00Z",
        ).toList()
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `--since skips a card already processed at the current pipeline_version`() {
        val cardA = cardDto(id = "scry-a", oracleId = "oracle-a", name = "Card A")
        val cardB = cardDto(id = "scry-b", oracleId = "oracle-b", name = "Card B")
        val pipeline = TagPipeline()
        val rows = pipeline.buildRows(
            cardDtos = sequenceOf(cardA, cardB),
            oracleTagRows = emptySequence(),
            themeIndexByCardName = emptyMap(),
            generatedAt = "2026-07-20T00:00:00Z",
            pipelineVersion = 2,
            since = SinceFilter(manifest = mapOf("oracle-a" to 2, "oracle-b" to 1)),
        ).toList()

        // oracle-a is already at the CURRENT version -> skipped; oracle-b is older -> reprocessed.
        assertEquals(listOf("oracle-b"), rows.map { it.oracleId })
    }

    @Test
    fun `output rows are deterministic across two runs with identical inputs`() {
        val card = cardDto(
            id = "scry-x", oracleId = "oracle-x", name = "Chaos Card",
            oracleText = "Destroy target creature. Search your library for a card. Draw a card.",
        )
        val pipeline = TagPipeline()
        fun run() = pipeline.buildRows(
            cardDtos = sequenceOf(card),
            oracleTagRows = sequenceOf(
                OracleTagDto(slug = "removal", type = "oracle", taggings = listOf(TaggingDto("oracle-x"))),
                OracleTagDto(slug = "tutor", type = "oracle", taggings = listOf(TaggingDto("oracle-x"))),
            ),
            themeIndexByCardName = mapOf("Chaos Card" to mapOf("SPELLSLINGER" to 0.2f, "ARISTOCRATS" to 0.1f)),
            generatedAt = "2026-07-20T00:00:00Z",
        ).single()

        val first = run()
        val second = run()
        assertEquals(first, second)
        // Determinism means sorted, not just "equal by chance" — assert the sort explicitly too.
        assertEquals(first.tags.sorted(), first.tags)
        assertEquals(first.sources.sorted(), first.sources)
        assertEquals(first.themes.keys.sorted(), first.themes.keys.toList())
    }

    @Test
    fun `an oracle_id with a resolved Archidekt category gets it unioned into tags plus the archidekt source`() {
        val card = cardDto(id = "scry-z", oracleId = "oracle-z", name = "Card Z", typeLine = "Creature")
        val pipeline = TagPipeline()
        val row = pipeline.buildRows(
            cardDtos = sequenceOf(card),
            oracleTagRows = emptySequence(),
            themeIndexByCardName = emptyMap(),
            generatedAt = "2026-07-21T00:00:00Z",
            archidektCategoryByOracleId = mapOf("oracle-z" to "ramp"),
        ).single()

        assertTrue("ramp" in row.tags)
        assertEquals("ramp", row.archidektCategory)
        assertTrue("archidekt" in row.sources)
    }

    @Test
    fun `a card absent from the Archidekt enrichment map is unaffected (archidektCategory null, no source)`() {
        val card = cardDto(id = "scry-w", oracleId = "oracle-w", name = "Card W")
        val pipeline = TagPipeline()
        val row = pipeline.buildRows(
            cardDtos = sequenceOf(card),
            oracleTagRows = emptySequence(),
            themeIndexByCardName = emptyMap(),
            generatedAt = "2026-07-21T00:00:00Z",
            archidektCategoryByOracleId = mapOf("some-other-oracle-id" to "ramp"),
        ).single()

        assertEquals(null, row.archidektCategory)
        assertFalse("archidekt" in row.sources)
    }

    @Test
    fun `default archidektCategoryByOracleId is empty, backward compatible with existing call sites`() {
        val card = cardDto(id = "scry-v", oracleId = "oracle-v", name = "Card V")
        val pipeline = TagPipeline()
        // No archidektCategoryByOracleId argument at all - must still compile and behave as before.
        val row = pipeline.buildRows(
            cardDtos = sequenceOf(card),
            oracleTagRows = emptySequence(),
            themeIndexByCardName = emptyMap(),
            generatedAt = "2026-07-21T00:00:00Z",
        ).single()
        assertEquals(null, row.archidektCategory)
    }

    // ── tags dedup across sources (spot-checked 2026-07-21 alongside the EDHREC fix) ────────────

    @Test
    fun `a tag confirmed by BOTH the rule engine and oracle_tags appears exactly once in the final tags`() {
        // Swords to Plowshares' oracle text ("Exile target creature...") makes the rule engine
        // confirm "removal" on its own; the oracle_tags "removal" slug ALSO maps to CardTag
        // "removal" via TAGGER_TAG_TO_CARD_TAG. Both sources plausibly emitting the literal same
        // string is exactly the case that would surface a raw-concatenation dedup bug (tags built
        // via List `+` instead of Set `+`) — the merge in TagPipeline.buildRows is Set<String> +
        // Set<String> + Set<String>, which structurally cannot produce a duplicate.
        val swords = cardDto(
            id = "scry-dedup", oracleId = "oracle-dedup", name = "Swords to Plowshares",
            oracleText = "Exile target creature. Its controller gains life equal to its power.",
        )
        val pipeline = TagPipeline()
        val row = pipeline.buildRows(
            cardDtos = sequenceOf(swords),
            oracleTagRows = sequenceOf(
                OracleTagDto(slug = "removal", type = "oracle", taggings = listOf(TaggingDto("oracle-dedup"))),
            ),
            themeIndexByCardName = emptyMap(),
            generatedAt = "2026-07-21T00:00:00Z",
        ).single()

        assertEquals(1, row.tags.count { it == "removal" }, "removal must appear exactly once even though both the rule engine and oracle_tags confirm it")
        assertEquals(row.tags.distinct(), row.tags, "the whole tags list must have zero duplicate entries, not just 'removal'")
    }

    @Test
    fun `a tag confirmed by BOTH the rule engine and Archidekt category appears exactly once`() {
        // A ramp spell the rule engine independently confirms as "ramp" (oracle text: "Search your
        // library for a basic land card"), where the Archidekt enrichment pass ALSO resolved a
        // dominant category of "ramp" for the same oracle_id — the third real overlapping-source
        // pairing (rule_engine vs. archidekt, the first test above covers rule_engine vs. oracle_tags).
        val rampSpell = cardDto(
            id = "scry-dedup2", oracleId = "oracle-dedup2", name = "Rampant Growth",
            oracleText = "Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.",
        )
        val pipeline = TagPipeline()

        // First confirm the rule engine ALONE (no Archidekt) already tags this "ramp" -- otherwise
        // the assertion below would pass trivially from Archidekt alone and prove nothing about dedup.
        val ruleEngineOnlyRow = pipeline.buildRows(
            cardDtos = sequenceOf(rampSpell),
            oracleTagRows = emptySequence(),
            themeIndexByCardName = emptyMap(),
            generatedAt = "2026-07-21T00:00:00Z",
        ).single()
        assertTrue("ramp" in ruleEngineOnlyRow.tags, "test setup invalid: the rule engine must independently confirm 'ramp' for this fixture")

        val row = pipeline.buildRows(
            cardDtos = sequenceOf(rampSpell),
            oracleTagRows = emptySequence(),
            themeIndexByCardName = emptyMap(),
            generatedAt = "2026-07-21T00:00:00Z",
            archidektCategoryByOracleId = mapOf("oracle-dedup2" to "ramp"),
        ).single()

        assertEquals(1, row.tags.count { it == "ramp" }, "ramp must appear exactly once even though both the rule engine and Archidekt confirm it")
        assertEquals(row.tags.distinct(), row.tags)
    }

    // ── archetypes (fixed 2026-07-21 — previously hardcoded to emptyMap() for every row) ────────

    @Test
    fun `merges EDHREC archetype affinities into the archetypes field and edhrec source`() {
        val card = cardDto(id = "scry-arch1", oracleId = "oracle-arch1", name = "Ramp Card")
        val pipeline = TagPipeline()
        val row = pipeline.buildRows(
            cardDtos = sequenceOf(card),
            oracleTagRows = emptySequence(),
            themeIndexByCardName = emptyMap(),
            generatedAt = "2026-07-21T00:00:00Z",
            archetypeIndexByCardName = mapOf("Ramp Card" to mapOf("RAMP" to 0.3f)),
        ).single()

        assertEquals(mapOf("RAMP" to 0.3f), row.archetypes)
        assertTrue("edhrec" in row.sources)
    }

    @Test
    fun `a card with both a theme and an archetype hit gets both, sources still has one edhrec entry`() {
        val card = cardDto(id = "scry-arch2", oracleId = "oracle-arch2", name = "Hybrid Card")
        val pipeline = TagPipeline()
        val row = pipeline.buildRows(
            cardDtos = sequenceOf(card),
            oracleTagRows = emptySequence(),
            themeIndexByCardName = mapOf("Hybrid Card" to mapOf("TOKENS" to 0.2f)),
            generatedAt = "2026-07-21T00:00:00Z",
            archetypeIndexByCardName = mapOf("Hybrid Card" to mapOf("AGGRO" to 0.4f)),
        ).single()

        assertEquals(mapOf("TOKENS" to 0.2f), row.themes)
        assertEquals(mapOf("AGGRO" to 0.4f), row.archetypes)
        assertEquals(listOf("edhrec", "rule_engine"), row.sources)
    }

    @Test
    fun `default archetypeIndexByCardName is empty, backward compatible with existing call sites`() {
        val card = cardDto(id = "scry-arch3", oracleId = "oracle-arch3", name = "No Archetype Card")
        val pipeline = TagPipeline()
        // No archetypeIndexByCardName argument at all - must still compile and behave as before.
        val row = pipeline.buildRows(
            cardDtos = sequenceOf(card),
            oracleTagRows = emptySequence(),
            themeIndexByCardName = emptyMap(),
            generatedAt = "2026-07-21T00:00:00Z",
        ).single()
        assertTrue(row.archetypes.isEmpty())
    }

    @Test
    fun `EDHREC theme index resolves by exact card name, a mismatched name yields no themes`() {
        val card = cardDto(id = "scry-y", oracleId = "oracle-y", name = "Exact Name")
        val pipeline = TagPipeline()
        val row = pipeline.buildRows(
            cardDtos = sequenceOf(card),
            oracleTagRows = emptySequence(),
            themeIndexByCardName = mapOf("exact name" to mapOf("TOKENS" to 0.9f)), // wrong case
            generatedAt = "2026-07-20T00:00:00Z",
        ).single()
        assertFalse("edhrec" in row.sources)
        assertTrue(row.themes.isEmpty())
    }
}
