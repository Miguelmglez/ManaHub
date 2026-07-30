package com.mmg.manahub.web

/*
 * KMP web roadmap W0 — runtime smoke spike (THROWAWAY, replaced entirely in W1).
 *
 * Goal: prove supabase-kt, Ktor (Js engine), Coil3, and real `window.localStorage` all work at
 * wasmJs RUNTIME (not just compile). See docs/plans/kmp-migration-plan.md §5 W0 and the findings
 * recorded in memory `project_kmp_spike_findings`.
 *
 * Deliberately does NOT reuse:
 *  - app/src/main/java/com/mmg/manahub/core/di/SupabaseModule.kt (Hilt + Android `Context` +
 *    `SecureSessionManager` — Android-only).
 *  - shared/core-data's ScryfallClient (commonMain, but wired for Android's OkHttp Ktor engine in
 *    the real DI graph; core-data has zero wasmJsMain files yet — that's W3's job). This spike uses
 *    a fresh standalone `HttpClient(Js)` instead, which is enough to prove the Js engine works.
 */

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.mmg.manahub.web.config.WebAppConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

@Serializable
private data class SpikeImageUrisDto(
    val normal: String? = null,
)

@Serializable
private data class SpikeCardDto(
    val name: String,
    @SerialName("image_uris") val imageUris: SpikeImageUrisDto? = null,
)

/** Long-lived scope for the two fire-and-forget spike calls launched from [main]. */
private val spikeScope = MainScope()

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // ── 1) kotlinx-browser: real window.localStorage round-trip ──────────────────────────────────
    // Proves this is real persistent storage, not core-common's in-memory LocalStorageKeyValueStore
    // stub -- verified for real by reloading the page in a browser and re-reading the key.
    val storageKey = "manahub_w0_spike"
    localStorage.setItem(storageKey, "hello-from-wasmjs")
    val persistedValue = localStorage.getItem(storageKey)
    println("[W0 spike] localStorage round-trip (setItem then getItem, same load): $persistedValue")

    // ── 2) supabase-kt: own bare-bones client, Ktor Js engine, no Android sessionManager ─────────
    val supabase = createSupabaseClient(
        supabaseUrl = WebAppConfig.SUPABASE_URL,
        supabaseKey = WebAppConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth)
        install(Postgrest)
        httpEngine = Js.create()
    }

    // ── 3) Plain Ktor GET to Scryfall via a standalone Js-engine HttpClient ───────────────────────
    val scryfallHttpClient = HttpClient(Js) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    var supabaseStatusText by mutableStateOf("pending...")
    var scryfallCardName by mutableStateOf<String?>(null)
    var scryfallCardImageUrl by mutableStateOf<String?>(null)
    var scryfallStatusText by mutableStateOf("pending...")

    spikeScope.launch {
        // One auth method call: anonymous sign-in. Logged to console either way -- this is the
        // W0 hard-gate signal (CLAUDE.md / plan §3): a clean typed failure is an acceptable outcome
        // to record, an unresolved hang or a raw crash is NOT.
        try {
            supabase.auth.signInAnonymously()
            val status = supabase.auth.sessionStatus.value
            // Never log/render the full SessionStatus -- its toString() includes the raw JWT
            // access/refresh tokens (verified during manual browser testing: the full session
            // object -- bearer token included -- showed up both in the browser console and on
            // screen before this fix). Log only the status TYPE, never the session/token material.
            val statusSummary = status::class.simpleName.orEmpty()
            println("[W0 spike] Supabase anonymous sign-in resolved. sessionStatus=$statusSummary")
            supabaseStatusText = "resolved: $statusSummary"
        } catch (e: Throwable) {
            println("[W0 spike] Supabase anonymous sign-in FAILED: ${e::class.simpleName}: ${e.message}")
            supabaseStatusText = "FAILED: ${e::class.simpleName}: ${e.message}"
        }
    }

    spikeScope.launch {
        try {
            val card: SpikeCardDto = scryfallHttpClient
                .get("https://api.scryfall.com/cards/named?fuzzy=black+lotus")
                .body()
            println("[W0 spike] Scryfall card resolved: ${card.name}, image=${card.imageUris?.normal}")
            scryfallCardName = card.name
            scryfallCardImageUrl = card.imageUris?.normal
            scryfallStatusText = "resolved"
        } catch (e: Throwable) {
            println("[W0 spike] Scryfall request FAILED: ${e::class.simpleName}: ${e.message}")
            scryfallStatusText = "FAILED: ${e::class.simpleName}: ${e.message}"
        }
    }

    ComposeViewport(document.body!!) {
        // Explicit Coil3 ImageLoader wiring with the Ktor network fetcher -- coil-network-ktor3's
        // auto-registration story is JVM-ServiceLoader-based and NOT guaranteed on wasmJs, so this
        // spike sets it up explicitly rather than relying on an ambiguous "maybe it auto-registered"
        // result (see project_kmp_spike_findings for the runtime-verified answer).
        setSingletonImageLoaderFactory { context ->
            ImageLoader.Builder(context)
                .components { add(KtorNetworkFetcherFactory()) }
                .build()
        }

        MaterialTheme {
            Surface {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ManaHub Web -- W0 runtime smoke spike")
                    Text("localStorage round-trip: $persistedValue")
                    Text("Supabase anonymous sign-in: $supabaseStatusText")
                    Text("Scryfall fetch: $scryfallStatusText")
                    scryfallCardName?.let { name -> Text("Card: $name") }
                    scryfallCardImageUrl?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = scryfallCardName,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
