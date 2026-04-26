package com.mmg.manahub.core.data.local

import androidx.room.*
import com.mmg.manahub.core.data.local.converter.RoomConverters
import com.mmg.manahub.core.data.local.dao.*
import com.mmg.manahub.core.data.local.entity.*
import com.mmg.manahub.core.data.local.paging.RemoteKeyDao
import com.mmg.manahub.core.data.local.paging.RemoteKeyEntity
import com.mmg.manahub.feature.draft.data.local.DraftSetDao
import com.mmg.manahub.feature.draft.data.local.DraftSetEntity
import com.mmg.manahub.feature.friends.data.local.dao.FriendDao
import com.mmg.manahub.feature.friends.data.local.entity.FriendEntity
import com.mmg.manahub.feature.friends.data.local.entity.FriendRequestEntity
import com.mmg.manahub.feature.news.data.local.ContentSourceEntity
import com.mmg.manahub.feature.news.data.local.NewsArticleEntity
import com.mmg.manahub.feature.news.data.local.NewsDao
import com.mmg.manahub.feature.news.data.local.NewsVideoEntity

@Database(
    entities = [
        CardEntity::class,
        UserCardCollectionEntity::class,
        DeckEntity::class,
        DeckCardEntity::class,
        RemoteKeyEntity::class,
        ManaSymbolEntity::class,
        GameSessionEntity::class,
        PlayerSessionEntity::class,
        SurveyAnswerEntity::class,
        TournamentEntity::class,
        TournamentPlayerEntity::class,
        TournamentMatchEntity::class,
        NewsArticleEntity::class,
        NewsVideoEntity::class,
        ContentSourceEntity::class,
        DraftSetEntity::class,
        FriendEntity::class,
        FriendRequestEntity::class,
    ],
    version = 27,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class MtgDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun userCardCollectionDao(): UserCardCollectionDao
    abstract fun deckDao(): DeckDao
    abstract fun statsDao(): StatsDao
    abstract fun manaSymbolDao(): ManaSymbolDao
    abstract fun gameSessionDao(): GameSessionDao
    abstract fun surveyAnswerDao(): SurveyAnswerDao
    abstract fun tournamentDao(): TournamentDao
    abstract fun newsDao(): NewsDao
    abstract fun draftSetDao(): DraftSetDao
    abstract fun friendDao(): FriendDao
    abstract fun remoteKeyDao(): RemoteKeyDao
}
