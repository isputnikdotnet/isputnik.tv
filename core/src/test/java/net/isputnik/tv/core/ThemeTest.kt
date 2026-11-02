package net.isputnik.tv.core

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    // Home's THEME_PREFERENCES, in its db.ts.
    private val themes = listOf("system", "light", "dark", "plain-light", "plain-dark", "minimalist", "orbit", "thanksgiving")

    private fun contrast(a: Color, b: Color): Float {
        val (lighter, darker) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    @Test fun `a name it doesn't know, or none, gets the default`() {
        assertEquals(OrbitPalette, paletteFor(null))
        assertEquals(OrbitPalette, paletteFor("some-future-theme"))
    }

    @Test fun `every theme on the website has its own palette, but system`() {
        val named = themes - "system" - "orbit"
        named.forEach { assertNotEquals(it, OrbitPalette, paletteFor(it)) }
        assertEquals(named.size, named.map(::paletteFor).toSet().size)
        assertEquals(paletteFor("plain-dark"), paletteFor("system"))
    }

    @Test fun `every palette stays dark, and readable across a room`() {
        themes.map(::paletteFor).forEach { p ->
            assertTrue("$p background", p.background.luminance() < 0.05f)
            assertTrue("$p text", contrast(p.text, p.background) >= 7f)
            assertTrue("$p muted", contrast(p.muted, p.surface) >= 4.5f)
            assertTrue("$p onAccent", contrast(p.onAccent, p.accent) >= 4.4f)
        }
    }

    // A button takes focus by going from raised to accent; a primary one by gaining a white ring.
    @Test fun `focus shows in every palette`() {
        themes.map(::paletteFor).forEach { p ->
            assertTrue("$p accent", contrast(p.accent, p.surfaceRaised) >= 3f)
            assertTrue("$p ring", contrast(Color.White, p.accent) >= 1.5f)
        }
    }
}
