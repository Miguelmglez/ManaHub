package com.mmg.manahub.feature.collection

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.feature.collection.data.AndroidTransferDelivery
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidTransferDeliveryTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val a=Uri.parse("content://transfer-fixture/a")
    private val b=Uri.parse("content://transfer-fixture/b")
    private fun clips(vararg uris: Uri)=ClipData.newRawUri("Fixture",uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
    @Test fun typedStreamAndClipDataAgreeAfterDeduplicationWithoutUsingText() {
        val intent=Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM,arrayListOf(a,b,a)).putExtra(Intent.EXTRA_TEXT,"999 Unrelated card").apply { clipData=clips(b,a) }
        assertEquals(TransferDeliveryResult.Accepted(listOf(a.toString(),b.toString())),AndroidTransferDelivery.normalize(intent))
        intent.clipData=clips(a)
        assertTrue(AndroidTransferDelivery.normalize(intent) is TransferDeliveryResult.Rejected)
    }
    @Test fun malformedStreamsAndMixedSchemesRejectWholeDelivery() {
        assertTrue(AndroidTransferDelivery.normalize(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM,"not parcelable")) is TransferDeliveryResult.Rejected)
        assertTrue(AndroidTransferDelivery.normalize(Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM,arrayListOf(a,Uri.parse("file:///tmp/cards.csv")))) is TransferDeliveryResult.Rejected)
        assertTrue(AndroidTransferDelivery.normalize(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT,"1 Fixture")) is TransferDeliveryResult.Rejected)
    }
    @Test fun viewRequiresSingleDataAndMatchingOptionalStreams() {
        val intent=Intent(Intent.ACTION_VIEW).setDataAndType(a,"text/csv").apply { clipData=clips(a) }
        assertTrue(AndroidTransferDelivery.handles(intent))
        assertEquals(TransferDeliveryResult.Accepted(listOf(a.toString())),AndroidTransferDelivery.normalize(intent))
        intent.clipData=clips(a,b)
        assertTrue(AndroidTransferDelivery.normalize(intent) is TransferDeliveryResult.Rejected)
        assertFalse(AndroidTransferDelivery.handles(Intent(Intent.ACTION_VIEW,Uri.parse("manahub://auth"))))
        assertFalse(AndroidTransferDelivery.handles(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("https://fixture/cards.csv"),"text/csv")))
    }
    @Test fun multipleLimitIsValidatedBeforeAnyProviderAccess() {
        val ten=(1..10).map { Uri.parse("content://transfer-fixture/$it") }
        fun intent(uris: List<Uri>)=Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM,ArrayList(uris))
        assertTrue(AndroidTransferDelivery.normalize(intent(ten+ten.first())) is TransferDeliveryResult.Accepted)
        assertTrue(AndroidTransferDelivery.normalize(intent(ten+Uri.parse("content://transfer-fixture/11"))) is TransferDeliveryResult.Rejected)
        assertTrue(AndroidTransferDelivery.normalize(Intent(Intent.ACTION_SEND).apply { clipData=clips(a,b) }) is TransferDeliveryResult.Rejected)
    }
    @Test fun manifestResolvesExactMimesWithoutFileOrHttpViewFilters() {
        fun resolves(intent: Intent)=context.packageManager.queryIntentActivities(intent.setPackage(context.packageName),PackageManager.MATCH_DEFAULT_ONLY).any { it.activityInfo.name=="com.mmg.manahub.app.MainActivity" }
        for(mime in AndroidTransferDelivery.mimeTypes) {
            assertTrue(resolves(Intent(Intent.ACTION_SEND).setType(mime)))
            assertTrue(resolves(Intent(Intent.ACTION_SEND_MULTIPLE).setType(mime)))
            assertTrue(resolves(Intent(Intent.ACTION_VIEW).setDataAndType(a,mime)))
        }
        assertFalse(resolves(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file:///tmp/fixture.csv"),"text/csv")))
        assertFalse(resolves(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("https://fixture/fixture.csv"),"text/csv")))
        assertFalse(resolves(Intent(Intent.ACTION_SEND).setType("application/pdf")))
    }
}
