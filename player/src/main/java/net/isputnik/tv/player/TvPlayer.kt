package net.isputnik.tv.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.OkHttpClient

private const val TAG = "TvPlayer"
// A decoder that says yes and draws nothing counts as failed (T0).
private const val FIRST_FRAME_TIMEOUT_MS = 12_000L

/** A subtitle the server serves as WebVTT, inside the file or beside it. */
data class SideSubtitle(val id: String, val url: String, val language: String?)

/** What's to be played. `original` is the file; `converted` the server's copy for when this
 *  device can't. Either may be missing: an older engine names no original. */
data class PlaySource(
    val original: String?,
    val converted: String?,
    val startMs: Long,
    val subtitles: List<SideSubtitle> = emptyList()
)

interface TvPlayerEvents {
    fun onSwitchedToConverted()
    fun onFailed(failure: PlayFailure)
    fun onEnded()
    fun onPlayingChanged(playing: Boolean)
}

/** Media3 with the fallback rule of Fallback.kt. It carries the session through `http`, so
 *  every byte goes through home's gateway the way the browser player's do. */
class TvPlayer(context: Context, http: OkHttpClient, private val source: PlaySource, private val events: TvPlayerEvents) {
    private val dataSources = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(http))
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources))
        .build()

    private val handler = Handler(Looper.getMainLooper())
    private var firstFrame = false
    private var finished = false
    private var converted = source.converted

    /** The audio track wanted in the original, by its place among the file's audio tracks. */
    private var wantedAudio: Int? = null
    /** The subtitle wanted, by the server's id; null for none. */
    private var wantedSubtitle: String? = null

    var usingConverted = source.original == null
        private set

    /** This device couldn't play the original: going back to it would fail again. */
    var fellBack = false
        private set

    private val watchdog = Runnable { if (!firstFrame) apply(Fallback.onError(PlayFailure.DECODER, usingConverted, converted != null)) }

    private val listener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            val video = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
            val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            if (video.isEmpty() && audio.isEmpty()) return
            apply(Fallback.onTracks(
                video.isNotEmpty(), video.any { it.isSupported },
                audio.isNotEmpty(), audio.any { it.isSupported },
                usingConverted, converted != null
            ))
            applySelections(tracks)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "${error.errorCodeName}: ${error.message}")
            apply(Fallback.onError(classify(error), usingConverted, converted != null))
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY && !firstFrame) {
                handler.removeCallbacks(watchdog)
                handler.postDelayed(watchdog, FIRST_FRAME_TIMEOUT_MS)
            }
            if (state == Player.STATE_ENDED && !finished) {
                finished = true
                events.onEnded()
            }
        }

        override fun onRenderedFirstFrame() {
            firstFrame = true
            handler.removeCallbacks(watchdog)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = events.onPlayingChanged(isPlaying)
    }

    private fun classify(error: PlaybackException): PlayFailure = when {
        (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode in setOf(404, 410) -> PlayFailure.GONE
        error.errorCode in PlaybackException.ERROR_CODE_IO_UNSPECIFIED..PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> PlayFailure.NETWORK
        error.errorCode in PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED -> PlayFailure.DECODER
        error.errorCode in PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED..PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED -> PlayFailure.DECODER
        else -> PlayFailure.OTHER
    }

    private fun apply(decision: Fallback.Decision) {
        if (finished) return
        when (decision) {
            Fallback.Decision.KeepGoing -> Unit
            Fallback.Decision.UseConverted -> {
                fellBack = true
                Log.i(TAG, "switching to the converted copy at ${player.currentPosition}ms")
                playConverted(converted!!)
                events.onSwitchedToConverted()
            }
            is Fallback.Decision.Stop -> {
                finished = true
                handler.removeCallbacks(watchdog)
                player.stop()
                events.onFailed(decision.failure)
            }
        }
    }

    /** Asks for what's wanted, once the tracks are known. Nothing else picks: a file's own
     *  default subtitle must not switch itself on behind the person's choice. */
    private fun applySelections(tracks: Tracks) {
        val before = player.trackSelectionParameters
        val params = before.buildUpon()
        val subtitle = wantedSubtitle
        if (subtitle == null) {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).clearOverridesOfType(C.TRACK_TYPE_TEXT)
        } else {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            tracks.groups.firstOrNull { group -> group.type == C.TRACK_TYPE_TEXT && isSubtitle(group, subtitle) }?.let {
                params.setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, 0))
            }
        }
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val group = wantedAudio?.let { audioGroups.getOrNull(it) }?.takeIf { it.isSupported }
        if (group != null && !usingConverted) {
            params.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
        } else {
            params.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        }
        val after = params.build()
        if (after != before) player.trackSelectionParameters = after
        val selected = tracks.groups.filter { it.isSelected }.joinToString { "${it.type}:${it.getTrackFormat(0).language ?: it.getTrackFormat(0).id}" }
        Log.i(TAG, "selected $selected")
    }

    /** A side-loaded track keeps the id it was given, behind a prefix Media3 adds. */
    private fun isSubtitle(group: Tracks.Group, id: String): Boolean {
        val format = group.getTrackFormat(0).id ?: return false
        return format == id || format.endsWith(":$id")
    }

    private fun load(url: String, positionMs: Long) {
        firstFrame = false
        handler.removeCallbacks(watchdog)
        val item = MediaItem.Builder().setUri(Uri.parse(url)).setSubtitleConfigurations(source.subtitles.map { subtitle ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                .setId(subtitle.id)
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage(subtitle.language)
                .build()
        }).build()
        player.setMediaItem(item, positionMs)
        player.prepare()
        player.playWhenReady = true
    }

    fun start(audioPosition: Int?, subtitle: String?) {
        wantedAudio = audioPosition
        wantedSubtitle = subtitle
        player.addListener(listener)
        val first = source.original ?: source.converted
        if (first == null) {
            finished = true
            events.onFailed(PlayFailure.DECODER)
            return
        }
        load(first, source.startMs)
    }

    fun selectSubtitle(id: String?) {
        wantedSubtitle = id
        applySelections(player.currentTracks)
    }

    /** False when this device has no decoder for that track, so it has to come converted. */
    fun canPlayAudio(position: Int): Boolean =
        player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.getOrNull(position)?.isSupported == true

    fun selectAudio(position: Int) {
        wantedAudio = position
        applySelections(player.currentTracks)
    }

    /** Another copy from the server, from the same place: another sound, or burned subtitles. */
    fun playConverted(url: String) {
        usingConverted = true
        converted = url
        load(url, player.currentPosition)
    }

    /** Back to the file itself, after burned subtitles were turned off. */
    fun playOriginal(audioPosition: Int?) {
        val original = source.original ?: return
        usingConverted = false
        wantedAudio = audioPosition
        load(original, player.currentPosition)
    }

    /** Whether the player is on a copy the file itself could replace. */
    val hasOriginal: Boolean get() = source.original != null && !fellBack

    fun release() {
        finished = true
        handler.removeCallbacksAndMessages(null)
        player.removeListener(listener)
        player.release()
    }
}
