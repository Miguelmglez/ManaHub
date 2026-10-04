package com.mmg.manahub.core.domain.collection.transfer

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TransferSessionGateTest {
    @Test fun unavailableAndAnotherOwnerNeverEnterOwnedWork()=runTest {
        val gate=TransferSessionGate(); val owner=TransferOwner.Account("a")
        assertNull(gate.withOwner(owner) { _,_ -> error("Entered loading work") })
        gate.changeOwner(TransferOwner.Account("b"))
        assertNull(gate.withOwner(owner) { _,_ -> error("Entered foreign work") })
        gate.changeOwner(TransferOwner.VerifiedGuest("installation"))
        assertNotNull(gate.withOwner(TransferOwner.VerifiedGuest("installation")) { _,ensure -> ensure(); true })
    }
    @Test fun transitionInvalidatesRunningResultBeforeWaitingForItsLock()=runTest {
        val gate=TransferSessionGate(); val a=TransferOwner.Account("a"); val b=TransferOwner.Account("b")
        gate.changeOwner(a)
        val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>()
        val work=async { gate.withOwner(a) { _,ensure -> entered.complete(Unit); release.await(); ensure(); "obsolete" } }
        entered.await()
        val transition=async { gate.changeOwner(b) }
        gate.sessions.first { (it as? TransferSession.Available)?.owner==b }
        assertFalse(transition.isCompleted)
        release.complete(Unit); assertNull(work.await()); transition.await()
        val previous=gate.currentSession; gate.changeOwner(a)
        assertNotEquals(previous,gate.currentSession)
    }
    @Test fun quantityMathNeverSaturatesAndDeletedRowsRestart() {
        assertEquals(2147483647,transferCollectionQuantity(2147483646,false,1L))
        assertFailsWith<TransferQuantityOverflowException> { transferCollectionQuantity(Int.MAX_VALUE,false,1L) }
        assertFailsWith<TransferQuantityOverflowException> { transferCollectionQuantity(-1,false,1L) }
        assertFailsWith<TransferQuantityOverflowException> { transferCollectionQuantity(null,false,2147483648L) }
        assertEquals(3,transferCollectionQuantity(Int.MAX_VALUE,true,3L))
    }
}
