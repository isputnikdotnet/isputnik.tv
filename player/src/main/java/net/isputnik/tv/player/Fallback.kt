package net.isputnik.tv.player

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

    fun onError(failure: PlayFailure, usingConverted: Boolean, hasConverted: Boolean): Decision = when (failure) {
        PlayFailure.NETWORK, PlayFailure.GONE -> Decision.Stop(failure)
        PlayFailure.DECODER, PlayFailure.OTHER -> afterDecoderProblem(usingConverted, hasConverted, failure)
    }

    private fun afterDecoderProblem(usingConverted: Boolean, hasConverted: Boolean, failure: PlayFailure = PlayFailure.DECODER): Decision =
        if (!usingConverted && hasConverted) Decision.UseConverted else Decision.Stop(failure)
}
