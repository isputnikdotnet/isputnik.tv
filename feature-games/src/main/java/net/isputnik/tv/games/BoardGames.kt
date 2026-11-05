package net.isputnik.tv.games

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// The two board games the TV plays, read from the state the server sends. The TV knows
// how to DRAW a position and how to pick one of the legal moves the server lists; it never
// decides what is legal. Cells are counted on the screen as White sees the board: row 0 at
// the top (Black's side, rank 8), column 0 on the left (the a-file).

internal enum class BoardKind(val id: String) {
    CHESS("chess"), CHECKERS("checkers");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id }
    }
}

/** A piece on a cell: whose (seat 0 is White, who sits at the bottom) and what. */
internal data class BoardPiece(val seat: Int, val letter: Char, val king: Boolean = false)

/** One legal move as a walk over cells: from and to for chess, every landing for checkers.
 *  Promotions are the same walk with a different `promotion`. */
internal data class Candidate(val path: List<Int>, val move: JsonElement, val promotion: Char? = null)

internal data class Position(
    val pieces: Map<Int, BoardPiece>,
    val lastMove: List<Int>,
    /** The cell of a king in check. */
    val check: Int?,
    val status: GameStatus,
    /** The moves made so far: the length is the move count, the last one is highlighted. */
    val moveCount: Int
)

internal fun cellOfSquare(name: String): Int {
    val file = name[0] - 'a'
    val rank = name[1] - '1'
    return (7 - rank) * 8 + file
}

internal fun squareOfCell(cell: Int): String = "${'a' + cell % 8}${8 - cell / 8}"

internal fun statusOf(state: JsonObject): GameStatus {
    val s = state.getValue("status").jsonObject
    return GameStatus(
        kind = s.getValue("kind").jsonPrimitive.content,
        turn = s["turn"]?.jsonPrimitive?.intOrNull,
        winner = s["winner"]?.jsonPrimitive?.intOrNull,
        reason = s["reason"]?.jsonPrimitive?.contentOrNullSafe()
    )
}

private fun JsonPrimitive.contentOrNullSafe(): String? = if (this is kotlinx.serialization.json.JsonNull) null else content

internal fun positionOf(kind: BoardKind, state: JsonObject): Position = when (kind) {
    BoardKind.CHESS -> {
        val board = state.getValue("board").jsonPrimitive.content
        val pieces = buildMap {
            board.forEachIndexed { i, letter ->
                if (letter != '.') put((7 - i / 8) * 8 + i % 8, BoardPiece(if (letter.isUpperCase()) 0 else 1, letter))
            }
        }
        val moves = state["moves"]?.jsonArray.orEmpty()
        val last = moves.lastOrNull()?.jsonObject?.let { listOf(cellOfSquare(it.getValue("from").jsonPrimitive.content), cellOfSquare(it.getValue("to").jsonPrimitive.content)) }.orEmpty()
        val toMove = state["toMove"]?.jsonPrimitive?.intOrNull ?: 0
        val inCheck = state["check"]?.jsonPrimitive?.booleanOrNull == true
        val king = if (toMove == 0) 'K' else 'k'
        Position(pieces, last, if (inCheck) pieces.entries.firstOrNull { it.value.letter == king }?.key else null, statusOf(state), moves.size)
    }
    BoardKind.CHECKERS -> {
        val board = state.getValue("board").jsonArray
        val pieces = buildMap {
            board.forEachIndexed { i, cell ->
                if (cell is JsonObject) {
                    val seat = cell.getValue("player").jsonPrimitive.int
                    put(i, BoardPiece(seat, if (seat == 0) 'w' else 'b', cell.getValue("king").jsonPrimitive.boolean))
                }
            }
        }
        val moves = state["moves"]?.jsonArray.orEmpty()
        val last = moves.lastOrNull()?.jsonObject?.get("path")?.jsonArray?.map { it.jsonPrimitive.int }.orEmpty()
        Position(pieces, last, null, statusOf(state), moves.size)
    }
}

internal fun candidatesOf(kind: BoardKind, legal: List<JsonElement>): List<Candidate> = legal.map { move ->
    val o = move.jsonObject
    when (kind) {
        BoardKind.CHESS -> Candidate(
            listOf(cellOfSquare(o.getValue("from").jsonPrimitive.content), cellOfSquare(o.getValue("to").jsonPrimitive.content)),
            move,
            o["promotion"]?.jsonPrimitive?.contentOrNullSafe()?.firstOrNull()
        )
        BoardKind.CHECKERS -> Candidate((o.getValue("path") as JsonArray).map { it.jsonPrimitive.int }, move)
    }
}

/** Picking a move one cell at a time from the legal ones: the piece, then where it goes (and
 *  for a checkers capture, each landing in turn). Pure, so it can be tested without a screen. */
internal class MovePicker(private val candidates: List<Candidate>) {
    var chosen: List<Int> = emptyList()
        private set

    private fun matching() = candidates.filter { it.path.size > chosen.size && it.path.subList(0, chosen.size) == chosen || it.path == chosen }

    /** Cells holding a piece that can move: where a first press is accepted. */
    val movable: Set<Int> get() = candidates.map { it.path.first() }.toSet()

    /** Where the next press may go, once a piece is picked. */
    val targets: Set<Int> get() = if (chosen.isEmpty()) emptySet() else matching().filter { it.path.size > chosen.size }.map { it.path[chosen.size] }.toSet()

    sealed interface Result {
        data object Nothing : Result
        data object Picked : Result
        /** One move is chosen; more than one means the promotion is still to choose. */
        data class Done(val moves: List<Candidate>) : Result
    }

    fun press(cell: Int): Result {
        if (chosen.isNotEmpty() && cell == chosen.first() && chosen.size == 1) {
            chosen = emptyList()
            return Result.Picked
        }
        if (cell in targets) {
            chosen = chosen + cell
            val complete = candidates.filter { it.path == chosen }
            val longer = candidates.any { it.path.size > chosen.size && it.path.subList(0, chosen.size) == chosen }
            if (complete.isNotEmpty() && !longer) {
                chosen = emptyList()
                return Result.Done(complete)
            }
            return Result.Picked
        }
        if (cell in movable && chosen.size <= 1) {
            chosen = listOf(cell)
            return Result.Picked
        }
        return Result.Nothing
    }

    fun clear(): Boolean {
        if (chosen.isEmpty()) return false
        chosen = emptyList()
        return true
    }
}
