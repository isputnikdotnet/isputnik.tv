package net.isputnik.tv.games

import kotlinx.serialization.Serializable
import kotlin.math.abs

// Torpedo Run: a port of the engine's arcade/torpedo/rules.ts, line for line (see
// Arcade.kt for why it must stay exact). Through a periscope over three lanes of sea —
// near, mid, far — ships cross under the horizon. The sight turns ±20° and fires; the
// torpedo is slow and leans the way the periscope is turned, so a ship must be led. Ten
// torpedoes a round; 1 500 in the first earns a bonus round in the same run. Whole numbers
// throughout: bearings in tenths of a degree, time in ticks. Field names are the engine's.

internal object Torpedo {
    const val ID = "torpedo"
    const val VARIANT = "standard"

    class ShipClass(val points: Int, val lane: String, val speed: Int, val half: Int)

    val SHIPS = mapOf(
        "tanker" to ShipClass(100, "near", 2, 52),
        "cargo" to ShipClass(200, "mid", 3, 38),
        "destroyer" to ShipClass(300, "far", 4, 26),
        "patrol" to ShipClass(500, "mid", 6, 19)
    )
    val LANES = listOf("near", "mid", "far")

    const val TICK_MS = 50
    const val FIELD_HALF = 360
    const val MAX_BEARING = 200
    const val SIGHT_SPEED = 10
    val RANGE = mapOf("near" to 24, "mid" to 38, "far" to 56)
    val REACH = mapOf("near" to 64, "mid" to 81, "far" to 93)
    const val LEAN = 75
    const val TORPEDOES = 10
    const val ROUND_TICKS = 1800
    const val BONUS_AT = 1500
    const val ROUNDS = 2
    const val SINK_TICKS = 24
    private const val ZIG_FROM = 20
    private const val ZIG_SPAN = 30
    private val PATROL_SPEEDS = listOf(3, 5, 7, 9)
    private const val PATROL_CHANCE = 0.25
    private const val WAIT_FROM = 20
    private const val WAIT_SPAN = 60

    val INITIAL_INPUT = TorpedoInput(0, false)

    private fun emptyTally() = RoundTally(0, Hits(0, 0, 0, 0), 0)

    fun createRun(seed: Long): TorpedoState {
        val dice = Dice(seed and 0xffffffffL)
        val near = dice.between(30, 30)
        val mid = dice.between(10, 20)
        val far = dice.between(0, 10)
        return TorpedoState(
            variant = VARIANT, rng = dice.rng, tick = 0, phase = "play", round = 1, clock = ROUND_TICKS, bearing = 0,
            torpedoesLeft = TORPEDOES, torpedo = null, ships = emptyList(), waits = Waits(near, mid, far), nextId = 1,
            score = 0, rounds = listOf(emptyTally()), bonus = false, over = false
        )
    }

    private fun laneAt(run: Int): String? = LANES.firstOrNull { RANGE[it] == run }

    /** Where a torpedo fired down `bearing` crosses a lane. */
    fun courseAt(bearing: Int, lane: String): Int = jsRound((bearing.toDouble() * (10_000 + LEAN * REACH.getValue(lane))) / 10_000)

    fun covers(ship: Ship, bearing: Int) = ship.sinking == null && abs(ship.x - bearing) <= SHIPS.getValue(ship.kind).half

    /** The ship the crosshair has on it: what a torpedo fired now would meet if only it moved. */
    fun inSights(state: TorpedoState, bearing: Int): Ship? =
        LANES.firstNotNullOfOrNull { lane -> state.ships.firstOrNull { SHIPS.getValue(it.kind).lane == lane && covers(it, courseAt(bearing, lane)) } }

    private fun launchShip(dice: Dice, lane: String, id: Int): Ship {
        val kind = when (lane) {
            "near" -> "tanker"
            "far" -> "destroyer"
            else -> if (dice.next() < PATROL_CHANCE) "patrol" else "cargo"
        }
        val dir = if (dice.next() < 0.5) 1 else -1
        val ship = SHIPS.getValue(kind)
        val patrol = kind == "patrol"
        val speed = if (patrol) PATROL_SPEEDS[dice.between(0, PATROL_SPEEDS.size)] else ship.speed
        val zig = if (patrol) dice.between(ZIG_FROM, ZIG_SPAN) else 0
        return Ship(id, kind, -dir * (FIELD_HALF + ship.half), dir, speed, zig, null)
    }

    sealed interface Cue {
        data class Hit(val ship: Int, val kind: String, val points: Int) : Cue
        data class Miss(val bearing: Int) : Cue
        data class Pass(val lane: String, val bearing: Int) : Cue
        data object Launch : Cue
        data object Bonus : Cue
        data class RoundOver(val bonus: Boolean) : Cue
        data object Over : Cue
    }

    fun tick(state: TorpedoState, input: TorpedoInput, cues: MutableList<Cue>? = null): TorpedoState {
        check(!state.over) { "The run is over" }
        if (state.phase == "bonus") {
            if (!input.fire) return state.copy(tick = state.tick + 1)
            return state.copy(
                tick = state.tick + 1, phase = "play", round = state.round + 1, clock = ROUND_TICKS,
                torpedoesLeft = TORPEDOES, rounds = state.rounds + emptyTally()
            )
        }

        val dice = Dice(state.rng)
        val last = state.rounds.last()
        var fired = last.fired
        var hits = last.hits
        var tallyScore = last.score
        var waits = state.waits
        val clock = maxOf(0, state.clock - 1)
        val bearing = maxOf(-MAX_BEARING, minOf(MAX_BEARING, state.bearing + input.turn * SIGHT_SPEED))

        var ships = mutableListOf<Ship>()
        for (old in state.ships) {
            var ship = old
            if (ship.sinking != null) {
                val left = ship.sinking - 1
                if (left > 0) ships += ship.copy(sinking = left)
                continue
            }
            if (ship.kind == "patrol") {
                val zig = ship.zig - 1
                ship = if (zig <= 0) {
                    val speed = PATROL_SPEEDS[dice.between(0, PATROL_SPEEDS.size)]
                    ship.copy(speed = speed, zig = dice.between(ZIG_FROM, ZIG_SPAN))
                } else ship.copy(zig = zig)
            }
            ship = ship.copy(x = ship.x + ship.dir * ship.speed)
            if (abs(ship.x) > FIELD_HALF + SHIPS.getValue(ship.kind).half) continue
            ships += ship
        }

        var score = state.score
        var torpedo: TorpedoRun? = null
        var bonus = state.bonus
        if (state.torpedo != null) {
            val run = TorpedoRun(state.torpedo.bearing, state.torpedo.run + 1)
            val lane = laneAt(run.run)
            val target = lane?.let { l -> ships.firstOrNull { SHIPS.getValue(it.kind).lane == l && covers(it, courseAt(run.bearing, l)) } }
            if (target != null) {
                val points = SHIPS.getValue(target.kind).points
                ships = ships.map { if (it === target) it.copy(sinking = SINK_TICKS) else it }.toMutableList()
                score = state.score + points
                hits = hits.plus(target.kind)
                tallyScore += points
                cues?.add(Cue.Hit(target.id, target.kind, points))
                if (state.round == 1 && !state.bonus && score >= BONUS_AT) {
                    bonus = true
                    cues?.add(Cue.Bonus)
                }
            } else if (run.run >= RANGE.getValue("far")) {
                cues?.add(Cue.Miss(run.bearing))
            } else {
                torpedo = run
                if (lane != null) cues?.add(Cue.Pass(lane, run.bearing))
            }
        }

        var torpedoesLeft = state.torpedoesLeft
        if (input.fire && torpedo == null && state.torpedo == null && torpedoesLeft > 0 && clock > 0) {
            torpedo = TorpedoRun(bearing, 0)
            torpedoesLeft = state.torpedoesLeft - 1
            fired += 1
            cues?.add(Cue.Launch)
        }

        var nextId = state.nextId
        for (lane in LANES) {
            if (ships.any { SHIPS.getValue(it.kind).lane == lane }) continue
            val wait = waits[lane]
            if (wait > 0) {
                waits = waits.with(lane, wait - 1)
            } else {
                val ship = launchShip(dice, lane, nextId)
                nextId += 1
                ships += ship
                waits = waits.with(lane, dice.between(WAIT_FROM, WAIT_SPAN))
            }
        }

        val rounds = state.rounds.dropLast(1) + RoundTally(fired, hits, tallyScore)
        var next = state.copy(
            rng = dice.rng, tick = state.tick + 1, round = state.round, clock = clock, bearing = bearing, torpedoesLeft = torpedoesLeft,
            torpedo = torpedo, ships = ships, waits = waits, nextId = nextId, score = score, rounds = rounds, bonus = bonus
        )

        val spent = next.torpedoesLeft == 0 || next.clock == 0
        if (spent && next.torpedo == null) {
            if (next.bonus && next.round < ROUNDS) {
                next = next.copy(phase = "bonus")
                cues?.add(Cue.RoundOver(true))
            } else {
                next = next.copy(over = true)
                cues?.add(Cue.RoundOver(false))
                cues?.add(Cue.Over)
            }
        }
        return next
    }
}

@Serializable internal data class TorpedoInput(val turn: Int, val fire: Boolean)
@Serializable internal data class TorpedoRun(val bearing: Int, val run: Int)

@Serializable
internal data class Ship(val id: Int, val kind: String, val x: Int, val dir: Int, val speed: Int, val zig: Int, val sinking: Int?)

@Serializable
internal data class Waits(val near: Int, val mid: Int, val far: Int) {
    operator fun get(lane: String) = when (lane) { "near" -> near; "mid" -> mid; else -> far }
    fun with(lane: String, value: Int) = when (lane) { "near" -> copy(near = value); "mid" -> copy(mid = value); else -> copy(far = value) }
}

@Serializable
internal data class Hits(val tanker: Int, val cargo: Int, val destroyer: Int, val patrol: Int) {
    fun plus(kind: String) = when (kind) {
        "tanker" -> copy(tanker = tanker + 1)
        "cargo" -> copy(cargo = cargo + 1)
        "destroyer" -> copy(destroyer = destroyer + 1)
        else -> copy(patrol = patrol + 1)
    }
}

@Serializable internal data class RoundTally(val fired: Int, val hits: Hits, val score: Int)

@Serializable
internal data class TorpedoState(
    val variant: String,
    val rng: Long,
    val tick: Int,
    val phase: String,
    val round: Int,
    val clock: Int,
    val bearing: Int,
    val torpedoesLeft: Int,
    val torpedo: TorpedoRun?,
    val ships: List<Ship>,
    val waits: Waits,
    val nextId: Int,
    val score: Int,
    val rounds: List<RoundTally>,
    val bonus: Boolean,
    val over: Boolean
)
