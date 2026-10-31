package net.isputnik.tv.video

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import net.isputnik.tv.core.TvButton
import net.isputnik.tv.core.TvColors
import net.isputnik.tv.core.User

private sealed interface Route {
    data object Browse : Route
    data class ShowPage(val id: String) : Route
    data class Play(val id: String) : Route
}

/** Everything video: the rows, a show's episodes, the player. T2 replaces the plain screens
 *  with the look in docs/tv-app-plan.md; T1 is the whole path from a title to the player. */
@Composable
fun VideoSection(video: VideoApi, user: User, onUnlink: () -> Unit) {
    val stack = remember { mutableStateListOf<Route>(Route.Browse) }
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }
    when (val route = stack.last()) {
        Route.Browse -> BrowseScreen(video, user, onUnlink, { stack += Route.ShowPage(it) }, { stack += Route.Play(it) })
        is Route.ShowPage -> key(route.id) { ShowScreen(video, route.id) { stack += Route.Play(it) } }
        is Route.Play -> key(route.id) { PlayerScreen(video, route.id) { stack.removeAt(stack.lastIndex) } }
    }
}

@Composable
private fun Message(text: String, retry: (() -> Unit)? = null, focus: FocusRequester? = null) {
    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = TvColors.Text, fontSize = 20.sp)
        if (retry != null) {
            Spacer(Modifier.height(24.dp))
            TvButton(stringResource(R.string.video_retry), if (focus != null) Modifier.focusRequester(focus) else Modifier, retry)
        }
    }
}

private class Catalog(val available: Boolean, val resume: List<ContinueItem>, val movies: List<Summary>, val shows: List<Summary>)

@Composable
private fun BrowseScreen(video: VideoApi, user: User, onUnlink: () -> Unit, onShow: (String) -> Unit, onPlay: (String) -> Unit) {
    var catalog by remember { mutableStateOf<Catalog?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    val first = remember { FocusRequester() }

    // Runs again on the way back from the player, so Continue watching is current.
    LaunchedEffect(attempt) {
        failed = false
        try {
            catalog = if (!video.status().enabled) Catalog(false, emptyList(), emptyList(), emptyList())
            else Catalog(true, video.continueWatching(), video.movies(), video.shows())
        } catch (_: Exception) {
            failed = true
        }
    }
    val loaded = catalog
    LaunchedEffect(loaded, failed) { if (loaded != null || failed) runCatching { first.requestFocus() } }

    when {
        failed -> Message(stringResource(R.string.video_load_failed), { attempt++ }, first)
        loaded == null -> Message(stringResource(R.string.video_loading))
        !loaded.available -> Message(stringResource(R.string.video_unavailable), { attempt++ }, first)
        else -> {
            var firstUsed = false
            fun takeFirst(): FocusRequester? = if (firstUsed) null else { firstUsed = true; first }
            LazyColumn(contentPadding = PaddingValues(vertical = 32.dp)) {
                item { Text(stringResource(R.string.video_signed_in_as, user.displayName), color = TvColors.Muted, fontSize = 15.sp, modifier = Modifier.padding(start = 48.dp, bottom = 20.dp)) }
                if (loaded.resume.isNotEmpty()) {
                    val focus = takeFirst()
                    item { PosterRow(stringResource(R.string.video_continue), loaded.resume.map { RowItem(it.id, continueTitle(it), video.posterOf(it), fraction(it)) { onPlay(it.id) } }, focus) }
                }
                if (loaded.movies.isNotEmpty()) {
                    val focus = takeFirst()
                    item { PosterRow(stringResource(R.string.video_movies), loaded.movies.map { RowItem(it.id, it.title, it.poster?.let(video::absolute), null) { onPlay(it.id) } }, focus) }
                }
                if (loaded.shows.isNotEmpty()) {
                    val focus = takeFirst()
                    item { PosterRow(stringResource(R.string.video_shows), loaded.shows.map { RowItem(it.id, it.title, it.poster?.let(video::absolute), null) { onShow(it.id) } }, focus) }
                }
                if (loaded.resume.isEmpty() && loaded.movies.isEmpty() && loaded.shows.isEmpty()) {
                    item { Text(stringResource(R.string.video_empty), color = TvColors.Text, fontSize = 18.sp, modifier = Modifier.padding(48.dp)) }
                }
                item {
                    val focus = takeFirst()
                    var confirming by remember { mutableStateOf(false) }
                    val base = Modifier.padding(start = 48.dp, top = 16.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TvButton(stringResource(R.string.video_unlink), if (focus != null) base.focusRequester(focus) else base) {
                            if (confirming) onUnlink() else confirming = true
                        }
                        if (confirming) Text(stringResource(R.string.video_unlink_confirm), color = TvColors.Muted, fontSize = 15.sp, modifier = Modifier.padding(start = 20.dp, top = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun continueTitle(item: ContinueItem): String {
    val show = item.show ?: return item.title
    val code = if (item.season != null && item.episode != null) stringResource(R.string.video_episode_code, item.season, item.episode) + " · " else ""
    return "${show.title} · $code${item.title}"
}

private fun fraction(item: ContinueItem): Float? {
    val duration = item.duration ?: return null
    return if (item.resume && duration > 0) (item.position / duration).toFloat().coerceIn(0f, 1f) else null
}

private class RowItem(val id: String, val title: String, val poster: String?, val progress: Float?, val onClick: () -> Unit)

@Composable
private fun PosterRow(title: String, items: List<RowItem>, focus: FocusRequester?) {
    Column {
        Text(title, color = TvColors.Text, fontSize = 22.sp, modifier = Modifier.padding(start = 48.dp, bottom = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp)) {
            items(items, key = { it.id }) { item ->
                Poster(item, if (item === items.first()) focus else null)
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun Poster(item: RowItem, focus: FocusRequester?) {
    Column(Modifier.padding(end = 18.dp).width(160.dp)) {
        Card(
            onClick = item.onClick,
            scale = CardDefaults.scale(focusedScale = 1.06f),
            border = CardDefaults.border(focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(3.dp, TvColors.Accent))),
            modifier = Modifier.size(160.dp, 240.dp).let { if (focus != null) it.focusRequester(focus) else it }
        ) {
            Box(Modifier.fillMaxSize().background(TvColors.Surface)) {
                if (item.poster != null) {
                    AsyncImage(model = item.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                item.progress?.let { done ->
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x66000000))) {
                        Box(Modifier.fillMaxWidth(done).height(4.dp).background(TvColors.Accent))
                    }
                }
            }
        }
        Text(item.title, color = TvColors.Text, fontSize = 14.sp, maxLines = 2, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ShowScreen(video: VideoApi, id: String, onPlay: (String) -> Unit) {
    var show by remember { mutableStateOf<Show?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    val first = remember { FocusRequester() }
    LaunchedEffect(attempt) {
        failed = false
        try {
            show = video.show(id)
        } catch (_: Exception) {
            failed = true
        }
    }
    val loaded = show
    LaunchedEffect(loaded, failed) { if (loaded != null || failed) runCatching { first.requestFocus() } }
    when {
        failed -> Message(stringResource(R.string.video_load_failed), { attempt++ }, first)
        loaded == null -> Message(stringResource(R.string.video_loading))
        else -> LazyColumn(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp)) {
            item {
                Text(loaded.title, color = TvColors.Text, fontSize = 34.sp)
                loaded.plot?.let { Text(it, color = TvColors.Muted, fontSize = 16.sp, maxLines = 3, modifier = Modifier.padding(top = 8.dp).fillMaxWidth(0.7f)) }
                Spacer(Modifier.height(20.dp))
            }
            val next = loaded.nextUp
            if (next != null) {
                item {
                    val label = stringResource(if (next.position > 0) R.string.video_resume else R.string.video_play) + " · " + stringResource(R.string.video_episode_code, next.season, next.episode)
                    TvButton(label, Modifier.focusRequester(first)) { onPlay(next.id) }
                    Spacer(Modifier.height(20.dp))
                }
            }
            var firstEpisode = next == null
            for (season in loaded.seasons) {
                item { Text(stringResource(R.string.video_season, season.number), color = TvColors.Text, fontSize = 20.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)) }
                items(season.episodes, key = { it.id }) { episode ->
                    val focused = firstEpisode && episode === season.episodes.first()
                    ListItem(
                        selected = false,
                        onClick = { onPlay(episode.id) },
                        modifier = if (focused) Modifier.focusRequester(first) else Modifier,
                        headlineContent = { Text(stringResource(R.string.video_episode_code, season.number, episode.episode) + "  " + (episode.title ?: stringResource(R.string.video_episode_untitled, episode.episode))) },
                        trailingContent = if (episode.progress?.watched == true) ({ Text(stringResource(R.string.video_watched), fontSize = 13.sp) }) else null
                    )
                }
                firstEpisode = false
            }
        }
    }
}
