package com.mmg.manahub.feature.profile

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.SurveyAnswerDao
import com.mmg.manahub.core.domain.model.Achievement
import com.mmg.manahub.core.domain.model.CardValue
import com.mmg.manahub.core.domain.model.CollectionStats
import com.mmg.manahub.core.domain.model.MtgColor
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.domain.model.Rarity
import com.mmg.manahub.core.domain.repository.GameSessionRepository
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.domain.usecase.achievements.AchievementStats
import com.mmg.manahub.core.domain.usecase.achievements.CheckAchievementsUseCase
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import com.mmg.manahub.util.TestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ProfileViewModel].
 *
 * Covers:
 * - savePlayerName with blank string → ignored (no DataStore call)
 * - savePlayerName with valid name → state updated + DataStore called
 * - detectPlayStyle: avgWinTurn 1-7 → AGGRO
 * - detectPlayStyle: favoriteElim=COMMANDER_DAMAGE (outside aggro range) → MIDRANGE
 * - detectPlayStyle: avgWinTurn > 12 → CONTROL
 * - detectPlayStyle: default → BALANCED
 * - winRate computed property: 10 games 4 wins → 0.4f; 0 games → 0.0f
 * - buildAchievementStats with null collectionStats → defaults to 0
 * - computeFavouriteColor: skips COLORLESS entries
 * - computeMostValuableColor: multi-color → "M"; empty identity → "C"; single → that color
 *
 * DESIGN NOTES:
 * - ProfileViewModel has a complex init{} with many flow subscriptions.
 *   We wire all flows via mocks so the ViewModel constructs cleanly.
 * - detectPlayStyle and buildAchievementStats are private helpers; we test
 *   them indirectly by controlling the uiState values they read from.
 * - computeFavouriteColor / computeMostValuableColor are tested via the
 *   collectionStats observation path in init{}.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    // ── Test dispatcher ───────────────────────────────────────────────────────

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val statsRepo                = mockk<StatsRepository>()
    private val gameSessionRepo          = mockk<GameSessionRepository>()
    private val surveyAnswerDao          = mockk<SurveyAnswerDao>()
    private val checkAchievementsUseCase = mockk<CheckAchievementsUseCase>()
    private val userPreferencesDataStore = mockk<UserPreferencesDataStore>(relaxed = true)
    private val authRepository           = mockk<AuthRepository>(relaxed = true)
    private val friendRepository         = mockk<FriendRepository>(relaxed = true)

    // Mutable state flows used to drive ViewModel state changes in tests
    private val playerNameFlow    = MutableStateFlow("Player 1")
    private val avatarUrlFlow     = MutableStateFlow<String?>(null)
    private val preferencesFlow   = MutableStateFlow(TestFixtures.buildPreferences())
    private val sessionStateFlow  = MutableStateFlow<SessionState>(SessionState.Unauthenticated)

    private lateinit var viewModel: ProfileViewModel

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildEmptyCollectionStats(
        byColor:  Map<MtgColor, Int> = emptyMap(),
        byRarity: Map<Rarity, Int>   = emptyMap(),
        mostValuableCards: List<CardValue> = emptyList(),
    ) = CollectionStats(
        totalCards        = 0,
        uniqueCards       = 0,
        totalDecks        = 0,
        totalValueUsd     = 0.0,
        totalValueEur     = 0.0,
        mostValuableCards = mostValuableCards,
        byColor           = byColor,
        byRarity          = byRarity,
        byType            = emptyMap(),
        cmcDistribution   = emptyMap(),
        bySet             = emptyMap(),
    )

    private fun buildCardValue(colorIdentity: String) = CardValue(
        scryfallId   = "test-id",
        name         = "Test Card",
        priceUsd     = 10.0,
        priceEur     = 9.0,
        isFoil       = false,
        imageArtCrop = null,
        colorIdentity = colorIdentity,
    )

    /**
     * Wires all mandatory mocks so the ViewModel can be constructed without errors.
     * Individual tests may override specific flows to control the scenario.
     */
    private fun wireDefaultMocks(
        collectionStats: CollectionStats = buildEmptyCollectionStats(),
        totalGames: Int = 0,
        totalWins:  Int = 0,
        avgWinTurn: Double = 0.0,
        favoriteElim: String = "",
    ) {
        every { userPreferencesDataStore.playerNameFlow }  returns playerNameFlow
        every { userPreferencesDataStore.avatarUrlFlow }   returns avatarUrlFlow
        every { userPreferencesDataStore.preferencesFlow } returns preferencesFlow
        every { authRepository.sessionState }              returns sessionStateFlow

        every { statsRepo.observeCollectionStats(any()) }   returns flowOf(collectionStats)
        every { gameSessionRepo.observeTotalGames() }       returns flowOf(totalGames)
        every { gameSessionRepo.observeWins(any()) }        returns flowOf(totalWins)
        every { gameSessionRepo.observeAvgLifeOnWin() }     returns flowOf(null)
        every { gameSessionRepo.observeAvgLifeOnLoss() }    returns flowOf(null)
        every { gameSessionRepo.observeCurrentStreak(any()) } returns flowOf(0)
        every { gameSessionRepo.observeFavoriteMode() }     returns flowOf(null)
        every { gameSessionRepo.observeAvgDurationMs() }    returns flowOf(null)
        every { gameSessionRepo.observeMostFrequentElimination() } returns flowOf(
            if (favoriteElim.isBlank()) null
            else mockk { every { eliminationReason } returns favoriteElim }
        )
        every { gameSessionRepo.observeAvgWinTurn(any()) }  returns flowOf(avgWinTurn)
        every { gameSessionRepo.observeDeckStats() }        returns flowOf(emptyList())
        every { gameSessionRepo.observeRecentSessions(any()) } returns flowOf(emptyList())

        every { surveyAnswerDao.observeSurveyCount() }      returns flowOf(0)
        every { surveyAnswerDao.observeManaIssueCount() }   returns flowOf(0)
        every { surveyAnswerDao.observeAvgHandRating() }    returns flowOf(null)
        every { surveyAnswerDao.observeFavoriteWinStyle() } returns flowOf(null)

        every { friendRepository.observeFriendCount() }     returns flowOf(0)
        every { friendRepository.observePendingCount() }    returns flowOf(0)

        every { checkAchievementsUseCase(any(), any()) } returns emptyList()
    }

    private fun buildViewModel(): ProfileViewModel = ProfileViewModel(
        statsRepo                = statsRepo,
        gameSessionRepo          = gameSessionRepo,
        surveyAnswerDao          = surveyAnswerDao,
        checkAchievementsUseCase = checkAchievementsUseCase,
        userPreferencesDataStore = userPreferencesDataStore,
        authRepository           = authRepository,
        friendRepository         = friendRepository,
    )

    // ── Setup / Teardown ─────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — savePlayerName
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given blank name when savePlayerName then DataStore savePlayerName is NOT called`() = runTest {
        // Arrange
        wireDefaultMocks()
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Act
        viewModel.savePlayerName("   ")
        advanceUntilIdle()

        // Assert: guard prevents the DataStore write
        coVerify(exactly = 0) { userPreferencesDataStore.savePlayerName(any()) }
    }

    @Test
    fun `given empty string when savePlayerName then DataStore savePlayerName is NOT called`() = runTest {
        // Arrange
        wireDefaultMocks()
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Act
        viewModel.savePlayerName("")
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { userPreferencesDataStore.savePlayerName(any()) }
    }

    @Test
    fun `given valid name when savePlayerName then state is updated optimistically`() = runTest {
        // Arrange
        wireDefaultMocks()
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Act
        viewModel.savePlayerName("Gandalf")
        advanceUntilIdle()

        // Assert: optimistic update (state changes immediately, before DataStore confirms)
        assertEquals("Gandalf", viewModel.uiState.value.playerName)
    }

    @Test
    fun `given valid name when savePlayerName then DataStore_savePlayerName is called with trimmed name`() = runTest {
        // Arrange
        wireDefaultMocks()
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Act: name has surrounding spaces that should be trimmed
        viewModel.savePlayerName("  Gandalf  ")
        advanceUntilIdle()

        // Assert: saved with trim applied
        coVerify(exactly = 1) { userPreferencesDataStore.savePlayerName("Gandalf") }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — detectPlayStyle (tested via flow observation in init)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given avgWinTurn between 1 and 7 when ViewModel initializes then playStyle is AGGRO`() = runTest {
        // Arrange
        wireDefaultMocks(avgWinTurn = 5.0, favoriteElim = "")
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Assert: turn 5 is in the aggro range (1..7)
        assertEquals(PlayStyle.AGGRO, viewModel.uiState.value.playStyle)
    }

    @Test
    fun `given avgWinTurn 1 (boundary) when ViewModel initializes then playStyle is AGGRO`() = runTest {
        // Arrange: exact lower boundary of AGGRO range
        wireDefaultMocks(avgWinTurn = 1.0)
        viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals(PlayStyle.AGGRO, viewModel.uiState.value.playStyle)
    }

    @Test
    fun `given avgWinTurn 7 (boundary) when ViewModel initializes then playStyle is AGGRO`() = runTest {
        // Arrange: exact upper boundary of AGGRO range
        wireDefaultMocks(avgWinTurn = 7.0)
        viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals(PlayStyle.AGGRO, viewModel.uiState.value.playStyle)
    }

    @Test
    fun `given favoriteElim COMMANDER_DAMAGE and avgWinTurn outside aggro range when ViewModel initializes then playStyle is MIDRANGE`() = runTest {
        // Arrange: avgWinTurn=9 is outside aggro range; COMMANDER_DAMAGE → MIDRANGE
        wireDefaultMocks(avgWinTurn = 9.0, favoriteElim = "COMMANDER_DAMAGE")
        viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals(PlayStyle.MIDRANGE, viewModel.uiState.value.playStyle)
    }

    @Test
    fun `given avgWinTurn greater than 12 and no COMMANDER_DAMAGE when ViewModel initializes then playStyle is CONTROL`() = runTest {
        // Arrange: long games → CONTROL
        wireDefaultMocks(avgWinTurn = 15.0, favoriteElim = "")
        viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals(PlayStyle.CONTROL, viewModel.uiState.value.playStyle)
    }

    @Test
    fun `given avgWinTurn 8 and no special elimination when ViewModel initializes then playStyle is BALANCED`() = runTest {
        // Arrange: turn 8 is outside aggro, no COMMANDER_DAMAGE, not >12 → BALANCED
        wireDefaultMocks(avgWinTurn = 8.0, favoriteElim = "")
        viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals(PlayStyle.BALANCED, viewModel.uiState.value.playStyle)
    }

    @Test
    fun `given avgWinTurn 0 (default) when ViewModel initializes then playStyle is BALANCED`() = runTest {
        // Arrange: 0.0 is not in 1..7 range (range is inclusive from 1.0) → BALANCED
        wireDefaultMocks(avgWinTurn = 0.0)
        viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals(PlayStyle.BALANCED, viewModel.uiState.value.playStyle)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — winRate computed property
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given 10 games and 4 wins when winRate accessed then returns 0_4f`() = runTest {
        // Arrange
        wireDefaultMocks(totalGames = 10, totalWins = 4)
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Assert
        assertEquals(0.4f, viewModel.uiState.value.winRate, 0.001f)
    }

    @Test
    fun `given 0 games when winRate accessed then returns 0_0f to avoid division by zero`() = runTest {
        // Arrange: totalGames = 0 → guard prevents division by zero
        wireDefaultMocks(totalGames = 0, totalWins = 0)
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Assert
        assertEquals(0.0f, viewModel.uiState.value.winRate, 0.001f)
    }

    @Test
    fun `given 1 game and 1 win when winRate accessed then returns 1_0f`() = runTest {
        // Arrange
        wireDefaultMocks(totalGames = 1, totalWins = 1)
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Assert: perfect win rate
        assertEquals(1.0f, viewModel.uiState.value.winRate, 0.001f)
    }

    @Test
    fun `given 3 games and 1 win when winRate accessed then returns 0_333f`() = runTest {
        // Arrange
        wireDefaultMocks(totalGames = 3, totalWins = 1)
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Assert
        assertEquals(1f / 3f, viewModel.uiState.value.winRate, 0.001f)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — buildAchievementStats with null collectionStats
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given null collectionStats when checkAchievementsUseCase is called then AchievementStats defaults totalCards to 0`() = runTest {
        // Arrange: flow never emits, so collectionStats stays null
        every { statsRepo.observeCollectionStats(any()) } returns kotlinx.coroutines.flow.flow {
            throw RuntimeException("Stats unavailable")
        }
        wireDefaultMocks()
        // Override statsRepo.observeCollectionStats AFTER wireDefaultMocks sets it up
        every { statsRepo.observeCollectionStats(any()) } returns kotlinx.coroutines.flow.flow {
            throw RuntimeException("Stats unavailable")
        }

        viewModel = buildViewModel()
        advanceUntilIdle()

        // Assert: achievements were computed with 0 totalCards (default for null stats)
        verify {
            checkAchievementsUseCase(
                match { stats: AchievementStats -> stats.totalCards == 0 },
                any()
            )
        }
    }

    @Test
    fun `given collectionStats with 100 cards when buildAchievementStats then AchievementStats totalCards is 100`() = runTest {
        // Arrange
        val stats = buildEmptyCollectionStats().copy(totalCards = 100)
        wireDefaultMocks(collectionStats = stats)
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Assert: achievements computed with correct card count
        verify {
            checkAchievementsUseCase(
                match { achievementStats: AchievementStats -> achievementStats.totalCards == 100 },
                any()
            )
        }
    }

    @Test
    fun `given null collectionStats when buildAchievementStats then hasMythic defaults to false`() = runTest {
        // Arrange: null collectionStats → byRarity is empty → hasMythic = false
        wireDefaultMocks()
        every { statsRepo.observeCollectionStats(any()) } returns kotlinx.coroutines.flow.flow {
            throw RuntimeException("Stats unavailable")
        }
        viewModel = buildViewModel()
        advanceUntilIdle()

        verify {
            checkAchievementsUseCase(
                match { stats: AchievementStats -> !stats.hasMythic },
                any()
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — computeFavouriteColor
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given byColor with COLORLESS only when collectionStats emits then favouriteColor is null`() = runTest {
        // Arrange: computeFavouriteColor filters out COLORLESS, so only colorless → null
        val stats = buildEmptyCollectionStats(
            byColor = mapOf(MtgColor.COLORLESS to 100)
        )
        wireDefaultMocks(collectionStats = stats)
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Assert
        assertNull(viewModel.uiState.value.favouriteColor)
    }

    @Test
    fun `given byColor with COLORLESS and Red when collectionStats emits then favouriteColor is R`() = runTest {
        // Arrange: COLORLESS is skipped; R has most cards → R is favourite
        val stats = buildEmptyCollectionStats(
            byColor = mapOf(
                MtgColor.COLORLESS to 200,
                MtgColor.R         to 50,
            )
        )
        wireDefaultMocks(collectionStats = stats)
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Assert: COLORLESS was skipped; R is the only remaining colour
        assertEquals("R", viewModel.uiState.value.favouriteColor)
    }

    @Test
    fun `given byColor with multiple colors when collectionStats emits then the color with most cards is favourite`() = runTest {
        // Arrange
        val stats = buildEmptyCollectionStats(
            byColor = mapOf(
                MtgColor.W to 10,
                MtgColor.U to 30,
                MtgColor.B to 20,
                MtgColor.R to 5,
                MtgColor.G to 15,
            )
        )
        wireDefaultMocks(collectionStats = stats)
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Assert: U has the most (30)
        assertEquals("U", viewModel.uiState.value.favouriteColor)
    }

    @Test
    fun `given empty byColor when collectionStats emits then favouriteColor is null`() = runTest {
        // Arrange
        val stats = buildEmptyCollectionStats(byColor = emptyMap())
        wireDefaultMocks(collectionStats = stats)
        viewModel = buildViewModel()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.favouriteColor)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — computeMostValuableColor
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given mostValuableCard with multi-color identity when collectionStats emits then mostValuableColor is M`() = runTest {
        // Arrange: colorIdentity contains two colors → "M" (multicolor)
        val cardValue = buildCardValue(colorIdentity = "[\"R\", \"G\"]")
        val stats = buildEmptyCollectionStats(mostValuableCards = listOf(cardValue))
        wireDefaultMocks(collectionStats = stats)
        viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals("M", viewModel.uiState.value.mostValuableColor)
    }

    @Test
    fun `given mostValuableCard with empty colorIdentity when collectionStats emits then mostValuableColor is C`() = runTest {
        // Arrange: empty colorIdentity → colorless → "C"
        val cardValue = buildCardValue(colorIdentity = "[]")
        val stats = buildEmptyCollectionStats(mostValuableCards = listOf(cardValue))
        wireDefaultMocks(collectionStats = stats)
        viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals("C", viewModel.uiState.value.mostValuableColor)
    }

    @Test
    fun `given mostValuableCard with single color identity when collectionStats emits then mostValuableColor is that color`() = runTest {
        // Arrange
        val cardValue = buildCardValue(colorIdentity = "[\"U\"]")
        val stats = buildEmptyCollectionStats(mostValuableCards = listOf(cardValue))
        wireDefaultMocks(collectionStats = stats)
        viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals("U", viewModel.uiState.value.mostValuableColor)
    }

    @Test
    fun `given no mostValuableCards when collectionStats emits then mostValuableColor is null`() = runTest {
        // Arrange: empty list → first() returns null → function returns null
        val stats = buildEmptyCollectionStats(mostValuableCards = emptyList())
        wireDefaultMocks(collectionStats = stats)
        viewModel = buildViewModel()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.mostValuableColor)
    }

    @Test
    fun `given mostValuableCard with Red single color when collectionStats emits then mostValuableColor is R`() = runTest {
        // Arrange
        val cardValue = buildCardValue(colorIdentity = "[\"R\"]")
        val stats = buildEmptyCollectionStats(mostValuableCards = listOf(cardValue))
        wireDefaultMocks(collectionStats = stats)
        viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals("R", viewModel.uiState.value.mostValuableColor)
    }

    @Test
    fun `given mostValuableCard with 5-color identity when collectionStats emits then mostValuableColor is M`() = runTest {
        // Arrange: WUBRG identity → multicolor → "M"
        val cardValue = buildCardValue(colorIdentity = "[\"W\", \"U\", \"B\", \"R\", \"G\"]")
        val stats = buildEmptyCollectionStats(mostValuableCards = listOf(cardValue))
        wireDefaultMocks(collectionStats = stats)
        viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals("M", viewModel.uiState.value.mostValuableColor)
    }
}
