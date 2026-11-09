package net.isputnik.tv.photos

import kotlinx.serialization.Serializable
import net.isputnik.tv.core.ApiClient

// What home answers about photos on the TV (home's docs/tv-photos-plan.md). Only what the app
// uses: anything else in an answer is ignored, so a newer home can't break an older TV.

/** A slideshow or album an admin put on the TV, as the person and profile watching see it:
 *  `itemCount` is the photos they may see, not the whole of it. */
@Serializable
data class TvPhotoItem(
    val pickId: String,
    val kind: String,
    val id: String,
    val name: String,
    val coverUrl: String? = null,
    val itemCount: Int = 0,
    val slideSeconds: Double? = null,
    val transition: String? = null,
    val musicTrackId: String? = null,
    val movie: Boolean = false
) {
    val isSlideshow: Boolean get() = kind == "slideshow"
}

@Serializable data class TvPhotoList(val items: List<TvPhotoItem> = emptyList())

/** One photo (or video) of an album or slideshow. `previewUrl` is home's 1600 px copy, enough
 *  for a 1080p screen; `dwellSeconds` is a slideshow's own time for this slide, when set. */
@Serializable
data class PhotoAsset(
    val id: String,
    val kind: String = "photo",
    val title: String? = null,
    val takenAt: String? = null,
    val takenPrecision: String? = null,
    val placeText: String? = null,
    val previewUrl: String? = null,
    val coverUrl: String? = null,
    val dwellSeconds: Double? = null
) {
    /** What the viewer draws: the large copy, else the small one. */
    val picture: String? get() = previewUrl ?: coverUrl
}

@Serializable data class AssetPage(val assets: List<PhotoAsset> = emptyList(), val total: Int = 0)

class PhotosApi(private val api: ApiClient) {
    fun absolute(path: String): String = if (path.startsWith("http")) path else api.absolute(path)

    /** The TV's list for whoever is watching; empty when there's nothing, or no Photos at all. */
    suspend fun list(): List<TvPhotoItem> = api.get<TvPhotoList>("$BASE/tv").items

    /** One page of what `item` holds, as this viewer may see it. */
    suspend fun page(item: TvPhotoItem, offset: Int): AssetPage {
        val (path, size) = if (item.isSlideshow) "slideshows" to SLIDESHOW_PAGE else "albums" to ALBUM_PAGE
        return api.get("$BASE/$path/${item.id}?limit=$size&offset=$offset")
    }

    companion object {
        private const val BASE = "/api/library/gallery"
        /** Home's largest pages for each. */
        const val ALBUM_PAGE = 200
        const val SLIDESHOW_PAGE = 500

        /** Whether this person gets the Photos section: a list with something on it. False
         *  when the server can't say, as with Games. */
        suspend fun available(api: ApiClient): Boolean = runCatching { PhotosApi(api).list().isNotEmpty() }.getOrDefault(false)
    }
}
