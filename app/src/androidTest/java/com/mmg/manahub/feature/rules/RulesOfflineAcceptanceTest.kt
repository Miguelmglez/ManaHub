package com.mmg.manahub.feature.rules

import android.content.ContextWrapper
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.mmg.manahub.core.data.rules.RulesTextParser
import com.mmg.manahub.core.data.rules.RulesSearchIndex
import com.mmg.manahub.feature.rules.data.AndroidRulesSnapshotStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlin.system.measureNanoTime

class RulesOfflineAcceptanceTest {
    @Test fun fullPackagedCorpusAndWarmQueries() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val snapshot = AndroidRulesSnapshotStore(instrumentation.targetContext).baseline()
        val runtime = Runtime.getRuntime()
        val initialHeap = stabilizedLiveHeap(runtime)
        lateinit var edition: com.mmg.manahub.core.model.rules.RulesEdition
        val parseNs = measureNanoTime { edition = RulesTextParser().parse(snapshot.text, snapshot.manifest) }
        assertEquals(4066, edition.nodes.size)
        lateinit var index: RulesSearchIndex
        val indexNs = measureNanoTime { index = RulesSearchIndex(edition) }
        val retainedHeap = stabilizedLiveHeap(runtime)
        val retainedHeapDelta = retainedHeap - initialHeap
        val coldQueryNs = measureNanoTime { index.search("commander") }
        val queries = listOf("702.5a", "702.5", "deathtouch", "commander", "priority", "state based actions", "trample", "the")
        queries.forEach { index.search(it) }
        val timings = (0 until 80).map { n -> measureNanoTime { val result = index.search(queries[n % queries.size]); assertTrue(result.nodes.size <= 50) } / 1_000_000.0 }.sorted()
        assertEquals("702.5a", index.search("702.5a").nodes.first().id)
        val status = Bundle().apply {
            putString("rules_benchmark", "bytes=${snapshot.manifest.byteCount};nodes=${edition.nodes.size};parse_ms=${parseNs / 1_000_000};index_ms=${indexNs / 1_000_000};post_gc_heap_before_bytes=$initialHeap;post_gc_heap_after_bytes=$retainedHeap;approx_retained_heap_delta_bytes=${retainedHeapDelta.takeIf { it >= 0 } ?: "inconclusive"};cold_query_ms=${coldQueryNs / 1_000_000.0};warm_p95_ms=${timings[75]}")
        }
        instrumentation.sendStatus(0, status)
        assertTrue("Warm query p95 exceeds 150 ms", timings[75] <= 150.0)
        Unit
    }

    private fun stabilizedLiveHeap(runtime: Runtime): Long {
        runtime.gc()
        System.runFinalization()
        runtime.gc()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    @Test fun persistedEditionReopensAndRejectsTamperedHash() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.cacheDir, "rules-test-${UUID.randomUUID()}")
        val isolated = object : ContextWrapper(context) { override fun getNoBackupFilesDir(): File = fixture }
        try {
            val first = AndroidRulesSnapshotStore(isolated)
            val baseline = first.baseline()
            first.install(baseline)
            assertEquals(baseline.manifest, AndroidRulesSnapshotStore(isolated).active()!!.manifest)
            val directory = File(fixture, "rules-editions")
            File(directory, "${"f".repeat(64)}.txt.new").writeText("interrupted candidate")
            File(directory, "active.new").writeText("f".repeat(64))
            assertEquals(baseline.manifest.sha256, AndroidRulesSnapshotStore(isolated).active()!!.manifest.sha256)
            assertFalse(File(directory, "active.new").exists())
            assertFalse(File(directory, "${"f".repeat(64)}.txt.new").exists())
            File(directory, "active.bak").writeText(baseline.manifest.sha256)
            File(directory, "active").writeText("f".repeat(64))
            assertEquals(baseline.manifest.sha256, AndroidRulesSnapshotStore(isolated).active()!!.manifest.sha256)
            val active = File(fixture, "rules-editions/${baseline.manifest.sha256}.txt")
            active.appendText("tampered")
            assertTrue(runCatching { AndroidRulesSnapshotStore(isolated).active() }.isFailure)
            assertEquals(baseline.manifest.sha256, first.baseline().manifest.sha256)
        } finally {
            assertTrue(fixture.canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator))
            fixture.deleteRecursively()
        }
        Unit
    }
}
