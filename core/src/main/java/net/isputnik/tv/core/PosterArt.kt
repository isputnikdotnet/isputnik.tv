package net.isputnik.tv.core

import androidx.annotation.DrawableRes

/** What stands on a card whose title has no picture: its kind's art, the same the website draws
 *  (home's Assets/covers), with the name over the art's plain lower part. */
enum class PosterArt {
    MOVIE,
    SHOW;

    /** The 2:3 poster for a card taller than it is wide, the 16:9 frame for a wide one. */
    @DrawableRes
    fun drawable(wide: Boolean): Int = when (this) {
        MOVIE -> if (wide) R.drawable.cover_movie_wide else R.drawable.cover_movie
        SHOW -> if (wide) R.drawable.cover_show_wide else R.drawable.cover_show
    }

    companion object {
        /** Home's lists name a kind "movies" or "shows"; anything else is a film. */
        fun of(kind: String?): PosterArt = if (kind == "shows") SHOW else MOVIE
    }
}
