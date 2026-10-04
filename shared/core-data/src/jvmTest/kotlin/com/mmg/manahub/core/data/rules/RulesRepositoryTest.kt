package com.mmg.manahub.core.data.rules

import com.mmg.manahub.core.common.*
import com.mmg.manahub.core.domain.rules.*
import com.mmg.manahub.core.model.rules.*
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlin.test.*

class RulesRepositoryTest {
    private fun baseline(): RulesRawSnapshot {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }.first { File(it, "app/src/main/assets/rules/baseline.txt").exists() }
        val bytes = File(root, "app/src/main/assets/rules/baseline.txt").readBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return RulesRawSnapshot(RulesManifest("https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt", "2026-09-25", hash, 1, 4066, bytes.size), bytes.toString(Charsets.UTF_8))
    }
    private class Store(val packaged: RulesRawSnapshot) : RulesSnapshotStore {
        var current: RulesRawSnapshot? = null
        var brokenRead = false
        var brokenWrite = false
        var writes = 0
        val old = mutableMapOf<String, RulesRawSnapshot>()
        override suspend fun baseline() = packaged
        override suspend fun active(): RulesRawSnapshot? { if (brokenRead) error("private storage data"); return current }
        override suspend fun edition(id: String) = old[id]
        override suspend fun install(snapshot: RulesRawSnapshot) { if (brokenWrite) error("private file path"); writes++; current = snapshot; old[snapshot.manifest.sha256] = snapshot }
    }
    private class Reporter : CrashReporter {
        val exceptions = mutableListOf<Throwable>()
        override fun log(message: String) = Unit
        override fun setCustomKey(key: String, value: String) = Unit
        override fun recordException(throwable: Throwable) { exceptions += throwable }
    }
    private fun repository(store: Store, reporter: Reporter = Reporter(), source: suspend () -> RulesRawSnapshot = { error("offline") }): RulesRepositoryImpl =
        RulesRepositoryImpl(store, object : RulesOfficialSource { override suspend fun download() = source() }, DispatcherProvider(), reporter)
    private fun candidate(snapshot: RulesRawSnapshot, text: String): RulesRawSnapshot {
        val bytes = text.toByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return RulesRawSnapshot(snapshot.manifest.copy(sha256 = hash, nodeCount = 0, byteCount = bytes.size), text)
    }

    @Test fun invalidActiveFallsBackButPinnedEditionDoesNot() = runBlocking {
        val baseline = baseline(); val store = Store(baseline); store.brokenRead = true
        val reporter = Reporter(); val repository = repository(store, reporter)
        assertEquals(baseline.manifest.sha256, repository.load().id)
        assertFails { repository.load("f".repeat(64)) }
        assertEquals("rules_snapshot_invalid", reporter.exceptions.single().message)
        assertNull(reporter.exceptions.single().cause)
    }
    @Test fun malformedAndTruncatedCandidatesPreserveActive() = runBlocking {
        val baseline = baseline()
        for (text in listOf(baseline.text.replaceFirst("702.5a ", "702.xa "), baseline.text.substringBefore("Astartes,"), baseline.text.substringBeforeLast("Nickelodeon, Teenage Mutant Ninja Turtles").trimEnd())) {
            val store = Store(baseline)
            val repository = repository(store, source = { candidate(baseline, text) })
            assertEquals(RulesUpdateResult.Failed(RulesFailure.INVALID_SOURCE), repository.update())
            assertEquals(0, store.writes)
            assertEquals(baseline.manifest.sha256, repository.load().id)
        }
    }
    @Test fun storageFailureAndCancellationPreservePreviousEdition() = runBlocking {
        val baseline = baseline(); val store = Store(baseline); store.brokenWrite = true
        val changed = candidate(baseline, baseline.text.replaceFirst("ultimate authority", "ultimate reference authority"))
        val repository = repository(store, source = { changed })
        assertEquals(RulesUpdateResult.Failed(RulesFailure.STORAGE), repository.update())
        assertEquals(baseline.manifest.sha256, repository.load().id)
        val reporter = Reporter(); val entered = CompletableDeferred<Unit>(); val gate = CompletableDeferred<Unit>()
        val cancelledRepository = repository(store, reporter) { entered.complete(Unit); gate.await(); changed }
        val request = launch { cancelledRepository.update() }; entered.await(); request.cancelAndJoin()
        assertEquals(0, store.writes); assertTrue(reporter.exceptions.isEmpty())
        assertEquals(baseline.manifest.sha256, cancelledRepository.load().id)
    }
    @Test fun concurrentUpdatesShareOneCandidateAndPinnedReaderStaysOld() = runBlocking {
        val baseline = baseline(); val store = Store(baseline)
        val changed = candidate(baseline, baseline.text.replaceFirst("ultimate authority", "ultimate reference authority"))
        val entered = CompletableDeferred<Unit>(); val gate = CompletableDeferred<Unit>(); var calls = 0
        val repository = repository(store, source = { calls++; entered.complete(Unit); gate.await(); changed })
        val old = repository.load()
        val first = async { repository.update() }; entered.await()
        val second = async { repository.update() }; yield(); gate.complete(Unit)
        assertEquals(first.await(), second.await()); assertEquals(1, calls); assertEquals(1, store.writes)
        assertEquals(changed.manifest.sha256, repository.load().id)
        assertEquals(old, repository.load(old.id))
        assertEquals(changed.manifest.sha256, repository(store).load().id)
    }
    @Test fun identicalEditionNeverWrites() = runBlocking {
        val store = Store(baseline())
        val repository = repository(store, source = { store.packaged })
        assertEquals(RulesUpdateResult.Unchanged, repository.update())
        assertEquals(0, store.writes)
    }
}
