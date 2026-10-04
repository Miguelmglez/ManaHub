package com.mmg.manahub.core.domain.collection.transfer

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransferIntentModelsTest {
    private val id = "01234567-89ab-cdef-0123-456789abcdef"
    private val entry = TransferReviewEntry("entry", "printing", false, "NM", "en", 12345L, 0L, false, null)
    private fun request(destination: TransferDestination, scope: TransferActionScope = TransferActionScope.Entry("entry", 2L)) =
        TransferActionRequest(TransferActionId(id), TransferJobId(id), 3L, destination, scope)

    @Test fun recognitionAndLossAcceptanceNeverChooseCollection() {
        assertFalse(request(TransferDestination.COLLECTION).copy(acceptExclusions=true, acceptRepeatedFiles=true).matchesEntry(entry,3L,7L))
        assertFalse(request(TransferDestination.NONE).matchesEntry(entry,3L,7L))
    }

    @Test fun explicitDestinationAndCurrentVersionAreRequired() {
        val chosen=entry.copy(destination=TransferDestination.COLLECTION,version=2L)
        assertTrue(request(TransferDestination.COLLECTION).matchesEntry(chosen,3L,7L))
        assertFalse(request(TransferDestination.WISHLIST).matchesEntry(chosen,3L,7L))
        assertFalse(request(TransferDestination.COLLECTION).matchesEntry(chosen.copy(version=3L),3L,7L))
        assertFalse(request(TransferDestination.COLLECTION).matchesEntry(chosen,4L,7L))
    }

    @Test fun bulkRevisionAndImmutableActiveMarkersProtectPayload() {
        val chosen=entry.copy(destination=TransferDestination.WISHLIST,version=2L)
        val bulk=request(TransferDestination.WISHLIST,TransferActionScope.DestinationSelection(7L))
        assertTrue(bulk.matchesEntry(chosen,3L,7L))
        assertFalse(bulk.matchesEntry(chosen,3L,8L))
        assertFalse(bulk.matchesEntry(chosen.copy(appliedQuantity=1L),3L,7L))
        assertFalse(bulk.matchesEntry(chosen.copy(activeActionId=TransferActionId(id)),3L,7L))
        assertFalse(bulk.matchesEntry(chosen.copy(quantity=Int.MAX_VALUE.toLong()+1L),3L,7L))
    }
}
