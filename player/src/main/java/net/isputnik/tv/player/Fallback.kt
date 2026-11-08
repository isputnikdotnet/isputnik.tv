package net.isputnik.tv.player

import androidx.media3.common.PlaybackException

/** Why the file as it is can't be played on this device. */
enum class PlayFailure { DECODER, NETWORK, GONE, OTHER }

/**
 * What to do when something goes wrong. Pure, because this is the rule that keeps a file
 * from ending in a crash: try the original, say which device can't, and move to the
 * server's converted copy once. A lost network isn't the file's fault and converting
 * wouldn't help, so that one is reported rather than retried as a conversion.
 */
object Fallback {
    sealed interface Decision {
        data object KeepGoing : Decision
        data object UseConverted : Decision
        data class Stop(val failure: PlayFailure) : Decision
    }

    /** The tracks are known: does the device have a decoder for picture and sound? */
    fun onTracks(
        hasVideo: Boolean, videoSupported: Boolean,
        hasAudio: Boolean, audioSupported: Boolean,
        usingConverted: Boolean, hasConverted: Boolean
    ): Decision {
        if ((!hasVideo || videoSupported) && (!hasAudio || audioSupported)) return Decision.KeepGoing
        return afterDecoderProblem(usingConverted, hasConverted)
    }

    /**
     * Names a player error. `httpStatus` is the server's answer when one came back;
     * `readerCrashed` is true when Media3's own code threw while reading the file
     * (Loader.UnexpectedLoaderException) — Media3 files that under "IO", but it is the
     * file, not the network: an iTunes .m4v whose AC3 header holds an out-of-range
     * bitrate code crashed Media3 1.11's Ac3Util, and the copy the server makes plays.
     */
    fun classify(errorCode: Int, httpStatus: Int?, readerCrashed: Boolean): PlayFailure = when {
        httpStatus == 404 || httpStatus == 410 -> PlayFailure.GONE
        readerCrashed -> PlayFailure.OTHER
        errorCode in IO_ERRORS -> PlayFailure.NETWORK
        errorCode in DECODER_ERRORS || errorCode in AUDIO_TRACK_ERRORS -> PlayFailure.DECODER
        else -> PlayFailure.OTHER
    }

    private val IO_ERRORS = PlaybackException.ERROR_CODE_IO_UNSPECIFIED..PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
    private val DECODER_ERRORS = PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED
    private val AUDIO_TRACK_ERRORS = PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED..PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED

    fun onError(failure: PlayFailure, usingConverted: Boolean, hasConverted: Boolean): Decision = when (failure) {
        PlayFailure.NETWORK, PlayFailure.GONE -> Decision.Stop(failure)
        PlayFailure.DECODER, PlayFailure.OTHER -> afterDecoderProblem(usingConverted, hasConverted, failure)
    }

    private fun afterDecoderProblem(usingConverted: Boolean, hasConverted: Boolean, failure: PlayFailure = PlayFailure.DECODER): Decision =
        if (!usingConverted && hasConverted) Decision.UseConverted else Decision.Stop(failure)
}
