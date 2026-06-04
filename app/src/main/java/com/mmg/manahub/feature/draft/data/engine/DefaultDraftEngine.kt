package com.mmg.manahub.feature.draft.data.engine

import com.mmg.manahub.feature.draft.domain.engine.BotDrafter
import com.mmg.manahub.feature.draft.domain.engine.BoosterGenerator
import com.mmg.manahub.feature.draft.domain.engine.DraftEngine
import com.mmg.manahub.feature.draft.domain.model.BoosterPack
import com.mmg.manahub.feature.draft.domain.model.DraftConfig
import com.mmg.manahub.feature.draft.domain.model.DraftMode
import com.mmg.manahub.feature.draft.domain.model.DraftSeat
import com.mmg.manahub.feature.draft.domain.model.DraftState
import com.mmg.manahub.feature.draft.domain.model.DraftStatus
import com.mmg.manahub.feature.draft.domain.model.DraftableSet
import com.mmg.manahub.feature.draft.domain.model.PassDirection
import kotlin.random.Random

class DefaultDraftEngine(
    private val boosterGenerator: BoosterGenerator,
    private val botDrafter: BotDrafter,
    private val random: Random = Random.Default,
) : DraftEngine {

    override fun start(set: DraftableSet, config: DraftConfig): DraftState {
        // The bot drafter is stateless and derives commitments dynamically from the pool.
        return when (config.mode) {
            DraftMode.DRAFT -> startDraft(set, config)
            DraftMode.SEALED -> startSealed(set, config)
        }
    }

    private fun startDraft(set: DraftableSet, config: DraftConfig): DraftState {
        // Generate all packs: packCount × seatCount, seat-major order
        val allPacks = boosterGenerator.generate(set, config)
        val seats = List(config.seatCount) { i ->
            DraftSeat(index = i, isHuman = i == 0)
        }

        // packsInFlight: round 1 pack for each seat (index i * packCount + 0)
        val packsInFlight = (0 until config.seatCount).associate { i ->
            i to allPacks[i * config.packCount]
        }

        // pendingPacks: rounds 2..packCount for each seat
        val pendingPacks = (0 until config.seatCount).associate { i ->
            i to (1 until config.packCount).map { r -> allPacks[i * config.packCount + r] }
        }

        return DraftState(
            config = config,
            round = 1,
            pickNumber = 1,
            seats = seats,
            packsInFlight = packsInFlight,
            passDirection = PassDirection.LEFT,
            status = DraftStatus.DRAFTING,
            pendingPacks = pendingPacks,
        )
    }

    private fun startSealed(set: DraftableSet, config: DraftConfig): DraftState {
        // SEALED is always a single player opening 6 packs. Force a 1-seat / 6-pack config so the
        // booster generator does not produce empty bot seats from the incoming config.seatCount.
        val sealedConfig = config.copy(seatCount = 1, packCount = 6)
        val packs = boosterGenerator.generate(set, sealedConfig)
        val allCards = packs.flatMap { it.cards }

        val humanSeat = DraftSeat(index = 0, isHuman = true, pool = allCards)
        // No bot seats — SEALED is single-player.

        return DraftState(
            config = config,
            round = 1,
            pickNumber = 1,
            seats = listOf(humanSeat),
            packsInFlight = emptyMap(),
            passDirection = PassDirection.LEFT,
            status = DraftStatus.BUILDING,
            pendingPacks = emptyMap(),
        )
    }

    override fun applyHumanPick(state: DraftState, scryfallId: String): DraftState {
        val seatCount = state.seats.size
        val humanIndex = state.seats.indexOfFirst { it.isHuman }.takeIf { it >= 0 } ?: 0

        // 1. Remove the human's pick from their pack and add to pool
        val humanPack = state.packsInFlight[humanIndex]
            ?: return state
        val pickedCard = humanPack.cards.firstOrNull { it.card.scryfallId == scryfallId }
            ?: humanPack.cards.firstOrNull()
            ?: return state
        val humanPackAfter = humanPack.copy(cards = humanPack.cards - pickedCard)

        // 2. Bot picks: for each non-human seat, pick one card
        val newSeats = state.seats.toMutableList()
        val newPacksInFlight = state.packsInFlight.toMutableMap()
        newPacksInFlight[humanIndex] = humanPackAfter
        newSeats[humanIndex] = newSeats[humanIndex].copy(
            pool = newSeats[humanIndex].pool + pickedCard,
        )

        for (i in state.seats.indices) {
            if (i == humanIndex) continue
            val pack = newPacksInFlight[i] ?: continue
            if (pack.cards.isEmpty()) continue
            val botPick = botDrafter.pick(newSeats[i], pack, state.round, state.pickNumber)
            newPacksInFlight[i] = pack.copy(cards = pack.cards - botPick)
            newSeats[i] = newSeats[i].copy(pool = newSeats[i].pool + botPick)
        }

        // 3. Rotate packs
        val rotatedPacks = rotatePacks(newPacksInFlight, seatCount, state.passDirection)

        // 4. Advance pick number; check if round is over.
        // Inspect ALL packs, not an arbitrary one — HashMap iteration order is not deterministic,
        // so firstOrNull() could sample a pack that is out of sync with the rest.
        val roundOver = rotatedPacks.isEmpty() ||
            rotatedPacks.values.all { it.cards.isEmpty() }

        return if (!roundOver) {
            state.copy(
                seats = newSeats,
                packsInFlight = rotatedPacks,
                pickNumber = state.pickNumber + 1,
            )
        } else {
            advanceRound(state, newSeats, rotatedPacks)
        }
    }

    private fun rotatePacks(
        packs: Map<Int, BoosterPack>,
        seatCount: Int,
        direction: PassDirection,
    ): Map<Int, BoosterPack> {
        val result = mutableMapOf<Int, BoosterPack>()
        for ((seatIdx, pack) in packs) {
            val dest = when (direction) {
                PassDirection.LEFT -> (seatIdx - 1 + seatCount) % seatCount
                PassDirection.RIGHT -> (seatIdx + 1) % seatCount
            }
            result[dest] = pack
        }
        return result
    }

    private fun advanceRound(
        state: DraftState,
        seats: List<DraftSeat>,
        rotatedPacks: Map<Int, BoosterPack>,
    ): DraftState {
        val nextRound = state.round + 1
        return if (nextRound > state.config.packCount) {
            state.copy(
                seats = seats,
                packsInFlight = emptyMap(),
                status = DraftStatus.BUILDING,
            )
        } else {
            // Load next round's packs from pendingPacks (first entry per seat)
            val newPacksInFlight = (0 until state.seats.size).associate { i ->
                i to (state.pendingPacks[i]?.firstOrNull()
                    ?: BoosterPack("empty-$i", emptyList()))
            }
            val newPendingPacks = state.pendingPacks.mapValues { (_, list) ->
                list.drop(1)
            }
            val nextDirection = when (state.passDirection) {
                PassDirection.LEFT -> PassDirection.RIGHT
                PassDirection.RIGHT -> PassDirection.LEFT
            }
            state.copy(
                seats = seats,
                round = nextRound,
                pickNumber = 1,
                packsInFlight = newPacksInFlight,
                passDirection = nextDirection,
                pendingPacks = newPendingPacks,
            )
        }
    }

    override fun autoPick(state: DraftState): DraftState {
        val humanIndex = state.seats.indexOfFirst { it.isHuman }.takeIf { it >= 0 } ?: 0
        val humanSeat = state.seats[humanIndex]
        val pack = state.packsInFlight[humanIndex] ?: return state
        val best = botDrafter.pick(humanSeat, pack, state.round, state.pickNumber)
        return applyHumanPick(state, best.card.scryfallId)
    }

    override fun isComplete(state: DraftState): Boolean =
        state.status == DraftStatus.BUILDING || state.status == DraftStatus.COMPLETE
}
