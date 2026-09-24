package com.mmg.manahub.core.data.remote.trades

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.remote.dto.TradeItemDto
import com.mmg.manahub.core.data.remote.dto.TradeItemRequestDto
import com.mmg.manahub.core.data.remote.dto.TradeProposalDto
import com.mmg.manahub.core.model.parseTradeError
import com.mmg.manahub.core.model.ReviewFlags
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * Remote data source for trade proposals (CRUD + lifecycle actions).
 *
 * All calls delegate to [SupabaseClient] PostgREST and run on [DispatcherProvider.io]
 * (KMP-safe replacement for `Dispatchers.IO`). Trade-specific [RestException]s are mapped to
 * domain [com.mmg.manahub.core.model.TradeError] via [parseTradeError].
 *
 * @param supabaseClient      The Supabase client for PostgREST calls.
 * @param dispatcherProvider   Platform dispatcher abstraction.
 */
class TradesRemoteDataSource(
    private val supabaseClient: SupabaseClient,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider(),
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Every proposal where [userId] is a participant, optionally only the thread rooted at
     * [rootProposalId], drained page by page in `(created_at, id)` order. Fails unless every page
     * was fetched, so a truncated list can never replace the cached one.
     */
    suspend fun fetchProposals(userId: String, rootProposalId: String? = null): Result<List<TradeProposalDto>> =
        drainByCreatedAt(
            id = { it.id },
            createdAt = { it.createdAt },
        ) { after, limit -> fetchProposalsPage(userId, rootProposalId, after, limit) }.toResult()

    /** One `(created_at, id)` keyset page of [fetchProposals]. */
    suspend fun fetchProposalsPage(
        userId: String,
        rootProposalId: String?,
        after: CreatedAtCursor?,
        limit: Int,
    ): Result<List<TradeProposalDto>> = safeCall {
        supabaseClient.postgrest["trade_proposals"]
            .select {
                filter {
                    if (rootProposalId != null) eq("root_proposal_id", rootProposalId)
                    // Two top-level `or` groups would overwrite each other, so they are nested under one `and`.
                    and {
                        or {
                            eq("proposer_id", userId)
                            eq("receiver_id", userId)
                        }
                        if (after != null) afterCreatedAt(after)
                    }
                }
                createdAtPage(limit)
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
        supabaseClient.postgrest.rpc("create_proposal", params).decodeAs<String>()
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
        reviewFlags: ReviewFlags,
    ): Result<String> = safeCall {
        val params = buildJsonObject {
            put("p_parent_proposal_id", parentProposalId)
            put("p_items", json.encodeToJsonElement(items))
            put("p_new_review_flags", buildJsonObject {
                put("from_proposer", reviewFlags.fromProposer)
                put("from_receiver", reviewFlags.fromReceiver)
            })
        }
        supabaseClient.postgrest.rpc("counter_proposal", params).decodeAs<String>()
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
        dispatcherProvider.remoteResult(block).recoverCatching { e ->
            // Trade RPC errors carry a typed token in the message; everything else passes through.
            throw if (e is RestException) parseTradeError(e.message) else e
        }
}
