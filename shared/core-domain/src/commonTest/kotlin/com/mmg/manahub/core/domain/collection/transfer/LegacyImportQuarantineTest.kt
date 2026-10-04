package com.mmg.manahub.core.domain.collection.transfer

import kotlinx.coroutines.CancellationException
import kotlin.test.*

class LegacyImportQuarantineTest {
    private class Source(var values: Map<String,String>): LegacyImportQuarantineSource {
        var removed=false
        override fun snapshot()=values
        override fun removeIfUnchanged(snapshot: Map<String,String>): Boolean {
            if(values!=snapshot)return false
            removed=true; values=emptyMap(); return true
        }
    }
    @Test fun verifiedCopyPrecedesRemovalAndDoesNotClaimApplicationHistory() {
        val source=Source(mapOf("queue" to "private-content")); var copy=emptyMap<String,String>()
        val result=QuarantineLegacyImport(source,object : LegacyImportQuarantineStorage {
            override fun preserveAndVerify(snapshot: Map<String,String>): Boolean { assertFalse(source.removed); copy=snapshot; return true }
        }).run()
        assertEquals(mapOf("queue" to "private-content"),copy); assertTrue(source.removed)
        assertEquals(LegacyImportRecoveryState.RECOVERY_AVAILABLE,result.state); assertTrue(result.previousApplicationUnknown); assertFalse(result.toString().contains("private-content"))
    }
    @Test fun failedCopyOrIntegrityNeverRemovesOriginal() {
        for(throwFailure in listOf(false,true)) {
            val source=Source(mapOf("queue" to "private"))
            val result=QuarantineLegacyImport(source,object : LegacyImportQuarantineStorage {
                override fun preserveAndVerify(snapshot: Map<String,String>): Boolean { if(throwFailure)error("Storage unavailable"); return false }
            }).run()
            assertEquals(LegacyImportRecoveryState.RECOVERY_FAILED,result.state); assertFalse(source.removed); assertEquals("private",source.values["queue"])
        }
    }
    @Test fun changedSourceIsNeverClearedAndEmptySourceCreatesNoRecovery() {
        val source=Source(mapOf("queue" to "old")); var calls=0
        val storage=object : LegacyImportQuarantineStorage { override fun preserveAndVerify(snapshot: Map<String,String>): Boolean { calls++; source.values=mapOf("queue" to "new"); return true } }
        assertEquals(LegacyImportRecoveryState.RECOVERY_FAILED,QuarantineLegacyImport(source,storage).run().state); assertEquals("new",source.values["queue"]); assertFalse(source.removed)
        source.values=emptyMap(); assertEquals(LegacyImportRecoveryState.NONE,QuarantineLegacyImport(source,storage).run().state); assertEquals(1,calls)
    }
    @Test fun cancellationPropagatesWithoutRemovingSource() {
        val source=Source(mapOf("queue" to "private"))
        assertFailsWith<CancellationException> { QuarantineLegacyImport(source,object : LegacyImportQuarantineStorage { override fun preserveAndVerify(snapshot: Map<String,String>): Boolean=throw CancellationException("Cancelled") }).run() }
        assertFalse(source.removed)
    }
}
