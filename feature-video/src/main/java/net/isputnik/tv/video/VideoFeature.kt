package net.isputnik.tv.video

import androidx.compose.runtime.Composable
import net.isputnik.tv.core.Destination
import net.isputnik.tv.core.Feature
import net.isputnik.tv.core.Navigator
import net.isputnik.tv.core.Route
import net.isputnik.tv.core.R as CoreR

sealed interface VideoRoute : Route {
    data object Home : VideoRoute { override val key = "video/home" }
    data object Movies : VideoRoute { override val key = "video/movies" }
    data object Shows : VideoRoute { override val key = "video/shows" }
    data object Search : VideoRoute { override val key = "video/search" }
    data class MoviePage(val id: String) : VideoRoute { override val key = "video/movie/$id" }
    data class ShowPage(val id: String) : VideoRoute { override val key = "video/show/$id" }
    data class SetPage(val id: String) : VideoRoute { override val key = "video/set/$id" }
    data class Play(val id: String, val fromStart: Boolean = false) : VideoRoute { override val key = "video/play/$id" }
}

/** Shows and movies. Home lives here until a second section has rows to mix in (T5). */
class VideoFeature(private val video: VideoApi) : Feature {
    private val store = VideoStore(video)

    override val destinations = listOf(
        Destination(VideoRoute.Home, R.string.menu_home, CoreR.drawable.ic_home),
        Destination(VideoRoute.Movies, R.string.menu_movies, CoreR.drawable.ic_movie),
        Destination(VideoRoute.Shows, R.string.menu_shows, CoreR.drawable.ic_tv),
        Destination(VideoRoute.Search, R.string.menu_search, CoreR.drawable.ic_search)
    )

    override fun owns(route: Route) = route is VideoRoute

    @Composable
    override fun Content(route: Route, nav: Navigator) {
        when (val page = route as VideoRoute) {
            VideoRoute.Home -> HomePage(video, store, nav)
            VideoRoute.Movies -> LibraryPage(video, store, nav, Kind.MOVIES)
            VideoRoute.Shows -> LibraryPage(video, store, nav, Kind.SHOWS)
            VideoRoute.Search -> SearchPage(video, nav)
            is VideoRoute.MoviePage -> MoviePage(video, nav, page.id)
            is VideoRoute.ShowPage -> ShowPage(video, nav, page.id)
            is VideoRoute.SetPage -> SetPage(video, nav, page.id)
            is VideoRoute.Play -> PlayerScreen(video, page.id, page.fromStart) { nav.back() }
        }
    }
}
