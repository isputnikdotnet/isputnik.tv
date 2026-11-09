package net.isputnik.tv.photos

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ShowTest {
    private val album = TvPhotoItem(pickId = "p1", kind = "album", id = "a1", name = "Grandma's 80th", itemCount = 3)
    private val slideshow = TvPhotoItem(pickId = "p2", kind = "slideshow", id = "s1", name = "Summer", itemCount = 3, slideSeconds = 4.0, transition = "slide")

    @Test fun albumsUseTheTvInterval() {
        assertEquals(ALBUM_SECONDS, secondsFor(album, PhotoAsset("x", dwellSeconds = 12.0)), 0.0)
    }

    @Test fun slideshowsKeepTheirOwnTimeAndASlidesOwnComesFirst() {
        assertEquals(4.0, secondsFor(slideshow, PhotoAsset("x")), 0.0)
        assertEquals(9.0, secondsFor(slideshow, PhotoAsset("x", dwellSeconds = 9.0)), 0.0)
        assertEquals(ALBUM_SECONDS, secondsFor(slideshow.copy(slideSeconds = null), null), 0.0)
    }

    @Test fun noSlideIsTooQuickForASofa() {
        assertEquals(MIN_SECONDS, secondsFor(slideshow.copy(slideSeconds = 0.5), null), 0.0)
    }

    @Test fun stepsGoRoundOnceEverythingIsHere() {
        assertEquals(1, stepFrom(0, 1, loaded = 3, total = 3))
        assertEquals(0, stepFrom(2, 1, loaded = 3, total = 3))
        assertEquals(2, stepFrom(0, -1, loaded = 3, total = 3))
    }

    @Test fun stepsWaitForTheNextPage() {
        assertNull(stepFrom(199, 1, loaded = 200, total = 450))
        assertNull(stepFrom(0, -1, loaded = 200, total = 450))
        assertEquals(200, stepFrom(199, 1, loaded = 201, total = 450))
    }

    @Test fun oneSlideStaysPutAndNothingGoesNowhere() {
        assertEquals(0, stepFrom(0, 1, loaded = 1, total = 1))
        assertNull(stepFrom(0, 1, loaded = 0, total = 0))
    }

    @Test fun changesFollowTheSlideshow() {
        assertEquals(Change.NONE, Change.of("none"))
        assertEquals(Change.SLIDE, Change.of("slide"))
        assertEquals(Change.DIP, Change.of("dipblack"))
        assertEquals(Change.CROSSFADE, Change.of("kenburns"))
        assertEquals(Change.CROSSFADE, Change.of("random"))
        assertEquals(Change.CROSSFADE, Change.of(null))
    }

    @Test fun datesSayOnlyWhatIsKnown() {
        val decade = { year: Int -> "${year}s" }
        assertEquals("1960s", takenText("1964-01-01T00:00:00.000Z", "decade", Locale.US, decade))
        assertEquals("1962", takenText("1962-01-01T00:00:00.000Z", "year", Locale.US, decade))
        assertEquals("July 1962", takenText("1962-07-01T00:00:00.000Z", "month", Locale.US, decade))
        assertEquals("March 12, 2019", takenText("2019-03-12T23:30:00.000Z", "time", Locale.US, decade))
        assertNull(takenText(null, "day", Locale.US, decade))
        assertNull(takenText("garbage", "day", Locale.US, decade))
    }

    @Test fun russianMonthsStandAlone() {
        assertEquals("Июль 1962", takenText("1962-07-01T00:00:00.000Z", "month", Locale.forLanguageTag("ru"), { "$it" }))
    }

    @Test fun readsHomesAnswers() {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val list = json.decodeFromString<TvPhotoList>(
            """{"items":[{"pickId":"p","kind":"slideshow","id":"s","name":"Summer","coverUrl":"/api/library/covers/c","itemCount":42,"slideSeconds":5,"transition":"fade","musicTrackId":null,"movie":true,"extra":1}]}"""
        ).items
        assertEquals(1, list.size)
        assertTrue(list[0].isSlideshow)
        assertTrue(list[0].movie)
        val page = json.decodeFromString<AssetPage>(
            """{"album":{"id":"a"},"assets":[{"id":"x","kind":"photo","previewUrl":"/p","coverUrl":"/c","placeText":"Minsk","gps":{"lat":1}},{"id":"y","coverUrl":"/c2"}],"total":2}"""
        )
        assertEquals("/p", page.assets[0].picture)
        assertEquals("/c2", page.assets[1].picture)
        assertFalse(TvPhotoItem("p", "album", "a", "A").isSlideshow)
    }
}
