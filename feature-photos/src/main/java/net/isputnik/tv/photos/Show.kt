package net.isputnik.tv.photos

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// The viewer's rules, apart from the screen so they can be tested: how long a slide stays,
// where a step goes, how a slide changes, and how a photo's date reads.

/** An album's slides stay this long (home's docs/tv-photos-plan.md, open question 2). */
const val ALBUM_SECONDS = 6.0

/** No slide goes by faster than this, whatever a slideshow says: a TV is watched from a sofa. */
const val MIN_SECONDS = 2.0

/** How long the caption stays after a key, before it fades. */
const val CAPTION_MILLIS = 4_000L

/** How long `asset` stays on screen: its own time in a slideshow, else the slideshow's, else
 *  an album's. */
fun secondsFor(item: TvPhotoItem, asset: PhotoAsset?): Double {
    val own = if (item.isSlideshow) asset?.dwellSeconds ?: item.slideSeconds else null
    return maxOf(own ?: ALBUM_SECONDS, MIN_SECONDS)
}

/**
 * Where a step from `index` by `by` lands among the `loaded` slides of `total`. Past the end it
 * goes round to the first, but only once everything is loaded: until then it stays put (null),
 * and the next page brings the slide it was going to.
 */
fun stepFrom(index: Int, by: Int, loaded: Int, total: Int): Int? {
    if (loaded == 0) return null
    val target = index + by
    return when {
        target in 0 until loaded -> target
        target >= loaded -> if (loaded >= total) 0 else null
        // Back from the first: to the last, if it's here.
        else -> if (loaded >= total) loaded - 1 else null
    }
}

/** How one slide gives way to the next, from a slideshow's own choice. An album fades. */
enum class Change {
    NONE, CROSSFADE, SLIDE, DIP;

    companion object {
        fun of(transition: String?): Change = when (transition) {
            "none" -> NONE
            "slide" -> SLIDE
            "dipblack" -> DIP
            // fade, crossfade, kenburns and random all read as a crossfade on the TV, for now.
            else -> CROSSFADE
        }
    }
}

/**
 * A photo's date as far as it's known (home's taken_precision): a day in full, a month and
 * year, a year, or a decade (`decade` words it: "1960s"). Home keeps a coarse date as its
 * period's first instant, so only the date part of `takenAt` is read, never shifted by zone.
 */
fun takenText(takenAt: String?, precision: String?, locale: Locale, decade: (Int) -> String): String? {
    val date = takenAt?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: return null
    return when (precision) {
        "decade" -> decade(date.year - date.year % 10)
        "year" -> date.year.toString()
        "month" -> DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(date).replaceFirstChar { it.titlecase(locale) }
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(date)
    }
}
