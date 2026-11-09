package net.isputnik.tv.photos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P3Test {
    @Test fun videosPlayInPlaceOnlyWhenHomeCanPlayThem() {
        assertTrue(playsInPlace(PhotoAsset("v", kind = "video", playbackUrl = "/f?web=1")))
        assertTrue(playsInPlace(PhotoAsset("v", kind = "video", playbackUrl = "/f", playable = null)))
        assertFalse(playsInPlace(PhotoAsset("v", kind = "video", playbackUrl = "/f", playable = false)))
        assertFalse(playsInPlace(PhotoAsset("v", kind = "video")))
        assertFalse(playsInPlace(PhotoAsset("p", kind = "photo", playbackUrl = "/f")))
        assertFalse(playsInPlace(null))
    }

    @Test fun captionsNameAFewAndCountTheRest() {
        val more = { n: Int -> "and $n more" }
        assertNull(peopleText(emptyList(), more))
        assertNull(peopleText(listOf(" ", ""), more))
        assertEquals("Anna, Boris", peopleText(listOf("Anna", "Boris", "Anna"), more))
        assertEquals("A, B, C, D and 2 more", peopleText(listOf("A", "B", "C", "D", "E", "F"), more))
    }

    @Test fun clockReadsLikeAPlayer() {
        assertEquals("0:00", clock(0))
        assertEquals("1:05", clock(65_400))
        assertEquals("1:02:05", clock(3_725_000))
    }
}
