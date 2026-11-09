package net.isputnik.tv.photos

import android.content.Context
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import net.isputnik.tv.core.ProgressLine
import net.isputnik.tv.core.requestWhenReady
import okhttp3.OkHttpClient
import androidx.compose.ui.input.key.key as composeKey

/** A player that reads through the session's client, so home sees the TV's cookies. */
internal fun photoPlayer(context: Context, http: OkHttpClient): ExoPlayer =
    ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, OkHttpDataSource.Factory(http))))
        .build()

private const val SEEK_MS = 10_000L

/**
 * A slideshow's rendered movie, played as a film (P3 of home's docs/tv-photos-plan.md): Left and
 * Right jump 10 s, OK pauses, Back leaves; it leaves by itself at the end. Home offers the movie
 * only when this viewer sees every photo in it; if it won't play anyway, [onFailed] turns to
 * the live show.
 */
@Composable
internal fun SlideshowMovie(photos: PhotosApi, item: TvPhotoItem, onEnded: () -> Unit, onFailed: () -> Unit) {
    val context = LocalContext.current
    val player = remember(item) { photoPlayer(context, photos.http) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var wake by remember { mutableIntStateOf(0) }
    var barOn by remember { mutableStateOf(true) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
            }
            override fun onPlayerError(error: PlaybackException) = onFailed()
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playing = playWhenReady
            }
        }
        player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri(photos.movieUrl(item)))
        player.prepare()
        player.playWhenReady = true
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }
    LaunchedEffect(wake) {
        barOn = true
        delay(CAPTION_MILLIS)
        barOn = false
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestWhenReady() }
    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).focusable().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.composeKey.nativeKeyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { player.seekTo((player.currentPosition - SEEK_MS).coerceAtLeast(0L)); wake++; true }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    val end = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                    player.seekTo((player.currentPosition + SEEK_MS).coerceAtMost(end - 500).coerceAtLeast(0L)); wake++; true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    player.playWhenReady = !player.playWhenReady
                    wake++
                    true
                }
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> false
                else -> { wake++; true }
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
                    this.player = player
                }
            }
        )
        AnimatedVisibility(barOn || !playing, enter = fadeIn(tween(250)), exit = fadeOut(tween(600)), modifier = Modifier.align(Alignment.BottomStart)) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f)))
                    .padding(start = 48.dp, end = 48.dp, top = 60.dp, bottom = 32.dp)
            ) {
                Text(item.name, color = Color(0xFFF1F3EE), fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProgressLine(if (duration > 0) position.toFloat() / duration else 0f, Modifier.weight(1f))
                    Spacer(Modifier.width(14.dp))
                    Text("${clock(position)} / ${clock(duration)}", color = Color(0xB3F1F3EE), fontSize = 13.sp)
                }
                Text(
                    if (playing) stringResource(R.string.photos_movie_keys) else stringResource(R.string.photos_paused) + " · " + stringResource(R.string.photos_movie_keys),
                    color = Color(0xB3F1F3EE),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/** 1:05 or 1:02:05. */
internal fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
