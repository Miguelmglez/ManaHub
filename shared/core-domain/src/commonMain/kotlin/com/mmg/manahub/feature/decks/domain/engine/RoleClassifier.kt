package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier.Companion.STAR_POWER
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier.Companion.TAG_CONFIDENCE
import com.mmg.manahub.feature.decks.domain.engine.TribeDeriver.normalizeTribeWord

// ═══════════════════════════════════════════════════════════════════════════════
//  RoleClassifier (v2 — pattern bank + weighted roles)
//
//  Maps Card -> Map<DeckRole, Float>: each functional role the card covers, with a
//  confidence in (0,1]. This is the piece that gives the engine "role awareness",
//  something MagicScorer/SynergyScorer did not have.
//
//  Source of truth = the tags the tagging system already produces (locale-safe,
//  key-based). The English oracle text is the SAFETY NET when a card arrives without
//  the relevant tags: a structured pattern bank (RolePattern) of destroy/exile/
//  damage/fight/bounce/edict removal, mass-removal wipes, typed-land/Treasure ramp
//  (excluding one-shot rituals), tiered card advantage, and counter/protection
//  interaction. To improve durable role coverage, enrich the TagDictionary rather
//  than the patterns here; the patterns only fill oracle-fallback gaps.
//
//  Analysis is ENGLISH-ONLY: only `card.oracleText` is scanned (reminder text in
//  parentheses is stripped, the card's own name is replaced with `~`). printedText/
//  lang are never read.
// ═══════════════════════════════════════════════════════════════════════════════

class RoleClassifier {

    /**
     * Weighted classification: every functional [DeckRole] the card covers, mapped to
     * a confidence in (0,1]. Tag-based hits use [TAG_CONFIDENCE]; oracle-fallback hits
     * use the matched [RolePattern]'s confidence. LAND short-circuits to {LAND: 1f};
     * a card with no functional role falls back to {FILLER: 1f} (or {THREAT: w} /
     * {SYNERGY: w} when those incidental roles apply).
     */
    fun classify(card: Card): Map<DeckRole, Float> {
        if (BasicLandCalculator.isLand(card)) return mapOf(DeckRole.LAND to 1f)

        val weights = mutableMapOf<DeckRole, Float>()
        fun credit(role: DeckRole, confidence: Float) {
            val prev = weights[role] ?: 0f
            if (confidence > prev) weights[role] = confidence
        }

        val tagKeys = (card.tags + card.userTags).map { it.key }.toSet()
        val oracle = normalizedOracle(card)

        // ── By tag (preferred, full confidence) ─────────────────────────────────
        if (CardTag.MANA_ROCK.key in tagKeys || CardTag.MANA_DORK.key in tagKeys || CardTag.RAMP.key in tagKeys)
            credit(DeckRole.RAMP, TAG_CONFIDENCE)
        if (CardTag.DRAW_ENGINE.key in tagKeys) credit(DeckRole.CARD_ADVANTAGE, TAG_CONFIDENCE)
        if (CardTag.REMOVAL.key in tagKeys) credit(DeckRole.SPOT_REMOVAL, TAG_CONFIDENCE)
        if (CardTag.WRATH.key in tagKeys) credit(DeckRole.BOARD_WIPE, TAG_CONFIDENCE)
        if (CardTag.COUNTERSPELL.key in tagKeys || CardTag.PROTECTION.key in tagKeys || CardTag.STAX.key in tagKeys)
            credit(DeckRole.INTERACTION, TAG_CONFIDENCE)
        if (CardTag.TUTOR.key in tagKeys) credit(DeckRole.TUTOR, TAG_CONFIDENCE)
        if (CardTag.WIN_CON.key in tagKeys) credit(DeckRole.PAYOFF, TAG_CONFIDENCE)

        // ── Oracle (English) pattern bank — only fills roles a tag did not set ──
        if (oracle.isNotEmpty()) {
            // BOARD_WIPE is evaluated first: a mass effect must NOT also count as spot
            // removal (a "destroy all" is not a targeted answer).
            val isWipe = DeckRole.BOARD_WIPE in weights ||
                ROLE_PATTERNS.any { it.role == DeckRole.BOARD_WIPE && it.matches(oracle) }

            ROLE_PATTERNS.forEach { pattern ->
                // Skip the spot-removal patterns entirely when the card is a board wipe.
                if (pattern.role == DeckRole.SPOT_REMOVAL && isWipe) return@forEach
                // A ritual exclusion guard for RAMP lives inside the pattern predicates.
                if (pattern.matches(oracle)) credit(pattern.role, pattern.confidence)
            }
        }

        // ── Strategy/Tribal with no hard functional role -> synergy gear ───────
        if (weights.keys.none { it.isFunctional }) {
            val hasStrategyTag = (card.tags + card.userTags).any {
                it.category == TagCategory.STRATEGY || it.category == TagCategory.TRIBAL
            }
            if (hasStrategyTag) credit(DeckRole.SYNERGY, TAG_CONFIDENCE)
        }

        // ── Creature with a relevant body and no other role -> threat ──────────
        if (weights.keys.none { it.isFunctional } && card.typeLine.contains("Creature", ignoreCase = true)) {
            val threat = threatConfidence(card)
            if (threat > 0f) credit(DeckRole.THREAT, threat)
        }

        if (weights.isEmpty()) weights[DeckRole.FILLER] = 1f
        return weights
    }

    /**
     * Compatibility view: the SET of roles the card covers (presence only, weights
     * discarded). Existing call sites that only need "does this card cover role X"
     * use this; the weighted [classify] feeds the scoring math.
     */
    fun classifyRoles(card: Card): Set<DeckRole> = classify(card).keys

    // ── THREAT power signal (A5) ─────────────────────────────────────────────────

    /**
     * Confidence that a creature is a relevant threat. Replaces the old
     * `power.toIntOrNull() >= 3` check, which mis-read `*`/`X`/`1+*` power as 0.
     *  · `*` / `X` / `1+*` power -> [STAR_POWER] (format-relative average ~2).
     *  · power >= 3 -> full body confidence.
     *  · power == 2 -> a threat only when cheap (cmc <= 3) or evasive.
     *  · evasion keywords (flying, menace, trample, …) lift a marginal body.
     */
    private fun threatConfidence(card: Card): Float {
        val power = powerToInt(card.power)
        val hasEvasion = card.oracleText?.let { hasEvasionKeyword(it.lowercase()) } ?: false
        val cheap = card.cmc <= 3.0
        return when {
            power >= 4 -> 0.9f
            power == 3 -> 0.8f
            // A 2-power body is a real threat only when it is cheap (≤3 cmc) or evasive;
            // an expensive vanilla 2-power is NOT a threat (A5 — low-cmc bodies count, not big ones).
            power == 2 && (cheap || hasEvasion) -> 0.7f
            power <= 1 && hasEvasion && cheap -> 0.5f
            else -> 0f
        }
    }

    /**
     * Parses a printed power into an Int signal. `*`, `1+*`, `2+*`, `X`, `?` and other
     * variable/derived powers are NOT 0 — they resolve to [STAR_POWER] (a format-
     * relative average) so a Tarmogoyf-style body is still seen as a threat (A5).
     */
    private fun powerToInt(power: String?): Int {
        if (power.isNullOrBlank()) return 0
        power.toIntOrNull()?.let { return it }
        val p = power.lowercase()
        // Variable / derived power: '*', 'X', '1+*', '2+*', '∞', '?'…
        if (p.contains('*') || p.contains('x') || p.contains('?') || p.contains('+') || p.contains('∞'))
            return STAR_POWER
        return 0
    }

    private fun hasEvasionKeyword(oracle: String): Boolean =
        EVASION_KEYWORDS.any { oracle.contains(it) }

    /**
     * Lower-cases the oracle text, strips reminder text in parentheses, and replaces
     * the card's own name with `~` (so a card named "Murder" does not self-match the
     * verb "murder", and "~ deals 3 damage" reads cleanly). English only.
     */
    private fun normalizedOracle(card: Card): String {
        val raw = card.oracleText?.lowercase().orEmpty()
        if (raw.isEmpty()) return ""
        val withoutName = card.name.takeIf { it.isNotBlank() }
            ?.let { raw.replace(it.lowercase(), "~") } ?: raw
        return withoutName.replace(REMINDER_TEXT, "").trim()
    }

    private companion object {
        /** Confidence assigned to a tag-based (curated) classification. */
        const val TAG_CONFIDENCE = 1f

        /** Format-relative average power used for `*`/`X`/`1+*` bodies (A5). */
        const val STAR_POWER = 2

        /** Reminder text in parentheses, stripped before matching. */
        val REMINDER_TEXT = Regex("\\([^)]*\\)")

        val EVASION_KEYWORDS = listOf(
            "flying", "menace", "trample", "shadow", "fear", "intimidate",
            "skulk", "horsemanship", "unblockable", "can't be blocked",
        )

        // ═══════════════════════════════════════════════════════════════════════
        //  Oracle pattern bank — the safety net for tag-less cards.
        //  ONE structured, diffable table. Each RolePattern owns a role, a
        //  confidence and a predicate over the normalized oracle text.
        // ═══════════════════════════════════════════════════════════════════════

        // ── Reusable regexes (English oracle phrasings) ─────────────────────────
        private val DAMAGE_TO_TARGET = Regex(
            "deals? (?:\\d+|x) damage to (?:up to \\w+ )?(?:target|any target|any other target)" +
                "(?:[^.]*?(?:creature|planeswalker|permanent))?",
        )
        private val DAMAGE_TO_ANY = Regex("deals? (?:\\d+|x) damage to any target")
        private val MINUS_TO_TARGET = Regex("target [^.]*?gets? -\\d*x?/-\\d*x?")
        private val FIGHT = Regex("fights? (?:up to \\w+ )?(?:another )?target")
        private val BOUNCE_TARGET = Regex(
            "return target [^.]*?(?:to (?:its|their) owner'?s?'? hand|to the battlefield|on top)",
        )
        private val DESTROY_OR_EXILE_TARGET = Regex(
            "(?:destroy|exile) (?:up to \\w+ )?target [^.]*?(?:creature|planeswalker|permanent|artifact|enchantment|land|nonland|nonblack|tapped|monocolored|spell)",
        )
        private val EDICT = Regex("(?:each (?:opponent|player)|target (?:opponent|player)) [^.]*?sacrifices?")

        // Board wipes
        private val DESTROY_OR_EXILE_ALL = Regex("(?:destroy|exile) all (?:creatures|permanents|nonland)")
        private val DAMAGE_TO_EACH = Regex("deals? (?:\\d+|x) damage to each (?:creature|opponent and each)")
        private val MASS_MINUS = Regex("(?:all|each) creatures? gets? -\\d*x?/-\\d*x?")
        // "each player sacrifices [a creature]" — also catches edict-style sweepers like Smallpox
        // ("each player … sacrifices a creature …"), which are mass effects, not spot removal.
        private val EACH_PLAYER_SACRIFICES = Regex("each player [^.]*?sacrifices? [^.]*?creature")
        private val MASS_BOUNCE = Regex("return all [^.]*?(?:to (?:its|their) owners?'? hands?)")

        // Ramp
        private val TYPED_LAND_FETCH = Regex(
            "search your library for (?:up to \\w+ )?(?:a |an |two )?(?:basic )?" +
                "(?:land|forest|plains|island|swamp|mountain|gate)s?(?:[^.]*?card)?" +
                "[^.]*?(?:onto the battlefield|put (?:it|that card|one|them) onto the battlefield)",
        )
        private val PUT_LAND_ONTO_BATTLEFIELD = Regex(
            "put (?:a |that |target )?land[^.]*?onto the battlefield",
        )
        private val ADD_MANA = Regex("add (?:\\{|one mana|two mana|three mana|that much)")
        private val TREASURE = Regex("create[^.]*?treasure token")
        private val COST_REDUCTION = Regex(
            "(?:spells you cast|creature spells you cast|artifact spells you cast) cost \\{?\\d",
        )

        // Card advantage
        private val DRAW_ONE = Regex("(?<!don't )draw a card")
        private val DRAW_MANY = Regex("draws? (?:two|three|four|five|six|seven|x|\\d+) cards")
        private val REPEATABLE_DRAW = Regex(
            "(?:whenever|at the beginning of|each time)[^.]*?draws? (?:a|\\w+) cards?",
        )
        private val IMPULSE = Regex("exile[^.]*?(?:you may (?:play|cast)|may play|may cast)")
        private val WHEEL = Regex("(?:discards? (?:their|your) hand|then draws seven)")

        // Interaction
        private val COUNTER_SPELL = Regex("counter target (?:[^.]*?)?spell")
        private val GRANT_PROTECTION = Regex(
            "(?:gains?|have|has) (?:hexproof|indestructible|protection from|shroud)",
        )
        private val TAX = Regex("costs? \\{?\\d+\\}? more to cast")

        // Tutor
        private val TUTOR_FOR_CARD = Regex(
            "search your library for (?:a|an|up to)[^.]*?card[^.]*?" +
                "(?:into your hand|on top|reveal|put (?:it|that card) into your hand)",
        )

        /**
         * The pattern bank. Order matters only for BOARD_WIPE-vs-SPOT_REMOVAL, which
         * is resolved in [classify] by suppressing spot-removal patterns on wipes.
         */
        val ROLE_PATTERNS: List<RolePattern> = listOf(
            // ── Board wipe (A2) — evaluated before spot removal ─────────────────
            RolePattern(DeckRole.BOARD_WIPE, 0.95f) { DESTROY_OR_EXILE_ALL.containsMatchIn(it) },
            RolePattern(DeckRole.BOARD_WIPE, 0.9f) { DAMAGE_TO_EACH.containsMatchIn(it) },
            RolePattern(DeckRole.BOARD_WIPE, 0.9f) { MASS_MINUS.containsMatchIn(it) },
            RolePattern(DeckRole.BOARD_WIPE, 0.85f) { EACH_PLAYER_SACRIFICES.containsMatchIn(it) },
            RolePattern(DeckRole.BOARD_WIPE, 0.8f) { MASS_BOUNCE.containsMatchIn(it) },

            // ── Spot removal (A1) ───────────────────────────────────────────────
            RolePattern(DeckRole.SPOT_REMOVAL, 0.95f) { DESTROY_OR_EXILE_TARGET.containsMatchIn(it) },
            RolePattern(DeckRole.SPOT_REMOVAL, 0.9f) { DAMAGE_TO_TARGET.containsMatchIn(it) },
            RolePattern(DeckRole.SPOT_REMOVAL, 0.85f) { DAMAGE_TO_ANY.containsMatchIn(it) },
            RolePattern(DeckRole.SPOT_REMOVAL, 0.9f) { MINUS_TO_TARGET.containsMatchIn(it) },
            RolePattern(DeckRole.SPOT_REMOVAL, 0.85f) { FIGHT.containsMatchIn(it) },
            RolePattern(DeckRole.SPOT_REMOVAL, 0.85f) { EDICT.containsMatchIn(it) },
            // Bounce is removal at a lower confidence (it is temporary).
            RolePattern(DeckRole.SPOT_REMOVAL, 0.55f) { BOUNCE_TARGET.containsMatchIn(it) },

            // ── Ramp (A3) — typed-land fetch / land-to-battlefield / Treasure /
            //    add-mana — BUT a one-shot ritual (instant/sorcery whose only effect
            //    is add-mana) must NOT be RAMP. The predicate rejects rituals.
            RolePattern(DeckRole.RAMP, 0.9f) { TYPED_LAND_FETCH.containsMatchIn(it) },
            RolePattern(DeckRole.RAMP, 0.85f) { PUT_LAND_ONTO_BATTLEFIELD.containsMatchIn(it) },
            RolePattern(DeckRole.RAMP, 0.8f) { TREASURE.containsMatchIn(it) },
            RolePattern(DeckRole.RAMP, 0.7f) { COST_REDUCTION.containsMatchIn(it) },
            RolePattern(DeckRole.RAMP, 0.7f) { oracle -> ADD_MANA.containsMatchIn(oracle) && !isRitual(oracle) },

            // ── Card advantage (A4) — tiered ────────────────────────────────────
            RolePattern(DeckRole.CARD_ADVANTAGE, 0.85f) { WHEEL.containsMatchIn(it) && it.contains("draw") },
            RolePattern(DeckRole.CARD_ADVANTAGE, 0.8f) { DRAW_MANY.containsMatchIn(it) },
            RolePattern(DeckRole.CARD_ADVANTAGE, 0.8f) { REPEATABLE_DRAW.containsMatchIn(it) },
            RolePattern(DeckRole.CARD_ADVANTAGE, 0.5f) { IMPULSE.containsMatchIn(it) },
            // A bare "draw a card" cantrip is real card advantage but LOW weight.
            RolePattern(DeckRole.CARD_ADVANTAGE, 0.3f) { DRAW_ONE.containsMatchIn(it) },

            // ── Interaction ─────────────────────────────────────────────────────
            RolePattern(DeckRole.INTERACTION, 0.9f) { COUNTER_SPELL.containsMatchIn(it) },
            RolePattern(DeckRole.INTERACTION, 0.6f) { GRANT_PROTECTION.containsMatchIn(it) },
            RolePattern(DeckRole.INTERACTION, 0.6f) { TAX.containsMatchIn(it) },

            // ── Tutor ───────────────────────────────────────────────────────────
            RolePattern(DeckRole.TUTOR, 0.85f) { TUTOR_FOR_CARD.containsMatchIn(it) },
        )

        /**
         * A one-shot ritual is an instant/sorcery whose net effect is to add mana
         * (Dark Ritual, Rite of Flame). We can only see the oracle here, so we treat
         * a SHORT oracle dominated by "add {…}" with no permanent payoff as a ritual.
         * Mana rocks/dorks tap for mana ("{t}: add …") and are NOT rejected.
         */
        private fun isRitual(oracle: String): Boolean {
            val mentionsTapForMana = oracle.contains("{t}") || oracle.contains(": add")
            if (mentionsTapForMana) return false
            // A bare "add {b}{b}{b}." with nothing else is a ritual.
            val trimmed = oracle.trim()
            return trimmed.startsWith("add ") || trimmed.startsWith("~ adds") ||
                Regex("^add \\{[^}]*\\}+\\.?$").matches(trimmed)
        }
    }
}

/**
 * A single oracle-fallback rule: when [predicate] holds over the normalized oracle
 * text, the card covers [role] with [confidence]. Kept in one diffable table in
 * [RoleClassifier] so the safety-net heuristics are easy to review and test.
 */
class RolePattern(
    val role: DeckRole,
    val confidence: Float,
    private val predicate: (String) -> Boolean,
) {
    fun matches(oracle: String): Boolean = predicate(oracle)
}

// ═══════════════════════════════════════════════════════════════════════════════
//  TribeDeriver (Phase 2 — fixes B1)
//
//  Derives per-tribe identity signal from a card WITHOUT any DB / CardTag change:
//   · [subtypeKeys] — `tribe:<subtype>` keys from the card's creature subtypes
//     (the portion of `typeLine` after the em/hyphen dash). Structural and
//     language-neutral; the subtype list is the same in every language Scryfall
//     emits the type line, so this does not break the "English-only oracle
//     analysis" rule — type-line subtypes are structural data, not oracle prose.
//   · [payoffTribeKeys] — `tribe:<tribe>` keys NAMED in a card's English oracle
//     text as a tribal PAYOFF ("Other Elves you control", "Elves you control
//     get +1/+1", "choose a creature type"). Lords/anthems that pump a tribe are
//     credited against THAT tribe so a tribeless lord still aligns with its deck.
//   · [tribeKeys] — the union: the card's full per-tribe identity (subtypes ∪
//     payoff tribes), used as the card-side synergy signal in [DeckScorer.fit].
//
//  The derived `tribe:` keys are RUNTIME-ONLY: they are never written to `card.tags`,
//  `userTags`, Room or the tagging store — they exist only inside the scoring engine
//  for the duration of a profile/fit call (the prefix [TRIBE_PREFIX] guarantees they
//  can never collide with a persisted tag key, and they are not in [CardTag.canonical]).
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Pure, stateless derivation of per-tribe identity keys from a [Card]. Shared by
 * [DeckScorer.profile] (to build the deck's per-tribe fingerprint) and
 * [DeckScorer.fit] (to match a candidate's own tribes against that fingerprint).
 */
object TribeDeriver {

    /** Prefix for every derived tribe fingerprint/identity key (`tribe:elf`, …). */
    const val TRIBE_PREFIX = "tribe:"

    /**
     * Creature/Tribal subtypes parsed from the part of [Card.typeLine] AFTER the dash,
     * each as a `tribe:<subtype-lowercase>` key. Returns empty for cards that are not
     * creatures (and not Tribal/Kindred) or have no subtype segment.
     */
    fun subtypeKeys(card: Card): Set<String> {
        if (!isTribalType(card.typeLine)) return emptySet()
        return subtypes(card.typeLine).map { TRIBE_PREFIX + it }.toSet()
    }

    /**
     * Raw lowercase creature subtypes from a type line ("Legendary Creature — Elf
     * Warrior" → {elf, warrior}). Multiword tokens (split on whitespace) are kept as
     * individual subtype words — MTG subtypes are single words.
     */
    fun subtypes(typeLine: String): Set<String> {
        val dash = typeLine.indexOfFirst { it == '—' || it == '–' || it == '-' }
        if (dash < 0) return emptySet()
        return typeLine.substring(dash + 1)
            .split(' ', '\t', '\n')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && it.all { ch -> ch.isLetter() } }
            .toSet()
    }

    /**
     * Tribes a card NAMES in its oracle text as a tribal payoff (lords/anthems,
     * "<Tribe>(s) you control", "other <Tribe>", "choose a creature type"). Used to
     * credit a tribeless lord against the tribe it actually supports.
     *
     * English-only: scans `oracleText` (lower-cased; reminder text and the card's own
     * name are NOT stripped here — payoff phrasing is robust to both). A matched tribe
     * word is normalized via [normalizeTribeWord] (plural → singular) so "elves you
     * control" credits `tribe:elf`, consistent with the singular subtype keys.
     */
    fun payoffTribeKeys(card: Card): Set<String> {
        val oracle = card.oracleText?.lowercase()?.replace(REMINDER, "") ?: return emptySet()
        if (oracle.isBlank()) return emptySet()
        val keys = mutableSetOf<String>()
        // "choose a creature type" / "creatures of the chosen type" — a generic tribal
        // payoff that names no specific tribe; credit it against ALL of the card's own
        // subtypes so a Changeling-style "is every creature type" payoff still aligns
        // with whatever tribe its body declares.
        if (CHOOSE_TYPE.containsMatchIn(oracle)) keys += subtypeKeys(card)
        listOf(TRIBE_YOU_CONTROL, OTHER_TRIBE, TRIBE_GET).forEach { rx ->
            rx.findAll(oracle).forEach { m ->
                val word = m.groupValues.getOrNull(1)?.trim().orEmpty()
                if (word.isNotEmpty()) keys += TRIBE_PREFIX + normalizeTribeWord(word)
            }
        }
        return keys
    }

    /** The card's full per-tribe identity: its own subtypes ∪ the tribes it pays off. */
    fun tribeKeys(card: Card): Set<String> = subtypeKeys(card) + payoffTribeKeys(card)

    private fun isTribalType(typeLine: String): Boolean =
        typeLine.contains("Creature", ignoreCase = true) ||
            typeLine.contains("Tribal", ignoreCase = true) ||
            typeLine.contains("Kindred", ignoreCase = true)

    /**
     * Naive English plural→singular for tribe words so the oracle "elves"/"goblins"/
     * "wolves" align with the singular subtype keys "elf"/"goblin"/"wolf". Only the
     * common MTG plural shapes are handled (the result just needs to MATCH the subtype
     * key, which is itself the singular printed subtype).
     */
    private fun normalizeTribeWord(word: String): String = when {
        word.endsWith("ves") -> word.dropLast(3) + "f"   // elves→elf, wolves→wolf
        word.endsWith("ies") -> word.dropLast(3) + "y"   // allies→ally
        word.endsWith("zombies") -> "zombie"
        word.endsWith("es") && (word.endsWith("shes") || word.endsWith("ches") ||
            word.endsWith("xes") || word.endsWith("sses")) -> word.dropLast(2)
        word.endsWith("s") && word.length > 3 -> word.dropLast(1) // goblins→goblin
        else -> word
    }

    // A tribe word is one or more letters; the patterns capture it for normalization.
    private val REMINDER = Regex("\\([^)]*\\)")
    private val TRIBE_YOU_CONTROL = Regex("\\b([a-z]+) you control\\b")
    private val OTHER_TRIBE = Regex("\\bother ([a-z]+)\\b")
    private val TRIBE_GET = Regex("\\b([a-z]+) you control (?:gets?|have|has|gain)\\b")
    private val CHOOSE_TYPE = Regex("choose a creature type|creatures? of the chosen type|of the chosen creature type")
}
