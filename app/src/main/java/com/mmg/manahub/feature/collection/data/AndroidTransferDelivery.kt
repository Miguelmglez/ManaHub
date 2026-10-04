package com.mmg.manahub.feature.collection.data

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import com.mmg.manahub.core.domain.collection.transfer.*

/** File VIEW is distinct from existing auth, invitation, trade and push routing. */
object AndroidTransferDelivery {
    val mimeTypes = arrayOf("text/csv","text/comma-separated-values","application/csv","application/x-csv","text/plain","application/vnd.ms-excel","application/octet-stream")

    fun handles(intent: Intent): Boolean = intent.action in setOf(Intent.ACTION_SEND,Intent.ACTION_SEND_MULTIPLE) ||
        (intent.action==Intent.ACTION_VIEW && intent.data?.scheme=="content" && intent.type in mimeTypes)

    fun normalize(intent: Intent): TransferDeliveryResult { return try {
        val action=when(intent.action) {
            Intent.ACTION_SEND -> TransferDeliveryAction.SEND
            Intent.ACTION_SEND_MULTIPLE -> TransferDeliveryAction.MULTIPLE
            Intent.ACTION_VIEW -> TransferDeliveryAction.VIEW
            else -> return TransferDeliveryResult.Rejected(TransferError.INVALID_SOURCE)
        }
        val streams=if(intent.hasExtra(Intent.EXTRA_STREAM)) {
            if(action==TransferDeliveryAction.MULTIPLE) {
                IntentCompat.getParcelableArrayListExtra(intent,Intent.EXTRA_STREAM,Uri::class.java)?.map(Uri::toString)
                    ?: return TransferDeliveryResult.Rejected(TransferError.INVALID_SOURCE)
            } else {
                listOf((IntentCompat.getParcelableExtra(intent,Intent.EXTRA_STREAM,Uri::class.java)
                    ?: return TransferDeliveryResult.Rejected(TransferError.INVALID_SOURCE)).toString())
            }
        } else null
        val clips=intent.clipData?.let { clip -> (0 until clip.itemCount).map { index ->
            clip.getItemAt(index).uri?.toString() ?: return TransferDeliveryResult.Rejected(TransferError.INVALID_SOURCE)
        } }
        normalizeTransferDelivery(TransferDeliveryInput(action,intent.data?.toString(),streams,clips))
    } catch(_: Exception) { TransferDeliveryResult.Rejected(TransferError.INVALID_SOURCE) } }
}
