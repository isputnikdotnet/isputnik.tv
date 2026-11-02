package net.isputnik.tv.video

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key as composeKey
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import net.isputnik.tv.core.ProgressLine
import net.isputnik.tv.core.TvButton
import net.isputnik.tv.core.TvColors
import net.isputnik.tv.player.PlayFailure
import net.isputnik.tv.player.TvPlayer
import net.isputnik.tv.player.TvPlayerEvents
import kotlinx.coroutines.CancellationException

private const val SAVE_EVERY_MS = 15_000L
private const val CONTROLS_MS = 4_000L
private const val STEP_BACK_MS = 10_000L
private const val STEP_FORWARD_MS = 30_000L
private const val NEXT_IN_SECONDS = 10

private fun Player.durationSeconds(): Double? = duration.takeIf { it != C.TIME_UNSET && it > 0 }?.let { it / 1000.0 }

/** What the player starts on, worked out before the first frame. Tracks go by their own number
 *  and id: asking the engine again marks a different one selected, so whole tracks won't match. */
private class PlayStart(val info: PlayInfo, val audio: Int?, val subtitle: String?)

@Composable
fun PlayerScreen(video: VideoApi, id: String, fromStart: Boolean, prefs: PlayerPrefs, onNext: (String) -> Unit, onExit: () -> Unit) {
    var start by remember { mutableStateOf<PlayStart?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        try {
            var info = video.play(id)
            // In the languages chosen last time, when this one has them. An image subtitle
            // is burned in, so the engine is asked again for that copy.
            val audio = pickAudio(info.audioTracks, prefs.audio)
            val subtitle = pickSubtitle(info.subtitleTracks, prefs.subtitle)
            val burn = subtitle?.takeIf { it.kind == "image" }?.id
            if ((audio != null && !audio.selected) || burn != null) {
                info = runCatching { video.play(id, audio?.index, burn) }.getOrDefault(info)
            }
            start = PlayStart(info, audio?.index, subtitle?.id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            loadFailed = true
        }
    }
    val loaded = start
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            loadFailed -> Failed(stringResource(R.string.player_failed_network), onExit)
            loaded == null -> Text(stringResource(R.string.video_loading), color = TvColors.Text, modifier = Modifier.align(Alignment.Center))
            !loaded.info.available -> Failed(stringResource(R.string.player_failed_gone), onExit)
            else -> Playing(video, loaded, fromStart, prefs, onNext, onExit)
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
            TvButton(stringResource(R.string.player_back), Modifier.focusRequester(focus), onClick = onExit)
        }
    }
}

/** Where the remote's keys go. */
private enum class Layer { PICTURE, BAR, AUDIO_MENU, SUBTITLE_MENU, NEXT_CARD }

@Composable
private fun audioLabel(track: AudioTrack, position: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    val name = languageName(track.language, locale) ?: stringResource(R.string.player_track, position + 1)
    return listOfNotNull(name, track.title, channelsName(track.channels)).joinToString(" · ")
}

@Composable
private fun subtitleLabel(track: SubtitleTrack, position: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    val name = languageName(track.language, locale) ?: stringResource(R.string.player_track, position + 1)
    return listOfNotNull(name, track.title, if (track.forced) stringResource(R.string.player_forced) else null).joinToString(" · ")
}

@Composable
private fun Playing(video: VideoApi, start: PlayStart, fromStart: Boolean, prefs: PlayerPrefs, onNext: (String) -> Unit, onExit: () -> Unit) {
    val info = start.info
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var failure by remember { mutableStateOf<PlayFailure?>(null) }
    var notice by remember { mutableStateOf<Int?>(null) }
    var paused by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var wake by remember { mutableIntStateOf(0) }
    var showControls by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var layer by remember { mutableStateOf(Layer.PICTURE) }
    var audio by remember { mutableStateOf(start.audio) }
    var subtitle by remember { mutableStateOf(start.subtitle) }
    var burned by remember { mutableStateOf(info.subtitleTracks.firstOrNull { it.id == start.subtitle && it.kind == "image" }?.id) }
    /** Seconds until the next title; null when no card is up. */
    var nextIn by remember { mutableStateOf<Int?>(null) }
    var creditsOffered by remember { mutableStateOf(false) }
    val exit by rememberUpdatedState(onExit)
    val background = video.api.background

    val holder = remember { arrayOfNulls<TvPlayer>(1) }
    fun save(watched: Boolean? = null) {
        val player = holder[0]?.player ?: return
        val seconds = player.durationSeconds()
        val at = if (watched == true) seconds ?: (player.currentPosition / 1000.0) else player.currentPosition / 1000.0
        if (at <= 0) return
        background.launch { runCatching { video.saveProgress(info.id, at, seconds, watched) } }
    }

    // Made during composition, not in the effect below: the surface takes the player when
    // it's created, which is before effects run. Without one it plays sound and no picture.
    val tv = remember(info.id) {
        val source = info.source(video::absolute).let { base ->
            val adjusted = if (fromStart) base.copy(startMs = 0) else base
            // Burned subtitles exist only in the converted copy.
            if (burned != null) adjusted.copy(original = null) else adjusted
        }
        TvPlayer(context, video.api.http, source, object : TvPlayerEvents {
            override fun onSwitchedToConverted() {
                notice = R.string.player_converted
                wake++
            }
            override fun onFailed(failure_: PlayFailure) { failure = failure_ }
            override fun onEnded() {
                save(watched = true)
                ended = true
                // Already counting from the credits: it goes on, not over.
                if (info.next != null) nextIn = nextIn ?: NEXT_IN_SECONDS else exit()
            }
            override fun onPlayingChanged(playing: Boolean) {
                paused = !playing && holder[0]?.player?.playbackState == Player.STATE_READY
                if (paused) {
                    save()
                    wake++
                    // Stopped in the credits: nothing moves on by itself.
                    nextIn = null
                }
            }
        }).also { holder[0] = it }
    }

    DisposableEffect(tv) {
        val audioPosition = info.audioTracks.indexOfFirst { it.index == start.audio }.takeIf { it >= 0 }
        tv.start(audioPosition, subtitle?.takeIf { it != burned })
        onDispose {
            if (failure == null && tv.player.playbackState != Player.STATE_ENDED) save()
            if (tv.usingConverted) background.launch { runCatching { video.stopConverting(info.id) } }
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
                val seconds = position / 1000.0
                // The end card comes up with the credits, once, unless paused there;
                // seeking back from them takes it away and lets it come again.
                if (info.next != null && !ended) {
                    if (!inCredits(info.markers, seconds)) {
                        if (creditsOffered) {
                            creditsOffered = false
                            nextIn = null
                        }
                    } else if (!creditsOffered && player.isPlaying) {
                        creditsOffered = true
                        nextIn = NEXT_IN_SECONDS
                    }
                }
            }
            delay(500)
        }
    }
    LaunchedEffect(wake, paused, layer) {
        showControls = true
        if (!paused && layer == Layer.PICTURE) {
            delay(CONTROLS_MS)
            showControls = false
        }
    }
    val next = info.next
    LaunchedEffect(nextIn) {
        val left = nextIn
        if (left == null) {
            if (layer == Layer.NEXT_CARD) layer = Layer.PICTURE
            return@LaunchedEffect
        }
        layer = Layer.NEXT_CARD
        delay(1000)
        if (left <= 1) next?.let { onNext(it.id) } else nextIn = left - 1
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

    /** Another copy from the server; a busy server says no and the picture carries on. */
    suspend fun fetchConverted(audioIndex: Int?, burn: String?): String? {
        val answer = runCatching { video.play(info.id, audioIndex, burn) }.getOrNull()
        val url = answer?.playback?.url?.takeIf { answer.playback.busy == null && answer.playback.mode != "direct" }
        if (url == null) notice = R.string.player_choice_failed
        return url?.let(video::absolute)
    }

    fun chooseAudio(track: AudioTrack) {
        layer = Layer.PICTURE
        if (track.index == audio) return
        prefs.audio = track.language
        scope.launch {
            val place = info.audioTracks.indexOfFirst { it.index == track.index }
            if (!tv.usingConverted && tv.canPlayAudio(place)) {
                tv.selectAudio(place)
                audio = track.index
            } else {
                fetchConverted(track.index, burned)?.let {
                    tv.playConverted(it)
                    audio = track.index
                }
            }
        }
    }

    fun chooseSubtitle(track: SubtitleTrack?) {
        layer = Layer.PICTURE
        if (track?.id == subtitle) return
        prefs.subtitle = if (track == null) SUBTITLES_OFF else track.language
        scope.launch {
            val burn = track?.takeIf { it.kind == "image" }?.id
            if (burn != burned) {
                if (burn == null && tv.hasOriginal) {
                    tv.playOriginal(info.audioTracks.indexOfFirst { it.index == audio }.takeIf { it >= 0 })
                } else {
                    val url = fetchConverted(audio, burn) ?: return@launch
                    tv.playConverted(url)
                }
                burned = burn
            }
            tv.selectSubtitle(track?.takeIf { it.kind == "text" }?.id)
            subtitle = track?.id
        }
    }

    val picture = remember { FocusRequester() }
    val barFirst = remember { FocusRequester() }
    val menuFirst = remember { FocusRequester() }
    val nextButton = remember { FocusRequester() }
    LaunchedEffect(layer) {
        delay(16)
        runCatching {
            when (layer) {
                Layer.PICTURE -> picture
                Layer.BAR -> barFirst
                Layer.AUDIO_MENU, Layer.SUBTITLE_MENU -> menuFirst
                Layer.NEXT_CARD -> nextButton
            }.requestFocus()
        }
    }

    BackHandler(enabled = layer == Layer.BAR) { layer = Layer.PICTURE }
    BackHandler(enabled = layer == Layer.AUDIO_MENU || layer == Layer.SUBTITLE_MENU) { layer = Layer.BAR }
    BackHandler(enabled = layer == Layer.NEXT_CARD) { if (ended) onExit() else nextIn = null }

    fun seek(by: Long) {
        val player = tv.player
        player.seekTo((player.currentPosition + by).coerceAtLeast(0))
        position = player.currentPosition
        wake++
    }
    fun toggle() {
        val player = tv.player
        if (player.isPlaying) player.pause() else player.play()
        wake++
    }
    val seconds = position / 1000.0
    val intro = info.markers?.intro?.takeIf { inIntro(info.markers, seconds) }
    val hasAudioChoice = info.audioTracks.size > 1
    val hasSubtitleChoice = info.subtitleTracks.isNotEmpty()

    // The surface takes no keys: on the picture every press comes here, so Left and Right
    // always seek. Down opens the buttons, and they and the menus then use the keys themselves.
    Box(
        Modifier.fillMaxSize().focusRequester(picture).focusable().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val code = event.composeKey.nativeKeyCode
            if (layer == Layer.BAR && code == KeyEvent.KEYCODE_DPAD_UP) {
                layer = Layer.PICTURE
                return@onPreviewKeyEvent true
            }
            if (layer != Layer.PICTURE) return@onPreviewKeyEvent false
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { seek(-STEP_BACK_MS); true }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seek(STEP_FORWARD_MS); true }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { toggle(); true }
                // While the intro plays, Select is the Skip intro on screen.
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (intro != null) {
                        tv.player.seekTo((intro.end * 1000).toLong())
                        wake++
                    } else {
                        toggle()
                    }
                    true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (hasAudioChoice || hasSubtitleChoice) layer = Layer.BAR else wake++
                    true
                }
                KeyEvent.KEYCODE_DPAD_UP -> { wake++; true }
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

        if (intro != null && layer == Layer.PICTURE) {
            val shape = RoundedCornerShape(8.dp)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 40.dp, bottom = if (showControls) 120.dp else 40.dp)
                    .clip(shape)
                    .background(TvColors.Accent)
                    .border(3.dp, Color.White, shape)
                    .padding(horizontal = 22.dp, vertical = 11.dp)
            ) {
                Text(stringResource(R.string.player_skip_intro), color = TvColors.OnAccent, fontSize = 16.sp)
            }
        }

        if (showControls && layer != Layer.NEXT_CARD) {
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(TvColors.Background.copy(alpha = 0.7f)).padding(horizontal = 40.dp, vertical = 18.dp)) {
                Text(info.heading(), color = TvColors.Text, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                notice?.let { Text(stringResource(it), color = TvColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp)) }
                Spacer(Modifier.height(10.dp))
                ProgressLine(if (duration > 0) position.toFloat() / duration else 0f, Modifier.fillMaxWidth(), height = 6.dp)
                Spacer(Modifier.height(6.dp))
                Row {
                    Text(formatPlayTime(position), color = TvColors.Text, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Text(formatPlayTime(duration), color = TvColors.Muted, fontSize = 14.sp)
                }
                if ((layer == Layer.BAR || layer == Layer.AUDIO_MENU || layer == Layer.SUBTITLE_MENU) && (hasAudioChoice || hasSubtitleChoice)) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (hasAudioChoice) {
                            TvButton(stringResource(R.string.player_audio), Modifier.focusRequester(barFirst)) { layer = Layer.AUDIO_MENU }
                        }
                        if (hasSubtitleChoice) {
                            TvButton(stringResource(R.string.player_subtitles), if (hasAudioChoice) Modifier else Modifier.focusRequester(barFirst)) { layer = Layer.SUBTITLE_MENU }
                        }
                    }
                }
            }
        }

        if (layer == Layer.AUDIO_MENU) {
            ChoiceMenu(stringResource(R.string.player_audio), info.audioTracks.mapIndexed { place, track ->
                Choice(audioLabel(track, place), track.index == audio) { chooseAudio(track) }
            }, menuFirst)
        }
        if (layer == Layer.SUBTITLE_MENU) {
            val off = Choice(stringResource(R.string.player_subtitles_off), subtitle == null) { chooseSubtitle(null) }
            ChoiceMenu(stringResource(R.string.player_subtitles), listOf(off) + info.subtitleTracks.mapIndexed { place, track ->
                Choice(subtitleLabel(track, place), track.id == subtitle) { chooseSubtitle(track) }
            }, menuFirst)
        }

        val left = nextIn
        if (left != null && next != null) {
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 32.dp, bottom = 32.dp)
                    .width(340.dp)
                    .clip(shape)
                    .background(TvColors.Background.copy(alpha = 0.95f))
                    .border(1.dp, Color(0x33FFFFFF), shape)
                    .padding(18.dp)
            ) {
                Text(
                    stringResource(if (next.episode != null) R.string.player_next_episode_in else R.string.player_next_film_in, left),
                    color = TvColors.Muted, fontSize = 14.sp
                )
                next.title?.let { Text(it, color = TvColors.Text, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton(stringResource(R.string.player_play_now), Modifier.focusRequester(nextButton), primary = true) { onNext(next.id) }
                    TvButton(stringResource(R.string.player_cancel)) { if (ended) onExit() else nextIn = null }
                }
            }
        }
    }
}

private class Choice(val label: String, val selected: Boolean, val onClick: () -> Unit)

/** A list down the right of the picture, focus on what's chosen now. */
@Composable
private fun ChoiceMenu(title: String, choices: List<Choice>, focus: FocusRequester) {
    val focused = choices.firstOrNull { it.selected } ?: choices.firstOrNull()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier.fillMaxHeight().width(330.dp).background(TvColors.Background.copy(alpha = 0.95f)).padding(horizontal = 18.dp, vertical = 26.dp)
        ) {
            Text(title, color = TvColors.Text, fontSize = 20.sp, modifier = Modifier.padding(bottom = 12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(choices, key = { it.label }) { choice ->
                    TvButton(
                        (if (choice.selected) "✓  " else "") + choice.label,
                        Modifier.fillMaxWidth().let { if (choice === focused) it.focusRequester(focus) else it },
                        onClick = choice.onClick
                    )
                }
            }
        }
    }
}
