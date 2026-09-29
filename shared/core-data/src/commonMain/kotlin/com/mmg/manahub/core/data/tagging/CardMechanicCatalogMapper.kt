package com.mmg.manahub.core.data.tagging

import com.mmg.manahub.core.data.remote.dto.CardMechanicCatalogDto
import com.mmg.manahub.core.model.DetectionRule
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.model.TagDictionaryEntry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

fun CardMechanicCatalogDto.toDictionaryEntry(): TagDictionaryEntry {
    require(reviewStatus == "active" && revision > 0 && key.matches(Regex("[a-z][a-z0-9_]*")))
    require(labelEn.isNotBlank())
    val tagCategory = TagCategory.entries.firstOrNull { it.name.equals(category, ignoreCase = true) }
        ?: error("Unsupported mechanic category")
    val patterns = rules["patterns"] as? JsonArray ?: JsonArray(emptyList())
    val detectionRules = patterns.map { pattern ->
        val record = pattern as? JsonObject ?: error("Invalid mechanic rule")
        DetectionRule(
            allOf = record.stringList("allOf"),
            anyOf = record.stringList("anyOf"),
            noneOf = record.stringList("noneOf"),
            typeLineAnyOf = record.stringList("typeLineAnyOf"),
            typeLineNoneOf = record.stringList("typeLineNoneOf"),
            confidence = record["confidence"]?.jsonPrimitive?.floatOrNull,
        )
    }
    return TagDictionaryEntry(
        key = key,
        category = tagCategory,
        labels = mapOf("en" to labelEn),
        rules = detectionRules,
    )
}

private fun JsonObject.stringList(name: String): List<String> =
    ((this[name] ?: JsonArray(emptyList())) as? JsonArray ?: error("Invalid mechanic rule list"))
        .map { (it as? JsonPrimitive)?.content ?: error("Invalid mechanic rule term") }

fun CardMechanicCatalogDto.verifiedQueryOrNull(): String? =
    scryfallQuery?.takeIf { it.isNotBlank() && scryfallQueryVerifiedAt != null }
