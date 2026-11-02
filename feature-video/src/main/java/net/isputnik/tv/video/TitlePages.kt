package net.isputnik.tv.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import net.isputnik.tv.core.Navigator
import net.isputnik.tv.core.PageMessage
import net.isputnik.tv.core.PosterCard
import net.isputnik.tv.core.RestoreFocus
import net.isputnik.tv.core.TvButton
import net.isputnik.tv.core.TvColors
import net.isputnik.tv.core.remembersFocus
import net.isputnik.tv.core.R as CoreR

/** The picture behind a title page, faded so the words on it stay readable. */
@Composable
private fun Backdrop(video: VideoApi, backdrop: String?, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        backdrop?.let {
            AsyncImage(model = video.absolute(it), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to TvColors.Background.copy(alpha = 0.96f), 0.6f to TvColors.Background.copy(alpha = 0.7f), 1f to TvColors.Background.copy(alpha = 0.4f))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to TvColors.Background.copy(alpha = 0f), 1f to TvColors.Background)))
        content()
    }
}

/** 2019 · 1 h 42 min · Drama, Family · ★ 7.4: whichever of them is known. */
@Composable
private fun metaLine(year: Int?, runtimeMinutes: Int?, genres: List<String>, rating: Double?): String = listOfNotNull(
    year?.toString(),
    runtimeMinutes?.let {
        if (it >= 60) stringResource(R.string.video_runtime_hours, it / 60, it % 60) else stringResource(R.string.video_runtime_minutes, it)
    },
    genres.take(3).joinToString(", ").ifEmpty { null },
    rating?.let { "★ %.1f".format(it) }
).joinToString(" · ")

@Composable
private fun TitleHead(title: String, meta: String, plot: String?, plotLines: Int) {
    Text(title, color = TvColors.Text, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.7f))
    if (meta.isNotEmpty()) Text(meta, color = TvColors.Muted, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    plot?.let {
        Text(it, color = TvColors.Text, fontSize = 14.sp, lineHeight = 20.sp, maxLines = plotLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp).fillMaxWidth(0.62f))
    }
}

@Composable
private fun <T : Any> rememberTitle(id: String, load: suspend (String) -> T): Triple<T?, Boolean, () -> Unit> {
    var value by remember(id) { mutableStateOf<T?>(null) }
    var failed by remember(id) { mutableStateOf(false) }
    var attempt by remember(id) { mutableIntStateOf(0) }
    LaunchedEffect(id, attempt) {
        failed = false
        try {
            value = load(id)
        } catch (_: java.io.IOException) {
            failed = true
        } catch (_: kotlinx.serialization.SerializationException) {
            failed = true
        }
    }
    return Triple(value, failed, { attempt++ })
}

@Composable
internal fun MoviePage(video: VideoApi, nav: Navigator, id: String) {
    val (movie, failed, retry) = rememberTitle(id) { video.movie(it) }
    when {
        movie == null && failed -> PageMessage(stringResource(R.string.video_load_failed), stringResource(R.string.video_retry), retry)
        movie == null -> PageMessage(stringResource(R.string.video_loading))
        else -> Backdrop(video, movie.backdrop) {
            LazyColumn(state = rememberLazyListState(), contentPadding = PaddingValues(top = 56.dp, bottom = 28.dp)) {
                item(key = "head") {
                    Column(Modifier.padding(horizontal = PageGutter)) {
                        TitleHead(movie.title, metaLine(movie.year, movie.runtimeMinutes, movie.genres, movie.rating), movie.plot, 5)
                        Spacer(Modifier.height(18.dp))
                        val started = (movie.progress?.position ?: 0.0) > 0 && movie.progress?.watched != true
                        Row {
                            TvButton(
                                stringResource(if (started) R.string.video_resume else R.string.video_play),
                                Modifier.remembersFocus("play", isDefault = true),
                                primary = true,
                                icon = CoreR.drawable.ic_play
                            ) { nav.push(VideoRoute.Play(movie.id)) }
                            if (started) {
                                Spacer(Modifier.width(12.dp))
                                TvButton(stringResource(R.string.video_start_over), Modifier.remembersFocus("over")) { nav.push(VideoRoute.Play(movie.id, fromStart = true)) }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
                val set = movie.set
                if (set != null && set.films.size > 1) {
                    item(key = "set") {
                        PosterRow(set.name, set.films.map { film ->
                            RowCard("set/${film.id}", film.title, film.poster?.let(video::absolute), film.year?.toString(), film.progress.fraction()) {
                                nav.push(VideoRoute.MoviePage(film.id))
                            }
                        })
                    }
                }
            }
            RestoreFocus(true)
        }
    }
}

@Composable
internal fun ShowPage(video: VideoApi, nav: Navigator, id: String) {
    val (show, failed, retry) = rememberTitle(id) { video.show(it) }
    var chosen by rememberSaveable(id) { mutableStateOf<Int?>(null) }
    when {
        show == null && failed -> PageMessage(stringResource(R.string.video_load_failed), stringResource(R.string.video_retry), retry)
        show == null -> PageMessage(stringResource(R.string.video_loading))
        else -> Backdrop(video, show.backdrop) {
            val next = show.nextUp
            // Opens on the season you're up to.
            val current = show.seasons.firstOrNull { it.number == (chosen ?: next?.season) } ?: show.seasons.firstOrNull()
            LazyColumn(state = rememberLazyListState(), contentPadding = PaddingValues(top = 34.dp, bottom = 24.dp)) {
                item(key = "head") {
                    Column(Modifier.padding(horizontal = PageGutter)) {
                        TitleHead(show.title, metaLine(show.year, null, show.genres, show.rating), show.plot, 3)
                        Spacer(Modifier.height(14.dp))
                        if (next != null) {
                            val code = stringResource(R.string.video_banner_code, next.season, next.episode)
                            TvButton(
                                stringResource(if (next.position > 0) R.string.video_continue else R.string.video_play) + " · " + code,
                                Modifier.remembersFocus("play", isDefault = true),
                                primary = true,
                                icon = CoreR.drawable.ic_play
                            ) { nav.push(VideoRoute.Play(next.id)) }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
                if (show.seasons.size > 1) {
                    item(key = "seasons") {
                        LazyRow(contentPadding = PaddingValues(horizontal = PageGutter), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                            items(show.seasons, key = { it.number }) { season ->
                                TvButton(
                                    stringResource(R.string.video_season, season.number),
                                    Modifier.remembersFocus("season/${season.number}"),
                                    primary = season.number == current?.number
                                ) { chosen = season.number }
                            }
                        }
                    }
                }
                current?.let { season ->
                    item(key = "episodes/${season.number}") {
                        LazyRow(state = rememberLazyListState(), contentPadding = PaddingValues(horizontal = PageGutter), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(season.episodes, key = { it.id }) { episode ->
                                PosterCard(
                                    title = episode.title ?: stringResource(R.string.video_episode_untitled, episode.episode),
                                    picture = episode.still?.let(video::absolute),
                                    focusKey = "episode/${episode.id}",
                                    width = 208.dp,
                                    height = 117.dp,
                                    subtitle = stringResource(R.string.video_banner_code, season.number, episode.episode) +
                                        if (episode.progress?.watched == true) " · " + stringResource(R.string.video_watched) else "",
                                    progress = episode.progress.fraction(),
                                    isDefault = next == null && episode === season.episodes.first()
                                ) { nav.push(VideoRoute.Play(episode.id)) }
                            }
                        }
                    }
                }
            }
            RestoreFocus(true)
        }
    }
}

@Composable
internal fun SetPage(video: VideoApi, nav: Navigator, id: String) {
    val (set, failed, retry) = rememberTitle(id) { video.set(it) }
    when {
        set == null && failed -> PageMessage(stringResource(R.string.video_load_failed), stringResource(R.string.video_retry), retry)
        set == null -> PageMessage(stringResource(R.string.video_loading))
        else -> Backdrop(video, set.backdrop) {
            LazyColumn(state = rememberLazyListState(), contentPadding = PaddingValues(top = 44.dp, bottom = 28.dp)) {
                item(key = "head") {
                    Column(Modifier.padding(horizontal = PageGutter).padding(bottom = 22.dp)) {
                        TitleHead(set.name, pluralStringResource(R.plurals.video_set_films, set.films.size, set.films.size), set.overview, 4)
                    }
                }
                item(key = "films") {
                    PosterRow(stringResource(R.string.video_movies), set.films.map { film ->
                        RowCard("film/${film.id}", film.title, film.poster?.let(video::absolute), film.year?.toString(), film.progress.fraction()) {
                            nav.push(VideoRoute.MoviePage(film.id))
                        }
                    }, defaultFocus = true)
                }
            }
            RestoreFocus(true)
        }
    }
}
