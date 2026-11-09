package net.isputnik.tv.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import net.isputnik.tv.core.Destination
import net.isputnik.tv.core.Feature
import net.isputnik.tv.core.Navigator
import net.isputnik.tv.core.Route
import net.isputnik.tv.core.R as CoreR

sealed interface VideoRoute : Route {
    data object Movies : VideoRoute { override val key = "video/movies" }
    data object Shows : VideoRoute { override val key = "video/shows" }
    data object Search : VideoRoute { override val key = "video/search" }
    data class MoviePage(val id: String) : VideoRoute { override val key = "video/movie/$id" }
    data class ShowPage(val id: String) : VideoRoute { override val key = "video/show/$id" }
    data class SetPage(val id: String) : VideoRoute { override val key = "video/set/$id" }
    data class PersonPage(val id: String) : VideoRoute { override val key = "video/person/$id" }
    data class Play(val id: String, val fromStart: Boolean = false) : VideoRoute { override val key = "video/play/$id" }
}

/** Movies and shows. The menu has Movies and Shows (each the video page's rows of that kind, then
 *  everything); Search is reached from the top bar's icon ([searchRoute]). */
class VideoFeature(private val video: VideoApi, private val userId: String) : Feature {
    private val store = VideoStore(video)

    override val destinations = listOf(
        Destination(VideoRoute.Movies, R.string.menu_movies, CoreR.drawable.ic_movie),
        Destination(VideoRoute.Shows, R.string.menu_shows, CoreR.drawable.ic_tv)
    )

    /** Where the top bar's search icon goes. */
    val searchRoute: Route = VideoRoute.Search

    override fun owns(route: Route) = route is VideoRoute

    @Composable
    override fun Content(route: Route, nav: Navigator) {
        when (val page = route as VideoRoute) {
            VideoRoute.Movies -> KindPage(video, store, nav, Kind.MOVIES)
            VideoRoute.Shows -> KindPage(video, store, nav, Kind.SHOWS)
            VideoRoute.Search -> SearchPage(video, nav)
            is VideoRoute.MoviePage -> MoviePage(video, nav, page.id)
            is VideoRoute.ShowPage -> ShowPage(video, nav, page.id)
            is VideoRoute.SetPage -> SetPage(video, nav, page.id)
            is VideoRoute.PersonPage -> PersonPage(video, nav, page.id)
            is VideoRoute.Play -> {
                val context = LocalContext.current
                val prefs = remember(userId) { PlayerPrefs(context, userId) }
                PlayerScreen(video, page.id, page.fromStart, prefs, onNext = { nav.replace(VideoRoute.Play(it)) }, onExit = { nav.back() })
            }
        }
    }
}
