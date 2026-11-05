package net.isputnik.tv.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import net.isputnik.tv.core.Navigator
import net.isputnik.tv.core.RestoreFocus
import net.isputnik.tv.core.TvButton
import net.isputnik.tv.core.TvColors
import net.isputnik.tv.core.remembersFocus
import kotlin.math.cos
import kotlin.math.sin

// The Games page: what waits on you (an invitation, your move), the four games a remote
// suits, and your games against the family still going. And, for Chess and Checkers, the
// page that starts one: against the computer, two on the sofa, or someone in the family.

private val Gutter = 48.dp
private val TileWidth = 220.dp
private val TileHeight = 140.dp

@Composable
internal fun LobbyPage(games: GamesApi, saves: Saves, userId: String, nav: Navigator) {
    var activity by remember { mutableStateOf<Activity?>(null) }
    var matches by remember { mutableStateOf<List<MatchSummary>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching { games.summary() }.onSuccess { activity = it }
        runCatching { games.matches() }.onSuccess { matches = it }
    }
    val waiting = activity?.let { a ->
        a.invitations.filter { it.game in TV_BOARD_GAMES }.map { Triple(it.id, it.game, stringResource(R.string.games_invites_you, it.from.name)) } +
            a.yourMove.filter { it.game in TV_BOARD_GAMES }.map { Triple(it.id, it.game, stringResource(R.string.games_your_move_against, it.opponent.name)) }
    }.orEmpty()
    val context = LocalContext.current
    val chessArt = remember { ChessArt.get(context) }
    val tileArt: DrawScope.(String) -> Unit = { game -> boardArt(game); if (game == "chess") chessPieces(chessArt) }
    val waitingIds = waiting.map { it.first }.toSet()
    val going = matches.filter { it.game in TV_BOARD_GAMES && it.id !in waitingIds && (it.status == "playing" || it.status == "invited") }

    LazyColumn(state = rememberLazyListState(), contentPadding = PaddingValues(top = 6.dp, bottom = 32.dp)) {
        item(key = "head") {
            Column(Modifier.padding(horizontal = Gutter).padding(bottom = 14.dp)) {
                Text(stringResource(R.string.games_heading), color = TvColors.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.games_heading_line), color = TvColors.Muted, fontSize = 13.sp)
            }
        }
        if (waiting.isNotEmpty()) {
            item(key = "waiting") {
                Shelf(stringResource(R.string.games_waiting_for_you)) {
                    items(waiting, key = { it.first }) { (id, game, line) ->
                        Tile(stringResource(kindName(BoardKind.of(game)!!)), line, "match/$id", art = { tileArt(game) }) { nav.push(GamesRoute.Match(id)) }
                    }
                }
            }
        }
        item(key = "games") {
            Shelf(stringResource(R.string.games_all)) {
                item(key = "chess") {
                    Tile(stringResource(R.string.games_chess), stringResource(R.string.games_chess_line), "game/chess", isDefault = waiting.isEmpty(), art = { tileArt("chess") }) { nav.push(GamesRoute.Setup("chess")) }
                }
                item(key = "checkers") {
                    Tile(stringResource(R.string.games_checkers), stringResource(R.string.games_checkers_line), "game/checkers", art = { tileArt("checkers") }) { nav.push(GamesRoute.Setup("checkers")) }
                }
                item(key = "sputnik") {
                    Tile(stringResource(R.string.games_sputnik), stringResource(R.string.games_sputnik_line), "game/sputnik", art = { sputnikArt() }) { nav.push(GamesRoute.Sputnik) }
                }
                item(key = "torpedo") {
                    Tile(stringResource(R.string.games_torpedo), stringResource(R.string.games_torpedo_line), "game/torpedo", art = { torpedoArt() }) { nav.push(GamesRoute.Torpedo) }
                }
            }
        }
        if (going.isNotEmpty()) {
            item(key = "going") {
                Shelf(stringResource(R.string.games_your_games)) {
                    items(going, key = { it.id }) { match ->
                        val other = match.players.firstOrNull { it.id != userId }?.name ?: ""
                        val line = when {
                            match.status == "invited" -> stringResource(R.string.games_invited_waiting, other)
                            else -> stringResource(R.string.games_their_move, other)
                        }
                        Tile(stringResource(kindName(BoardKind.of(match.game)!!)), line, "match/${match.id}", art = { tileArt(match.game) }) { nav.push(GamesRoute.Match(match.id)) }
                    }
                }
            }
        }
    }
    RestoreFocus(true)
}

@Composable
private fun Shelf(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(Modifier.padding(bottom = 18.dp)) {
        Text(title, color = TvColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = Gutter, bottom = 10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
private fun Tile(
    title: String,
    line: String,
    focusKey: String,
    isDefault: Boolean = false,
    width: Dp = TileWidth,
    art: DrawScope.() -> Unit,
    onClick: () -> Unit
) {
    Column(Modifier.width(width)) {
        Card(
            onClick = onClick,
            scale = CardDefaults.scale(focusedScale = 1.05f),
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, TvColors.Accent))),
            modifier = Modifier.size(width, TileHeight).remembersFocus(focusKey, isDefault)
        ) {
            Box(Modifier.fillMaxSize().background(TvColors.Surface)) {
                Canvas(Modifier.fillMaxSize()) { art() }
            }
        }
        Text(title, color = TvColors.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        Text(line, color = TvColors.Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ── The tiles' little pictures ───────────────────────────────────────────

private fun DrawScope.boardArt(game: String) {
    val cell = size.height / 5
    val left = (size.width - cell * 8) / 2
    for (row in 0 until 5) for (col in 0 until 8) {
        drawRect(if ((row + col) % 2 == 1) Color(0xFFA97F5A) else Color(0xFFECDCBF), Offset(left + col * cell, row * cell), Size(cell, cell))
    }
    if (game == "checkers") {
        for (col in 0 until 8) {
            val row = if (col % 2 == 0) 1 else 0
            drawCircle(Color(0xFF2B2622), cell * 0.36f, Offset(left + col * cell + cell / 2, row * cell + cell / 2))
            val low = if (col % 2 == 0) 3 else 4
            drawCircle(Color(0xFFFBF8F1), cell * 0.36f, Offset(left + col * cell + cell / 2, low * cell + cell / 2))
            drawCircle(Color(0xFF2A2520), cell * 0.36f, Offset(left + col * cell + cell / 2, low * cell + cell / 2), style = Stroke(cell * 0.05f))
        }
    }
}

private fun DrawScope.chessPieces(art: ChessArt) {
    val cell = size.height / 5
    val left = (size.width - cell * 8) / 2
    "rnbqkbnr".forEachIndexed { col, letter ->
        art.pieces[letter]?.let { p -> drawIntoCanvas { it.drawPicture(p, left + col * cell, 0f, cell, cell) } }
        art.pieces[letter.uppercaseChar()]?.let { p -> drawIntoCanvas { it.drawPicture(p, left + col * cell, 4 * cell, cell, cell) } }
    }
}

private fun DrawScope.sputnikArt() {
    drawRect(Color(0xFFC5CBB2))
    val ink = Color(0xFF1B2119)
    val c = Offset(size.width * 0.5f, size.height * 0.48f)
    val r = size.height * 0.14f
    for (a in listOf(150.0, 170.0, 190.0, 210.0)) {
        val rad = Math.toRadians(a)
        drawLine(ink, c + Offset((cos(rad) * r).toFloat(), (sin(rad) * r).toFloat()), c + Offset((cos(rad) * r * 3.2).toFloat(), (sin(rad) * r * 3.2).toFloat()), 3f)
    }
    drawCircle(Color(0xFFC5CBB2), r, c)
    drawCircle(ink, r, c, style = Stroke(4f))
    drawLine(Color(0xFF6F6C62), Offset(size.width * 0.1f, size.height * 0.2f), Offset(size.width * 0.9f, size.height * 0.8f), 5f)
}

private val tanker by lazy {
    PathParser().parsePathString("M0 -7 L100 -7 L96 0 L4 0 Z M5 -7 V-17 H19 V-7 Z M8 -17 V-23 H13 V-17 Z M22 -7 V-9 H86 V-7 Z M89 -7 V-14 H90.5 V-7 Z").toPath()
}

private fun DrawScope.torpedoArt() {
    val horizon = size.height * 0.5f
    drawRect(Color(0xFF889B98), size = Size(size.width, horizon))
    drawRect(Color(0xFF254440), Offset(0f, horizon), Size(size.width, size.height - horizon))
    val s = size.width / 180
    withTransform({ translate(size.width * 0.25f, horizon + 4); scale(s, s, Offset.Zero) }) { drawPath(tanker, Color(0xFF121A1C)) }
    drawLine(Color(0xB3041012), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2f)
    for (i in 0 until 4) drawCircle(Color(0xFFFFC46B), 5f - i, Offset(size.width / 2, size.height - 12 - i * 14f))
}

// ── Starting a game of Chess or Checkers ─────────────────────────────────

@Composable
internal fun SetupPage(games: GamesApi, saves: Saves, userId: String, game: String, nav: Navigator) {
    val kind = BoardKind.of(game) ?: return
    val scope = rememberCoroutineScope()
    var seat by rememberSaveable { mutableStateOf(0) }
    var members by remember { mutableStateOf<List<Member>>(emptyList()) }
    var inviteFailed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(saves.load(game)) }
    LaunchedEffect(Unit) {
        saved = saves.load(game)
        runCatching { games.members() }.onSuccess { list -> members = list.filter { it.id != userId && !it.guest } }
    }
    val context = LocalContext.current
    val chessArt = remember { if (kind == BoardKind.CHESS) ChessArt.get(context) else null }

    val startLocal = { mode: String, level: String ->
        saves.save(SavedGame(game, mode, level, if (mode == "computer") seat else 0, newGameKey()))
        nav.push(GamesRoute.Local(game, System.nanoTime()))
    }

    LazyColumn(state = rememberLazyListState(), contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 18.dp, bottom = 32.dp)) {
        item(key = "head") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(150.dp, 94.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        boardArt(game)
                        if (chessArt != null) chessPieces(chessArt)
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(stringResource(kindName(kind)), color = TvColors.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(if (kind == BoardKind.CHESS) R.string.games_chess_line else R.string.games_checkers_line), color = TvColors.Muted, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        val current = saved
        if (current != null && !current.finished && current.moves.isNotEmpty()) {
            item(key = "continue") {
                Section(stringResource(R.string.games_continue_heading)) {
                    TvButton(stringResource(R.string.games_continue), Modifier.remembersFocus("continue", isDefault = true), primary = true) {
                        nav.push(GamesRoute.Local(game, System.nanoTime()))
                    }
                    Text(
                        if (current.mode == "computer") stringResource(R.string.games_vs_computer_level, stringResource(levelName(current.level))) else stringResource(R.string.games_sofa),
                        color = TvColors.Muted, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }
        item(key = "side") {
            Section(stringResource(R.string.games_your_side)) {
                TvButton(stringResource(R.string.games_play_white), Modifier.remembersFocus("white"), primary = seat == 0) { seat = 0 }
                TvButton(stringResource(R.string.games_play_black), Modifier.remembersFocus("black"), primary = seat == 1) { seat = 1 }
            }
        }
        item(key = "computer") {
            Section(stringResource(R.string.games_vs_computer)) {
                listOf("easy", "normal", "hard").forEach { level ->
                    TvButton(stringResource(levelName(level)), Modifier.remembersFocus("level/$level", isDefault = level == "easy" && (current == null || current.finished || current.moves.isEmpty()))) {
                        startLocal("computer", level)
                    }
                }
            }
        }
        item(key = "sofa") {
            Section(stringResource(R.string.games_sofa)) {
                TvButton(stringResource(R.string.games_sofa_start), Modifier.remembersFocus("sofa")) { startLocal("sofa", "normal") }
                Text(stringResource(R.string.games_sofa_line), color = TvColors.Muted, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
        item(key = "family") {
            Section(stringResource(R.string.games_vs_family)) {
                if (members.isEmpty()) Text(stringResource(R.string.games_no_members), color = TvColors.Muted, fontSize = 14.sp)
                members.forEach { member ->
                    TvButton(member.name, Modifier.remembersFocus("member/${member.id}")) {
                        scope.launch {
                            runCatching { games.invite(game, member, seat) }
                                .onSuccess { nav.push(GamesRoute.Match(it.id)) }
                                .onFailure { inviteFailed = true }
                        }
                    }
                }
            }
            Text(
                stringResource(if (inviteFailed) R.string.games_invite_failed else R.string.games_vs_family_line),
                color = TvColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
    RestoreFocus(true)
}

@Composable
private fun Section(title: String, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column(Modifier.padding(bottom = 16.dp)) {
        Text(title, color = TvColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
