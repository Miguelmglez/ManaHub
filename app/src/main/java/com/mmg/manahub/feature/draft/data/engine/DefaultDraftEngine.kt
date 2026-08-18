package com.mmg.manahub.feature.draft.data.engine

import com.mmg.manahub.core.domain.engine.BoosterGenerator
import com.mmg.manahub.core.domain.engine.BotDrafter
import com.mmg.manahub.core.domain.engine.DraftEngine
import com.mmg.manahub.core.model.BoosterPack
import com.mmg.manahub.core.model.DraftCard
import com.mmg.manahub.core.model.DraftConfig
import com.mmg.manahub.core.model.DraftMode
import com.mmg.manahub.core.model.DraftSeat
import com.mmg.manahub.core.model.DraftState
import com.mmg.manahub.core.model.DraftStatus
import com.mmg.manahub.core.model.DraftableSet
import com.mmg.manahub.core.model.EngineConfig
import com.mmg.manahub.core.model.PassDirection
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

    override fun applyHumanPick(
        state: DraftState,
        scryfallIds: List<String>,
        engine: EngineConfig?,
    ): DraftState {
        val seatCount = state.seats.size
        val humanIndex = state.seats.indexOfFirst { it.isHuman }.takeIf { it >= 0 } ?: 0

        val humanPack = state.packsInFlight[humanIndex] ?: return state
        if (humanPack.cards.isEmpty()) return state

        // 1. Apply each requested id against a shrinking local copy of the human's pack, capped at
        // config.picksPerTurn cards taken THIS TURN (defensive against a caller passing more ids
        // than the turn allows) and at the pack running out first (odd-sized final turn).
        var remainingHumanCards = humanPack.cards
        val pickedByHuman = mutableListOf<DraftCard>()
        var picksTakenInTurn = state.picksTakenInTurn
        for (id in scryfallIds) {
            if (picksTakenInTurn >= state.config.picksPerTurn) break
            if (remainingHumanCards.isEmpty()) break
            val pickedCard = remainingHumanCards.firstOrNull { it.card.scryfallId == id }
                ?: remainingHumanCards.firstOrNull()
                ?: break
            pickedByHuman += pickedCard
            remainingHumanCards = remainingHumanCards - pickedCard
            picksTakenInTurn++
        }
        // Nothing could be picked (e.g. an empty id list) — return state unchanged.
        if (pickedByHuman.isEmpty()) return state
        val humanPicksThisCall = pickedByHuman.size

        val newSeats = state.seats.toMutableList()
        val newPacksInFlight = state.packsInFlight.toMutableMap()
        newPacksInFlight[humanIndex] = humanPack.copy(cards = remainingHumanCards)
        newSeats[humanIndex] = newSeats[humanIndex].copy(
            pool = newSeats[humanIndex].pool + pickedByHuman,
        )

        // 2. The turn is complete once picksPerTurn is reached OR the human's pack is exhausted
        // first (the odd-sized final turn of a round). Every seat's pack is the same size at the
        // start of a turn (lockstep invariant), so the human running out is equivalent to "any
        // seat's pack is exhausted first".
        val turnComplete = picksTakenInTurn >= state.config.picksPerTurn ||
            remainingHumanCards.isEmpty()

        if (!turnComplete) {
            // Partial turn (defensive — Phase C's UI always sends a full turn's worth in one
            // call): only the human has picked so far. No bot picks, no rotation yet.
            return state.copy(
                seats = newSeats,
                packsInFlight = newPacksInFlight,
                picksTakenInTurn = picksTakenInTurn,
            )
        }

        // 3. Bot picks: every non-human seat takes the SAME number of cards the human took this
        // call (capped by that bot's own remaining cards), so every seat's pack shrinks in
        // lockstep. Each bot pick is applied against a shrinking local copy of its own pack/pool
        // so a multi-card turn lets the bot's later picks see its own earlier picks this turn.
        for (i in state.seats.indices) {
            if (i == humanIndex) continue
            val pack = newPacksInFlight[i] ?: continue
            if (pack.cards.isEmpty()) continue

            var botCards = pack.cards
            var botSeat = newSeats[i]
            val botPickCount = minOf(humanPicksThisCall, botCards.size)
            var taken = 0
            while (taken < botPickCount && botCards.isNotEmpty()) {
                val botPick = botDrafter.pick(
                    botSeat,
                    BoosterPack(pack.id, botCards),
                    state.round,
                    state.pickNumber,
                    engine,
                )
                botCards = botCards - botPick
                botSeat = botSeat.copy(pool = botSeat.pool + botPick)
                taken++
            }
            newPacksInFlight[i] = pack.copy(cards = botCards)
            newSeats[i] = botSeat
        }

        // 4. Rotate packs
        val rotatedPacks = rotatePacks(newPacksInFlight, seatCount, state.passDirection)

        // 5. Advance pick number; check if round is over.
        // Inspect ALL packs, not an arbitrary one — HashMap iteration order is not deterministic,
        // so firstOrNull() could sample a pack that is out of sync with the rest.
        val roundOver = rotatedPacks.isEmpty() ||
            rotatedPacks.values.all { it.cards.isEmpty() }

        return if (!roundOver) {
            state.copy(
                seats = newSeats,
                packsInFlight = rotatedPacks,
                pickNumber = state.pickNumber + 1,
                picksTakenInTurn = 0,
            )
        } else {
            advanceRound(state, newSeats, rotatedPacks).copy(picksTakenInTurn = 0)
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

    override fun autoPick(state: DraftState, engine: EngineConfig?): DraftState {
        val humanIndex = state.seats.indexOfFirst { it.isHuman }.takeIf { it >= 0 } ?: 0
        val pack = state.packsInFlight[humanIndex] ?: return state
        // Never call the drafter with an empty pack — it would throw. An empty in-flight pack means
        // there is nothing to pick this beat, so leave the state unchanged.
        if (pack.cards.isEmpty()) return state

        // Pick up to picksPerTurn cards (capped by the pack's remaining size, same as the odd-sized
        // final turn of a round) via the drafter, in a loop against a shrinking local copy so a
        // multi-card auto-pick lets each subsequent pick see the human seat's own earlier picks
        // this turn — then delegate the resulting id list to applyHumanPick for the real bookkeeping
        // (bot picks + rotation).
        val picksRemaining = (state.config.picksPerTurn - state.picksTakenInTurn).coerceAtLeast(0)
        val pickCount = minOf(picksRemaining, pack.cards.size)
        if (pickCount == 0) return state

        var remainingCards = pack.cards
        // The human seat carries no diversity prior, so this suggestion is neutral.
        var seatState = state.seats[humanIndex]
        val ids = mutableListOf<String>()
        var taken = 0
        while (taken < pickCount && remainingCards.isNotEmpty()) {
            val best = botDrafter.pick(
                seatState,
                BoosterPack(pack.id, remainingCards),
                state.round,
                state.pickNumber,
                engine,
            )
            ids += best.card.scryfallId
            remainingCards = remainingCards - best
            seatState = seatState.copy(pool = seatState.pool + best)
            taken++
        }
        return applyHumanPick(state, ids, engine)
    }

    override fun isComplete(state: DraftState): Boolean =
        state.status == DraftStatus.BUILDING || state.status == DraftStatus.COMPLETE
}
