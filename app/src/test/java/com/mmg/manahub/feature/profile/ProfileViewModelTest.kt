package com.mmg.manahub.feature.profile

import app.cash.turbine.test
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.SurveyAnswerDao
import com.mmg.manahub.core.model.CardValue
import com.mmg.manahub.core.model.CollectionStats
import com.mmg.manahub.core.model.MtgColor
import com.mmg.manahub.core.model.Rarity
import com.mmg.manahub.feature.game.domain.repository.GameSessionRepository
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.gamification.domain.QuestPeriod
import com.mmg.manahub.core.gamification.domain.QuestWeightClass
import com.mmg.manahub.core.gamification.domain.model.AchievementUiModel
import com.mmg.manahub.core.gamification.domain.model.EquippedCosmetics
import com.mmg.manahub.core.gamification.domain.model.PlayerProgression
import com.mmg.manahub.core.gamification.domain.model.QuestBoard
import com.mmg.manahub.core.gamification.domain.model.QuestUiModel
import com.mmg.manahub.core.gamification.domain.model.RewardsBoard
import com.mmg.manahub.core.gamification.domain.model.StreakUiModel
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import com.mmg.manahub.core.gamification.domain.usecase.ClaimResult
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.feature.profile.presentation.PlayStyle
import com.mmg.manahub.feature.profile.presentation.ProfileViewModel
import com.mmg.manahub.util.TestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertTrue
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
 * - achievements: observeAchievements() flow drives UiState.achievements
 * - computeFavouriteColor: skips COLORLESS entries
 * - computeMostValuableColor: multi-color → "M"; empty identity → "C"; single → that color
 *
 * DESIGN NOTES:
 * - ProfileViewModel has a complex init{} with many flow subscriptions.
 *   We wire all flows via mocks so the ViewModel constructs cleanly.
 * - detectPlayStyle is a private helper; we test it indirectly by controlling
 *   the uiState values it reads from.
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
    private val userPreferencesDataStore = mockk<UserPreferencesDataStore>(relaxed = true)
    private val authRepository           = mockk<AuthRepository>(relaxed = true)
    private val friendRepository         = mockk<FriendRepository>(relaxed = true)
    private val gamificationRepository   = mockk<GamificationRepository>(relaxed = true)

    // Mutable state flows used to drive ViewModel state changes in tests
    private val playerNameFlow    = MutableStateFlow("Wizard")
    private val avatarUrlFlow     = MutableStateFlow<String?>(null)
    private val preferencesFlow   = MutableStateFlow(TestFixtures.buildPreferences())
    private val sessionStateFlow  = MutableStateFlow<SessionState>(SessionState.Unauthenticated)
    private val gamificationEnabledFlow = MutableStateFlow(true)
    private val progressionFlow   = MutableStateFlow(
        PlayerProgression(
            totalXp = 0L,
            level = 1,
            xpIntoLevel = 0L,
            xpForNextLevel = 100L,
            updatedAt = java.time.Instant.EPOCH,
        )
    )
    private val achievementsFlow = MutableStateFlow<List<AchievementUiModel>>(emptyList())
    private val questBoardFlow = MutableStateFlow(QuestBoard.empty)
    private val streakFlow = MutableStateFlow(StreakUiModel(current = 0, longest = 0, freezeTokens = 0))
    private val rewardsBoardFlow = MutableStateFlow(RewardsBoard.EMPTY)
    private val equippedCosmeticsFlow = MutableStateFlow(EquippedCosmetics.NONE)

    private lateinit var viewModel: ProfileViewModel

    private fun quest(
        instanceId: String,
        status: String = "ACTIVE",
        xpReward: Int = 50,
    ) = QuestUiModel(
        instanceId = instanceId,
        templateId = "tmpl_$instanceId",
        titleRes = 0,
        descRes = 0,
        emoji = "⚔",
        period = QuestPeriod.DAILY,
        weightClass = QuestWeightClass.ACCESSIBLE,
        progress = 0,
        target = 1,
        status = status,
        xpReward = xpReward,
    )

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
        every { userPreferencesDataStore.gamificationEnabledFlow } returns gamificationEnabledFlow
        every { authRepository.sessionState }              returns sessionStateFlow
        every { gamificationRepository.observeProgression() } returns progressionFlow
        every { gamificationRepository.observeAchievements() } returns achievementsFlow
        every { gamificationRepository.observeActiveQuests() } returns questBoardFlow
        every { gamificationRepository.observeDailyActivityStreak() } returns streakFlow
        every { gamificationRepository.observeRewards() } returns rewardsBoardFlow
        every { gamificationRepository.observeEquippedCosmetics() } returns equippedCosmeticsFlow

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
    }

    private fun buildViewModel(): ProfileViewModel = ProfileViewModel(
        statsRepo                = statsRepo,
        gameSessionRepo          = gameSessionRepo,
        surveyAnswerDao          = surveyAnswerDao,
        userPreferencesDataStore = userPreferencesDataStore,
        friendRepository         = friendRepository,
        authRepository           = authRepository,
        gamificationRepository   = gamificationRepository,
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
    //  GROUP 4 — achievements come from observeAchievements() (gamification Phase 1)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given empty achievements flow when ViewModel initializes then achievements is empty`() = runTest {
        // Arrange: default flow emits emptyList()
        wireDefaultMocks()
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Assert
        assertEquals(emptyList<AchievementUiModel>(), viewModel.uiState.value.achievements)
    }

    @Test
    fun `given achievements flow emits models when ViewModel initializes then uiState exposes them`() = runTest {
        // Arrange
        wireDefaultMocks()
        achievementsFlow.value = listOf(
            AchievementUiModel(
                id = "FIRST_WIN",
                category = com.mmg.manahub.core.gamification.domain.model.AchievementCategory.GAMES,
                titleRes = 0,
                descRes = 0,
                emoji = "⚔️",
                tierThresholds = listOf(1),
                currentValue = 1,
                tierReached = 1,
                maxTier = 1,
                unlockedAt = 123L,
                isSecret = false,
            )
        )
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Assert: the flow drives UiState.achievements; the unlocked stamp is the real persisted value.
        assertEquals(1, viewModel.uiState.value.achievements.size)
        assertEquals("FIRST_WIN", viewModel.uiState.value.achievements.first().id)
        assertEquals(123L, viewModel.uiState.value.achievements.first().unlockedAt)
    }

    @Test
    fun `given a secret locked achievement when ViewModel initializes then it is masked`() = runTest {
        // Arrange: secret + still locked → isMasked == true
        wireDefaultMocks()
        achievementsFlow.value = listOf(
            AchievementUiModel(
                id = "SECRET_ONE_LIFE_WIN",
                category = com.mmg.manahub.core.gamification.domain.model.AchievementCategory.GAMES,
                titleRes = 0,
                descRes = 0,
                emoji = "💀",
                tierThresholds = listOf(1),
                currentValue = 0,
                tierReached = 0,
                maxTier = 1,
                unlockedAt = null,
                isSecret = true,
            )
        )
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Assert
        assertEquals(true, viewModel.uiState.value.achievements.first().isMasked)
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

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — gamification progression + master toggle (ADR-002, Phase 0)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given progression flow emits when ViewModel initializes then uiState exposes the progression`() = runTest {
        // Arrange
        wireDefaultMocks()
        progressionFlow.value = PlayerProgression(
            totalXp = 250L,
            level = 3,
            xpIntoLevel = 40L,
            xpForNextLevel = 120L,
            updatedAt = java.time.Instant.EPOCH,
        )
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Assert
        assertEquals(3, viewModel.uiState.value.progression?.level)
        assertEquals(40L, viewModel.uiState.value.progression?.xpIntoLevel)
    }

    @Test
    fun `given gamification disabled in DataStore when ViewModel initializes then gamificationEnabled is false`() = runTest {
        // Arrange
        wireDefaultMocks()
        gamificationEnabledFlow.value = false
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Assert
        assertEquals(false, viewModel.uiState.value.gamificationEnabled)
    }

    @Test
    fun `given default mocks when ViewModel initializes then gamificationEnabled defaults to true`() = runTest {
        // Arrange
        wireDefaultMocks()
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Assert: master toggle is ON by default (ADR-002 opt-out-first-class)
        assertEquals(true, viewModel.uiState.value.gamificationEnabled)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 8 — quests + streak + claim (gamification Phase 2)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given quest board emits when ViewModel initializes then uiState exposes the board`() = runTest {
        // Arrange
        wireDefaultMocks()
        questBoardFlow.value = QuestBoard(
            daily = listOf(quest("d1"), quest("d2", status = "COMPLETED")),
            weekly = listOf(quest("w1")),
        )
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Assert
        assertEquals(2, viewModel.uiState.value.questBoard.daily.size)
        assertEquals(1, viewModel.uiState.value.questBoard.weekly.size)
    }

    @Test
    fun `given streak flow emits when ViewModel initializes then uiState exposes the streak`() = runTest {
        // Arrange
        wireDefaultMocks()
        streakFlow.value = StreakUiModel(current = 5, longest = 9, freezeTokens = 2)
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Assert
        assertEquals(5, viewModel.uiState.value.streak.current)
        assertEquals(2, viewModel.uiState.value.streak.freezeTokens)
    }

    @Test
    fun `given a claimable quest when claimQuest succeeds then a QuestClaimed event is emitted`() = runTest {
        // Arrange
        wireDefaultMocks()
        coEvery { gamificationRepository.claimQuest("q1") } returns
            ClaimResult.Claimed(xpAwarded = 50, newLevel = 2, leveledUp = false)
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Act + Assert: Turbine subscribes to the one-shot Channel before the action fires.
        viewModel.events.test {
            viewModel.claimQuest("q1")
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is ProfileViewModel.Event.QuestClaimed)
            assertEquals(50, (event as ProfileViewModel.Event.QuestClaimed).xpAwarded)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { gamificationRepository.claimQuest("q1") }
    }

    @Test
    fun `given a non-completed quest when claimQuest returns NotCompleted then QuestClaimFailed is emitted`() = runTest {
        // Arrange
        wireDefaultMocks()
        coEvery { gamificationRepository.claimQuest("q2") } returns ClaimResult.NotCompleted
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Act + Assert
        viewModel.events.test {
            viewModel.claimQuest("q2")
            advanceUntilIdle()
            assertTrue(awaitItem() is ProfileViewModel.Event.QuestClaimFailed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given claimQuest throws then QuestClaimFailed is emitted (no crash)`() = runTest {
        // Arrange
        wireDefaultMocks()
        coEvery { gamificationRepository.claimQuest("q3") } throws RuntimeException("boom")
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Act + Assert: the failure is swallowed and surfaced as a failed-claim event.
        viewModel.events.test {
            viewModel.claimQuest("q3")
            advanceUntilIdle()
            assertTrue(awaitItem() is ProfileViewModel.Event.QuestClaimFailed)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
