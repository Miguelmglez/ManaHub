package com.mmg.manahub.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CardMechanicCatalogDto(
    val key: String,
    val category: String,
    @SerialName("label_en") val labelEn: String,
    val rules: JsonObject = JsonObject(emptyMap()),
    val provenance: JsonObject = JsonObject(emptyMap()),
    val revision: Int,
    @SerialName("scryfall_query") val scryfallQuery: String? = null,
    @SerialName("scryfall_query_verified_at") val scryfallQueryVerifiedAt: String? = null,
    @SerialName("review_status") val reviewStatus: String,
)
