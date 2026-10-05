package com.mmg.manahub.core.domain.collection.transfer

import kotlin.test.*

class TransferDeliveryTest {
    private val a="content://provider/a"
    private val b="content://provider/b"
    @Test fun matchingRepresentationsDeduplicateAndPreserveStreamOrder() {
        assertEquals(TransferDeliveryResult.Accepted(listOf(b,a)),normalizeTransferDelivery(TransferDeliveryInput(TransferDeliveryAction.MULTIPLE,streams=listOf(b,a,b),clips=listOf(a,b))))
    }
    @Test fun ambiguousDeliveryRejectsAllSiblings() {
        assertIs<TransferDeliveryResult.Rejected>(normalizeTransferDelivery(TransferDeliveryInput(TransferDeliveryAction.MULTIPLE,streams=listOf(a),clips=listOf(b))))
        assertIs<TransferDeliveryResult.Rejected>(normalizeTransferDelivery(TransferDeliveryInput(TransferDeliveryAction.VIEW,data=a,streams=listOf(b))))
    }
    @Test fun cardinalityIsCheckedAfterExactDeduplication() {
        assertIs<TransferDeliveryResult.Accepted>(normalizeTransferDelivery(TransferDeliveryInput(TransferDeliveryAction.MULTIPLE,streams=(1..10).map { "content://p/$it" })))
        assertEquals(TransferDeliveryResult.Rejected(TransferError.TOO_MANY_FILES),normalizeTransferDelivery(TransferDeliveryInput(TransferDeliveryAction.MULTIPLE,streams=(1..11).map { "content://p/$it" })))
        assertIs<TransferDeliveryResult.Rejected>(normalizeTransferDelivery(TransferDeliveryInput(TransferDeliveryAction.SEND,streams=listOf(a,b))))
        assertIs<TransferDeliveryResult.Accepted>(normalizeTransferDelivery(TransferDeliveryInput(TransferDeliveryAction.SEND,streams=listOf(a,a))))
    }
    @Test fun unsupportedAndMalformedSourcesNeverProduceAPrefix() {
        for(source in listOf("file:///tmp/a","https://provider/a","content:///a",""))assertIs<TransferDeliveryResult.Rejected>(normalizeTransferDelivery(TransferDeliveryInput(TransferDeliveryAction.MULTIPLE,streams=listOf(a,source))))
        assertIs<TransferDeliveryResult.Rejected>(normalizeTransferDelivery(TransferDeliveryInput(TransferDeliveryAction.VIEW)))
        assertIs<TransferDeliveryResult.Rejected>(normalizeTransferDelivery(TransferDeliveryInput(TransferDeliveryAction.SEND,streams=listOf(a),malformed=true)))
    }
}
