package net.isputnik.tv.core

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The address of a home server, as typed on a remote: forgiving about what's left out. */
object ServerAddress {
    private val PRIVATE_HOST = Regex("""^(10\.|127\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.|localhost$)""")

    /** Just scheme, host and port; null for what isn't an address. With no scheme, an
     *  address on a home network is plain http and any other name https, since that's what
     *  each of them almost always is. */
    fun normalize(typed: String): String? {
        val text = typed.trim().trimEnd('/')
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val withScheme = when {
            text.contains("://") -> text
            PRIVATE_HOST.containsMatchIn(text) -> "http://$text"
            else -> "https://$text"
        }
        val url = withScheme.toHttpUrlOrNull() ?: return null
        val default = (url.scheme == "https" && url.port == 443) || (url.scheme == "http" && url.port == 80)
        return "${url.scheme}://${url.host}" + if (default) "" else ":${url.port}"
    }

    fun isPlainHttp(address: String): Boolean = address.startsWith("http://")
}
