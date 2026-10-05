package com.mmg.manahub.core.domain.collection.transfer

import kotlinx.coroutines.CancellationException

/** Recovery notices contain no cards, filenames, owners or unverified historical application claims. */
enum class LegacyImportRecoveryState { NONE, PENDING, RECOVERY_AVAILABLE, RECOVERY_FAILED }

/** A quarantined preference queue has no verifiable owner or retroactive apply markers. */
data class LegacyImportRecoveryNotice(
    val state: LegacyImportRecoveryState,
    val previousApplicationUnknown: Boolean = state != LegacyImportRecoveryState.NONE,
)

/** Preferences are removed only after a verified, durable recovery copy and unchanged source check. */
interface LegacyImportQuarantineSource {
    fun snapshot(): Map<String,String>
    fun removeIfUnchanged(snapshot: Map<String,String>): Boolean
}

/** Implementations preserve opaque content privately and never return it to the current session. */
interface LegacyImportQuarantineStorage {
    fun preserveAndVerify(snapshot: Map<String,String>): Boolean
}

/** Failed copy, failed integrity or a changed source leaves original preferences intact. */
class QuarantineLegacyImport(
    private val source: LegacyImportQuarantineSource,
    private val storage: LegacyImportQuarantineStorage,
) {
    fun run(): LegacyImportRecoveryNotice {
        val snapshot=source.snapshot()
        if(snapshot.isEmpty())return LegacyImportRecoveryNotice(LegacyImportRecoveryState.NONE)
        return try {
            if(storage.preserveAndVerify(snapshot) && source.removeIfUnchanged(snapshot))
                LegacyImportRecoveryNotice(LegacyImportRecoveryState.RECOVERY_AVAILABLE)
            else LegacyImportRecoveryNotice(LegacyImportRecoveryState.RECOVERY_FAILED)
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { LegacyImportRecoveryNotice(LegacyImportRecoveryState.RECOVERY_FAILED) }
    }
}
