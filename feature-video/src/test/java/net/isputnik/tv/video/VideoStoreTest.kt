package net.isputnik.tv.video

import net.isputnik.tv.core.FocusMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoStoreTest {
    private val show = Show(id = "s", title = "The Expanse", plot = "Out there.", backdrop = "/b", nextUp = NextUp("e6", 3, 6))
    private val episode = ContinueItem(id = "e5", type = "episode", title = "Ep", show = ShowRef("s", "The Expanse"), season = 3, episode = 5, position = 600.0, duration = 3120.0)

    @Test fun `an episode in progress continues, and Episodes goes to the show`() {
        val banner = bannerFor(episode, show, null)!!
        assertTrue(banner.inProgress)
        assertEquals("e5", banner.playId)
        assertEquals(VideoRoute.ShowPage("s"), banner.moreRoute)
        assertEquals(3, banner.season)
        assertEquals(42, banner.minutesLeft)
        assertEquals(600f / 3120f, banner.fraction!!, 0.001f)
    }

    @Test fun `the episode after a finished one starts, with no bar to fill`() {
        val banner = bannerFor(episode.copy(resume = false, position = 0.0), show, null)!!
        assertFalse(banner.inProgress)
        assertNull(banner.fraction)
        assertNull(banner.minutesLeft)
    }

    @Test fun `a new show plays what's next, and a finished one offers only its episodes`() {
        assertEquals("e6", bannerFor(show).playId)
        assertNull(bannerFor(show.copy(nextUp = null)).playId)
    }

    @Test fun `a film's banner leads to its own page`() {
        val movie = Movie(id = "m", title = "Film")
        assertEquals(VideoRoute.MoviePage("m"), bannerFor(movie).moreRoute)
    }

    @Test fun `time left reads in whole minutes, never as none while any is left`() {
        assertEquals(42, minutesLeft(600.0, 3120.0))
        assertEquals(1, minutesLeft(3119.0, 3120.0))
        assertNull(minutesLeft(3120.0, 3120.0))
        assertNull(minutesLeft(10.0, null))
    }

    @Test fun `a watched title is a full bar, an unstarted one has none`() {
        assertEquals(1f, Progress(watched = true).fraction())
        assertNull(Progress().fraction())
        assertNull((null as Progress?).fraction())
    }

    @Test fun `a poster says nothing for a title never started, or from an engine that says nothing`() {
        assertEquals(WatchMark(), Summary(id = "m", title = "Film").watchMark())
        assertEquals(WatchMark(), Summary(id = "s", title = "Show", episodes = 10, watchedEpisodes = 0).watchMark())
        // A peek is not a place to come back to.
        assertEquals(WatchMark(), Summary(id = "m", title = "Film", progress = Progress(12.0, 6000.0)).watchMark())
    }

    @Test fun `a film left part-way has a bar, a finished one a check and no bar`() {
        assertEquals(0.25f, Summary(id = "m", title = "Film", progress = Progress(1500.0, 6000.0)).watchMark().fraction!!, 0.001f)
        assertEquals(WatchMark(watched = true), Summary(id = "m", title = "Film", progress = Progress(6000.0, 6000.0, watched = true)).watchMark())
    }

    @Test fun `a show part seen counts what is left, and a new episode brings a finished one back`() {
        val show = ListedTitle(kind = "shows", id = "s", title = "Show", episodes = 10, watchedEpisodes = 2, started = true)
        assertEquals(WatchMark(fraction = 0.2f, unwatched = 8), show.watchMark())
        assertEquals(WatchMark(watched = true), show.copy(watchedEpisodes = 10).watchMark())
        assertEquals(WatchMark(fraction = 10f / 11f, unwatched = 1), show.copy(episodes = 11, watchedEpisodes = 10).watchMark())
        // One episode begun and none finished: started, with every episode still to see.
        assertEquals(WatchMark(unwatched = 10), show.copy(watchedEpisodes = 0).watchMark())
    }

    @Test fun `focus returns to the card left, if it's still there`() {
        val present = setOf("a", "b")
        assertEquals("b", FocusMemory.choose("b", "a", present))
        assertEquals("a", FocusMemory.choose("gone", "a", present))
        assertEquals("a", FocusMemory.choose(null, "a", present))
        assertNull(FocusMemory.choose("gone", "also-gone", present))
    }

    @Test fun `a row is named from what it asks for, as the web page names it`() {
        val libraries = mapOf("lib_kids" to "Kids")
        assertEquals(RowName.Given("Cartoons"), rowName(HomeRow("r", "titles", title = "Cartoons"), libraries))
        assertEquals(RowName.Res(R.string.video_recent_movies), rowName(HomeRow("r", "titles", kind = "movies"), libraries))
        assertEquals(RowName.Res(R.string.video_recent_all), rowName(HomeRow("r", "titles"), libraries))
        assertEquals(RowName.Joined("Comedy, Drama", "shows"), rowName(HomeRow("r", "titles", kind = "shows", genres = listOf("Comedy", "Drama")), libraries))
        assertEquals(RowName.Joined("Kids", "all"), rowName(HomeRow("r", "titles", libraries = listOf("lib_kids")), libraries))
        assertEquals(RowName.Res(R.string.video_movies), rowName(HomeRow("r", "titles", kind = "movies", sort = "title"), libraries))
        assertEquals(RowName.Given("Family night"), rowName(HomeRow("r", "collection", collectionId = "c"), libraries, "Family night"))
        assertEquals(RowName.Res(R.string.video_my_list), rowName(HomeRow("r", "mylist"), libraries))
    }

    @Test fun `a tab's rows are empty only when no row has anything in it`() {
        val film = ListedTitle("movies", "m", "Film")
        assertTrue(KindData(null, emptyList(), listOf(HomeEntry.Resume("c", RowName.Res(0)), HomeEntry.Titles("t", RowName.Res(0), emptyList())), emptyList()).rowsEmpty)
        assertFalse(KindData(null, emptyList(), listOf(HomeEntry.Titles("t", RowName.Res(0), listOf(film))), emptyList()).rowsEmpty)
        assertFalse(KindData(null, listOf(episode), emptyList(), emptyList()).rowsEmpty)
    }

    @Test fun `Movies and Shows each draw the rows of their kind, as the website's switch does`() {
        val both = HomeRow("comedy", "titles", kind = "all", genres = listOf("Comedy"))
        val films = HomeRow("new-movies", "titles", kind = "movies")
        val series = HomeRow("new-shows", "titles", kind = "shows")
        val sets = HomeRow("sets", "sets")
        val resume = HomeRow("continue", "continue")
        val mine = HomeRow("mine", "mylist")
        assertEquals(both.copy(kind = "movies"), rowFor(both, Kind.MOVIES))
        assertEquals(both.copy(kind = "shows"), rowFor(both, Kind.SHOWS))
        assertEquals(films, rowFor(films, Kind.MOVIES))
        assertNull(rowFor(films, Kind.SHOWS))
        assertNull(rowFor(series, Kind.MOVIES))
        assertEquals(sets, rowFor(sets, Kind.MOVIES))
        assertNull(rowFor(sets, Kind.SHOWS))
        assertEquals(resume, rowFor(resume, Kind.SHOWS))
        assertEquals(mine, rowFor(mine, Kind.MOVIES))
    }

    @Test fun `a narrowed row keeps its own name, and a plain one says its kind`() {
        val comedy = HomeRow("comedy", "titles", kind = "all", genres = listOf("Comedy"))
        assertEquals(RowName.Joined("Comedy", "all"), nameFor(comedy, rowFor(comedy, Kind.MOVIES)!!, emptyMap()))
        val recent = HomeRow("recent", "titles", kind = "all")
        assertEquals(RowName.Res(R.string.video_recent_shows), nameFor(recent, rowFor(recent, Kind.SHOWS)!!, emptyMap()))
    }

    @Test fun `only the tab's kind of what's in progress or listed shows`() {
        val film = ContinueItem(id = "m", type = "movie", title = "Film", position = 60.0, duration = 600.0)
        assertEquals(listOf(episode), listOf(episode, film).ofKind(Kind.SHOWS))
        assertEquals(listOf(film), listOf(episode, film).ofKind(Kind.MOVIES))
        val listed = listOf(ListedTitle("movies", "m", "Film"), ListedTitle("shows", "s", "Show"))
        assertEquals(listOf("s"), listed.ofKind(Kind.SHOWS).map { it.id })
    }
}
