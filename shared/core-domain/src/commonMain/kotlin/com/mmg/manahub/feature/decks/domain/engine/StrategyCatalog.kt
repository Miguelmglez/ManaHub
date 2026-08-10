package com.mmg.manahub.feature.decks.domain.engine

// ═══════════════════════════════════════════════════════════════════════════════
//  StrategyCatalog — Deck Wizard & Engine Rework plan (docs/plans/deck-wizard-rework-plan.md),
//  Workstream 1.1/1.4 -- the ONE vocabulary's player-facing copy + compatibility data.
//
//  Root cause this fixes (plan §0 F1): the wizard's Direction step, the Doctor's plan sheet, and
//  the Discoveries browser each spoke a slightly different vocabulary with no shared notion of
//  "which themes fit which archetype" -- this file is the single source of truth for both. It sits
//  ABOVE [ArchetypeModels.kt]'s [ArchetypeId]/[ThemeId] enums (never renames or adds entries to
//  them -- persisted `archetypeOverride`/`themesOverride` raw-string columns depend on `.name`,
//  CLAUDE.md hard rule) and is consumed by [ColorStrategyAffinity] (WS 1.4, every curated entry's
//  strategy list must pass [StrategyCatalog.isValidCombination]) and by the shared picker
//  component (WS 1.2, `StrategyPickerSheet` in `:app`'s presentation layer).
//
//  Per decision D-B (plan "Resolved decisions"): Casual Flow A requires a REAL strategy pick with
//  no GENERIC/"Balanced" escape, so every description here is genuine player-facing UI copy, not
//  internal documentation -- write for a player deciding what to build, not a developer.
//
//  Text provenance note: the archetype/theme description strings mirror the EXISTING Android
//  string resources `deck_wizard_archetype_desc_*`/`deck_wizard_theme_desc_*`
//  (`app/src/main/res/values/strings.xml`, already-reviewed copy from the Deck Builder v2 wizard's
//  Identity step) rather than inventing new prose. `commonMain` cannot reference `R.string`
//  (KMP layering rule, CLAUDE.md) so this is a deliberate plain-Kotlin-string duplicate of that
//  exact copy, not independently authored -- when WS 2/3 wires the wizard onto this catalog, the
//  Android resources become the single remaining copy (retire the `R.string` duplicates then, not
//  in this pass -- WS 1 does not touch the wizard screens).
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * One theme's catalog entry: player-facing copy plus the compatibility data the shared picker
 * (WS 1.2) and [ColorStrategyAffinity] (WS 1.4) both consume.
 *
 * @property description 2-3 sentence English explanation of the mechanical strategy (D-B UI copy).
 * @property compatibleArchetypes the [ArchetypeId]s this theme is commonly built as (excludes
 *   [ArchetypeId.GENERIC] deliberately -- GENERIC is the neutral "Balanced" default and is always
 *   compatible with every theme by construction, see [StrategyCatalog.isValidCombination]/
 *   [StrategyCatalog.compatibleThemes] rather than being listed here 22 times).
 * @property requiresTribe `true` ONLY for [ThemeId.TRIBAL] -- the one theme whose identity is
 *   incomplete without a concrete creature-type pick (mirrors [TribeDeriver]'s runtime `tribe:<x>`
 *   contract; every other theme carries its own complete identity with no further axis needed).
 * @property defaultArchetype the [ArchetypeId] pre-filled when the user picks only this theme
 *   (colors-first Flow B / a Discoveries "Build this" tribe/theme-only hand-off) -- always a member
 *   of [compatibleArchetypes].
 */
data class ThemeCatalogEntry(
    val theme: ThemeId,
    val description: String,
    val compatibleArchetypes: Set<ArchetypeId>,
    val requiresTribe: Boolean,
    val defaultArchetype: ArchetypeId,
)

/**
 * The single vocabulary's pure data table (WS 1.1) -- descriptions, compatibility matrix, and the
 * [isValidCombination] gate BOTH the wizard's strategy steps (WS 2/3, not wired in this pass) and
 * the Doctor's `ArchetypePlanSheet` (WS 8, not wired in this pass) will share.
 */
object StrategyCatalog {

    /** 2-3 sentence English game-plan description for [archetype] (D-B UI copy) -- covers win
     * condition, pacing, and what the deck spends its turns doing. [ArchetypeId.GENERIC] gets a
     * short "Balanced" description (the Commander-only escape hatch, WS 2.2). */
    fun description(archetype: ArchetypeId): String = ARCHETYPE_DESCRIPTIONS.getValue(archetype)

    /** 2-3 sentence English description for [theme] -- see [ThemeCatalogEntry.description]. */
    fun description(theme: ThemeId): String = THEME_CATALOG.getValue(theme).description

    /** The full catalog entry for [theme] (compatibility + tribe requirement + default archetype). */
    fun entry(theme: ThemeId): ThemeCatalogEntry = THEME_CATALOG.getValue(theme)

    /** [ArchetypeId]s [theme] is commonly built as. [ArchetypeId.GENERIC] is compatible with EVERY
     * theme by construction (the neutral default has no restriction) and is never listed inside a
     * theme's own curated set -- callers that need "is GENERIC included" should special-case it,
     * mirroring [isValidCombination]'s own GENERIC short-circuit. */
    fun compatibleArchetypes(theme: ThemeId): Set<ArchetypeId> = THEME_CATALOG.getValue(theme).compatibleArchetypes

    /** True only for [ThemeId.TRIBAL] -- see [ThemeCatalogEntry.requiresTribe]. */
    fun requiresTribe(theme: ThemeId): Boolean = THEME_CATALOG.getValue(theme).requiresTribe

    /** The [ArchetypeId] to pre-fill when only [theme] is picked -- see
     * [ThemeCatalogEntry.defaultArchetype]. */
    fun defaultArchetype(theme: ThemeId): ArchetypeId = THEME_CATALOG.getValue(theme).defaultArchetype

    /**
     * Inverse view (archetype -> compatible themes), GENERATED from [THEME_CATALOG] -- the plan's
     * "single source of truth, do not hand-maintain two directions" requirement (WS 1.1). For
     * [ArchetypeId.GENERIC] this returns ALL 22 themes (the neutral default restricts nothing).
     */
    fun compatibleThemes(archetype: ArchetypeId): Set<ThemeId> =
        if (archetype == ArchetypeId.GENERIC) {
            ThemeId.entries.toSet()
        } else {
            THEME_ARCHETYPE_REVERSE[archetype].orEmpty()
        }

    /**
     * Validates a (archetype, themes, tribe) combination against the curated compatibility
     * matrix -- used by BOTH the wizard's strategy steps and the Doctor's plan sheet (WS 1.1's
     * shared validation requirement) so neither surface can construct a combination the other
     * would reject.
     *
     * Rules:
     * 1. At most 2 themes (mirrors [ArchetypeSkeletonResolver]'s own 2-theme cap -- a 3rd theme
     *    has nowhere to resolve to and would silently be ignored downstream, which is worse than
     *    rejecting it here).
     * 2. [ArchetypeId.GENERIC] (or `null`, treated the same as the unpinned/neutral default) is
     *    compatible with every theme -- no per-theme check applies.
     * 3. For any OTHER archetype, every theme in [themes] must list it in
     *    [ThemeCatalogEntry.compatibleArchetypes].
     * 4. [ThemeId.TRIBAL] requires a non-blank [tribe]; conversely a non-blank [tribe] with NO
     *    [ThemeId.TRIBAL] in [themes] is also invalid -- [StrategyProfile]'s own KDoc documents
     *    tribe as "only meaningful WITH `ThemeId.TRIBAL`", so this keeps the validator symmetric
     *    with that contract instead of silently accepting an orphaned tribe pick.
     */
    fun isValidCombination(archetype: ArchetypeId?, themes: List<ThemeId>, tribe: String? = null): Boolean {
        if (themes.size > MAX_THEMES) return false

        val hasTribalTheme = ThemeId.TRIBAL in themes
        val hasTribePick = !tribe.isNullOrBlank()
        if (hasTribalTheme != hasTribePick) return false

        val effectiveArchetype = archetype ?: ArchetypeId.GENERIC
        if (effectiveArchetype == ArchetypeId.GENERIC) return true

        return themes.all { theme -> effectiveArchetype in compatibleArchetypes(theme) }
    }

    /** Mirrors [ArchetypeSkeletonResolver]'s 2-theme cap (see [StrategyProfile]'s own `themes` KDoc). */
    const val MAX_THEMES = 2

    // ── Archetype descriptions (D-B UI copy; 7 values incl. GENERIC) ───────────────────────────
    // Mirrors app/src/main/res/values/strings.xml `deck_wizard_archetype_desc_*` verbatim -- see
    // this file's header KDoc "Text provenance note".
    private val ARCHETYPE_DESCRIPTIONS: Map<ArchetypeId, String> = mapOf(
        ArchetypeId.AGGRO to
            "Fast, cheap creatures that race to close the game before your opponent stabilizes.",
        ArchetypeId.MIDRANGE to
            "Efficient creatures and removal with a flexible plan -- apply pressure now, out-value them later.",
        ArchetypeId.CONTROL to
            "Removal and card draw to survive the early game, then win late with a single big finisher.",
        ArchetypeId.TEMPO to
            "Efficient threats backed by protection and disruption -- keep the initiative and never let go.",
        ArchetypeId.COMBO to
            "Assemble a specific set of cards to generate an overwhelming or game-ending effect.",
        ArchetypeId.RAMP to
            "Accelerate your mana ahead of schedule to cast big, powerful spells early.",
        ArchetypeId.GENERIC to
            "A flexible, balanced approach with no single dominant strategy.",
    )

    // ── Theme catalog (22 values) -- descriptions mirror `deck_wizard_theme_desc_*` verbatim;
    //    compatibility/default-archetype/requiresTribe are NEW data (WS 1.1). Every compatibility
    //    cell carries a one-line rationale comment per the plan's requirement. ──────────────────
    private val THEME_CATALOG: Map<ThemeId, ThemeCatalogEntry> = buildMap {
        fun add(
            theme: ThemeId,
            description: String,
            compatibleArchetypes: Set<ArchetypeId>,
            defaultArchetype: ArchetypeId,
            requiresTribe: Boolean = false,
        ) {
            put(theme, ThemeCatalogEntry(theme, description, compatibleArchetypes, requiresTribe, defaultArchetype))
        }

        add(
            theme = ThemeId.REANIMATOR,
            description = "Discard or mill big threats early, then bring them back from the graveyard for a fraction of their mana cost.",
            // MIDRANGE/CONTROL: the discard-then-reanimate shell is inherently grindy/value-first;
            // COMBO: reanimating a specific combo piece (e.g. a graveyard-triggered win-con) is a
            // classic reanimator sub-shell. Excludes AGGRO/TEMPO/RAMP -- cheating creatures into
            // play is the opposite of a fast low-curve clock, and it replaces (not stacks with) a
            // ramp plan.
            compatibleArchetypes = setOf(ArchetypeId.MIDRANGE, ArchetypeId.CONTROL, ArchetypeId.COMBO),
            defaultArchetype = ArchetypeId.MIDRANGE,
        )
        add(
            theme = ThemeId.SELF_MILL,
            description = "Deliberately put cards from your library into the graveyard to fuel graveyard-matters payoffs.",
            // Same rationale family as REANIMATOR (it's the enabler half of the same shell) --
            // MIDRANGE/CONTROL/COMBO. Excludes AGGRO/TEMPO (milling yourself is a durdle-first
            // plan) and RAMP (orthogonal resource axis).
            compatibleArchetypes = setOf(ArchetypeId.MIDRANGE, ArchetypeId.CONTROL, ArchetypeId.COMBO),
            defaultArchetype = ArchetypeId.MIDRANGE,
        )
        add(
            theme = ThemeId.ARISTOCRATS,
            description = "Sacrifice your own creatures for value -- death triggers, drain effects, and token engines reward every loss.",
            // AGGRO: sac-for-drain is a real fast clock (Rakdos aristocrats); MIDRANGE: the classic
            // grindy value-engine shell; COMBO: sac-loop combo pieces (free sac outlet + death
            // trigger = infinite). Excludes CONTROL/TEMPO/RAMP -- aristocrats wants a wide, cheap
            // board to feed the sac outlet, not a control shell's low creature count.
            compatibleArchetypes = setOf(ArchetypeId.AGGRO, ArchetypeId.MIDRANGE, ArchetypeId.COMBO),
            defaultArchetype = ArchetypeId.MIDRANGE,
        )
        add(
            theme = ThemeId.TOKENS,
            description = "Generate armies of token creatures and go wide, then push damage or value with anthem effects.",
            // AGGRO: go-wide fast damage; MIDRANGE: token value + anthems as a grindier plan
            // (TOKENS' documented default). Excludes CONTROL (too few threats by design)/COMBO
            // (tokens are a board-state plan, not a combo piece)/RAMP (orthogonal).
            compatibleArchetypes = setOf(ArchetypeId.AGGRO, ArchetypeId.MIDRANGE),
            defaultArchetype = ArchetypeId.MIDRANGE,
        )
        add(
            theme = ThemeId.SPELLSLINGER,
            description = "Build around instants and sorceries -- payoffs trigger off casting noncreature spells.",
            // TEMPO: cheap spells + protection is the archetypal Izzet tempo shell; CONTROL: spell
            // density overlaps heavily with a control gameplan; COMBO: storm/spell-copy loops;
            // AGGRO: burn-spellslinger is a real fast shell. Excludes MIDRANGE/RAMP -- spellslinger
            // wants a low, cheap-spell curve, not midrange's creature-heavy balance.
            compatibleArchetypes = setOf(ArchetypeId.TEMPO, ArchetypeId.CONTROL, ArchetypeId.COMBO, ArchetypeId.AGGRO),
            defaultArchetype = ArchetypeId.TEMPO,
        )
        add(
            theme = ThemeId.VOLTRON,
            description = "Stack auras, equipment, and buffs onto a single evasive threat and win through commander damage or raw power.",
            // Plan's own worked example (WS 1.1): AGGRO (fastest possible commander-damage clock)
            // and MIDRANGE (a grindier, more resilient single-threat plan) both build voltron
            // shells. Excludes CONTROL/TEMPO/COMBO/RAMP -- voltron's win condition IS the buffed
            // creature, not a control/combo backup plan.
            compatibleArchetypes = setOf(ArchetypeId.AGGRO, ArchetypeId.MIDRANGE),
            defaultArchetype = ArchetypeId.AGGRO,
        )
        add(
            theme = ThemeId.STAX,
            description = "Slow the whole table down with resource-denial lock pieces -- taxes, resets, and asymmetric restrictions.",
            // Plan's own worked example (WS 1.1): STAX excludes AGGRO -- a fast-clock deck does not
            // want to also lock down the board (it wants everyone tapped out and racing, not
            // durdling). CONTROL: stax IS a control sub-shell (deny resources instead of answering
            // threats one-for-one); COMBO: "stax combo" decks use lock pieces to buy time to
            // assemble a combo. Excludes MIDRANGE/TEMPO/RAMP -- none of those plans want to slow
            // THEMSELVES down too, which asymmetric stax pieces often do incidentally.
            compatibleArchetypes = setOf(ArchetypeId.CONTROL, ArchetypeId.COMBO),
            defaultArchetype = ArchetypeId.CONTROL,
        )
        add(
            theme = ThemeId.LANDFALL,
            description = "Reward playing lands -- extra ramp, tokens, or damage every time a land enters the battlefield.",
            // RAMP: landfall's most natural home -- extra land drops feed a big-mana plan directly;
            // MIDRANGE: landfall value/tokens as an incremental-advantage engine. Excludes
            // AGGRO/TEMPO (landfall payoffs are a slow-building engine, not a fast clock)/CONTROL/
            // COMBO (orthogonal to those plans' core identity).
            compatibleArchetypes = setOf(ArchetypeId.RAMP, ArchetypeId.MIDRANGE),
            defaultArchetype = ArchetypeId.RAMP,
        )
        add(
            theme = ThemeId.LIFEGAIN,
            description = "Gain large amounts of life and turn it into card draw, damage, or other payoffs.",
            // AGGRO: white lifegain-aggro (extort-style) is a real, well-known shell; MIDRANGE: the
            // classic grindy lifegain-payoff engine; CONTROL: life total as a resource to out-grind
            // the opponent. Excludes TEMPO/COMBO/RAMP -- lifegain is a resource-accrual plan
            // orthogonal to those identities.
            compatibleArchetypes = setOf(ArchetypeId.AGGRO, ArchetypeId.MIDRANGE, ArchetypeId.CONTROL),
            defaultArchetype = ArchetypeId.MIDRANGE,
        )
        add(
            theme = ThemeId.PLUS1_COUNTERS,
            description = "Stack +1/+1 counters onto your creatures and proliferate them for a growing, resilient board.",
            // AGGRO: counters-aggro (e.g. Abzan/Gruul counters) is a real fast shell; MIDRANGE: the
            // default grow-your-board value plan; RAMP: big creatures with counters pairs naturally
            // with a ramp-into-threats plan. Excludes CONTROL/TEMPO/COMBO -- counters are a
            // creature-board investment, not those plans' core identity.
            compatibleArchetypes = setOf(ArchetypeId.AGGRO, ArchetypeId.MIDRANGE, ArchetypeId.RAMP),
            defaultArchetype = ArchetypeId.MIDRANGE,
        )
        add(
            theme = ThemeId.TRIBAL,
            description = "Lean into a single creature type -- lords and type-specific payoffs reward playing more of that tribe.",
            // Tribal viability genuinely depends on WHICH tribe (Elves lean RAMP/AGGRO, Goblins
            // AGGRO, Vampires MIDRANGE/aristocrats-adjacent, Spirits CONTROL/TEMPO) -- the widest
            // set of the 4 archetypes that regularly headline a tribal shell in practice. Excludes
            // COMBO (a tribe is a board-state identity, not a combo-piece identity) --
            // requiresTribe=true is what actually narrows this down once a tribe is picked.
            compatibleArchetypes = setOf(ArchetypeId.AGGRO, ArchetypeId.MIDRANGE, ArchetypeId.RAMP, ArchetypeId.CONTROL),
            defaultArchetype = ArchetypeId.MIDRANGE,
            requiresTribe = true,
        )
        add(
            theme = ThemeId.ARTIFACTS,
            description = "Build around artifact synergies -- cost reducers, artifact payoffs, and combo pieces.",
            // AGGRO: affinity-style artifact aggro; MIDRANGE: the default value-artifacts shell;
            // RAMP: mana-rocks-matter overlaps directly with a ramp identity; COMBO: artifact combo
            // pieces (cost reducers enabling a combo turn) are a classic shell; CONTROL: colorless
            // removal/card-advantage artifacts and artifact-based stax pieces are a real control
            // toolbox (e.g. "Esper artifacts control"). Excludes TEMPO -- artifacts-matter is a
            // board/engine identity, not tempo's core.
            compatibleArchetypes = setOf(ArchetypeId.AGGRO, ArchetypeId.MIDRANGE, ArchetypeId.RAMP, ArchetypeId.COMBO, ArchetypeId.CONTROL),
            defaultArchetype = ArchetypeId.MIDRANGE,
        )
        add(
            theme = ThemeId.ENCHANTRESS,
            description = "Draw cards off enchantments and stack auras/sagas for incremental value every turn.",
            // MIDRANGE: the classic grindy enchantress value-engine; CONTROL: the draw engine
            // outgrinding the opponent; COMBO: enchantress + free-cast enchantment loops (e.g.
            // Sythis-style combo). Excludes AGGRO/TEMPO/RAMP -- enchantress is a slow card-advantage
            // engine, not a fast-clock or acceleration identity.
            compatibleArchetypes = setOf(ArchetypeId.MIDRANGE, ArchetypeId.CONTROL, ArchetypeId.COMBO),
            defaultArchetype = ArchetypeId.MIDRANGE,
        )
        add(
            theme = ThemeId.WHEELS,
            description = "Empty your hand and refill it -- wheel effects punish opponents while restocking your own cards.",
            // Plan's own worked example (WS 1.1): WHEELS -> COMBO/CONTROL -- wheel effects are
            // either a combo enabler (discard-then-wheel loops with graveyard/madness payoffs) or a
            // control refill tool. Excludes AGGRO/TEMPO/MIDRANGE/RAMP -- emptying your OWN hand is
            // actively hostile to a creature-board or fast-clock plan.
            compatibleArchetypes = setOf(ArchetypeId.COMBO, ArchetypeId.CONTROL),
            defaultArchetype = ArchetypeId.CONTROL,
        )
        add(
            theme = ThemeId.MILL,
            description = "Put cards from opponents' libraries into their graveyard to win by decking them out.",
            // CONTROL: mill-as-alt-win-con is a grindy control sub-plan; COMBO: dedicated mill combo
            // (deck-out in one turn). Excludes AGGRO/TEMPO/MIDRANGE/RAMP -- mill's clock is far too
            // slow to pair with a creature-combat plan; it needs the control shell's survival tools.
            compatibleArchetypes = setOf(ArchetypeId.CONTROL, ArchetypeId.COMBO),
            defaultArchetype = ArchetypeId.CONTROL,
        )
        add(
            theme = ThemeId.GROUP_HUG,
            description = "Give every player extra resources -- mana, cards, or turns -- to keep the table peaceful and open-ended.",
            // CONTROL: durdle-to-political-win is the natural group-hug shell; RAMP: giving out
            // extra mana/turns pairs with a big-mana plan of your own. Excludes AGGRO (giving away
            // resources directly contradicts racing to close the game)/TEMPO/MIDRANGE/COMBO --
            // group hug's whole premise is giving up tempo/resource advantage for social capital.
            compatibleArchetypes = setOf(ArchetypeId.CONTROL, ArchetypeId.RAMP),
            defaultArchetype = ArchetypeId.CONTROL,
        )
        add(
            theme = ThemeId.GROUP_SLUG,
            description = "Deal damage to every opponent equally with symmetrical effects that punish the whole table.",
            // CONTROL: grindy symmetrical-damage engines (e.g. Wound Reflection-style) that outlast
            // the table; AGGRO: burn-everyone is a real fast multiplayer clock; COMBO: mass-damage
            // combo finishers. Excludes MIDRANGE/TEMPO/RAMP -- group slug is a specific
            // damage-engine identity, not those plans' core.
            compatibleArchetypes = setOf(ArchetypeId.CONTROL, ArchetypeId.AGGRO, ArchetypeId.COMBO),
            defaultArchetype = ArchetypeId.CONTROL,
        )
        add(
            theme = ThemeId.BLINK,
            description = "Repeatedly exile and return your own creatures to re-trigger their enter-the-battlefield abilities.",
            // MIDRANGE: the classic grindy ETB-value engine; CONTROL: blink as a repeatable answer
            // engine (re-trigger removal-on-ETB effects); COMBO: blink combo loops (e.g. free-blink
            // + mana-producing ETB = infinite mana). Excludes AGGRO/TEMPO/RAMP -- blink is a slow
            // value-engine identity, not a fast-clock or acceleration plan.
            compatibleArchetypes = setOf(ArchetypeId.MIDRANGE, ArchetypeId.CONTROL, ArchetypeId.COMBO),
            defaultArchetype = ArchetypeId.MIDRANGE,
        )
        add(
            theme = ThemeId.SUPERFRIENDS,
            description = "Build around planeswalkers -- protect them and stack their loyalty abilities for inevitable value.",
            // CONTROL: planeswalkers-as-inevitability is the archetypal control finisher plan;
            // MIDRANGE: protecting a walker with a creature board is the other common shell.
            // Excludes AGGRO/TEMPO/COMBO/RAMP -- superfriends wants to durdle behind protection, the
            // opposite of a fast clock, and walkers aren't a combo piece or acceleration tool.
            compatibleArchetypes = setOf(ArchetypeId.CONTROL, ArchetypeId.MIDRANGE),
            defaultArchetype = ArchetypeId.CONTROL,
        )
        add(
            theme = ThemeId.VEHICLES,
            description = "Crew vehicles with your creatures to attack as resilient, removal-resistant threats.",
            // AGGRO: crew-and-attack is a fast, removal-resistant clock; MIDRANGE: vehicles as
            // resilient value threats. Excludes CONTROL/TEMPO/COMBO/RAMP -- vehicles are a
            // creature-combat identity, not those plans' core.
            compatibleArchetypes = setOf(ArchetypeId.AGGRO, ArchetypeId.MIDRANGE),
            defaultArchetype = ArchetypeId.AGGRO,
        )
        add(
            theme = ThemeId.TOOLBOX,
            description = "Tutor for the exact answer or piece you need out of a wide toolbox of one-of effects.",
            // CONTROL: a toolbox of situational answers is a classic control tool; MIDRANGE: flexible
            // one-ofs as a value plan; COMBO: tutor-chain-into-combo-piece is a direct combo enabler.
            // Excludes AGGRO/TEMPO/RAMP -- toolbox singleton value is a slow, grindy identity, not a
            // fast-clock or acceleration plan.
            compatibleArchetypes = setOf(ArchetypeId.CONTROL, ArchetypeId.MIDRANGE, ArchetypeId.COMBO),
            defaultArchetype = ArchetypeId.CONTROL,
        )
        add(
            theme = ThemeId.CLONES_THEFT,
            description = "Copy or steal your opponents' best creatures and permanents instead of building your own.",
            // MIDRANGE: stealing/copying the best thing on the board is a flexible value plan;
            // COMBO: clone-combo pieces (copying a specific combo enabler); CONTROL: stealing an
            // opponent's finisher doubles as removal. Excludes AGGRO/TEMPO/RAMP -- clone/theft
            // reacts to what opponents play, the opposite of a proactive fast-clock or ramp plan.
            compatibleArchetypes = setOf(ArchetypeId.MIDRANGE, ArchetypeId.COMBO, ArchetypeId.CONTROL),
            defaultArchetype = ArchetypeId.MIDRANGE,
        )
    }

    // putIfAbsent is JVM-only -- plain insert-only-if-missing check instead (KMP-safe, mirrors
    // DeckIdentitySeedTags' own reverse-map construction convention). Not used here since this
    // reverse map accumulates a SET per archetype (every theme that lists it), not a first-wins
    // scalar -- buildMap + getOrPut is the right shape.
    private val THEME_ARCHETYPE_REVERSE: Map<ArchetypeId, Set<ThemeId>> = buildMap<ArchetypeId, MutableSet<ThemeId>> {
        THEME_CATALOG.forEach { (theme, entry) ->
            entry.compatibleArchetypes.forEach { archetype ->
                getOrPut(archetype) { mutableSetOf() } += theme
            }
        }
    }
}
