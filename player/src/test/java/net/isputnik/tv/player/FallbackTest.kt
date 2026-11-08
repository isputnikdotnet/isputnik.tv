package net.isputnik.tv.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Test

class FallbackTest {
    private fun tracks(video: Boolean = true, audio: Boolean = true, converted: Boolean = false, hasConverted: Boolean = true) =
        Fallback.onTracks(true, video, true, audio, converted, hasConverted)

    @Test fun `what the device decodes keeps playing`() {
        assertEquals(Fallback.Decision.KeepGoing, tracks())
    }

    @Test fun `a picture or a sound it can't decode moves to the converted copy`() {
        assertEquals(Fallback.Decision.UseConverted, tracks(video = false))
        assertEquals(Fallback.Decision.UseConverted, tracks(audio = false))
    }

    @Test fun `a silent film isn't missing its sound`() {
        assertEquals(Fallback.Decision.KeepGoing, Fallback.onTracks(true, true, false, false, false, true))
    }

    @Test fun `only once, and only if there is a copy`() {
        val stop = Fallback.Decision.Stop(PlayFailure.DECODER)
        assertEquals(stop, tracks(video = false, converted = true))
        assertEquals(stop, tracks(video = false, hasConverted = false))
    }

    @Test fun `a lost connection isn't the file's fault`() {
        assertEquals(Fallback.Decision.Stop(PlayFailure.NETWORK), Fallback.onError(PlayFailure.NETWORK, false, true))
        assertEquals(Fallback.Decision.Stop(PlayFailure.GONE), Fallback.onError(PlayFailure.GONE, false, true))
    }

    @Test fun `errors are named by what went wrong`() {
        assertEquals(PlayFailure.NETWORK, Fallback.classify(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, null, false))
        assertEquals(PlayFailure.NETWORK, Fallback.classify(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 502, false))
        assertEquals(PlayFailure.GONE, Fallback.classify(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 404, false))
        assertEquals(PlayFailure.DECODER, Fallback.classify(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, null, false))
        assertEquals(PlayFailure.DECODER, Fallback.classify(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED, null, false))
        assertEquals(PlayFailure.OTHER, Fallback.classify(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, null, false))
    }

    @Test fun `a crash reading the file is the file's fault, and the copy is tried`() {
        // Media3 1.11 threw in Ac3Util on an iTunes .m4v and called it ERROR_CODE_IO_UNSPECIFIED.
        val failure = Fallback.classify(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, null, true)
        assertEquals(PlayFailure.OTHER, failure)
        assertEquals(Fallback.Decision.UseConverted, Fallback.onError(failure, false, true))
    }

    @Test fun `a decoder error part way through still falls back`() {
        assertEquals(Fallback.Decision.UseConverted, Fallback.onError(PlayFailure.DECODER, false, true))
        assertEquals(Fallback.Decision.Stop(PlayFailure.OTHER), Fallback.onError(PlayFailure.OTHER, true, true))
    }
}
