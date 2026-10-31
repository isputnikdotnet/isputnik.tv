package net.isputnik.tv.core

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class ReleasesTest {
    private val server = MockWebServer()
    private val http = OkHttpClient()

    @After
    fun tearDown() = server.shutdown()

    private fun release(tag: String, asset: String = "iSputnik-TV.apk") =
        MockResponse().setBody("""{"tag_name":"$tag","extra":1,"assets":[{"name":"notes.txt","browser_download_url":"http://x/n"},{"name":"$asset","browser_download_url":"http://x/a"}]}""")

    @Test
    fun comparesVersionsByNumberNotAsText() {
        assertTrue(Releases.isNewer("0.10.0", "0.9.0"))
        assertTrue(Releases.isNewer("v1.0.0", "0.9.9"))
        assertTrue(Releases.isNewer("0.2.1", "0.2"))
        assertFalse(Releases.isNewer("0.2.0", "0.2.0"))
        assertFalse(Releases.isNewer("0.1.9", "0.2.0"))
    }

    @Test
    fun anOddTagOffersNothing() {
        assertFalse(Releases.isNewer("nightly", "0.2.0"))
        assertFalse(Releases.isNewer("1.0.0-beta", "0.2.0"))
        assertFalse(Releases.isNewer("9.0.0", "dev"))
    }

    @Test
    fun aNewerReleaseIsOfferedWithItsApk() = runBlocking {
        server.enqueue(release("v0.3.0"))
        val update = Releases.check(http, server.url("/latest").toString(), "0.2.0")
        assertEquals(AvailableUpdate("0.3.0", "http://x/a"), update)
    }

    @Test
    fun theSameVersionOrAReleaseWithNoApkOffersNothing() = runBlocking {
        server.enqueue(release("v0.2.0"))
        assertNull(Releases.check(http, server.url("/latest").toString(), "0.2.0"))
        server.enqueue(release("v0.3.0", asset = "source.zip"))
        assertNull(Releases.check(http, server.url("/latest").toString(), "0.2.0"))
    }

    @Test(expected = IOException::class)
    fun aRefusedCheckThrows() {
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(403))
            Releases.check(http, server.url("/latest").toString(), "0.2.0")
        }
    }

    @Test
    fun aDownloadArrivesWhole() = runBlocking {
        val target = File(Files.createTempDirectory("releases").toFile(), "updates/app.apk")
        server.enqueue(MockResponse().setBody("apk bytes"))
        val seen = mutableListOf<Int>()
        Releases.download(http, server.url("/a").toString(), target) { seen += it }
        assertEquals("apk bytes", target.readText())
        assertEquals(100, seen.last())
    }

    @Test
    fun aFailedDownloadLeavesNothingBehind() = runBlocking {
        val folder = Files.createTempDirectory("releases").toFile()
        server.enqueue(MockResponse().setResponseCode(500))
        val failed = runCatching { Releases.download(http, server.url("/a").toString(), File(folder, "app.apk")) { } }
        assertTrue(failed.isFailure)
        assertEquals(0, folder.listFiles()?.size)
    }
}
