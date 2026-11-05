package net.isputnik.tv.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import net.isputnik.tv.core.TvButton
import net.isputnik.tv.core.TvColors
import net.isputnik.tv.core.requestWhenReady
import kotlin.math.ceil
import kotlin.math.max

// Torpedo Run on the TV: the periscope's lens, after the web app's (games/torpedo/Periscope.tsx):
// a daytime sea under flat clouds, the ships crossing under the horizon, the view turning with
// the periscope. Left and right, held, turn it; OK fires. The run goes to the server as its
// seed and inputs, which replays it for the score.

private object View {
    const val FIELD_W = 1000f
    const val CENTRE = 500f
    const val HORIZON = 207f
    const val SPAN = 660f
    val K = FIELD_W / (2 * Torpedo.FIELD_HALF)
    fun screenX(bearing: Float) = CENTRE + bearing * K
    fun shipWidth(kind: String) = 2 * Torpedo.SHIPS.getValue(kind).half * K
}

private val SHIP_PATHS = mapOf(
    "tanker" to "M0 -7 L100 -7 L96 0 L4 0 Z M5 -7 V-17 H19 V-7 Z M8 -17 V-23 H13 V-17 Z M22 -7 V-9 H86 V-7 Z M30 -9 V-11 H34 V-9 Z M52 -9 V-11 H56 V-9 Z M74 -9 V-11 H78 V-9 Z M89 -7 V-14 H90.5 V-7 Z",
    "cargo" to "M0 -9 L88 -9 L100 -13 L94 0 L5 0 Z M14 -9 V-22 H28 V-9 Z M18 -22 V-28 H24 V-22 Z M34 -9 V-16 H82 V-9 Z M40 -16 V-21 H58 V-16 Z M62 -16 V-20 H78 V-16 Z M48 -21 V-31 H49.6 V-21 Z M49 -31 L60 -23 L59 -22 L49 -29 Z M70 -20 V-30 H71.6 V-20 Z M71 -30 L81 -22 L80 -21 L71 -28 Z",
    "destroyer" to "M0 -6 L84 -7 L100 -10 L95 0 L3 0 Z M30 -7 V-14 H56 V-7 Z M50 -14 V-20 H58 V-14 Z M36 -14 V-20 H42 V-14 Z M45 -14 V-30 H46.4 V-14 Z M41 -25 H51 V-24 H41 Z M68 -7 V-11 H77 V-7 Z M77 -10 H87 V-9 H77 Z M12 -6 V-10 H21 V-6 Z M4 -9 H12 V-8 H4 Z",
    "patrol" to "M0 -6 L80 -7 L100 -11 L90 0 L6 0 Z M24 -7 V-17 H52 L62 -7 Z M36 -17 V-27 H38 V-17 Z M8 -7 V-10 H16 V-7 Z"
)
private val SHIP_HEIGHT = mapOf("tanker" to 23f, "cargo" to 31f, "destroyer" to 30f, "patrol" to 27f)
private val CLOUDS = listOf(
    Triple(-300f, 46f, 210f), Triple(-110f, 70f, 240f), Triple(140f, 70f, 250f), Triple(370f, 52f, 150f),
    Triple(700f, 62f, 230f), Triple(930f, 78f, 170f), Triple(1130f, 50f, 220f), Triple(1330f, 72f, 190f)
)
private const val SEA_FROM = -400f
private const val SEA_TO = 1400f
private const val LAMP_EVERY = 4
private const val BANNER_TICKS = 28

private val Sky = Brush.verticalGradient(listOf(Color(0xFF526B70), Color(0xFF889B98), Color(0xFFB2B8A6)))
private val Hull = Color(0xFF121A1C)
private val Sighted = Color(0xFF3A2A10)
private val Lamp = Color(0xFFFFC46B)
private val Brass = Color(0xFFD9C893)

private enum class RunMode { IDLE, PLAY, PAUSED, BONUS, OVER }

private data class Banner(val kind: String, val points: Int, val tick: Int)

@Composable
internal fun TorpedoScreen(games: GamesApi, onLeave: () -> Unit) {
    val scope = rememberCoroutineScope()
    val json = remember { Json }
    var mode by remember { mutableStateOf(RunMode.IDLE) }
    var state by remember { mutableStateOf(Torpedo.createRun(newSeed())) }
    var seed by remember { mutableStateOf(0L) }
    var key by remember { mutableStateOf("") }
    val log = remember { mutableListOf<Pair<Int, TorpedoInput>>() }
    var last by remember { mutableStateOf(Torpedo.INITIAL_INPUT) }
    var left by remember { mutableStateOf(false) }
    var right by remember { mutableStateOf(false) }
    var fire by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<Banner?>(null) }
    var answer by remember { mutableStateOf<ArcadeAnswer?>(null) }
    var sendFailed by remember { mutableStateOf(false) }
    var family by remember { mutableStateOf<List<ArcadeRow>>(emptyList()) }
    var frame by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        runCatching { games.stats() }.onSuccess { s -> family = s.arcade.all.filter { it.game == Torpedo.ID } }
    }

    val start = {
        seed = newSeed()
        key = newGameKey()
        log.clear()
        last = Torpedo.INITIAL_INPUT
        left = false; right = false; fire = false
        banner = null
        answer = null
        sendFailed = false
        state = Torpedo.createRun(seed)
        mode = RunMode.PLAY
    }

    val finish = {
        val sent = log.map { (tick, input) -> tick to json.encodeToJsonElement(input) }
        scope.launch {
            runCatching { games.reportArcade(Torpedo.ID, Torpedo.VARIANT, seed, key, sent) }
                .onSuccess { answer = it; runCatching { games.stats() }.onSuccess { s -> family = s.arcade.all.filter { r -> r.game == Torpedo.ID } } }
                .onFailure { sendFailed = true }
        }
    }

    // Twenty ticks a second; the input in effect is logged each time it changes.
    LaunchedEffect(mode) {
        if (mode != RunMode.PLAY && mode != RunMode.BONUS) return@LaunchedEffect
        var next = System.nanoTime()
        while (mode == RunMode.PLAY || mode == RunMode.BONUS) {
            val turn = (if (right) 1 else 0) - (if (left) 1 else 0)
            val input = TorpedoInput(if (mode == RunMode.BONUS) 0 else turn, fire)
            fire = false
            if (input != last) {
                log += state.tick to input
                last = input
            }
            val cues = mutableListOf<Torpedo.Cue>()
            state = Torpedo.tick(state, input, cues)
            for (cue in cues) when (cue) {
                is Torpedo.Cue.Hit -> banner = Banner("hit", cue.points, state.tick)
                is Torpedo.Cue.Miss -> banner = Banner("miss", 0, state.tick)
                Torpedo.Cue.Bonus -> banner = Banner("bonus", 0, state.tick)
                else -> Unit
            }
            frame++
            if (state.over) {
                mode = RunMode.OVER
                finish()
                break
            }
            mode = if (state.phase == "bonus") RunMode.BONUS else RunMode.PLAY
            next += Torpedo.TICK_MS * 1_000_000L
            delay(max(0L, (next - System.nanoTime()) / 1_000_000L))
        }
    }

    val lensFocus = remember { FocusRequester() }
    val menuFocus = remember { FocusRequester() }
    LaunchedEffect(mode == RunMode.PLAY || mode == RunMode.BONUS) {
        if (mode == RunMode.PLAY || mode == RunMode.BONUS) lensFocus.requestWhenReady() else menuFocus.requestWhenReady()
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 20.dp)) {
        val lensWidth = maxWidth - 300.dp
        val lensHeight = minOf(maxHeight - 110.dp, lensWidth * 0.5f)
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(lensWidth)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(lensHeight)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color(0xFF222D27))
                        .padding(6.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .focusRequester(lensFocus)
                        .onKeyEvent { event ->
                            val playing = mode == RunMode.PLAY || mode == RunMode.BONUS
                            if (!playing) return@onKeyEvent false
                            val down = event.type == KeyEventType.KeyDown
                            when (event.key) {
                                Key.DirectionLeft -> { left = down; true }
                                Key.DirectionRight -> { right = down; true }
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (down) fire = true; true }
                                Key.DirectionUp, Key.DirectionDown -> true
                                Key.MediaPlayPause, Key.MediaPause, Key.Back, Key.Escape -> {
                                    if (down && mode == RunMode.PLAY) { left = false; right = false; mode = RunMode.PAUSED }
                                    true
                                }
                                else -> false
                            }
                        }
                        .focusable()
                ) {
                    @Suppress("UNUSED_EXPRESSION") frame
                    Lens(state, mode == RunMode.PLAY, banner)
                    val prompt = when (mode) {
                        RunMode.BONUS -> stringResource(R.string.games_torpedo_bonus_prompt)
                        RunMode.PAUSED -> stringResource(R.string.games_paused)
                        RunMode.IDLE -> stringResource(R.string.games_torpedo_start_prompt)
                        else -> null
                    }
                    val shownBanner = banner?.takeIf { state.tick - it.tick in 0 until BANNER_TICKS }
                    val bannerText = when (shownBanner?.kind) {
                        "hit" -> stringResource(R.string.games_torpedo_hit, shownBanner.points)
                        "miss" -> stringResource(R.string.games_torpedo_miss)
                        "bonus" -> stringResource(R.string.games_torpedo_bonus)
                        else -> null
                    }
                    (bannerText ?: prompt)?.let {
                        Text(
                            it, color = if (shownBanner?.kind == "miss") Color(0xFFC9D6D6) else Color(0xFFFFE6A8), fontSize = 26.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Hud(state)
            }
            Spacer(Modifier.width(28.dp))
            Column(Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.games_torpedo), color = TvColors.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    when (mode) {
                        RunMode.IDLE -> stringResource(R.string.games_torpedo_how)
                        RunMode.PLAY, RunMode.BONUS -> stringResource(R.string.games_torpedo_playing)
                        RunMode.PAUSED -> stringResource(R.string.games_paused)
                        RunMode.OVER -> answer?.let { stringResource(R.string.games_arcade_result, it.score, it.best) }
                            ?: if (sendFailed) stringResource(R.string.games_arcade_unsent, state.score) else stringResource(R.string.games_arcade_sending, state.score)
                    },
                    color = TvColors.Muted, fontSize = 15.sp
                )
                Spacer(Modifier.height(6.dp))
                when (mode) {
                    RunMode.PAUSED -> {
                        TvButton(stringResource(R.string.games_resume), Modifier.focusRequester(menuFocus), primary = true) { mode = RunMode.PLAY }
                        TvButton(stringResource(R.string.games_leave), onClick = onLeave)
                    }
                    RunMode.IDLE, RunMode.OVER -> {
                        TvButton(stringResource(if (mode == RunMode.OVER) R.string.games_play_again else R.string.games_start), Modifier.focusRequester(menuFocus), primary = true) { start() }
                        TvButton(stringResource(R.string.games_leave), onClick = onLeave)
                    }
                    else -> Unit
                }
                if (mode != RunMode.PLAY && mode != RunMode.BONUS) FamilyBest(family, listOf(Torpedo.VARIANT to ""))
            }
        }
    }
}

@Composable
private fun Hud(state: TorpedoState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        HudFigure(stringResource(R.string.games_torpedo_score), formatScore(state.score))
        Column {
            Text(stringResource(R.string.games_torpedo_tubes, state.torpedoesLeft, Torpedo.TORPEDOES), color = TvColors.Muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(Torpedo.TORPEDOES) { i ->
                    Box(Modifier.size(12.dp).clip(CircleShape).background(if (i < state.torpedoesLeft) Lamp else Color(0xFF525C49)))
                }
            }
        }
        if (state.round > 1) HudFigure(stringResource(R.string.games_torpedo_bonus_round), stringResource(R.string.games_torpedo_round, state.round))
        else HudFigure(stringResource(R.string.games_torpedo_bonus_at), formatScore(Torpedo.BONUS_AT))
        HudFigure(stringResource(R.string.games_torpedo_clock), stringResource(R.string.games_torpedo_seconds, ceil(state.clock * Torpedo.TICK_MS / 1000.0).toInt()))
    }
}

@Composable
private fun HudFigure(label: String, value: String) {
    Column {
        Text(label, color = TvColors.Muted, fontSize = 12.sp)
        Text(
            value, color = Brass, fontSize = 22.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF263129)).padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

private fun formatScore(score: Int): String = String.format(java.util.Locale.forLanguageTag("ru-RU"), "%,d", score).replace(' ', ' ')

/** A cloud: three puffs over a flat base. */
private fun cloud(x: Float, y: Float, w: Float) = Path().apply {
    val h = w * 0.16f
    moveTo(x - w / 2, y)
    cubicTo(x - w * 0.44f, y - h * 0.9f, x - w * 0.3f, y - h * 1.2f, x - w * 0.18f, y - h * 0.9f)
    cubicTo(x - w * 0.12f, y - h * 2f, x + w * 0.14f, y - h * 2.1f, x + w * 0.2f, y - h)
    cubicTo(x + w * 0.3f, y - h * 1.4f, x + w * 0.46f, y - h * 0.8f, x + w / 2, y)
    close()
}

private val shipPaths: Map<String, Path> by lazy { SHIP_PATHS.mapValues { PathParser().parsePathString(it.value).toPath() } }

@Composable
private fun Lens(state: TorpedoState, playing: Boolean, banner: Banner?) {
    Canvas(Modifier.fillMaxSize()) {
        val aspect = size.width / size.height
        val viewW = View.SPAN
        val viewH = viewW / aspect
        val top = View.HORIZON - viewH * 0.47f
        val bottom = top + viewH
        val left = View.CENTRE - viewW / 2
        val scale = size.width / viewW
        val tubeY = bottom - 12f
        val laneY = Torpedo.LANES.associateWith { tubeY - (Torpedo.REACH.getValue(it) / 100f) * (tubeY - View.HORIZON) }
        // Scene units to the canvas, the world turned under the sight.
        val turn = -state.bearing * View.K
        withTransform({ scale(scale, scale, Offset.Zero); translate(-left, -top) }) {
            drawRect(Sky, Offset(SEA_FROM, top), Size(SEA_TO - SEA_FROM, View.HORIZON - top))
            drawRect(
                Brush.verticalGradient(listOf(Color(0xFF345652), Color(0xFF192F2A)), startY = View.HORIZON, endY = bottom),
                Offset(SEA_FROM, View.HORIZON), Size(SEA_TO - SEA_FROM, bottom - View.HORIZON)
            )
            translate(turn, 0f) {
                CLOUDS.forEach { (x, rise, w) -> drawPath(cloud(x, View.HORIZON - rise, w), Color(0xEBECDFC0)) }
                drawLine(Color(0x800A282E), Offset(SEA_FROM, View.HORIZON), Offset(SEA_TO, View.HORIZON), 1.5f)
                laneY.values.forEach { y ->
                    drawLine(Color(0x29E1EBDC), Offset(SEA_FROM, y), Offset(SEA_TO, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 9f)))
                }
                // A hit puts the sea in shadow for a moment, so the fire glows.
                val hitAge = banner?.takeIf { it.kind == "hit" }?.let { state.tick - it.tick }
                if (hitAge != null && hitAge in 0 until Torpedo.SINK_TICKS) {
                    drawRect(Color(0xFF02090B).copy(alpha = 0.62f * (1 - hitAge.toFloat() / Torpedo.SINK_TICKS)), Offset(SEA_FROM, top), Size(SEA_TO - SEA_FROM, viewH))
                }
                val sighted = if (playing) Torpedo.inSights(state, state.bearing) else null
                state.ships.sortedBy { laneY.getValue(Torpedo.SHIPS.getValue(it.kind).lane) }.forEach { ship ->
                    drawShip(ship, laneY.getValue(Torpedo.SHIPS.getValue(ship.kind).lane), sighted?.id == ship.id)
                }
                state.torpedo?.let { torpedo ->
                    var run = 0
                    while (run <= torpedo.run) {
                        val (x, y, lamp) = wakePoint(torpedo.bearing, run, tubeY, laneY)
                        val head = torpedo.run - run < LAMP_EVERY
                        val glow = 0.35f + 0.65f * run / Torpedo.RANGE.getValue("far")
                        drawCircle(if (head) Color(0xFFFFF6DC) else Lamp.copy(alpha = glow * 0.8f), lamp / if (head) 1.6f else 2f, Offset(x, y))
                        run += LAMP_EVERY
                    }
                }
            }
            // The reticle: two fine etched lines; the sight is the middle between them.
            for (side in listOf(-1, 1)) {
                val x = left + viewW / 2 + side * viewW / 6
                drawLine(Color(0xB3041012), Offset(x, top), Offset(x, bottom), 1.5f)
                listOf("far", "mid", "near").forEachIndexed { i, lane ->
                    val y = laneY.getValue(lane)
                    drawLine(Color(0xB3041012), Offset(x, y), Offset(x - side * (6 + i * 3), y), 1.5f)
                }
            }
        }
        // The vignette at the lens's edge.
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, Color(0x99000000)), center = center, radius = size.width * 0.62f))
    }
}

private data class Point(val x: Float, val y: Float, val size: Float)

/** Where a torpedo `run` ticks out is on its course: under the sight at the tube, leaning the
 *  way the periscope was turned to LEAN of the turn again on the horizon (view.ts). */
private fun wakePoint(bearing: Int, run: Int, tubeY: Float, lanes: Map<String, Float>): Point {
    val stops = listOf(0 to tubeY, Torpedo.RANGE.getValue("near") to lanes.getValue("near"), Torpedo.RANGE.getValue("mid") to lanes.getValue("mid"), Torpedo.RANGE.getValue("far") to lanes.getValue("far"))
    var y = lanes.getValue("far")
    for (i in 1 until stops.size) {
        val (r0, y0) = stops[i - 1]
        val (r1, y1) = stops[i]
        if (run <= r1) {
            y = y0 + (run - r0).toFloat() / (r1 - r0) * (y1 - y0)
            break
        }
    }
    val reach = (tubeY - y) / (tubeY - View.HORIZON)
    val x = View.screenX(bearing * (1 + Torpedo.LEAN / 100f * reach))
    return Point(x, y, 15f - run.toFloat() / Torpedo.RANGE.getValue("far") * 7f)
}

private fun DrawScope.drawShip(ship: Ship, y: Float, sighted: Boolean) {
    val x = View.screenX(ship.x.toFloat())
    val s = View.shipWidth(ship.kind) / 100f
    val sinking = ship.sinking?.let { 1 - it.toFloat() / Torpedo.SINK_TICKS } ?: 0f
    val height = SHIP_HEIGHT.getValue(ship.kind) * s
    val path = shipPaths.getValue(ship.kind)
    clipRect(top = -10_000f, bottom = y + 0.5f, left = -10_000f, right = 10_000f) {
        withTransform({
            translate(x, y + sinking * height)
            scale(s * ship.dir, s, Offset.Zero)
            translate(-50f, 0f)
        }) {
            drawPath(path, (if (sighted) Sighted else Hull).copy(alpha = 1 - sinking * 0.9f))
        }
    }
    if (ship.sinking == null) {
        val wx = x - ship.dir * 50 * s - (if (ship.dir > 0) 30 * s else 0f)
        drawRect(Color(0x8CEBF5F0), Offset(wx, y - 1), Size(30 * s, max(1.5f, 2 * s)))
    } else {
        if (sinking < 0.6f) {
            val fade = 1 - sinking / 0.6f
            val r = height * (0.8f + sinking * 2.4f)
            val c = Offset(x, y - height * 0.4f)
            drawCircle(
                Brush.radialGradient(listOf(Color(0xF2FFF0C8), Color(0xD9FFA046), Color(0x73D14B2D), Color(0x00D14B2D)), center = c, radius = r),
                r, c, alpha = fade
            )
        }
        drawOval(Color(0x8CE6F5F5).copy(alpha = 0.55f * (1 - sinking)), Offset(x - 50 * s * (0.6f + sinking), y - (3 + 4 * s)), Size(100 * s * (0.6f + sinking), 2 * (3 + 4 * s)))
    }
}
