package com.mmg.manahub.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FriendshipDto(
    @SerialName("id") val id: String,
    @SerialName("user_id_1") val userId1: String,
    @SerialName("user_id_2") val userId2: String,
    @SerialName("status") val status: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class UserSearchResultDto(
    @SerialName("id") val id: String,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("game_tag") val gameTag: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class SendFriendRequestDto(
    @SerialName("user_id_1") val userId1: String,
    @SerialName("user_id_2") val userId2: String,
)

@Serializable
data class UpdateFriendshipStatusDto(
    @SerialName("status") val status: String,
)

@Serializable
data class ReferralCodeDto(
    @SerialName("referral_code") val referralCode: String? = null,
)

@Serializable
data class AcceptInviteRequestDto(
    @SerialName("p_referral_code") val pReferralCode: String,
)

@Serializable
data class AcceptInviteResultDto(
    @SerialName("inviter_id") val inviterId: String,
    @SerialName("inviter_nickname") val inviterNickname: String? = null,
)

@Serializable
data class GetFriendCollectionRequestDto(
    @SerialName("p_friend_user_id") val pFriendUserId: String,
    @SerialName("p_list") val pList: String,
    @SerialName("p_query") val pQuery: String = "",
    @SerialName("p_sets") val pSets: List<String>? = null,
    @SerialName("p_rarities") val pRarities: List<String>? = null,
    @SerialName("p_colors") val pColors: List<String>? = null,
    @SerialName("p_foil_only") val pFoilOnly: Boolean? = null,
    @SerialName("p_conditions") val pConditions: List<String>? = null,
    @SerialName("p_languages") val pLanguages: List<String>? = null,
    @SerialName("p_limit") val pLimit: Int = 50,
    @SerialName("p_offset") val pOffset: Int = 0,
)

@Serializable
data class FriendCardDto(
    @SerialName("source_list") val sourceList: String,
    @SerialName("scryfall_id") val scryfallId: String,
    @SerialName("quantity") val quantity: Int,
    @SerialName("is_foil") val isFoil: Boolean,
    @SerialName("condition") val condition: String? = null,
    @SerialName("language") val language: String? = null,
)

@Serializable
data class FriendStatsDto(
    @SerialName("user_id") val userId: String,
    @SerialName("unique_cards") val uniqueCards: Int,
    @SerialName("total_cards") val totalCards: Int,
    @SerialName("total_value_eur") val totalValueEur: Double,
    @SerialName("total_value_usd") val totalValueUsd: Double,
    @SerialName("favourite_color") val favouriteColor: String? = null,
    @SerialName("most_valuable_color") val mostValuableColor: String? = null,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class UpsertCollectionStatsDto(
    @SerialName("p_unique_cards") val pUniqueCards: Int,
    @SerialName("p_total_cards") val pTotalCards: Int,
    @SerialName("p_total_value_eur") val pTotalValueEur: Double,
    @SerialName("p_total_value_usd") val pTotalValueUsd: Double,
    @SerialName("p_favourite_color") val pFavouriteColor: String?,
    @SerialName("p_most_valuable_color") val pMostValuableColor: String?,
)

@Serializable
data class GetFriendMatchHistoryRequestDto(
    @SerialName("p_friend_user_id") val pFriendUserId: String,
)

@Serializable
data class FriendMatchHistoryDto(
    @SerialName("my_wins") val myWins: Int,
    @SerialName("opponent_wins") val opponentWins: Int,
    @SerialName("total_games") val totalGames: Int,
    @SerialName("last_played_at") val lastPlayedAt: String? = null,
)
