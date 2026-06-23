package com.mmg.manahub.core.tagging

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DetectionRule
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.model.TagDictionaryEntry
import com.mmg.manahub.core.tagging.TagDictionary.applyOverrides
import com.mmg.manahub.core.util.CardTypeTranslator
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════════════════
//  Tag dictionary (English-only analysis, future-proof label structure)
//
//  Process-wide singleton, populated at class-init with a baseline set of labels
//  and English oracle-text detection rules.
//
//  DESIGN DECISIONS (do not re-litigate):
//   - Analysis is ENGLISH-ONLY: the [StrategyAnalyzer] scans only the English
//     `card.oracleText`. Non-English printings are resolved to their English
//     version upstream before analysis ever runs.
//   - Labels remain a `Map<String, String>` keyed by language code so we can show
//     tag labels in other languages later, but only "en" is populated today.
//   - Detection patterns are structured [DetectionRule]s (allOf / anyOf / noneOf
//     + type-line conditions) rather than loose OR'd substrings. This fixes a
//     class of false positives (e.g. "gain control of target creature" matching
//     "lifegain").
//
//  User overrides are loaded at app start by [TagDictionaryRepository] and merged
//  on top via [applyOverrides]. The Settings → Tag Dictionary screen edits those
//  overrides directly.
//
//  Lookups:
//   - localize(tag)            → label in current device language ("en" fallback)
//   - findKeysByTerm("Flying") → ["flying"]   (reverse, label/key search)
//
//  TYPE-category tags (creature subtypes etc.) are *not* stored here; they
//  delegate to the existing [CardTypeTranslator] so we have a single source of
//  truth for type translations.
// ═══════════════════════════════════════════════════════════════════════════════

object TagDictionary {

    /**
     * Backwards-compatible alias for [TagDictionaryEntry].
     *
     * The data class moved to `:shared:core-model` (`commonMain`) during the KMP migration so
     * that the shared tagging analyzers can reference it. This alias keeps existing
     * `TagDictionary.Entry` references inside `:app` compiling without edits.
     */
    @Suppress("unused")
    typealias Entry = TagDictionaryEntry

    /** Mutable map so [applyOverrides] can replace entries at runtime. */
    @Volatile
    private var entries: Map<String, TagDictionaryEntry> = baseEntries.associateBy { it.key }

    // ── Public API ───────────────────────────────────────────────────────────

    fun all(): Collection<TagDictionaryEntry> = entries.values

    fun get(key: String): TagDictionaryEntry? = entries[key]

    /** Localize a CardTag for the current locale. Returns null if unknown. */
    fun localize(tag: CardTag, lang: String = currentLang()): String? = when (tag.category) {
        TagCategory.TYPE -> {
            // "basic_land" -> "Basic Land"
            val words = tag.key.split("_")
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

            // Delegate to the type translator (single source of truth).
            CardTypeTranslator.translateTypeLine(words, lang).takeIf { it.isNotBlank() }
        }
        else -> entries[tag.key]?.labels?.get(lang)
            ?: entries[tag.key]?.labels?.get("en")
    }

    /** Reverse search: find canonical keys whose label or key contains [term]. */
    fun findKeysByTerm(term: String): List<String> {
        if (term.isBlank()) return emptyList()
        val needle = term.trim().lowercase()
        val hits = mutableSetOf<String>()
        entries.values.forEach { e ->
            e.labels.values.forEach { label ->
                if (label.lowercase().contains(needle)) hits += e.key
            }
            if (e.key.contains(needle)) hits += e.key
        }
        return hits.toList()
    }

    /** Apply persisted user overrides on top of the base entries. */
    @Synchronized
    fun applyOverrides(overrides: List<TagOverride>) {
        if (overrides.isEmpty()) {
            entries = baseEntries.associateBy { it.key }
            return
        }
        val merged = baseEntries.associateBy { it.key }.toMutableMap()
        overrides.forEach { ov ->
            val parsedRules = ov.patterns.mapNotNull { parseRuleLine(it) }
            val base = merged[ov.key]
            if (base != null) {
                merged[ov.key] = base.copy(
                    // Labels merge per language key (en-only in practice).
                    labels = base.labels + ov.labels,
                    // A non-empty override rule set REPLACES the base entry's rules,
                    // preserving the pre-existing wholesale-replacement behavior.
                    rules  = if (parsedRules.isNotEmpty()) parsedRules else base.rules,
                )
            } else {
                // Net-new entry from the user.
                merged[ov.key] = TagDictionaryEntry(
                    key            = ov.key,
                    category       = ov.category ?: TagCategory.STRATEGY,
                    labels         = ov.labels,
                    rules          = parsedRules,
                    baseConfidence = 0.85f,
                )
            }
        }
        entries = merged
    }

    /**
     * Parse a single user override rule line into a [DetectionRule].
     *
     * Syntax:
     *  - Terms separated by " + " are ANDed (allOf).
     *  - A term prefixed with "!" is negative (noneOf).
     *  - A plain line is a single-substring rule.
     *
     * Example: `gain + life + !gain control` →
     *   allOf = ["gain", "life"], noneOf = ["gain control"].
     *
     * Returns null when the line yields no usable terms.
     */
    fun parseRuleLine(line: String): DetectionRule? {
        val terms = line.split(" + ").map { it.trim() }.filter { it.isNotEmpty() }
        if (terms.isEmpty()) return null
        val allOf  = mutableListOf<String>()
        val noneOf = mutableListOf<String>()
        terms.forEach { term ->
            if (term.startsWith("!")) {
                val negated = term.removePrefix("!").trim()
                if (negated.isNotEmpty()) noneOf += negated.lowercase()
            } else {
                allOf += term.lowercase()
            }
        }
        if (allOf.isEmpty() && noneOf.isEmpty()) return null
        return DetectionRule(allOf = allOf, noneOf = noneOf)
    }

    /**
     * Render a [DetectionRule] back into the user override line syntax used by the
     * Tag Dictionary screen. anyOf/typeLine conditions are not expressible in the
     * simple line syntax and are omitted from the rendered text (base rules using
     * them still show their allOf/noneOf terms).
     */
    fun renderRuleLine(rule: DetectionRule): String {
        val parts = rule.allOf + rule.noneOf.map { "!$it" }
        return parts.joinToString(" + ")
    }

    private fun currentLang(): String = Locale.getDefault().language.lowercase()
}

/**
 * Persisted user override applied on top of [TagDictionary.baseEntries].
 *
 * [patterns] is a list of rule lines in the user syntax (see
 * [TagDictionary.parseRuleLine]); [labels] is keyed by language code (en-only in
 * practice).
 */
data class TagOverride(
    val key:      String,
    val category: TagCategory?       = null,
    val labels:   Map<String, String> = emptyMap(),
    val patterns: List<String>        = emptyList(),
)

// ═══════════════════════════════════════════════════════════════════════════════
//  Base dictionary — keywords + strategies + roles + archetypes + tribal
//  English labels and English oracle-text detection rules only.
//
//  IMPORTANT: tag keys are persisted in user data — NEVER rename an existing key.
//  When adding compound-phrase detection, prefer allOf over loosely OR'd word
//  fragments to avoid false positives.
// ═══════════════════════════════════════════════════════════════════════════════

private val baseEntries: List<TagDictionaryEntry> = buildList {

    // ── KEYWORD entries (labels only; KeywordAnalyzer reads card.keywords) ─────
    // Evergreen + popular recurring/deciduous keywords Scryfall emits in `keywords`.
    add(kw("flying",        "Flying"))
    add(kw("first_strike",  "First Strike"))
    add(kw("double_strike", "Double Strike"))
    add(kw("trample",       "Trample"))
    add(kw("haste",         "Haste"))
    add(kw("vigilance",     "Vigilance"))
    add(kw("lifelink",      "Lifelink"))
    add(kw("deathtouch",    "Deathtouch"))
    add(kw("reach",         "Reach"))
    add(kw("hexproof",      "Hexproof"))
    add(kw("indestructible","Indestructible"))
    add(kw("menace",        "Menace"))
    add(kw("defender",      "Defender"))
    add(kw("flash",         "Flash"))
    add(kw("prowess",       "Prowess"))
    add(kw("ward",          "Ward"))
    add(kw("cascade",       "Cascade"))
    add(kw("convoke",       "Convoke"))
    add(kw("landfall",      "Landfall"))
    add(kw("cycling",       "Cycling"))
    add(kw("equip",         "Equip"))
    // ── Additional keyword labels ──────────────────────────────────────────────
    add(kw("enchant",       "Enchant"))
    // NOTE: `protection` and `mill` keyword keys collide with the richer detection
    // entries declared further below (protection→ROLE, mill→STRATEGY). They are
    // intentionally NOT declared as keyword labels here so the detection entry is
    // the single Entry for that key. KeywordAnalyzer still emits the tag from
    // card.keywords; the detection Entry supplies its label + rules.
    add(kw("goad",          "Goad"))
    add(kw("scry",          "Scry"))
    add(kw("surveil",       "Surveil"))
    add(kw("explore",       "Explore"))
    add(kw("investigate",   "Investigate"))
    add(kw("amass",         "Amass"))
    add(kw("foretell",      "Foretell"))
    add(kw("flashback",     "Flashback"))
    add(kw("kicker",        "Kicker"))
    add(kw("madness",       "Madness"))
    add(kw("morph",         "Morph"))
    add(kw("disturb",       "Disturb"))
    add(kw("mutate",        "Mutate"))
    add(kw("ninjutsu",      "Ninjutsu"))
    add(kw("delve",         "Delve"))
    add(kw("dredge",        "Dredge"))
    add(kw("storm",         "Storm"))
    add(kw("suspend",       "Suspend"))
    add(kw("exalted",       "Exalted"))
    add(kw("extort",        "Extort"))
    add(kw("annihilator",   "Annihilator"))
    add(kw("infect",        "Infect"))
    add(kw("toxic",         "Toxic"))
    add(kw("affinity",      "Affinity"))
    add(kw("adapt",         "Adapt"))
    add(kw("evolve",        "Evolve"))
    add(kw("crew",          "Crew"))
    add(kw("fabricate",     "Fabricate"))
    add(kw("embalm",        "Embalm"))
    add(kw("eternalize",    "Eternalize"))
    add(kw("escape",        "Escape"))
    add(kw("emerge",        "Emerge"))
    add(kw("evoke",         "Evoke"))
    add(kw("bestow",        "Bestow"))
    add(kw("dash",          "Dash"))
    add(kw("exploit",       "Exploit"))
    add(kw("connive",       "Connive"))
    add(kw("blitz",         "Blitz"))
    add(kw("casualty",      "Casualty"))
    add(kw("prototype",     "Prototype"))
    add(kw("unearth",       "Unearth"))
    add(kw("backup",        "Backup"))
    add(kw("bargain",       "Bargain"))
    add(kw("discover",      "Discover"))
    add(kw("craft",         "Craft"))
    add(kw("plot",          "Plot"))
    add(kw("saddle",        "Saddle"))
    add(kw("offspring",     "Offspring"))
    add(kw("impending",     "Impending"))

    // ════════════════════════════════════════════════════════════════════════════
    //  STRATEGY / ROLE / ARCHETYPE entries with English detection rules.
    //  Rule confidences: when a rule has no explicit `confidence`, it falls back to
    //  the entry's `baseConfidence`.
    // ════════════════════════════════════════════════════════════════════════════

    // ── tokens ──────────────────────────────────────────────────────────────────
    add(strat("tokens", TagCategory.STRATEGY, "Tokens", 0.95f, listOf(
        rule(allOf = listOf("create", "token")),
    )))

    // ── ramp (basic lands excluded via typeLineNoneOf) ───────────────────────────
    add(strat("ramp", TagCategory.ARCHETYPE, "Ramp", 0.90f, listOf(
        rule(allOf = listOf("search your library for", "land", "onto the battlefield"),
            typeLineNoneOf = listOf("basic"), confidence = 0.95f),
        rule(allOf = listOf("put", "land", "onto the battlefield"),
            typeLineNoneOf = listOf("basic"), confidence = 0.85f),
        rule(allOf = listOf("you may play an additional land"), confidence = 0.95f),
        // Weak signal: nonbasic utility/dual lands that tap for mana.
        rule(allOf = listOf("{t}: add"), typeLineAnyOf = listOf("land"),
            typeLineNoneOf = listOf("basic"), confidence = 0.65f),
    )))

    // ── card_draw ─────────────────────────────────────────────────────────────────
    add(strat("card_draw", TagCategory.ROLE, "Card Draw", 0.90f, listOf(
        rule(anyOf = listOf(
            "draw a card", "draws a card", "draw two cards", "draw three cards",
            "draw x cards", "draw that many cards", "draw cards equal",
        )),
    )))

    // ── removal ─────────────────────────────────────────────────────────────────
    add(strat("removal", TagCategory.ROLE, "Removal", 0.90f, listOf(
        rule(allOf = listOf("destroy target"), noneOf = listOf("destroy target land"), confidence = 0.92f),
        rule(allOf = listOf("exile target"),
            anyOf = listOf("creature", "permanent", "planeswalker", "artifact", "enchantment"),
            confidence = 0.92f),
        rule(anyOf = listOf("deals damage to any target", "damage to any target"), confidence = 0.90f),
        rule(anyOf = listOf("fights target creature", "fight target creature", "fights up to one target"),
            confidence = 0.85f),
        rule(allOf = listOf("target creature gets -"), confidence = 0.80f),
    )))

    // ── board_wipe ──────────────────────────────────────────────────────────────
    add(strat("board_wipe", TagCategory.ROLE, "Board Wipe", 0.95f, listOf(
        rule(anyOf = listOf(
            "destroy all creatures", "destroy each creature", "exile all creatures",
            "exile each creature", "destroy all other creatures",
        ), confidence = 0.97f),
        rule(anyOf = listOf("destroy all", "exile all", "destroy each", "exile each", "each player sacrifices"),
            confidence = 0.85f),
        rule(allOf = listOf("deals", "damage to each creature"), confidence = 0.90f),
        rule(allOf = listOf("all creatures get -"), confidence = 0.90f),
    )))

    // ── counterspell ────────────────────────────────────────────────────────────
    add(strat("counterspell", TagCategory.ROLE, "Counterspell", 0.95f, listOf(
        rule(anyOf = listOf("counter target", "counter that spell", "counter all")),
    )))

    // ── lifegain (fixes the OR-bug; "gain control" excluded) ─────────────────────
    add(strat("lifegain", TagCategory.STRATEGY, "Lifegain", 0.80f, listOf(
        rule(allOf = listOf("gain", "life"), noneOf = listOf("gain control"), confidence = 0.80f),
        rule(allOf = listOf("gains", "life"), confidence = 0.75f),
        rule(allOf = listOf("lifelink"), confidence = 0.80f),
        rule(allOf = listOf("whenever you gain life"), confidence = 0.97f),
    )))

    // ── burn (fixes the "deals" everywhere bug) ──────────────────────────────────
    add(strat("burn", TagCategory.STRATEGY, "Burn", 0.80f, listOf(
        rule(allOf = listOf("deals", "damage to"),
            anyOf = listOf("any target", "target player", "target opponent", "each opponent", "each player"),
            confidence = 0.85f),
        rule(allOf = listOf("deals", "damage to target creature"), confidence = 0.70f),
    )))

    // ── graveyard (generic, suggestion-only) ─────────────────────────────────────
    add(strat("graveyard", TagCategory.STRATEGY, "Graveyard", 0.65f, listOf(
        rule(allOf = listOf("graveyard"), confidence = 0.65f),
    )))

    // ── sacrifice ───────────────────────────────────────────────────────────────
    add(strat("sacrifice", TagCategory.STRATEGY, "Sacrifice", 0.80f, listOf(
        rule(anyOf = listOf(
            "sacrifice a creature", "sacrifice another", "sacrifice a permanent",
            "sacrifice an artifact", "sacrifice this",
        ), confidence = 0.80f),
        rule(allOf = listOf("as an additional cost", "sacrifice"), confidence = 0.85f),
    )))

    // ── blink ───────────────────────────────────────────────────────────────────
    add(strat("blink", TagCategory.STRATEGY, "Blink/ETB", 0.90f, listOf(
        rule(allOf = listOf("exile", "return", "to the battlefield"),
            anyOf = listOf("you control", "you own"), confidence = 0.85f),
        rule(allOf = listOf("exile up to one target creature", "return"), confidence = 0.90f),
    )))

    // ── tutor ───────────────────────────────────────────────────────────────────
    add(strat("tutor", TagCategory.ROLE, "Tutor", 0.85f, listOf(
        rule(allOf = listOf("search your library for"),
            noneOf = listOf("basic land", "land card"), confidence = 0.85f),
    )))

    // ── plus_counters ─────────────────────────────────────────────────────────────
    add(strat("plus_counters", TagCategory.STRATEGY, "+1/+1 Counters", 0.95f, listOf(
        rule(allOf = listOf("+1/+1 counter")),
    )))

    // ── proliferate ─────────────────────────────────────────────────────────────
    add(strat("proliferate", TagCategory.STRATEGY, "Proliferate", 0.99f, listOf(
        rule(allOf = listOf("proliferate")),
    )))

    // ── protection (ROLE — supersedes the keyword label of the same key) ─────────
    add(strat("protection", TagCategory.ROLE, "Protection", 0.80f, listOf(
        rule(anyOf = listOf(
            "hexproof", "indestructible", "protection from", "can't be targeted",
            "gains shroud", "phases out", "ward ",
        )),
    )))

    // ── mana_rock ───────────────────────────────────────────────────────────────
    add(strat("mana_rock", TagCategory.ROLE, "Mana Rock", 0.90f, listOf(
        rule(allOf = listOf("{t}: add"),
            typeLineAnyOf = listOf("artifact"), typeLineNoneOf = listOf("creature", "land")),
    )))

    // ── mana_dork ───────────────────────────────────────────────────────────────
    add(strat("mana_dork", TagCategory.ROLE, "Mana Dork", 0.90f, listOf(
        rule(allOf = listOf("{t}: add"),
            typeLineAnyOf = listOf("creature"), typeLineNoneOf = listOf("land", "artifact")),
    )))

    // ── win_con (manual-pick role + a strong detection rule) ─────────────────────
    add(strat("win_con", TagCategory.ROLE, "Win Condition", 0.90f, listOf(
        rule(anyOf = listOf("you win the game", "each opponent loses the game"),
            noneOf = listOf("you lose the game", "loses the game unless"), confidence = 0.95f),
    )))

    // ── stax ────────────────────────────────────────────────────────────────────
    add(strat("stax", TagCategory.STRATEGY, "Stax/Prison", 0.80f, listOf(
        rule(anyOf = listOf(
            "can't untap", "don't untap during", "spells cost {1} more", "cost {1} more to cast",
            "can't cast spells", "players can't", "can't be activated",
        )),
    )))

    // ── New ROLE entries ──────────────────────────────────────────────────────────
    add(strat("bounce", TagCategory.ROLE, "Bounce", 0.90f, listOf(
        rule(allOf = listOf("return", "to its owner's hand")),
        rule(allOf = listOf("return", "to their owners' hands")),
    )))
    add(strat("discard", TagCategory.ROLE, "Discard", 0.90f, listOf(
        rule(anyOf = listOf(
            "target player discards", "each opponent discards", "that player discards",
            "each player discards",
        )),
    )))
    add(strat("land_destruction", TagCategory.ROLE, "Land Destruction", 0.95f, listOf(
        rule(anyOf = listOf("destroy target land", "destroy all lands")),
    )))
    add(strat("graveyard_hate", TagCategory.ROLE, "Graveyard Hate", 0.85f, listOf(
        rule(allOf = listOf("exile", "graveyard"),
            anyOf = listOf("all cards", "each player's", "target player's", "every"), confidence = 0.85f),
        rule(allOf = listOf("exile target card from a graveyard"), confidence = 0.90f),
    )))
    add(strat("anthem", TagCategory.ROLE, "Anthem", 0.90f, listOf(
        rule(anyOf = listOf("creatures you control get +", "other creatures you control get +")),
    )))
    add(strat("evasion", TagCategory.ROLE, "Evasion", 0.90f, listOf(
        rule(anyOf = listOf("can't be blocked", "is unblockable")),
    )))
    add(strat("cost_reduction", TagCategory.ROLE, "Cost Reduction", 0.90f, listOf(
        rule(anyOf = listOf("cost {1} less to cast", "cost {2} less to cast", "spells you cast cost")),
    )))
    add(strat("free_spells", TagCategory.ROLE, "Free Spells", 0.95f, listOf(
        rule(allOf = listOf("without paying its mana cost")),
    )))
    add(strat("pinger", TagCategory.ROLE, "Pinger", 0.85f, listOf(
        rule(allOf = listOf("{t}:", "deals 1 damage")),
    )))
    add(strat("untapper", TagCategory.ROLE, "Untapper", 0.85f, listOf(
        rule(anyOf = listOf("untap target", "untap all", "untap up to")),
    )))
    add(strat("recursion", TagCategory.ROLE, "Recursion", 0.85f, listOf(
        rule(allOf = listOf("return", "from your graveyard to your hand")),
    )))

    // ── New STRATEGY entries ─────────────────────────────────────────────────────
    add(strat("reanimator", TagCategory.STRATEGY, "Reanimator", 0.95f, listOf(
        rule(allOf = listOf("return", "from your graveyard to the battlefield")),
        rule(allOf = listOf("return", "from a graveyard to the battlefield")),
        rule(allOf = listOf("put", "from your graveyard onto the battlefield")),
    )))
    add(strat("mill", TagCategory.STRATEGY, "Mill", 0.95f, listOf(
        rule(anyOf = listOf("mills ", "mill x", "mills x", "mill that many")),
        rule(allOf = listOf("mills"), confidence = 0.90f),
    )))
    add(strat("wheel", TagCategory.STRATEGY, "Wheel", 0.90f, listOf(
        rule(allOf = listOf("each player", "discards", "draws")),
    )))
    add(strat("extra_turns", TagCategory.STRATEGY, "Extra Turns", 0.97f, listOf(
        rule(allOf = listOf("take an extra turn")),
    )))
    add(strat("extra_combats", TagCategory.STRATEGY, "Extra Combats", 0.95f, listOf(
        rule(anyOf = listOf("additional combat phase", "extra combat")),
    )))
    add(strat("theft", TagCategory.STRATEGY, "Theft", 0.90f, listOf(
        rule(anyOf = listOf("gain control of target", "gain control of all", "gains control of")),
    )))
    add(strat("clones", TagCategory.STRATEGY, "Clones", 0.92f, listOf(
        rule(anyOf = listOf(
            "as a copy of", "becomes a copy of", "copy of target creature", "copy of any creature",
        )),
    )))
    add(strat("spell_copy", TagCategory.STRATEGY, "Spell Copy", 0.90f, listOf(
        rule(anyOf = listOf(
            "copy target instant", "copy target sorcery", "copy that spell",
            "copy it. you may choose new targets",
        )),
    )))
    add(strat("spellslinger", TagCategory.STRATEGY, "Spellslinger", 0.90f, listOf(
        rule(anyOf = listOf(
            "whenever you cast an instant or sorcery", "instant and sorcery spells you cast",
            "whenever you cast a noncreature spell", "magecraft",
        )),
    )))
    add(strat("artifacts_matter", TagCategory.STRATEGY, "Artifacts Matter", 0.85f, listOf(
        rule(anyOf = listOf(
            "whenever an artifact you control enters", "artifacts you control", "for each artifact",
            "artifact spells you cast", "whenever you cast an artifact spell", "metalcraft",
            "affinity for artifacts",
        )),
    )))
    // enchantress: enchantments-matter (key already existed as a plain ARCHETYPE pick).
    add(strat("enchantress", TagCategory.STRATEGY, "Enchantress", 0.85f, listOf(
        rule(anyOf = listOf(
            "whenever you cast an enchantment", "enchantments you control", "for each enchantment",
            "constellation",
        )),
    )))
    add(strat("equipment_matters", TagCategory.STRATEGY, "Equipment Matters", 0.85f, listOf(
        rule(anyOf = listOf("equipped creature", "whenever an equipment", "equip ")),
    )))
    add(strat("auras", TagCategory.STRATEGY, "Auras", 0.80f, listOf(
        rule(anyOf = listOf("enchanted creature gets", "enchanted creature has", "whenever you cast an aura")),
    )))
    add(strat("lands_matter", TagCategory.STRATEGY, "Lands Matter", 0.85f, listOf(
        rule(anyOf = listOf(
            "landfall", "whenever a land you control enters", "whenever a land enters",
            "for each land you control", "lands you control",
        )),
    )))
    add(strat("death_triggers", TagCategory.STRATEGY, "Death Triggers", 0.90f, listOf(
        rule(anyOf = listOf(
            "whenever a creature you control dies", "whenever another creature you control dies",
            "whenever a creature dies", "whenever this creature dies", "when this creature dies",
        )),
    )))
    add(strat("etb", TagCategory.STRATEGY, "ETB", 0.70f, listOf(
        rule(allOf = listOf("when", "enters"), confidence = 0.70f),
        rule(anyOf = listOf(
            "whenever a creature you control enters", "whenever another creature you control enters",
        ), confidence = 0.90f),
    )))
    add(strat("lifedrain", TagCategory.STRATEGY, "Lifedrain", 0.85f, listOf(
        rule(allOf = listOf("loses", "life", "you gain")),
        rule(allOf = listOf("lose", "life", "gain that much life")),
    )))
    add(strat("loot", TagCategory.STRATEGY, "Looting", 0.85f, listOf(
        rule(allOf = listOf("draw a card, then discard")),
        rule(allOf = listOf("discard a card", "draw a card"), confidence = 0.80f),
    )))
    add(strat("impulse_draw", TagCategory.STRATEGY, "Impulse Draw", 0.90f, listOf(
        rule(allOf = listOf("exile the top"), anyOf = listOf("may play", "may cast")),
    )))
    add(strat("card_selection", TagCategory.STRATEGY, "Card Selection", 0.75f, listOf(
        rule(allOf = listOf("look at the top", "of your library")),
    )))
    add(strat("minus_counters", TagCategory.STRATEGY, "-1/-1 Counters", 0.90f, listOf(
        rule(allOf = listOf("-1/-1 counter")),
    )))
    add(strat("monarch", TagCategory.STRATEGY, "Monarch", 0.95f, listOf(
        rule(allOf = listOf("the monarch")),
    )))
    add(strat("group_hug", TagCategory.STRATEGY, "Group Hug", 0.80f, listOf(
        rule(anyOf = listOf("each player draws", "each player may draw")),
    )))
    add(strat("group_slug", TagCategory.STRATEGY, "Group Slug", 0.80f, listOf(
        rule(anyOf = listOf(
            "each opponent loses", "deals damage to each opponent", "deals damage to each player",
        )),
    )))
    add(strat("devotion", TagCategory.STRATEGY, "Devotion", 0.95f, listOf(
        rule(allOf = listOf("devotion to")),
    )))
    add(strat("doublers", TagCategory.STRATEGY, "Doublers", 0.85f, listOf(
        rule(anyOf = listOf("is doubled", "double that", "twice that many", "twice that much")),
    )))
    add(strat("sneak", TagCategory.STRATEGY, "Sneak (Cheat into Play)", 0.88f, listOf(
        rule(allOf = listOf("put", "from your hand onto the battlefield")),
        rule(allOf = listOf("put", "from your library onto the battlefield")),
    )))
    add(strat("treasure", TagCategory.STRATEGY, "Treasure", 0.92f, listOf(
        rule(allOf = listOf("treasure token")),
    )))
    add(strat("food", TagCategory.STRATEGY, "Food", 0.92f, listOf(
        rule(allOf = listOf("food token")),
    )))
    add(strat("clues", TagCategory.STRATEGY, "Clues", 0.92f, listOf(
        rule(anyOf = listOf("clue token", "investigate")),
    )))
    add(strat("poison", TagCategory.STRATEGY, "Poison", 0.92f, listOf(
        rule(anyOf = listOf("poison counter", "toxic ", "infect")),
    )))
    add(strat("energy", TagCategory.STRATEGY, "Energy", 0.95f, listOf(
        rule(allOf = listOf("{e}")),
    )))
    add(strat("dungeon", TagCategory.STRATEGY, "Dungeon", 0.97f, listOf(
        rule(allOf = listOf("venture into")),
    )))
    add(strat("the_ring", TagCategory.STRATEGY, "The Ring", 0.97f, listOf(
        rule(allOf = listOf("the ring tempts you")),
    )))
    add(strat("pillow_fort", TagCategory.STRATEGY, "Pillow Fort", 0.85f, listOf(
        rule(allOf = listOf("attack you", "unless")),
        rule(anyOf = listOf("can't attack you", "can't attack you or planeswalkers you control")),
    )))

    // ── ARCHETYPE plains (manual-pick only) ──────────────────────────────────────
    add(plain("aggro",          TagCategory.ARCHETYPE, "Aggro"))
    add(plain("control",        TagCategory.ARCHETYPE, "Control"))
    add(plain("combo",          TagCategory.ARCHETYPE, "Combo"))
    add(plain("midrange",       TagCategory.ARCHETYPE, "Midrange"))
    add(plain("tempo",          TagCategory.ARCHETYPE, "Tempo"))
    add(plain("tribal",         TagCategory.STRATEGY,  "Tribal"))
    add(plain("infinite_combo", TagCategory.STRATEGY,  "Infinite Combo"))
    add(plain("game_changer",   TagCategory.ROLE,      "Game Changer"))
    add(plain("voltron",        TagCategory.ARCHETYPE, "Voltron"))

    // ── TRIBAL plains ─────────────────────────────────────────────────────────────
    add(plain("goblin",   TagCategory.TRIBAL, "Goblin"))
    add(plain("elf",      TagCategory.TRIBAL, "Elf"))
    add(plain("dragon",   TagCategory.TRIBAL, "Dragon"))
    add(plain("zombie",   TagCategory.TRIBAL, "Zombie"))
    add(plain("wizard",   TagCategory.TRIBAL, "Wizard"))
    add(plain("vampire",  TagCategory.TRIBAL, "Vampire"))
    add(plain("angel",    TagCategory.TRIBAL, "Angel"))
    add(plain("demon",    TagCategory.TRIBAL, "Demon"))
    add(plain("human",    TagCategory.TRIBAL, "Human"))
    add(plain("merfolk",  TagCategory.TRIBAL, "Merfolk"))
    add(plain("dinosaur", TagCategory.TRIBAL, "Dinosaur"))
    add(plain("sliver",   TagCategory.TRIBAL, "Sliver"))
    add(plain("spirit",   TagCategory.TRIBAL, "Spirit"))
    add(plain("knight",   TagCategory.TRIBAL, "Knight"))
    add(plain("soldier",  TagCategory.TRIBAL, "Soldier"))
    add(plain("cat",      TagCategory.TRIBAL, "Cat"))
    add(plain("rat",      TagCategory.TRIBAL, "Rat"))
    add(plain("bird",     TagCategory.TRIBAL, "Bird"))
    add(plain("hydra",    TagCategory.TRIBAL, "Hydra"))
    add(plain("eldrazi",  TagCategory.TRIBAL, "Eldrazi"))
}.distinctBy { it.key }
// distinctBy is a defensive de-dupe (keeps the FIRST entry per key). The base
// list is built to contain exactly one Entry per key: the `protection` and `mill`
// keyword keys are NOT declared as keyword labels — their richer detection entries
// (declared in the strategy/role block) are the single source for those keys.

/** Single-language keyword label entry (KeywordAnalyzer reads card.keywords directly). */
private fun kw(key: String, en: String) = TagDictionaryEntry(
    key            = key,
    category       = TagCategory.KEYWORD,
    labels         = mapOf("en" to en),
    rules          = emptyList(),
    baseConfidence = 1.0f,
)

/** Strategy/role/archetype entry carrying English detection rules. */
private fun strat(
    key: String,
    category: TagCategory,
    en: String,
    baseConfidence: Float,
    rules: List<DetectionRule>,
) = TagDictionaryEntry(
    key            = key,
    category       = category,
    labels         = mapOf("en" to en),
    rules          = rules,
    baseConfidence = baseConfidence,
)

/** Manual-pick-only entry (no detection rules). */
private fun plain(key: String, category: TagCategory, en: String) = TagDictionaryEntry(
    key            = key,
    category       = category,
    labels         = mapOf("en" to en),
    rules          = emptyList(),
    baseConfidence = 0.0f,
)

/** Convenience builder for a [DetectionRule]. */
private fun rule(
    allOf: List<String> = emptyList(),
    anyOf: List<String> = emptyList(),
    noneOf: List<String> = emptyList(),
    typeLineAnyOf: List<String> = emptyList(),
    typeLineNoneOf: List<String> = emptyList(),
    confidence: Float? = null,
) = DetectionRule(allOf, anyOf, noneOf, typeLineAnyOf, typeLineNoneOf, confidence)
