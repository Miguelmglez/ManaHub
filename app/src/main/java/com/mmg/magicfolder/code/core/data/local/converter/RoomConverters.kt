package com.mmg.magicfolder.code.core.data.local.converter


import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RoomConverters {
    private val gson = Gson()
    private val listType = object : TypeToken>() {}.type

    @TypeConverter
    fun fromStringList(list: List): String = gson.toJson(list)

    @TypeConverter
    fun toStringList(json: String): List =
        gson.fromJson(json, listType) ?: emptyList()
}