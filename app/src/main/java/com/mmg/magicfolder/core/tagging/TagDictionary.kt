package com.mmg.magicfolder.core.tagging

import com.mmg.magicfolder.core.domain.model.CardTag
import com.mmg.magicfolder.core.domain.model.TagCategory
import com.mmg.magicfolder.core.util.CardTypeTranslator
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════════════════
//  Multilingual tag dictionary
//
//  Process-wide singleton, populated at class-init with a baseline set of
//  labels & strategy-detection patterns for EN / ES / DE.
//
//  User overrides are loaded at app start by [TagDictionaryRepository] and
//  merged on top via [applyOverrides]. The Settings → Tag Dictionary screen
//  edits those overrides directly.
//
//  Lookups:
//   - localize(tag)            → label in current device language
//   - findKeysByTerm("Volar")  → ["flying"]      (reverse, language-agnostic)
//   - patternsFor(key, lang)   → list of substrings the StrategyAnalyzer scans
//
//  TYPE-category tags (creature subtypes etc.) are *not* stored here; they
//  delegate to the existing [CardTypeTranslator] so we have a single source
//  of truth for type translations.
// ═══════════════════════════════════════════════════════════════════════════════

object TagDictionary {

    /** A single dictionary entry — labels by language and detection patterns by language. */
    data class Entry(
        val key:            String,
        val category:       TagCategory,
        val labels:         Map<String, String>,        // lang → label
        val patterns:       Map<String, List<String>>,  // lang → lowercased substrings
        val baseConfidence: Float = 0.85f,
    )

    /** Mutable map so [applyOverrides] can replace entries at runtime. */
    @Volatile
    private var entries: Map<String, Entry> = baseEntries.associateBy { it.key }

    // ── Public API ───────────────────────────────────────────────────────────

    fun all(): Collection<Entry> = entries.values

    fun get(key: String): Entry? = entries[key]

    /** Localize a CardTag for the current locale. Returns null if unknown. */
    fun localize(tag: CardTag, lang: String = currentLang()): String? = when (tag.category) {
        TagCategory.TYPE -> {
            // Delegate to the type translator (single source of truth).
            val word = tag.key.replaceFirstChar { it.uppercase() }
            CardTypeTranslator.translateWord(word).takeIf { it.isNotBlank() }
        }
        else -> entries[tag.key]?.labels?.get(lang)
            ?: entries[tag.key]?.labels?.get("en")
    }

    /** Reverse search: find canonical keys whose label (in any language) contains [term]. */
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

    /** Patterns to scan oracle/printed text in [lang]. */
    fun patternsFor(key: String, lang: String): List<String> =
        entries[key]?.patterns?.get(lang).orEmpty()

    /** Apply persisted user overrides on top of the base entries. */
    @Synchronized
    fun applyOverrides(overrides: List<TagOverride>) {
        if (overrides.isEmpty()) {
            entries = baseEntries.associateBy { it.key }
            return
        }
        val merged = baseEntries.associateBy { it.key }.toMutableMap()
        overrides.forEach { ov ->
            val base = merged[ov.key]
            if (base != null) {
                merged[ov.key] = base.copy(
                    labels   = base.labels   + ov.labels,
                    patterns = base.patterns + ov.patterns,
                )
            } else {
                // Net-new entry from the user.
                merged[ov.key] = Entry(
                    key            = ov.key,
                    category       = ov.category ?: TagCategory.STRATEGY,
                    labels         = ov.labels,
                    patterns       = ov.patterns,
                    baseConfidence = 0.85f,
                )
            }
        }
        entries = merged
    }

    private fun currentLang(): String = Locale.getDefault().language.lowercase()
}

/** Persisted user override applied on top of [TagDictionary.baseEntries]. */
data class TagOverride(
    val key:      String,
    val category: TagCategory? = null,
    val labels:   Map<String, String>       = emptyMap(),
    val patterns: Map<String, List<String>> = emptyMap(),
)

// ═══════════════════════════════════════════════════════════════════════════════
//  Base dictionary — keywords + strategies + roles + archetypes
//  EN / ES / DE labels and detection patterns.
// ═══════════════════════════════════════════════════════════════════════════════

private val baseEntries: List<TagDictionary.Entry> = listOf(

    // ── Evergreen Scryfall keywords (KEYWORD category, confidence = 1.0 via KeywordAnalyzer)
    kw("flying",        "Flying",        "Volar",         "Fliegend"),
    kw("first_strike",  "First strike",  "Daña primero",  "Erstschlag"),
    kw("double_strike", "Double strike", "Doble daño",    "Doppelschlag"),
    kw("trample",       "Trample",       "Arrolla",       "Verursacht Trampelschaden"),
    kw("haste",         "Haste",         "Prisa",         "Eile"),
    kw("vigilance",     "Vigilance",     "Vigilancia",    "Wachsamkeit"),
    kw("lifelink",      "Lifelink",      "Vínculo vital", "Lebensverknüpfung"),
    kw("deathtouch",    "Deathtouch",    "Toque mortal",  "Todesberührung"),
    kw("reach",         "Reach",         "Alcance",       "Reichweite"),
    kw("hexproof",      "Hexproof",      "Antimaleficio", "Fluchsicher"),
    kw("indestructible","Indestructible","Indestructible","Unzerstörbar"),
    kw("menace",        "Menace",        "Amenaza",       "Bedrohlich"),
    kw("defender",      "Defender",      "Defensor",      "Verteidiger"),
    kw("flash",         "Flash",         "Destello",      "Aufblitzen"),
    kw("prowess",       "Prowess",       "Destreza",      "Können"),
    kw("ward",          "Ward",          "Salvaguarda",   "Schutzwehr"),
    kw("cascade",       "Cascade",       "Cascada",       "Kaskade"),
    kw("convoke",       "Convoke",       "Convocar",      "Beschwören"),
    kw("landfall",      "Landfall",      "Tierras al mar","Landgang"),
    kw("cycling",       "Cycling",       "Reciclar",      "Umwandeln"),
    kw("equip",         "Equip",         "Equipar",       "Ausrüsten"),

    // ── Strategy patterns (STRATEGY / ROLE / ARCHETYPE category)
    TagDictionary.Entry(
        key      = "tokens",
        category = TagCategory.STRATEGY,
        labels   = mapOf("en" to "+Tokens", "es" to "+Fichas", "de" to "+Spielsteine"),
        patterns = mapOf(
            "en" to listOf("create ", " token"),
            "es" to listOf("crea ", " ficha"),
            "de" to listOf("erzeuge ", " spielstein"),
        ),
        baseConfidence = 0.95f,
    ),
    TagDictionary.Entry(
        key      = "ramp",
        category = TagCategory.ARCHETYPE,
        labels   = mapOf("en" to "Ramp", "es" to "Aceleración", "de" to "Ramp"),
        patterns = mapOf(
            "en" to listOf("add {", "search your library for", "basic land"),
            "es" to listOf("añade {", "busca en tu biblioteca", "tierra básica"),
            "de" to listOf("erzeuge {", "durchsuche deine bibliothek", "standardland"),
        ),
        baseConfidence = 0.90f,
    ),
    TagDictionary.Entry(
        key      = "card_draw",
        category = TagCategory.ROLE,
        labels   = mapOf("en" to "Card Draw", "es" to "Robo de cartas", "de" to "Kartenziehen"),
        patterns = mapOf(
            "en" to listOf("draw a card", "draw cards", "draw two cards", "draw three cards"),
            "es" to listOf("roba una carta", "roba cartas", "roba dos cartas", "roba tres cartas"),
            "de" to listOf("ziehe eine karte", "ziehst karten", "ziehe zwei karten"),
        ),
        baseConfidence = 0.90f,
    ),
    TagDictionary.Entry(
        key      = "removal",
        category = TagCategory.ROLE,
        labels   = mapOf("en" to "Removal", "es" to "Remoción", "de" to "Entfernung"),
        patterns = mapOf(
            "en" to listOf(
                "destroy target", "exile target creature", "exile target permanent",
                "deals damage to any target", "fight target", "to any target",
            ),
            "es" to listOf(
                "destruye la criatura objetivo", "destruye el permanente objetivo",
                "exilia la criatura objetivo", "exilia el permanente objetivo",
                "hace daño a cualquier objetivo", "luchar contra la criatura objetivo",
            ),
            "de" to listOf(
                "zerstöre die zielkreatur", "zerstöre das ziel",
                "schicke die zielkreatur ins exil", "fügt einem beliebigen ziel",
            ),
        ),
        baseConfidence = 0.90f,
    ),
    TagDictionary.Entry(
        key      = "board_wipe",
        category = TagCategory.ROLE,
        labels   = mapOf("en" to "Board Wipe", "es" to "Barrido", "de" to "Massenentfernung"),
        patterns = mapOf(
            "en" to listOf("destroy all", "exile all", "destroy each creature", "exile each creature"),
            "es" to listOf("destruye todas", "destruye todos", "exilia todas", "exilia todos", "destruye cada criatura"),
            "de" to listOf("zerstöre alle", "schicke alle", "zerstöre jede kreatur"),
        ),
        baseConfidence = 0.95f,
    ),
    TagDictionary.Entry(
        key      = "counterspell",
        category = TagCategory.ROLE,
        labels   = mapOf("en" to "Counterspell", "es" to "Contrahechizo", "de" to "Gegenzauber"),
        patterns = mapOf(
            "en" to listOf("counter target", "counter that spell"),
            "es" to listOf("contrarresta el hechizo objetivo", "contrarresta ese hechizo"),
            "de" to listOf("neutralisiere den zielzauberspruch", "neutralisiere diesen"),
        ),
        baseConfidence = 0.95f,
    ),
    TagDictionary.Entry(
        key      = "lifegain",
        category = TagCategory.STRATEGY,
        labels   = mapOf("en" to "Lifegain", "es" to "Ganancia de vidas", "de" to "Lebenspunktegewinn"),
        patterns = mapOf(
            "en" to listOf("gain ", " life"),
            "es" to listOf("ganas ", " vida"),
            "de" to listOf("erhältst ", " lebenspunkte"),
        ),
        baseConfidence = 0.80f,
    ),
    TagDictionary.Entry(
        key      = "burn",
        category = TagCategory.STRATEGY,
        labels   = mapOf("en" to "Burn", "es" to "Quema", "de" to "Direktschaden"),
        patterns = mapOf(
            "en" to listOf("deals", "damage to"),
            "es" to listOf("hace", "daño a"),
            "de" to listOf("fügt", "schaden zu"),
        ),
        baseConfidence = 0.70f,
    ),
    TagDictionary.Entry(
        key      = "graveyard",
        category = TagCategory.STRATEGY,
        labels   = mapOf("en" to "Graveyard", "es" to "Cementerio", "de" to "Friedhof"),
        patterns = mapOf(
            "en" to listOf("graveyard"),
            "es" to listOf("cementerio"),
            "de" to listOf("friedhof"),
        ),
        baseConfidence = 0.70f,
    ),
    TagDictionary.Entry(
        key      = "sacrifice",
        category = TagCategory.STRATEGY,
        labels   = mapOf("en" to "Sacrifice", "es" to "Sacrificio", "de" to "Opfern"),
        patterns = mapOf(
            "en" to listOf("sacrifice "),
            "es" to listOf("sacrifica "),
            "de" to listOf("opfere "),
        ),
        baseConfidence = 0.80f,
    ),
    TagDictionary.Entry(
        key      = "blink",
        category = TagCategory.STRATEGY,
        labels   = mapOf("en" to "Blink/ETB", "es" to "Parpadeo/ETB", "de" to "Flackern/ETB"),
        patterns = mapOf(
            "en" to listOf("exile target creature you control. return"),
            "es" to listOf("exilia la criatura objetivo que controlas. devuelve"),
            "de" to listOf("schicke eine kreatur, die du kontrollierst, ins exil. bringe"),
        ),
        baseConfidence = 0.90f,
    ),
    TagDictionary.Entry(
        key      = "tutor",
        category = TagCategory.ROLE,
        labels   = mapOf("en" to "Tutor", "es" to "Tutor", "de" to "Tutor"),
        patterns = mapOf(
            "en" to listOf("search your library for"),
            "es" to listOf("busca en tu biblioteca"),
            "de" to listOf("durchsuche deine bibliothek"),
        ),
        baseConfidence = 0.85f,
    ),
    TagDictionary.Entry(
        key      = "plus_counters",
        category = TagCategory.STRATEGY,
        labels   = mapOf("en" to "+1/+1 Counters", "es" to "Contadores +1/+1", "de" to "+1/+1-Marken"),
        patterns = mapOf(
            "en" to listOf("+1/+1 counter"),
            "es" to listOf("contador +1/+1"),
            "de" to listOf("+1/+1-marke"),
        ),
        baseConfidence = 0.95f,
    ),
    TagDictionary.Entry(
        key      = "proliferate",
        category = TagCategory.STRATEGY,
        labels   = mapOf("en" to "Proliferate", "es" to "Proliferar", "de" to "Wuchern"),
        patterns = mapOf(
            "en" to listOf("proliferate"),
            "es" to listOf("prolifera"),
            "de" to listOf("wuchern"),
        ),
        baseConfidence = 1.0f,
    ),
    TagDictionary.Entry(
        key      = "protection",
        category = TagCategory.ROLE,
        labels   = mapOf("en" to "Protection", "es" to "Protección", "de" to "Schutz"),
        patterns = mapOf(
            "en" to listOf("hexproof", "indestructible", "protection from"),
            "es" to listOf("antimaleficio", "indestructible", "protección contra"),
            "de" to listOf("fluchsicher", "unzerstörbar", "schutz vor"),
        ),
        baseConfidence = 0.85f,
    ),
    TagDictionary.Entry(
        key      = "mana_rock",
        category = TagCategory.ROLE,
        labels   = mapOf("en" to "Mana Rock", "es" to "Roca de maná", "de" to "Mana-Stein"),
        patterns = mapOf(
            "en" to listOf("{t}: add"),
            "es" to listOf("{t}: añade"),
            "de" to listOf("{t}: erzeuge"),
        ),
        baseConfidence = 0.85f,
    ),

    // ── Canonical archetype/strategy entries that exist mainly so the picker
    //    UI and translation flow can render labels for tags chosen manually.
    plain("aggro",     TagCategory.ARCHETYPE, "Aggro",      "Aggro",         "Aggro"),
    plain("control",   TagCategory.ARCHETYPE, "Control",    "Control",       "Control"),
    plain("combo",     TagCategory.ARCHETYPE, "Combo",      "Combo",         "Combo"),
    plain("midrange",  TagCategory.ARCHETYPE, "Midrange",   "Midrange",      "Midrange"),
    plain("tempo",     TagCategory.ARCHETYPE, "Tempo",      "Tempo",         "Tempo"),
    plain("tribal",    TagCategory.STRATEGY,  "Tribal",     "Tribal",        "Tribal"),
    plain("enchantress",TagCategory.STRATEGY, "Enchantress","Enchantress",   "Verzauberin"),
    plain("infinite_combo", TagCategory.STRATEGY, "Infinite Combo", "Combo infinito", "Endlos-Combo"),
    plain("stax",      TagCategory.STRATEGY,  "Stax/Prison","Stax/Prisión",  "Stax/Gefängnis"),
    plain("mana_dork", TagCategory.ROLE,      "Mana Dork",  "Criatura de maná","Mana-Kreatur"),
    plain("win_con",   TagCategory.ROLE,      "Win Condition","Condición de victoria","Siegbedingung"),
    plain("goblin",    TagCategory.TRIBAL,    "Goblin",     "Trasgo",        "Goblin"),
    plain("elf",       TagCategory.TRIBAL,    "Elf",        "Elfo",          "Elf"),
    plain("dragon",    TagCategory.TRIBAL,    "Dragon",     "Dragón",        "Drache"),
    plain("zombie",    TagCategory.TRIBAL,    "Zombie",     "Zombi",         "Zombie"),
    plain("wizard",    TagCategory.TRIBAL,    "Wizard",     "Mago",          "Zauberer"),
    plain("vampire",   TagCategory.TRIBAL,    "Vampire",    "Vampiro",       "Vampir"),
)

private fun kw(key: String, en: String, es: String, de: String) = TagDictionary.Entry(
    key            = key,
    category       = TagCategory.KEYWORD,
    labels         = mapOf("en" to en, "es" to es, "de" to de),
    patterns       = emptyMap(), // KeywordAnalyzer reads card.keywords directly.
    baseConfidence = 1.0f,
)

private fun plain(
    key: String, category: TagCategory, en: String, es: String, de: String,
) = TagDictionary.Entry(
    key            = key,
    category       = category,
    labels         = mapOf("en" to en, "es" to es, "de" to de),
    patterns       = emptyMap(),
    baseConfidence = 0.0f,
)
