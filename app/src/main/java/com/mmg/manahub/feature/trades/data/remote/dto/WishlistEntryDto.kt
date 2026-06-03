package com.mmg.manahub.feature.trades.data.remote.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class WishlistEntryDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("card_id") val cardId: String,
    // @EncodeDefault forces quantity to always be included in the serialized JSON even
    // when its value equals the default (1). Without this, kotlinx.serialization with
    // encodeDefaults=false (the Supabase SDK default) omits the field. PostgREST then
    // interprets the field listed in the ?columns= param but absent from the body as
    // NULL, violating the NOT NULL constraint and returning 400.
    @EncodeDefault
    val quantity: Int = 1,
    @SerialName("match_any_variant") val matchAnyVariant: Boolean,
    @SerialName("is_foil") val isFoil: Boolean? = null,
    val condition: String? = null,
    val language: String? = null,
    @SerialName("created_at") val createdAt: String,
)
