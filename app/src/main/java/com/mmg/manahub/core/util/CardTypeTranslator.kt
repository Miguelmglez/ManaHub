package com.mmg.manahub.core.util

object CardTypeTranslator {

    // Supertypes
    private val supertypes = mapOf(
        "Basic"      to mapOf("es" to "Básica",      "de" to "Basis"),
        "Legendary"  to mapOf("es" to "Legendaria",  "de" to "Legendär"),
        "Snow"       to mapOf("es" to "Nevada",      "de" to "Schnee"),
        "World"      to mapOf("es" to "Mundial",     "de" to "Welt"),
        "Ongoing"    to mapOf("es" to "Continuo",    "de" to "Laufend")
    )

    // Main types
    private val mainTypes = mapOf(
        "Creature"     to mapOf("es" to "Criatura",     "de" to "Kreatur"),
        "Instant"      to mapOf("es" to "Instantáneo",  "de" to "Spontanzauber"),
        "Sorcery"      to mapOf("es" to "Conjuro",      "de" to "Hexerei"),
        "Enchantment"  to mapOf("es" to "Encantamiento","de" to "Verzauberung"),
        "Artifact"     to mapOf("es" to "Artefacto",    "de" to "Artefakt"),
        "Planeswalker" to mapOf("es" to "Planeswalker", "de" to "Planeswalker"),
        "Land"         to mapOf("es" to "Tierra",       "de" to "Land"),
        "Battle"       to mapOf("es" to "Batalla",      "de" to "Schlacht"),
        "Tribal"       to mapOf("es" to "Tribal",       "de" to "Tribal")
    )

    // Subtypes de criatura más comunes
    private val creatureSubtypes = mapOf(
        "Human"       to mapOf("es" to "Humano",        "de" to "Mensch"),
        "Wizard"      to mapOf("es" to "Mago",          "de" to "Zauberer"),
        "Warrior"     to mapOf("es" to "Guerrero",      "de" to "Krieger"),
        "Knight"      to mapOf("es" to "Caballero",     "de" to "Ritter"),
        "Soldier"     to mapOf("es" to "Soldado",       "de" to "Soldat"),
        "Elf"         to mapOf("es" to "Elfo",          "de" to "Elf"),
        "Goblin"      to mapOf("es" to "Trasgo",        "de" to "Goblin"),
        "Zombie"      to mapOf("es" to "Zombi",         "de" to "Zombie"),
        "Vampire"     to mapOf("es" to "Vampiro",       "de" to "Vampir"),
        "Dragon"      to mapOf("es" to "Dragón",        "de" to "Drache"),
        "Angel"       to mapOf("es" to "Ángel",         "de" to "Engel"),
        "Demon"       to mapOf("es" to "Demonio",       "de" to "Dämon"),
        "Spirit"      to mapOf("es" to "Espíritu",      "de" to "Geist"),
        "Beast"       to mapOf("es" to "Bestia",        "de" to "Bestie"),
        "Bird"        to mapOf("es" to "Pájaro",        "de" to "Vogel"),
        "Merfolk"     to mapOf("es" to "Tritón",        "de" to "Meerfolk"),
        "Faerie"      to mapOf("es" to "Hada",          "de" to "Fee"),
        "Giant"       to mapOf("es" to "Gigante",       "de" to "Riese"),
        "Elemental"   to mapOf("es" to "Elemental",     "de" to "Elemental"),
        "Horror"      to mapOf("es" to "Horror",        "de" to "Schrecken"),
        "Illusion"    to mapOf("es" to "Ilusión",       "de" to "Illusion"),
        "Rogue"       to mapOf("es" to "Pícaro",        "de" to "Schurke"),
        "Shaman"      to mapOf("es" to "Chamán",        "de" to "Schamane"),
        "Cleric"      to mapOf("es" to "Clérigo",       "de" to "Kleriker"),
        "Druid"       to mapOf("es" to "Druida",        "de" to "Druide"),
        "Archer"      to mapOf("es" to "Arquero",       "de" to "Bogenschütze"),
        "Scout"       to mapOf("es" to "Explorador",    "de" to "Kundschafter"),
        "Monk"        to mapOf("es" to "Monje",         "de" to "Mönch"),
        "Advisor"     to mapOf("es" to "Consejero",     "de" to "Berater"),
        "Assassin"    to mapOf("es" to "Asesino",       "de" to "Attentäter"),
        "Artificer"   to mapOf("es" to "Artificiero",   "de" to "Artifex"),
        "Berserker"   to mapOf("es" to "Berserker",     "de" to "Berserker"),
        "Pirate"      to mapOf("es" to "Pirata",        "de" to "Pirat"),
        "Dinosaur"    to mapOf("es" to "Dinosaurio",    "de" to "Dinosaurier"),
        "Hydra"       to mapOf("es" to "Hidra",         "de" to "Hydra"),
        "Phoenix"     to mapOf("es" to "Fénix",         "de" to "Phönix"),
        "Sphinx"      to mapOf("es" to "Esfinge",       "de" to "Sphinx"),
        "Wurm"        to mapOf("es" to "Sierpe",        "de" to "Wurm"),
        "Sliver"      to mapOf("es" to "Fragmento",     "de" to "Splitter"),
        "Treefolk"    to mapOf("es" to "Arborícola",    "de" to "Baumvolk"),
        "Dwarf"       to mapOf("es" to "Enano",         "de" to "Zwerg"),
        "Gnome"       to mapOf("es" to "Gnomo",         "de" to "Gnom"),
        "Cat"         to mapOf("es" to "Felino",        "de" to "Katze"),
        "Wolf"        to mapOf("es" to "Lobo",          "de" to "Wolf"),
        "Snake"       to mapOf("es" to "Serpiente",     "de" to "Schlange"),
        "Insect"      to mapOf("es" to "Insecto",       "de" to "Insekt"),
        "Skeleton"    to mapOf("es" to "Esqueleto",     "de" to "Skelett"),
        "Construct"   to mapOf("es" to "Constructo",    "de" to "Konstrukt"),
        "Golem"       to mapOf("es" to "Gólem",         "de" to "Golem"),
        "Wall"        to mapOf("es" to "Muro",          "de" to "Mauer")
    )

    // Tipos de tierra
    private val landSubtypes = mapOf(
        "Forest"    to mapOf("es" to "Bosque",   "de" to "Wald"),
        "Island"    to mapOf("es" to "Isla",     "de" to "Insel"),
        "Mountain"  to mapOf("es" to "Montaña",  "de" to "Gebirge"),
        "Plains"    to mapOf("es" to "Llanura",  "de" to "Ebene"),
        "Swamp"     to mapOf("es" to "Pantano",  "de" to "Sumpf")
    )

    // Tipos de artefacto
    private val artifactSubtypes = mapOf(
        "Equipment"  to mapOf("es" to "Equipo",    "de" to "Ausrüstung"),
        "Vehicle"    to mapOf("es" to "Vehículo",  "de" to "Fahrzeug"),
        "Food"       to mapOf("es" to "Alimento",  "de" to "Nahrung"),
        "Treasure"   to mapOf("es" to "Tesoro",    "de" to "Schatz"),
        "Clue"       to mapOf("es" to "Pista",     "de" to "Hinweis")
    )

    // Tipos complejos o combinados
    private val complexTypes = mapOf(
        "Basic Land" to mapOf("es" to "Tierra Básica", "de" to "Standardland")
    )

    // Diccionario combinado de todos los tipos
    private val allTypes: Map<String, Map<String, String>> by lazy {
        supertypes + mainTypes + creatureSubtypes + landSubtypes + artifactSubtypes + complexTypes
    }

    // Traduce una palabra de la type_line al idioma del dispositivo
    fun translateWord(word: String): String {
        val sanitized = word.replace('_', ' ')
        val lang = java.util.Locale.getDefault().language.lowercase()
        if (lang == "en") return sanitized

        val capitalized = sanitized.split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        return allTypes[capitalized]?.get(lang) ?: sanitized
    }

    // Traduce la type_line completa
    // Input:  "Legendary Creature — Elf Wizard"
    // Output: "Criatura Legendaria — Elfo Mago" (en español)
    fun translateTypeLine(typeLine: String, lang: String = currentLang()): String {
        if (lang == "en") return typeLine

        val parts = typeLine.split(" — ", " - ")
        return if (parts.size >= 2) {
            val mainPart = parts[0].split(" ")
                .joinToString(" ") { translateWord(it) }
            val subPart = parts[1].split(" ")
                .joinToString(" ") { translateWord(it) }
            "$mainPart — $subPart"
        } else {
            typeLine.split(" ")
                .joinToString(" ") { translateWord(it) }
        }
    }

    // Traduce solo el tipo principal (sin subtipos) para chips y filtros
    fun translateMainType(type: String, lang: String = currentLang()): String {
        if (lang == "en") return type
        return mainTypes[type]?.get(lang) ?: type
    }

    private fun currentLang(): String = java.util.Locale.getDefault().language.lowercase()
}
