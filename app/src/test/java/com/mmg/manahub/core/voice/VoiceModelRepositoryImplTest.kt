package com.mmg.manahub.core.voice

import android.content.Context
import app.cash.turbine.test
import com.mmg.manahub.core.voice.data.VoiceModelRepositoryImpl
import com.mmg.manahub.core.voice.domain.VoiceLanguage
import com.mmg.manahub.core.voice.domain.VoiceModelState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Unit tests for [VoiceModelRepositoryImpl].
 *
 * Uses [MockWebServer] for HTTP responses and [TemporaryFolder] for filesystem isolation.
 * All tests operate on [VoiceLanguage.ENGLISH] — per-language behaviour is identical across
 * all languages (same download + extraction code path, different URL segment and directory).
 *
 * GROUP 1 — Happy path: initial state and transition to Ready
 * GROUP 2 — HTTP error (404) → Error state, modelDir returns null
 * GROUP 3 — Idempotent: download() when already Ready is a no-op
 * GROUP 4 — modelDir() reflects the current state correctly
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceModelRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var context: Context
    private lateinit var filesDir: File
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        filesDir = tempFolder.newFolder("files")
        cacheDir = tempFolder.newFolder("cache")

        context = mockk(relaxed = true)
        every { context.filesDir } returns filesDir
        every { context.cacheDir } returns cacheDir
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Creates a repository pointing at the mock server's base URL. */
    private fun buildRepository(): VoiceModelRepositoryImpl {
        val client = OkHttpClient()
        val baseUrl = server.url("/").toString()
        return VoiceModelRepositoryImpl(context, client, baseUrl)
    }

    /**
     * Builds a minimal valid Vosk model zip for [VoiceLanguage.ENGLISH].
     * Vosk zips include a top-level directory; the impl strips it during extraction.
     */
    private fun buildValidModelZipBuffer(): Buffer {
        val baos = java.io.ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("vosk-model-small-en-us-0.15/"))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("vosk-model-small-en-us-0.15/am/final.mdl"))
            zos.write("fake model content".toByteArray())
            zos.closeEntry()
        }
        return Buffer().write(baos.toByteArray())
    }

    private fun enqueueValidModelResponse() {
        val body = buildValidModelZipBuffer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(body)
                .addHeader("Content-Length", body.size.toString()),
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — Happy path
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given no model on disk then initial state for ENGLISH is NotDownloaded`() = runTest {
        val repo = buildRepository()

        repo.modelStates.test {
            val initial = awaitItem()
            assertTrue(
                "Fresh repository (no model on disk) must start ENGLISH in NotDownloaded",
                initial[VoiceLanguage.ENGLISH] is VoiceModelState.NotDownloaded,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given no model on disk then all languages start as NotDownloaded`() = runTest {
        val repo = buildRepository()

        repo.modelStates.test {
            val initial = awaitItem()
            VoiceLanguage.entries.forEach { lang ->
                assertTrue(
                    "$lang must start in NotDownloaded when no model is on disk",
                    initial[lang] is VoiceModelState.NotDownloaded,
                )
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given valid zip response when download ENGLISH then state transitions to Ready`() = runTest {
        enqueueValidModelResponse()
        val repo = buildRepository()

        repo.modelStates.test {
            awaitItem() // consume initial map

            repo.download(VoiceLanguage.ENGLISH)

            var last: VoiceModelState = VoiceModelState.NotDownloaded
            while (true) {
                val map = awaitItem()
                last = map[VoiceLanguage.ENGLISH] ?: VoiceModelState.NotDownloaded
                if (last is VoiceModelState.Ready || last is VoiceModelState.Error) break
            }

            assertTrue("State must be Ready after successful download", last is VoiceModelState.Ready)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given valid zip with content-length when downloading then progress stays in 0 to 1`() = runTest {
        enqueueValidModelResponse()
        val repo = buildRepository()
        val progressValues = mutableListOf<Float>()

        repo.modelStates.test {
            awaitItem() // initial
            repo.download(VoiceLanguage.ENGLISH)

            var last: VoiceModelState = VoiceModelState.NotDownloaded
            while (true) {
                val map = awaitItem()
                last = map[VoiceLanguage.ENGLISH] ?: VoiceModelState.NotDownloaded
                if (last is VoiceModelState.Downloading) progressValues += last.progress
                if (last is VoiceModelState.Ready || last is VoiceModelState.Error) break
            }
            cancelAndIgnoreRemainingEvents()
        }

        progressValues.forEach { p ->
            assertTrue("Progress $p must be in [0f, 1f]", p in 0f..1f)
        }
    }

    @Test
    fun `given ENGLISH download does not affect SPANISH state`() = runTest {
        enqueueValidModelResponse()
        val repo = buildRepository()

        repo.modelStates.test {
            awaitItem()
            repo.download(VoiceLanguage.ENGLISH)

            var lastMap = emptyMap<VoiceLanguage, VoiceModelState>()
            while (true) {
                lastMap = awaitItem()
                val en = lastMap[VoiceLanguage.ENGLISH] ?: VoiceModelState.NotDownloaded
                if (en is VoiceModelState.Ready || en is VoiceModelState.Error) break
            }

            assertTrue(
                "SPANISH must remain NotDownloaded when only ENGLISH was downloaded",
                lastMap[VoiceLanguage.SPANISH] is VoiceModelState.NotDownloaded,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — HTTP error → Error state
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given 404 response when download called then state transitions to Error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val repo = buildRepository()

        repo.modelStates.test {
            awaitItem()
            repo.download(VoiceLanguage.ENGLISH)

            var last: VoiceModelState = VoiceModelState.NotDownloaded
            while (true) {
                val map = awaitItem()
                last = map[VoiceLanguage.ENGLISH] ?: VoiceModelState.NotDownloaded
                if (last is VoiceModelState.Error || last is VoiceModelState.Ready) break
            }
            assertTrue("State must be Error after HTTP 404", last is VoiceModelState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given 404 response then error message mentions HTTP status code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val repo = buildRepository()

        repo.modelStates.test {
            awaitItem()
            repo.download(VoiceLanguage.ENGLISH)

            var last: VoiceModelState = VoiceModelState.NotDownloaded
            while (true) {
                val map = awaitItem()
                last = map[VoiceLanguage.ENGLISH] ?: VoiceModelState.NotDownloaded
                if (last is VoiceModelState.Error || last is VoiceModelState.Ready) break
            }
            assertTrue("final state must be Error", last is VoiceModelState.Error)
            assertTrue(
                "Error message must contain '404'",
                (last as VoiceModelState.Error).message.contains("404"),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given download fails then modelDir returns null`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val repo = buildRepository()

        repo.modelStates.test {
            awaitItem()
            repo.download(VoiceLanguage.ENGLISH)
            while (true) {
                val map = awaitItem()
                val state = map[VoiceLanguage.ENGLISH] ?: VoiceModelState.NotDownloaded
                if (state is VoiceModelState.Error || state is VoiceModelState.Ready) break
            }
            cancelAndIgnoreRemainingEvents()
        }

        assertNull("modelDir must be null when download failed", repo.modelDir(VoiceLanguage.ENGLISH))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — Idempotent download
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given state is already Ready when download called again then no extra HTTP request is sent`() = runTest {
        enqueueValidModelResponse()
        val repo = buildRepository()

        // First download — reaches Ready
        repo.modelStates.test {
            awaitItem()
            repo.download(VoiceLanguage.ENGLISH)
            while (true) {
                val map = awaitItem()
                val s = map[VoiceLanguage.ENGLISH] ?: VoiceModelState.NotDownloaded
                if (s is VoiceModelState.Ready || s is VoiceModelState.Error) break
            }
            cancelAndIgnoreRemainingEvents()
        }

        val requestsAfterFirst = server.requestCount

        // Second call when already Ready — must be a no-op
        repo.download(VoiceLanguage.ENGLISH)

        assertTrue(
            "download() when already Ready must not send a second HTTP request",
            server.requestCount == requestsAfterFirst,
        )
    }

    @Test
    fun `given state is Ready when download called then state remains Ready`() = runTest {
        enqueueValidModelResponse()
        val repo = buildRepository()

        repo.modelStates.test {
            awaitItem()
            repo.download(VoiceLanguage.ENGLISH)
            while (true) {
                val map = awaitItem()
                val s = map[VoiceLanguage.ENGLISH] ?: VoiceModelState.NotDownloaded
                if (s is VoiceModelState.Ready || s is VoiceModelState.Error) break
            }
            cancelAndIgnoreRemainingEvents()
        }

        // Call download again — must be a no-op; state stays Ready
        repo.download(VoiceLanguage.ENGLISH)

        repo.modelStates.test {
            val state = awaitItem()[VoiceLanguage.ENGLISH]
            assertTrue("State must remain Ready after redundant download()", state is VoiceModelState.Ready)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — modelDir() reflects state
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given state is NotDownloaded then modelDir returns null`() = runTest {
        val repo = buildRepository()
        assertNull("modelDir must be null before any download", repo.modelDir(VoiceLanguage.ENGLISH))
    }

    @Test
    fun `given state is Ready then modelDir returns an existing non-empty directory`() = runTest {
        enqueueValidModelResponse()
        val repo = buildRepository()

        repo.modelStates.test {
            awaitItem()
            repo.download(VoiceLanguage.ENGLISH)
            while (true) {
                val map = awaitItem()
                val s = map[VoiceLanguage.ENGLISH] ?: VoiceModelState.NotDownloaded
                if (s is VoiceModelState.Ready || s is VoiceModelState.Error) break
            }
            cancelAndIgnoreRemainingEvents()
        }

        val dir = repo.modelDir(VoiceLanguage.ENGLISH)
        assertNotNull("modelDir must not be null when state is Ready", dir)
        assertTrue("modelDir must exist on disk", dir!!.exists())
        assertFalse("modelDir must not be empty after extraction", dir.listFiles().isNullOrEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — delete()
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given state is Ready when delete called then state returns to NotDownloaded`() = runTest {
        enqueueValidModelResponse()
        val repo = buildRepository()

        // Reach Ready first
        repo.modelStates.test {
            awaitItem()
            repo.download(VoiceLanguage.ENGLISH)
            while (true) {
                val map = awaitItem()
                val s = map[VoiceLanguage.ENGLISH] ?: VoiceModelState.NotDownloaded
                if (s is VoiceModelState.Ready || s is VoiceModelState.Error) break
            }
            cancelAndIgnoreRemainingEvents()
        }

        repo.delete(VoiceLanguage.ENGLISH)

        repo.modelStates.test {
            val state = awaitItem()[VoiceLanguage.ENGLISH]
            assertTrue("State must be NotDownloaded after delete", state is VoiceModelState.NotDownloaded)
            cancelAndIgnoreRemainingEvents()
        }

        assertNull("modelDir must be null after delete", repo.modelDir(VoiceLanguage.ENGLISH))
    }

    @Test
    fun `given delete ENGLISH then SPANISH state is unaffected`() = runTest {
        enqueueValidModelResponse()
        val repo = buildRepository()

        // Download English, then delete it
        repo.modelStates.test {
            awaitItem()
            repo.download(VoiceLanguage.ENGLISH)
            while (true) {
                val map = awaitItem()
                if (map[VoiceLanguage.ENGLISH] is VoiceModelState.Ready) break
                if (map[VoiceLanguage.ENGLISH] is VoiceModelState.Error) break
            }
            cancelAndIgnoreRemainingEvents()
        }
        repo.delete(VoiceLanguage.ENGLISH)

        repo.modelStates.test {
            val map = awaitItem()
            assertTrue(
                "SPANISH must remain NotDownloaded when only ENGLISH was deleted",
                map[VoiceLanguage.SPANISH] is VoiceModelState.NotDownloaded,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
