package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.data.remote.dto.AcceptInviteRequestDto
import com.mmg.manahub.core.data.remote.dto.AcceptInviteResultDto
import com.mmg.manahub.core.data.remote.dto.FriendCardDto
import com.mmg.manahub.core.data.remote.dto.FriendMatchHistoryDto
import com.mmg.manahub.core.data.remote.dto.FriendStatsDto
import com.mmg.manahub.core.data.remote.dto.FriendshipDto
import com.mmg.manahub.core.data.remote.dto.GetFriendCollectionRequestDto
import com.mmg.manahub.core.data.remote.dto.GetFriendMatchHistoryRequestDto
import com.mmg.manahub.core.data.remote.dto.ReferralCodeDto
import com.mmg.manahub.core.data.remote.dto.SendFriendRequestDto
import com.mmg.manahub.core.data.remote.dto.UpdateFriendshipStatusDto
import com.mmg.manahub.core.data.remote.dto.UpsertCollectionStatsDto
import com.mmg.manahub.core.data.remote.dto.UserSearchResultDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class FriendshipClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {
    suspend fun getFriendships(
        statusFilter: String = "eq.ACCEPTED",
        select: String = "id,user_id_1,user_id_2,status,created_at",
        or: String,
    ): List<FriendshipDto> =
        httpClient.get("${baseUrl}friendships") {
            parameter("status", statusFilter)
            parameter("select", select)
            parameter("or", or)
        }.body()

    suspend fun getPendingRequests(
        userId2Filter: String,
        statusFilter: String = "eq.PENDING",
        select: String = "id,user_id_1,user_id_2,status,created_at",
    ): List<FriendshipDto> =
        httpClient.get("${baseUrl}friendships") {
            parameter("user_id_2", userId2Filter)
            parameter("status", statusFilter)
            parameter("select", select)
        }.body()

    suspend fun getOutgoingPendingRequests(
        userId1Filter: String,
        statusFilter: String = "eq.PENDING",
        select: String = "id,user_id_1,user_id_2,status,created_at",
    ): List<FriendshipDto> =
        httpClient.get("${baseUrl}friendships") {
            parameter("user_id_1", userId1Filter)
            parameter("status", statusFilter)
            parameter("select", select)
        }.body()

    suspend fun searchByGameTag(
        gameTagFilter: String,
        select: String = "id,nickname,game_tag,avatar_url",
    ): List<UserSearchResultDto> =
        httpClient.get("${baseUrl}user_profiles") {
            parameter("game_tag", gameTagFilter)
            parameter("select", select)
        }.body()

    suspend fun sendFriendRequest(
        prefer: String = "return=representation",
        body: SendFriendRequestDto,
    ) {
        httpClient.post("${baseUrl}friendships") {
            header("Prefer", prefer)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    suspend fun updateFriendshipStatus(
        idFilter: String,
        prefer: String = "return=minimal",
        body: UpdateFriendshipStatusDto,
    ) {
        httpClient.patch("${baseUrl}friendships") {
            parameter("id", idFilter)
            header("Prefer", prefer)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    suspend fun deleteFriendship(idFilter: String) {
        httpClient.delete("${baseUrl}friendships") {
            parameter("id", idFilter)
        }
    }

    suspend fun getProfilesByIds(
        idFilter: String,
        select: String = "id,nickname,game_tag,avatar_url",
    ): List<UserSearchResultDto> =
        httpClient.get("${baseUrl}user_profiles") {
            parameter("id", idFilter)
            parameter("select", select)
        }.body()

    suspend fun getMyReferralCode(): List<ReferralCodeDto> =
        httpClient.post("${baseUrl}rpc/get_my_referral_code") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }.body()

    suspend fun acceptInvite(body: AcceptInviteRequestDto): List<AcceptInviteResultDto> =
        httpClient.post("${baseUrl}rpc/accept_invite") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun getFriendCollection(body: GetFriendCollectionRequestDto): List<FriendCardDto> =
        httpClient.post("${baseUrl}rpc/get_friend_collection") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun getFriendStats(
        userIdFilter: String,
        select: String = "user_id,unique_cards,total_cards,total_value_eur,total_value_usd,favourite_color,most_valuable_color,updated_at",
    ): List<FriendStatsDto> =
        httpClient.get("${baseUrl}user_collection_stats") {
            parameter("user_id", userIdFilter)
            parameter("select", select)
        }.body()

    suspend fun upsertCollectionStats(body: UpsertCollectionStatsDto) {
        httpClient.post("${baseUrl}rpc/upsert_collection_stats") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    suspend fun getFriendMatchHistory(body: GetFriendMatchHistoryRequestDto): List<FriendMatchHistoryDto> =
        httpClient.post("${baseUrl}rpc/get_friend_match_history") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
}
