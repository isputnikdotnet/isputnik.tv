package net.isputnik.tv.games

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.isputnik.tv.core.ApiException

// A board game in progress on the TV, whichever way it is played. The screen draws `view`
// and calls move(); where the position comes from is the session's business: the server's
// /play for a game on this TV (against the computer, or two on the sofa), the match API
// for a game against someone on their own phone or computer.

/** Who sits in a seat, as the panel names them. */
internal sealed interface Seat {
    data object You : Seat
    data object Computer : Seat
    /** Two on the sofa: White and Black, both this person's. */
    data object Sofa : Seat
    data class Someone(val name: String) : Seat
}

internal data class GameView(
    val position: Position,
    /** The legal moves when the person at the screen is to move; empty otherwise. */
    val candidates: List<Candidate>,
    val seats: List<Seat>,
    /** The seat drawn at the bottom. */
    val bottom: Int,
    /** The last few moves in words, newest last. */
    val log: List<String>,
    /** "invited", "playing", "finished", or a match that went away ("declined", "cancelled"). */
    val stage: String,
    /** An invitation the person here is to answer. */
    val invitedHere: Boolean = false,
    /** The computer (or the other player) is to move. */
    val waiting: Boolean = false
)

internal abstract class GameSession(protected val scope: CoroutineScope) {
    var view by mutableStateOf<GameView?>(null)
        protected set
    var busy by mutableStateOf(false)
        protected set
    var failed by mutableStateOf(false)
        protected set
    /** True once a move or resignation was refused: shown, then the position is reloaded. */
    var refused by mutableStateOf(false)
        protected set

    abstract val kind: BoardKind
    abstract fun start()
    abstract fun move(candidate: Candidate)
    abstract fun resign()
    open fun stop() {}

    protected fun logOf(feed: List<JsonObject>): List<String> = feed.takeLast(LOG_LINES).map { line ->
        val number = line["number"]?.jsonPrimitive?.content
        val text = line["san"]?.jsonPrimitive?.content ?: line["notation"]?.jsonPrimitive?.content ?: ""
        val by = line["by"]?.jsonPrimitive?.content
        if (number != null && by == "0") "$number. $text" else if (number != null) "$number… $text" else text
    }

    companion object {
        const val LOG_LINES = 8
    }
}

/** A game kept on this TV between visits, one per board game. */
@Serializable
internal data class SavedGame(
    val game: String,
    /** "computer" or "sofa". */
    val mode: String,
    val level: String = "normal",
    /** The person's seat against the computer. */
    val seat: Int = 0,
    val key: String,
    val moves: List<JsonElement> = emptyList(),
    /** How a resigned game ended: the seat that gave up. */
    val resigned: Int? = null,
    val reported: Boolean = false,
    /** Over: the setup page offers no Continue for it. */
    val finished: Boolean = false
)

internal class Saves(context: Context, userId: String) {
    private val prefs = context.getSharedPreferences("games-$userId", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(game: String): SavedGame? = prefs.getString(game, null)?.let { runCatching { json.decodeFromString<SavedGame>(it) }.getOrNull() }
    fun save(saved: SavedGame) = prefs.edit().putString(saved.game, json.encodeToString(SavedGame.serializer(), saved)).apply()
    fun clear(game: String) = prefs.edit().remove(game).apply()
}

/** Against the computer, or two on the sofa: the moves are kept here and every position is
 *  the server's answer to them. */
internal class LocalSession(
    private val games: GamesApi,
    private val saves: Saves,
    initial: SavedGame,
    scope: CoroutineScope
) : GameSession(scope) {
    private var saved = initial
    override val kind = BoardKind.of(initial.game)!!
    private val computer = if (saved.mode == "computer") Computer(1 - saved.seat, saved.level) else null
    private val seats = when {
        computer == null -> listOf(Seat.Sofa, Seat.Sofa)
        saved.seat == 0 -> listOf(Seat.You, Seat.Computer)
        else -> listOf(Seat.Computer, Seat.You)
    }

    override fun start() = ask(saved.moves, withComputer = true)

    private fun show(answer: PlayAnswer, computerToMove: Boolean) {
        var position = positionOf(kind, answer.state)
        val resigned = saved.resigned
        if (resigned != null && position.status.kind == "playing") {
            position = position.copy(status = GameStatus("finished", winner = 1 - resigned, reason = "resigned"))
        }
        val over = position.status.kind != "playing"
        view = GameView(
            position = position,
            candidates = if (over || computerToMove) emptyList() else candidatesOf(kind, answer.legalMoves),
            seats = seats,
            bottom = if (computer != null) saved.seat else 0,
            log = logOf(answer.feed),
            stage = if (over) "finished" else "playing",
            waiting = computerToMove && !over
        )
        if (over && !saved.finished) {
            saved = saved.copy(finished = true)
            saves.save(saved)
        }
        if (over) report(position.status)
    }

    /** The position after `moves`; with the computer's reply when it is the computer's move. */
    private fun ask(moves: List<JsonElement>, withComputer: Boolean) {
        busy = true
        scope.launch {
            try {
                val answer = games.play(kind.id, moves, if (withComputer) computer else null)
                saved = saved.copy(moves = answer.moves)
                saves.save(saved)
                val status = statusOf(answer.state)
                val computerToMove = computer != null && status.kind == "playing" && status.turn == computer.seat
                show(answer, computerToMove)
                failed = false
                // The person's move is on the board; now the computer thinks about its reply.
                if (computerToMove && !withComputer) ask(answer.moves, withComputer = true)
            } catch (e: Exception) {
                if (e is ApiException && e.code == 400) {
                    // A save the server no longer accepts: start that game afresh.
                    refused = true
                    saved = saved.copy(moves = emptyList(), resigned = null, reported = false, key = newGameKey())
                    saves.save(saved)
                } else {
                    failed = true
                }
            } finally {
                busy = false
            }
        }
    }

    override fun move(candidate: Candidate) {
        if (busy) return
        refused = false
        view = view?.copy(candidates = emptyList(), waiting = computer != null)
        ask(saved.moves + candidate.move, withComputer = false)
    }

    override fun resign() {
        val current = view ?: return
        val turn = current.position.status.turn ?: return
        saved = saved.copy(resigned = if (computer != null) saved.seat else turn)
        saves.save(saved)
        ask(saved.moves, withComputer = false)
    }

    private fun report(status: GameStatus) {
        if (computer == null || saved.reported) return
        val result = when (status.winner) {
            null -> "drawn"
            saved.seat -> "won"
            else -> "lost"
        }
        val key = saved.key
        scope.launch {
            runCatching { games.reportComputer(kind.id, saved.level, result, key) }.onSuccess {
                saved = saved.copy(reported = true)
                saves.save(saved)
            }
        }
    }
}

/** Against someone on their own screen: the server's match, looked at every few seconds. */
internal class MatchSession(
    private val games: GamesApi,
    private val matchId: String,
    private val userId: String,
    scope: CoroutineScope
) : GameSession(scope) {
    private var detail: MatchDetail? = null
    override val kind: BoardKind get() = BoardKind.of(detail?.game ?: "chess") ?: BoardKind.CHESS
    private var polling: Job? = null

    override fun start() {
        polling?.cancel()
        polling = scope.launch {
            while (isActive) {
                load()
                val d = detail
                val mine = d != null && d.status == "playing" && d.players.getOrNull(d.turn ?: -1)?.id == userId
                if (d != null && d.status != "playing" && d.status != "invited") break
                delay(if (mine) SLOW_MS else POLL_MS)
            }
        }
    }

    override fun stop() {
        polling?.cancel()
    }

    private suspend fun load() {
        try {
            show(games.match(matchId))
            failed = false
        } catch (e: Exception) {
            failed = true
        }
    }

    private fun show(d: MatchDetail) {
        detail = d
        val game = BoardKind.of(d.game) ?: return
        val me = d.players.indexOfFirst { it.id == userId }.coerceAtLeast(0)
        val seats = d.players.mapIndexed { i, p -> if (i == me) Seat.You else Seat.Someone(p.name) }
        var position = positionOf(game, d.state)
        if (d.status == "finished" && position.status.kind == "playing") {
            position = position.copy(status = GameStatus("finished", winner = d.winner, reason = d.finishReason))
        }
        val myMove = d.status == "playing" && d.turn == me
        view = GameView(
            position = position,
            candidates = if (myMove) candidatesOf(game, d.legalMoves.orEmpty()) else emptyList(),
            seats = seats,
            bottom = me,
            log = logOf(d.feed),
            stage = d.status,
            invitedHere = d.status == "invited" && d.createdBy != userId,
            waiting = d.status == "playing" && !myMove
        )
    }

    override fun move(candidate: Candidate) {
        val d = detail ?: return
        if (busy) return
        busy = true
        refused = false
        view = view?.copy(candidates = emptyList())
        scope.launch {
            try {
                show(games.move(matchId, candidate.move, d.moveCount))
            } catch (e: ApiException) {
                refused = true
                load()
            } catch (e: Exception) {
                failed = true
            } finally {
                busy = false
            }
            start()
        }
    }

    override fun resign() = act { games.resign(matchId) }
    fun accept() = act { games.accept(matchId) }
    fun decline() = act { games.decline(matchId) }
    fun withdraw() = act { games.cancel(matchId) }

    private fun act(call: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                call()
            } catch (_: ApiException) {
                refused = true
            } catch (_: Exception) {
                failed = true
            } finally {
                busy = false
            }
            load()
            start()
        }
    }

    companion object {
        /** As the web app's match page: quick while the other side is to move. */
        const val POLL_MS = 2500L
        const val SLOW_MS = 10000L
    }
}
