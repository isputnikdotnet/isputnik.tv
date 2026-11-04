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

    @Test fun `home is empty only when no row has anything in it`() {
        val film = ListedTitle("movies", "m", "Film")
        assertTrue(HomeData(emptyList(), listOf(HomeEntry.Resume("c", RowName.Res(0)), HomeEntry.Titles("t", RowName.Res(0), emptyList())), null).isEmpty)
        assertFalse(HomeData(emptyList(), listOf(HomeEntry.Titles("t", RowName.Res(0), listOf(film))), null).isEmpty)
        assertFalse(HomeData(listOf(episode), emptyList(), null).isEmpty)
    }
}
