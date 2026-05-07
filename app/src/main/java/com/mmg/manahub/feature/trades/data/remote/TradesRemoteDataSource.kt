package com.mmg.manahub.feature.trades.data.remote

import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.feature.trades.data.remote.dto.TradeItemDto
import com.mmg.manahub.feature.trades.data.remote.dto.TradeItemRequestDto
import com.mmg.manahub.feature.trades.data.remote.dto.TradeProposalDto
import com.mmg.manahub.feature.trades.domain.model.parseTradeError
import com.mmg.manahub.feature.trades.domain.repository.ReviewFlags
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TradesRemoteDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchProposals(userId: String): Result<List<TradeProposalDto>> =
        safeCall {
            supabaseClient.postgrest["trade_proposals"]
                .select {
                    filter {
                        or {
                            eq("proposer_id", userId)
                            eq("receiver_id", userId)
                        }
                    }
                }
                .decodeList<TradeProposalDto>()
        }

    suspend fun fetchProposalItems(proposalId: String): Result<List<TradeItemDto>> =
        safeCall {
            supabaseClient.postgrest["trade_items"]
                .select { filter { eq("trade_proposal_id", proposalId) } }
                .decodeList<TradeItemDto>()
        }

    suspend fun createProposal(
        receiverId: String,
        items: List<TradeItemRequestDto>,
        includesReviewFromProposer: Boolean,
        includesReviewFromReceiver: Boolean,
        autoSend: Boolean,
    ): Result<String> = safeCall {
        val params = buildJsonObject {
            put("p_receiver_id", receiverId)
            put("p_items", json.encodeToJsonElement(items))
            put("p_includes_review_from_proposer", includesReviewFromProposer)
            put("p_includes_review_from_receiver", includesReviewFromReceiver)
            put("p_auto_send", autoSend)
        }
        supabaseClient.postgrest.rpc("create_proposal", params).decodeSingle<String>()
    }

    suspend fun editProposal(
        proposalId: String,
        expectedVersion: Int,
        newItems: List<TradeItemRequestDto>,
        reviewFlags: ReviewFlags,
    ): Result<Unit> = safeCall {
        val params = buildJsonObject {
            put("p_trade_proposal_id", proposalId)
            put("p_expected_version", expectedVersion)
            put("p_new_items", json.encodeToJsonElement(newItems))
            put("p_new_review_flags", buildJsonObject {
                put("from_proposer", reviewFlags.fromProposer)
                put("from_receiver", reviewFlags.fromReceiver)
            })
        }
        supabaseClient.postgrest.rpc("edit_proposal", params)
        Unit
    }

    suspend fun sendProposal(proposalId: String): Result<Unit> = safeCall {
        supabaseClient.postgrest.rpc(
            "send_proposal",
            buildJsonObject { put("p_trade_proposal_id", proposalId) }
        )
        Unit
    }

    suspend fun cancelProposal(proposalId: String): Result<Unit> = safeCall {
        supabaseClient.postgrest.rpc(
            "cancel_proposal",
            buildJsonObject { put("p_trade_proposal_id", proposalId) }
        )
        Unit
    }

    suspend fun declineProposal(proposalId: String): Result<Unit> = safeCall {
        supabaseClient.postgrest.rpc(
            "decline_proposal",
            buildJsonObject { put("p_trade_proposal_id", proposalId) }
        )
        Unit
    }

    suspend fun counterProposal(
        parentProposalId: String,
        items: List<TradeItemRequestDto>,
    ): Result<String> = safeCall {
        val params = buildJsonObject {
            put("p_parent_proposal_id", parentProposalId)
            put("p_items", json.encodeToJsonElement(items))
        }
        supabaseClient.postgrest.rpc("counter_proposal", params).decodeSingle<String>()
    }

    suspend fun acceptProposal(proposalId: String): Result<Unit> = safeCall {
        supabaseClient.postgrest.rpc(
            "accept_proposal",
            buildJsonObject { put("p_trade_proposal_id", proposalId) }
        )
        Unit
    }

    suspend fun revokeAcceptance(proposalId: String): Result<Unit> = safeCall {
        supabaseClient.postgrest.rpc(
            "revoke_acceptance",
            buildJsonObject { put("p_trade_proposal_id", proposalId) }
        )
        Unit
    }

    suspend fun markCompleted(proposalId: String): Result<Unit> = safeCall {
        supabaseClient.postgrest.rpc(
            "mark_completed",
            buildJsonObject { put("p_trade_proposal_id", proposalId) }
        )
        Unit
    }

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> =
        withContext(ioDispatcher) {
            try {
                Result.success(block())
            } catch (e: RestException) {
                Result.failure(parseTradeError(e.message))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
