package net.isputnik.tv.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import net.isputnik.tv.core.TvButton
import net.isputnik.tv.core.TvColors
import net.isputnik.tv.core.requestWhenReady

// A board game on the TV: the board as tall as the screen allows, and a panel beside it —
// who plays whom, whose move, the last moves, and what can be done (resign, answer an
// invitation, a new game). On the board a cursor walks the squares with the arrows; OK picks
// a piece up (its moves light up) and OK again puts it down. Back puts a picked piece back,
// and otherwise leaves the game, which is kept: on the server, or on this TV.

// The Classic board of the web app (chess.css).
private val Light = Color(0xFFECDCBF)
private val Dark = Color(0xFFA97F5A)
private val LastTint = Color(0x6BDEBA2E)
private val PickedTint = Color(0xADDEBA2E)
private val MarkTint = Color(0x6B1E4828)
private val CheckTint = Color(0xE6D6261C)
private val WhiteFill = Color(0xFFFBF8F1)
private val WhiteLine = Color(0xFF2A2520)
private val BlackFill = Color(0xFF2B2622)
private val BlackLine = Color(0xFF0F0D0B)
private val KingGold = Color(0xFFC4A76B)

@Composable
internal fun BoardScreen(
    session: GameSession,
    title: String,
    subtitle: String,
    onNewGame: (() -> Unit)?,
    onLeave: () -> Unit
) {
    DisposableEffect(session) {
        session.start()
        onDispose { session.stop() }
    }
    val view = session.view
    if (view == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(if (session.failed) R.string.games_load_failed else R.string.games_loading), color = TvColors.Muted, fontSize = 18.sp)
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
        val side = minOf(maxHeight, maxWidth - 340.dp)
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Board(session, view, Modifier.size(side))
            Spacer(Modifier.width(32.dp))
            Panel(session, view, title, subtitle, onNewGame, onLeave, Modifier.width(300.dp).fillMaxHeight())
        }
    }
}

@Composable
private fun Board(session: GameSession, view: GameView, modifier: Modifier) {
    val kind = session.kind
    val context = LocalContext.current
    val art = remember { if (kind == BoardKind.CHESS) ChessArt.get(context) else null }
    val flip = view.bottom == 1
    val shown = { cell: Int -> if (flip) 63 - cell else cell }

    val picker = remember(view.candidates) { MovePicker(view.candidates) }
    var changed by remember { mutableIntStateOf(0) }
    var promotions by remember(view.candidates) { mutableStateOf<List<Candidate>?>(null) }
    var promotionIndex by remember { mutableIntStateOf(0) }
    // The cursor starts on a piece that can move, nearest the person.
    var cursor by remember { mutableIntStateOf(-1) }
    LaunchedEffect(view.candidates) {
        if (cursor < 0) cursor = picker.movable.maxOfOrNull { shown(it) } ?: 52
    }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestWhenReady() }

    val press = {
        val promo = promotions
        if (promo != null) {
            session.move(promo[promotionIndex])
            promotions = null
        } else when (val result = picker.press(shown(cursor))) {
            is MovePicker.Result.Done -> if (result.moves.size == 1) session.move(result.moves[0]) else {
                promotions = result.moves.sortedBy { "qrbn".indexOf(it.promotion ?: 'q') }
                promotionIndex = 0
            }
            else -> Unit
        }
        changed++
    }

    Box(
        modifier
            .focusRequester(focus)
            .onFocusChanged { focused = it.hasFocus }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val promo = promotions
                if (promo != null) {
                    when (event.key) {
                        Key.DirectionLeft -> { promotionIndex = (promotionIndex + promo.size - 1) % promo.size; true }
                        Key.DirectionRight -> { promotionIndex = (promotionIndex + 1) % promo.size; true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { press(); true }
                        Key.Back, Key.Escape -> { promotions = null; true }
                        else -> true
                    }
                } else when (event.key) {
                    Key.DirectionUp -> if (cursor >= 8) { cursor -= 8; true } else false
                    Key.DirectionDown -> if (cursor < 56) { cursor += 8; true } else false
                    Key.DirectionLeft -> if (cursor % 8 > 0) { cursor -= 1; true } else false
                    Key.DirectionRight -> if (cursor % 8 < 7) { cursor += 1; true } else false
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { press(); true }
                    Key.Back, Key.Escape -> picker.clear().also { if (it) changed++ }
                    else -> false
                }
            }
            .focusable()
            .clip(RoundedCornerShape(6.dp))
    ) {
        @Suppress("UNUSED_EXPRESSION") changed
        val accent = TvColors.Accent
        Canvas(Modifier.fillMaxSize()) {
            val cell = size.width / 8
            val chosen = picker.chosen.toSet()
            val targets = picker.targets
            for (display in 0 until 64) {
                val board = shown(display)
                val row = display / 8
                val col = display % 8
                val topLeft = Offset(col * cell, row * cell)
                val dark = (row + col) % 2 == 1
                drawRect(if (dark) Dark else Light, topLeft, Size(cell, cell))
                if (board in view.position.lastMove) drawRect(LastTint, topLeft, Size(cell, cell))
                if (board in chosen) drawRect(PickedTint, topLeft, Size(cell, cell))
                if (board == view.position.check) drawCircle(CheckTint, cell * 0.48f, topLeft + Offset(cell / 2, cell / 2))
                val piece = view.position.pieces[board]
                if (piece != null) drawPiece(kind, piece, art, topLeft, cell)
                if (board in targets) {
                    if (piece == null) drawCircle(MarkTint, cell * 0.15f, topLeft + Offset(cell / 2, cell / 2))
                    else drawCircle(MarkTint, cell * 0.46f, topLeft + Offset(cell / 2, cell / 2), style = Stroke(cell * 0.08f))
                }
            }
            if (focused && cursor >= 0 && promotions == null) {
                // The theme's accent over a dark edge, so it shows on either colour of square
                // whatever the theme (some accents are pale).
                val inset = cell * 0.04f
                val at = Offset((cursor % 8) * cell + inset, (cursor / 8) * cell + inset)
                val box = Size(cell - 2 * inset, cell - 2 * inset)
                drawRoundRect(Color(0xCC0B1218), at, box, CornerRadius(cell * 0.08f), style = Stroke(cell * 0.12f))
                drawRoundRect(accent, at, box, CornerRadius(cell * 0.08f), style = Stroke(cell * 0.06f))
            }
        }
        promotions?.let { promo ->
            Row(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(10.dp)).background(TvColors.Surface).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                promo.forEachIndexed { i, candidate ->
                    val letter = candidate.promotion ?: 'q'
                    val seat = view.position.status.turn ?: 0
                    Box(
                        Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(Light)
                            .then(if (i == promotionIndex) Modifier.border(4.dp, accent, RoundedCornerShape(8.dp)) else Modifier)
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawPiece(kind, BoardPiece(seat, if (seat == 0) letter.uppercaseChar() else letter), art, Offset.Zero, size.width)
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawPiece(kind: BoardKind, piece: BoardPiece, art: ChessArt?, topLeft: Offset, cell: Float) {
    if (kind == BoardKind.CHESS) {
        val picture = art?.pieces?.get(piece.letter) ?: return
        val inset = cell * 0.04f
        drawIntoCanvas { it.drawPicture(picture, topLeft.x + inset, topLeft.y + inset, cell - 2 * inset, cell - 2 * inset) }
        return
    }
    val centre = topLeft + Offset(cell / 2, cell / 2)
    val r = cell * 0.36f
    val (fill, line) = if (piece.seat == 0) WhiteFill to WhiteLine else BlackFill to BlackLine
    drawCircle(Color(0x44000000), r, centre + Offset(0f, cell * 0.04f))
    drawCircle(fill, r, centre)
    drawCircle(line, r, centre, style = Stroke(cell * 0.035f))
    drawCircle(line.copy(alpha = 0.35f), r * 0.68f, centre, style = Stroke(cell * 0.02f))
    if (piece.king) {
        drawCircle(KingGold, r * 0.5f, centre, style = Stroke(cell * 0.06f))
        drawCircle(KingGold, r * 0.16f, centre)
    }
}

@Composable
private fun Panel(
    session: GameSession,
    view: GameView,
    title: String,
    subtitle: String,
    onNewGame: (() -> Unit)?,
    onLeave: () -> Unit,
    modifier: Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Text(title, color = TvColors.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TvColors.Muted, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))
        val turn = view.position.status.turn
        view.seats.forEachIndexed { seat, who ->
            PlayerLine(seat, nameOf(who, seat), active = view.stage == "playing" && turn == seat)
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(statusText(session.kind, view), color = TvColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        if (session.refused) Text(stringResource(R.string.games_move_refused), color = TvColors.Muted, fontSize = 13.sp)
        else if (session.failed) Text(stringResource(R.string.games_offline), color = TvColors.Muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f, fill = false).fillMaxWidth()) {
            view.log.forEach { Text(it, color = TvColors.Muted, fontSize = 14.sp, maxLines = 1) }
        }
        Spacer(Modifier.height(18.dp))
        Actions(session, view, onNewGame, onLeave)
    }
}

@Composable
private fun PlayerLine(seat: Int, name: String, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 22.dp).background(if (active) TvColors.Accent else Color.Transparent))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(16.dp).clip(CircleShape).background(if (seat == 0) WhiteFill else BlackFill).border(1.5.dp, if (seat == 0) WhiteLine else Color(0xFF8A8079), CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(name, color = if (active) TvColors.Text else TvColors.Muted, fontSize = 16.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
private fun nameOf(who: Seat, seat: Int): String = when (who) {
    Seat.You -> stringResource(R.string.games_you)
    Seat.Computer -> stringResource(R.string.games_computer)
    Seat.Sofa -> stringResource(if (seat == 0) R.string.games_white else R.string.games_black)
    is Seat.Someone -> who.name
}

@Composable
private fun statusText(kind: BoardKind, view: GameView): String {
    val status = view.position.status
    val turn = status.turn
    return when {
        view.stage == "declined" -> stringResource(R.string.games_declined)
        view.stage == "cancelled" -> stringResource(R.string.games_cancelled)
        view.stage == "invited" && view.invitedHere -> stringResource(R.string.games_invited_you)
        view.stage == "invited" -> stringResource(R.string.games_waiting_answer, nameOf(view.seats.firstOrNull { it is Seat.Someone } ?: Seat.Computer, 1))
        status.kind == "finished" -> {
            val winner = status.winner
            val reason = when (status.reason) {
                "checkmate" -> stringResource(R.string.games_reason_checkmate)
                "stalemate" -> stringResource(R.string.games_reason_stalemate)
                "resigned" -> stringResource(R.string.games_reason_resigned)
                "repetition" -> stringResource(R.string.games_reason_repetition)
                "fifty-moves" -> stringResource(R.string.games_reason_fifty)
                "material" -> stringResource(R.string.games_reason_material)
                else -> null
            }
            val result = when {
                winner == null -> stringResource(R.string.games_draw)
                view.seats[winner] == Seat.You -> stringResource(R.string.games_you_win)
                view.seats[winner] == Seat.Sofa -> stringResource(R.string.games_wins, nameOf(Seat.Sofa, winner))
                else -> stringResource(R.string.games_wins, nameOf(view.seats[winner], winner))
            }
            if (reason != null) "$result · $reason" else result
        }
        turn == null -> ""
        view.seats[turn] == Seat.Computer -> stringResource(R.string.games_computer_thinking)
        view.seats[turn] == Seat.You -> stringResource(if (kind == BoardKind.CHESS && view.position.check != null) R.string.games_your_move_check else R.string.games_your_move)
        view.seats[turn] == Seat.Sofa -> stringResource(R.string.games_side_to_move, nameOf(Seat.Sofa, turn))
        else -> stringResource(R.string.games_their_move, nameOf(view.seats[turn], turn))
    }
}

@Composable
private fun Actions(session: GameSession, view: GameView, onNewGame: (() -> Unit)?, onLeave: () -> Unit) {
    val match = session as? MatchSession
    var confirming by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            match != null && view.stage == "invited" && view.invitedHere -> {
                TvButton(stringResource(R.string.games_accept), primary = true) { match.accept() }
                TvButton(stringResource(R.string.games_decline)) { match.decline() }
            }
            match != null && view.stage == "invited" -> TvButton(stringResource(R.string.games_withdraw)) { match.withdraw() }
            view.stage == "playing" && view.position.status.kind == "playing" -> {
                TvButton(
                    stringResource(if (confirming) R.string.games_resign_confirm else R.string.games_resign),
                    Modifier.onFocusChanged { if (!it.hasFocus) confirming = false }
                ) {
                    if (confirming) {
                        confirming = false
                        session.resign()
                    } else confirming = true
                }
            }
            else -> if (onNewGame != null) TvButton(stringResource(R.string.games_new_game), primary = true, onClick = onNewGame)
        }
        TvButton(stringResource(R.string.games_leave), onClick = onLeave)
    }
}
