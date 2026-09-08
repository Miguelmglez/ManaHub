package com.mmg.manahub.core.domain.search

import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.CollectionSource
import com.mmg.manahub.core.model.ColorMatchMode
import com.mmg.manahub.core.model.ComparisonOperator
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.feature.decks.domain.engine.card
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Local evaluator for [AdvancedSearchQuery] — the offline counterpart of
 * `BuildScryfallQueryUseCase`, shared by the Collection screen's advanced search (strict) and Deck
 * Studio's "Browse for X" Collection tab (lenient).
 */
class AdvancedSearchCardMatcherTest {

    private val bolt = card(
        id = "bolt",
        name = "Lightning Bolt",
        typeLine = "Instant",
        cmc = 1.0,
        colors = listOf("R"),
        colorIdentity = listOf("R"),
        tags = listOf(CardTag.REMOVAL),
    )

    private val forest = card(
        id = "forest",
        name = "Forest",
        typeLine = "Basic Land — Forest",
        cmc = 0.0,
        colors = emptyList(),
        colorIdentity = listOf("G"),
    )

    // ── Basic criteria ────────────────────────────────────────────────────────

    @Test
    fun `every criterion must match for the card to pass`() {
        val query = AdvancedSearchQuery(
            criteria = listOf(
                SearchCriterion.CardType(setOf("Instant")),
                SearchCriterion.Colors(setOf("R")),
            )
        )
        assertTrue(AdvancedSearchCardMatcher.matches(bolt, query))

        val mismatched = AdvancedSearchQuery(
            criteria = listOf(
                SearchCriterion.CardType(setOf("Instant")),
                SearchCriterion.Colors(setOf("U")),
            )
        )
        assertFalse(AdvancedSearchCardMatcher.matches(bolt, mismatched))
    }

    @Test
    fun `an empty query matches every card`() {
        assertTrue(AdvancedSearchCardMatcher.matches(bolt, AdvancedSearchQuery()))
    }

    @Test
    fun `CardType exclude negates the type instead of requiring it`() {
        val curveBucket = AdvancedSearchQuery(
            criteria = listOf(
                SearchCriterion.ManaCost(0, ComparisonOperator.EQUAL),
                SearchCriterion.CardType(setOf("land"), exclude = true),
            )
        )
        assertFalse(AdvancedSearchCardMatcher.matches(forest, curveBucket), "a basic land must not fill a 0-cost curve bucket")
        assertTrue(
            AdvancedSearchCardMatcher.matches(
                card(id = "ornithopter", name = "Ornithopter", typeLine = "Artifact Creature — Thopter", cmc = 0.0),
                curveBucket,
            )
        )
    }

    @Test
    fun `HasTag reads userTags as well as auto tags`() {
        val userTagged = card(id = "u", name = "User Tagged", userTags = listOf(CardTag.REMOVAL))
        val criterion = SearchCriterion.HasTag(listOf(CardTag.REMOVAL.key))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(userTagged, criterion))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(bolt, criterion))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(forest, criterion))
    }

    @Test
    fun `CollectionStatus is evaluated from the caller-supplied context`() {
        val criterion = SearchCriterion.CollectionStatus(CollectionSource.WISHLIST)
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(bolt, criterion, isWishlisted = true))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(bolt, criterion, isWishlisted = false))
        // Default context (a plain-Card caller with no collection data) never claims a match.
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(bolt, criterion))
    }

    @Test
    fun `CollectionStatus sources are mutually exclusive`() {
        val forTrade = SearchCriterion.CollectionStatus(CollectionSource.FOR_TRADE)
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(bolt, forTrade, isForTrade = true))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(bolt, forTrade, isWishlisted = true))

        // COLLECTION is the caller's own base list, never a per-card constraint.
        val collection = SearchCriterion.CollectionStatus(CollectionSource.COLLECTION)
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(bolt, collection))
    }

    @Test
    fun `Format matches the per-format legality field`() {
        val banned = card(id = "b", name = "Banned", legalityCommander = "banned")
        val legal = SearchCriterion.Format(listOf("commander"), legal = true)
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(bolt, legal))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(banned, legal))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(banned, SearchCriterion.Format(listOf("commander"), legal = false)))
    }

    // ── strict vs lenient ─────────────────────────────────────────────────────

    @Test
    fun `a mapped CardFunction is evaluated identically in both modes`() {
        val criterion = SearchCriterion.CardFunction(setOf("spot-removal"))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(bolt, criterion, lenient = false))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(bolt, criterion, lenient = true))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(forest, criterion, lenient = false))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(forest, criterion, lenient = true))
    }

    @Test
    fun `an unmapped CardFunction fails strictly and is skipped leniently`() {
        // "edict" carries no CardFunctionOption.collectionTagKeys — Scryfall-search-only.
        val criterion = SearchCriterion.CardFunction(setOf("edict"))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(bolt, criterion, lenient = false))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(bolt, criterion, lenient = true))
    }

    @Test
    fun `lenient mode still enforces the criteria it can evaluate`() {
        val query = AdvancedSearchQuery(
            criteria = listOf(
                SearchCriterion.CardFunction(setOf("edict")),
                SearchCriterion.Colors(setOf("U")),
            )
        )
        assertFalse(AdvancedSearchCardMatcher.matches(bolt, query, lenient = true), "skipping the unmatchable facet must not skip the rest")
    }

    @Test
    fun `criteria with no local equivalent never constrain the result`() {
        // Loyalty, Language, Artist and FlavorText have no cached field to evaluate, so they stay
        // on the `else -> true` fallthrough in BOTH modes. OracleTerms/ManaProduction used to sit
        // here too and are now genuinely evaluated -- see the sections below.
        val criterion = SearchCriterion.Artist("Christopher Rush")
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(bolt, criterion, lenient = false))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(bolt, criterion, lenient = true))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  OracleTerms — the dictionary-translated role sections + every fingerprint: theme section
    // ══════════════════════════════════════════════════════════════════════════

    private val altar = card(
        id = "altar",
        name = "Ashnods Altar",
        typeLine = "Artifact",
        oracleText = "Sacrifice a creature: Add {C}{C}. (This is reminder text about mana.)",
    )

    private val selfReferential = card(
        id = "self",
        name = "Lightning Bolt",
        typeLine = "Instant",
        oracleText = "Lightning Bolt deals 3 damage to any target.",
    )

    private val vanilla = card(id = "vanilla", name = "Grizzly Bears", typeLine = "Creature — Bear", oracleText = null)

    @Test
    fun `OracleTerms allOf requires EVERY term to be present`() {
        val both = SearchCriterion.OracleTerms(allOf = listOf("sacrifice a creature", "add"))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(altar, both))

        val missingOne = SearchCriterion.OracleTerms(allOf = listOf("sacrifice a creature", "draw a card"))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(altar, missingOne))
    }

    @Test
    fun `OracleTerms anyOfGroups ORs within a group and ANDs across groups`() {
        val orWithinGroup = SearchCriterion.OracleTerms(
            anyOfGroups = listOf(listOf("draw a card", "sacrifice a creature")),
        )
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(altar, orWithinGroup))

        val andAcrossGroups = SearchCriterion.OracleTerms(
            anyOfGroups = listOf(listOf("sacrifice a creature"), listOf("add", "draw a card")),
        )
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(altar, andAcrossGroups))

        val secondGroupUnmatched = SearchCriterion.OracleTerms(
            anyOfGroups = listOf(listOf("sacrifice a creature"), listOf("draw a card", "counter target spell")),
        )
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(altar, secondGroupUnmatched))
    }

    @Test
    fun `OracleTerms matches case-insensitively, like Scryfall's oracle clause`() {
        val criterion = SearchCriterion.OracleTerms(allOf = listOf("SACRIFICE A CREATURE"))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(altar, criterion))
    }

    @Test
    fun `OracleTerms ignores parenthesized reminder text, as the tagging engine does`() {
        // "reminder text" appears ONLY inside the parens -- stripping it is what stops a rule from
        // firing on reminder wording the printed rules text does not actually contain.
        val criterion = SearchCriterion.OracleTerms(allOf = listOf("reminder text"))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(altar, criterion))
    }

    @Test
    fun `OracleTerms sees the card's own name as tilde, matching StrategyAnalyzer normalization`() {
        val viaTilde = SearchCriterion.OracleTerms(allOf = listOf("~ deals 3 damage"))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(selfReferential, viaTilde))

        val viaLiteralName = SearchCriterion.OracleTerms(allOf = listOf("lightning bolt deals"))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(selfReferential, viaLiteralName))
    }

    @Test
    fun `OracleTerms typeLineAnyOf ORs type-line terms`() {
        val matching = SearchCriterion.OracleTerms(typeLineAnyOf = listOf("enchantment", "artifact"))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(altar, matching))

        val notMatching = SearchCriterion.OracleTerms(typeLineAnyOf = listOf("enchantment", "land"))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(altar, notMatching))
    }

    @Test
    fun `OracleTerms typeLineAnyOf still filters when the card has no oracle text at all`() {
        // A type-line-only criterion must NOT degrade to "cannot evaluate" -- the trap
        // core/tagging/CLAUDE.md documents for StrategyAnalyzer's own blank-oracle short-circuit.
        val creature = SearchCriterion.OracleTerms(typeLineAnyOf = listOf("creature"))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(vanilla, creature, lenient = false))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(vanilla, creature, lenient = true))

        val land = SearchCriterion.OracleTerms(typeLineAnyOf = listOf("land"))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(vanilla, land, lenient = false))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(vanilla, land, lenient = true))
    }

    @Test
    fun `OracleTerms with a blank oracle text fails strictly and is skipped leniently`() {
        val criterion = SearchCriterion.OracleTerms(allOf = listOf("sacrifice a creature"))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(vanilla, criterion, lenient = false))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(vanilla, criterion, lenient = true))
    }

    @Test
    fun `an OracleTerms criterion with no terms at all is not a constraint`() {
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(vanilla, SearchCriterion.OracleTerms()))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ManaProduction — Mana Base per-colour sections + the mana_fix role
    // ══════════════════════════════════════════════════════════════════════════

    private val dualLand = card(
        id = "harbor",
        name = "Hinterland Harbor",
        typeLine = "Land",
        producedMana = "UG",
    )

    private val solRing = card(
        id = "sol-ring",
        name = "Sol Ring",
        typeLine = "Artifact",
        producedMana = "",
    )

    @Test
    fun `ManaProduction matches a single produced colour`() {
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(dualLand, SearchCriterion.ManaProduction(colors = setOf("G"))))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(dualLand, SearchCriterion.ManaProduction(colors = setOf("W"))))
    }

    @Test
    fun `ManaProduction is case-insensitive about the requested colour letter`() {
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(dualLand, SearchCriterion.ManaProduction(colors = setOf("u"))))
    }

    @Test
    fun `ManaProduction ANDs multiple colours, mirroring one produces clause per colour`() {
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(dualLand, SearchCriterion.ManaProduction(colors = setOf("U", "G"))))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(dualLand, SearchCriterion.ManaProduction(colors = setOf("U", "R"))))
    }

    @Test
    fun `ManaProduction minDistinctColors is the mana-fixing threshold`() {
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(dualLand, SearchCriterion.ManaProduction(minDistinctColors = 2)))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(dualLand, SearchCriterion.ManaProduction(minDistinctColors = 3)))

        val monoGreenLand = card(id = "forest-g", name = "Forest", typeLine = "Basic Land — Forest", producedMana = "G")
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(monoGreenLand, SearchCriterion.ManaProduction(minDistinctColors = 2)))
    }

    @Test
    fun `ManaProduction requireLand rejects a non-land in BOTH modes, before production is consulted`() {
        val criterion = SearchCriterion.ManaProduction(colors = setOf("G"), requireLand = true)
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(bolt, criterion, lenient = false))
        // Leniency skips only what cannot be evaluated -- `t:land` always can, so it still filters.
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(bolt, criterion, lenient = true))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(dualLand, criterion))
    }

    @Test
    fun `ManaProduction with a blank producedMana fails strictly and is skipped leniently`() {
        // "" means both "produces nothing" and "row predates Room v42" -- indistinguishable, so it
        // is treated as unevaluable rather than guessed.
        val criterion = SearchCriterion.ManaProduction(colors = setOf("G"))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(solRing, criterion, lenient = false))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(solRing, criterion, lenient = true))
    }

    @Test
    fun `a ManaProduction criterion with neither colours nor a threshold is not a constraint`() {
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(solRing, SearchCriterion.ManaProduction()))
    }

    // ── ColorMatchMode ────────────────────────────────────────────────────────
    //  Regression: "Browse for Card Draw" on a Commander deck returned 97 cards on the All Cards
    //  tab and 0 on the Collection tab over the same collection (2026-09-07). The section query
    //  rendered as `id<=wub` remotely but was evaluated locally as "identity contains ALL of
    //  W, U and B" — a superset test, the exact inverse. These tests pin both directions.

    private val monoWhiteDraw = card(
        id = "draw-w",
        name = "Mentor of the Meek",
        typeLine = "Creature — Human Soldier",
        colors = listOf("W"),
        colorIdentity = listOf("W"),
        tags = listOf(CardTag.DRAW_ENGINE),
    )

    private val esperDraw = card(
        id = "draw-wub",
        name = "Sphinx of the Guildpact",
        typeLine = "Creature — Sphinx",
        colors = listOf("W", "U", "B"),
        colorIdentity = listOf("W", "U", "B"),
        tags = listOf(CardTag.DRAW_ENGINE),
    )

    private val colorlessDraw = card(
        id = "draw-c",
        name = "Endless Atlas",
        typeLine = "Artifact",
        colors = emptyList(),
        colorIdentity = emptyList(),
        tags = listOf(CardTag.DRAW_ENGINE),
    )

    /** The real "Browse for Card Draw" query shape from a Commander deck's Analysis tab. */
    private fun cardDrawSectionQuery(deckColors: Set<String>) = AdvancedSearchQuery(
        criteria = listOf(
            SearchCriterion.CardFunction(setOf("card-advantage")),
            SearchCriterion.ColorIdentity(deckColors, ColorMatchMode.AT_MOST),
            SearchCriterion.Format(listOf("commander")),
        )
    )

    @Test
    fun `a Commander card-draw section admits every card that fits inside the deck identity`() {
        val esperDeck = cardDrawSectionQuery(setOf("W", "U", "B"))
        // The pre-fix superset test failed ALL THREE of these: no card carries every one of W, U
        // and B unless it is exactly Esper.
        assertTrue(AdvancedSearchCardMatcher.matches(monoWhiteDraw, esperDeck, lenient = true))
        assertTrue(AdvancedSearchCardMatcher.matches(esperDraw, esperDeck, lenient = true))
        assertTrue(
            AdvancedSearchCardMatcher.matches(colorlessDraw, esperDeck, lenient = true),
            "a colorless card fits inside every identity",
        )
    }

    @Test
    fun `a Commander card-draw section excludes cards that break the deck identity`() {
        val monoWhiteDeck = cardDrawSectionQuery(setOf("W"))
        assertTrue(AdvancedSearchCardMatcher.matches(monoWhiteDraw, monoWhiteDeck, lenient = true))
        assertFalse(
            AdvancedSearchCardMatcher.matches(esperDraw, monoWhiteDeck, lenient = true),
            "an Esper card is illegal in a mono-white commander deck",
        )
        // The pre-fix superset test got this one RIGHT by accident and everything else wrong.
        assertFalse(AdvancedSearchCardMatcher.matches(bolt, monoWhiteDeck, lenient = true))
    }

    @Test
    fun `the three colour modes are distinct`() {
        val wu = setOf("W", "U")
        val azoriusCard = card(id = "wu", colors = listOf("W", "U"), colorIdentity = listOf("W", "U"))

        // AT_MOST: subset. AT_LEAST: superset. EXACTLY: equality.
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(monoWhiteDraw, SearchCriterion.ColorIdentity(wu, ColorMatchMode.AT_MOST)))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(monoWhiteDraw, SearchCriterion.ColorIdentity(wu, ColorMatchMode.AT_LEAST)))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(monoWhiteDraw, SearchCriterion.ColorIdentity(wu, ColorMatchMode.EXACTLY)))

        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(azoriusCard, SearchCriterion.ColorIdentity(wu, ColorMatchMode.AT_MOST)))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(azoriusCard, SearchCriterion.ColorIdentity(wu, ColorMatchMode.AT_LEAST)))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(azoriusCard, SearchCriterion.ColorIdentity(wu, ColorMatchMode.EXACTLY)))

        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(esperDraw, SearchCriterion.ColorIdentity(wu, ColorMatchMode.AT_MOST)))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(esperDraw, SearchCriterion.ColorIdentity(setOf("W"), ColorMatchMode.AT_LEAST)))
    }

    @Test
    fun `Colors keeps the at-least semantics Scryfall's c operator has always had`() {
        // Unlike identity, `c:` IS at-least on Scryfall, so this facet was never inverted -- the
        // default mode must keep matching the pre-mode behaviour exactly.
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(esperDraw, SearchCriterion.Colors(setOf("W", "U"))))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(monoWhiteDraw, SearchCriterion.Colors(setOf("W", "U"))))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(monoWhiteDraw, SearchCriterion.Colors(setOf("W"))))
    }

    // ── ANY_OF ("any of these colors") ────────────────────────────────────────
    //  Mirrors BuildScryfallQueryUseCase's `(c>=w or c>=u)` rendering. Divergence here IS the
    //  defect ColorMatchMode exists to prevent, so every case below has a rendering twin in
    //  BuildScryfallQueryUseCaseTest.

    @Test
    fun `ANY_OF matches a card that shares at least one selected color`() {
        val wu = setOf("W", "U")
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(monoWhiteDraw, SearchCriterion.Colors(wu, ColorMatchMode.ANY_OF)))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(esperDraw, SearchCriterion.Colors(wu, ColorMatchMode.ANY_OF)))
        // Red shares nothing with W/U -- and AT_LEAST would have rejected mono-white too.
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(bolt, SearchCriterion.Colors(wu, ColorMatchMode.ANY_OF)))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(monoWhiteDraw, SearchCriterion.Colors(wu, ColorMatchMode.AT_LEAST)))
    }

    @Test
    fun `ANY_OF with a single color is the same test as AT_LEAST`() {
        val w = setOf("W")
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(monoWhiteDraw, SearchCriterion.Colors(w, ColorMatchMode.ANY_OF)))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(esperDraw, SearchCriterion.Colors(w, ColorMatchMode.ANY_OF)))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(bolt, SearchCriterion.Colors(w, ColorMatchMode.ANY_OF)))
    }

    @Test
    fun `ANY_OF applies to color identity too`() {
        val bg = setOf("B", "G")
        // Forest has an empty colors list but a G identity -- the two facets must not be conflated.
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(forest, SearchCriterion.ColorIdentity(bg, ColorMatchMode.ANY_OF)))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(forest, SearchCriterion.Colors(bg, ColorMatchMode.ANY_OF)))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(bolt, SearchCriterion.ColorIdentity(bg, ColorMatchMode.ANY_OF)))
    }

    @Test
    fun `C means colorless, never a sixth color`() {
        val c = setOf("C")
        // `c=c` remotely: only a card with no colors at all.
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(colorlessDraw, SearchCriterion.Colors(c, ColorMatchMode.ANY_OF)))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(monoWhiteDraw, SearchCriterion.Colors(c, ColorMatchMode.ANY_OF)))
        // A lone C collapses to the same clause under every mode.
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(colorlessDraw, SearchCriterion.Colors(c, ColorMatchMode.AT_LEAST)))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(colorlessDraw, SearchCriterion.Colors(c, ColorMatchMode.EXACTLY)))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(colorlessDraw, SearchCriterion.Colors(c, ColorMatchMode.AT_MOST)))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(monoWhiteDraw, SearchCriterion.Colors(c, ColorMatchMode.AT_LEAST)))
    }

    @Test
    fun `C alongside real colors is an ANY_OF alternative but is dropped by the set modes`() {
        val wc = setOf("W", "C")
        // `(c>=w or c=c)`
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(monoWhiteDraw, SearchCriterion.Colors(wc, ColorMatchMode.ANY_OF)))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(colorlessDraw, SearchCriterion.Colors(wc, ColorMatchMode.ANY_OF)))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(bolt, SearchCriterion.Colors(wc, ColorMatchMode.ANY_OF)))
        // `c>=w` -- the C is dropped, so a colorless card no longer qualifies.
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(monoWhiteDraw, SearchCriterion.Colors(wc, ColorMatchMode.AT_LEAST)))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(colorlessDraw, SearchCriterion.Colors(wc, ColorMatchMode.AT_LEAST)))
    }

    @Test
    fun `AT_MOST on printed colors admits a colorless card, exactly like c is at most wu`() {
        // `c<=wu` includes every colorless card remotely — the identity twin of this case was
        // already pinned, the `c` prefix one was not.
        val wu = setOf("W", "U")
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(colorlessDraw, SearchCriterion.Colors(wu, ColorMatchMode.AT_MOST)))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(monoWhiteDraw, SearchCriterion.Colors(wu, ColorMatchMode.AT_MOST)))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(esperDraw, SearchCriterion.Colors(wu, ColorMatchMode.AT_MOST)))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(bolt, SearchCriterion.Colors(wu, ColorMatchMode.AT_MOST)))
    }

    @Test
    fun `EXACTLY drops a C picked alongside real colors, mirroring the rendered c=w clause`() {
        // `c=w`: the C is not a sixth letter, so a colorless card no longer qualifies and the
        // selection collapses to plain equality on W.
        val wc = setOf("W", "C")
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(monoWhiteDraw, SearchCriterion.Colors(wc, ColorMatchMode.EXACTLY)))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(colorlessDraw, SearchCriterion.Colors(wc, ColorMatchMode.EXACTLY)))
        assertFalse(AdvancedSearchCardMatcher.matchesCriterion(esperDraw, SearchCriterion.Colors(wc, ColorMatchMode.EXACTLY)))
        // AT_MOST drops it the same way, and there a colorless card fits inside the selection.
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(colorlessDraw, SearchCriterion.Colors(wc, ColorMatchMode.AT_MOST)))
    }

    @Test
    fun `an empty color selection is not a constraint`() {
        // The renderer returns null for an empty set, so the local half must match everything --
        // anything else re-creates the remote-vs-local split the mode enum exists to close.
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(bolt, SearchCriterion.Colors(emptySet(), ColorMatchMode.AT_MOST)))
        assertTrue(AdvancedSearchCardMatcher.matchesCriterion(bolt, SearchCriterion.ColorIdentity(emptySet(), ColorMatchMode.AT_MOST)))
    }
}
