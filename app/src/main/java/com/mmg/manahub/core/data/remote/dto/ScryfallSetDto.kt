package com.mmg.manahub.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScryfallSetDto(
    @SerialName("id")           val id: String,
    @SerialName("code")         val code: String,
    @SerialName("name")         val name: String,
    @SerialName("set_type")     val setType: String,
    @SerialName("released_at")  val releasedAt: String? = null,
    @SerialName("card_count")   val cardCount: Int,
    @SerialName("icon_svg_uri") val iconSvgUri: String,
    @SerialName("scryfall_uri") val scryfallUri: String = "",
    @SerialName("digital")      val digital: Boolean = false,
)

@Serializable
data class ScryfallSetsResponseDto(
    @SerialName("data") val data: List<ScryfallSetDto>,
)
