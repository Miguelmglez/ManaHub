package com.mmg.manahub.core.domain.collection.transfer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DurableCollectionTransferContractsTest {
    private val uuid = "01234567-89ab-cdef-0123-456789abcdef"
    private val owner = TransferOwner.Account("fixture-account")
    private val file = TransferFileSummary(
        TransferFileId(uuid), 0, CollectionFileFormat.TEXT, TransferPhase.REVIEW_READY,
        true, false, false, 100L, 10L, 0L, 10L, 0L, 10L, 0L, null,
    )
    private val summary = TransferSummary(
        TransferJobId(uuid), owner, TransferPhase.REVIEW_READY, 1L, 2L,
        listOf(file), 10L, 0L, 10L, 0L, 1L, null,
    )
    private val confirmation = TransferConfirmation(1L, 2L, false, false)

    @Test
    fun onlyCanonicalUuidIdentitiesCanBecomeFilePathsOrWorkerData() {
        assertEquals(uuid, TransferJobId(uuid).value)
        assertFailsWith<IllegalArgumentException> { TransferJobId("../source") }
        assertFailsWith<IllegalArgumentException> { TransferFileId(uuid.uppercase()) }
        assertFailsWith<IllegalArgumentException> { TransferFileId("") }
    }

    @Test
    fun accountGuestAndLoadingAreDistinctAndAnAbsentOwnerCannotBeConstructed() {
        assertNotEquals<TransferOwner>(owner, TransferOwner.VerifiedGuest("fixture-account"))
        assertFailsWith<IllegalArgumentException> { TransferOwner.Account(" ") }
        assertFailsWith<IllegalArgumentException> { TransferOwner.VerifiedGuest("") }
        assertTrue(listOf<TransferSession>(TransferSession.Loading).filterIsInstance<TransferSession.Available>().isEmpty())
    }

    @Test
    fun selectionAndPayloadChangesInvalidateOldConfirmation() {
        assertTrue(confirmation.matches(summary))
        assertFalse(confirmation.matches(summary.copy(generation = 2L)))
        assertFalse(confirmation.matches(summary.copy(payloadVersion = 3L)))
        assertFalse(confirmation.matches(summary.copy(phase = TransferPhase.APPLYING)))
        assertTrue(confirmation.matches(summary.copy(phase = TransferPhase.REVIEW_REQUIRED)))
    }

    @Test
    fun pendingFailedOrEmptySelectedFilesCannotBeConfirmed() {
        assertFalse(confirmation.matches(summary.copy(files = emptyList())))
        assertFalse(confirmation.copy(acceptExclusions = true).matches(summary.copy(files = listOf(file.copy(selected = false)))))
        assertFalse(confirmation.matches(summary.copy(files = listOf(file.copy(phase = TransferPhase.PARSING)))))
        assertFalse(confirmation.copy(acceptExclusions = true).matches(summary.copy(files = listOf(file.copy(phase = TransferPhase.REJECTED)))))
    }

    @Test
    fun recordBudgetsAreInclusiveAndOverflowCannotBypassConfirmation() {
        assertTrue(confirmation.matches(summary.copy(files = listOf(file.copy(dataRecords = 100000L)))))
        assertFalse(confirmation.matches(summary.copy(files = listOf(file.copy(dataRecords = 100001L)))))
        assertFalse(confirmation.matches(summary.copy(files = listOf(file.copy(preambleRecords = 100001L)))))
        assertFalse(confirmation.matches(summary.copy(files = listOf(file.copy(dataRecords = Long.MAX_VALUE), file.copy(dataRecords = Long.MAX_VALUE)))))
        assertFalse(confirmation.matches(summary.copy(files = listOf(file.copy(dataRecords = -1L)))))
        assertFalse(confirmation.matches(summary.copy(files = List(11) { file })))
    }

    @Test
    fun duplicateAndPriorPartialApplicationRequireSeparateConsent() {
        for (flagged in listOf(file.copy(duplicate = true), file.copy(previouslyParticipated = true))) {
            val repeated = summary.copy(files = listOf(flagged))
            assertFalse(confirmation.matches(repeated))
            assertFalse(confirmation.copy(acceptExclusions = true).matches(repeated))
            assertTrue(confirmation.copy(acceptRepeatedFiles = true).matches(repeated))
        }
    }

    @Test
    fun excludedFilesAndRejectedOrUnresolvedRowsRequireExplicitLossAcceptance() {
        assertFalse(confirmation.matches(summary.copy(excludedEntries = 1L)))
        assertTrue(confirmation.copy(acceptExclusions = true).matches(summary.copy(excludedEntries = 1L)))
        for (files in listOf(listOf(file.copy(invalidRecords = 1L)), listOf(file.copy(unresolvedRecords = 1L)), listOf(file, file.copy(id = TransferFileId("11234567-89ab-cdef-0123-456789abcdef"), selected = false)))) {
            val losses = summary.copy(files = files)
            assertFalse(confirmation.matches(losses))
            assertTrue(confirmation.copy(acceptExclusions = true).matches(losses))
        }
    }
}
