package com.mmg.manahub.core.domain.collection.transfer

/** External delivery validation precedes any provider access or receipt creation. */
enum class TransferDeliveryAction { VIEW, SEND, MULTIPLE }

/** URI strings stay transient and never become navigation arguments or persisted source paths. */
data class TransferDeliveryInput(
    val action: TransferDeliveryAction,
    val data: String? = null,
    val streams: List<String>? = null,
    val clips: List<String>? = null,
    val malformed: Boolean = false,
)

sealed interface TransferDeliveryResult {
    data class Accepted(val sources: List<String>) : TransferDeliveryResult
    data class Rejected(val error: TransferError) : TransferDeliveryResult
}

/** Contradictory representations reject the entire delivery, including otherwise valid siblings. */
fun normalizeTransferDelivery(input: TransferDeliveryInput): TransferDeliveryResult {
    fun reject(error: TransferError = TransferError.INVALID_SOURCE) = TransferDeliveryResult.Rejected(error)
    if(input.malformed)return reject()
    val streams=input.streams?.distinct()
    val clips=input.clips?.distinct()
    if(streams!=null && clips!=null && streams.toSet()!=clips.toSet())return reject()
    val sources=when(input.action) {
        TransferDeliveryAction.VIEW -> {
            val data=input.data ?: return reject()
            if(streams!=null && streams!=listOf(data))return reject()
            if(clips!=null && clips!=listOf(data))return reject()
            listOf(data)
        }
        TransferDeliveryAction.SEND,TransferDeliveryAction.MULTIPLE -> streams ?: clips ?: return reject()
    }
    if(sources.size>TransferLimits.MAX_FILES)return reject(TransferError.TOO_MANY_FILES)
    if(sources.isEmpty() || (input.action!=TransferDeliveryAction.MULTIPLE && sources.size!=1))return reject()
    if(sources.any { !it.startsWith("content://") || it.substringAfter("content://").substringBefore('/').isBlank() })return reject()
    return TransferDeliveryResult.Accepted(sources)
}
