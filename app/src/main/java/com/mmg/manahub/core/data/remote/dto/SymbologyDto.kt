package com.mmg.manahub.core.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SymbologyListDto(
    @SerializedName("data") val data: List<CardSymbolDto>,
)

data class CardSymbolDto(
    @SerializedName("symbol")      val symbol:      String,
    @SerializedName("description") val description: String,
    @SerializedName("svg_uri")     val svgUri:      String,
    @SerializedName("hybrid")      val hybrid:      Boolean = false,
    @SerializedName("phyrexian")   val phyrexian:   Boolean = false,
    @SerializedName("funny")       val funny:       Boolean = false,
)
