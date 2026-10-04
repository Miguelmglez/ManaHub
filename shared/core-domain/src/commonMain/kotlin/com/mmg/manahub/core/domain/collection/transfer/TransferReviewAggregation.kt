package com.mmg.manahub.core.domain.collection.transfer

/** A source key remains independent of later user edits to its printing or attributes. */
fun transferSourceKey(printing: String, foil: Boolean, condition: String, language: String): String =
    listOf(printing, if (foil) "1" else "0", condition, language).joinToString(":") { value ->
        value.encodeToByteArray().joinToString("") { byte -> (byte.toInt() and 255).toString(16).padStart(2, '0').uppercase() }
    }

/** Sorted content hashes preserve multiplicity without depending on source names or order. */
fun transferFingerprintInput(hashes: List<String>): String {
    require(hashes.size in 1..TransferLimits.MAX_FILES && hashes.all { it.matches(Regex("[0-9a-f]{64}")) })
    return hashes.sorted().joinToString("\n")
}

/** Checked positive accounting never wraps or silently clamps accepted copies. */
fun checkedTransferCopies(left: Long, right: Long): Long {
    require(left >= 0L && right >= 0L && left <= Long.MAX_VALUE - right)
    return left + right
}

/** Membership changes require a deliberate decision before reusing a manually edited payload. */
enum class TransferReviewDecision { KEEP_EDIT, USE_SOURCE, DISMISS_REMOVED }
