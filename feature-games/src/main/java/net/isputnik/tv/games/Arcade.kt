package net.isputnik.tv.games

// The games for one player against the clock, played on the TV. Their rules are
// ports of the games engine's (isputnik.games, packages/engine/src/arcade), and
// they must stay exact: the server replays every run from its seed and input log
// with ITS engine, and keeps the score that comes out. A port that drifts gets its
// runs refused. ArcadeGoldenTest replays runs exported from the engine
// (scripts/export-tv.ts in the games repo) and checks every snapshot field by field.

/** The engine's mulberry32 step (random.ts randomStep): a value in [0, 1) and the next
 *  state. States are kept as JavaScript keeps them, unsigned 32-bit, so they compare. */
internal fun randomStep(state: Long): Pair<Double, Long> {
    val a = state.toInt() + 0x6d2b79f5
    var t = a
    t = (t xor (t ushr 15)) * (t or 1)
    t = t xor (t + (t xor (t ushr 7)) * (t or 61))
    val value = ((t xor (t ushr 14)).toLong() and 0xffffffffL).toDouble() / 4294967296.0
    return value to (a.toLong() and 0xffffffffL)
}

/** Draws from a run's generator, as the engine's Dice does. */
internal class Dice(var rng: Long) {
    fun next(): Double {
        val (value, next) = randomStep(rng)
        rng = next
        return value
    }

    /** A whole number from `from` to `from + span - 1`. */
    fun between(from: Int, span: Int): Int = from + kotlin.math.floor(next() * span).toInt()
}

/** JavaScript's Math.round: halves go up, -2.5 to -2. */
internal fun jsRound(x: Double): Int = kotlin.math.floor(x + 0.5).toInt()

