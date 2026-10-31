package net.isputnik.tv.video

import kotlinx.serialization.Serializable
import net.isputnik.tv.core.ApiClient
import net.isputnik.tv.player.PlaySource

// What the engine answers (isputnik.video, through home's gateway). Only what the app
// uses: anything else in an answer is ignored, so a newer engine can't break an older TV.

@Serializable data class Status(val enabled: Boolean = false)
@Serializable data class Page<T>(val items: List<T> = emptyList(), val total: Int = 0)
@Serializable data class Summary(val id: String, val title: String, val year: Int? = null, val poster: String? = null)
@Serializable data class ShowRef(val id: String, val title: String)
@Serializable data class Progress(val position: Double = 0.0, val duration: Double? = null, val watched: Boolean = false)

@Serializable
data class ContinueItem(
    val id: String, val type: String, val title: String, val show: ShowRef? = null,
    val season: Int? = null, val episode: Int? = null,
    val position: Double = 0.0, val duration: Double? = null, val resume: Boolean = true
)
@Serializable data class ContinueList(val items: List<ContinueItem> = emptyList())

@Serializable data class Episode(val id: String, val episode: Int, val title: String? = null, val progress: Progress? = null)
@Serializable data class Season(val number: Int, val episodes: List<Episode> = emptyList())
@Serializable data class NextUp(val id: String, val season: Int, val episode: Int, val position: Double = 0.0)
@Serializable data class Show(val id: String, val title: String, val plot: String? = null, val seasons: List<Season> = emptyList(), val nextUp: NextUp? = null)

@Serializable data class Busy(val limit: Int = 0, val reason: String? = null, val title: String? = null)
@Serializable data class Playback(val mode: String = "none", val url: String? = null, val original: String? = null, val busy: Busy? = null)

@Serializable
data class PlayInfo(
    val id: String, val title: String, val show: ShowRef? = null, val season: Int? = null, val episode: Int? = null,
    val available: Boolean = true, val playback: Playback = Playback(), val resumeAt: Double = 0.0
) {
    /** The file first, the converted copy as the way out: this app decides per device what it
     *  decodes. An engine older than 0.13 names no original, and its own choice is played.
     *  A busy server has no copy to offer. */
    fun source(absolute: (String) -> String): PlaySource {
        val original = playback.original ?: playback.url.takeIf { playback.mode == "direct" }
        val converted = playback.url.takeIf { it != original && playback.busy == null }
        return PlaySource(original?.let(absolute), converted?.let(absolute), (resumeAt * 1000).toLong())
    }

    fun heading(): String = show?.title?.let { "$it · $title" } ?: title
}

@Serializable private data class ProgressBody(val position: Double, val duration: Double?, val watched: Boolean? = null)

class VideoApi(val api: ApiClient) {
    suspend fun status(): Status = api.get("/api/video/status")
    suspend fun continueWatching(): List<ContinueItem> = api.get<ContinueList>("$BASE/continue").items
    suspend fun movies(): List<Summary> = all("movies")
    suspend fun shows(): List<Summary> = all("shows")
    suspend fun show(id: String): Show = api.get("$BASE/shows/$id")
    suspend fun play(id: String): PlayInfo = api.get("$BASE/play/$id")

    private suspend fun all(kind: String): List<Summary> {
        val out = mutableListOf<Summary>()
        var page = 1
        while (true) {
            val answer = api.get<Page<Summary>>("$BASE/$kind?pageSize=200&page=$page")
            out += answer.items
            if (answer.items.isEmpty() || out.size >= answer.total) return out
            page++
        }
    }

    suspend fun saveProgress(id: String, position: Double, duration: Double?, watched: Boolean? = null) {
        val body = api.json.encodeToString(ProgressBody.serializer(), ProgressBody(position, duration, watched))
        api.send("PUT", "$BASE/progress/$id", body)
    }

    /** The engine would stop a conversion nobody reads after a minute; this saves that minute. */
    suspend fun stopConverting(id: String) {
        api.send("DELETE", "$BASE/hls/$id")
    }

    /** A show's poster stands in for its episodes, which have none of their own in this list. */
    fun posterOf(item: ContinueItem): String = api.absolute("$BASE/art/${item.show?.id ?: item.id}/poster")
    fun absolute(path: String): String = api.absolute(path)

    private companion object {
        const val BASE = "/video/api"
    }
}

/** 1:05:09 or 4:07: a place in a film, as a player shows it. */
fun formatPlayTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = (total % 60).toString().padStart(2, '0')
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$seconds" else "$minutes:$seconds"
}
