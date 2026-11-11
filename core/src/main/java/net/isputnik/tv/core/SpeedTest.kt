package net.isputnik.tv.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import kotlin.math.roundToInt

// How fast this TV pulls bytes from home (GET /api/speed-test, home 4.84+), in the words
// people use: "42 Mbit/s — enough for 1080p". The bytes come through the session's own
// client, the same road the films take.

private const val TEST_BYTES = 100L * 1024 * 1024
/** Connecting and TCP's slow start aren't the link: the clock starts after this much. */
private const val WARM_UP_BYTES = 512L * 1024
private const val TEST_NANOS = 8_000_000_000L

/** Counts what came in after the warm-up, against the time it took. Pure, to test. */
class SpeedMeter(private val warmUpBytes: Long = WARM_UP_BYTES, private val limitNanos: Long = TEST_NANOS) {
    private var seen = 0L
    private var counted = 0L
    private var startedAt = -1L
    private var lastAt = -1L

    /** `bytes` more arrived at `now` (System.nanoTime()). */
    fun add(bytes: Long, now: Long) {
        seen += bytes
        if (startedAt < 0) {
            if (seen >= warmUpBytes) startedAt = now
            return
        }
        counted += bytes
        lastAt = now
    }

    /** Bits per second so far; zero until there is something to divide. */
    val bitsPerSecond: Double get() = if (startedAt < 0 || lastAt <= startedAt) 0.0 else bitsPerSecond(counted, lastAt - startedAt)

    /** Enough has been measured: the time is up. */
    fun done(now: Long): Boolean = startedAt >= 0 && now - startedAt >= limitNanos

    /** 0 to 1, for a bar while it runs. */
    fun progress(now: Long): Float = if (startedAt < 0) 0f else ((now - startedAt).toFloat() / limitNanos).coerceIn(0f, 1f)
}

sealed interface SpeedResult {
    data class Measured(val bitsPerSecond: Double) : SpeedResult
    /** A home from before the speed test answers 404. */
    data object OldServer : SpeedResult
    data object Failed : SpeedResult
}

/** Reads for about eight seconds, telling `onProgress` the speed so far; stops at once when
 *  the screen that asked goes (the coroutine is cancelled). */
suspend fun testSpeed(api: ApiClient, onProgress: (bitsPerSecond: Double, done: Float) -> Unit): SpeedResult = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(api.absolute("/api/speed-test?bytes=$TEST_BYTES")).header("Cache-Control", "no-store").build()
    val call = api.http.newCall(request)
    try {
        call.execute().use { response ->
            if (response.code == 404) return@withContext SpeedResult.OldServer
            if (!response.isSuccessful) return@withContext SpeedResult.Failed
            val source = response.body?.source() ?: return@withContext SpeedResult.Failed
            val meter = SpeedMeter()
            val buffer = ByteArray(64 * 1024)
            var lastReport = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = source.read(buffer)
                val now = System.nanoTime()
                if (read < 0) break
                meter.add(read.toLong(), now)
                if (meter.done(now)) break
                if (now - lastReport > 250_000_000L) {
                    lastReport = now
                    withContext(Dispatchers.Main) { onProgress(meter.bitsPerSecond, meter.progress(now)) }
                }
            }
            call.cancel()
            if (meter.bitsPerSecond > 0) SpeedResult.Measured(meter.bitsPerSecond) else SpeedResult.Failed
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        call.cancel()
        throw e
    } catch (_: Exception) {
        SpeedResult.Failed
    }
}

/** Bits per second, from bytes read in a time. */
fun bitsPerSecond(bytes: Long, nanos: Long): Double = if (nanos <= 0) 0.0 else bytes * 8.0 / (nanos / 1e9)

/** "42 Mbit/s", "8.5 Mbit/s", "850 kbit/s": two figures that mean something, no more. The
 *  units come in the person's language. */
fun formatSpeed(bitsPerSecond: Double, mbit: String = "Mbit/s", kbit: String = "kbit/s"): String {
    val megabits = bitsPerSecond / 1_000_000
    return when {
        megabits >= 10 -> "${megabits.roundToInt()} $mbit"
        megabits >= 1 -> {
            val tenths = (megabits * 10).roundToInt()
            (if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}") + " $mbit"
        }
        else -> "${(bitsPerSecond / 1000).roundToInt()} $kbit"
    }
}

/** The best picture a speed carries without stopping. The engine's rungs top out at 2, 4
 *  and 10 Mbit/s (480p, 720p, 1080p converted); a file played as it is runs to 40 for a
 *  Blu-ray or 4K remux. Half again on top, for a link that is never steady. */
enum class SpeedVerdict { ORIGINAL_4K, P1080, P720, P480, TOO_SLOW }

fun speedVerdict(bitsPerSecond: Double): SpeedVerdict {
    val mbit = bitsPerSecond / 1_000_000
    return when {
        mbit >= 60 -> SpeedVerdict.ORIGINAL_4K
        mbit >= 15 -> SpeedVerdict.P1080
        mbit >= 6 -> SpeedVerdict.P720
        mbit >= 3 -> SpeedVerdict.P480
        else -> SpeedVerdict.TOO_SLOW
    }
}
