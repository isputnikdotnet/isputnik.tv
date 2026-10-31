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

@Serializable private data class Stored(val server: String? = null, val cookies: List<StoredCookie> = emptyList())

/**
 * The one way this app talks to home. It holds what a browser would: the session and CSRF
 * cookies, kept across runs. That's the whole sign-in story: a linked TV has a device
 * session (home's core/device-link.ts), so every route answers as it does on the website,
 * with its permissions, and nothing here decides who may see what.
 */
class ApiClient(private val store: TextStore, userAgent: String) {
    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** For work that must outlive a screen: the last progress save of a film just left. */
    val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val lock = Any()
    private var cookies: List<Cookie>
    var server: String?
        private set

    private val _signedOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** The server stopped accepting this TV's session: revoked in Profile, or expired. */
    val signedOut: SharedFlow<Unit> = _signedOut

    init {
        val saved = store.read()?.let { runCatching { json.decodeFromString<Stored>(it) }.getOrNull() } ?: Stored()
        server = saved.server
        cookies = saved.cookies.map { c ->
            Cookie.Builder().name(c.name).value(c.value).expiresAt(c.expiresAt).path(c.path).apply {
                if (c.hostOnly) hostOnlyDomain(c.domain) else domain(c.domain)
                if (c.secure) secure()
                if (c.httpOnly) httpOnly()
            }.build()
        }
    }

    private fun persist() {
        store.write(json.encodeToString(Stored.serializer(), Stored(server, cookies.map {
            StoredCookie(it.name, it.value, it.expiresAt, it.domain, it.path, it.secure, it.httpOnly, it.hostOnly)
        })))
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

    /** Changing server drops the session: it belonged to the old one. */
    fun useServer(address: String?) = synchronized(lock) {
        if (address != server) cookies = emptyList()
        server = address
        persist()
    }

    /** Forget this TV's session; the address stays for linking again. */
    fun clearSession() = synchronized(lock) {
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
        val parsed = okhttp3.HttpUrl.Builder().let { Request.Builder().url(url).build().url }
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
