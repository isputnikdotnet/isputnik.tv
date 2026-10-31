package net.isputnik.tv.video

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoApiTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val abs = { path: String -> "http://home$path" }

    private fun info(playback: String, resumeAt: Double = 0.0) = json.decodeFromString<PlayInfo>(
        """{"id":"a","title":"Film","future":"ignored","playback":$playback,"resumeAt":$resumeAt}"""
    )

    @Test fun `the file first, the converted copy as the way out`() {
        val source = info("""{"mode":"transcode","url":"/video/api/hls/a/t1/index.m3u8","original":"/video/api/stream/a"}""", 61.5).source(abs)
        assertEquals("http://home/video/api/stream/a", source.original)
        assertEquals("http://home/video/api/hls/a/t1/index.m3u8", source.converted)
        assertEquals(61_500L, source.startMs)
    }

    @Test fun `a file that plays direct has no copy to fall back on`() {
        val source = info("""{"mode":"direct","url":"/video/api/stream/a","original":"/video/api/stream/a"}""").source(abs)
        assertEquals("http://home/video/api/stream/a", source.original)
        assertNull(source.converted)
    }

    @Test fun `an engine that names no original plays what it chose`() {
        val hls = info("""{"mode":"remux","url":"/video/api/hls/a/t1/index.m3u8"}""").source(abs)
        assertNull(hls.original)
        assertEquals("http://home/video/api/hls/a/t1/index.m3u8", hls.converted)
        assertEquals("http://home/video/api/stream/a", info("""{"mode":"direct","url":"/video/api/stream/a"}""").source(abs).original)
    }

    @Test fun `a busy server still offers the original, but no copy`() {
        val source = info("""{"mode":"transcode","url":"/video/api/hls/a/t1/index.m3u8","original":"/video/api/stream/a","busy":{"limit":2,"reason":"household"}}""").source(abs)
        assertEquals("http://home/video/api/stream/a", source.original)
        assertNull(source.converted)
    }

    @Test fun `times read like a player's`() {
        assertEquals("4:07", formatPlayTime(247_000))
        assertEquals("1:05:09", formatPlayTime(3_909_000))
        assertEquals("0:00", formatPlayTime(-5))
    }
}
