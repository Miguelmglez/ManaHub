package com.mmg.manahub.core.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ScryfallSetDto(
    @SerializedName("id")           val id: String,
    @SerializedName("code")         val code: String,
    @SerializedName("name")         val name: String,
    @SerializedName("set_type")     val setType: String,
    @SerializedName("released_at")  val releasedAt: String?,
    @SerializedName("card_count")   val cardCount: Int,
    @SerializedName("icon_svg_uri") val iconSvgUri: String,
    @SerializedName("scryfall_uri") val scryfallUri: String = "",
    @SerializedName("digital")      val digital: Boolean = false,
)

data class ScryfallSetsResponseDto(
    @SerializedName("data") val data: List<ScryfallSetDto>,
)
