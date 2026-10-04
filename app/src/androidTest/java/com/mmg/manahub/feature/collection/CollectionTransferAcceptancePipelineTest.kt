package com.mmg.manahub.feature.collection

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.core.domain.repository.CardLookupIdentifier
import com.mmg.manahub.core.model.CollectionGroupingMode
import com.mmg.manahub.core.model.CollectionSource
import com.mmg.manahub.feature.collection.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Connected acceptance uses private disk fixtures and never resolves a production account. */
@RunWith(AndroidJUnit4::class)
class CollectionTransferAcceptancePipelineTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private val owner = TransferOwner.Account("phase4-isolated-fixture")
    private val key = "account:${owner.id}"
    private fun uuid() = UUID.randomUUID().toString()
    private fun printing(index: Int) = "00000000-0000-4000-8000-${index.toString().padStart(12, '0')}"
    private fun index(id: String) = id.takeLast(12).toInt()
    private fun name(index: Int) = "Fixture ${index.toString().padStart(6, '0')}"
    private fun quantity(index: Int) = 1L + index % 7
    private fun foil(index: Int) = index % 2 == 0
    private fun condition(index: Int) = if (index % 3 == 0) "LP" else "NM"
    private fun language(index: Int) = if (index % 5 == 0) "ja" else "en"
    private fun query() = CollectionSelectionQuery(CollectionSource.COLLECTION, "", null,
        CollectionSelectionSort.NAME, true, CollectionGroupingMode.NONE)
    private val reporter = object : CrashReporter {
        override fun log(message: String) = Unit
        override fun recordException(throwable: Throwable) { throw AssertionError("Unexpected export failure", throwable) }
        override fun setCustomKey(key: String, value: String) = Unit
    }
    private val scheduler = object : TransferWorkScheduler {
        override suspend fun enqueue(id: TransferJobId) = Unit
        override suspend fun cancel(id: TransferJobId) = Unit
        override suspend fun enqueueWishlist() = Unit
        override suspend fun cancelWishlist() = Unit
        override fun observeFinished(id: TransferJobId?): Flow<Boolean> = flowOf(false)
    }
    private inner class Harness(val label: String, val persistentName: String = "phase4-${uuid()}") {
        val directory = File(context.filesDir, persistentName).apply { check(mkdirs() || isDirectory) }
        val db = Room.databaseBuilder(context, MtgDatabase::class.java, persistentName).build()
        val gate = TransferSessionGate()
        val auth = MutableStateFlow<SessionState>(SessionState.Authenticated(AuthUser(owner.id, null, null, null, null, "fixture")))
        val observer = TransferAuthSessionObserver(auth, VerifiedTransferGuestIdentity(context), gate, db.collectionTransferDao())
        val repository = RoomCollectionTransferRepository(db, gate, observer::matchesObserved, scheduler = scheduler)
        val files = AndroidCollectionTransferFileStore(directory, db.collectionTransferDao(), session = { gate.currentSession })
        var events = 0L
        var remoteCalls = 0L
        val gateway = object : TransferResolutionGateway {
            override suspend fun cached(identifier: CardLookupIdentifier): TransferPrinting? {
                val id = identifier.scryfallId ?: identifier.collectorNumber?.toIntOrNull()?.let(::printing)
                    ?: identifier.name?.removePrefix("Fixture ")?.toIntOrNull()?.let(::printing)
                    ?: error("Unexpected non-fixture identity")
                return if (id.startsWith("00000000-")) {
                    val n = index(id); TransferPrinting(id, name(n), "tst", n.toString())
                } else db.cardDao().getById(id)?.let { TransferPrinting(it.scryfallId, it.name, it.setCode, it.collectorNumber) }
            }
            override suspend fun lookup(identifiers: List<CardLookupIdentifier>): TransferLookupBatch = error("Real network is forbidden")
            override suspend fun fallbackName(name: String): TransferNameLookup = error("Real network is forbidden")
        }
        val resolver = DurableTransferResolver(RoomTransferResolutionStore(db.collectionTransferDao()) { gate.currentSession }, gateway, System::currentTimeMillis)
        val review = RoomTransferReviewBuilder(db.collectionTransferDao(), { gate.currentSession }, System::currentTimeMillis)
        val collection = RoomTransferCollectionExecutor(db, gate, System::currentTimeMillis, { events++ }, matchesObservedOwner = observer::matchesObserved)
        val wishlist = RoomTransferWishlistExecutor(db, gate, System::currentTimeMillis, observer::matchesObserved)
        val delivery = RoomTransferWishlistDeliveryStore(db, gate, observer::matchesObserved, System::currentTimeMillis)
        val sync = TransferWishlistSync(gate, delivery, TransferWishlistDeliveryGateway { _, _ -> remoteCalls++; error("Offline fake delivery") }, observer::matchesObserved)
        val coordinator = RoomCollectionTransferCoordinator(db, gate, observer::matchesObserved, files, resolver, review, collection, wishlist, sync, scheduler)
        val selection = RoomCollectionSelectionRepository(db, gate, observer)
        val exportRoot = File(directory, "exports")
        val exports = RoomCollectionExportRepository(context, db, selection, gate, observer, gateway, reporter, root = exportRoot)
        suspend fun start() { gate.changeOwner(owner); db.openHelper.writableDatabase }
        fun close(remove: Boolean = true) { db.close(); if (remove) { context.deleteDatabase(persistentName); directory.deleteRecursively() } }
        suspend fun receive(source: File): TransferJobId {
            val job = TransferJobId(uuid())
            files.receive(job, (gate.currentSession as TransferSession.Available).generation,
                listOf(object : TransferInputSource { override val identity = source.name; override fun open() = source.inputStream() }))
            assertEquals(TransferMutationResult.Accepted, repository.bindReceipt(job, owner,
                (gate.currentSession as TransferSession.Available).generation, TransferOrigin.SAF))
            return job
        }
        suspend fun prepare(job: TransferJobId) {
            measured("$label/parse") {
                files.recoverReceipt(job)
                for (file in db.collectionTransferDao().currentFiles(job.value, key).filter { it.selected && it.phase == "PARSING" }) {
                    files.verifySource(job, TransferFileId(file.id)); files.parseSource(job, TransferFileId(file.id))
                }
            }
            measured("$label/resolve") {
                var runs = 0
                while (resolver.runSlice(job, owner, 100) == TransferSliceResult.CONTINUE) assertTrue(++runs < 100)
            }
            measured("$label/review") {
                var runs = 0
                while (review.runSlice(job, owner, 100).result == TransferSliceResult.CONTINUE) assertTrue(++runs < 100)
            }
            assertTrue("Preparation did not publish review", db.collectionTransferDao().getJob(job.value, key)!!.phase in setOf("REVIEW_READY", "REVIEW_REQUIRED"))
        }
        suspend fun summary(job: TransferJobId) = repository.observeSummary(job, owner).filterNotNull().first()
        suspend fun chooseAll(job: TransferJobId, destination: TransferDestination) {
            val s = summary(job)
            assertEquals(TransferMutationResult.Accepted, repository.chooseDestination(job, owner, s.generation, s.intentRevision, destination))
        }
        suspend fun command(job: TransferJobId, destination: TransferDestination): TransferActionId {
            val s = summary(job); val action = TransferActionId(uuid())
            assertEquals(TransferMutationResult.Accepted, repository.confirmAction(owner,
                TransferActionRequest(action, job, s.generation, destination, TransferActionScope.DestinationSelection(s.intentRevision))))
            return action
        }
        suspend fun apply(job: TransferJobId, destination: TransferDestination): TransferActionId {
            val action = command(job, destination)
            repeat(30) {
                RunTransferWork(coordinator).run(job)
                if (repository.readAction(owner, action).phase == TransferActionPhase.COMPLETED) return action
            }
            error("Apply did not complete")
        }
        suspend fun count(sql: String): Long = db.openHelper.writableDatabase.query(sql).use { it.moveToFirst(); it.getLong(0) }
    }
    private suspend fun <T> measured(label: String, block: suspend () -> T): T {
        val start = SystemClock.elapsedRealtime(); emit("BEGIN $label")
        val result = block(); emit("END $label elapsed_ms=${SystemClock.elapsedRealtime() - start}"); return result
    }
    private fun emit(value: String) { Log.i("TransferAcceptance", value); println("PHASE4 $value") }
    private fun metadata(n: Int) = CardEntity(scryfallId = printing(n), name = name(n), printedName = null,
        lang = "en", manaCost = null, cmc = 1.0, colors = "[]", colorIdentity = "[]", typeLine = "Artifact",
        printedTypeLine = null, oracleText = null, printedText = null, keywords = "[]", power = null,
        toughness = null, loyalty = null, setCode = "tst", setName = "Fixture Set", collectorNumber = n.toString(),
        rarity = "common", releasedAt = "2026-01-01", imageNormal = null, imageArtCrop = null, imageBackNormal = null,
        priceUsd = null, priceUsdFoil = null, priceEur = null, priceEurFoil = null, legalityStandard = "legal",
        legalityPioneer = "legal", legalityModern = "legal", legalityCommander = "legal", flavorText = null,
        artist = null, scryfallUri = "", oracleId = "oracle-$n")
    private suspend fun seed(h: Harness, count: Int) {
        seedMetadata(h.db, count)
    }
    internal suspend fun seedMetadata(database: MtgDatabase, count: Int) {
        for (start in 0 until count step 200) database.cardDao().upsertAll((start until minOf(start + 200, count)).map(::metadata))
    }
    internal fun writeSource(file: File, count: Int, repeated: Boolean = false, errors: Boolean = false) {
        file.bufferedWriter().use { writer ->
            writer.appendLine("Name,Set code,Collector number,Foil,Quantity,Scryfall ID,Condition,Language")
            repeat(count) { n ->
                val i = if (repeated) 0 else n
                writer.appendLine(CollectionExportFormatter.csvRecord(listOf(name(i), "tst", i.toString(),
                    if (foil(i)) "foil" else "normal", if (errors) "invalid" else quantity(n).toString(),
                    printing(i), condition(i), language(i))))
            }
        }
    }
    private fun tuple(id: String, foil: Boolean, condition: String, language: String, quantity: Long) = "$id|$foil|$condition|$language|$quantity\n"
    private fun hex(digest: MessageDigest) = digest.digest().joinToString("") { "%02x".format(it) }
    private fun expected(count: Int, all: Boolean = false): String {
        val digest = MessageDigest.getInstance("SHA-256")
        repeat(count) { n -> if (all || n % 10 < 8) digest.update(tuple(printing(n), foil(n), condition(n), language(n), quantity(n)).toByteArray()) }
        return hex(digest)
    }
    private suspend fun collectionDigest(h: Harness): String {
        val digest = MessageDigest.getInstance("SHA-256"); var after = ""
        while (true) {
            var rows = 0
            h.db.openHelper.writableDatabase.query("SELECT scryfall_id,is_foil,condition,language,quantity FROM user_card_collection WHERE user_id=? AND is_deleted=0 AND scryfall_id>? ORDER BY scryfall_id,is_foil,condition,language LIMIT 200", arrayOf(owner.id, after)).use { c ->
                while (c.moveToNext()) { rows++; after = c.getString(0); digest.update(tuple(after, c.getInt(1) != 0, c.getString(2), c.getString(3), c.getLong(4)).toByteArray()) }
            }
            if (rows < 200) break
        }
        return hex(digest)
    }
    private class Sampler {
        val peak = AtomicLong(); val mainDelay = AtomicLong(); private val handler = Handler(Looper.getMainLooper())
        @Volatile var active = true
        private var due = SystemClock.uptimeMillis()
        private val pulse = object : Runnable {
            override fun run() {
                val now = SystemClock.uptimeMillis(); mainDelay.accumulateAndGet(maxOf(0, now - due), ::maxOf)
                if (active) { due = now + 10; handler.postDelayed(this, 10) }
            }
        }
        fun start(scope: CoroutineScope): Job {
            handler.post(pulse)
            return scope.launch(Dispatchers.Default) { while (active) { val r = Runtime.getRuntime(); peak.accumulateAndGet(r.totalMemory() - r.freeMemory(), ::maxOf); delay(10) } }
        }
        fun stop() { active = false; handler.removeCallbacks(pulse) }
    }
    private suspend fun pipeline(count: Int, scope: CoroutineScope): Pair<Long, Long> {
        val h = Harness("distinct-$count"); h.start(); val sampler = Sampler(); val sampling = sampler.start(scope)
        try {
            measured("$count/cache_seed") { seed(h, count) }
            val source = File(h.directory, "fixture.csv"); measured("$count/stream_seed") { writeSource(source, count) }
            val job = measured("$count/receive") { h.receive(source) }
            measured("$count/parse_resolve_review") { h.prepare(job) }
            val ready = h.summary(job); assertEquals((0 until count).sumOf(::quantity), ready.acceptedCopies)
            assertEquals(count.toLong(), ready.pendingEntries); assertEquals(0L, h.count("SELECT COUNT(*) FROM user_card_collection"))
            measured("$count/mixed_explicit_choices") {
                h.chooseAll(job, TransferDestination.COLLECTION)
                var cursor: TransferPageCursor? = null; var seen = 0L
                do {
                    val page = h.repository.readPage(job, owner, cursor)
                    for (entry in page.entries) {
                        seen++; val i = index(entry.scryfallId)
                        if (i % 10 >= 8) assertEquals(TransferMutationResult.Accepted,
                            h.repository.editPendingEntry(job, owner, ready.generation, entry.copy(destination = if (i % 10 == 8) TransferDestination.WISHLIST else TransferDestination.NONE)))
                    }
                    cursor = page.next
                    if (seen % 10000L == 0L) emit("$count/choices_seen=$seen")
                } while (cursor != null)
                assertEquals(count.toLong(), seen)
            }
            val collectionAction = measured("$count/collection_apply") { h.apply(job, TransferDestination.COLLECTION) }
            val wishlistAction = measured("$count/wishlist_apply") { h.apply(job, TransferDestination.WISHLIST) }
            val s = h.summary(job)
            var collectionCopies = 0L; var wishlistCopies = 0L; var noneCopies = 0L
            repeat(count) { when (it % 10) { in 0..7 -> collectionCopies += quantity(it); 8 -> wishlistCopies += quantity(it); else -> noneCopies += quantity(it) } }
            assertEquals(collectionCopies, s.appliedCopies); assertEquals(wishlistCopies, s.wishlistCompletedCopies)
            assertEquals(noneCopies, s.pendingCopies); assertEquals(count / 10L, s.pendingEntries)
            assertEquals(count * 8L / 10, h.count("SELECT COUNT(*) FROM user_card_collection WHERE user_id='${owner.id}'"))
            assertEquals(count / 10L, h.count("SELECT COUNT(*) FROM collection_import_entries WHERE job_id='${job.value}' AND destination='NONE' AND state='PENDING'"))
            assertEquals(wishlistCopies, h.count("SELECT SUM(completed_quantity) FROM collection_transfer_action_entries WHERE action_id='${wishlistAction.value}'"))
            assertEquals(count.toLong(), h.count("SELECT SUM(source_records) FROM collection_import_provenance WHERE job_id='${job.value}'"))
            assertEquals(count * 8L / 10, h.count("SELECT COUNT(*) FROM collection_import_provenance WHERE job_id='${job.value}' AND participated=1"))
            assertEquals(count / 10L, h.count("SELECT COUNT(*) FROM collection_import_provenance WHERE job_id='${job.value}' AND wishlist_participated=1"))
            measured("$count/fake_wishlist_delivery_failure") { h.sync.runSlice(owner) }
            assertEquals(1L, h.remoteCalls)
            assertTrue(h.delivery.hasPending(owner))
            val before = collectionDigest(h); assertEquals(expected(count), before)
            RunTransferWork(h.coordinator).run(job); assertEquals(before, collectionDigest(h))
            assertEquals(TransferActionPhase.COMPLETED, h.repository.readAction(owner, collectionAction).phase)
            assertEquals(0, h.collection.reconcilePendingEvents(owner)); assertTrue(h.events > 0L)
            val downstream = measured("$count/real_stats_query") { h.db.statsDao().observeTotals(null, null, owner.id).first() }
            emit("$count/stats=$downstream")
            val frozen = measured("$count/freeze_export") { h.exports.create(owner, query(), CollectionFileFormat.MANABOX_CSV, CollectionExportTarget.SAVE) }
            val export = measured("$count/write_export") { h.exports.prepare(owner, frozen) }
            assertEquals(count * 8L / 10, export.rows); assertEquals(collectionCopies, export.copies); assertEquals(0L, export.omittedRows)
            val file = File(h.exportRoot, "$frozen/$frozen.csv")
            val second = Harness("reimport-$count"); second.start()
            try {
                val restored = measured("$count/reimport_receive") { second.receive(file) }
                measured("$count/reimport_prepare") { second.prepare(restored) }
                second.chooseAll(restored, TransferDestination.COLLECTION)
                measured("$count/reimport_apply") { second.apply(restored, TransferDestination.COLLECTION) }
                assertEquals(before, collectionDigest(second))
            } finally { second.close() }
            emit("RESULT distinct=$count collection_entries=${s.appliedEntries} collection_copies=${s.appliedCopies} wishlist_entries=${s.wishlistCompletedEntries} wishlist_copies=${s.wishlistCompletedCopies} none_entries=${s.pendingEntries} none_copies=${s.pendingCopies} digest=$before events=${h.events} remote_calls=${h.remoteCalls} peak_heap_bytes=${sampler.peak.get()} main_probe_max_delay_ms=${sampler.mainDelay.get()} max_heap_bytes=${Runtime.getRuntime().maxMemory()}")
            return sampler.peak.get() to sampler.mainDelay.get()
        } finally { sampler.stop(); sampling.join(); h.close() }
    }
    @Test fun streamedDistinctPipelineWithMixedDestinationsAndLosslessReimport() = runBlocking<Unit>(Dispatchers.IO) {
        val sizes = args.getString("fixtureSizes", "10000,100000")!!.split(',').map(String::toInt)
        var warm: Long? = null
        for (size in sizes) {
            System.gc(); delay(1000)
            val result = pipeline(size, this)
            if (size == 10000) warm = result.first
            if (size == 100000 && warm != null) {
                emit("HEAP_DELTA bytes=${result.first - warm} limit_bytes=${32L * 1024 * 1024}")
                assertTrue("100k heap growth exceeds 32 MiB", result.first - warm <= 32L * 1024 * 1024)
            }
        }
    }
    @Test fun repeatedAndInvalidSourcesStreamCompleteReports() = runBlocking<Unit>(Dispatchers.IO) {
        for (errors in listOf(false, true)) {
            val h = Harness(if (errors) "errors" else "repeated"); h.start()
            try {
                val source = File(h.directory, "source.csv"); writeSource(source, 100000, repeated = true, errors = errors)
                val job = measured("100000/${h.label}/receive") { h.receive(source) }
                measured("100000/${h.label}/prepare") { h.prepare(job) }
                val f = h.summary(job).files.single()
                assertEquals(100000L, f.dataRecords); assertEquals(if (errors) 100000L else 0L, f.invalidRecords)
                assertEquals(if (errors) 0L else 100000L, f.validRecords)
                if (!errors) { assertEquals(1L, h.summary(job).pendingEntries); assertEquals(399995L, h.summary(job).pendingCopies) }
                val output = File(h.directory, "report.csv")
                val writer = AndroidTransferReportWriter(h.db, h.gate, h.observer, context.contentResolver, h.directory) { output.outputStream() }
                assertEquals(100001L, measured("100000/${h.label}/report") { writer.write(job, owner, Uri.parse("content://isolated/report")) })
                var reportedData = 0L
                var reportedErrors = 0L
                var reportedHeaders = 0L
                output.bufferedReader().use { reader ->
                    val fields = mutableListOf<String>(); val field = StringBuilder(); var quoted = false
                    while (true) {
                        val next = reader.read(); if (next < 0) break
                        val character = next.toChar()
                        when {
                            character == '"' -> quoted = !quoted
                            !quoted && character == ',' -> { fields += field.toString(); field.setLength(0) }
                            !quoted && character == '\n' -> {
                                fields += field.toString(); field.setLength(0)
                                assertEquals(17, fields.size)
                                if (fields[5] == "HEADER") reportedHeaders++
                                if (fields[5] == "DATA") {
                                    reportedData++; if (fields[12].isNotEmpty()) reportedErrors++
                                    assertTrue(fields[15].isNotBlank()); assertEquals("COMPLETE", fields[16])
                                }
                                fields.clear()
                            }
                            character != '\r' -> { field.append(character); check(field.length <= 2048) }
                        }
                    }
                    assertFalse(quoted); assertTrue(fields.isEmpty()); assertEquals(0, field.length)
                }
                assertEquals(100000L, reportedData); assertEquals(if (errors) 100000L else 0L, reportedErrors)
                assertEquals(1L, reportedHeaders)
                val lines = output.useLines { it.count() }
                assertTrue(lines >= 100002); emit("RESULT ${h.label} data=${f.dataRecords} valid=${f.validRecords} invalid=${f.invalidRecords} report_lines=$lines report_bytes=${output.length()}")
            } finally { h.close() }
        }
    }
    @Test fun actualManaBox421ExportRoundTrip() = runBlocking<Unit>(Dispatchers.IO) {
        val input = File(context.filesDir, "phase4-manabox.csv")
        assertTrue("Push the original ManaBox fixture to the test package private files directory", input.isFile)
        val digest = MessageDigest.getInstance("SHA-256")
        input.inputStream().use { stream -> val buffer = ByteArray(8192); while (true) { val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        assertEquals(521L, input.length()); assertEquals("62b926d2b8c30863e172a0a5b939ffff19cb2739d7ad48f1469bce08e6e78204", hex(digest))
        val h = Harness("manabox"); h.start()
        try {
            h.db.cardDao().upsertAll(listOf(metadata(0).copy(scryfallId = "77c6fa74-5543-42ac-9ead-0e890b188e99", name = "Lightning Bolt", setCode = "clu", collectorNumber = "141"),
                metadata(1).copy(scryfallId = "e768c957-3a1f-42f5-853a-96942f645df5", name = "Lightning Bolt", setCode = "m11", collectorNumber = "149")))
            val job = h.receive(input); h.prepare(job)
            val entries = h.repository.readPage(job, owner, null).entries
            assertEquals(setOf(tuple("77c6fa74-5543-42ac-9ead-0e890b188e99", false, "LP", "ja", 2), tuple("e768c957-3a1f-42f5-853a-96942f645df5", true, "NM", "en", 3)), entries.map { tuple(it.scryfallId, it.isFoil, it.condition, it.language, it.quantity) }.toSet())
            h.chooseAll(job, TransferDestination.COLLECTION); h.apply(job, TransferDestination.COLLECTION)
            val before = collectionDigest(h)
            val exported = h.exports.create(owner, query(), CollectionFileFormat.MANABOX_CSV, CollectionExportTarget.SAVE)
            assertEquals(2L, h.exports.prepare(owner, exported).rows)
            val second = Harness("manabox-reimport"); second.start()
            try {
                second.db.cardDao().upsertAll(h.db.cardDao().getByIds(entries.map { it.scryfallId }))
                val restored = second.receive(File(h.exportRoot, "$exported/$exported.csv")); second.prepare(restored)
                second.chooseAll(restored, TransferDestination.COLLECTION); second.apply(restored, TransferDestination.COLLECTION)
                assertEquals(before, collectionDigest(second)); emit("RESULT actual_manabox_4.2.1 rows=2 copies=5 digest=$before")
            } finally { second.close() }
        } finally { h.close() }
    }
    private fun byteSource(identity: String, length: Long) = object : TransferInputSource {
        override val identity = identity
        override fun open(): InputStream = object : InputStream() {
            private var remaining = length
            override fun read(): Int = if (remaining-- > 0) 32 else -1
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (remaining <= 0) return -1
                val n = minOf(length.toLong(), remaining).toInt(); buffer.fill(32, offset, offset + n); remaining -= n; return n
            }
        }
    }
    @Test fun actualByteLimitsAreInclusiveAndCountRejectedMembers() = runBlocking<Unit>(Dispatchers.IO) {
        val fifty = 50L * 1024 * 1024
        for (lengths in listOf(listOf(fifty, fifty), listOf(fifty, fifty, 1L), listOf(fifty + 1L))) {
            val h = Harness("bytes-${lengths.size}"); h.start()
            try {
                val job = TransferJobId(uuid())
                val members = measured("bytes/${lengths.joinToString("+")}") {
                    h.files.receive(job, (h.gate.currentSession as TransferSession.Available).generation,
                        lengths.mapIndexed { n, size -> byteSource("member-$n", size) })
                }
                assertEquals(lengths.sum(), h.db.collectionTransferDao().getReceipt(job.value)!!.receivedBytes)
                if (lengths == listOf(fifty, fifty)) assertTrue(members.all { it.phase == "PARSING" && it.bytes == fifty })
                else assertEquals(if (lengths.size == 1) "FILE_TOO_LARGE" else "BATCH_TOO_LARGE", members.last().error)
                emit("RESULT bytes=${lengths.sum()} file_bytes=${members.map { it.bytes }} errors=${members.map { it.error }}")
            } finally { h.close() }
        }
        val h = Harness("eleven-members"); h.start()
        try {
            var opened = 0
            val sources = (0..10).map { n -> object : TransferInputSource { override val identity = "$n"; override fun open(): InputStream { opened++; return byteSource("", 1).open() } } }
            try { h.files.receive(TransferJobId(uuid()), (h.gate.currentSession as TransferSession.Available).generation, sources); fail("Eleven members must reject") }
            catch (failure: TransferStorageException) { assertEquals(TransferError.TOO_MANY_FILES, failure.category) }
            assertEquals(0, opened); emit("RESULT eleven_members rejected_before_open=true")
        } finally { h.close() }
    }
    @Test fun tenMixedFormatsRespectSelectedRecordBudgetAndExclusion() = runBlocking<Unit>(Dispatchers.IO) {
        for (extra in listOf(0, 1)) {
            val h = Harness("mixed-$extra"); h.start()
            try {
                val inputs = (0..9).map { member ->
                    val format = CollectionFileFormat.entries[member % 3]
                    val file = File(h.directory, "member-$member.${format.fileExtension}")
                    file.bufferedWriter().use { writer ->
                        writer.append(CollectionExportFormatter.header(format))
                        repeat(10000 + if (member == 9) extra else 0) { local ->
                            val n = member * 10000 + local
                            writer.append(CollectionExportFormatter.record(CollectionExportEntry(quantity(n).toInt(), name(n), "tst", "Fixture Set", n.toString(), printing(n), "common", foil(n), "NM", "en"), format))
                        }
                    }
                    object : TransferInputSource { override val identity = "member-$member"; override fun open() = file.inputStream() }
                }
                val job = TransferJobId(uuid())
                measured("mixed-$extra/receive") { h.files.receive(job, (h.gate.currentSession as TransferSession.Available).generation, inputs) }
                assertEquals(TransferMutationResult.Accepted, h.repository.bindReceipt(job, owner, (h.gate.currentSession as TransferSession.Available).generation, TransferOrigin.SAF))
                if (extra == 0) h.prepare(job) else {
                    try { h.prepare(job); fail("100001 records cannot publish review") } catch (_: AssertionError) {
                        assertEquals("WAITING_FILE_DECISION", h.db.collectionTransferDao().getJob(job.value, key)!!.phase)
                        assertEquals("SELECTION_LIMIT", h.db.collectionTransferDao().getJob(job.value, key)!!.error)
                    }
                    val s = h.summary(job)
                    val last = s.files.single { it.order == 9 }
                    assertEquals(TransferMutationResult.Accepted, h.repository.selectFile(job, owner, last.id, s.generation, false))
                    h.prepare(job)
                }
                val s = h.summary(job)
                assertEquals(if (extra == 0) 100000L else 90000L, s.pendingEntries)
                assertEquals((0 until if (extra == 0) 100000 else 90000).sumOf(::quantity), s.pendingCopies)
                assertEquals(100000L + extra, s.files.sumOf { it.dataRecords })
                assertEquals(10, s.files.size)
                assertEquals(CollectionFileFormat.entries.toSet(), s.files.mapNotNull { it.format }.toSet())
                assertEquals(if (extra == 0) 100000L else 90000L, h.count("SELECT SUM(source_records) FROM collection_import_provenance WHERE job_id='${job.value}'"))
                emit("RESULT mixed_formats=10 original_records=${100000 + extra} selected_entries=${s.pendingEntries} selected_copies=${s.pendingCopies} excluded_sources=${s.files.count { !it.selected }}")
            } finally { h.close() }
        }
    }
    @Test fun abruptProcessDeathRecoversIsolatedApplyExactlyOnce() = runBlocking<Unit>(Dispatchers.IO) {
        org.junit.Assume.assumeTrue("Explicit external death controller required", args.containsKey("deathStage"))
        require(android.os.Build.HARDWARE in setOf("ranchu", "goldfish")) { "Process death is restricted to isolated emulators" }
        val point = args.getString("deathPoint", "AFTER_INCREMENT")!!
        val stage = args.getString("deathStage", "verify")!!
        require(stage in setOf("kill", "verify", "reset"))
        require(point in setOf("RECEIPT", "AFTER_RENAME", "PARSE", "BEFORE_TRANSACTION", "AFTER_INCREMENT", "AFTER_COMMIT", "BEFORE_EVENT"))
        val persistent = "phase4-death-$point"
        val h = Harness("death-$point", persistent); h.start()
        try {
            if (stage == "reset") {
                assertEquals(0L, h.count("SELECT COUNT(*) FROM collection_transfer_jobs WHERE owner_key != '$key'"))
                emit("RESET isolated_death_fixture=$point")
                return@runBlocking
            }
            val jobFile = File(h.directory, "job-id")
            if (stage == "kill") {
                val source = File(h.directory, "source.csv"); writeSource(source, 1000)
                val job = if (point == "AFTER_RENAME") {
                    val received = TransferJobId(uuid()); jobFile.writeText(received.value)
                    val crashingFiles = AndroidCollectionTransferFileStore(h.directory, h.db.collectionTransferDao(),
                        { h.gate.currentSession }, checkpoint = { current ->
                            if (current == TransferReceiptCheckpoint.AFTER_RENAME) {
                                emit("DEATH checkpoint=AFTER_RENAME pid=${android.os.Process.myPid()} db=$persistent")
                                android.os.Process.killProcess(android.os.Process.myPid())
                                error("Process death failed")
                            }
                        })
                    crashingFiles.receive(received, (h.gate.currentSession as TransferSession.Available).generation,
                        listOf(object : TransferInputSource { override val identity = "fixture.csv"; override fun open() = source.inputStream() }))
                    error("Rename checkpoint was not reached")
                } else h.receive(source)
                jobFile.writeText(job.value)
                fun die() { emit("DEATH checkpoint=$point pid=${android.os.Process.myPid()} db=$persistent job=${job.value}"); android.os.Process.killProcess(android.os.Process.myPid()); error("Process death failed") }
                if (point == "RECEIPT") die()
                if (point == "PARSE") {
                    coroutineScope {
                        launch(Dispatchers.IO) {
                            while (h.count("SELECT COUNT(*) FROM collection_import_rows WHERE job_id='${job.value}'") < 200) delay(5)
                            die()
                        }
                        h.prepare(job)
                    }
                } else h.prepare(job)
                h.chooseAll(job, TransferDestination.COLLECTION)
                val action = h.command(job, TransferDestination.COLLECTION)
                File(h.directory, "action-id").writeText(action.value)
                if (point == "BEFORE_TRANSACTION") die()
                val executor = RoomTransferCollectionExecutor(h.db, h.gate, System::currentTimeMillis,
                    { if (point == "BEFORE_EVENT") die() },
                    checkpoint = { current, _ -> if (current.name == point) die() }, matchesObservedOwner = h.observer::matchesObserved)
                repeat(10) { executor.runSlice(action, owner) }
                error("Configured checkpoint was not reached")
            } else {
                assertTrue("Run deathStage=kill on the isolated emulator first", jobFile.isFile)
                val job = TransferJobId(jobFile.readText())
                val before = h.count("SELECT COALESCE(SUM(quantity),0) FROM user_card_collection")
                if (point in setOf("AFTER_COMMIT", "BEFORE_EVENT")) assertTrue(before > 0L) else assertEquals(0L, before)
                if (point == "AFTER_RENAME") {
                    val recovered = h.files.recoverReceipt(job)
                    assertEquals(1, recovered.size); assertEquals("PARSING", recovered.single().phase)
                    assertEquals(TransferMutationResult.Accepted, h.repository.bindReceipt(job, owner,
                        (h.gate.currentSession as TransferSession.Available).generation, TransferOrigin.SAF))
                }
                if (point in setOf("RECEIPT", "AFTER_RENAME", "PARSE")) { h.prepare(job); h.chooseAll(job, TransferDestination.COLLECTION); h.command(job, TransferDestination.COLLECTION) }
                repeat(10) { RunTransferWork(h.coordinator).run(job) }
                assertEquals(1000L, h.count("SELECT COUNT(*) FROM user_card_collection"))
                assertEquals(3997L, h.count("SELECT SUM(quantity) FROM user_card_collection"))
                assertEquals(expected(1000, all = true), collectionDigest(h))
                assertEquals(0, h.collection.reconcilePendingEvents(owner))
                emit("RESULT actual_process_death=$point before_copies=$before after_entries=1000 after_copies=3997 digest=${collectionDigest(h)} recovered_events=${h.events}")
            }
        } finally { h.close(remove = stage != "kill") }
    }
}
