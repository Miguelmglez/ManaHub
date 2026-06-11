package com.mmg.manahub.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SymbologyListDto(
    @SerialName("data") val data: List<CardSymbolDto>,
)

@Serializable
data class CardSymbolDto(
    @SerialName("symbol")      val symbol:      String,
    @SerialName("description") val description: String,
    @SerialName("svg_uri")     val svgUri:      String,
    @SerialName("hybrid")      val hybrid:      Boolean = false,
    @SerialName("phyrexian")   val phyrexian:   Boolean = false,
    @SerialName("funny")       val funny:       Boolean = false,
)
