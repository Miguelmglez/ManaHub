package com.mmg.manahub.tools.tagpipeline.catalog

import com.mmg.manahub.core.data.remote.dto.CardDto
import com.mmg.manahub.core.data.tagging.TagDictionary
import com.mmg.manahub.tools.tagpipeline.io.PIPELINE_JSON
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class MechanicCatalogCandidate(
    val key: String,
    val label: String,
    val cardCount: Int,
    val query: String? = null,
    val reason: String? = null,
)

@Serializable
data class MechanicCatalogReport(
    val generatedAt: String,
    val discoveredKeywords: Int,
    val published: List<MechanicCatalogCandidate>,
    val existing: List<MechanicCatalogCandidate>,
    val needsReview: List<MechanicCatalogCandidate>,
    val unknownExpressions: List<UnknownExpression>,
)

@Serializable
data class UnknownExpression(val expression: String, val cardCount: Int, val exampleCard: String)

/** Publishes only exact Scryfall keyword signals; strategic text remains a human review report. */
class MechanicCatalogPublisher(
    private val supabaseUrl: String,
    private val serviceRoleKey: String,
    private val send: (HttpRequest) -> HttpResponse<String> = { request ->
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()
            .send(request, HttpResponse.BodyHandlers.ofString())
    },
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val sleepMillis: (Long) -> Unit = Thread::sleep,
) {
    private var nextScryfallRequestAt = 0L
    private var scryfallCooldownUntil = 0L

    fun publish(cards: Sequence<CardDto>, reportPath: Path, dryRun: Boolean = false): MechanicCatalogReport {
        val knownKeys = TagDictionary.all().mapTo(mutableSetOf()) { it.key }
        val keywords = sortedMapOf<String, MutableSet<String>>()
        val expressions = sortedMapOf<String, MutableSet<String>>()
        cards.forEach { card ->
            card.keywords.forEach { label -> keywords.getOrPut(label) { mutableSetOf() }.add(card.oracleId ?: card.id) }
            card.oracleText?.let { oracleText ->
                oracleText.lines().map(String::trim).filter { line ->
                    line.startsWith("Whenever ") || line.startsWith("At the beginning of ") || line.startsWith("If ")
                }.forEach { line ->
                    val expression = line.take(160)
                    expressions.getOrPut(expression) { mutableSetOf() }.add(card.name)
                }
                mechanicLeadIns(oracleText).forEach { expression ->
                    expressions.getOrPut(expression) { mutableSetOf() }.add(card.name)
                }
            }
        }

        val existingKeys = if (dryRun) emptySet() else fetchExistingKeys()
        val published = mutableListOf<MechanicCatalogCandidate>()
        val publishedKeys = mutableSetOf<String>()
        val existing = mutableListOf<MechanicCatalogCandidate>()
        val needsReview = mutableListOf<MechanicCatalogCandidate>()
        keywords.forEach { (label, cardIds) ->
            val key = keywordKey(label)
            val candidate = MechanicCatalogCandidate(key.orEmpty(), label, cardIds.size)
            if (key == null) {
                needsReview += candidate.copy(key = "", reason = "Keyword label cannot form a unique catalog key")
                return@forEach
            }
            if (key in knownKeys || key in existingKeys || key in publishedKeys) {
                existing += candidate
                return@forEach
            }
            val query = verifyQuery(label)
            if (query == null) {
                needsReview += candidate.copy(reason = "No Scryfall query returned the exact keyword")
                return@forEach
            }
            val validated = candidate.copy(query = query)
            if (dryRun || insertNewKeyword(validated)) published += validated else existing += validated
            publishedKeys += key
        }
        val unknown = expressions.entries.asSequence()
            .filter { (expression, names) -> names.size >= 2 || expression.startsWith("Empower ") }
            .sortedByDescending { it.value.size }
            .take(200)
            .map { (expression, names) -> UnknownExpression(expression, names.size, names.first()) }
            .toList()
        val report = MechanicCatalogReport(
            generatedAt = Instant.now().toString(),
            discoveredKeywords = keywords.size,
            published = published,
            existing = existing,
            needsReview = needsReview,
            unknownExpressions = unknown,
        )
        Files.createDirectories(reportPath.toAbsolutePath().parent)
        Files.writeString(reportPath, PIPELINE_JSON.encodeToString(MechanicCatalogReport.serializer(), report))
        return report
    }

    private fun fetchExistingKeys(): Set<String> {
        val keys = mutableSetOf<String>()
        var offset = 0
        do {
            val request = supabaseRequest("/rest/v1/card_mechanic_catalog?select=key&limit=1000&offset=$offset")
                .GET().build()
            val response = send(request)
            check(response.statusCode() in 200..299) { "Catalog read failed: HTTP ${response.statusCode()}" }
            val page = PIPELINE_JSON.decodeFromString(ListSerializer(ExistingKey.serializer()), response.body())
            keys += page.map { it.key }
            offset += page.size
        } while (page.size == 1000)
        return keys
    }

    internal fun verifyQuery(label: String): String? {
        val candidates = listOf("keyword:\"$label\"", "o:\"$label\"")
        candidates.forEach { query ->
            val encoded = URLEncoder.encode(query, Charsets.UTF_8)
            val request = HttpRequest.newBuilder(URI.create("https://api.scryfall.com/cards/search?q=$encoded&unique=cards"))
                .header("User-Agent", "ManaHubMechanicsCatalog/1 (+https://github.com/Miguelmglez/ManaHub)")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET().build()
            val response = sendScryfallQuery(request)
            if (response.statusCode() == 404) return@forEach
            check(response.statusCode() == 200) { "Scryfall query validation failed: HTTP ${response.statusCode()}" }
            val result = PIPELINE_JSON.decodeFromString(SearchResult.serializer(), response.body())
            if (result.data.any { card -> card.keywords.any { it.equals(label, ignoreCase = true) } }) return query
        }
        return null
    }

    private fun sendScryfallQuery(request: HttpRequest): HttpResponse<String> {
        repeat(MAX_SCRYFALL_ATTEMPTS) { attempt ->
            val waitMillis = maxOf(nextScryfallRequestAt, scryfallCooldownUntil) - nowMillis()
            if (waitMillis > 0) sleepMillis(waitMillis)
            val response = send(request)
            val receivedAt = nowMillis()
            nextScryfallRequestAt = receivedAt + SCRYFALL_REQUEST_INTERVAL_MS
            if (response.statusCode() !in RETRYABLE_SCRYFALL_STATUSES) return response
            if (attempt == MAX_SCRYFALL_ATTEMPTS - 1) {
                error("Scryfall query validation exhausted $MAX_SCRYFALL_ATTEMPTS attempts: HTTP ${response.statusCode()}")
            }
            val backoff = (1_000L shl attempt).coerceAtMost(MAX_SCRYFALL_COOLDOWN_MS)
            val retryAfter = response.headers().firstValue("Retry-After").orElse(null)
                ?.let { retryAfterMillis(it, receivedAt) } ?: 0L
            val cooldown = maxOf(backoff, retryAfter)
            check(cooldown <= MAX_SCRYFALL_COOLDOWN_MS) {
                "Scryfall Retry-After exceeds the bounded cooldown: HTTP ${response.statusCode()}"
            }
            scryfallCooldownUntil = maxOf(scryfallCooldownUntil, receivedAt + cooldown)
        }
        error("Scryfall query validation exhausted")
    }

    private fun retryAfterMillis(value: String, receivedAt: Long): Long =
        value.trim().toLongOrNull()?.coerceAtLeast(0)?.coerceAtMost(61)?.times(1_000L)
            ?: runCatching {
                (ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - receivedAt)
                    .coerceAtLeast(0L)
            }.getOrDefault(0L)

    private fun insertNewKeyword(candidate: MechanicCatalogCandidate): Boolean {
        val now = Instant.now().toString()
        val body = PIPELINE_JSON.encodeToString(
            CatalogInsert.serializer(),
            CatalogInsert(
                key = candidate.key,
                labelEn = candidate.label,
                provenance = mapOf("source" to "scryfall_keywords", "card_count" to candidate.cardCount.toString()),
                scryfallQuery = candidate.query ?: error("Unverified query"),
                verifiedAt = now,
            ),
        )
        val request = supabaseRequest("/rest/v1/card_mechanic_catalog?on_conflict=key")
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=ignore-duplicates,return=representation")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build()
        val response = send(request)
        check(response.statusCode() in 200..299) { "Catalog insert failed: HTTP ${response.statusCode()}" }
        val inserted = PIPELINE_JSON.decodeFromString(ListSerializer(ExistingKey.serializer()), response.body())
        return inserted.any { it.key == candidate.key }
    }

    private fun supabaseRequest(path: String): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create(supabaseUrl.trimEnd('/') + path))
            .header("apikey", serviceRoleKey)
            .header("Authorization", "Bearer $serviceRoleKey")
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(30))

    private companion object {
        const val SCRYFALL_REQUEST_INTERVAL_MS = 250L
        const val MAX_SCRYFALL_ATTEMPTS = 5
        const val MAX_SCRYFALL_COOLDOWN_MS = 60_000L
        val RETRYABLE_SCRYFALL_STATUSES = setOf(429, 503)
    }
}

fun keywordKey(label: String): String? {
    val key = label.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    return key.takeIf { it.matches(Regex("[a-z][a-z0-9_]*")) }
}

fun mechanicLeadIns(oracleText: String): Set<String> =
    Regex("\\bEmpower [A-Z][a-z]+\\s+\\d+\\b")
        .findAll(oracleText)
        .map { it.value.substringBeforeLast(' ') }
        .toSet()

@Serializable
private data class ExistingKey(val key: String)

@Serializable
private data class SearchResult(val data: List<SearchCard> = emptyList())

@Serializable
private data class SearchCard(val keywords: List<String> = emptyList())

@Serializable
private data class CatalogInsert(
    val key: String,
    val category: String = "keyword",
    @SerialName("label_en") val labelEn: String,
    val rules: Map<String, String> = emptyMap(),
    val provenance: Map<String, String>,
    val revision: Int = 1,
    @SerialName("scryfall_query") val scryfallQuery: String,
    @SerialName("scryfall_query_verified_at") val verifiedAt: String,
    @SerialName("review_status") val reviewStatus: String = "active",
)
