package net.isputnik.tv.video

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.isputnik.tv.player.Copy
import net.isputnik.tv.player.Scaling
import net.isputnik.tv.player.TvPlayer
import net.isputnik.tv.player.codecName
import net.isputnik.tv.player.resolutionLabel
import net.isputnik.tv.player.scaling

/** What is on the screen now, read from Media3 and the display: the picture's size and
 *  codec, the sound's codec, and the size of the screen the TV sends out. */
internal data class NowPlaying(
    val width: Int, val height: Int, val pixelRatio: Float,
    val videoMime: String?, val audioMime: String?,
    val screenWidth: Int, val screenHeight: Int,
    val converted: Boolean
)

/** The display's current mode, not the app's window: a Fire TV draws its menus at 1080p
 *  and still sends 4K to the set. A TV that matches the film's frame changes it as it plays. */
internal fun readNowPlaying(context: Context, tv: TvPlayer): NowPlaying? {
    val player = tv.player
    val size = player.videoSize
    if (size.width <= 0 || size.height <= 0) return null
    val mode = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.mode
    // A mode is given as the panel is built, which on a phone is upright; the film plays across.
    val long = maxOf(mode?.physicalWidth ?: 0, mode?.physicalHeight ?: 0)
    val short = minOf(mode?.physicalWidth ?: 0, mode?.physicalHeight ?: 0)
    return NowPlaying(
        size.width, size.height, size.pixelWidthHeightRatio,
        player.videoFormat?.sampleMimeType, player.audioFormat?.sampleMimeType,
        long, short,
        tv.usingConverted
    )
}

/** "1080p · H.264 · AAC · Converted copy, 720p · Upscaled to the 4K screen" */
@Composable
internal fun qualityLine(now: NowPlaying, copy: Copy): String {
    val parts = mutableListOf<String>()
    resolutionLabel(now.width, now.height)?.let(parts::add)
    codecName(now.videoMime)?.let(parts::add)
    codecName(now.audioMime)?.let(parts::add)
    parts += when (copy) {
        Copy.Original -> stringResource(R.string.quality_original)
        Copy.Repacked -> stringResource(R.string.quality_repacked)
        is Copy.Converted -> copy.rung?.let { stringResource(R.string.quality_converted_rung, "${it}p") } ?: stringResource(R.string.quality_converted)
    }
    val screen = resolutionLabel(now.screenWidth, now.screenHeight)
    when (scaling(now.width, now.height, now.pixelRatio, now.screenWidth, now.screenHeight)) {
        Scaling.UPSCALE -> parts += stringResource(R.string.quality_upscaled, screen ?: "")
        Scaling.DOWNSCALE -> parts += stringResource(R.string.quality_downscaled, screen ?: "")
        Scaling.NATIVE -> parts += stringResource(R.string.quality_native)
        null -> Unit
    }
    return parts.joinToString(" · ")
}
