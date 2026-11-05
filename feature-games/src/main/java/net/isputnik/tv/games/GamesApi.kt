package net.isputnik.tv.games

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import net.isputnik.tv.core.ApiClient

// The games server (isputnik.games, through home's gateway at /games) and home's two games
// routes. The TV holds no board-game rules: every position comes from the server, with the
// legal moves in it (POST /play for a game on this TV, ?legal=1 on a match). Only what the
// app uses is read: a newer server can't break an older TV.

@Serializable internal data class GamesStatus(val enabled: Boolean = false)
@Serializable internal data class Member(val id: String, val name: String, val guest: Boolean = false)
@Serializable internal data class Members(val members: List<Member> = emptyList())

@Serializable internal data class GameStatus(val kind: String, val turn: Int? = null, val winner: Int? = null, val reason: String? = null)

/** What both board games' states share, and what each adds; the rest is ignored. */
@Serializable
internal data class BoardState(
    val status: GameStatus,
    /** Chess: a1…h8 as 64 letters. Checkers: absent (see [pieces]). */
    val board: JsonElement? = null,
    val check: Boolean = false,
    val toMove: Int? = null
)

@Serializable internal data class PlayAnswer(
    val moves: List<JsonElement> = emptyList(),
    val state: JsonObject,
    val legalMoves: List<JsonElement> = emptyList(),
    val feed: List<JsonObject> = emptyList(),
    val computerMove: JsonElement? = null
)

@Serializable
internal data class MatchSummary(
    val id: String,
    val game: String,
    val status: String,
    val players: List<Member>,
    val createdBy: String,
    val moveCount: Int = 0,
    val turn: Int? = null,
    val winner: Int? = null,
    val finishReason: String? = null,
    val updatedAt: String = ""
)
@Serializable internal data class MatchList(val matches: List<MatchSummary> = emptyList())

@Serializable
internal data class MatchDetail(
    val id: String,
    val game: String,
    val status: String,
    val players: List<Member>,
    val createdBy: String,
    val moveCount: Int = 0,
    val turn: Int? = null,
    val winner: Int? = null,
    val finishReason: String? = null,
    val state: JsonObject,
    val feed: List<JsonObject> = emptyList(),
    val legalMoves: List<JsonElement>? = null
)

@Serializable internal data class Invitation(val id: String, val game: String, val from: Member)
@Serializable internal data class YourMove(val id: String, val game: String, val opponent: Member)
@Serializable internal data class Activity(val invitations: List<Invitation> = emptyList(), val yourMove: List<YourMove> = emptyList())

@Serializable internal data class ArcadeAnswer(val recorded: Boolean = false, val score: Int = 0, val best: Int = 0)
@Serializable internal data class ArcadeRow(val id: String, val name: String, val game: String, val variant: String, val best: Int, val played: Int)
@Serializable internal data class ArcadeStats(val all: List<ArcadeRow> = emptyList())
@Serializable internal data class FamilyStats(val arcade: ArcadeStats = ArcadeStats())

@Serializable private data class Empty(val ok: Boolean? = null)

internal class GamesApi(val api: ApiClient) {
    suspend fun status(): GamesStatus = api.get("/api/games/status")
    suspend fun members(): List<Member> = api.get<Members>("/api/games/members").members
    suspend fun summary(): Activity = api.get("$BASE/summary")
    suspend fun matches(): List<MatchSummary> = api.get<MatchList>("$BASE/matches").matches
    suspend fun match(id: String): MatchDetail = api.get("$BASE/matches/$id?legal=1")

    /** A position on this TV: the server replays the moves and, against the computer, plays its reply. */
    suspend fun play(game: String, moves: List<JsonElement>, computer: Computer?): PlayAnswer {
        val body = buildJsonObject {
            put("game", game)
            putJsonObject("options") {}
            put("moves", buildJsonArray { moves.forEach { add(it) } })
            if (computer != null) putJsonObject("computer") {
                put("seat", computer.seat)
                put("level", computer.level)
            }
        }
        return api.json.decodeFromString(api.send("POST", "$BASE/play", body.toString()))
    }

    suspend fun invite(game: String, opponent: Member, seat: Int): MatchSummary {
        val body = buildJsonObject {
            put("game", game)
            putJsonObject("options") {}
            putJsonObject("opponent") {
                put("id", opponent.id)
                put("name", opponent.name)
            }
            put("seat", seat)
        }
        return api.json.decodeFromString(api.send("POST", "$BASE/matches", body.toString()))
    }

    suspend fun accept(id: String) { api.send("POST", "$BASE/matches/$id/accept") }
    suspend fun decline(id: String) { api.send("POST", "$BASE/matches/$id/decline") }
    suspend fun cancel(id: String) { api.send("POST", "$BASE/matches/$id/cancel") }
    suspend fun resign(id: String) { api.send("POST", "$BASE/matches/$id/resign") }

    suspend fun move(id: String, move: JsonElement, moveCount: Int): MatchDetail {
        val body = buildJsonObject {
            put("move", move)
            put("moveCount", moveCount)
        }
        return api.json.decodeFromString(api.send("POST", "$BASE/matches/$id/moves", body.toString()))
    }

    /** A finished game against the computer, for the family's vs Computer board. */
    suspend fun reportComputer(game: String, level: String, result: String, key: String) {
        val body = buildJsonObject {
            put("game", game)
            put("level", level)
            put("result", result)
            put("key", key)
        }
        api.send("POST", "$BASE/computer-results", body.toString())
    }

    /** A finished arcade run, as its seed and inputs: the server replays it for the score. */
    suspend fun reportArcade(game: String, variant: String, seed: Long, key: String, log: List<Pair<Int, JsonElement>>): ArcadeAnswer {
        val body = buildJsonObject {
            put("game", game)
            put("variant", variant)
            put("seed", seed)
            put("key", key)
            put("log", buildJsonArray {
                log.forEach { (tick, input) -> add(buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(tick)); add(input) }) }
            })
        }
        return api.json.decodeFromString(api.send("POST", "$BASE/arcade/scores", body.toString()))
    }

    suspend fun stats(): FamilyStats = api.get("$BASE/stats")

    companion object {
        const val BASE = "/games/api"
    }
}

internal data class Computer(val seat: Int, val level: String)

/** A game's own id for a result, as the browser makes one (shared/gameKey). */
internal fun newGameKey(): String {
    val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    val random = java.security.SecureRandom()
    return (1..20).map { letters[random.nextInt(letters.length)] }.joinToString("")
}

/** A seed as the engine takes one: a whole number from 0 to 2³²−1. */
internal fun newSeed(): Long = java.security.SecureRandom().nextInt().toLong() and 0xffffffffL
