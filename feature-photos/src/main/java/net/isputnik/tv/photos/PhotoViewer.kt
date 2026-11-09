package net.isputnik.tv.photos

import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import net.isputnik.tv.core.PageMessage
import net.isputnik.tv.core.requestWhenReady
import androidx.compose.ui.input.key.key as composeKey

/** The words over a photo, light on the dark gradient under them in every theme. */
private val CaptionText = Color(0xFFF1F3EE)
private val CaptionMuted = Color(0xB3F1F3EE)

/**
 * A pick played live (P2 of home's docs/tv-photos-plan.md): each photo fitted to the screen on
 * black, for its slideshow's time or an album's six seconds, changing as the slideshow says.
 * The next photo is fetched ahead, so a slide never waits on the network. Left and Right step,
 * OK pauses, any other key brings the caption back for a while, and Back leaves (the shell's).
 */
@Composable
internal fun PhotoViewer(photos: PhotosApi, item: TvPhotoItem) {
    val assets = remember(item) { mutableStateListOf<PhotoAsset>() }
    /** How many there are to see; -1 until home says. */
    var total by remember(item) { mutableIntStateOf(-1) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(item, attempt) {
        failed = false
        try {
            // The first page shows at once; the rest follow while it plays.
            do {
                val page = photos.page(item, assets.size)
                if (page.assets.isEmpty()) break
                assets += page.assets
                total = page.total
            } while (assets.size < total)
            total = assets.size
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Partway through, go round what came; with nothing yet, say so.
            if (assets.isEmpty()) failed = true else total = assets.size
        }
    }

    val view = LocalView.current
    DisposableEffect(view) {
        // A show is watched without touching the remote: no screensaver over it.
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    when {
        assets.isEmpty() && failed -> PageMessage(stringResource(R.string.photos_load_failed), stringResource(R.string.photos_retry)) { attempt++ }
        assets.isEmpty() && total == 0 -> PageMessage(stringResource(R.string.photos_nothing))
        assets.isEmpty() -> Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.photos_loading), color = CaptionMuted, fontSize = 16.sp)
        }
        else -> Show(photos, item, assets, total)
    }
}

@Composable
private fun Show(photos: PhotosApi, item: TvPhotoItem, assets: List<PhotoAsset>, total: Int) {
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    /** The slide whose picture has drawn (or failed to): its time starts only then. */
    var drawn by remember { mutableIntStateOf(-1) }
    /** Which way the last step went, for a sliding change. */
    var direction by remember { mutableIntStateOf(1) }
    /** Counts the presses that bring the caption back. */
    var wake by remember { mutableIntStateOf(0) }
    var captionOn by remember { mutableStateOf(true) }
    LaunchedEffect(wake) {
        captionOn = true
        delay(CAPTION_MILLIS)
        captionOn = false
    }

    fun step(by: Int) {
        stepFrom(index, by, assets.size, total)?.let {
            direction = if (by < 0) -1 else 1
            index = it
        }
    }

    // The clock: once this slide is on screen, its time; then on, as soon as the next is here.
    LaunchedEffect(index, paused, drawn == index) {
        if (paused || drawn != index) return@LaunchedEffect
        delay((secondsFor(item, assets.getOrNull(index)) * 1000).toLong())
        val next = snapshotFlow { stepFrom(index, 1, assets.size, total) }.filterNotNull().first()
        direction = 1
        index = next
    }

    // The next picture, fetched ahead into the image cache.
    val context = LocalContext.current
    LaunchedEffect(index, assets.size, total) {
        val next = stepFrom(index, 1, assets.size, total) ?: return@LaunchedEffect
        assets[next].picture?.let { SingletonImageLoader.get(context).enqueue(ImageRequest.Builder(context).data(photos.absolute(it)).build()) }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestWhenReady() }
    val change = remember(item) { Change.of(if (item.isSlideshow) item.transition else null) }

    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).focusable().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.composeKey.nativeKeyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { step(-1); wake++; true }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_NEXT -> { step(1); wake++; true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> { paused = !paused; wake++; true }
                // Back is the shell's: it leaves.
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> false
                else -> { wake++; true }
            }
        }
    ) {
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                when (change) {
                    Change.NONE -> EnterTransition.None togetherWith ExitTransition.None
                    Change.CROSSFADE -> fadeIn(tween(800)) togetherWith fadeOut(tween(800))
                    Change.DIP -> fadeIn(tween(450, delayMillis = 450)) togetherWith fadeOut(tween(450))
                    Change.SLIDE -> slideInHorizontally(tween(700)) { it * direction } togetherWith slideOutHorizontally(tween(700)) { -it * direction }
                }
            },
            label = "slide",
            modifier = Modifier.fillMaxSize()
        ) { shownIndex ->
            val picture = assets.getOrNull(shownIndex)?.picture
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                if (picture == null) {
                    LaunchedEffect(shownIndex) { drawn = shownIndex }
                } else {
                    AsyncImage(
                        model = photos.absolute(picture),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        onSuccess = { drawn = shownIndex },
                        onError = { drawn = shownIndex },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        AnimatedVisibility(captionOn || paused, enter = fadeIn(tween(250)), exit = fadeOut(tween(600)), modifier = Modifier.align(Alignment.BottomStart)) {
            Caption(item, assets.getOrNull(index), index, total, paused)
        }
    }
}

@Composable
private fun Caption(item: TvPhotoItem, asset: PhotoAsset?, index: Int, total: Int, paused: Boolean) {
    val locale = LocalConfiguration.current.locales[0]
    val decade = stringResource(R.string.photos_decade)
    val line = listOfNotNull(
        asset?.let { takenText(it.takenAt, it.takenPrecision, locale) { year -> decade.format(year) } },
        asset?.placeText?.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f)))
            .padding(start = 48.dp, end = 48.dp, top = 60.dp, bottom = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(item.name.uppercase(), color = CaptionMuted, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (line.isNotEmpty()) {
                    Text(line, color = CaptionText, fontSize = 24.sp, fontFamily = FontFamily.Serif, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
                Text(stringResource(R.string.photos_keys), color = CaptionMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (paused) {
                    Text(
                        stringResource(R.string.photos_paused),
                        color = CaptionText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Text("${index + 1} / ${maxOf(total, index + 1)}", color = CaptionMuted, fontSize = 13.sp)
            }
        }
    }
}
