package net.isputnik.tv.games

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import net.isputnik.tv.core.TvButton
import net.isputnik.tv.core.TvColors
import net.isputnik.tv.core.requestWhenReady

// Sputnik Catch on the TV: the handheld's LCD, as large as the screen allows, played with
// the arrows — left and right move the robot across, up and down raise and lower his net.
// OK or Play/Pause pauses; Back pauses, and from a pause leaves. The run is played here and
// sent to the server as its seed and inputs; the server replays it and keeps the score.

private val LcdGround = Color(0xFFC5CBB2)
private val Rim = Color(0xFF15514C)
private const val BLINK_MS = 280L

private enum class Mode { IDLE, PLAY, PAUSED, OVER }

/** Which segments are dark this moment (the web app's lit.ts). */
private fun litSegments(mode: Mode, state: SputnikState, net: Int, blink: Boolean): Set<String> {
    val on = mutableSetOf<String>()
    for (s in state.sputniks) on += "s${s.rail}${s.step}"
    if (mode != Mode.OVER || blink) {
        on += if (net < 2) "robot-left" else "robot-right"
        on += "net$net"
    }
    val score = Sputnik.displayScore(state).toString().padStart(4, ' ')
    score.forEachIndexed { i, ch -> if (ch != ' ') DIGITS.getValue(ch).forEach { on += "d$i$it" } }
    val whole = minOf(3, state.halves / 2)
    for (i in 0 until whole) on += "miss$i"
    if (state.halves % 2 == 1 && whole < 3 && blink) on += "miss$whole"
    if (state.belka.out) on += "belka"
    state.crash?.let {
        on += "wreck-${it.side}"
        on += "alien-${it.side}${minOf(it.step, Sputnik.ALIEN_STEPS - 1)}"
    }
    if (mode != Mode.IDLE) on += if (state.variant == "A") "game-a" else "game-b"
    return on
}

private val DIGITS = mapOf(
    '0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc",
    '5' to "afgcd", '6' to "afgedc", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg"
)

@Composable
internal fun SputnikScreen(games: GamesApi, onLeave: () -> Unit) {
    val context = LocalContext.current
    val russian = LocalConfiguration.current.locales[0].language == "ru"
    val art = remember(russian) { LcdArt(context, russian) }
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(Mode.IDLE) }
    var state by remember { mutableStateOf(Sputnik.createRun("A", newSeed())) }
    var net by remember { mutableStateOf(0) }
    var blink by remember { mutableStateOf(false) }
    var seed by remember { mutableStateOf(0L) }
    var key by remember { mutableStateOf("") }
    val log = remember { mutableListOf<Pair<Int, Int>>() }
    var answer by remember { mutableStateOf<ArcadeAnswer?>(null) }
    var sendFailed by remember { mutableStateOf(false) }
    var family by remember { mutableStateOf<List<ArcadeRow>>(emptyList()) }

    LaunchedEffect(Unit) {
        runCatching { games.stats() }.onSuccess { stats -> family = stats.arcade.all.filter { it.game == Sputnik.ID } }
    }

    val start = { variant: String ->
        seed = newSeed()
        key = newGameKey()
        log.clear()
        net = 0
        answer = null
        sendFailed = false
        state = Sputnik.createRun(variant, seed)
        mode = Mode.PLAY
    }

    val finish = {
        val variant = state.variant
        val sent = log.map { (tick, rail) -> tick to JsonPrimitive(rail) }
        scope.launch {
            runCatching { games.reportArcade(Sputnik.ID, variant, seed, key, sent) }
                .onSuccess { answer = it; runCatching { games.stats() }.onSuccess { s -> family = s.arcade.all.filter { r -> r.game == Sputnik.ID } } }
                .onFailure { sendFailed = true }
        }
    }

    LaunchedEffect(mode) {
        if (mode != Mode.PLAY) return@LaunchedEffect
        delay(500)
        while (mode == Mode.PLAY) {
            state = Sputnik.tick(state, net)
            if (state.over) {
                mode = Mode.OVER
                finish()
                break
            }
            delay(Sputnik.periodOf(state).toLong())
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(BLINK_MS)
            blink = !blink
        }
    }

    val move = { direction: Direction ->
        val rail = Sputnik.railFor(net, direction)
        if (rail != net) {
            net = rail
            // The net in effect at the next tick: of two presses before it, the last counts.
            val last = log.lastOrNull()
            if (last != null && last.first == state.tick) log[log.lastIndex] = state.tick to rail else log += state.tick to rail
        }
    }

    val lcdFocus = remember { FocusRequester() }
    val menuFocus = remember { FocusRequester() }
    LaunchedEffect(mode) { if (mode == Mode.PLAY) lcdFocus.requestWhenReady() else menuFocus.requestWhenReady() }

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 20.dp)) {
        val lcdWidth = minOf(maxWidth - 320.dp, (maxHeight - 40.dp) * (art.width.toFloat() / art.height))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1B5752), Rim, Color(0xFF0B2A28))))
                    .padding(14.dp)
            ) {
                Box(
                    Modifier
                        .size(lcdWidth, lcdWidth * (art.height.toFloat() / art.width))
                        .clip(RoundedCornerShape(8.dp))
                        .focusRequester(lcdFocus)
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown || mode != Mode.PLAY) return@onKeyEvent false
                            when (event.key) {
                                Key.DirectionLeft -> { move(Direction.LEFT); true }
                                Key.DirectionRight -> { move(Direction.RIGHT); true }
                                Key.DirectionUp -> { move(Direction.UP); true }
                                Key.DirectionDown -> { move(Direction.DOWN); true }
                                Key.DirectionCenter, Key.Enter, Key.MediaPlayPause, Key.MediaPause, Key.Back, Key.Escape -> { mode = Mode.PAUSED; true }
                                else -> false
                            }
                        }
                        .focusable()
                        .drawWithCache {
                            // The backdrop and every segment faintly there, drawn once for this size.
                            val bitmap = android.graphics.Bitmap.createBitmap(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
                            val base = android.graphics.Canvas(bitmap)
                            base.drawColor(LcdGround.toArgbInt())
                            base.drawScaled(art.backdrop, size.width, size.height)
                            val faint = (art.unlit * 255).toInt()
                            art.segments.values.forEach { picture ->
                                base.saveLayerAlpha(0f, 0f, size.width, size.height, faint)
                                base.drawScaled(picture, size.width, size.height)
                                base.restore()
                            }
                            val image = bitmap.asImageBitmap()
                            onDrawBehind {
                                drawImage(image)
                                val lit = litSegments(mode, state, net, blink)
                                drawIntoCanvas { canvas ->
                                    val c = canvas.nativeCanvas
                                    c.saveLayerAlpha(0f, 0f, size.width, size.height, (art.lit * 255).toInt())
                                    lit.forEach { id -> art.segments[id]?.let { c.drawScaled(it, size.width, size.height) } }
                                    c.restore()
                                }
                                // The glass's sheen, as the web app's LCD.
                                drawRect(Brush.linearGradient(listOf(Color(0x38FFFFFF), Color.Transparent), end = androidx.compose.ui.geometry.Offset(size.width * 0.45f, size.height * 0.6f)))
                            }
                        }
                )
            }
            Spacer(Modifier.width(28.dp))
            Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.games_sputnik), color = TvColors.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    when (mode) {
                        Mode.IDLE -> stringResource(R.string.games_sputnik_how)
                        Mode.PLAY -> stringResource(R.string.games_sputnik_playing)
                        Mode.PAUSED -> stringResource(R.string.games_paused)
                        Mode.OVER -> answer?.let { stringResource(R.string.games_arcade_result, it.score, it.best) }
                            ?: if (sendFailed) stringResource(R.string.games_arcade_unsent, state.caught) else stringResource(R.string.games_arcade_sending, state.caught)
                    },
                    color = TvColors.Muted, fontSize = 15.sp
                )
                Spacer(Modifier.height(6.dp))
                when (mode) {
                    Mode.PAUSED -> {
                        TvButton(stringResource(R.string.games_resume), Modifier.focusRequester(menuFocus), primary = true) { mode = Mode.PLAY }
                        TvButton(stringResource(R.string.games_leave), onClick = onLeave)
                    }
                    Mode.IDLE, Mode.OVER -> {
                        TvButton(stringResource(R.string.games_sputnik_a), Modifier.focusRequester(menuFocus), primary = true) { start("A") }
                        TvButton(stringResource(R.string.games_sputnik_b)) { start("B") }
                        TvButton(stringResource(R.string.games_leave), onClick = onLeave)
                    }
                    Mode.PLAY -> Unit
                }
                if (mode != Mode.PLAY) FamilyBest(family, listOf("A" to stringResource(R.string.games_sputnik_a), "B" to stringResource(R.string.games_sputnik_b)))
            }
        }
    }
}

/** The family's best runs per variant, from the games server's leaderboard. */
@Composable
internal fun FamilyBest(rows: List<ArcadeRow>, variants: List<Pair<String, String>>) {
    if (rows.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    Text(stringResource(R.string.games_family_best), color = TvColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    variants.forEach { (variant, label) ->
        val top = rows.filter { it.variant == variant }.sortedByDescending { it.best }.take(3)
        if (top.isNotEmpty()) {
            if (variants.size > 1) Text(label, color = TvColors.Muted, fontSize = 13.sp)
            top.forEach { Text("${it.name} · ${it.best}", color = TvColors.Muted, fontSize = 14.sp, maxLines = 1) }
        }
    }
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

private fun android.graphics.Canvas.drawScaled(picture: android.graphics.Picture, width: Float, height: Float) {
    save()
    scale(width / picture.width, height / picture.height)
    drawPicture(picture)
    restore()
}

