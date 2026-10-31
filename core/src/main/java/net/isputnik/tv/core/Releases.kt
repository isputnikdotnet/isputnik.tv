package net.isputnik.tv.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

@Serializable private data class ReleaseAsset(val name: String, @SerialName("browser_download_url") val url: String)
@Serializable private data class Release(@SerialName("tag_name") val tag: String, val assets: List<ReleaseAsset> = emptyList())

/** A newer version of this app, and where its APK is. */
data class AvailableUpdate(val version: String, val url: String)

/** Where a new version comes from. TVs don't update sideloaded apps, so the app has to ask. */
object Releases {
    private val json = Json { ignoreUnknownKeys = true }

    /** False for anything that isn't a plain version, so an odd tag never offers an update. */
    fun isNewer(latest: String, current: String): Boolean {
        val a = parts(latest) ?: return false
        val b = parts(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val difference = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (difference != 0) return difference > 0
        }
        return false
    }

    private fun parts(version: String): List<Int>? =
        version.removePrefix("v").split(".").map { it.toIntOrNull() ?: return null }

    /** The update on offer, or null when there's none. Throws when the answer can't be had. */
    suspend fun check(http: OkHttpClient, url: String, current: String): AvailableUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()
        val release = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            json.decodeFromString<Release>(response.body?.string().orEmpty())
        }
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@withContext null
        if (isNewer(release.tag, current)) AvailableUpdate(release.tag.removePrefix("v"), apk.url) else null
    }

    /** Fetches to `target` whole or not at all; `progress` is told the percent, when it's known. */
    suspend fun download(http: OkHttpClient, url: String, target: File, progress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val partial = File(target.path + ".part")
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("No body")
                val total = body.contentLength()
                var done = 0L
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            if (total > 0) progress((done * 100 / total).toInt())
                        }
                    }
                }
                if (total > 0 && done != total) throw IOException("Cut short")
            }
            if (!partial.renameTo(target)) throw IOException("Unable to keep the download")
        } finally {
            partial.delete()
        }
    }
}
