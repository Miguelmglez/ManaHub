package com.mmg.manahub.core.data.local.converter


import androidx.room.TypeConverter
import com.google.gson.Gson

class RoomConverters {
    private val gson = Gson()
    private val listType = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type

    @TypeConverter
    fun fromStringList(list: List<String>): String = gson.toJson(list)

    @TypeConverter
    fun toStringList(json: String): List<String> =
        gson.fromJson(json, listType) ?: emptyList()
}
