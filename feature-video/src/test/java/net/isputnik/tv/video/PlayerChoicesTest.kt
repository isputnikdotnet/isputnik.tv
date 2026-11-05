package net.isputnik.tv.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PlayerChoicesTest {
    private val rus = AudioTrack(index = 1, language = "rus", selected = true)
    private val eng = AudioTrack(index = 2, language = "eng")

    private fun subtitle(id: String, language: String?, kind: String = "text", forced: Boolean = false, default: Boolean = false) =
        SubtitleTrack(id = id, kind = kind, language = language, forced = forced, default = default)

    @Test fun `files say rus, file names say ru, and they're one language`() {
        assertTrue(sameLanguage("rus", "ru"))
        assertTrue(sameLanguage("en", "ENG"))
        assertFalse(sameLanguage("rus", "eng"))
        assertFalse(sameLanguage(null, "eng"))
    }

    @Test fun `audio follows the language chosen last, else what the engine selected`() {
        assertEquals(eng, pickAudio(listOf(rus, eng), "en"))
        assertEquals(rus, pickAudio(listOf(rus, eng), "fre"))
        assertEquals(rus, pickAudio(listOf(rus, eng), null))
        assertNull(pickAudio(emptyList(), "eng"))
    }

    @Test fun `subtitles stay off once turned off, even with a default track`() {
        assertNull(pickSubtitle(listOf(subtitle("a", "en", default = true)), SUBTITLES_OFF))
    }

    @Test fun `the language chosen wins, full before forced, text before image`() {
        val forced = subtitle("forced", "ru", forced = true)
        val image = subtitle("image", "ru", kind = "image")
        val text = subtitle("text", "rus")
        assertEquals(text, pickSubtitle(listOf(forced, image, text), "ru"))
        assertEquals(image, pickSubtitle(listOf(forced, image), "ru"))
    }

    @Test fun `with no choice, the file's default, never a forced one`() {
        assertEquals("d", pickSubtitle(listOf(subtitle("f", "en", forced = true, default = true), subtitle("d", "en", default = true)), null)?.id)
        assertNull(pickSubtitle(listOf(subtitle("a", "en")), null))
    }

    @Test fun `Skip passes over the recap, then the intro, each to its own end`() {
        val markers = Markers(recap = IntroMark(18.0, 80.0), intro = IntroMark(200.0, 270.0))
        assertNull(skippableAt(markers, 17.9))
        assertEquals(Skippable.RECAP to 80.0, skippableAt(markers, 18.0))
        assertNull(skippableAt(markers, 80.0))
        assertEquals(Skippable.INTRO to 270.0, skippableAt(markers, 230.0))
        // An engine from before recaps sends none.
        assertEquals(Skippable.INTRO to 270.0, skippableAt(Markers(intro = IntroMark(200.0, 270.0)), 230.0))
    }

    @Test fun `Skip intro shows only inside the intro, and the card from the credits on`() {
        val markers = Markers(intro = IntroMark(5.0, 20.0), credits = CreditsMark(100.0))
        assertFalse(inIntro(markers, 4.9))
        assertTrue(inIntro(markers, 5.0))
        assertFalse(inIntro(markers, 20.0))
        assertFalse(inCredits(markers, 99.0))
        assertTrue(inCredits(markers, 100.0))
        assertFalse(inIntro(null, 10.0))
        assertFalse(inCredits(Markers(), 1000.0))
    }

    @Test fun `languages read in the person's own words`() {
        assertEquals("Russian", languageName("rus", Locale.ENGLISH))
        assertEquals("Русский", languageName("ru", Locale.forLanguageTag("ru")))
        assertNull(languageName("und", Locale.ENGLISH))
        assertNull(languageName(null, Locale.ENGLISH))
    }

    @Test fun `only surround layouts are worth naming`() {
        assertEquals("5.1", channelsName(6))
        assertNull(channelsName(2))
    }

    @Test fun `two tracks of one name get their numbers, so a menu never has two rows alike`() {
        // Bad Boys (1995), a DVD rip: five sound tracks, two pairs named the same; English
        // subtitles as a DVD picture track and as text. Two rows of one name crashed the menu.
        val audio = listOf("English · Stereo", "English · Surround 5.1", "Spanish · Stereo", "English · Stereo", "English · Surround 5.1")
        assertEquals(
            listOf("English · Stereo · Track 1", "English · Surround 5.1 · Track 2", "Spanish · Stereo", "English · Stereo · Track 4", "English · Surround 5.1 · Track 5"),
            distinguish(audio) { "Track ${it + 1}" }
        )
        val subtitles = listOf("English", "French", "Spanish", "English")
        assertEquals(listOf("English · Track 1", "French", "Spanish", "English · Track 4"), distinguish(subtitles) { "Track ${it + 1}" })
        assertEquals(listOf("English", "French"), distinguish(listOf("English", "French")) { "Track ${it + 1}" })
    }
}
