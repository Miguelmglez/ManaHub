package com.mmg.manahub.feature.decks.domain.engine.analysisv3

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry

/**
 * Fixture #8 (spec §9) — Chainer, Dementia Master, Reanimator. Commander, mono-Black.
 * Expected: macro MIDRANGE, posture ATTRITION, theme REANIMATOR.
 *
 * Phase 3a-FIX batch B (density expansion, 2026-08-26): expanded from ~24 to 63 non-land entries
 * + commander, mirroring batch A's fixtures 1-7 treatment (see
 * `project_deck_analysis_v3_phase3a_fix_batch_a.md`). Authoring effort went into GRAVEYARD's
 * producer side (`graveyard_enabler`) and payoff side (`reanimation`/`recursion`), plus a real
 * Reanimator support shell (draw, removal, tutors, ramp, big reanimation targets).
 */
fun fixture08Chainer(): AnalysisV3Fixture {
    val commander = card(
        id = "cmd-chainer", name = "Chainer, Dementia Master", typeLine = "Legendary Creature — Human Wizard",
        cmc = 4.0, colorIdentity = listOf("B"), power = "3", toughness = "2",
        oracleText = "{1}{B}, Discard a card: Put target creature card from a graveyard onto the battlefield under your control. That creature is a black Nightmare in addition to its other colors and types. Exile it at the beginning of the next end step. Activate only once each turn.",
    )
    val nonland = listOf(
        entry(commander),
        entry(card(id = "reanimate-ch", name = "Reanimate", typeLine = "Sorcery", cmc = 1.0, colorIdentity = listOf("B"), oracleText = "Put target creature card from a graveyard onto the battlefield under your control. You lose life equal to its mana value.", tags = listOf(roleTag("reanimation")))),
        entry(card(id = "animate-dead-ch", name = "Animate Dead", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "Put target creature card from a graveyard onto the battlefield under your control.", tags = listOf(roleTag("reanimation")))),
        entry(card(id = "necromancy-ch", name = "Necromancy", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("B"), oracleText = "Put target creature card from a graveyard onto the battlefield under your control.", tags = listOf(roleTag("reanimation")))),
        entry(card(id = "exhume", name = "Exhume", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "Each player puts a creature card from their graveyard onto the battlefield.", tags = listOf(roleTag("reanimation")))),
        entry(card(id = "entomb-ch", name = "Entomb", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("B"), oracleText = "Search your library for a card, put that card into your graveyard, then shuffle.", tags = listOf(roleTag("graveyard_enabler")))),
        entry(card(id = "buried-alive-ch", name = "Buried Alive", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "Search your library for up to three creature cards and put them into your graveyard, then shuffle.", tags = listOf(roleTag("graveyard_enabler")))),
        entry(card(id = "putrid-imp", name = "Putrid Imp", typeLine = "Creature — Imp", cmc = 1.0, colorIdentity = listOf("B"), power = "1", toughness = "1", oracleText = "When Putrid Imp enters the battlefield, discard a card.", tags = listOf(roleTag("graveyard_enabler")))),
        entry(card(id = "stitchers-supplier", name = "Stitcher's Supplier", typeLine = "Creature — Zombie", cmc = 1.0, colorIdentity = listOf("B"), power = "1", toughness = "1", oracleText = "When Stitcher's Supplier enters the battlefield, mill three cards. Whenever a creature card is put into your graveyard from your library, if Stitcher's Supplier is in your graveyard... sacrifice Stitcher's Supplier: Mill three cards.", tags = listOf(roleTag("graveyard_enabler")))),
        entry(card(id = "griselbrand-ch", name = "Griselbrand", typeLine = "Legendary Creature — Demon", cmc = 8.0, colorIdentity = listOf("B"), power = "7", toughness = "7", oracleText = "Flying, lifelink. Pay 7 life: Draw seven cards.", tags = listOf(CardTag.WIN_CON))),
        entry(card(id = "sheoldred-ch", name = "Sheoldred, the Apocalypse", typeLine = "Legendary Creature — Phyrexian Praetor", cmc = 4.0, colorIdentity = listOf("B"), power = "4", toughness = "5", oracleText = "Deathtouch. Whenever you draw a card, you gain 2 life. Whenever an opponent draws a card, they lose 2 life.")),
        entry(card(id = "massacre-wurm", name = "Massacre Wurm", typeLine = "Creature — Wurm", cmc = 5.0, colorIdentity = listOf("B"), power = "6", toughness = "5", oracleText = "When Massacre Wurm enters the battlefield, creatures your opponents control get -2/-2 until end of turn. Whenever a creature an opponent controls dies, that player loses 2 life.")),
        entry(card(id = "demonic-tutor-ch", name = "Demonic Tutor", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "Search your library for a card and put that card into your hand, then shuffle.", tags = listOf(CardTag.TUTOR))),
        entry(card(id = "toxic-deluge-ch", name = "Toxic Deluge", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("B"), oracleText = "As an additional cost to cast this spell, pay X life. All creatures get -X/-X until end of turn.", tags = listOf(CardTag.WRATH))),
        entry(card(id = "damnation-ch", name = "Damnation", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("B"), oracleText = "Destroy all creatures. They can't be regenerated.", tags = listOf(CardTag.WRATH))),
        entry(card(id = "go-for-the-throat", name = "Go for the Throat", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "Destroy target creature. It can't be an artifact creature.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "fatal-push", name = "Fatal Push", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("B"), oracleText = "Destroy target creature if it has mana value 2 or less.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "phyrexian-arena-ch", name = "Phyrexian Arena", typeLine = "Enchantment", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "At the beginning of your upkeep, you draw a card and you lose 1 life.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "night's-whisper", name = "Night's Whisper", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "You draw two cards and you lose 2 life.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "sol-ring-ch", name = "Sol Ring", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), oracleText = "{T}: Add {C}{C}.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "arcane-signet-ch", name = "Arcane Signet", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), oracleText = "{T}: Add one mana of any color in your commander's color identity.")),
        entry(card(id = "raise-dead-ch", name = "Raise Dead", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), power = null, toughness = null, oracleText = "Return target creature card from your graveyard to your hand.", tags = listOf(roleTag("recursion")))),
        entry(card(id = "sheoldreds-edict", name = "Sheoldred's Edict", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "Each player sacrifices a creature or planeswalker.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "grim-tutor", name = "Grim Tutor", typeLine = "Instant", cmc = 3.0, colorIdentity = listOf("B"), oracleText = "Search your library for a card, put it into your hand, then shuffle. You lose 3 life.", tags = listOf(CardTag.TUTOR))),
        entry(card(id = "corpse-dance", name = "Corpse Dance", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "Put target creature card from your graveyard onto the battlefield. Return that creature to your hand at the beginning of the next end step. Flashback {5}{B}.", tags = listOf(roleTag("reanimation")))),
        // Batch B expansion — additional reanimation spells (real staples, mono-black legal).
        entry(card(id = "zombify-ch", name = "Zombify", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("B"), oracleText = "Put target creature card from your graveyard onto the battlefield.", tags = listOf(roleTag("reanimation")))),
        entry(card(id = "dread-return-ch", name = "Dread Return", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("B"), oracleText = "Put target creature card from your graveyard onto the battlefield. Flashback—Sacrifice three creatures.", tags = listOf(roleTag("reanimation")))),
        entry(card(id = "ever-after-ch", name = "Ever After", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("B"), oracleText = "Return target creature card from your graveyard to the battlefield and target land card from your graveyard to your hand. Flashback {5}{B}{B}.", tags = listOf(roleTag("reanimation")))),
        entry(card(id = "beacon-of-unrest-ch", name = "Beacon of Unrest", typeLine = "Artifact", cmc = 5.0, colorIdentity = listOf("B"), oracleText = "Put target artifact or creature card from a graveyard onto the battlefield under your control. Flashback {7}{B}.", tags = listOf(roleTag("reanimation")))),
        // Batch B expansion — additional graveyard enablers.
        entry(card(id = "nyx-weaver-ch", name = "Nyx Weaver", typeLine = "Creature — Spider", cmc = 3.0, colorIdentity = listOf("B"), power = "2", toughness = "4", oracleText = "Reach. At the beginning of your end step, mill a card. {2}{B}, Exile a creature card from your graveyard: Nyx Weaver gets +2/+2 until end of turn.", tags = listOf(roleTag("graveyard_enabler")))),
        entry(card(id = "vile-entomber-ch", name = "Vile Entomber", typeLine = "Creature — Human Cleric", cmc = 3.0, colorIdentity = listOf("B"), power = "2", toughness = "2", oracleText = "When Vile Entomber enters the battlefield, search your library for a card, put it into your graveyard, then shuffle.", tags = listOf(roleTag("graveyard_enabler")))),
        entry(card(id = "corpse-churn-ch", name = "Corpse Churn", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "Mill three cards, then you may return a creature card from your graveyard to your hand.", tags = listOf(roleTag("graveyard_enabler")))),
        entry(card(id = "altar-of-dementia-ch", name = "Altar of Dementia", typeLine = "Artifact", cmc = 1.0, colorIdentity = emptyList(), oracleText = "Sacrifice a creature: Target player mills a number of cards equal to the sacrificed creature's power.", tags = listOf(roleTag("graveyard_enabler")))),
        // Batch B expansion — additional recursion (return creature card from graveyard to hand).
        entry(card(id = "gravedigger-ch", name = "Gravedigger", typeLine = "Creature — Zombie", cmc = 3.0, colorIdentity = listOf("B"), power = "2", toughness = "2", oracleText = "When Gravedigger enters the battlefield, you may return target creature card from your graveyard to your hand.", tags = listOf(roleTag("recursion")))),
        entry(card(id = "tortured-existence-ch", name = "Tortured Existence", typeLine = "Enchantment", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "Discard a creature card: Return target creature card from your graveyard to your hand.", tags = listOf(roleTag("recursion")))),
        entry(card(id = "cadaver-imp-ch", name = "Cadaver Imp", typeLine = "Creature — Imp", cmc = 2.0, colorIdentity = listOf("B"), power = "1", toughness = "1", oracleText = "{1}{B}, {T}, Sacrifice a creature: Return target creature card from your graveyard to your hand.", tags = listOf(roleTag("recursion")))),
        // Batch B expansion — card draw.
        entry(card(id = "sign-in-blood-ch", name = "Sign in Blood", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "Target player draws two cards and loses 2 life.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "read-the-bones-ch", name = "Read the Bones", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("B"), oracleText = "Look at the top four cards of your library. Put two of them into your hand and the rest into your graveyard. You lose 2 life.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "necropotence-ch", name = "Necropotence", typeLine = "Enchantment", cmc = 3.0, colorIdentity = listOf("B"), oracleText = "You have no maximum hand size. Skip your draw step. Whenever you exile a card from your library with Necropotence's last ability... pay 1 life: Exile the top card of your library face down. Put that card into your hand at the beginning of your next end step.", tags = listOf(CardTag.DRAW_ENGINE))),
        entry(card(id = "bolas-citadel-ch", name = "Bolas's Citadel", typeLine = "Artifact", cmc = 3.0, colorIdentity = listOf("B"), oracleText = "You may play the top card of your library. If you cast a spell this way, pay life equal to its mana value rather than pay its mana cost. {T}, Sacrifice ten nonland permanents: Search your library for a card, put it into your hand, then shuffle.", tags = listOf(CardTag.DRAW_ENGINE))),
        // Batch B expansion — spot removal.
        entry(card(id = "doom-blade-ch", name = "Doom Blade", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "Destroy target nonblack creature.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "infernal-grasp-ch", name = "Infernal Grasp", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "Destroy target creature or planeswalker. You lose 2 life.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "feed-the-swarm-ch", name = "Feed the Swarm", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "Destroy target creature or enchantment an opponent controls. You lose life equal to that permanent's mana value.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "baleful-mastery-ch", name = "Baleful Mastery", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "Exile target creature or planeswalker. Its controller may search their library for a card with a different name and equal or lesser mana value.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "eliminate-ch", name = "Eliminate", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "Destroy target tapped creature or planeswalker.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "ravenous-chupacabra-ch", name = "Ravenous Chupacabra", typeLine = "Creature — Beast", cmc = 4.0, colorIdentity = listOf("B"), power = "2", toughness = "2", oracleText = "When Ravenous Chupacabra enters the battlefield, destroy target creature an opponent controls.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "bloodchiefs-thirst-ch", name = "Bloodchief's Thirst", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("B"), oracleText = "Destroy target creature or planeswalker with mana value 2 or less. Kicker {2}{B}.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "nekrataal-ch", name = "Nekrataal", typeLine = "Creature — Human Assassin", cmc = 3.0, colorIdentity = listOf("B"), power = "2", toughness = "1", oracleText = "When Nekrataal enters the battlefield, destroy target nonartifact, nonblack creature.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "diabolic-edict-ch", name = "Diabolic Edict", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("B"), oracleText = "Target player sacrifices a creature.", tags = listOf(CardTag.REMOVAL))),
        entry(card(id = "crux-of-fate-ch", name = "Crux of Fate", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("B"), oracleText = "Choose one — Destroy all Dragon creatures; or destroy all non-Dragon creatures.", tags = listOf(CardTag.WRATH))),
        // Batch B expansion — tutors.
        entry(card(id = "vampiric-tutor-ch", name = "Vampiric Tutor", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("B"), oracleText = "Search your library for a card and put it on top. You lose 2 life.", tags = listOf(CardTag.TUTOR))),
        entry(card(id = "diabolic-tutor-ch", name = "Diabolic Tutor", typeLine = "Sorcery", cmc = 4.0, colorIdentity = listOf("B"), oracleText = "Search your library for a card, put it into your hand, then shuffle.", tags = listOf(CardTag.TUTOR))),
        // Batch B expansion — big reanimation targets / finishers.
        entry(card(id = "archon-of-cruelty-ch", name = "Archon of Cruelty", typeLine = "Creature — Archon", cmc = 6.0, colorIdentity = listOf("B"), power = "6", toughness = "5", oracleText = "Flying. When Archon of Cruelty enters the battlefield, target opponent sacrifices a creature, discards a card, and loses 3 life. You gain 3 life, draw a card, and create a 1/1 black Bat creature token with flying and lifelink.", tags = listOf(CardTag.WIN_CON))),
        entry(card(id = "grave-titan-ch", name = "Grave Titan", typeLine = "Creature — Giant", cmc = 6.0, colorIdentity = listOf("B"), power = "6", toughness = "6", oracleText = "Deathtouch. When Grave Titan enters the battlefield, create two 2/2 black Zombie creature tokens. Whenever Grave Titan attacks, create a 2/2 black Zombie creature token that's tapped and attacking.", tags = listOf(CardTag.WIN_CON))),
        entry(card(id = "razaketh-ch", name = "Razaketh, the Foulblooded", typeLine = "Legendary Creature — Demon", cmc = 5.0, colorIdentity = listOf("B"), power = "6", toughness = "5", oracleText = "Flying. Sacrifice a creature: Search your library for a card, put it into your hand, then shuffle. You lose 2 life.", tags = listOf(CardTag.WIN_CON))),
        entry(card(id = "kokusho-ch", name = "Kokusho, the Evening Star", typeLine = "Legendary Creature — Dragon Spirit", cmc = 6.0, colorIdentity = listOf("B"), power = "4", toughness = "4", oracleText = "Flying. When Kokusho, the Evening Star dies, each opponent loses 5 life and you gain life equal to the life lost this way.", tags = listOf(CardTag.WIN_CON))),
        entry(card(id = "gray-merchant-ch", name = "Gray Merchant of Asphodel", typeLine = "Creature — Zombie Cleric", cmc = 4.0, colorIdentity = listOf("B"), power = "2", toughness = "3", oracleText = "When Gray Merchant of Asphodel enters the battlefield, each opponent loses X life and you gain X life, where X is your devotion to black.", tags = listOf(CardTag.WIN_CON))),
        // Batch B expansion — ramp.
        entry(card(id = "charcoal-diamond-ch", name = "Charcoal Diamond", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), oracleText = "Charcoal Diamond enters the battlefield tapped. {T}: Add {B}.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "mind-stone-ch", name = "Mind Stone", typeLine = "Artifact", cmc = 2.0, colorIdentity = emptyList(), oracleText = "{T}: Add {C}. {1}, {T}, Sacrifice Mind Stone: Draw a card.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "crypt-ghast-ch", name = "Crypt Ghast", typeLine = "Creature — Zombie Cleric", cmc = 3.0, colorIdentity = listOf("B"), power = "2", toughness = "2", oracleText = "Extort. Whenever a Swamp you control is tapped for mana, add an additional {B}.", tags = listOf(CardTag.RAMP))),
        entry(card(id = "nirkana-revenant-ch", name = "Nirkana Revenant", typeLine = "Legendary Creature — Avatar", cmc = 6.0, colorIdentity = listOf("B"), power = "5", toughness = "5", oracleText = "Swampwalk. Whenever a Swamp you control is tapped for mana, add an additional {B}.", tags = listOf(CardTag.RAMP))),
        // Batch B expansion — generic staples (no dedicated role, real value engines).
        entry(card(id = "skullclamp-ch", name = "Skullclamp", typeLine = "Artifact — Equipment", cmc = 1.0, colorIdentity = emptyList(), oracleText = "Equipped creature gets +1/-1. Whenever equipped creature dies, draw two cards. Equip {1}.")),
        entry(card(id = "rankle-ch", name = "Rankle, Master of Pranks", typeLine = "Legendary Creature — Devil", cmc = 4.0, colorIdentity = listOf("B"), power = "3", toughness = "3", oracleText = "Flying, haste. Whenever Rankle, Master of Pranks deals combat damage to a player, choose any number — Each player discards a card; each player sacrifices a creature; each player loses 1 life and draws a card.")),
        entry(card(id = "carrion-feeder-ch", name = "Carrion Feeder", typeLine = "Creature — Zombie", cmc = 1.0, colorIdentity = listOf("B"), power = "1", toughness = "1", oracleText = "Sacrifice a creature: Put a +1/+1 counter on Carrion Feeder. Activate only as a sorcery.")),
    )
    val mainboard = withBasicsCommander(nonland, "Swamp", "B")
    return AnalysisV3Fixture(
        id = 8, name = "Chainer, Dementia Master (Reanimator)", anchor = "Chainer, Dementia Master",
        format = DeckFormat.COMMANDER, mainboard = mainboard,
        colorIdentity = setOf(ManaColor.B),
        // Commander prior workstream: Chainer's own ability is a repeatable single-target
        // reanimation engine -- the well-established grindy-recursion MIDRANGE reanimator read,
        // independent of this fixture's own authored decklist.
        commanderTags = listOf(CardTag.MIDRANGE, CardTag.GRAVEYARD),
        expectedMacro = "MIDRANGE", expectedPosture = "ATTRITION", expectedThemes = "REANIMATOR",
    )
}
