package net.isputnik.tv.player

// What is playing, in the words people use for it: "1080p", "H.264", "upscaled to the
// screen". Kept pure to test; the player screen reads the numbers from Media3.

/**
 * A picture's size as people name it. A film cropped to a wide frame keeps its name
 * (1920×800 is still "1080p"), so the width counts as much as the height; a DVD is
 * 576p (PAL) or 480p (NTSC); anything smaller says its own height.
 */
fun resolutionLabel(width: Int, height: Int): String? {
    if (width <= 0 || height <= 0) return null
    return when {
        width >= 3200 || height >= 1800 -> "4K"
        width >= 2200 || height >= 1300 -> "1440p"
        width >= 1600 || height >= 900 -> "1080p"
        width >= 1100 || height >= 650 -> "720p"
        height >= 540 -> "576p"
        height >= 400 || width >= 640 -> "480p"
        else -> "${height}p"
    }
}

/** What the TV does to the picture to fill the screen. */
enum class Scaling { UPSCALE, NATIVE, DOWNSCALE }

/**
 * The picture fitted to the screen whole, the way the player draws it (letterboxed, never
 * cropped). A pixel that isn't square (an anamorphic DVD) is widened first. Within 5% is
 * the screen's own size: nobody sees a 1920×1036 picture on 1920×1080 as scaled.
 */
fun scaling(videoWidth: Int, videoHeight: Int, pixelRatio: Float, screenWidth: Int, screenHeight: Int): Scaling? {
    if (videoWidth <= 0 || videoHeight <= 0 || screenWidth <= 0 || screenHeight <= 0) return null
    val shownWidth = videoWidth * (if (pixelRatio > 0f) pixelRatio else 1f)
    val factor = minOf(screenWidth / shownWidth, screenHeight / videoHeight.toFloat())
    return when {
        factor > 1.05f -> Scaling.UPSCALE
        factor < 0.95f -> Scaling.DOWNSCALE
        else -> Scaling.NATIVE
    }
}

/** A codec by the name on the box, from Media3's MIME type; null when it has none. */
fun codecName(mimeType: String?): String? = when (mimeType) {
    null -> null
    "video/avc" -> "H.264"
    "video/hevc" -> "HEVC"
    "video/av01" -> "AV1"
    "video/x-vnd.on2.vp9" -> "VP9"
    "video/x-vnd.on2.vp8" -> "VP8"
    "video/mp4v-es" -> "MPEG-4"
    "video/mpeg2" -> "MPEG-2"
    "video/dolby-vision" -> "Dolby Vision"
    "audio/mp4a-latm" -> "AAC"
    "audio/ac3" -> "Dolby Digital"
    "audio/eac3", "audio/eac3-joc" -> "Dolby Digital Plus"
    "audio/true-hd" -> "Dolby TrueHD"
    "audio/vnd.dts", "audio/vnd.dts.hd", "audio/vnd.dts.hd;profile=lbr" -> "DTS"
    "audio/opus" -> "Opus"
    "audio/vorbis" -> "Vorbis"
    "audio/mpeg" -> "MP3"
    "audio/flac" -> "FLAC"
    "audio/raw" -> "PCM"
    else -> mimeType.substringAfter('/').removePrefix("x-").uppercase()
}

/** Which copy the server is sending: the file itself, the file's picture repacked as it is
 *  (another container or sound), or a picture made again — at a rung's height when one was
 *  asked for (480, 720, 1080), else at most 1080p. */
sealed interface Copy {
    data object Original : Copy
    data object Repacked : Copy
    data class Converted(val rung: Int?) : Copy
}

/** The engine's answer as the copy playing: `mode` is "direct", "remux" or "transcode". */
fun copyOf(usingConverted: Boolean, mode: String?, rung: Int?): Copy = when {
    !usingConverted -> Copy.Original
    mode == "remux" -> Copy.Repacked
    else -> Copy.Converted(rung)
}
