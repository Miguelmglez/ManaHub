package core.data.local

import androidx.room.*
import core.data.local.converter.RoomConverters
import core.data.local.dao.*
import core.data.local.entity.*

@Database(
    entities = [
        CardEntity::class,
        UserCardEntity::class,
        DeckEntity::class,
        DeckCardCrossRef::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class MtgDatabase : RoomDatabase() {
    abstract fun cardDao():     CardDao
    abstract fun userCardDao(): UserCardDao
    abstract fun deckDao():     DeckDao
    abstract fun statsDao():    StatsDao
}
