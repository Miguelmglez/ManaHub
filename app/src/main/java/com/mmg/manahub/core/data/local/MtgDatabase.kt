package com.mmg.manahub.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mmg.manahub.core.data.local.converter.RoomConverters
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.local.dao.GameSessionDao
import com.mmg.manahub.core.data.local.dao.ManaSymbolDao
import com.mmg.manahub.core.data.local.dao.StatsDao
import com.mmg.manahub.core.data.local.dao.SurveyAnswerDao
import com.mmg.manahub.core.data.local.dao.TournamentDao
import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.data.local.entity.DeckCardEntity
import com.mmg.manahub.core.data.local.entity.DeckEntity
import com.mmg.manahub.core.data.local.entity.GameSessionEntity
import com.mmg.manahub.core.data.local.entity.ManaSymbolEntity
import com.mmg.manahub.core.data.local.entity.PlayerSessionEntity
import com.mmg.manahub.core.data.local.entity.SurveyAnswerEntity
import com.mmg.manahub.core.data.local.entity.TournamentEntity
import com.mmg.manahub.core.data.local.entity.TournamentMatchEntity
import com.mmg.manahub.core.data.local.entity.TournamentPlayerEntity
import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import com.mmg.manahub.core.data.local.paging.RemoteKeyDao
import com.mmg.manahub.core.data.local.paging.RemoteKeyEntity
import com.mmg.manahub.feature.draft.data.local.DraftSetDao
import com.mmg.manahub.feature.draft.data.local.DraftSetEntity
import com.mmg.manahub.feature.friends.data.local.dao.FriendDao
import com.mmg.manahub.feature.friends.data.local.entity.FriendEntity
import com.mmg.manahub.feature.friends.data.local.entity.FriendRequestEntity
import com.mmg.manahub.feature.friends.data.local.entity.OutgoingFriendRequestEntity
import com.mmg.manahub.feature.news.data.local.ContentSourceEntity
import com.mmg.manahub.feature.news.data.local.NewsArticleEntity
import com.mmg.manahub.feature.news.data.local.NewsDao
import com.mmg.manahub.feature.news.data.local.NewsVideoEntity
import com.mmg.manahub.feature.trades.data.local.dao.LocalOpenForTradeDao
import com.mmg.manahub.feature.trades.data.local.dao.LocalWishlistDao
import com.mmg.manahub.feature.trades.data.local.entity.LocalOpenForTradeEntity
import com.mmg.manahub.feature.trades.data.local.entity.LocalWishlistEntity

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
        OutgoingFriendRequestEntity::class,
        LocalWishlistEntity::class,
        LocalOpenForTradeEntity::class,
    ],
    version = 33,
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
    abstract fun localWishlistDao(): LocalWishlistDao
    abstract fun localOpenForTradeDao(): LocalOpenForTradeDao
}
