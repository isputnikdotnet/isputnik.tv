package net.isputnik.tv.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A refused request; `code` is the HTTP status, `reason` the server's machine-readable one. */
class ApiException(val code: Int, message: String, val reason: String? = null) : IOException(message)

@Serializable private data class ErrorBody(val error: String? = null, val reason: String? = null)

@Serializable
private data class StoredCookie(
    val name: String, val value: String, val expiresAt: Long, val domain: String, val path: String,
    val secure: Boolean, val httpOnly: Boolean, val hostOnly: Boolean
)

@Serializable private data class StoredPerson(val id: String, val name: String, val cookies: List<StoredCookie> = emptyList())

@Serializable
private data class Stored(
    val server: String? = null,
    /** The cookies in use. Before anyone is linked these are a person not yet known, and a TV
     *  linked by T1 or T2 has its one person here until they're next seen. */
    val cookies: List<StoredCookie> = emptyList(),
    val people: List<StoredPerson> = emptyList(),
    val activeId: String? = null
)

/** Someone linked on this TV, as the picker shows them. */
data class Person(val id: String, val name: String)

/**
 * The one way this app talks to home. It holds what a browser would: the session and CSRF
 * cookies, kept across runs, for each person linked on this TV. That's the whole sign-in story:
 * each person has their own device session (home's core/device-link.ts), so every route answers
 * as it does on the website, with their permissions, and nothing here decides who may see what.
 */
class ApiClient(private val store: TextStore, userAgent: String) {
    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** For work that must outlive a screen: the last progress save of a film just left. */
    val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val lock = Any()
    private var cookies: List<Cookie>
    private var saved: List<StoredPerson>
    private var activeId: String?
    var server: String?
        private set

    private val _signedOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** The server stopped accepting this person's session: revoked in Profile, or expired. */
    val signedOut: SharedFlow<Unit> = _signedOut

    init {
        val state = store.read()?.let { runCatching { json.decodeFromString<Stored>(it) }.getOrNull() } ?: Stored()
        server = state.server
        saved = state.people
        activeId = state.activeId
        cookies = state.cookies.map(::toCookie)
    }

    private fun toCookie(c: StoredCookie): Cookie =
        Cookie.Builder().name(c.name).value(c.value).expiresAt(c.expiresAt).path(c.path).apply {
            if (c.hostOnly) hostOnlyDomain(c.domain) else domain(c.domain)
            if (c.secure) secure()
            if (c.httpOnly) httpOnly()
        }.build()

    private fun fromCookie(it: Cookie) = StoredCookie(it.name, it.value, it.expiresAt, it.domain, it.path, it.secure, it.httpOnly, it.hostOnly)

    private fun persist() {
        val current = cookies.map(::fromCookie)
        // What's in use is also what comes back when this person is chosen again.
        saved = saved.map { if (it.id == activeId) it.copy(cookies = current) else it }
        store.write(json.encodeToString(Stored.serializer(), Stored(server, current, saved, activeId)))
    }

    private val jar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
            val now = System.currentTimeMillis()
            cookies.filter { it.expiresAt > now && it.matches(url) }
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = synchronized(lock) {
            val incoming = cookies.map { Triple(it.name, it.domain, it.path) }.toSet()
            val now = System.currentTimeMillis()
            this@ApiClient.cookies = this@ApiClient.cookies.filterNot { Triple(it.name, it.domain, it.path) in incoming } +
                cookies.filter { it.expiresAt > now }
            persist()
        }
    }

    /** Shared by the API, the player and the image loader, so all three carry the session. */
    val http: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(jar)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
        }
        .build()

    /** Changing server drops everyone: their sessions belonged to the old one. */
    fun useServer(address: String?) = synchronized(lock) {
        if (address != server) {
            cookies = emptyList()
            saved = emptyList()
            activeId = null
        }
        server = address
        persist()
    }

    val people: List<Person> get() = synchronized(lock) { saved.map { Person(it.id, it.name) } }
    val activePerson: String? get() = synchronized(lock) { activeId }

    /** The session in use is this person's: keeps them on this TV, by name for the picker. */
    fun remember(user: User) = synchronized(lock) {
        activeId = user.id
        val entry = StoredPerson(user.id, user.displayName, cookies.map(::fromCookie))
        saved = if (saved.any { it.id == user.id }) saved.map { if (it.id == user.id) entry else it } else saved + entry
        persist()
    }

    /** Moves the requests that follow to this person's session. */
    fun switchTo(id: String) = synchronized(lock) {
        val person = saved.firstOrNull { it.id == id } ?: return@synchronized
        activeId = id
        cookies = person.cookies.map(::toCookie)
        persist()
    }

    /** A clean slate for linking someone else; everyone already here stays. */
    fun startLinking() = synchronized(lock) {
        activeId = null
        cookies = emptyList()
        persist()
    }

    /** Forget the person in use, keeping everyone else and the address. */
    fun clearSession() = synchronized(lock) {
        saved = saved.filterNot { it.id == activeId }
        activeId = null
        cookies = emptyList()
        persist()
    }

    fun absolute(path: String): String = (server ?: "") + path

    suspend fun send(method: String, path: String, body: String? = null): String = withContext(Dispatchers.IO) {
        val url = absolute(path)
        val builder = Request.Builder().url(url)
        if (method != "GET") {
            // Home's double-submit check: the token cookie comes with any first GET, and is
            // echoed back like the web app does. No exemption on the server is needed.
            if (csrfToken(url) == null) http.newCall(Request.Builder().url(absolute("/api/auth/me")).build()).execute().close()
            builder.header("X-CSRF-Token", csrfToken(url) ?: "")
            // As a browser's request would say. Home builds the phone's link from this
            // (requestOrigin): without it the QR names APP_URL, not the address this TV reached.
            builder.header("Origin", server ?: "")
            builder.method(method, (body ?: "{}").toRequestBody(JSON_TYPE))
        }
        http.newCall(builder.build()).execute().use { read(it, path) }
    }

    private fun csrfToken(url: String): String? {
        val parsed = Request.Builder().url(url).build().url
        return jar.loadForRequest(parsed).firstOrNull { it.name.endsWith("isputnik_csrf") }?.value
    }

    private fun read(response: Response, path: String): String {
        val text = response.body?.string().orEmpty()
        if (response.isSuccessful) return text
        val parsed = runCatching { json.decodeFromString<ErrorBody>(text) }.getOrNull()
        // Asking whether anyone is signed in isn't being signed out; everything else is.
        if (response.code == 401 && path != ME) _signedOut.tryEmit(Unit)
        throw ApiException(response.code, parsed?.error ?: "HTTP ${response.code}", parsed?.reason)
    }

    suspend inline fun <reified T> get(path: String): T = json.decodeFromString(send("GET", path))

    suspend inline fun <reified T, reified B> post(path: String, body: B): T =
        json.decodeFromString(send("POST", path, json.encodeToString(kotlinx.serialization.serializer<B>(), body)))

    companion object {
        const val ME = "/api/auth/me"
        private val JSON_TYPE = "application/json".toMediaType()
    }
}
