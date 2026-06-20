package com.mmg.manahub.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mmg.manahub.core.data.local.converter.RoomConverters
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.local.dao.DraftSessionDao
import com.mmg.manahub.core.data.local.dao.GameSessionDao
import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.dao.GamificationStatsDao
import com.mmg.manahub.core.data.local.dao.ManaSymbolDao
import com.mmg.manahub.core.data.local.dao.PlaytestDao
import com.mmg.manahub.core.data.local.dao.StatsDao
import com.mmg.manahub.core.data.local.dao.SurveyAnswerDao
import com.mmg.manahub.core.data.local.dao.SurveyCardImpactDao
import com.mmg.manahub.core.data.local.dao.TournamentDao
import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.entity.AchievementProgressEntity
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.data.local.entity.DeckCardEntity
import com.mmg.manahub.core.data.local.entity.DeckEntity
import com.mmg.manahub.core.data.local.entity.DraftSessionEntity
import com.mmg.manahub.core.data.local.entity.EntitlementEntity
import com.mmg.manahub.core.data.local.entity.GameSessionEntity
import com.mmg.manahub.core.data.local.entity.ManaSymbolEntity
import com.mmg.manahub.core.data.local.entity.PlayerProgressionEntity
import com.mmg.manahub.core.data.local.entity.PlayerSessionEntity
import com.mmg.manahub.core.data.local.entity.PlaytestCardStatEntity
import com.mmg.manahub.core.data.local.entity.PlaytestSessionEntity
import com.mmg.manahub.core.data.local.entity.PlaytestSurveyAnswerEntity
import com.mmg.manahub.core.data.local.entity.QuestInstanceEntity
import com.mmg.manahub.core.data.local.entity.StreakEntity
import com.mmg.manahub.core.data.local.entity.SurveyAnswerEntity
import com.mmg.manahub.core.data.local.entity.SurveyCardImpactEntity
import com.mmg.manahub.core.data.local.entity.TournamentEntity
import com.mmg.manahub.core.data.local.entity.TournamentMatchEntity
import com.mmg.manahub.core.data.local.entity.TournamentPlayerEntity
import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import com.mmg.manahub.core.data.local.entity.XpTransactionEntity
import com.mmg.manahub.core.data.local.paging.RemoteKeyDao
import com.mmg.manahub.core.data.local.paging.RemoteKeyEntity
import com.mmg.manahub.core.data.local.dao.CommunityDeckCacheDao
import com.mmg.manahub.core.data.local.entity.CommunityDeckCacheEntity
import com.mmg.manahub.core.data.local.dao.DraftSetDao
import com.mmg.manahub.core.data.local.entity.DraftSetEntity
import com.mmg.manahub.core.data.local.dao.FriendDao
import com.mmg.manahub.core.data.local.entity.FriendEntity
import com.mmg.manahub.core.data.local.entity.FriendRequestEntity
import com.mmg.manahub.core.data.local.entity.OutgoingFriendRequestEntity
import com.mmg.manahub.core.data.local.entity.ContentSourceEntity
import com.mmg.manahub.core.data.local.entity.NewsArticleEntity
import com.mmg.manahub.core.data.local.dao.NewsDao
import com.mmg.manahub.core.data.local.entity.NewsVideoEntity
import com.mmg.manahub.core.data.local.dao.LocalOpenForTradeDao
import com.mmg.manahub.core.data.local.dao.LocalWishlistDao
import com.mmg.manahub.core.data.local.dao.TradeCollectionSyncDao
import com.mmg.manahub.core.data.local.entity.LocalOpenForTradeEntity
import com.mmg.manahub.core.data.local.entity.LocalWishlistEntity
import com.mmg.manahub.core.data.local.entity.TradeCollectionSyncEntity

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
        SurveyCardImpactEntity::class,
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
        TradeCollectionSyncEntity::class,
        PlaytestSessionEntity::class,
        PlaytestCardStatEntity::class,
        PlaytestSurveyAnswerEntity::class,
        DraftSessionEntity::class,
        // Gamification (ADR-002, v39)
        PlayerProgressionEntity::class,
        XpTransactionEntity::class,
        AchievementProgressEntity::class,
        QuestInstanceEntity::class,
        StreakEntity::class,
        EntitlementEntity::class,
        // Community Decks (v41)
        CommunityDeckCacheEntity::class,
    ],
    version = 41,
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
    abstract fun surveyCardImpactDao(): SurveyCardImpactDao
    abstract fun tournamentDao(): TournamentDao
    abstract fun newsDao(): NewsDao
    abstract fun draftSetDao(): DraftSetDao
    abstract fun friendDao(): FriendDao
    abstract fun remoteKeyDao(): RemoteKeyDao
    abstract fun localWishlistDao(): LocalWishlistDao
    abstract fun localOpenForTradeDao(): LocalOpenForTradeDao
    abstract fun tradeCollectionSyncDao(): TradeCollectionSyncDao
    abstract fun playtestDao(): PlaytestDao
    abstract fun draftSessionDao(): DraftSessionDao
    abstract fun gamificationDao(): GamificationDao

    /**
     * Read-only snapshot DAO over existing tables (collection/games/decks/surveys) for Family-A
     * achievement resolvers + backfill. Adds no entities → no schema change (ADR-002, Phase 1).
     */
    abstract fun gamificationStatsDao(): GamificationStatsDao

    /** Cache of fetched community deck (Archidekt) responses (Community Decks, v41). */
    abstract fun communityDeckCacheDao(): CommunityDeckCacheDao
}
