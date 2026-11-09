package net.isputnik.tv.video

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What the banner is about, for its small heading. */
enum class BannerKind { CONTINUE, FILM, SERIES }

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
    val moreRoute: VideoRoute,
    /** The title's poster, beside the words. */
    val poster: String? = null,
    val kind: BannerKind = BannerKind.FILM
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
        moreRoute = VideoRoute.ShowPage(show.id),
        poster = show.poster,
        kind = BannerKind.CONTINUE
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
        moreRoute = VideoRoute.MoviePage(movie.id),
        poster = movie.poster,
        kind = BannerKind.CONTINUE
    )
    else -> null
}

fun bannerFor(show: Show): BannerModel = BannerModel(
    id = show.id, title = show.title, line = show.plot, backdrop = show.backdrop,
    season = show.nextUp?.season, episode = show.nextUp?.episode,
    playId = show.nextUp?.id, moreRoute = VideoRoute.ShowPage(show.id),
    poster = show.poster, kind = BannerKind.SERIES
)

fun bannerFor(movie: Movie): BannerModel = BannerModel(
    id = movie.id, title = movie.title, line = movie.plot, backdrop = movie.backdrop,
    playId = movie.id, moreRoute = VideoRoute.MoviePage(movie.id),
    poster = movie.poster, kind = BannerKind.FILM
)

/** A row's name: its own, else one made from what it asks for, as home's web page names it. */
sealed interface RowName {
    data class Given(val text: String) : RowName
    data class Res(val id: Int) : RowName
    /** Genres or libraries, and (a row of one kind) which kind. */
    data class Joined(val what: String, val kind: String) : RowName
}

/** One row of a Movies or Shows tab, in the order the person's video page has them. */
sealed interface HomeEntry {
    val key: String
    val name: RowName
    data class Resume(override val key: String, override val name: RowName) : HomeEntry
    data class Titles(override val key: String, override val name: RowName, val items: List<ListedTitle>) : HomeEntry
    data class Sets(override val key: String, override val name: RowName, val sets: List<SetSummary>) : HomeEntry
}

/** The video page as it was before rows could be chosen, for a home server that has none. */
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

/**
 * A row of the video page as the Movies or Shows tab draws it, or null when it has no place
 * there: the website's All · Movies · Shows switch, on the TV. A row of both kinds asks for
 * this tab's kind; a row of the other kind isn't drawn; sets are films. Continue watching,
 * My list and a Collection row keep their place and show only this tab's kind (rowItems).
 */
fun rowFor(row: HomeRow, kind: Kind): HomeRow? = when (row.type) {
    "titles" -> when (row.kind) {
        "all" -> row.copy(kind = kind.path)
        kind.path -> row
        else -> null
    }
    "sets" -> if (kind == Kind.MOVIES) row else null
    else -> row
}

/** The name a narrowed row wears: a row of both kinds named after its genres or libraries
 *  keeps that name (the tab says which kind), one named after what it asks for says it. */
fun nameFor(row: HomeRow, narrowed: HomeRow, libraryNames: Map<String, String>, collectionName: String? = null): RowName =
    rowName(if (row.genres.isEmpty() && row.libraries.isEmpty()) narrowed else row.copy(kind = "all"), libraryNames, collectionName)

/** Titles of this tab's kind only. */
@JvmName("listedOfKind")
fun List<ListedTitle>.ofKind(kind: Kind) = filter { it.kind == kind.path }
@JvmName("continueOfKind")
fun List<ContinueItem>.ofKind(kind: Kind) = filter { if (kind == Kind.SHOWS) it.show != null else it.show == null }

/** A Movies or Shows tab: its banner, its rows, then every title of the kind. */
data class KindData(val banner: BannerModel?, val resume: List<ContinueItem>, val rows: List<HomeEntry>, val all: List<Summary>) {
    /** Whether the rows have anything to draw (the list below them is separate). */
    val rowsEmpty: Boolean
        get() = resume.isEmpty() && rows.all { (it as? HomeEntry.Titles)?.items?.isEmpty() ?: (it as? HomeEntry.Sets)?.sets?.isEmpty() ?: true }
}

/** Keeps what was loaded, so coming back shows the same rows at once (focus can return to the
 *  card it left) while the server is asked again behind them. */
class VideoStore(private val video: VideoApi) {
    var available by mutableStateOf<Boolean?>(null)
        private set
    private val tabs = mutableStateMapOf<Pair<Kind, Sort>, KindData>()

    fun tab(kind: Kind, sort: Sort): KindData? = tabs[kind to sort]

    /** The newest, or nothing: a banner that won't load must not cost the page its rows. */
    private suspend fun resumeBanner(item: ContinueItem): BannerModel? = runCatching {
        if (item.show != null) bannerFor(item, video.show(item.show.id), null) else bannerFor(item, null, video.movie(item.id))
    }.getOrNull()

    private suspend fun titleBanner(kind: Kind, id: String?): BannerModel? = runCatching {
        when {
            id == null -> null
            kind == Kind.SHOWS -> bannerFor(video.show(id))
            else -> bannerFor(video.movie(id))
        }
    }.getOrNull()

    /** A row's titles; a row that can't be read is an empty one, never the page's end. */
    private suspend fun titlesOf(row: HomeRow, chosen: Boolean, kind: Kind): List<ListedTitle> = runCatching {
        if (chosen) return@runCatching video.titles(row)
        // A home with no rows of its own has an engine from before /titles too.
        video.list(kind, Sort.ADDED).take(VideoApi.ROW_SIZE).map { ListedTitle(kind.path, it.id, it.title, it.year, it.poster) }
    }.getOrDefault(emptyList())

    suspend fun refresh(kind: Kind, sort: Sort) {
        val enabled = video.status().enabled
        available = enabled
        if (!enabled) return
        val resume = video.continueWatching().ofKind(kind)
        val layout = video.homeLayout()
        val rows = (layout?.rows ?: DefaultRows).filter { !it.hidden }
        val libraryNames = if (rows.any { it.libraries.isNotEmpty() }) {
            runCatching { video.libraries() }.getOrDefault(emptyList()).associate { it.id to it.name }
        } else emptyMap()
        val entries = rows.mapNotNull { row ->
            val narrowed = rowFor(row, kind) ?: return@mapNotNull null
            val name = nameFor(row, narrowed, libraryNames)
            when (row.type) {
                "continue" -> HomeEntry.Resume(row.id, name)
                "titles" -> HomeEntry.Titles(row.id, name, titlesOf(narrowed, layout != null, kind))
                "sets" -> HomeEntry.Sets(row.id, name, runCatching { video.sets() }.getOrDefault(emptyList()))
                "mylist" -> HomeEntry.Titles(row.id, name, runCatching { video.myList() }.getOrDefault(emptyList()).ofKind(kind))
                "collection" -> row.collectionId?.let { id ->
                    val list = runCatching { video.collectionRow(id) }.getOrDefault(TitleList())
                    HomeEntry.Titles(row.id, nameFor(row, narrowed, libraryNames, list.name), list.items.ofKind(kind))
                }
                else -> null
            }
        }
        val all = video.list(kind, sort)
        // What's in progress, else the first title the rows show, else the newest.
        val first = entries.firstNotNullOfOrNull { (it as? HomeEntry.Titles)?.items?.firstOrNull() }?.id
        val banner = resume.firstOrNull()?.let { resumeBanner(it) }
            ?: titleBanner(kind, first ?: (if (sort == Sort.ADDED) all.firstOrNull() else video.list(kind, Sort.ADDED).firstOrNull())?.id)
        tabs[kind to sort] = KindData(banner, resume, entries, all)
    }
}
