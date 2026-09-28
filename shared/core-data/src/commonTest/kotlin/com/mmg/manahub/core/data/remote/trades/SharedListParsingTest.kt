package com.mmg.manahub.core.data.remote.trades

import com.mmg.manahub.core.model.SharedListItem
import com.mmg.manahub.core.model.SharedListResult
import com.mmg.manahub.core.model.SharedListType
import com.mmg.manahub.core.model.isValidShareId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SharedListParsingTest {

    private fun parse(json: String) = parseSharedListResult(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun parsesAWishlistPayloadIntoTypedItems() {
        val result = parse(
            """{"status":"ok","list_type":"WISHLIST","user_id":"u1","owner_nickname":"Ana",
               "items":[{"card_id":"c1","match_any_variant":false,"is_foil":true,"condition":"NM","language":"en"},
                        {"card_id":"c2","match_any_variant":true,"is_foil":null,"condition":null,"language":null}]}""",
        )

        val ok = assertIs<SharedListResult.Ok>(result)
        assertEquals(SharedListType.WISHLIST, ok.listType)
        assertEquals("Ana", ok.ownerNickname)
        assertEquals(
            listOf(
                SharedListItem(cardId = "c1", isFoil = true, condition = "NM", language = "en"),
                SharedListItem(cardId = "c2", matchAnyVariant = true),
            ),
            ok.items,
        )
    }

    @Test
    fun parsesOpenForTradeQuantitiesAndSkipsRowsWithoutACard() {
        val result = parse(
            """{"status":"ok","list_type":"OPEN_FOR_TRADE","user_id":"u1","owner_nickname":"",
               "items":[{"user_card_id":"r1","card_id":"c1","is_foil":false,"quantity":3},{"user_card_id":"r2"}]}""",
        )

        val ok = assertIs<SharedListResult.Ok>(result)
        assertEquals(listOf(SharedListItem(cardId = "c1", quantity = 3, isFoil = false, userCardId = "r1")), ok.items)
    }

    @Test
    fun statusAndUnknownListTypesMapToTheTerminalStates() {
        assertEquals(SharedListResult.Private, parse("""{"status":"private"}"""))
        assertEquals(SharedListResult.NotFound, parse("""{"status":"not_found"}"""))
        assertEquals(SharedListResult.NotFound, parse("""{"status":"ok","list_type":"DECK","items":[]}"""))
    }

    @Test
    fun onlyUuidsAreValidShareIds() {
        assertTrue(isValidShareId("0f8fad5b-d9cb-469f-a165-70867728950e"))
        assertFalse(isValidShareId("abc"))
        assertFalse(isValidShareId("0f8fad5b-d9cb-469f-a165-70867728950e'--"))
    }
}
