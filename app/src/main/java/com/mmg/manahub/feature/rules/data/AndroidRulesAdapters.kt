package com.mmg.manahub.feature.rules.data

import android.content.Context
import android.util.AtomicFile
import com.mmg.manahub.core.domain.rules.RulesOfficialSource
import com.mmg.manahub.core.domain.rules.RulesSnapshotStore
import com.mmg.manahub.core.model.rules.RulesManifest
import com.mmg.manahub.core.model.rules.RulesRawSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.EventListener
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal const val RULES_BYTE_LIMIT = 4 * 1024 * 1024

internal fun rulesHash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
internal fun decodeRules(bytes: ByteArray): String = try {
    Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
} catch (_: java.nio.charset.CharacterCodingException) { throw IllegalArgumentException("Invalid rules encoding") }
internal fun validatedRulesUrl(url: String): String = url.toHttpUrl().let {
    require(it.scheme == "https" && it.host in setOf("magic.wizards.com", "media.wizards.com") && it.username.isEmpty() && it.password.isEmpty() && it.port == 443)
    it.toString()
}

internal fun rulesManifest(json: String): RulesManifest = JSONObject(json).let {
    RulesManifest(it.getString("sourceUrl"), it.getString("effectiveDate"), it.getString("sha256"), it.getInt("schemaVersion"), it.getInt("nodeCount"), it.getInt("byteCount"))
}

private fun RulesManifest.json(): String = JSONObject().put("sourceUrl", sourceUrl).put("effectiveDate", effectiveDate).put("sha256", sha256).put("schemaVersion", schemaVersion).put("nodeCount", nodeCount).put("byteCount", byteCount).toString()

class AndroidRulesSnapshotStore(private val context: Context) : RulesSnapshotStore {
    private val root = File(context.noBackupFilesDir, "rules-editions")
    private fun validId(id: String) { require(id.matches(Regex("[a-f0-9]{64}"))) }
    private fun read(bytes: ByteArray, manifest: RulesManifest): RulesRawSnapshot {
        require(bytes.size == manifest.byteCount && bytes.size <= RULES_BYTE_LIMIT && rulesHash(bytes) == manifest.sha256 && manifest.schemaVersion == 1)
        return RulesRawSnapshot(manifest, decodeRules(bytes))
    }
    override suspend fun baseline(): RulesRawSnapshot = withContext(Dispatchers.IO) {
        val manifest = rulesManifest(context.assets.open("rules/baseline-manifest.json").bufferedReader().use { it.readText() })
        read(context.assets.open("rules/baseline.txt").use { it.readBytes() }, manifest)
    }
    override suspend fun active(): RulesRawSnapshot? = withContext(Dispatchers.IO) {
        if (!root.exists()) return@withContext null
        val pointer = AtomicFile(File(root, "active"))
        val loaded = if (!pointer.baseFile.exists() && !File(root, "active.bak").exists()) null
        else { require(pointer.baseFile.length() <= 64); edition(pointer.readFully().toString(Charsets.UTF_8)) }
        root.listFiles()?.filter { it.name.matches(Regex("[a-f0-9]{64}\\.(txt|json)\\.(new|bak)")) || it.name == "active.new" }?.forEach { it.delete() }
        loaded
    }
    override suspend fun edition(id: String): RulesRawSnapshot? = withContext(Dispatchers.IO) {
        validId(id)
        val file = File(root, "$id.txt")
        val manifest = File(root, "$id.json")
        if (!file.exists() || !manifest.exists()) null
        else { require(file.length() <= RULES_BYTE_LIMIT && manifest.length() <= 8192); val metadata = rulesManifest(manifest.readText()); require(metadata.sha256 == id); read(file.readBytes(), metadata) }
    }
    private fun atomicWrite(file: File, bytes: ByteArray) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) } catch (failure: Exception) { atomic.failWrite(stream); throw failure }
    }
    override suspend fun install(snapshot: RulesRawSnapshot) = withContext(Dispatchers.IO) {
        validId(snapshot.manifest.sha256)
        read(snapshot.text.toByteArray(Charsets.UTF_8), snapshot.manifest)
        check(root.mkdirs() || root.isDirectory)
        val id = snapshot.manifest.sha256
        coroutineContext.ensureActive()
        atomicWrite(File(root, "$id.txt"), snapshot.text.toByteArray(Charsets.UTF_8))
        coroutineContext.ensureActive()
        atomicWrite(File(root, "$id.json"), snapshot.manifest.json().toByteArray(Charsets.UTF_8))
        require(edition(id) != null)
        coroutineContext.ensureActive()
        atomicWrite(File(root, "active"), id.toByteArray(Charsets.UTF_8))
    }
}

class AndroidRulesOfficialSource(client: OkHttpClient) : RulesOfficialSource {
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES).authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
        .eventListener(EventListener.NONE).cache(null)
        .callTimeout(30, TimeUnit.SECONDS).connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .apply { interceptors().clear(); networkInterceptors().clear() }.build()
    private fun validate(url: String): String = validatedRulesUrl(url)
    private suspend fun fetch(url: String, cap: Int): Pair<ByteArray, String?> = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(Request.Builder().url(validate(url)).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(IOException("Rules download failed")) }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (response.code in 300..399) {
                            val location = response.header("Location") ?: error("Missing redirect")
                            val next = response.request.url.resolve(location) ?: error("Invalid redirect")
                            continuation.resume(ByteArray(0) to validate(next.toString()))
                        } else {
                            if (!response.isSuccessful) throw IOException("Rules HTTP failure")
                            val body = response.body ?: error("Missing rules body")
                            require(body.contentLength() <= cap)
                            val bytes = ByteArrayOutputStream()
                            body.byteStream().use { input ->
                                val buffer = ByteArray(8192)
                                while (continuation.isActive) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    require(bytes.size() + count <= cap)
                                    bytes.write(buffer, 0, count)
                                }
                            }
                            if (continuation.isActive) continuation.resume(bytes.toByteArray() to null)
                        }
                    } catch (_: IOException) { if (continuation.isActive) continuation.resumeWithException(IOException("Rules download failed")) }
                    catch (_: Exception) { if (continuation.isActive) continuation.resumeWithException(IllegalArgumentException("Invalid official rules response")) }
                }
            }
        })
    }
    private suspend fun downloadBytes(url: String, cap: Int): ByteArray {
        var next = validate(url)
        repeat(5) {
            val (bytes, redirect) = fetch(next, cap)
            if (redirect == null) return bytes
            next = redirect
        }
        throw IllegalArgumentException("Too many rules redirects")
    }
    override suspend fun download(): RulesRawSnapshot {
        val pageUrl = "https://magic.wizards.com/en/rules"
        val html = decodeRules(downloadBytes(pageUrl, RULES_BYTE_LIMIT))
        val links = Regex("href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).findAll(html).mapNotNull {
            pageUrl.toHttpUrl().resolve(it.groupValues[1].replace("&amp;", "&"))?.toString()
        }.filter { it.substringBefore('?').endsWith(".txt", true) && it.contains("MagicCompRules", true) }.distinct().toList()
        require(links.size == 1)
        val url = validate(links.single())
        val bytes = downloadBytes(url, RULES_BYTE_LIMIT)
        val text = decodeRules(bytes)
        val date = requireNotNull(Regex("These rules are effective as of ([A-Za-z]+) ([0-9]{1,2}), ([0-9]{4})\\.").find(text)?.groupValues) { "Missing rules date" }
        val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        val month = months.indexOf(date[1]) + 1
        require(month > 0)
        val effectiveDate = "${date[3]}-${month.toString().padStart(2, '0')}-${date[2].padStart(2, '0')}"
        return RulesRawSnapshot(RulesManifest(url, effectiveDate, rulesHash(bytes), 1, 0, bytes.size), text)
    }
}
