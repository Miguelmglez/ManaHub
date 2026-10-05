package com.mmg.manahub.feature.collection

import android.content.Intent
import android.os.Process
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.mmg.manahub.app.MainActivity
import com.mmg.manahub.app.ManaHubApp
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.feature.collection.data.AndroidTransferWorkScheduler
import com.mmg.manahub.feature.collection.data.storageKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.TimeUnit

/** Three externally coordinated phases distinguish task restoration from durable reboot review. */
@RunWith(AndroidJUnit4::class)
class CollectionTransferLifecycleAcceptanceTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val application get()=instrumentation.targetContext.applicationContext as ManaHubApp
    private val checkpoint get()=File(application.filesDir,"transfer-lifecycle-acceptance.properties")
    private fun emit(value: String)=Log.i("TransferLifecycleAcceptance",value)

    private suspend fun session(): Pair<MtgDatabase,TransferSession.Available> {
        assertTrue(instrumentation is CollectionTransferAcceptanceRunner)
        assertEquals("true",InstrumentationRegistry.getArguments().getString("lifecycleAcceptance"))
        val user=InstrumentationRegistry.getArguments().getString("fixtureUser")!!.toInt()
        assertEquals(user,Process.myUid()/100000)
        val koin=GlobalContext.get()
        val db=koin.get<MtgDatabase>()
        assertSame(application.transferDatabase,db)
        assertTrue(File(checkNotNull(db.openHelper.writableDatabase.path)).canonicalPath.startsWith("/data/user/$user/com.mmg.manahub/"))
        withTimeout(30000) { koin.get<AuthRepository>().sessionState.first { it!=SessionState.Loading } }
        assertEquals(SessionState.Unauthenticated,koin.get<AuthRepository>().sessionState.value)
        val captured=withTimeout(30000) { koin.get<TransferSessionGate>().sessions.first { it is TransferSession.Available } } as TransferSession.Available
        assertTrue(captured.owner is TransferOwner.VerifiedGuest)
        return db to captured
    }

    private fun save(values: Properties) {
        FileOutputStream(checkpoint).use { output -> values.store(output,null);output.fd.sync() }
    }
    private fun load()=Properties().apply { checkpoint.inputStream().use { load(it) } }
    private fun scalar(db: MtgDatabase,sql: String,vararg args: String)=db.openHelper.readableDatabase.query(sql,args).use { cursor -> assertTrue(cursor.moveToFirst());cursor.getLong(0) }
    private fun digest(db: MtgDatabase,sql: String,vararg args: String): String {
        val hash=MessageDigest.getInstance("SHA-256")
        db.openHelper.readableDatabase.query(sql,args).use { cursor ->
            while(cursor.moveToNext())for(index in 0 until cursor.columnCount) {
                val value=if(cursor.isNull(index))"<null>" else cursor.getString(index)
                hash.update("${value.length}:$value;".toByteArray(Charsets.UTF_8))
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
    private fun preserved(db: MtgDatabase,owner: String,small: String): String {
        val rows=digest(db,"SELECT u.id,u.scryfall_id,u.quantity,u.is_foil,u.condition,u.language FROM user_card_collection u JOIN collection_transfer_guest_rows g ON g.row_id=u.id WHERE g.owner_key=? AND u.user_id IS NULL AND u.is_deleted=0 ORDER BY u.id",owner)
        val entries=digest(db,"SELECT * FROM collection_import_entries WHERE job_id=? ORDER BY id",small)
        val actions=digest(db,"SELECT * FROM collection_transfer_actions WHERE job_id=? ORDER BY id",small)
        val commands=digest(db,"SELECT e.* FROM collection_transfer_action_entries e JOIN collection_transfer_actions a ON a.id=e.action_id WHERE a.job_id=? ORDER BY e.action_id,e.entry_id",small)
        return "$rows/$entries/$actions/$commands"
    }
    private suspend fun assertRetained(db: MtgDatabase,captured: TransferSession.Available,values: Properties) {
        val owner=captured.owner.storageKey()
        assertEquals(values.getProperty("owner"),owner)
        val small=values.getProperty("small")
        withTimeout(30000) { while(db.collectionTransferDao().getJob(small,owner)?.phase!="REVIEW_READY")delay(100) }
        assertEquals(values.getProperty("preserved"),preserved(db,owner,small))
        assertEquals(1999L,scalar(db,"SELECT COUNT(*) FROM user_card_collection u JOIN collection_transfer_guest_rows g ON g.row_id=u.id WHERE g.owner_key=? AND u.user_id IS NULL AND u.is_deleted=0",owner))
        assertEquals(7989L,scalar(db,"SELECT SUM(u.quantity) FROM user_card_collection u JOIN collection_transfer_guest_rows g ON g.row_id=u.id WHERE g.owner_key=? AND u.user_id IS NULL AND u.is_deleted=0",owner))
        assertEquals(1L,scalar(db,"SELECT COUNT(*) FROM collection_import_entries WHERE job_id=? AND destination='NONE' AND state='PENDING'",small))
        val receipt=values.getProperty("receipt")
        withTimeout(30000) { while(db.collectionTransferDao().getJob(receipt,owner)?.phase!="REVIEW_READY")delay(100) }
        assertEquals(1L,scalar(db,"SELECT COUNT(*) FROM collection_import_entries WHERE job_id=? AND destination='NONE' AND applied_quantity=0 AND quantity=73",receipt))
        assertEquals(0L,scalar(db,"SELECT COUNT(*) FROM collection_transfer_actions WHERE job_id=?",receipt))
        assertEquals(values.getProperty("jobs").toLong(),scalar(db,"SELECT COUNT(*) FROM collection_transfer_jobs"))
        assertEquals(values.getProperty("receipts").toLong(),scalar(db,"SELECT COUNT(*) FROM collection_transfer_receipts"))
    }

    /** Prepare a durable receipt before an external normal-host process restoration check. */
    @Test fun prepareReceiptForExternalLifecycle()=runBlocking<Unit>(Dispatchers.IO) {
        val existing=checkpoint.takeIf { it.exists() }?.let { load() }
        instrumentation.runOnMainSync { application.startActivity(Intent(application,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        click("Home");waitFor("Quick actions")
        val (db,captured)=session()
        val inventory=db.collectionTransferDao().observeReconciliationJobs(captured.owner.storageKey()).first()
        val small=inventory.single { it.dataRecords==2000L && it.appliedEntries==1999L }
        val values=(existing ?: Properties()).apply {
            if(existing==null) {
                setProperty("small",small.id);setProperty("owner",captured.owner.storageKey())
                setProperty("preserved",preserved(db,captured.owner.storageKey(),small.id))
            } else {
                assertEquals(getProperty("small"),small.id);assertEquals(getProperty("owner"),captured.owner.storageKey())
            }
            setProperty("pid",Process.myPid().toString())
            setProperty("boot",File("/proc/sys/kernel/random/boot_id").readText().trim())
        }
        if(existing!=null) {
            assertRetained(db,captured,values)
            click("Library");waitFor("Cards");click("More options");click("Import to collection");waitFor("Import cards")
            val index=inventory.indexOfFirst { it.id==values.getProperty("receipt") };assertTrue(index>=0)
            click("Resume transfer ${index+1}")
            values.setProperty("expected_back","Cards")
        } else {
        val printing=db.openHelper.readableDatabase.query("SELECT scryfall_id FROM user_card_collection WHERE is_deleted=0 ORDER BY id LIMIT 1").use { cursor -> assertTrue(cursor.moveToFirst());cursor.getString(0) }
        val card=checkNotNull(db.cardDao().getById(printing))
        val source=File(application.cacheDir,"exports/lifecycle-source.csv").apply { parentFile!!.mkdirs() }
        source.writeText("Name,Set code,Collector number,Foil,Quantity,Scryfall ID,Condition,Language\n"+CollectionExportFormatter.csvRecord(listOf(card.name,card.setCode,card.collectorNumber,"normal","73",printing,"NM","en"))+"\n")
        val uri=FileProvider.getUriForFile(application,"${application.packageName}.fileprovider",source)
        val before=inventory.map { it.id }.toSet()
        instrumentation.runOnMainSync { application.startActivity(Intent(Intent.ACTION_SEND).setClass(application,MainActivity::class.java).setType("text/csv").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
        val received=withTimeout(60000) { db.collectionTransferDao().observeReconciliationJobs(captured.owner.storageKey()).first { rows -> rows.any { it.id !in before && it.phase=="REVIEW_READY" } }.single { it.id !in before } }
        values.setProperty("receipt",received.id)
        values.setProperty("jobs",scalar(db,"SELECT COUNT(*) FROM collection_transfer_jobs").toString())
        values.setProperty("receipts",scalar(db,"SELECT COUNT(*) FROM collection_transfer_receipts").toString())
        values.setProperty("expected_back","Quick actions")
        }
        assertRetained(db,captured,values)
        val receipt=values.getProperty("receipt")
        val work=WorkManager.getInstance(application).getWorkInfosForUniqueWork(AndroidTransferWorkScheduler.name(TransferJobId(receipt))).get(10,TimeUnit.SECONDS)
        assertTrue("The real scheduler must prepare the receipt",work.isNotEmpty())
        waitFor("Review import");waitFor("73 pending copies",contains=true)
        save(values)
        emit("RECEIPT_PREPARED receipt=$receipt original_rows=1999 original_copies=7989 new_none=1 actual_worker=true")
    }

    /** Persistence verification runs only after the controller's separate framework/UI restoration. */
    @Test fun verifyLedgerAfterExternalTaskRestoration()=runBlocking<Unit>(Dispatchers.IO) {
        val values=load()
        assertNotEquals(values.getProperty("pid").toInt(),Process.myPid())
        assertEquals("true",InstrumentationRegistry.getArguments().getString("externalTaskRestored"))
        instrumentation.runOnMainSync { application.startActivity(Intent(application,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        click("Home");waitFor("Quick actions")
        val (db,captured)=session()
        assertRetained(db,captured,values)
        values.setProperty("restored_pid",Process.myPid().toString());save(values)
        emit("RESTORATION_LEDGER_VERIFIED old_pid=${values.getProperty("pid")} verifier_pid=${Process.myPid()} receipt=${values.getProperty("receipt")} original_rows=1999 original_copies=7989 original_none=1 new_none=1 commands_unchanged=true")
    }

    /** A retained neutral receipt survives reboot while all immutable applied outcomes stay exact. */
    @Test fun retainedReviewAfterDeviceReboot()=runBlocking<Unit>(Dispatchers.IO) {
        assertEquals("true",InstrumentationRegistry.getArguments().getString("lifecycleAfterReboot"))
        val values=load()
        assertNotEquals(values.getProperty("boot"),File("/proc/sys/kernel/random/boot_id").readText().trim())
        instrumentation.runOnMainSync { application.startActivity(Intent(application,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        click("Home");waitFor("Quick actions")
        val (db,captured)=session()
        assertRetained(db,captured,values)
        click("Library");waitFor("Cards")
        click("More options");click("Import to collection");waitFor("Import cards")
        val inventory=db.collectionTransferDao().observeReconciliationJobs(captured.owner.storageKey()).first()
        val index=inventory.indexOfFirst { it.id==values.getProperty("receipt") };assertTrue(index>=0)
        click("Resume transfer ${index+1}");waitFor("Review import");waitFor("73 pending copies",contains=true)
        assertRetained(db,captured,values)
        emit("REBOOT_RETAINED_REVIEW receipt=${values.getProperty("receipt")} owner_unchanged=true original_rows=1999 original_copies=7989 original_none=1 new_none=1 commands_unchanged=true no_inferred_consent=true pending_worker_drain=false")
    }

    private fun nodes(): List<AccessibilityNodeInfo> = buildList {
        fun visit(node: AccessibilityNodeInfo) { add(node);for(index in 0 until node.childCount)node.getChild(index)?.let(::visit) }
        instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
    }
    private fun find(label: String,contains: Boolean=false)=nodes().firstOrNull { node -> listOf(node.text,node.contentDescription).any { value -> value!=null && if(contains)value.toString().contains(label,true) else value.toString().equals(label,true) } }
    private suspend fun waitFor(label: String,contains: Boolean=false): AccessibilityNodeInfo=withTimeout(15000) { while(true) { find(label,contains)?.let { return@withTimeout it };delay(16) };error("unreachable") }
    private suspend fun click(label: String) {
        var node=waitFor(label)
        while(!node.isClickable)node=node.parent ?: break
        assertTrue("Click $label",node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
}
