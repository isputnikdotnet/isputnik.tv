package net.isputnik.tv.video

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key as composeKey
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.isputnik.tv.core.TvButton
import net.isputnik.tv.core.TvColors
import net.isputnik.tv.player.PlayFailure
import net.isputnik.tv.player.TvPlayer
import net.isputnik.tv.player.TvPlayerEvents

private const val SAVE_EVERY_MS = 15_000L
private const val CONTROLS_MS = 4_000L
private const val STEP_BACK_MS = 10_000L
private const val STEP_FORWARD_MS = 30_000L

private fun Player.durationSeconds(): Double? = duration.takeIf { it != C.TIME_UNSET && it > 0 }?.let { it / 1000.0 }

@Composable
fun PlayerScreen(video: VideoApi, id: String, onExit: () -> Unit) {
    var info by remember { mutableStateOf<PlayInfo?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        try {
            info = video.play(id)
        } catch (_: Exception) {
            loadFailed = true
        }
    }
    val loaded = info
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            loadFailed -> Failed(stringResource(R.string.player_failed_network), onExit)
            loaded == null -> Text(stringResource(R.string.video_loading), color = TvColors.Text, modifier = Modifier.align(Alignment.Center))
            !loaded.available -> Failed(stringResource(R.string.player_failed_gone), onExit)
            else -> Playing(video, loaded, onExit)
        }
    }
}

@Composable
private fun Failed(message: String, onExit: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.player_failed_title), color = TvColors.Text, fontSize = 26.sp)
            Text(message, color = TvColors.Muted, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp, bottom = 24.dp))
            TvButton(stringResource(R.string.player_back), Modifier.focusRequester(focus), onExit)
        }
    }
}

@Composable
private fun Playing(video: VideoApi, info: PlayInfo, onExit: () -> Unit) {
    val context = LocalContext.current
    var failure by remember { mutableStateOf<PlayFailure?>(null) }
    var converted by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var wake by remember { mutableIntStateOf(0) }
    var showControls by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    val exit by rememberUpdatedState(onExit)
    val scope = video.api.background

    val holder = remember { arrayOfNulls<TvPlayer>(1) }
    fun save(watched: Boolean? = null) {
        val player = holder[0]?.player ?: return
        val seconds = player.durationSeconds()
        val at = if (watched == true) seconds ?: (player.currentPosition / 1000.0) else player.currentPosition / 1000.0
        if (at <= 0) return
        scope.launch { runCatching { video.saveProgress(info.id, at, seconds, watched) } }
    }

    // Made during composition, not in the effect below: the surface takes the player when
    // it's created, which is before effects run. Without one it plays sound and no picture.
    val tv = remember(info.id) {
        TvPlayer(context, video.api.http, info.source(video::absolute), object : TvPlayerEvents {
            override fun onSwitchedToConverted() { converted = true; wake++ }
            override fun onFailed(failure_: PlayFailure) { failure = failure_ }
            override fun onEnded() { save(watched = true); exit() }
            override fun onPlayingChanged(playing: Boolean) {
                paused = !playing && holder[0]?.player?.playbackState == Player.STATE_READY
                if (paused) { save(); wake++ }
            }
        }).also { holder[0] = it }
    }

    DisposableEffect(tv) {
        tv.start()
        onDispose {
            if (failure == null && tv.player.playbackState != Player.STATE_ENDED) save()
            if (tv.usingConverted) scope.launch { runCatching { video.stopConverting(info.id) } }
            holder[0] = null
            tv.release()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(SAVE_EVERY_MS)
            if (holder[0]?.player?.isPlaying == true) save()
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            holder[0]?.player?.let { player ->
                position = player.currentPosition
                duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0
            }
            delay(500)
        }
    }
    LaunchedEffect(wake, paused) {
        showControls = true
        if (!paused) {
            delay(CONTROLS_MS)
            showControls = false
        }
    }

    val failed = failure
    if (failed != null) {
        val busy = info.playback.busy
        Failed(
            when {
                failed == PlayFailure.NETWORK -> stringResource(R.string.player_failed_network)
                failed == PlayFailure.GONE -> stringResource(R.string.player_failed_gone)
                failed == PlayFailure.DECODER && busy?.reason == "person" -> stringResource(R.string.player_failed_busy_person, busy.title ?: info.title)
                failed == PlayFailure.DECODER && busy != null -> stringResource(R.string.player_failed_busy)
                failed == PlayFailure.DECODER -> stringResource(R.string.player_failed_decoder)
                else -> stringResource(R.string.player_failed_other)
            },
            onExit
        )
        return
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun seek(by: Long) {
        val player = holder[0]?.player ?: return
        player.seekTo((player.currentPosition + by).coerceAtLeast(0))
        position = player.currentPosition
        wake++
    }
    fun toggle() {
        val player = holder[0]?.player ?: return
        if (player.isPlaying) player.pause() else player.play()
        wake++
    }

    // The surface takes no keys: every press comes here, so Left and Right always seek.
    Box(
        Modifier.fillMaxSize().focusRequester(focus).focusable().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.composeKey.nativeKeyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { seek(-STEP_BACK_MS); true }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seek(STEP_FORWARD_MS); true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { toggle(); true }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { wake++; true }
                else -> false
            }
        }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    isFocusable = false
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = holder[0]?.player
                }
            }
        )
        if (showControls) {
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0xB30A1420)).padding(horizontal = 48.dp, vertical = 24.dp)) {
                Text(info.heading(), color = TvColors.Text, fontSize = 22.sp, maxLines = 1)
                if (converted) Text(stringResource(R.string.player_converted), color = TvColors.Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                val done = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0x55FFFFFF))) {
                    Box(Modifier.fillMaxWidth(done).height(6.dp).background(TvColors.Accent))
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Text(formatPlayTime(position), color = TvColors.Text, fontSize = 15.sp)
                    Spacer(Modifier.weight(1f))
                    Text(formatPlayTime(duration), color = TvColors.Muted, fontSize = 15.sp)
                }
            }
        }
    }
}
