package net.isputnik.tv.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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

/** What's to be played. `original` is the file; `converted` the server's copy for when this
 *  device can't. Either may be missing: an older engine names no original. */
data class PlaySource(val original: String?, val converted: String?, val startMs: Long)

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
    var usingConverted = source.original == null
        private set

    private val watchdog = Runnable { if (!firstFrame) apply(Fallback.onError(PlayFailure.DECODER, usingConverted, source.converted != null)) }

    private val listener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            val video = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
            val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            if (video.isEmpty() && audio.isEmpty()) return
            apply(Fallback.onTracks(
                video.isNotEmpty(), video.any { it.isSupported },
                audio.isNotEmpty(), audio.any { it.isSupported },
                usingConverted, source.converted != null
            ))
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "${error.errorCodeName}: ${error.message}")
            apply(Fallback.onError(classify(error), usingConverted, source.converted != null))
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
            Fallback.Decision.UseConverted -> switchToConverted()
            is Fallback.Decision.Stop -> {
                finished = true
                handler.removeCallbacks(watchdog)
                player.stop()
                events.onFailed(decision.failure)
            }
        }
    }

    private fun switchToConverted() {
        usingConverted = true
        firstFrame = false
        handler.removeCallbacks(watchdog)
        val position = player.currentPosition
        Log.i(TAG, "switching to the converted copy at ${position}ms")
        load(source.converted!!, position)
        events.onSwitchedToConverted()
    }

    private fun load(url: String, positionMs: Long) {
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)), positionMs)
        player.prepare()
        player.playWhenReady = true
    }

    fun start() {
        player.addListener(listener)
        val first = source.original ?: source.converted
        if (first == null) {
            finished = true
            events.onFailed(PlayFailure.DECODER)
            return
        }
        load(first, source.startMs)
    }

    fun release() {
        finished = true
        handler.removeCallbacksAndMessages(null)
        player.removeListener(listener)
        player.release()
    }
}
