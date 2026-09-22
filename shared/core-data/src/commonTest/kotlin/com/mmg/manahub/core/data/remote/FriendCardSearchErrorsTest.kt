package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.model.ColorMatchMode
import com.mmg.manahub.core.model.FriendCardCursor
import com.mmg.manahub.core.model.FriendCardSearchException
import com.mmg.manahub.core.model.FriendCardSearchParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FriendCardSearchErrorsTest {

    @Test
    fun `access denied is read from the message, not the sqlstate`() {
        val e = FriendCardSearchErrors.fromResponse(400, """{"code":"P0005","message":"ACCESS_DENIED","details":null}""")
        assertIs<FriendCardSearchException.AccessDenied>(e)
    }

    @Test
    fun `invalid argument keeps the detail token`() {
        val e = FriendCardSearchErrors.fromResponse(400, """{"code":"22023","message":"INVALID_ARGUMENT","details":"cursor"}""")
        assertIs<FriendCardSearchException.InvalidArgument>(e)
        assertEquals("cursor", e.detail)
    }

    @Test
    fun `known token in a non-json body is still recognized`() {
        assertIs<FriendCardSearchException.SessionExpired>(FriendCardSearchErrors.fromResponse(403, "error NOT_AUTHENTICATED"))
    }

    @Test
    fun `unknown message maps to rejected with the http status`() {
        val e = FriendCardSearchErrors.fromResponse(500, """{"message":"statement timeout"}""")
        assertIs<FriendCardSearchException.Rejected>(e)
        assertEquals("HTTP_500", e.reason)
    }

    @Test
    fun `self lookup is a rejection`() {
        val e = FriendCardSearchErrors.fromResponse(400, """{"message":"SELF_LOOKUP"}""")
        assertIs<FriendCardSearchException.Rejected>(e)
        assertEquals("SELF_LOOKUP", e.reason)
    }

    @Test
    fun `request dto carries modes, cursor and clamps the page size`() {
        val dto = FriendCardSearchParams(colors = listOf("W"), colorsMode = ColorMatchMode.ANY_OF)
            .toRequestDto("friend", "wishlist", FriendCardCursor("0bolt", "row-1"), limit = 500)
        assertEquals("any_of", dto.pColorsMode)
        assertEquals("at_most", dto.pIdentityMode)
        assertEquals(100, dto.pLimit)
        assertEquals("0bolt", dto.pAfterSortKey)
        assertEquals("row-1", dto.pAfterRowId)
        assertNull(dto.pName)
    }

    @Test
    fun `http 401 with an unknown body is a session expiry`() {
        assertIs<FriendCardSearchException.SessionExpired>(FriendCardSearchErrors.fromResponse(401, """{"message":"whatever"}"""))
    }

    @Test
    fun `expired jwt body is a session expiry`() {
        val body = """{"code":"PGRST301","message":"JWT expired","details":null}"""
        assertIs<FriendCardSearchException.SessionExpired>(FriendCardSearchErrors.fromResponse(400, body))
    }

    @Test
    fun `profile incomplete maps to its own type`() {
        val body = """{"code":"42501","message":"PROFILE_INCOMPLETE"}"""
        assertIs<FriendCardSearchException.ProfileIncomplete>(FriendCardSearchErrors.fromResponse(403, body))
    }

    @Test
    fun `non-primitive message or details never throw`() {
        val e = FriendCardSearchErrors.fromResponse(500, """{"message":{"nested":1},"details":[1,2]}""")
        assertIs<FriendCardSearchException.Rejected>(e)
        assertEquals("HTTP_500", e.reason)
        assertIs<FriendCardSearchException.Rejected>(FriendCardSearchErrors.fromResponse(502, "[1,2"))
        assertIs<FriendCardSearchException.Rejected>(FriendCardSearchErrors.fromResponse(500, ""))
    }
}
