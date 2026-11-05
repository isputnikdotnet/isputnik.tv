package net.isputnik.tv.games

import kotlinx.serialization.Serializable

// Sputnik Catch («Лови спутник!»): a port of the engine's arcade/sputnik/rules.ts, line
// for line (see Arcade.kt for why it must stay exact). Four station modules launch
// sputniks down four rails; robot РОБ-1 holds his net at the foot of one. Each tick every
// sputnik moves a step; off the end of its rail it lands in the net or crashes, and an
// alien runs off. Three aliens loose end the run; while Belka looks out, a crash counts half.
// The names of the fields are the engine's, so a snapshot of its state decodes into these.

internal object Sputnik {
    const val ID = "sputnik"
    val VARIANTS = listOf("A", "B")

    /** Rails: 0 upper left, 1 lower left, 2 upper right, 3 lower right. */
    val RAILS = listOf(0, 1, 2, 3)
    const val STEPS = 5
    const val ALIEN_STEPS = 4
    const val LOST_HALVES = 6
    val PARDONS = listOf(200, 500)
    const val DISPLAY_WRAP = 10000

    private const val LAUNCH_CHANCE = 0.6
    private const val LAUNCH_GAP = 1

    private class Curve(val period: Int, val flying: Int, val maxFlying: Int)
    private val CURVES = mapOf("A" to Curve(620, 1, 4), "B" to Curve(460, 2, 5))
    private const val PER_POINT_MS = 2.4
    private const val PER_HUNDRED_MS = 40
    const val FLOOR_MS = 210
    private const val POINTS_PER_SPUTNIK = 20
    const val ALIEN_MS = 240

    fun sideOf(rail: Int) = if (rail < 2) "left" else "right"

    private fun belkaAway(dice: Dice, first: Boolean) = if (first) dice.between(25, 30) else dice.between(40, 60)
    private fun belkaStays(dice: Dice) = dice.between(16, 16)

    fun createRun(variant: String, seed: Long): SputnikState {
        val dice = Dice(seed and 0xffffffffL)
        val away = belkaAway(dice, true)
        return SputnikState(variant, dice.rng, 0, 0, 0, 0, emptyList(), Belka(false, away), null, false)
    }

    fun maxFlying(variant: String, caught: Int): Int {
        val curve = CURVES.getValue(variant)
        return minOf(curve.maxFlying, curve.flying + caught / POINTS_PER_SPUTNIK)
    }

    fun periodOf(state: SputnikState): Int {
        if (state.crash != null) return ALIEN_MS
        val caught = state.caught
        val period = CURVES.getValue(state.variant).period - (caught % 100) * PER_POINT_MS - (caught / 100) * PER_HUNDRED_MS
        return maxOf(FLOOR_MS, jsRound(period))
    }

    fun displayScore(state: SputnikState) = state.caught % DISPLAY_WRAP

    /** What a tick did that the screen shows or sounds: a catch, a crash. */
    sealed interface Cue {
        data class Catch(val rail: Int) : Cue
        data class Crash(val rail: Int, val half: Boolean) : Cue
        data object Launch : Cue
        data object Over : Cue
    }

    fun tick(state: SputnikState, net: Int, cues: MutableList<Cue>? = null): SputnikState {
        check(!state.over) { "The run is over" }
        var next = state.copy(net = net, tick = state.tick + 1)

        if (state.crash != null) {
            val step = state.crash.step + 1
            if (step < ALIEN_STEPS) {
                next = next.copy(crash = Crash(state.crash.side, step))
            } else {
                next = next.copy(crash = null)
                if (next.halves >= LOST_HALVES) {
                    next = next.copy(over = true)
                    cues?.add(Cue.Over)
                }
            }
            return next
        }

        val moved = state.sputniks.map { SputnikPiece(it.rail, it.step + 1) }
        val landed = moved.firstOrNull { it.step >= STEPS }
        next = next.copy(sputniks = moved.filter { it.step < STEPS })

        if (landed != null && landed.rail != net) {
            val half = state.belka.out
            cues?.add(Cue.Crash(landed.rail, half))
            return next.copy(halves = state.halves + if (half) 1 else 2, sputniks = emptyList(), crash = Crash(sideOf(landed.rail), 0))
        }
        if (landed != null) {
            next = next.copy(caught = state.caught + 1)
            cues?.add(Cue.Catch(landed.rail))
            if (next.caught in PARDONS) next = next.copy(halves = 0)
        }

        val dice = Dice(state.rng)
        if (next.sputniks.size < maxFlying(next.variant, next.caught) && dice.next() < LAUNCH_CHANCE) {
            val free = RAILS.filter { rail -> next.sputniks.none { it.rail == rail && it.step <= LAUNCH_GAP } }
            if (free.isNotEmpty()) {
                val rail = free[dice.between(0, free.size)]
                next = next.copy(sputniks = next.sputniks + SputnikPiece(rail, 0))
                cues?.add(Cue.Launch)
            }
        }

        val belka = next.belka
        next = if (belka.out) {
            val left = belka.ticks - 1
            next.copy(belka = if (left <= 0) Belka(false, belkaAway(dice, false)) else Belka(true, left))
        } else {
            val left = belka.ticks - 1
            next.copy(belka = if (left <= 0) Belka(true, belkaStays(dice)) else Belka(false, left))
        }
        return next.copy(rng = dice.rng)
    }

    /** The net the arrows put it at, from where it is: left and right keep its height,
     *  up and down keep its side (the web app's arrow keys). */
    fun railFor(net: Int, direction: Direction): Int = when (direction) {
        Direction.LEFT -> net % 2
        Direction.RIGHT -> 2 + net % 2
        Direction.UP -> if (net < 2) 0 else 2
        Direction.DOWN -> if (net < 2) 1 else 3
    }
}

internal enum class Direction { UP, DOWN, LEFT, RIGHT }

@Serializable internal data class SputnikPiece(val rail: Int, val step: Int)
@Serializable internal data class Belka(val out: Boolean, val ticks: Int)
@Serializable internal data class Crash(val side: String, val step: Int)

@Serializable
internal data class SputnikState(
    val variant: String,
    val rng: Long,
    val tick: Int,
    val caught: Int,
    val halves: Int,
    val net: Int,
    val sputniks: List<SputnikPiece>,
    val belka: Belka,
    val crash: Crash?,
    val over: Boolean
)
