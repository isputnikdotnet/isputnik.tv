package net.isputnik.tv.games

import android.content.Context
import android.graphics.Picture
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.nativeCanvas
import com.caverock.androidsvg.SVG
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The games' own drawings, taken from the games repository by its scripts/export-tv.ts
// (assets/chess-pieces.json, assets/sputnik-lcd.json) so the TV shows what the browser shows.
// Each SVG is turned into a Picture once; drawing one is then a cheap replay.

private val json = Json { ignoreUnknownKeys = true }

private fun picture(svg: String, width: Float, height: Float): Picture {
    val doc = SVG.getFromString(svg)
    doc.documentWidth = width
    doc.documentHeight = height
    return doc.renderToPicture()
}

/** Draws a picture made at its own size into `size` × `size`-shaped space at (x, y). */
internal fun Canvas.drawPicture(picture: Picture, x: Float, y: Float, width: Float, height: Float) {
    val c = nativeCanvas
    c.save()
    c.translate(x, y)
    c.scale(width / picture.width, height / picture.height)
    c.drawPicture(picture)
    c.restore()
}

/** The twelve pieces by letter: PNBRQK White, pnbrqk Black. Drawn at 180 px, crisp when
 *  scaled to a TV square. */
internal class ChessArt(context: Context) {
    val pieces: Map<Char, Picture> = run {
        val text = context.assets.open("chess-pieces.json").bufferedReader().readText()
        json.decodeFromString<Map<String, String>>(text).entries.associate { (letter, svg) -> letter[0] to picture(svg, 180f, 180f) }
    }

    companion object {
        @Volatile private var loaded: ChessArt? = null
        fun get(context: Context) = loaded ?: synchronized(this) { loaded ?: ChessArt(context.applicationContext).also { loaded = it } }
    }
}

@Serializable
private data class LcdFile(
    val width: Int,
    val height: Int,
    val unlit: Float,
    val lit: Float,
    val backdrop: String,
    val segments: Map<String, String>,
    val ru: Map<String, String> = emptyMap()
)

/** The Sputnik Catch LCD: its printed backdrop and every segment, each its own picture,
 *  switched on and off as the web app's LcdScreen does. */
internal class LcdArt(context: Context, russian: Boolean) {
    val width: Int
    val height: Int
    val unlit: Float
    val lit: Float
    val backdrop: Picture
    val segments: Map<String, Picture>

    init {
        val file = json.decodeFromString<LcdFile>(context.assets.open("sputnik-lcd.json").bufferedReader().readText())
        width = file.width
        height = file.height
        unlit = file.unlit
        lit = file.lit
        // Drawn at twice the LCD's own size, so it stays sharp on a large screen.
        val w = file.width * 2f
        val h = file.height * 2f
        val wrap = { inner: String -> """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${file.width} ${file.height}">$inner</svg>""" }
        backdrop = picture(wrap(file.backdrop), w, h)
        val words = if (russian) file.ru else emptyMap()
        segments = file.segments.mapValues { (id, svg) -> picture(wrap(words[id] ?: svg), w, h) }
    }
}
