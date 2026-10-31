package net.isputnik.tv.video

import android.content.Context
import kotlinx.serialization.Serializable
import java.util.Locale

// The player's rules, the same as the browser's (home's VideoPlayerPage): languages chosen
// last time carry over, and the times come from the engine's markers. Kept pure to test.

@Serializable
data class AudioTrack(
    val index: Int, val codec: String = "", val channels: Int = 0, val language: String? = null,
    val title: String? = null, val default: Boolean = false, val selected: Boolean = false
)

@Serializable
data class SubtitleTrack(
    val id: String, val kind: String = "text", val language: String? = null, val title: String? = null,
    val default: Boolean = false, val forced: Boolean = false, val url: String? = null, val burned: Boolean = false
)

@Serializable data class IntroMark(val start: Double, val end: Double)
@Serializable data class CreditsMark(val start: Double)
@Serializable data class Markers(val intro: IntroMark? = null, val credits: CreditsMark? = null)
@Serializable data class NextTitle(val id: String, val season: Int? = null, val episode: Int? = null, val title: String? = null)

const val SUBTITLES_OFF = "off"

/** "rus" in a file and "ru" in a file name are one language. */
fun sameLanguage(a: String?, b: String?): Boolean {
    if (a == null || b == null) return false
    if (a.equals(b, ignoreCase = true)) return true
    return iso3(a) == iso3(b)
}

@Suppress("DEPRECATION")
private fun iso3(code: String): String = runCatching { Locale(code).isO3Language }.getOrDefault(code).ifEmpty { code }

fun pickAudio(tracks: List<AudioTrack>, preference: String?): AudioTrack? =
    tracks.firstOrNull { sameLanguage(it.language, preference) } ?: tracks.firstOrNull { it.selected } ?: tracks.firstOrNull()

/** In the language chosen last time, full ones before forced and text before image; none once
 *  they were turned off; else the one the file marks as default. */
fun pickSubtitle(tracks: List<SubtitleTrack>, preference: String?): SubtitleTrack? {
    if (preference == SUBTITLES_OFF) return null
    if (preference != null) {
        val rank = { track: SubtitleTrack -> (if (track.forced) 2 else 0) + (if (track.kind == "image") 1 else 0) }
        tracks.filter { sameLanguage(it.language, preference) }.minByOrNull(rank)?.let { return it }
    }
    return tracks.firstOrNull { it.default && !it.forced }
}

fun inIntro(markers: Markers?, seconds: Double): Boolean {
    val intro = markers?.intro ?: return false
    return seconds >= intro.start && seconds < intro.end
}

fun inCredits(markers: Markers?, seconds: Double): Boolean {
    val credits = markers?.credits ?: return false
    return seconds >= credits.start
}

/** A language in the person's own words; null for none or for "und". */
fun languageName(code: String?, locale: Locale): String? {
    if (code.isNullOrBlank() || code == "und") return null
    @Suppress("DEPRECATION")
    val name = Locale(code).getDisplayLanguage(locale)
    return name.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }?.replaceFirstChar { it.titlecase(locale) } ?: code
}

fun channelsName(channels: Int): String? = when {
    channels == 6 -> "5.1"
    channels == 8 -> "7.1"
    else -> null
}

/** What this TV last chose, for each person on it. */
class PlayerPrefs(context: Context, userId: String) {
    private val store = context.getSharedPreferences("player-$userId", Context.MODE_PRIVATE)
    var audio: String?
        get() = store.getString("audio", null)
        set(value) = store.edit().putString("audio", value).apply()
    var subtitle: String?
        get() = store.getString("subtitle", null)
        set(value) = store.edit().putString("subtitle", value).apply()
}
