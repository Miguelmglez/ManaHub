package com.mmg.manahub.core.domain.collection.transfer

import kotlin.test.*

class TransferReviewAggregationTest {
    @Test fun keysSeparateAttributesAndRetainUnicodeIdentity() {
        assertEquals("70:30:4E4D:656E",transferSourceKey("p",false,"NM","en"))
        assertNotEquals(transferSourceKey("p",false,"NM","en"),transferSourceKey("p",true,"NM","en"))
        assertNotEquals(transferSourceKey("a:b",false,"NM","en"),transferSourceKey("a",false,"b:NM","en"))
        assertTrue(transferSourceKey("é",false,"NM","en").startsWith("C3A9:"))
    }
    @Test fun fingerprintsIgnoreOrderButPreserveMultiplicity() {
        val a="a".repeat(64); val b="b".repeat(64)
        assertEquals(transferFingerprintInput(listOf(a,b)),transferFingerprintInput(listOf(b,a)))
        assertNotEquals(transferFingerprintInput(listOf(a,b)),transferFingerprintInput(listOf(a,a,b)))
        assertFailsWith<IllegalArgumentException> { transferFingerprintInput(emptyList()) }
    }
    @Test fun copyAdditionAcceptsExactLongBoundaryAndRejectsOverflow() {
        assertEquals(Long.MAX_VALUE,checkedTransferCopies(Long.MAX_VALUE-1L,1L))
        assertEquals(2147483648L,checkedTransferCopies(Int.MAX_VALUE.toLong(),1L))
        assertFailsWith<IllegalArgumentException> { checkedTransferCopies(Long.MAX_VALUE,1L) }
        assertFailsWith<IllegalArgumentException> { checkedTransferCopies(0L,-1L) }
    }
}
