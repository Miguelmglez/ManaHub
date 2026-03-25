package com.mmg.magicfolder.core.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mmg.magicfolder.core.data.local.converter.RoomConverters
import com.mmg.magicfolder.core.data.local.dao.*
import com.mmg.magicfolder.core.data.local.entity.*

@Database(
    entities = [
        CardEntity::class,
        UserCardEntity::class,
        DeckEntity::class,
        DeckCardCrossRef::class,
        ManaSymbolEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class MtgDatabase : RoomDatabase() {
    abstract fun cardDao():       CardDao
    abstract fun userCardDao():   UserCardDao
    abstract fun deckDao():       DeckDao
    abstract fun statsDao():      StatsDao
    abstract fun manaSymbolDao(): ManaSymbolDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE cards ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS mana_symbols (
                symbol      TEXT    NOT NULL PRIMARY KEY,
                svgUri      TEXT    NOT NULL,
                description TEXT    NOT NULL,
                isHybrid    INTEGER NOT NULL,
                isPhyrexian INTEGER NOT NULL,
                cachedAt    INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
