package net.isputnik.tv.core

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemoryStore : TextStore {
    var text: String? = null
    override fun read() = text
    override fun write(text: String?) { this.text = text }
}

class ApiClientTest {
    private val server = MockWebServer()
    private val store = MemoryStore()

    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()

    private fun client() = ApiClient(store, "iSputnikTV/test").also {
        it.useServer(server.url("/").toString().trimEnd('/'))
    }

    @Test fun `a write first collects the CSRF cookie, then echoes it`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).addHeader("Set-Cookie", "isputnik_csrf=tok1; Path=/").setBody("""{"error":"Not authenticated"}"""))
        server.enqueue(MockResponse().setBody("""{"status":"pending"}"""))
        val answer = client().post<LinkPoll, LinkPollBody>("/api/auth/device/poll", LinkPollBody("abc"))
        assertEquals("pending", answer.status)
        assertEquals("/api/auth/me", server.takeRequest().path)
        val post = server.takeRequest()
        assertEquals("tok1", post.getHeader("X-CSRF-Token"))
        assertTrue(post.getHeader("Cookie")!!.contains("isputnik_csrf=tok1"))
        assertEquals("iSputnikTV/test", post.getHeader("User-Agent"))
    }

    @Test fun `the session outlives the app, and goes with the server`() = runBlocking {
        server.enqueue(MockResponse().addHeader("Set-Cookie", "isputnik_session=s1; Path=/; HttpOnly").setBody("""{"user":{"id":"u","displayName":"Sam"}}"""))
        assertEquals("Sam", client().currentUser()?.displayName)

        server.enqueue(MockResponse().setBody("""{"user":{"id":"u"}}"""))
        val reopened = ApiClient(store, "iSputnikTV/test")
        reopened.currentUser()
        server.takeRequest()
        assertTrue(server.takeRequest().getHeader("Cookie")!!.contains("isputnik_session=s1"))

        reopened.useServer("http://other.example")
        assertTrue(!store.text!!.contains("isputnik_session"))
    }

    @Test fun `each person keeps their own session, across runs`() = runBlocking {
        val api = client()
        server.enqueue(MockResponse().addHeader("Set-Cookie", "isputnik_session=first; Path=/").setBody("""{"user":{"id":"a","displayName":"Ann"}}"""))
        api.remember(api.currentUser()!!)
        server.takeRequest()

        api.startLinking()
        server.enqueue(MockResponse().addHeader("Set-Cookie", "isputnik_session=second; Path=/").setBody("""{"user":{"id":"b","displayName":"Bo"}}"""))
        api.remember(api.currentUser()!!)
        // Linking the second didn't carry the first one's session along.
        assertEquals(null, server.takeRequest().getHeader("Cookie"))
        assertEquals(listOf("Ann", "Bo"), api.people.map { it.name })

        val reopened = ApiClient(store, "iSputnikTV/test")
        assertEquals("b", reopened.activePerson)
        reopened.switchTo("a")
        server.enqueue(MockResponse().setBody("""{"user":{"id":"a"}}"""))
        reopened.currentUser()
        assertTrue(server.takeRequest().getHeader("Cookie")!!.contains("isputnik_session=first"))
    }

    @Test fun `leaving forgets one person, and the same person linking again replaces themselves`() = runBlocking {
        val api = client()
        server.enqueue(MockResponse().addHeader("Set-Cookie", "isputnik_session=first; Path=/").setBody("""{"user":{"id":"a","displayName":"Ann"}}"""))
        api.remember(api.currentUser()!!)
        api.startLinking()
        server.enqueue(MockResponse().addHeader("Set-Cookie", "isputnik_session=again; Path=/").setBody("""{"user":{"id":"a","displayName":"Ann"}}"""))
        api.remember(api.currentUser()!!)
        assertEquals(1, api.people.size)

        api.startLinking()
        server.enqueue(MockResponse().addHeader("Set-Cookie", "isputnik_session=second; Path=/").setBody("""{"user":{"id":"b","displayName":"Bo"}}"""))
        api.remember(api.currentUser()!!)
        api.clearSession()
        assertEquals(listOf("Ann"), api.people.map { it.name })
        assertTrue(!store.text!!.contains("isputnik_session\",\"value\":\"second"))
    }

    @Test fun `a TV linked before people existed still has its session, until it's remembered`() = runBlocking {
        store.text = """{"server":"${server.url("/").toString().trimEnd('/')}","cookies":[{"name":"isputnik_session","value":"old","expiresAt":4102444800000,"domain":"${server.hostName}","path":"/","secure":false,"httpOnly":true,"hostOnly":true}]}"""
        val api = ApiClient(store, "iSputnikTV/test")
        assertTrue(api.people.isEmpty())
        server.enqueue(MockResponse().setBody("""{"user":{"id":"a","displayName":"Ann"}}"""))
        val user = api.currentUser()!!
        assertTrue(server.takeRequest().getHeader("Cookie")!!.contains("isputnik_session=old"))
        api.remember(user)
        assertEquals(listOf("Ann"), api.people.map { it.name })
    }

    @Test fun `no session is nobody, not an error, and doesn't report a sign-out`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Not authenticated"}"""))
        assertNull(client().currentUser())
    }

    @Test fun `a refused session elsewhere reports the sign-out`() {
        runBlocking<Unit> {
            val api = client()
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Not authenticated"}"""))
            val waiting = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(3000) { api.signedOut.first() }
            }
            runCatching { api.send("GET", "/video/api/movies") }
            waiting.await()
        }
    }

    @Test fun `the server's reason comes through`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"home only","reason":"local"}"""))
        val failure = runCatching { client().send("GET", "/x") }.exceptionOrNull() as ApiException
        assertEquals(403, failure.code)
        assertEquals("local", failure.reason)
    }
}

class ServerAddressTest {
    @Test fun `home addresses default to http, names to https`() {
        assertEquals("http://192.168.2.137:4000", ServerAddress.normalize(" 192.168.2.137:4000/ "))
        assertEquals("https://hub.example.com", ServerAddress.normalize("hub.example.com"))
        assertEquals("http://172.16.0.5", ServerAddress.normalize("172.16.0.5"))
        assertEquals("https://172.32.0.5", ServerAddress.normalize("172.32.0.5"))
    }

    @Test fun `a typed scheme wins, and paths are dropped`() {
        assertEquals("https://192.168.1.2", ServerAddress.normalize("https://192.168.1.2/control?x=1"))
        assertEquals("http://hub.example.com:8080", ServerAddress.normalize("http://hub.example.com:8080/"))
    }

    @Test fun `what isn't an address is refused`() {
        assertNull(ServerAddress.normalize(""))
        assertNull(ServerAddress.normalize("two words"))
        assertNull(ServerAddress.normalize("ftp://x.example"))
    }
}
