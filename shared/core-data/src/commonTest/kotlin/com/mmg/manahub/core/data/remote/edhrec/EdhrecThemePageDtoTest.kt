package com.mmg.manahub.core.data.remote.edhrec

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Coverage for [EdhrecThemePageDto]/[harvestCardSignals] (Deck Engine Unification plan §8a
 * addendum — promoted from `:tools:tag-pipeline`'s `EdhrecThemeMappingTest` verbatim, 2026-07-21,
 * the DTO+harvest half of that file, now that the DTO/function itself lives in this module).
 */
class EdhrecThemePageDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** A representative real response shape (2026-07-20/21, `pages/tags/aristocrats.json`) —
     *  trimmed to the fields this module reads, but the STRUCTURE (container.json_dict.cardlists[]
     *  .cardviews[]) matches the real live response verbatim, including the per-type breakdown
     *  lists (`creatures`, `instants`, `lands`, ...). */
    private val realAristocratsPageJson = """
        {"header":"Aristocrats","container":{"json_dict":{"cardlists":[
          {"tag":"topcommanders","header":"Top Commanders","cardviews":[
            {"id":"cd14f1ce","name":"Teysa Karlov","num_decks":4351,"potential_decks":73512}
          ]},
          {"tag":"topcards","header":"Top Cards","cardviews":[
            {"id":"a1","name":"Blood Artist","synergy":0.03,"num_decks":10000,"potential_decks":73512},
            {"id":"a2","name":"Zulaport Cutthroat","synergy":0.0,"num_decks":9000,"potential_decks":73512}
          ]},
          {"tag":"gamechangers","header":"Game Changers","cardviews":[
            {"id":"a1","name":"Blood Artist","synergy":0.05,"num_decks":10000,"potential_decks":73512}
          ]},
          {"tag":"creatures","header":"Creatures","cardviews":[
            {"id":"a1","name":"Blood Artist","synergy":0.03,"num_decks":10000,"potential_decks":73512},
            {"id":"a3","name":"Elenda, the Dusk Rose","synergy":0.26,"num_decks":8667,"potential_decks":27605}
          ]},
          {"tag":"instants","header":"Instants","cardviews":[
            {"id":"a4","name":"Village Rites","synergy":0.25,"num_decks":24538,"potential_decks":70906}
          ]}
        ]}}}
    """.trimIndent()

    @Test
    fun `decodes a real-shaped EDHREC theme page`() {
        val page = json.decodeFromString(EdhrecThemePageDto.serializer(), realAristocratsPageJson)
        assertEquals("Aristocrats", page.header)
        assertEquals(5, page.container?.jsonDict?.cardlists?.size)
    }

    @Test
    fun `harvestCardSignals reads the small top-N lists AND the per-type breakdown lists`() {
        val page = json.decodeFromString(EdhrecThemePageDto.serializer(), realAristocratsPageJson)
        val signals = harvestCardSignals(page)
        val names = signals.map { it.cardName }.toSet()
        // "Teysa Karlov" only appears in topcommanders (no synergy field, commander picks — never
        // harvested) — must stay absent.
        assertFalse("Teysa Karlov" in names)
        assertTrue("Blood Artist" in names)
        assertTrue("Zulaport Cutthroat" in names)
        assertTrue("Elenda, the Dusk Rose" in names, "creatures-only card must be harvested")
        assertTrue("Village Rites" in names, "instants-only card must be harvested")
    }

    @Test
    fun `harvestCardSignals keeps the HIGHEST weight across duplicate cardlist entries`() {
        val page = json.decodeFromString(EdhrecThemePageDto.serializer(), realAristocratsPageJson)
        val signals = harvestCardSignals(page)
        // Blood Artist appears in topcards (synergy 0.03), gamechangers (synergy 0.05), and
        // creatures (synergy 0.03) — the max across every harvested list (0.05) must win.
        val bloodArtist = signals.single { it.cardName == "Blood Artist" }
        assertEquals(0.05f, bloodArtist.weight)
    }

    @Test
    fun `harvestCardSignals falls back to inclusion rate when synergy is null or zero`() {
        // Zulaport Cutthroat's only harvested-list entry has synergy 0.0 -> falls back to
        // num_decks/potential_decks = 9000/73512.
        val page = json.decodeFromString(EdhrecThemePageDto.serializer(), realAristocratsPageJson)
        val signals = harvestCardSignals(page)
        val zulaport = signals.single { it.cardName == "Zulaport Cutthroat" }
        assertEquals(9000f / 73512f, zulaport.weight)
    }

    @Test
    fun `harvestCardSignals drops a negative-synergy entry instead of treating it as a weight`() {
        val negativeJson = """
            {"header":"Test","container":{"json_dict":{"cardlists":[
              {"tag":"lands","header":"Lands","cardviews":[
                {"id":"n1","name":"Negative Synergy Staple","synergy":-0.05,"num_decks":50000,"potential_decks":73512}
              ]}
            ]}}}
        """.trimIndent()
        val page = json.decodeFromString(EdhrecThemePageDto.serializer(), negativeJson)
        val signals = harvestCardSignals(page)
        assertFalse(signals.any { it.cardName == "Negative Synergy Staple" })
    }

    @Test
    fun `harvestCardSignals of an empty page is empty`() {
        val empty = EdhrecThemePageDto(header = "Empty", container = null)
        assertTrue(harvestCardSignals(empty).isEmpty())
    }
}
