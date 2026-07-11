package com.mmg.manahub.core.tagging

import com.mmg.manahub.core.model.Card
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Phase 0 (0.2) archetype-role dictionary expansion in [TagDictionary].
 *
 * Each new ROLE key gets a positive fixture (a real card whose oracle text/type line should
 * match) and a negative fixture (a real card that must NOT match) to guard against both
 * false negatives and the over-broad OR-substring regressions the tagging engine was
 * rebuilt to avoid (see `project_tagging_engine_v2`).
 */
class TagDictionaryArchetypeRolesTest {

    private fun createCard(
        typeLine: String,
        oracleText: String? = null,
        name: String = "Test Card",
    ): Card = Card(
        scryfallId = "test",
        name = name,
        printedName = null,
        manaCost = null,
        cmc = 0.0,
        colors = emptyList(),
        colorIdentity = emptyList(),
        typeLine = typeLine,
        printedTypeLine = null,
        oracleText = oracleText,
        printedText = null,
        keywords = emptyList(),
        power = null,
        toughness = null,
        loyalty = null,
        setCode = "TST",
        setName = "Test Set",
        collectorNumber = "1",
        rarity = "common",
        releasedAt = "2024-01-01",
        frameEffects = emptyList(),
        promoTypes = emptyList(),
        lang = "en",
        imageNormal = null,
        imageArtCrop = null,
        imageBackNormal = null,
        priceUsd = null,
        priceUsdFoil = null,
        priceEur = null,
        priceEurFoil = null,
        legalityStandard = "legal",
        legalityPioneer = "legal",
        legalityModern = "legal",
        legalityCommander = "legal",
        flavorText = null,
        artist = null,
        scryfallUri = "https://test.com"
    )

    private fun keysOf(card: Card): Set<String> =
        StrategyAnalyzer.analyze(card).map { it.tag.key }.toSet()

    @Test
    fun `mana_fix matches Arcane Signet and Chromatic Lantern but not Sol Ring`() {
        val arcaneSignet = createCard(
            "Artifact",
            "{T}: Add one mana of any color in your commander's color identity.",
            name = "Arcane Signet",
        )
        val chromaticLantern = createCard(
            "Artifact",
            "Lands you control have \"{T}: Add one mana of any color.\"",
            name = "Chromatic Lantern",
        )
        val solRing = createCard("Artifact", "{T}: Add {C}{C}.", name = "Sol Ring")

        assertTrue("mana_fix" in keysOf(arcaneSignet))
        assertTrue("mana_fix" in keysOf(chromaticLantern))
        assertFalse("mana_fix" in keysOf(solRing))
    }

    @Test
    fun `sac_outlet matches Ashnods Altar but not Lightning Bolt`() {
        val ashnodsAltar = createCard(
            "Artifact",
            "Sacrifice a creature: Add {C}{C}.",
            name = "Ashnod's Altar",
        )
        val bolt = createCard("Instant", "~ deals 3 damage to any target.", name = "Lightning Bolt")

        assertTrue("sac_outlet" in keysOf(ashnodsAltar))
        assertFalse("sac_outlet" in keysOf(bolt))
    }

    @Test
    fun `death_payoff matches Zulaport Cutthroat but not a vanilla creature`() {
        val zulaport = createCard(
            "Creature — Human Shaman",
            "Whenever ~ or another creature you control dies, each opponent loses 1 life and you gain 1 life.",
            name = "Zulaport Cutthroat",
        )
        val bear = createCard("Creature — Bear", "", name = "Grizzly Bears")

        assertTrue("death_payoff" in keysOf(zulaport))
        assertFalse("death_payoff" in keysOf(bear))
    }

    @Test
    fun `graveyard_enabler matches a self-mill effect but not a vanilla creature`() {
        val streamOfConsciousness = createCard(
            "Sorcery",
            "Put the top four cards of your library into your graveyard.",
            name = "Stream of Consciousness",
        )
        val bear = createCard("Creature — Bear", "", name = "Grizzly Bears")

        assertTrue("graveyard_enabler" in keysOf(streamOfConsciousness))
        assertFalse("graveyard_enabler" in keysOf(bear))
    }

    @Test
    fun `reanimation matches Animate Dead but not a graveyard hate effect`() {
        val animateDead = createCard(
            "Enchantment — Aura",
            "Enchant creature card in a graveyard. When ~ enters, if it's on the battlefield, return enchanted creature card to the battlefield under your control and attach ~ to it.",
            name = "Animate Dead",
        )
        val relic = createCard(
            "Artifact",
            "{T}, Sacrifice ~: Exile all cards from target player's graveyard.",
            name = "Relic of Progenitus",
        )

        assertTrue("reanimation" in keysOf(animateDead))
        assertFalse("reanimation" in keysOf(relic))
    }

    @Test
    fun `self_mill_payoff matches a delirium payoff but not a vanilla creature`() {
        val delirium = createCard(
            "Creature — Zombie",
            "Delirium — ~ gets +2/+2 as long as there are four or more card types among cards in your graveyard.",
            name = "Delirium Skulker",
        )
        val bear = createCard("Creature — Bear", "", name = "Grizzly Bears")

        assertTrue("self_mill_payoff" in keysOf(delirium))
        assertFalse("self_mill_payoff" in keysOf(bear))
    }

    @Test
    fun `stax_piece matches Winter Orb but not a vanilla creature`() {
        val winterOrb = createCard(
            "Artifact",
            "Players can't untap more than one land during their untap steps.",
            name = "Winter Orb",
        )
        val bear = createCard("Creature — Bear", "", name = "Grizzly Bears")

        assertTrue("stax_piece" in keysOf(winterOrb))
        assertFalse("stax_piece" in keysOf(bear))
    }

    @Test
    fun `landfall_payoff matches Lotus Cobra but not a basic land`() {
        val lotusCobra = createCard(
            "Creature — Snake",
            "Landfall — Whenever a land enters the battlefield under your control, you may add one mana of any color.",
            name = "Lotus Cobra",
        )
        val forest = createCard("Basic Land — Forest", "({T}: Add {G}.)")

        assertTrue("landfall_payoff" in keysOf(lotusCobra))
        assertFalse("landfall_payoff" in keysOf(forest))
    }

    @Test
    fun `lifegain_payoff matches Soul Warden but not a vanilla creature`() {
        val soulWarden = createCard(
            "Creature — Human Cleric",
            "Whenever another creature enters the battlefield, you gain 1 life.",
            name = "Soul Warden",
        )
        val trigger = createCard(
            "Creature — Human Cleric",
            "Whenever you gain life, draw a card.",
            name = "Well of Lost Dreams Proxy",
        )
        val bear = createCard("Creature — Bear", "", name = "Grizzly Bears")

        assertFalse("Soul Warden itself grants life, it is not the payoff", "lifegain_payoff" in keysOf(soulWarden))
        assertTrue("lifegain_payoff" in keysOf(trigger))
        assertFalse("lifegain_payoff" in keysOf(bear))
    }

    @Test
    fun `counters_payoff matches a plus-one counters card but not a vanilla creature`() {
        val hardenedScales = createCard(
            "Enchantment",
            "If one or more +1/+1 counters would be put on a creature you control, that many plus one +1/+1 counters are put on it instead.",
            name = "Hardened Scales",
        )
        val bear = createCard("Creature — Bear", "", name = "Grizzly Bears")

        assertTrue("counters_payoff" in keysOf(hardenedScales))
        assertFalse("counters_payoff" in keysOf(bear))
    }

    @Test
    fun `spell_payoff matches a magecraft card but not a vanilla creature`() {
        val kediMagecraft = createCard(
            "Creature — Cat Wizard",
            "Magecraft — Whenever you cast or copy an instant or sorcery spell, ~ deals 1 damage to any target.",
            name = "Kediss Magecraft Test",
        )
        val bear = createCard("Creature — Bear", "", name = "Grizzly Bears")

        assertTrue("spell_payoff" in keysOf(kediMagecraft))
        assertFalse("spell_payoff" in keysOf(bear))
    }

    @Test
    fun `mill_engine matches a targeted opponent mill effect but not self-mill`() {
        val opponentMill = createCard(
            "Sorcery",
            "Target player mills ten cards.",
            name = "Mind Sculpt",
        )
        val selfMill = createCard(
            "Sorcery",
            "Put the top four cards of your library into your graveyard.",
            name = "Stream of Consciousness",
        )

        assertTrue("mill_engine" in keysOf(opponentMill))
        assertFalse("mill_engine" in keysOf(selfMill))
    }

    @Test
    fun `threat_early is registered but never auto-detected from text`() {
        // threat_early is manual-pick only (MV/power are not oracle-text signals);
        // it must be a registered key with zero matching rules.
        val entry = TagDictionary.get("threat_early")
        assertTrue("threat_early must be registered", entry != null)
        assertTrue("threat_early has no textual detection rules", entry!!.rules.isEmpty())

        val aggressiveOneDrop = createCard("Creature — Goblin", "", name = "Goblin Guide")
        assertFalse("threat_early" in keysOf(aggressiveOneDrop))
    }

    @Test
    fun `equipment matches Bonesplitter type line but not a nonequipment artifact`() {
        val bonesplitter = createCard(
            "Artifact — Equipment",
            "Equipped creature gets +2/+0. Equip {1}",
            name = "Bonesplitter",
        )
        val solRing = createCard("Artifact", "{T}: Add {C}{C}.", name = "Sol Ring")

        assertTrue("equipment" in keysOf(bonesplitter))
        assertFalse("equipment" in keysOf(solRing))
    }

    @Test
    fun `aura_buff matches Rancor but not a nonbuff aura`() {
        val rancor = createCard(
            "Enchantment — Aura",
            "Enchant creature. Enchanted creature gets +2/+0 and has trample.",
            name = "Rancor",
        )
        val pacifism = createCard(
            "Enchantment — Aura",
            "Enchant creature. Enchanted creature can't attack or block.",
            name = "Pacifism",
        )

        assertTrue("aura_buff" in keysOf(rancor))
        assertFalse("aura_buff" in keysOf(pacifism))
    }

    @Test
    fun `token_generator matches Raise the Alarm but not a vanilla creature`() {
        val raiseTheAlarm = createCard(
            "Instant",
            "Create two 1/1 white Soldier creature tokens.",
            name = "Raise the Alarm",
        )
        val bear = createCard("Creature — Bear", "", name = "Grizzly Bears")

        assertTrue("token_generator" in keysOf(raiseTheAlarm))
        assertFalse("token_generator" in keysOf(bear))
    }

    @Test
    fun `blink_effect matches Restoration Angel but not a vanilla creature`() {
        val restorationAngel = createCard(
            "Creature — Angel",
            "Flash. Flying. When ~ enters, you may exile target non-Angel creature you control, then return that card to the battlefield under your control.",
            name = "Restoration Angel",
        )
        val bear = createCard("Creature — Bear", "", name = "Grizzly Bears")

        assertTrue("blink_effect" in keysOf(restorationAngel))
        assertFalse("blink_effect" in keysOf(bear))
    }

    @Test
    fun `etb_payoff matches Cathars Crusade but not a vanilla creature`() {
        val cathars = createCard(
            "Enchantment",
            "Whenever a creature you control enters, put a +1/+1 counter on each creature you control.",
            name = "Cathars' Crusade",
        )
        val bear = createCard("Creature — Bear", "", name = "Grizzly Bears")

        assertTrue("etb_payoff" in keysOf(cathars))
        assertFalse("etb_payoff" in keysOf(bear))
    }

    @Test
    fun `planeswalker matches a planeswalker type line but not a creature`() {
        // StrategyAnalyzer.analyze() bails out entirely on a BLANK oracleText (before any
        // rule — including typeLineAnyOf-only ones — ever runs), so this fixture needs
        // non-blank text to actually exercise the typeLineAnyOf match.
        val chandra = createCard(
            "Legendary Planeswalker — Chandra",
            "+1: Exile the top card of your library. You may cast it. If you don't, ~ deals 2 damage to each opponent.",
            name = "Chandra, Torch of Defiance",
        )
        val bear = createCard("Creature — Bear", "Trample.", name = "Grizzly Bears")

        assertTrue("planeswalker" in keysOf(chandra))
        assertFalse("planeswalker" in keysOf(bear))
    }

    @Test
    fun `vehicle matches a vehicle type line but not a creature`() {
        val smugglersCopter = createCard(
            "Artifact — Vehicle",
            "Flying. Crew 1.",
            name = "Smuggler's Copter",
        )
        val bear = createCard("Creature — Bear", "", name = "Grizzly Bears")

        assertTrue("vehicle" in keysOf(smugglersCopter))
        assertFalse("vehicle" in keysOf(bear))
    }

    @Test
    fun `clone_theft_effect matches Clone and a control-stealing aura but not a vanilla creature`() {
        val clone = createCard(
            "Creature — Shapeshifter",
            "You may have ~ enter as a copy of any creature on the battlefield.",
            name = "Clone",
        )
        val mindControl = createCard(
            "Enchantment — Aura",
            "Enchant creature. Gain control of target creature.",
            name = "Mind Control",
        )
        val bear = createCard("Creature — Bear", "", name = "Grizzly Bears")

        assertTrue("clone_theft_effect" in keysOf(clone))
        assertTrue("clone_theft_effect" in keysOf(mindControl))
        assertFalse("clone_theft_effect" in keysOf(bear))
    }

    @Test
    fun `group_effect matches a symmetric each-player draw spell but not a vanilla creature`() {
        val groupDraw = createCard(
            "Instant",
            "Each player draws a card.",
            name = "Group Draw Test",
        )
        val bear = createCard("Creature — Bear", "", name = "Grizzly Bears")

        assertTrue("group_effect" in keysOf(groupDraw))
        assertFalse("group_effect" in keysOf(bear))
    }

    @Test
    fun `enchantment_payoff matches Sythis but not a vanilla creature`() {
        val sythis = createCard(
            "Legendary Creature — Human Cleric",
            "Whenever you cast an enchantment spell, draw a card.",
            name = "Sythis, Harvest's Hand",
        )
        val bear = createCard("Creature — Bear", "", name = "Grizzly Bears")

        assertTrue("enchantment_payoff" in keysOf(sythis))
        assertFalse("enchantment_payoff" in keysOf(bear))
    }

    @Test
    fun `artifact_payoff matches an artifacts-matter card but not a vanilla creature`() {
        val redcap = createCard(
            "Creature — Goblin Rogue",
            "Whenever an artifact you control enters, ~ deals 1 damage to any target.",
            name = "Redcap Bombardier Test",
        )
        val bear = createCard("Creature — Bear", "", name = "Grizzly Bears")

        assertTrue("artifact_payoff" in keysOf(redcap))
        assertFalse("artifact_payoff" in keysOf(bear))
    }

    @Test
    fun `tribe_payoff matches a choose-a-creature-type lord but not a vanilla creature`() {
        val metallicMimic = createCard(
            "Artifact Creature — Shapeshifter",
            "As ~ enters, choose a creature type. Other creatures you control of the chosen type get +1/+1.",
            name = "Metallic Mimic",
        )
        val bear = createCard("Creature — Bear", "", name = "Grizzly Bears")

        assertTrue("tribe_payoff" in keysOf(metallicMimic))
        assertFalse("tribe_payoff" in keysOf(bear))
    }
}
