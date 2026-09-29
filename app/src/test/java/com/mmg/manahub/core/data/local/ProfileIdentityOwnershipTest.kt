package com.mmg.manahub.core.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileIdentityOwnershipTest {
    @Test
    fun `legacy ownerless identity is removed before account B loads offline`() {
        assertTrue(shouldClearProfileIdentity(owner = null, incomingUserId = "user-b", verifiedGuest = false))
    }

    @Test
    fun `verified guest identity survives first account claim`() {
        assertFalse(shouldClearProfileIdentity(owner = null, incomingUserId = "user-b", verifiedGuest = true))
    }

    @Test
    fun `another account identity is removed and same account identity is retained`() {
        assertTrue(shouldClearProfileIdentity(owner = "user-a", incomingUserId = "user-b", verifiedGuest = false))
        assertFalse(shouldClearProfileIdentity(owner = "user-b", incomingUserId = "user-b", verifiedGuest = false))
    }
}
