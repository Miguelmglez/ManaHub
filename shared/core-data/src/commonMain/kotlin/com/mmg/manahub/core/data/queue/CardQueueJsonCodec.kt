package com.mmg.manahub.core.data.queue

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardAddOrigin
import com.mmg.manahub.core.model.QueuedCard
import com.mmg.manahub.core.model.newQueuedCardId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Encodes/decodes the persisted queue payload. The wire format is the legacy `scanner_queue_v1`
 * JSON array written by the pre-shared-queue ScannerViewModel (org.json), so queues saved by older
 * app versions keep restoring. `oracleId`/`rarity`/`manaCost`/`typeLine`/`colors` are additive
 * optional keys.
 */
object CardQueueJsonCodec {

    /** Decode outcome: the entries that parsed plus how many malformed entries were skipped. */
    data class DecodeResult(val entries: List<QueuedCard>, val skippedEntries: Int)

    /** Serializes [entries] to the persisted JSON array string. */
    fun encode(entries: List<QueuedCard>): String = buildJsonArray {
        entries.forEach { entry -> add(entry.toJson()) }
    }.toString()

    /**
     * Parses a persisted payload. A malformed entry is skipped (counted in
     * [DecodeResult.skippedEntries]) rather than failing the whole restore.
     *
     * @throws IllegalArgumentException when [json] is not a JSON array at all.
     */
    fun decode(json: String): DecodeResult {
        val array = Json.parseToJsonElement(json).jsonArray
        var skipped = 0
        val seenIds = HashSet<String>()
        val entries = array.mapNotNull { element ->
            runCatching { element.jsonObject.toQueuedCard() }
                .onFailure { skipped++ }
                .getOrNull()
                // LazyColumn keys on the id and remove(id) would drop every row sharing it.
                ?.let { entry -> if (seenIds.add(entry.id)) entry else entry.copy(id = newQueuedCardId()) }
        }
        return DecodeResult(entries, skipped)
    }

    private fun QueuedCard.toJson(): JsonObject = buildJsonObject {
        put("scryfallId", card.scryfallId)
        put("name", card.name)
        put("setCode", card.setCode)
        put("setName", card.setName)
        put("lang", card.lang)
        put("priceUsd", card.priceUsd.validPriceOrNull())
        put("priceUsdFoil", card.priceUsdFoil.validPriceOrNull())
        put("priceEur", card.priceEur.validPriceOrNull())
        put("priceEurFoil", card.priceEurFoil.validPriceOrNull())
        put("imageNormal", card.imageNormal)
        put("imageArtCrop", card.imageArtCrop)
        put("collectorNumber", card.collectorNumber)
        put("manaCost", card.manaCost)
        put("typeLine", card.typeLine)
        put("colors", buildJsonArray { card.colors.forEach { add(JsonPrimitive(it)) } })
        put("oracleId", card.oracleId)
        put("rarity", card.rarity)
        put("quantity", quantity)
        put("isFoil", isFoil)
        put("language", language)
        put("condition", condition)
        put("timestamp", timestamp)
        put("id", id)
        put("origin", origin.name)
    }

    private fun JsonObject.toQueuedCard(): QueuedCard {
        val card = Card(
            scryfallId = requireString("scryfallId"),
            name = requireString("name"),
            printedName = null,
            manaCost = optionalString("manaCost"),
            cmc = 0.0,
            colors = optionalStringList("colors"),
            colorIdentity = emptyList(),
            typeLine = optionalString("typeLine").orEmpty(),
            printedTypeLine = null,
            oracleText = null,
            printedText = null,
            keywords = emptyList(),
            power = null,
            toughness = null,
            loyalty = null,
            setCode = requireString("setCode"),
            setName = requireString("setName"),
            collectorNumber = requireString("collectorNumber"),
            rarity = optionalString("rarity").orEmpty(),
            releasedAt = "",
            frameEffects = emptyList(),
            promoTypes = emptyList(),
            lang = requireString("lang"),
            imageNormal = optionalString("imageNormal"),
            imageArtCrop = optionalString("imageArtCrop"),
            imageBackNormal = null,
            priceUsd = optionalDouble("priceUsd"),
            priceUsdFoil = optionalDouble("priceUsdFoil"),
            priceEur = optionalDouble("priceEur"),
            priceEurFoil = optionalDouble("priceEurFoil"),
            legalityStandard = "",
            legalityPioneer = "",
            legalityModern = "",
            legalityCommander = "",
            flavorText = null,
            artist = null,
            scryfallUri = "",
            oracleId = optionalString("oracleId").orEmpty(),
        )
        return QueuedCard(
            card = card,
            quantity = requirePrimitive("quantity").intOrNull?.takeIf { it >= 1 } ?: error("quantity"),
            isFoil = requirePrimitive("isFoil").booleanOrNull ?: error("isFoil"),
            language = requireString("language"),
            condition = requireString("condition"),
            setCode = requireString("setCode"),
            timestamp = requirePrimitive("timestamp").longOrNull ?: error("timestamp"),
            // Entries persisted before the id field existed get a fresh one.
            id = optionalString("id") ?: newQueuedCardId(),
            // Entries persisted before the origin field existed are treated as manual adds.
            origin = optionalString("origin")
                ?.let { raw -> CardAddOrigin.entries.firstOrNull { it.name == raw } }
                ?: CardAddOrigin.MANUAL,
        )
    }

    private fun JsonObject.requirePrimitive(key: String): JsonPrimitive =
        (get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull } ?: error("missing $key")

    private fun JsonObject.requireString(key: String): String {
        val primitive = requirePrimitive(key)
        require(primitive.isString) { "$key is not a string" }
        return primitive.content
    }

    // Legacy org.json optString() turned a JSON null into the literal "null", which older versions
    // then re-persisted as a string.
    private fun JsonObject.optionalString(key: String): String? {
        val element: JsonElement = get(key) ?: return null
        val primitive = element as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.content.takeUnless { it.isBlank() || it == "null" }
    }

    private fun JsonObject.optionalStringList(key: String): List<String> =
        (get(key) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            ?: emptyList()

    private fun JsonObject.optionalDouble(key: String): Double? =
        (get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.doubleOrNull.validPriceOrNull()

    // Non-finite or negative prices are unknown, never a value; encoding NaN would also emit a non-JSON token.
    private fun Double?.validPriceOrNull(): Double? = this?.takeIf { it.isFinite() && it >= 0 }
}
