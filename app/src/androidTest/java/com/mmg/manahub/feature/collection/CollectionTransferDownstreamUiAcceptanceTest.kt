package com.mmg.manahub.feature.collection

import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mmg.manahub.app.MainActivity
import com.mmg.manahub.app.ManaHubApp
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.feature.collection.data.AndroidCollectionTransferFileStore
import com.mmg.manahub.feature.collection.data.TransferInputSource
import com.mmg.manahub.feature.collection.data.storageKey
import com.mmg.manahub.feature.collection.data.RoomCollectionSelectionRepository
import com.mmg.manahub.feature.collection.data.TransferAuthSessionObserver
import com.mmg.manahub.core.model.CollectionSource
import com.mmg.manahub.core.model.CollectionGroupingMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Exercises production screens and workers in a dedicated emulator framework user. */
@RunWith(AndroidJUnit4::class)
class CollectionTransferDownstreamUiAcceptanceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val actionTimes = mutableListOf<Pair<String, Long>>()
    private var measuring = false
    private fun emit(value: String) = Log.i("TransferAcceptanceUI", value)

    @Test fun realScreensAfterRetainedStreamedGuestImport() = runBlocking<Unit>(Dispatchers.IO) {
        retainedScreens(sourceCount=100000,prepareControl=false)
    }

    @Test fun realScreensAfterStreamedSmallGuestControl() = runBlocking<Unit>(Dispatchers.IO) {
        retainedScreens(sourceCount=2000,prepareControl=true)
    }

    @Test fun retainedLargeSelectionPhaseTimings() = runBlocking<Unit>(Dispatchers.IO) {
        assertTrue(instrumentation is CollectionTransferAcceptanceRunner)
        assertEquals("true",InstrumentationRegistry.getArguments().getString("reuseStreamedFixture"))
        val application=instrumentation.targetContext.applicationContext as ManaHubApp
        val koin=GlobalContext.get()
        val db=koin.get<MtgDatabase>()
        assertSame(application.transferDatabase,db)
        val user=InstrumentationRegistry.getArguments().getString("fixtureUser")!!.toInt()
        assertTrue(File(checkNotNull(db.openHelper.writableDatabase.path)).canonicalFile.toPath().startsWith(File("/data/user/$user/com.mmg.manahub").canonicalFile.toPath()))
        instrumentation.runOnMainSync { application.startActivity(Intent(application,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        tapAndWait("Home","Quick actions")
        val auth=koin.get<AuthRepository>()
        withTimeout(30000) { auth.sessionState.first { it !is SessionState.Loading } }
        assertEquals(SessionState.Unauthenticated,auth.sessionState.value)
        val gate=koin.get<TransferSessionGate>()
        val session=withTimeout(30000) { gate.sessions.first { it is TransferSession.Available } } as TransferSession.Available
        assertTrue(session.owner is TransferOwner.VerifiedGuest)
        assertTrue(db.collectionTransferDao().observeReconciliationJobs(session.owner.storageKey()).first().any { it.dataRecords==100000L && it.appliedEntries==99999L })
        var previous=SystemClock.elapsedRealtime()
        val phases=mutableListOf<String>()
        fun phase(name: String) {
            val current=SystemClock.elapsedRealtime()
            val value="SELECTION_PHASE name=$name elapsed_ms=${current-previous} uptime_ms=${SystemClock.uptimeMillis()}"
            phases+=value;emit(value);previous=current
        }
        val repository=RoomCollectionSelectionRepository(db,gate,koin.get<TransferAuthSessionObserver>(),onEvaluationPhase=::phase)
        val query=CollectionSelectionQuery(CollectionSource.COLLECTION,"",null,CollectionSelectionSort.DATE_ADDED,false,CollectionGroupingMode.NONE)
        val id=UUID.randomUUID().toString()
        try {
            repository.freeze(session.owner,query,id);phase("freeze")
            val summary=repository.evaluate(session.owner,id,query);phase("summary")
            assertEquals(99999L,summary.groups);assertEquals(443968L,summary.copies)
            assertEquals(50,repository.page(session.owner,id,0L).groups.size);phase("first_page")
        } finally {
            withContext(NonCancellable) { repository.discard(session.owner,id);phase("discard") }
            instrumentation.sendStatus(2,android.os.Bundle().apply { putString("stream","\n${phases.joinToString("\n")}\n") })
        }
    }

    private suspend fun retainedScreens(sourceCount: Int,prepareControl: Boolean) = coroutineScope {
        assertTrue(instrumentation is CollectionTransferAcceptanceRunner)
        assertEquals("true",InstrumentationRegistry.getArguments().getString(if(prepareControl)"smallStreamedControl" else "reuseStreamedFixture"))
        val application=instrumentation.targetContext.applicationContext as ManaHubApp
        val koin=GlobalContext.get()
        val db=koin.get<MtgDatabase>()
        assertSame(application.transferDatabase,db)
        val user=InstrumentationRegistry.getArguments().getString("fixtureUser")!!.toInt()
        val root=File("/data/user/$user/com.mmg.manahub").canonicalFile.toPath()
        assertTrue(File(checkNotNull(db.openHelper.writableDatabase.path)).canonicalFile.toPath().startsWith(root))
        instrumentation.runOnMainSync { application.startActivity(Intent(application,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        val auth=koin.get<AuthRepository>()
        withTimeout(30000) { auth.sessionState.first { it !is SessionState.Loading } }
        assertEquals(SessionState.Unauthenticated,auth.sessionState.value)
        val session=withTimeout(30000) { koin.get<TransferSessionGate>().sessions.first { it is TransferSession.Available } } as TransferSession.Available
        assertTrue(session.owner is TransferOwner.VerifiedGuest)
        val key=session.owner.storageKey()
        if(prepareControl)prepareSmallControl(application,db,session)
        fun totals(sql:String)=db.openHelper.readableDatabase.query(sql,arrayOf(key)).use { cursor ->
            assertTrue(cursor.moveToFirst());cursor.getLong(0) to cursor.getLong(1)
        }
        val expected=totals("SELECT COUNT(DISTINCT c.set_code||'|'||COALESCE(NULLIF(c.oracle_id,''),NULLIF(c.name,''),e.scryfall_id)),SUM(e.completed_quantity) FROM collection_transfer_action_entries e JOIN collection_transfer_actions a ON a.id=e.action_id JOIN cards c ON c.scryfall_id=e.scryfall_id WHERE a.owner_key=? AND a.destination='COLLECTION' AND a.phase='COMPLETED' AND e.state='COMPLETED'")
        val actual=totals("SELECT COUNT(DISTINCT c.set_code||'|'||COALESCE(NULLIF(c.oracle_id,''),NULLIF(c.name,''),u.scryfall_id)),SUM(u.quantity) FROM user_card_collection u JOIN collection_transfer_guest_rows g ON g.row_id=u.id JOIN cards c ON c.scryfall_id=u.scryfall_id WHERE g.owner_key=? AND u.user_id IS NULL AND u.is_deleted=0")
        assertEquals(expected,actual)
        assertTrue(actual.first>=sourceCount-1L)
        if(prepareControl)assertEquals(sourceCount-1L,actual.first)
        assertTrue(db.collectionTransferDao().observeReconciliationJobs(key).first().any { it.dataRecords==sourceCount.toLong() && it.appliedEntries==sourceCount-1L })
        emit("REUSED_REAL_PIPELINE source_count=$sourceCount groups=${actual.first} copies=${actual.second} immutable_commands_match=true shared_hilt_koin_db=true verified_guest=true heap_comparison=false")
        waitFor("Library",30000);dismissOnboarding()
        suspend fun routes() {
            tapAndWait("Library","Cards")
            waitFor("${actual.first} unique cards · ${actual.second} copies",60000)
            waitFor("Fixture",10000,contains=true);scroll("Collection")
            tapAndWait("Home","Quick actions")
            tapAndWait("My collection","Statistics");scroll("Stats");backAndWait("Quick actions")
            tapAndWait("Scan Card","Scan queue",containsArrival=true);dismissOnboarding();scroll("Scanner");backAndWait("Quick actions")
            tapAndWait("Library","Cards")
            waitFor("${actual.first} unique cards · ${actual.second} copies",60000)
            tapAndWait("Home","Quick actions")
        }
        emit("REUSED_WARM_BEGIN uptime_ms=${SystemClock.uptimeMillis()}")
        routes()
        emit("REUSED_WARM_END uptime_ms=${SystemClock.uptimeMillis()}")
        val maximum=AtomicLong();val peak=AtomicLong();var dispatch=0L
        instrumentation.runOnMainSync { Looper.getMainLooper().setMessageLogging { line ->
            if(line.startsWith(">>>>>"))dispatch=SystemClock.uptimeMillis()
            else if(line.startsWith("<<<<<") && dispatch!=0L) { maximum.accumulateAndGet(SystemClock.uptimeMillis()-dispatch,::maxOf);dispatch=0L }
        } }
        val sampler=launch { while(isActive) { val runtime=Runtime.getRuntime();peak.accumulateAndGet(runtime.totalMemory()-runtime.freeMemory(),::maxOf);delay(10) } }
        measuring=true
        emit("REUSED_UI_BEGIN uptime_ms=${SystemClock.uptimeMillis()}")
        try {
            routes()
            emit("REUSED_UI_END groups=${actual.first} copies=${actual.second} actual_main_message_max_ms=${maximum.get()} peak_heap_bytes=${peak.get()} uptime_ms=${SystemClock.uptimeMillis()}")
        } finally { sampler.cancelAndJoin();instrumentation.runOnMainSync { Looper.getMainLooper().setMessageLogging(null) } }
        assertTrue("Main exceeded100ms: ${maximum.get()}",maximum.get()<=100)
        actionTimes.forEach { (action,elapsed) -> assertTrue("Response exceeded500ms: $action=$elapsed",elapsed<=500) }
    }

    private suspend fun prepareSmallControl(application: ManaHubApp,db: MtgDatabase,session: TransferSession.Available) {
        val key=session.owner.storageKey()
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM collection_transfer_jobs").use { cursor ->
            assertTrue(cursor.moveToFirst());assertEquals("Small control requires a new empty framework user",0L,cursor.getLong(0))
        }
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM user_card_collection").use { cursor ->
            assertTrue(cursor.moveToFirst());assertEquals(0L,cursor.getLong(0))
        }
        val koin=GlobalContext.get()
        val helper=CollectionTransferAcceptancePipelineTest()
        val source=File(application.filesDir,"fixture-control-2000.csv")
        helper.seedMetadata(db,2000);helper.writeSource(source,2000)
        val job=TransferJobId(UUID.randomUUID().toString())
        koin.get<AndroidCollectionTransferFileStore>().receive(job,session.generation,listOf(object: TransferInputSource {
            override val identity=source.name
            override fun open()=source.inputStream()
        }))
        val repository=koin.get<CollectionTransferRepository>()
        assertEquals(TransferMutationResult.Accepted,repository.bindReceipt(job,session.owner,session.generation,TransferOrigin.SAF))
        withTimeout(1800000) {
            while(db.collectionTransferDao().getJob(job.value,key)?.phase!="REVIEW_READY")delay(500)
        }
        val ready=repository.observeSummary(job,session.owner).filterNotNull().first()
        assertEquals(2000L,ready.pendingEntries)
        assertEquals(TransferMutationResult.Accepted,repository.chooseDestination(job,session.owner,ready.generation,ready.intentRevision,TransferDestination.COLLECTION))
        val undecided=repository.readPage(job,session.owner,null).entries.first()
        assertEquals(TransferMutationResult.Accepted,repository.editPendingEntry(job,session.owner,ready.generation,undecided.copy(destination=TransferDestination.NONE)))
        val selected=repository.observeSummary(job,session.owner).filterNotNull().first()
        val action=TransferActionId(UUID.randomUUID().toString())
        assertEquals(TransferMutationResult.Accepted,repository.confirmAction(session.owner,TransferActionRequest(action,job,selected.generation,TransferDestination.COLLECTION,TransferActionScope.DestinationSelection(selected.intentRevision))))
        withTimeout(1800000) {
            while(db.collectionTransferDao().action(action.value,key)?.phase!="COMPLETED")delay(500)
        }
        val completed=repository.observeSummary(job,session.owner).filterNotNull().first()
        assertEquals(1999L,completed.appliedEntries);assertEquals(1L,completed.pendingEntries)
        emit("SMALL_CONTROL_PREPARED source_count=2000 applied_entries=1999 retained_none=1 production_workmanager=true")
    }

    @Test fun realScreensAfterStreamedGuestImport() = runBlocking<Unit>(Dispatchers.IO) {
        assertTrue(instrumentation is CollectionTransferAcceptanceRunner)
        val application = instrumentation.targetContext.applicationContext as ManaHubApp
        val koin = GlobalContext.get()
        val db = koin.get<MtgDatabase>()
        assertSame(application.transferDatabase, db)
        val arguments = InstrumentationRegistry.getArguments()
        val user = arguments.getString("fixtureUser")!!.toInt()
        val root = File("/data/user/$user/com.mmg.manahub").canonicalFile.toPath()
        assertTrue(File(checkNotNull(db.openHelper.writableDatabase.path)).canonicalFile.toPath().startsWith(root))
        assertTrue(application.filesDir.canonicalFile.toPath().startsWith(root))
        emit("ISOLATION shared_hilt_koin_db=true database_confined=true files_confined=true")
        instrumentation.startActivitySync(Intent(application, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val auth = koin.get<AuthRepository>()
        withTimeout(30000) { auth.sessionState.first { it !is SessionState.Loading } }
        assertEquals(SessionState.Unauthenticated, auth.sessionState.value)
        val gate = koin.get<TransferSessionGate>()
        val session = withTimeout(30000) { gate.sessions.first { it is TransferSession.Available } } as TransferSession.Available
        assertTrue(session.owner is TransferOwner.VerifiedGuest)
        emit("OWNER verified_guest=true")
        waitFor("Library", 30000)
        dismissOnboarding()
        val sizes = (arguments.getString("uiFixtureSizes") ?: arguments.getString("uiFixtureSize", "10000,100000"))!!
            .split(',').map(String::toInt)
        assertTrue(sizes.all { it in 2000..100000 })
        val helper = CollectionTransferAcceptancePipelineTest()
        val files = koin.get<AndroidCollectionTransferFileStore>()
        val repository = koin.get<CollectionTransferRepository>()
        val mainMax = AtomicLong()
        val maxAt = AtomicReference("")
        val stage = AtomicReference("warmup")
        val heapPeak = AtomicLong()
        val results = mutableListOf<Triple<Int, Long, Long>>()
        var dispatchStart = 0L
        suspend fun visitReview(job: TransferJobId, expectedName: String? = null) {
            stage.set("review_navigation")
            tapAndWait("Library", "Cards")
            clickAny(listOf("More options"))
            clickAny(listOf("Import to collection"))
            waitFor("Import cards", 10000)
            val inventory = db.collectionTransferDao().observeReconciliationJobs(session.owner.storageKey()).first()
            val index = inventory.indexOfFirst { it.id == job.value }
            assertTrue("Current fixture missing from bounded resume inventory", index >= 0)
            tapAndWait("Resume transfer ${index + 1}", "Review import")
            waitFor(expectedName ?: "Fixture", 10000, contains = expectedName == null)
            emit("REVIEW current_fixture_verified=true inventory_index=$index")
            scroll("Review")
            backAndWait("Cards", departure = "Review import")
            tapAndWait("Home", "Quick actions")
        }
        suspend fun batch(count: Int, warmup: Boolean) {
            helper.seedMetadata(db, count)
            val source = File(application.filesDir, "fixture-$count.csv")
            helper.writeSource(source, count)
            System.gc()
            delay(10000)
            measuring = !warmup
            mainMax.set(0); heapPeak.set(0); maxAt.set("")
            stage.set("receive")
            instrumentation.runOnMainSync {
                Looper.getMainLooper().setMessageLogging { message ->
                    if (message.startsWith(">>>>>")) dispatchStart = SystemClock.uptimeMillis()
                    else if (message.startsWith("<<<<<") && dispatchStart != 0L) {
                        val elapsed = SystemClock.uptimeMillis() - dispatchStart
                        if (elapsed > mainMax.get()) {
                            mainMax.set(elapsed)
                            maxAt.set("${stage.get()}@${SystemClock.uptimeMillis()}")
                        }
                        dispatchStart = 0L
                    }
                }
            }
            val sampler = launch {
                while (isActive) {
                    val runtime = Runtime.getRuntime()
                    heapPeak.accumulateAndGet(runtime.totalMemory() - runtime.freeMemory(), ::maxOf)
                    delay(10)
                }
            }
            val started = SystemClock.elapsedRealtime()
            emit("ROI_BEGIN count=$count warmup=$warmup uptime_ms=${SystemClock.uptimeMillis()}")
            try {
                val job = TransferJobId(UUID.randomUUID().toString())
                files.receive(job, session.generation, listOf(object : TransferInputSource {
                    override val identity = source.name
                    override fun open() = source.inputStream()
                }))
                assertEquals(TransferMutationResult.Accepted, repository.bindReceipt(job, session.owner, session.generation, TransferOrigin.SAF))
                suspend fun summary() = repository.observeSummary(job, session.owner).filterNotNull().first()
                withTimeout(1800000) {
                    var previous: TransferPhase? = null
                    while (true) {
                        val current = checkNotNull(db.collectionTransferDao().getJob(job.value, session.owner.storageKey()))
                        val phase=TransferPhase.valueOf(current.phase)
                        stage.set("prepare_$phase")
                        if (previous != phase) { emit("PREPARE count=$count phase=$phase"); previous = phase }
                        check(phase != TransferPhase.FAILED_RETRYABLE) { "Production preparation failed" }
                        if(phase==TransferPhase.REVIEW_READY && summary().pendingEntries==count.toLong())break
                        delay(500)
                    }
                }
                emit("REVIEW_READY count=$count uptime_ms=${SystemClock.uptimeMillis()}")
                visitReview(job)
                stage.set("choose")
                val ready = summary()
                assertEquals(TransferMutationResult.Accepted, repository.chooseDestination(job, session.owner, ready.generation, ready.intentRevision, TransferDestination.COLLECTION))
                val undecided = repository.readPage(job, session.owner, null).entries.first()
                assertEquals(TransferMutationResult.Accepted, repository.editPendingEntry(job, session.owner, ready.generation, undecided.copy(destination = TransferDestination.NONE)))
                val action = TransferActionId(UUID.randomUUID().toString())
                val selected = summary()
                assertEquals(TransferMutationResult.Accepted, repository.confirmAction(session.owner,
                    TransferActionRequest(action, job, selected.generation, TransferDestination.COLLECTION,
                        TransferActionScope.DestinationSelection(selected.intentRevision))))
                stage.set("apply")
                emit("APPLY_BEGIN count=$count uptime_ms=${SystemClock.uptimeMillis()}")
                withTimeout(1800000) { while (db.collectionTransferDao().action(action.value,session.owner.storageKey())?.phase != "COMPLETED") delay(500) }
                assertEquals(TransferActionPhase.COMPLETED,repository.readAction(session.owner,action).phase)
                assertEquals((count - 1).toLong(), summary().appliedEntries)
                assertEquals(1L, summary().pendingEntries)
                emit("FIXTURE count=$count applied_delta=${summary().appliedEntries} retained_none=1 guest_provenance=true")
                stage.set("collection_navigation")
                tapAndWait("Library", "Cards")
                val dataStarted = SystemClock.elapsedRealtime()
                waitFor("Fixture", 60000, contains = true)
                emit("DATA_READY count=$count screen=Collection elapsed_ms=${SystemClock.elapsedRealtime() - dataStarted}")
                scroll("Collection")
                tapAndWait("Home", "Quick actions")
                stage.set("stats_navigation")
                tapAndWait("My collection", "Statistics")
                scroll("Stats")
                backAndWait("Quick actions")
                stage.set("scanner_navigation")
                tapAndWait("Scan Card", "Scan queue", containsArrival = true)
                dismissOnboarding()
                emit("SCREEN Scanner reached=true")
                scroll("Scanner")
                val pauseStarted = SystemClock.elapsedRealtime()
                automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                val pauseDeadline = SystemClock.elapsedRealtime() + 10000
                while (automation.rootInActiveWindow?.packageName?.toString() in listOf(null, application.packageName) && SystemClock.elapsedRealtime() < pauseDeadline) SystemClock.sleep(16)
                record("pause", SystemClock.elapsedRealtime() - pauseStarted)
                instrumentation.runOnMainSync { application.startActivity(Intent(application, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                waitFor("Scan queue", 10000, contains = true)
                backAndWait("Quick actions")
                tapAndWait("Library", "Cards")
                val expectedName = checkNotNull(db.cardDao().getById(undecided.scryfallId)).name
                visitReview(job, expectedName)
                emit("ROI_END count=$count warmup=$warmup elapsed_ms=${SystemClock.elapsedRealtime()-started} actual_main_message_max_ms=${mainMax.get()} max_at=${maxAt.get()} peak_heap_bytes=${heapPeak.get()} max_heap_bytes=${Runtime.getRuntime().maxMemory()} uptime_ms=${SystemClock.uptimeMillis()}")
                if (!warmup) results += Triple(count, mainMax.get(), heapPeak.get())
            } finally {
                sampler.cancelAndJoin()
                instrumentation.runOnMainSync { Looper.getMainLooper().setMessageLogging(null) }
            }
        }
        batch(1000, warmup = true)
        for (count in sizes) batch(count, warmup = false)
        for ((count, main, _) in results) assertTrue("Measured Main exceeded100ms count=$count max=$main", main <= 100)
        for ((action, elapsed) in actionTimes) assertTrue("Measured response exceeded500ms action=$action elapsed=$elapsed", elapsed <= 500)
        val baseline = results.firstOrNull { it.first == 10000 }
        val large = results.firstOrNull { it.first == 100000 }
        if (baseline != null && large != null) {
            val delta = large.third - baseline.third
            emit("HEAP_DELTA bytes=$delta limit_bytes=33554432")
            assertTrue("UI heap increase exceeded32MiB: $delta", delta <= 33554432)
        }
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            result += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        automation.rootInActiveWindow?.let(::visit)
        return result
    }
    private fun labels() = nodes().mapNotNull { (it.text ?: it.contentDescription)?.toString() }.distinct()
    private fun find(label: String, contains: Boolean = false) = nodes().firstOrNull { node ->
        listOf(node.text, node.contentDescription).any { value -> value != null &&
            if (contains) value.toString().contains(label, ignoreCase = true) else value.toString().equals(label, ignoreCase = true) }
    }
    private fun waitFor(label: String, timeout: Long, contains: Boolean = false): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + timeout
        do { find(label, contains)?.let { return it }; SystemClock.sleep(16) } while (SystemClock.elapsedRealtime() < deadline)
        error("Missing $label; visible=${labels()}")
    }
    private fun clickAny(choices: List<String>, contains: Boolean = false) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        var found: AccessibilityNodeInfo? = null
        do {
            found = choices.firstNotNullOfOrNull { find(it, contains) }
            if (found == null) SystemClock.sleep(16)
        } while (found == null && SystemClock.elapsedRealtime() < deadline)
        var node = checkNotNull(found) { "Missing $choices; visible=${labels()}" }
        while (!node.isClickable) node = node.parent ?: break
        check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun record(action: String, elapsed: Long) {
        emit("ACTION name=$action elapsed_ms=$elapsed warmup=${!measuring} idle_wait_excluded=true")
        if (measuring) actionTimes += action to elapsed
    }
    private fun tapAndWait(label: String, arrival: String, containsArrival: Boolean = false) {
        val start = SystemClock.elapsedRealtime()
        clickAny(listOf(label))
        waitFor(arrival, 15000, containsArrival)
        record("navigate_$label", SystemClock.elapsedRealtime() - start)
    }
    private fun backAndWait(arrival: String, departure: String? = null) {
        val start = SystemClock.elapsedRealtime()
        automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        if (departure != null) {
            val deadline = SystemClock.elapsedRealtime() + 15000
            while (find(departure) != null && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(16)
            check(find(departure) == null) { "Previous screen still visible: $departure" }
        }
        waitFor(arrival, 15000)
        record("back_$arrival", SystemClock.elapsedRealtime()-start)
    }
    private fun scroll(screen: String) {
        val start = SystemClock.elapsedRealtime()
        val moved = nodes().firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false
        record("scroll_$screen", SystemClock.elapsedRealtime()-start)
        emit("SCROLL screen=$screen accepted=$moved")
    }
    private fun dismissOnboarding() {
        repeat(25) {
            val skip = find("Skip") ?: find("Got it") ?: return
            var node = skip
            while (!node.isClickable) node = node.parent ?: break
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            SystemClock.sleep(50)
        }
    }
}
