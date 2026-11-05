package net.isputnik.tv.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PosterArtTest {
    @Test
    fun aTallCardGetsThePosterAndAWideOneTheFrame() {
        assertEquals(R.drawable.cover_movie, PosterArt.MOVIE.drawable(wide = false))
        assertEquals(R.drawable.cover_movie_wide, PosterArt.MOVIE.drawable(wide = true))
        assertEquals(R.drawable.cover_show, PosterArt.SHOW.drawable(wide = false))
        assertEquals(R.drawable.cover_show_wide, PosterArt.SHOW.drawable(wide = true))
    }

    @Test
    fun homesKindNamesPickTheArt() {
        assertEquals(PosterArt.SHOW, PosterArt.of("shows"))
        assertEquals(PosterArt.MOVIE, PosterArt.of("movies"))
        assertEquals(PosterArt.MOVIE, PosterArt.of(null))
    }
}
