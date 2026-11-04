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

/** A row's name: its own, else one made from what it asks for, as home's web page names it. */
sealed interface RowName {
    data class Given(val text: String) : RowName
    data class Res(val id: Int) : RowName
    /** Genres or libraries, and (a row of one kind) which kind. */
    data class Joined(val what: String, val kind: String) : RowName
}

/** One row of Home, in the order the person's video page has them. */
sealed interface HomeEntry {
    val key: String
    val name: RowName
    data class Resume(override val key: String, override val name: RowName) : HomeEntry
    data class Titles(override val key: String, override val name: RowName, val items: List<ListedTitle>) : HomeEntry
    data class Sets(override val key: String, override val name: RowName, val sets: List<SetSummary>) : HomeEntry
}

/** Home as it was before rows could be chosen, for a home server that has none. */
val DefaultRows = listOf(
    HomeRow("continue", "continue"),
    HomeRow("new-movies", "titles", kind = "movies"),
    HomeRow("new-shows", "titles", kind = "shows")
)

fun rowName(row: HomeRow, libraryNames: Map<String, String>, collectionName: String? = null): RowName = when {
    row.title != null -> RowName.Given(row.title)
    row.type == "continue" -> RowName.Res(R.string.video_continue)
    row.type == "sets" -> RowName.Res(R.string.video_sets)
    row.type == "mylist" -> RowName.Res(R.string.video_my_list)
    row.type == "collection" -> collectionName?.let { RowName.Given(it) } ?: RowName.Res(R.string.video_collection)
    row.genres.isNotEmpty() -> RowName.Joined(row.genres.joinToString(", "), row.kind)
    row.libraries.isNotEmpty() -> RowName.Joined(row.libraries.joinToString(", ") { libraryNames[it] ?: it }, row.kind)
    row.sort == "added" -> RowName.Res(
        when (row.kind) { "movies" -> R.string.video_recent_movies; "shows" -> R.string.video_recent_shows; else -> R.string.video_recent_all }
    )
    else -> RowName.Res(when (row.kind) { "movies" -> R.string.video_movies; "shows" -> R.string.video_shows; else -> R.string.video_movies_and_shows })
}

data class HomeData(val resume: List<ContinueItem>, val rows: List<HomeEntry>, val banner: BannerModel?) {
    val isEmpty: Boolean
        get() = resume.isEmpty() && rows.all { (it as? HomeEntry.Titles)?.items?.isEmpty() ?: (it as? HomeEntry.Sets)?.sets?.isEmpty() ?: true }
}
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

    /** A row's titles; a row that can't be read is an empty one, never the page's end. */
    private suspend fun titlesOf(row: HomeRow, chosen: Boolean): List<ListedTitle> = runCatching {
        if (chosen) return@runCatching video.titles(row)
        // A home with no rows of its own has an engine from before /titles too.
        val kind = if (row.kind == "shows") Kind.SHOWS else Kind.MOVIES
        video.list(kind, Sort.ADDED).take(VideoApi.ROW_SIZE).map { ListedTitle(kind.path, it.id, it.title, it.year, it.poster) }
    }.getOrDefault(emptyList())

    suspend fun refreshHome() {
        val enabled = video.status().enabled
        available = enabled
        if (!enabled) return
        val resume = video.continueWatching()
        val layout = video.homeLayout()
        val rows = (layout?.rows ?: DefaultRows).filter { !it.hidden }
        val libraryNames = if (rows.any { it.libraries.isNotEmpty() }) {
            runCatching { video.libraries() }.getOrDefault(emptyList()).associate { it.id to it.name }
        } else emptyMap()
        val entries = rows.mapNotNull { row ->
            when (row.type) {
                "continue" -> HomeEntry.Resume(row.id, rowName(row, libraryNames))
                "titles" -> HomeEntry.Titles(row.id, rowName(row, libraryNames), titlesOf(row, layout != null))
                "sets" -> HomeEntry.Sets(row.id, rowName(row, libraryNames), runCatching { video.sets() }.getOrDefault(emptyList()))
                "mylist" -> HomeEntry.Titles(row.id, rowName(row, libraryNames), runCatching { video.myList() }.getOrDefault(emptyList()))
                "collection" -> row.collectionId?.let { id ->
                    val list = runCatching { video.collectionRow(id) }.getOrDefault(TitleList())
                    HomeEntry.Titles(row.id, rowName(row, libraryNames, list.name), list.items)
                }
                else -> null
            }
        }
        // What's in progress, else the first title the rows show.
        val first = entries.firstNotNullOfOrNull { (it as? HomeEntry.Titles)?.items?.firstOrNull() }
        val banner = resume.firstOrNull()?.let { resumeBanner(it) }
            ?: first?.let { newestBanner(if (it.kind == "shows") Kind.SHOWS else Kind.MOVIES, Summary(it.id, it.title, it.year, it.poster)) }
        home = HomeData(resume, entries, banner)
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
