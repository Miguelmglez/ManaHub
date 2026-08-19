package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.TagCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// ═══════════════════════════════════════════════════════════════════════════════
//  DeckAnalysisEngineGoldenTest — Deck Analysis Engine v2 plan, Phase 2 / §4 "Tests".
//
//  Golden fixtures for [AnalysisEngine] (the new, unified pillar pipeline) covering 6 Commander
//  reference archetypes -- aggro / control / combo / tokens / reanimator / landfall -- plus the 3
//  behavior-specific assertions the plan calls out explicitly: a strategy switch CHANGES totalScore
//  AND P3 role coverage (the bug this whole plan exists to fix), an anti-role over its tolerance
//  fires a finding, and an illegal card caps the total score.
//
//  DEVIATION FROM THE LITERAL "99-distinct-card" SPEC (documented per the plan's own gate item h):
//  each reference deck uses ~20-28 DISTINCT, real, well-known staples (singleton, quantity 1 --
//  legible, easy to audit) plus ONE bulk basic-land [DeckEntry] whose quantity closes the gap to
//  the legal Commander mainboard size (100 incl. the commander). Real Commander decklists commonly
//  list their basics exactly this way ("37x Mountain"), so this is not an unrealistic shape -- it
//  keeps the fixtures maintainable (the task's own stated goal) while still exercising real
//  per-role signal on every non-land card. Land counts are consequently NOT tuned to the skeleton's
//  ideal (a [Finding.LandCountOffTarget] WARNING is expected and harmless for these fixtures --
//  P1 calibration is explicitly Phase 4, not this phase).
// ═══════════════════════════════════════════════════════════════════════════════

class DeckAnalysisEngineGoldenTest {

    private val scorer = DeckScorer(RoleClassifier())

    private fun profileFor(mainboard: List<DeckEntry>, colorIdentity: Set<ManaColor>): DeckProfile =
        scorer.profile(mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, seedTags = emptyList())

    /** A bulk basic-land entry, closing the mainboard to exactly 100 cards (commander included). */
    private fun basics(landName: String, symbol: String, quantity: Int): DeckEntry = entry(
        card(
            id = "land-$landName",
            name = landName,
            typeLine = "Basic Land — $landName",
            cmc = 0.0,
            colors = emptyList(),
            colorIdentity = listOf(symbol),
            producedMana = symbol,
        ),
        quantity = quantity,
    )

    private fun roleTag(key: String): CardTag = CardTag(key, TagCategory.ROLE)

    /** Pads [nonland] (commander + spells) up to exactly 100 total with a single bulk basic-land
     * entry, and returns the full mainboard (nonland + that land entry). */
    private fun withBasics(nonland: List<DeckEntry>, landName: String, symbol: String): List<DeckEntry> {
        val nonlandCount = nonland.sumOf { it.quantity }
        val landQty = (100 - nonlandCount).coerceAtLeast(1)
        return nonland + basics(landName, symbol, landQty)
    }

    // ── 6 Commander reference decks ─────────────────────────────────────────────────────────

    @Test
    fun aggroReferenceDeck_evaluatesCleanlyAgainstAggroStrategy() {
        val commander = card(id = "cmd-krenko", name = "Krenko, Mob Boss", typeLine = "Legendary Creature — Goblin", cmc = 3.0, colorIdentity = listOf("R"))
        val nonland = listOf(
            entry(commander),
            entry(card(id = "goblin-guide", name = "Goblin Guide", typeLine = "Creature — Goblin", cmc = 1.0, colorIdentity = listOf("R"), power = "2", toughness = "2")),
            entry(card(id = "zurgo", name = "Zurgo Bellstriker", typeLine = "Creature — Orc Warrior", cmc = 1.0, colorIdentity = listOf("R"), power = "3", toughness = "2")),
            entry(card(id = "falkenrath-gorger", name = "Falkenrath Gorger", typeLine = "Creature — Vampire", cmc = 1.0, colorIdentity = listOf("R"), power = "3", toughness = "1")),
            entry(card(id = "bomat-courier", name = "Bomat Courier", typeLine = "Artifact Creature — Vehicle", cmc = 1.0, colorIdentity = listOf("R"), power = "2", toughness = "1")),
            entry(card(id = "kari-zev", name = "Kari Zev, Skyship Raider", typeLine = "Legendary Creature — Human Pirate", cmc = 2.0, colorIdentity = listOf("R"), power = "3", toughness = "2")),
            entry(card(id = "ash-zealot", name = "Ash Zealot", typeLine = "Creature — Human Warrior", cmc = 2.0, colorIdentity = listOf("R"), power = "3", toughness = "2")),
            entry(card(id = "hazoret", name = "Hazoret the Fervent", typeLine = "Legendary Creature — God", cmc = 3.0, colorIdentity = listOf("R"), power = "3", toughness = "4")),
            entry(card(id = "bolt", name = "Lightning Bolt", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))),
            entry(card(id = "abrade", name = "Abrade", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))),
            entry(card(id = "skewer", name = "Skewer the Critics", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))),
            entry(card(id = "skirk-prospector", name = "Skirk Prospector", typeLine = "Creature — Goblin", cmc = 1.0, colorIdentity = listOf("R"), tags = listOf(CardTag.RAMP))),
            entry(card(id = "simian-guide", name = "Simian Spirit Guide", typeLine = "Creature — Ape", cmc = 1.0, colorIdentity = listOf("R"), tags = listOf(CardTag.RAMP))),
            entry(card(id = "wheel", name = "Wheel of Fortune", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("R"), tags = listOf(CardTag.DRAW_ENGINE))),
            entry(card(id = "purphoros", name = "Purphoros, God of the Forge", typeLine = "Legendary Enchantment Creature — God", cmc = 5.0, colorIdentity = listOf("R"), tags = listOf(CardTag.WIN_CON))),
            entry(card(id = "skullclamp", name = "Skullclamp", typeLine = "Artifact — Equipment", cmc = 1.0, colorIdentity = emptyList())),
            entry(card(id = "embercleave", name = "Embercleave", typeLine = "Legendary Artifact — Equipment", cmc = 6.0, colorIdentity = listOf("R"))),
            entry(card(id = "chandra", name = "Chandra, Torch of Defiance", typeLine = "Legendary Planeswalker — Chandra", cmc = 4.0, colorIdentity = listOf("R"))),
            entry(card(id = "torbran", name = "Torbran, Thane of Red Fell", typeLine = "Legendary Creature — Dwarf Noble", cmc = 4.0, colorIdentity = listOf("R"), power = "3", toughness = "3")),
            entry(card(id = "seasoned-pyro", name = "Seasoned Pyromancer", typeLine = "Creature — Human Shaman", cmc = 2.0, colorIdentity = listOf("R"), power = "2", toughness = "2")),
            entry(card(id = "ragavan", name = "Ragavan, Nimble Pilferer", typeLine = "Legendary Creature — Monkey Pirate", cmc = 1.0, colorIdentity = listOf("R"), power = "2", toughness = "1")),
            entry(card(id = "sol-ring", name = "Sol Ring", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
            entry(card(id = "arcane-signet", name = "Arcane Signet", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList())),
            entry(card(id = "fireblast", name = "Fireblast", typeLine = "Instant", cmc = 4.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))),
            entry(card(id = "shock", name = "Shock", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))),
        )
        val mainboard = withBasics(nonland, "Mountain", "R")
        val colorIdentity = setOf(ManaColor.R)
        val profile = profileFor(mainboard, colorIdentity)

        val analysis = AnalysisEngine.evaluate(
            mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, profile = profile,
            archetype = ArchetypeId.AGGRO, themes = emptyList(), isManualOverride = true, confidence = 1f,
        )

        assertEquals(5, analysis.pillars.size)
        assertTrue(analysis.pillars.first { it.id == PillarId.PLAN_ROLES }.roleCoverage.isNotEmpty())
        // No P5 BLOCKER -- a legal, correctly-sized (100 incl. commander) singleton Commander deck.
        assertTrue(analysis.pillars.first { it.id == PillarId.LEGALITY }.findings.none { it.severity == FindingSeverity.BLOCKER })
        assertTrue(analysis.totalScore in 0..100)
    }

    @Test
    fun controlReferenceDeck_evaluatesCleanlyAgainstControlStrategy() {
        val commander = card(id = "cmd-talrand", name = "Talrand, Sky Summoner", typeLine = "Legendary Creature — Merfolk Wizard", cmc = 3.0, colorIdentity = listOf("U"))
        val nonland = listOf(
            entry(commander),
            entry(card(id = "counterspell", name = "Counterspell", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Counter target spell.")),
            entry(card(id = "mana-drain", name = "Mana Drain", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"), oracleText = "Counter target spell.")),
            entry(card(id = "cryptic", name = "Cryptic Command", typeLine = "Instant", cmc = 4.0, colorIdentity = listOf("U"), oracleText = "Choose two — Counter target spell.")),
            entry(card(id = "swan-song", name = "Swan Song", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), oracleText = "Counter target enchantment, artifact, or spell.")),
            entry(card(id = "wrath", name = "Wrath of God", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("W"), tags = listOf(CardTag.WRATH))),
            entry(card(id = "toxic-deluge", name = "Toxic Deluge", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("B"), tags = listOf(CardTag.WRATH))),
            entry(card(id = "supreme-verdict", name = "Supreme Verdict", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("U", "W"), tags = listOf(CardTag.WRATH))),
            entry(card(id = "swords", name = "Swords to Plowshares", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
            entry(card(id = "path", name = "Path to Exile", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
            entry(card(id = "fact-fiction", name = "Fact or Fiction", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
            entry(card(id = "rhystic", name = "Rhystic Study", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
            entry(card(id = "consecrated", name = "Consecrated Sphinx", typeLine = "Creature — Sphinx", cmc = 6.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE), power = "4", toughness = "6")),
            entry(card(id = "demonic-tutor", name = "Demonic Tutor", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), tags = listOf(CardTag.TUTOR))),
            entry(card(id = "arcane-signet", name = "Arcane Signet", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList())),
            entry(card(id = "sol-ring", name = "Sol Ring", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
            entry(card(id = "farseek", name = "Farseek", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("G"), tags = listOf(CardTag.RAMP))),
            entry(card(id = "jace-tms", name = "Jace, the Mind Sculptor", typeLine = "Legendary Planeswalker — Jace", cmc = 4.0, colorIdentity = listOf("U"), tags = listOf(CardTag.WIN_CON))),
            entry(card(id = "elspeth", name = "Elspeth, Sun's Champion", typeLine = "Legendary Planeswalker — Elspeth", cmc = 6.0, colorIdentity = listOf("W"))),
            entry(card(id = "esper-sentinel", name = "Esper Sentinel", typeLine = "Artifact Creature — Human Soldier", cmc = 1.0, colorIdentity = listOf("W"), power = "1", toughness = "1")),
            entry(card(id = "batterskull", name = "Batterskull", typeLine = "Legendary Artifact — Equipment", cmc = 5.0, colorIdentity = emptyList())),
            entry(card(id = "gilded-lotus", name = "Gilded Lotus", typeLine = "Artifact", cmc = 5.0, colorIdentity = emptyList())),
            entry(card(id = "vindicate", name = "Vindicate", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("W", "B"), tags = listOf(CardTag.REMOVAL))),
        )
        val mainboard = withBasics(nonland, "Island", "U")
        val colorIdentity = setOf(ManaColor.U, ManaColor.W, ManaColor.B, ManaColor.G)
        val profile = profileFor(mainboard, colorIdentity)

        val analysis = AnalysisEngine.evaluate(
            mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, profile = profile,
            archetype = ArchetypeId.CONTROL, themes = emptyList(), isManualOverride = true, confidence = 1f,
        )

        assertTrue(analysis.pillars.first { it.id == PillarId.PLAN_ROLES }.roleCoverage.isNotEmpty())
        assertTrue(analysis.totalScore in 0..100)
    }

    @Test
    fun comboReferenceDeck_evaluatesCleanlyAgainstComboStrategy() {
        val commander = card(id = "cmd-korvold", name = "Kinnan, Bonder Prodigy", typeLine = "Legendary Creature — Human Wizard", cmc = 2.0, colorIdentity = listOf("U", "G"))
        val nonland = listOf(
            entry(commander),
            entry(card(id = "basalt-monolith", name = "Basalt Monolith", typeLine = "Artifact", cmc = 3.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
            entry(card(id = "rings-of-brighthearth", name = "Rings of Brighthearth", typeLine = "Artifact", cmc = 4.0, colorIdentity = emptyList())),
            entry(card(id = "demonic-tutor", name = "Demonic Tutor", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), tags = listOf(CardTag.TUTOR))),
            entry(card(id = "vampiric-tutor", name = "Vampiric Tutor", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("B"), tags = listOf(CardTag.TUTOR))),
            entry(card(id = "mystical-tutor", name = "Mystical Tutor", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), tags = listOf(CardTag.TUTOR))),
            entry(card(id = "protean-hulk", name = "Protean Hulk", typeLine = "Creature — Shapeshifter", cmc = 5.0, colorIdentity = listOf("G"), tags = listOf(CardTag.COMBO, CardTag.INFINITE), power = "6", toughness = "6")),
            entry(card(id = "thassas-oracle", name = "Thassa's Oracle", typeLine = "Creature — Merfolk Wizard", cmc = 1.0, colorIdentity = listOf("U"), tags = listOf(CardTag.WIN_CON), power = "1", toughness = "3")),
            entry(card(id = "swan-song", name = "Swan Song", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), oracleText = "Counter target enchantment, artifact, or spell.")),
            entry(card(id = "fierce-guardianship", name = "Fierce Guardianship", typeLine = "Instant", cmc = 5.0, colorIdentity = listOf("U"), oracleText = "Counter target spell.")),
            entry(card(id = "swift-protection", name = "Swiftfoot Boots", typeLine = "Artifact — Equipment", cmc = 2.0, colorIdentity = emptyList(), oracleText = "Equipped creature has hexproof.")),
            entry(card(id = "lightning-greaves", name = "Lightning Greaves", typeLine = "Artifact — Equipment", cmc = 2.0, colorIdentity = emptyList(), oracleText = "Equipped creature has haste and shroud.")),
            entry(card(id = "sol-ring", name = "Sol Ring", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
            entry(card(id = "mana-crypt", name = "Mana Crypt", typeLine = "Artifact", cmc = 0.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
            entry(card(id = "brainstorm", name = "Brainstorm", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
            entry(card(id = "ponder", name = "Ponder", typeLine = "Sorcery", cmc = 1.0, colorIdentity = listOf("U"), tags = listOf(CardTag.DRAW_ENGINE))),
            entry(card(id = "swords", name = "Swords to Plowshares", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
            entry(card(id = "beast-within", name = "Beast Within", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("G"), tags = listOf(CardTag.REMOVAL))),
            entry(card(id = "arcane-signet", name = "Arcane Signet", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList())),
            entry(card(id = "cyclonic-rift", name = "Cyclonic Rift", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("U"))),
        )
        val mainboard = withBasics(nonland, "Island", "U")
        val colorIdentity = setOf(ManaColor.U, ManaColor.G, ManaColor.B, ManaColor.W)
        val profile = profileFor(mainboard, colorIdentity)

        val analysis = AnalysisEngine.evaluate(
            mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, profile = profile,
            archetype = ArchetypeId.COMBO, themes = emptyList(), isManualOverride = true, confidence = 1f,
        )

        assertTrue(analysis.pillars.first { it.id == PillarId.PLAN_ROLES }.roleCoverage.isNotEmpty())
        assertTrue(analysis.totalScore in 0..100)
    }

    @Test
    fun tokensReferenceDeck_evaluatesCleanlyAgainstTokensStrategy() {
        val commander = card(id = "cmd-krenko-tk", name = "Krenko, Mob Boss", typeLine = "Legendary Creature — Goblin", cmc = 3.0, colorIdentity = listOf("R"))
        val nonland = listOf(
            entry(commander),
            entry(card(id = "goblin-rally", name = "Goblin Rally", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("R"), tags = listOf(roleTag("token_generator")))),
            entry(card(id = "purphoros-tk", name = "Purphoros, God of the Forge", typeLine = "Legendary Enchantment Creature — God", cmc = 5.0, colorIdentity = listOf("R"), tags = listOf(CardTag.WIN_CON))),
            entry(card(id = "impact-tremors", name = "Impact Tremors", typeLine = "Enchantment", cmc = 2.0, colorIdentity = listOf("R"), tags = listOf(roleTag("token_generator")))),
            entry(card(id = "hanweir-battlements", name = "Hanweir Battlements", typeLine = "Legendary Land", cmc = 0.0, colorIdentity = emptyList())),
            entry(card(id = "krenko-tin-street", name = "Krenko, Tin Street Kingpin", typeLine = "Creature — Goblin", cmc = 2.0, colorIdentity = listOf("R"), tags = listOf(roleTag("token_generator")), power = "2", toughness = "2")),
            entry(card(id = "goblin-bombardment", name = "Goblin Bombardment", typeLine = "Enchantment", cmc = 2.0, colorIdentity = listOf("R"), tags = listOf(roleTag("sac_outlet")))),
            entry(card(id = "skullclamp", name = "Skullclamp", typeLine = "Artifact — Equipment", cmc = 1.0, colorIdentity = emptyList())),
            entry(card(id = "fervor-tk", name = "Fervor", typeLine = "Enchantment", cmc = 2.0, colorIdentity = listOf("R"))),
            entry(card(id = "bolt-tk", name = "Lightning Bolt", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))),
            entry(card(id = "abrade-tk", name = "Abrade", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))),
            entry(card(id = "sol-ring-tk", name = "Sol Ring", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
            entry(card(id = "skirk-prospector-tk", name = "Skirk Prospector", typeLine = "Creature — Goblin", cmc = 1.0, colorIdentity = listOf("R"), tags = listOf(CardTag.RAMP))),
            entry(card(id = "wheel-tk", name = "Wheel of Fortune", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("R"), tags = listOf(CardTag.DRAW_ENGINE))),
            entry(card(id = "goblin-guide-tk", name = "Goblin Guide", typeLine = "Creature — Goblin", cmc = 1.0, colorIdentity = listOf("R"), power = "2", toughness = "2")),
            entry(card(id = "kiki-tk", name = "Kiki-Jiki, Mirror Breaker", typeLine = "Legendary Creature — Goblin Shaman", cmc = 4.0, colorIdentity = listOf("R"), power = "2", toughness = "2")),
            entry(card(id = "chandra-tk", name = "Chandra, Torch of Defiance", typeLine = "Legendary Planeswalker — Chandra", cmc = 4.0, colorIdentity = listOf("R"))),
            entry(card(id = "arcane-signet-tk", name = "Arcane Signet", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList())),
        )
        val mainboard = withBasics(nonland, "Mountain", "R")
        val colorIdentity = setOf(ManaColor.R)
        val profile = profileFor(mainboard, colorIdentity)

        val analysis = AnalysisEngine.evaluate(
            mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, profile = profile,
            archetype = ArchetypeId.AGGRO, themes = listOf(ThemeId.TOKENS), isManualOverride = true, confidence = 1f,
        )

        assertTrue(analysis.pillars.first { it.id == PillarId.PLAN_ROLES }.roleCoverage.any { it.roleKey == "token_generator" })
        assertTrue(analysis.totalScore in 0..100)
    }

    @Test
    fun reanimatorReferenceDeck_evaluatesCleanlyAgainstReanimatorStrategy() {
        val commander = card(id = "cmd-meren", name = "Meren of Clan Nel Toth", typeLine = "Legendary Creature — Human Shaman", cmc = 4.0, colorIdentity = listOf("B", "G"))
        val nonland = listOf(
            entry(commander),
            entry(card(id = "reanimate", name = "Reanimate", typeLine = "Sorcery", cmc = 1.0, colorIdentity = listOf("B"), tags = listOf(roleTag("reanimation")))),
            entry(card(id = "animate-dead", name = "Animate Dead", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), tags = listOf(roleTag("reanimation")))),
            entry(card(id = "necromancy", name = "Necromancy", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("B"), tags = listOf(roleTag("reanimation")))),
            entry(card(id = "entomb", name = "Entomb", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("B"), tags = listOf(roleTag("graveyard_enabler")))),
            entry(card(id = "buried-alive", name = "Buried Alive", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), tags = listOf(roleTag("graveyard_enabler")))),
            entry(card(id = "faithless-looting", name = "Faithless Looting", typeLine = "Sorcery", cmc = 1.0, colorIdentity = listOf("R"), tags = listOf(roleTag("graveyard_enabler")))),
            entry(card(id = "griselbrand", name = "Griselbrand", typeLine = "Legendary Creature — Demon", cmc = 8.0, colorIdentity = listOf("B"), tags = listOf(CardTag.WIN_CON), power = "7", toughness = "7")),
            entry(card(id = "sheoldred", name = "Sheoldred, the Apocalypse", typeLine = "Legendary Creature — Phyrexian Praetor", cmc = 4.0, colorIdentity = listOf("B"), power = "4", toughness = "5")),
            entry(card(id = "demonic-tutor-re", name = "Demonic Tutor", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), tags = listOf(CardTag.TUTOR))),
            entry(card(id = "swords-re", name = "Swords to Plowshares", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("W"), tags = listOf(CardTag.REMOVAL))),
            entry(card(id = "toxic-deluge-re", name = "Toxic Deluge", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("B"), tags = listOf(CardTag.WRATH))),
            entry(card(id = "sol-ring-re", name = "Sol Ring", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
            entry(card(id = "farseek-re", name = "Farseek", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("G"), tags = listOf(CardTag.RAMP))),
            entry(card(id = "phyrexian-arena", name = "Phyrexian Arena", typeLine = "Enchantment", cmc = 2.0, colorIdentity = listOf("B"), tags = listOf(CardTag.DRAW_ENGINE))),
            entry(card(id = "arcane-signet-re", name = "Arcane Signet", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList())),
            entry(card(id = "eternal-witness", name = "Eternal Witness", typeLine = "Creature — Elf Shaman", cmc = 3.0, colorIdentity = listOf("G"), power = "2", toughness = "1")),
            entry(card(id = "beast-within-re", name = "Beast Within", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("G"), tags = listOf(CardTag.REMOVAL))),
        )
        val mainboard = withBasics(nonland, "Swamp", "B")
        val colorIdentity = setOf(ManaColor.B, ManaColor.G, ManaColor.W, ManaColor.R)
        val profile = profileFor(mainboard, colorIdentity)

        val analysis = AnalysisEngine.evaluate(
            mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, profile = profile,
            archetype = ArchetypeId.MIDRANGE, themes = listOf(ThemeId.REANIMATOR), isManualOverride = true, confidence = 1f,
        )

        assertTrue(analysis.pillars.first { it.id == PillarId.PLAN_ROLES }.roleCoverage.any { it.roleKey == "reanimation" })
        assertTrue(analysis.totalScore in 0..100)
    }

    @Test
    fun landfallReferenceDeck_evaluatesCleanlyAgainstLandfallStrategy() {
        val commander = card(id = "cmd-omnath", name = "Omnath, Locus of Rage", typeLine = "Legendary Creature — Elemental", cmc = 5.0, colorIdentity = listOf("R", "G"))
        val nonland = listOf(
            entry(commander),
            entry(card(id = "avenger-of-zendikar", name = "Avenger of Zendikar", typeLine = "Creature — Plant", cmc = 6.0, colorIdentity = listOf("G"), tags = listOf(roleTag("landfall_payoff")), power = "5", toughness = "5")),
            entry(card(id = "scute-swarm", name = "Scute Swarm", typeLine = "Creature — Insect", cmc = 3.0, colorIdentity = listOf("G"), tags = listOf(roleTag("landfall_payoff")), power = "1", toughness = "1")),
            entry(card(id = "tireless-tracker", name = "Tireless Tracker", typeLine = "Creature — Human Scout", cmc = 3.0, colorIdentity = listOf("G"), tags = listOf(roleTag("landfall_payoff")), power = "3", toughness = "2")),
            entry(card(id = "rampaging-baloths", name = "Rampaging Baloths", typeLine = "Creature — Beast", cmc = 5.0, colorIdentity = listOf("G"), tags = listOf(roleTag("landfall_payoff")), power = "6", toughness = "6")),
            entry(card(id = "cultivate", name = "Cultivate", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("G"), tags = listOf(CardTag.RAMP))),
            entry(card(id = "kodamas-reach", name = "Kodama's Reach", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("G"), tags = listOf(CardTag.RAMP))),
            entry(card(id = "rampant-growth", name = "Rampant Growth", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("G"), tags = listOf(CardTag.RAMP))),
            entry(card(id = "farseek-lf", name = "Farseek", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("G"), tags = listOf(CardTag.RAMP))),
            entry(card(id = "crucible", name = "Crucible of Worlds", typeLine = "Artifact", cmc = 3.0, colorIdentity = emptyList())),
            entry(card(id = "ramunap-excavator", name = "Ramunap Excavator", typeLine = "Creature — Human Nomad", cmc = 2.0, colorIdentity = listOf("G"), power = "2", toughness = "3")),
            entry(card(id = "beast-within-lf", name = "Beast Within", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("G"), tags = listOf(CardTag.REMOVAL))),
            entry(card(id = "bolt-lf", name = "Lightning Bolt", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))),
            entry(card(id = "sol-ring-lf", name = "Sol Ring", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
            entry(card(id = "arcane-signet-lf", name = "Arcane Signet", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList())),
            entry(card(id = "guardian-project", name = "The Great Henge", typeLine = "Legendary Artifact", cmc = 6.0, colorIdentity = listOf("G"), tags = listOf(CardTag.DRAW_ENGINE))),
            entry(card(id = "chandra-lf", name = "Chandra, Torch of Defiance", typeLine = "Legendary Planeswalker — Chandra", cmc = 4.0, colorIdentity = listOf("R"))),
        )
        val mainboard = withBasics(nonland, "Forest", "G")
        val colorIdentity = setOf(ManaColor.R, ManaColor.G)
        val profile = profileFor(mainboard, colorIdentity)

        val analysis = AnalysisEngine.evaluate(
            mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, profile = profile,
            archetype = ArchetypeId.RAMP, themes = listOf(ThemeId.LANDFALL), isManualOverride = true, confidence = 1f,
        )

        assertTrue(analysis.pillars.first { it.id == PillarId.PLAN_ROLES }.roleCoverage.any { it.roleKey == "landfall_payoff" })
        assertTrue(analysis.totalScore in 0..100)
    }

    // ── The 3 behavior-specific assertions the plan calls out explicitly ──────────────────────

    /**
     * THE bug this whole plan exists to fix: switching the resolved strategy over the SAME
     * mainboard must recompute BOTH `totalScore` AND the P3 role-coverage table -- not just
     * warnings (the legacy `applyArchetypeLayer` splice's bug). Uses the aggro reference deck
     * (heavy on early creatures/removal, light on counterspells/board wipes) evaluated once as
     * AGGRO and once as CONTROL.
     */
    @Test
    fun strategySwitch_changesTotalScoreAndPlanRoleCoverage() {
        val commander = card(id = "cmd-krenko-sw", name = "Krenko, Mob Boss", typeLine = "Legendary Creature — Goblin", cmc = 3.0, colorIdentity = listOf("R"))
        val nonland = listOf(
            entry(commander),
            entry(card(id = "goblin-guide-sw", name = "Goblin Guide", typeLine = "Creature — Goblin", cmc = 1.0, colorIdentity = listOf("R"), power = "2", toughness = "2")),
            entry(card(id = "zurgo-sw", name = "Zurgo Bellstriker", typeLine = "Creature — Orc Warrior", cmc = 1.0, colorIdentity = listOf("R"), power = "3", toughness = "2")),
            entry(card(id = "falkenrath-gorger-sw", name = "Falkenrath Gorger", typeLine = "Creature — Vampire", cmc = 1.0, colorIdentity = listOf("R"), power = "3", toughness = "1")),
            entry(card(id = "kari-zev-sw", name = "Kari Zev, Skyship Raider", typeLine = "Legendary Creature — Human Pirate", cmc = 2.0, colorIdentity = listOf("R"), power = "3", toughness = "2")),
            entry(card(id = "bolt-sw", name = "Lightning Bolt", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))),
            entry(card(id = "abrade-sw", name = "Abrade", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))),
            entry(card(id = "sol-ring-sw", name = "Sol Ring", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        )
        val mainboard = withBasics(nonland, "Mountain", "R")
        val colorIdentity = setOf(ManaColor.R)
        val profile = profileFor(mainboard, colorIdentity)

        val asAggro = AnalysisEngine.evaluate(
            mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, profile = profile,
            archetype = ArchetypeId.AGGRO, themes = emptyList(), isManualOverride = true, confidence = 1f,
        )
        val asControl = AnalysisEngine.evaluate(
            mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, profile = profile,
            archetype = ArchetypeId.CONTROL, themes = emptyList(), isManualOverride = true, confidence = 1f,
        )

        // Same mainboard, same profile -- ONLY the resolved strategy differs.
        assertNotEquals(asAggro.totalScore, asControl.totalScore, "strategy switch must recompute totalScore, not just warnings")
        val aggroCoverage = asAggro.pillars.first { it.id == PillarId.PLAN_ROLES }.roleCoverage.associateBy { it.roleKey }
        val controlCoverage = asControl.pillars.first { it.id == PillarId.PLAN_ROLES }.roleCoverage.associateBy { it.roleKey }
        assertNotEquals(aggroCoverage, controlCoverage, "strategy switch must recompute the P3 role-coverage table, not just warnings")
        // The P3 subscore itself (not just the table's contents) must also move.
        assertNotEquals(
            asAggro.pillars.first { it.id == PillarId.PLAN_ROLES }.subscore,
            asControl.pillars.first { it.id == PillarId.PLAN_ROLES }.subscore,
        )
    }

    /**
     * An anti-role run well over its resolved tolerance fires [Finding.AntiRoleOverMax] -- and
     * SURVIVES the per-pillar finding budget (plan §3.3: top 3 by severity+magnitude). AGGRO
     * Commander treats `removal_mass` (board wipes, max tolerance 2) as an anti-role; this deck
     * runs 8. Every OTHER AGGRO role band (ramp/card_draw/removal_spot/finisher min 6/6/4/6) is
     * satisfied at-or-above its minimum on purpose, so no competing RoleGap finding out-ranks the
     * anti-role violation in the budget -- this is what makes the assertion below meaningful rather
     * than a coincidence of the (Phase 4) default weights/thresholds.
     */
    @Test
    fun antiRoleOverMax_firesFinding() {
        val commander = card(id = "cmd-krenko-anti", name = "Krenko, Mob Boss", typeLine = "Legendary Creature — Goblin", cmc = 3.0, colorIdentity = listOf("R", "W"))
        val wipes = listOf(
            "Wrath of God" to "W", "Day of Judgment" to "W", "Austere Command" to "W", "Wave of Vitriol" to "W",
            "Toxic Deluge" to "B", "Damnation" to "B", "Supreme Verdict" to "W", "Blasphemous Act" to "R",
        ).mapIndexed { i, (name, color) ->
            entry(card(id = "wipe-$i", name = name, typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf(color), tags = listOf(CardTag.WRATH)))
        }
        val ramp = listOf("Sol Ring", "Arcane Signet", "Mind Stone", "Fellwar Stone", "Wayfarer's Bauble", "Skirk Prospector")
            .mapIndexed { i, name -> entry(card(id = "ramp-$i", name = name, typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))) }
        val draw = listOf("Wheel of Fortune", "Phyrexian Arena", "Night's Whisper", "Read the Bones", "Faithless Looting", "Sign in Blood")
            .mapIndexed { i, name -> entry(card(id = "draw-$i", name = name, typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), tags = listOf(CardTag.DRAW_ENGINE))) }
        val removalSpot = listOf("Lightning Bolt", "Abrade", "Skewer the Critics", "Fire")
            .mapIndexed { i, name -> entry(card(id = "removal-$i", name = name, typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))) }
        val finishers = listOf("Purphoros, God of the Forge", "Hazoret the Fervent", "Torbran, Thane of Red Fell", "Chandra, Torch of Defiance", "Embercleave", "Zealous Conscripts")
            .mapIndexed { i, name -> entry(card(id = "finisher-$i", name = name, typeLine = "Legendary Creature — God", cmc = 4.0, colorIdentity = listOf("R"), tags = listOf(CardTag.WIN_CON))) }
        val threats = listOf(
            "Goblin Guide" to "2", "Zurgo Bellstriker" to "3", "Falkenrath Gorger" to "3", "Bomat Courier" to "2",
            "Kari Zev, Skyship Raider" to "3", "Ash Zealot" to "3", "Vexing Devil" to "4", "Hellspark Elemental" to "2",
            "Kird Ape" to "2", "Robber of the Rich" to "2", "Reckless Bushwhacker" to "2", "Goblin Chieftain" to "2",
            "Krenko, Tin Street Kingpin" to "2", "Firebrand Archer" to "2",
        ).mapIndexed { i, (name, power) ->
            entry(card(id = "threat-$i", name = name, typeLine = "Creature — Goblin", cmc = 2.0, colorIdentity = listOf("R"), power = power, toughness = "2"))
        }
        val nonland = listOf(entry(commander)) + wipes + ramp + draw + removalSpot + finishers + threats
        val mainboard = withBasics(nonland, "Mountain", "R")
        val colorIdentity = setOf(ManaColor.R, ManaColor.W, ManaColor.B)
        val profile = profileFor(mainboard, colorIdentity)

        val analysis = AnalysisEngine.evaluate(
            mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, profile = profile,
            archetype = ArchetypeId.AGGRO, themes = emptyList(), isManualOverride = true, confidence = 1f,
        )

        val planRoles = analysis.pillars.first { it.id == PillarId.PLAN_ROLES }
        assertTrue(
            planRoles.findings.any { it is Finding.AntiRoleOverMax && it.roleKey == "removal_mass" },
            "AGGRO's removal_mass anti-role band should fire an AntiRoleOverMax finding for an 8-wipe deck: ${planRoles.findings}",
        )
        val wipeCoverage = planRoles.roleCoverage.first { it.roleKey == "removal_mass" }
        assertTrue(wipeCoverage.isAntiRole)
        assertTrue(wipeCoverage.current > wipeCoverage.max)
    }

    /** A banned-in-Commander card caps [DeckAnalysis.totalScore] at
     * [AnalysisEngine.ILLEGAL_DECK_SCORE_CAP], regardless of how clean the rest of the build is. */
    @Test
    fun illegalCard_capsTotalScore() {
        val commander = card(id = "cmd-krenko-illegal", name = "Krenko, Mob Boss", typeLine = "Legendary Creature — Goblin", cmc = 3.0, colorIdentity = listOf("R"))
        val bannedCard = card(
            id = "sway-fear", name = "Sway of the Stars", typeLine = "Sorcery", cmc = 7.0, colorIdentity = listOf("W"),
            legalityCommander = "banned",
        )
        val nonland = listOf(
            entry(commander),
            entry(bannedCard),
            entry(card(id = "goblin-guide-illegal", name = "Goblin Guide", typeLine = "Creature — Goblin", cmc = 1.0, colorIdentity = listOf("R"), power = "2", toughness = "2")),
            entry(card(id = "bolt-illegal", name = "Lightning Bolt", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))),
            entry(card(id = "sol-ring-illegal", name = "Sol Ring", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), tags = listOf(CardTag.RAMP))),
        )
        val mainboard = withBasics(nonland, "Mountain", "R")
        val colorIdentity = setOf(ManaColor.R, ManaColor.W)
        val profile = profileFor(mainboard, colorIdentity)

        val analysis = AnalysisEngine.evaluate(
            mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, profile = profile,
            archetype = ArchetypeId.AGGRO, themes = emptyList(), isManualOverride = true, confidence = 1f,
        )

        val legality = analysis.pillars.first { it.id == PillarId.LEGALITY }
        assertTrue(legality.findings.any { it is Finding.IllegalCard && it.cardName == "Sway of the Stars" })
        assertEquals(0, legality.subscore)
        assertTrue(analysis.totalScore <= AnalysisEngine.ILLEGAL_DECK_SCORE_CAP, "an illegal deck must never score above the documented cap")
    }

    /** Classifier audit fix (Phase 2 §1): a tag-less counterspell/protection-granting card is no
     * longer invisible to [ArchetypeRoleClassifier] -- it now falls back to the SAME oracle pattern
     * [RoleClassifier]'s INTERACTION role already validates. */
    @Test
    fun classifierAuditFix_countsTagLessCounterspellAndProtection() {
        val counterspellCard = card(id = "audit-counter", name = "Generic Counter", typeLine = "Instant", oracleText = "Counter target spell.")
        val protectionCard = card(id = "audit-protect", name = "Generic Protection", typeLine = "Instant", oracleText = "Target creature gains protection from red until end of turn.")
        val plainCard = card(id = "audit-plain", name = "Generic Vanilla", typeLine = "Sorcery", oracleText = "Do nothing relevant.")

        assertTrue((ArchetypeRoleClassifier.classify(counterspellCard)["counterspell"] ?: 0f) > 0f)
        assertTrue((ArchetypeRoleClassifier.classify(protectionCard)["protection"] ?: 0f) > 0f)
        assertEquals(null, ArchetypeRoleClassifier.classify(plainCard)["counterspell"])
        assertEquals(null, ArchetypeRoleClassifier.classify(plainCard)["protection"])
    }
}
