package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.AuthResult
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UpdateEntryOutcome
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.SuggestedTag
import com.mmg.manahub.core.model.WishlistEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Minimal, hand-written [CardRepository] fake resolving [getCardById] from [byId]. */
private class FakeCardRepository(
    private val byId: Map<String, Card> = emptyMap(),
    private val errorMessage: String? = null,
) : CardRepository {
    override suspend fun searchCardByName(query: String): DataResult<Card> = error("unused")
    override suspend fun searchCards(query: String, page: Int, bypassCache: Boolean): DataResult<List<Card>> = error("unused")
    override suspend fun searchCardsPaginated(query: String, page: Int, bypassCache: Boolean): DataResult<com.mmg.manahub.core.model.PaginatedCards> = error("unused")
    override suspend fun getCardById(scryfallId: String): DataResult<Card> =
        errorMessage?.let { DataResult.Error(it) }
            ?: byId[scryfallId]?.let { DataResult.Success(it) }
            ?: DataResult.Error("not found")
    override suspend fun refreshCardById(scryfallId: String): DataResult<Card> = error("unused")
    override suspend fun backfillMissingOracleIds(limit: Int) = error("unused")
    override suspend fun backfillMissingStrategyTags(limit: Int) = error("unused")
    override suspend fun getCardBySetAndNumber(set: String, number: String): DataResult<Card> = error("unused")
    override suspend fun getCachedEnglishSiblings(pairs: Set<Pair<String, String>>): Map<Pair<String, String>, Card> = error("unused")
    override suspend fun getLanguagePrints(setCode: String, collectorNumber: String): DataResult<List<Card>> = error("unused")
    override suspend fun getPlayableSets(): DataResult<List<com.mmg.manahub.core.model.MagicSet>> = error("unused")
    override suspend fun getCardPrints(name: String): DataResult<List<Card>> = error("unused")
    override suspend fun getCardArtVariants(name: String): DataResult<List<Card>> = error("unused")
    override suspend fun getCardByExactName(name: String): Result<Card> = error("unused")
    override suspend fun searchWithRawQuery(query: String, order: String?, page: Int): List<Card> = error("unused")
    override suspend fun getCardsByIds(scryfallIds: List<String>): List<Card> = error("unused")
    override fun observeCard(scryfallId: String): Flow<Card?> = flowOf(null)
    override suspend fun updatePrices(scryfallId: String, priceUsd: Double?, priceUsdFoil: Double?, priceEur: Double?, priceEurFoil: Double?, updatedAt: Long) = error("unused")
    override suspend fun updatePricesBatch(updates: List<com.mmg.manahub.core.domain.repository.CardPriceUpdate>) = error("unused")
    override suspend fun evictStaleCache() = error("unused")
    override suspend fun updateCardTags(scryfallId: String, tags: List<CardTag>) = error("unused")
    override suspend fun unionCardTags(scryfallId: String, tags: List<CardTag>) = error("unused")
    override suspend fun updateUserTags(scryfallId: String, userTags: List<CardTag>) = error("unused")
    override suspend fun updateSuggestedTags(scryfallId: String, suggestions: List<SuggestedTag>) = error("unused")
    override suspend fun confirmSuggestedTag(scryfallId: String, tag: CardTag) = error("unused")
    override suspend fun dismissSuggestedTag(scryfallId: String, tag: CardTag) = error("unused")
    override suspend fun warmCacheForIds(scryfallIds: List<String>) = error("unused")
}

/**
 * Minimal, hand-written [WishlistRepository] fake. Only [updateEntryWithMerge] is exercised by
 * [UpdateWishlistEntryUseCase] — every other member throws if accidentally invoked.
 */
private class FakeWishlistRepository(
    private val result: Result<UpdateEntryOutcome> = Result.success(UpdateEntryOutcome.UPDATED),
) : WishlistRepository {
    var mergeCallCount = 0
        private set
    var lastCall: MergeCall? = null
        private set

    data class MergeCall(
        val entryId: String,
        val newCardId: String,
        val isFoil: Boolean?,
        val condition: String?,
        val language: String?,
        val quantity: Int,
        val userId: String?,
    )

    override fun observeLocal(): Flow<List<WishlistEntry>> = error("unused")
    override fun observeByScryfallId(scryfallId: String): Flow<List<WishlistEntry>> = error("unused")
    override fun observeVersionsByOracle(oracleId: String, name: String): Flow<List<WishlistEntry>> = error("unused")
    override fun observeUnsyncedCount(): Flow<Int> = error("unused")
    override suspend fun addLocal(entry: WishlistEntry): Result<Unit> = error("unused")
    override suspend fun removeLocal(id: String): Result<Unit> = error("unused")
    override suspend fun updateQuantityLocal(id: String, quantity: Int): Result<Unit> = error("unused")
    override suspend fun getRemote(userId: String): Result<List<WishlistEntry>> = error("unused")
    override suspend fun addRemote(entry: WishlistEntry): Result<Unit> = error("unused")
    override suspend fun removeRemote(id: String): Result<Unit> = error("unused")
    override suspend fun migrateLocalToRemote(userId: String): Result<Int> = error("unused")
    override suspend fun addAndSync(entry: WishlistEntry, userId: String): Result<Unit> = error("unused")
    override suspend fun decrementByScryfallId(scryfallId: String, quantity: Int): Result<Unit> = error("unused")
    override suspend fun decrementByAttributes(scryfallId: String, quantity: Int, isFoil: Boolean, condition: String, language: String): Result<Unit> = error("unused")
    override suspend fun syncFromRemote(userId: String): Result<Unit> = error("unused")

    override suspend fun updateEntryWithMerge(
        entryId: String,
        newCardId: String,
        isFoil: Boolean?,
        condition: String?,
        language: String?,
        quantity: Int,
        userId: String?,
    ): Result<UpdateEntryOutcome> {
        mergeCallCount++
        lastCall = MergeCall(entryId, newCardId, isFoil, condition, language, quantity, userId)
        return result
    }
}

/**
 * Minimal, hand-written [AuthRepository] fake exposing only [sessionState] — every other member
 * (sign-in/out flows, account management) throws if accidentally invoked, since
 * [UpdateWishlistEntryUseCase] only reads the current session synchronously.
 */
private class FakeAuthRepository(initialState: SessionState = SessionState.Unauthenticated) : AuthRepository {
    override val sessionState: StateFlow<SessionState> = MutableStateFlow(initialState)

    override suspend fun signInWithEmail(email: String, password: String): AuthResult<AuthUser> = error("unused")
    override suspend fun signUpWithEmail(email: String, password: String, nickname: String, avatarUrl: String?): AuthResult<AuthUser> = error("unused")
    override suspend fun signInWithGoogle(idToken: String, rawNonce: String): AuthResult<AuthUser> = error("unused")
    override suspend fun signUpWithGoogle(idToken: String, rawNonce: String, nickname: String, avatarUrl: String?): AuthResult<AuthUser> = error("unused")
    override suspend fun signOut(): AuthResult<Unit> = error("unused")
    override suspend fun getCurrentUser(): AuthUser? = error("unused")
    override suspend fun resetPassword(email: String): AuthResult<Unit> = error("unused")
    override suspend fun deleteAccount(): AuthResult<Unit> = error("unused")
    override suspend fun updateNickname(nickname: String): AuthResult<AuthUser> = error("unused")
    override suspend fun linkGoogleIdentity(email: String, password: String, pendingIdToken: String, pendingNonce: String): AuthResult<AuthUser> = error("unused")
    override suspend fun updateAvatarUrl(avatarUrl: String?): AuthResult<Unit> = error("unused")
    override suspend fun resendConfirmationEmail(email: String): AuthResult<Unit> = error("unused")
    override suspend fun updateEmail(newEmail: String, code: String): AuthResult<Unit> = error("unused")
    override suspend fun updatePassword(newPassword: String, currentPassword: String?): AuthResult<Unit> = error("unused")
    override suspend fun unlinkIdentity(identityId: String): AuthResult<Unit> = error("unused")
    override suspend fun linkGoogleIdentityNative(redirectUrl: String): AuthResult<String?> = error("unused")
}

private fun buildCard(scryfallId: String = "scry-new") = Card(
    scryfallId = scryfallId,
    name = "Lightning Bolt",
    printedName = null,
    manaCost = "{R}",
    cmc = 1.0,
    colors = listOf("R"),
    colorIdentity = listOf("R"),
    typeLine = "Instant",
    printedTypeLine = null,
    oracleText = null,
    printedText = null,
    keywords = emptyList(),
    power = null,
    toughness = null,
    loyalty = null,
    setCode = "lea",
    setName = "Test Set",
    collectorNumber = "1",
    rarity = "common",
    releasedAt = "1993-08-05",
    frameEffects = emptyList(),
    promoTypes = emptyList(),
    lang = "en",
    imageNormal = null,
    imageArtCrop = null,
    imageBackNormal = null,
    priceUsd = null,
    priceUsdFoil = null,
    priceEur = null,
    priceEurFoil = null,
    legalityStandard = "legal",
    legalityPioneer = "legal",
    legalityModern = "legal",
    legalityCommander = "legal",
    flavorText = null,
    artist = null,
    scryfallUri = "https://scryfall.com/card/lea/1",
)

private fun authUser(id: String) = AuthUser(
    id = id,
    email = "test@example.com",
    nickname = "tester",
    gameTag = "#TEST01",
    avatarUrl = null,
    provider = "email",
    profileCompleted = true,
)

/**
 * Card Versions & Languages, Phase 1A. Covers [UpdateWishlistEntryUseCase]'s contract: resolve
 * the target card via [CardRepository.getCardById], derive the userId from the CURRENT session
 * (not a caller-supplied argument), then delegate to [WishlistRepository.updateEntryWithMerge].
 */
class UpdateWishlistEntryUseCaseTest {

    @Test
    fun invoke_onSuccess_resolvesCardAndDelegatesWithSessionUserId() = runTest {
        val card = buildCard(scryfallId = "scry-new")
        val wishlistRepository = FakeWishlistRepository(result = Result.success(UpdateEntryOutcome.UPDATED))
        val useCase = UpdateWishlistEntryUseCase(
            repo = wishlistRepository,
            cardRepository = FakeCardRepository(byId = mapOf("scry-new" to card)),
            authRepo = FakeAuthRepository(SessionState.Authenticated(authUser("user-1"))),
        )

        val result = useCase(
            entryId = "entry-1",
            newCardId = "scry-new",
            isFoil = true,
            condition = "LP",
            language = "es",
            quantity = 3,
        )

        assertTrue(result.isSuccess)
        assertEquals(UpdateEntryOutcome.UPDATED, result.getOrNull())
        assertEquals(1, wishlistRepository.mergeCallCount)
        assertEquals(
            FakeWishlistRepository.MergeCall("entry-1", "scry-new", true, "LP", "es", 3, "user-1"),
            wishlistRepository.lastCall,
        )
    }

    @Test
    fun invoke_whenUnauthenticated_passesNullUserId() = runTest {
        val card = buildCard(scryfallId = "scry-new")
        val wishlistRepository = FakeWishlistRepository()
        val useCase = UpdateWishlistEntryUseCase(
            repo = wishlistRepository,
            cardRepository = FakeCardRepository(byId = mapOf("scry-new" to card)),
            authRepo = FakeAuthRepository(SessionState.Unauthenticated),
        )

        useCase(entryId = "entry-1", newCardId = "scry-new", isFoil = null, condition = null, language = null, quantity = 1)

        assertNull(wishlistRepository.lastCall?.userId)
    }

    @Test
    fun invoke_whenCardLookupFails_propagatesFailureAndNeverCallsRepositoryMerge() = runTest {
        val wishlistRepository = FakeWishlistRepository()
        val useCase = UpdateWishlistEntryUseCase(
            repo = wishlistRepository,
            cardRepository = FakeCardRepository(errorMessage = "SCRYFALL_404"),
            authRepo = FakeAuthRepository(SessionState.Authenticated(authUser("user-1"))),
        )

        val result = useCase(
            entryId = "entry-1",
            newCardId = "missing-card",
            isFoil = false,
            condition = "NM",
            language = "en",
            quantity = 1,
        )

        assertTrue(result.isFailure)
        assertEquals("SCRYFALL_404", result.exceptionOrNull()?.message)
        assertEquals(0, wishlistRepository.mergeCallCount)
    }

    @Test
    fun invoke_whenRepositoryReportsEntryNotFound_propagatesThatOutcome() = runTest {
        val card = buildCard(scryfallId = "scry-new")
        val useCase = UpdateWishlistEntryUseCase(
            repo = FakeWishlistRepository(result = Result.success(UpdateEntryOutcome.ENTRY_NOT_FOUND)),
            cardRepository = FakeCardRepository(byId = mapOf("scry-new" to card)),
            authRepo = FakeAuthRepository(SessionState.Authenticated(authUser("user-1"))),
        )

        val result = useCase(
            entryId = "stale-entry",
            newCardId = "scry-new",
            isFoil = false,
            condition = "NM",
            language = "en",
            quantity = 1,
        )

        // The card-lookup step succeeded, so this is a Result.success — the caller (VM) must
        // branch on the WRAPPED UpdateEntryOutcome, not treat every success as "entry updated".
        assertTrue(result.isSuccess)
        assertEquals(UpdateEntryOutcome.ENTRY_NOT_FOUND, result.getOrNull())
    }
}
