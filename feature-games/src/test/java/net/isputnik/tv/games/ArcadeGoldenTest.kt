package net.isputnik.tv.games

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test

/** Replays runs exported from the games engine (scripts/export-tv.ts in isputnik.games) and
 *  checks every snapshot of the port against the engine's. The server replays a TV's run with
 *  the engine, so any difference here is a score it would refuse. */
class ArcadeGoldenTest {
    private val json = Json { ignoreUnknownKeys = false }
    private val golden: JsonObject by lazy {
        val text = javaClass.classLoader!!.getResourceAsStream("arcade-golden.json")!!.bufferedReader().readText()
        json.parseToJsonElement(text).jsonObject
    }

    @Test
    fun `random steps match the engine`() {
        // mulberry32 from seed 1, as random.ts gives it.
        val (first, next) = randomStep(1)
        assertEquals(0.6270739405881613, first, 0.0)
        assertEquals(1L + 0x6d2b79f5, next)
    }

    @Test
    fun `sputnik runs replay exactly`() {
        for (run in golden.getValue("sputnik").jsonArray) {
            val o = run.jsonObject
            val variant = o.getValue("variant").jsonPrimitive.content
            val seed = o.getValue("seed").jsonPrimitive.long
            val log = o.getValue("log").jsonArray.map { it.jsonArray[0].jsonPrimitive.int to it.jsonArray[1].jsonPrimitive.int }
            val snapshots = o.getValue("snapshots").jsonArray.map { json.decodeFromJsonElement<SputnikState>(it) }.associateBy { it.tick }
            var state = Sputnik.createRun(variant, seed)
            assertEquals(snapshots[0], state)
            var net = 0
            var at = 0
            while (!state.over) {
                while (at < log.size && log[at].first <= state.tick) net = log[at++].second
                state = Sputnik.tick(state, net)
                snapshots[state.tick]?.let { assertEquals("sputnik $variant seed $seed tick ${state.tick}", it, state) }
            }
            assertEquals(o.getValue("ticks").jsonPrimitive.int, state.tick)
            assertEquals(o.getValue("score").jsonPrimitive.int, state.caught)
        }
    }

    @Test
    fun `torpedo runs replay exactly`() {
        for (run in golden.getValue("torpedo").jsonArray) {
            val o = run.jsonObject
            val seed = o.getValue("seed").jsonPrimitive.long
            val log = (o.getValue("log") as JsonArray).map { it.jsonArray[0].jsonPrimitive.int to json.decodeFromJsonElement<TorpedoInput>(it.jsonArray[1]) }
            val snapshots = o.getValue("snapshots").jsonArray.map { json.decodeFromJsonElement<TorpedoState>(it) }.associateBy { it.tick }
            var state = Torpedo.createRun(seed)
            assertEquals(snapshots[0], state)
            var input = Torpedo.INITIAL_INPUT
            var at = 0
            while (!state.over) {
                while (at < log.size && log[at].first <= state.tick) input = log[at++].second
                state = Torpedo.tick(state, input)
                snapshots[state.tick]?.let { assertEquals("torpedo seed $seed tick ${state.tick}", it, state) }
            }
            assertEquals(o.getValue("ticks").jsonPrimitive.int, state.tick)
            assertEquals(o.getValue("score").jsonPrimitive.int, state.score)
        }
    }
}
