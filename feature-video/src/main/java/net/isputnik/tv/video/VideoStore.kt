package net.isputnik.tv.video

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What the big banner shows. Pure data, so what it picks can be tested. */
data class BannerModel(
    val id: String,
    val title: String,
    val line: String?,
    val backdrop: String?,
    val season: Int? = null,
    val episode: Int? = null,
    val fraction: Float? = null,
    val minutesLeft: Int? = null,
    /** Continue watching rather than Play. */
    val inProgress: Boolean = false,
    /** What the main button plays; null when there's nothing to start, a finished show. */
    val playId: String? = null,
    val moreRoute: VideoRoute
)

fun bannerFor(item: ContinueItem, show: Show?, movie: Movie?): BannerModel? = when {
    show != null -> BannerModel(
        id = show.id,
        title = show.title,
        line = show.plot ?: item.title,
        backdrop = show.backdrop,
        season = item.season,
        episode = item.episode,
        fraction = if (item.resume) fractionOf(item.position, item.duration) else null,
        minutesLeft = if (item.resume) minutesLeft(item.position, item.duration) else null,
        inProgress = item.resume,
        playId = item.id,
        moreRoute = VideoRoute.ShowPage(show.id)
    )
    movie != null -> BannerModel(
        id = movie.id,
        title = movie.title,
        line = movie.plot,
        backdrop = movie.backdrop,
        fraction = fractionOf(item.position, item.duration),
        minutesLeft = minutesLeft(item.position, item.duration),
        inProgress = item.resume,
        playId = item.id,
        moreRoute = VideoRoute.MoviePage(movie.id)
    )
    else -> null
}

fun bannerFor(show: Show): BannerModel = BannerModel(
    id = show.id, title = show.title, line = show.plot, backdrop = show.backdrop,
    season = show.nextUp?.season, episode = show.nextUp?.episode,
    playId = show.nextUp?.id, moreRoute = VideoRoute.ShowPage(show.id)
)

fun bannerFor(movie: Movie): BannerModel = BannerModel(
    id = movie.id, title = movie.title, line = movie.plot, backdrop = movie.backdrop,
    playId = movie.id, moreRoute = VideoRoute.MoviePage(movie.id)
)

data class HomeData(val resume: List<ContinueItem>, val movies: List<Summary>, val shows: List<Summary>, val banner: BannerModel?)
data class LibraryData(val banner: BannerModel?, val items: List<Summary>, val sets: List<SetSummary>)

/** Keeps what was loaded, so coming back shows the same rows at once (focus can return to the
 *  card it left) while the server is asked again behind them. */
class VideoStore(private val video: VideoApi) {
    var available by mutableStateOf<Boolean?>(null)
        private set
    var home by mutableStateOf<HomeData?>(null)
        private set
    private val libraries = mutableStateMapOf<Pair<Kind, Sort>, LibraryData>()

    fun library(kind: Kind, sort: Sort): LibraryData? = libraries[kind to sort]

    /** The newest, or nothing: a banner that won't load must not cost the page its rows. */
    private suspend fun resumeBanner(item: ContinueItem): BannerModel? = runCatching {
        if (item.show != null) bannerFor(item, video.show(item.show.id), null) else bannerFor(item, null, video.movie(item.id))
    }.getOrNull()

    private suspend fun newestBanner(kind: Kind, newest: Summary?): BannerModel? = runCatching {
        when {
            newest == null -> null
            kind == Kind.SHOWS -> bannerFor(video.show(newest.id))
            else -> bannerFor(video.movie(newest.id))
        }
    }.getOrNull()

    suspend fun refreshHome() {
        val enabled = video.status().enabled
        available = enabled
        if (!enabled) return
        val resume = video.continueWatching()
        val movies = video.list(Kind.MOVIES, Sort.ADDED)
        val shows = video.list(Kind.SHOWS, Sort.ADDED)
        val banner = resume.firstOrNull()?.let { resumeBanner(it) }
            ?: newestBanner(Kind.MOVIES, movies.firstOrNull())
            ?: newestBanner(Kind.SHOWS, shows.firstOrNull())
        home = HomeData(resume, movies, shows, banner)
    }

    suspend fun refreshLibrary(kind: Kind, sort: Sort) {
        val items = video.list(kind, sort)
        val sets = if (kind == Kind.MOVIES) runCatching { video.sets() }.getOrDefault(emptyList()) else emptyList()
        // The banner is what's in progress here, else the newest, whichever way the list is sorted.
        val resume = runCatching { video.continueWatching() }.getOrDefault(emptyList())
            .firstOrNull { if (kind == Kind.SHOWS) it.show != null else it.show == null }
        val banner = resume?.let { resumeBanner(it) }
            ?: newestBanner(kind, if (sort == Sort.ADDED) items.firstOrNull() else video.list(kind, Sort.ADDED).firstOrNull())
        libraries[kind to sort] = LibraryData(banner, items, sets)
    }
}
