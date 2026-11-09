package net.isputnik.tv.video

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import net.isputnik.tv.core.ApiException
import net.isputnik.tv.core.LocalFocusMemory
import net.isputnik.tv.core.Navigator
import net.isputnik.tv.core.PageMessage
import net.isputnik.tv.core.PosterArt
import net.isputnik.tv.core.PosterCard
import net.isputnik.tv.core.ProgressLine
import net.isputnik.tv.core.RestoreFocus
import net.isputnik.tv.core.ShowsBackdrop
import net.isputnik.tv.core.TvButton
import net.isputnik.tv.core.TvColors
import net.isputnik.tv.core.remembersFocus
import net.isputnik.tv.core.R as CoreR

internal val PosterWidth = 136.dp
internal val PosterHeight = 160.dp
private const val GridColumns = 6
internal val PageGutter = 32.dp

@Composable
private fun episodeCode(season: Int?, episode: Int?): String? =
    if (season != null && episode != null) stringResource(R.string.video_banner_code, season, episode) else null

@Composable
private fun timeLeft(minutes: Int?): String? = when {
    minutes == null -> null
    minutes >= 60 -> stringResource(R.string.video_left_hours, minutes / 60, minutes % 60)
    else -> stringResource(R.string.video_left_minutes, minutes)
}

/** The owner's banner (2026-11-09 mock-up): the poster on the left; beside it a small spaced
 *  heading saying what this is, an accent line, the title large in a serif, a line about it,
 *  how far you got, two buttons. Over the title's backdrop, which fills the screen behind. */
@Composable
private fun Banner(banner: BannerModel, nav: Navigator, video: VideoApi) {
    Row(Modifier.fillMaxWidth().height(190.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 120.dp, height = 170.dp).clip(RoundedCornerShape(8.dp)).background(TvColors.SurfaceRaised)) {
            // The title's own poster, else the engine's art for it (as the cards draw it).
            AsyncImage(model = banner.poster?.let(video::absolute) ?: video.posterOf(banner.id), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(
                    when (banner.kind) {
                        BannerKind.CONTINUE -> R.string.video_banner_continue
                        BannerKind.SERIES -> R.string.video_banner_series
                        BannerKind.FILM -> R.string.video_banner_film
                    }
                ).uppercase(),
                color = TvColors.Muted,
                fontSize = 11.sp,
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Medium
            )
            Box(Modifier.padding(top = 6.dp, bottom = 8.dp).size(width = 26.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(TvColors.Accent))
            Text(
                banner.title,
                color = TvColors.Text,
                fontSize = 34.sp,
                fontFamily = FontFamily.Serif,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.8f)
            )
            banner.line?.let {
                Text(it, color = TvColors.Text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp).fillMaxWidth(0.72f))
            }
            val details = listOfNotNull(episodeCode(banner.season, banner.episode), timeLeft(banner.minutesLeft)).joinToString(" · ")
            if (banner.fraction != null || details.isNotEmpty()) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    banner.fraction?.let {
                        ProgressLine(it, Modifier.width(150.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(details, color = TvColors.Muted, fontSize = 12.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row {
                val playId = banner.playId
                if (playId != null) {
                    TvButton(
                        stringResource(if (banner.inProgress) R.string.video_continue else R.string.video_play),
                        Modifier.remembersFocus("banner-play", isDefault = true),
                        primary = true,
                        icon = CoreR.drawable.ic_play
                    ) { nav.push(VideoRoute.Play(playId)) }
                    Spacer(Modifier.width(12.dp))
                }
                val isShow = banner.moreRoute is VideoRoute.ShowPage
                TvButton(
                    stringResource(if (isShow) R.string.video_episodes else R.string.video_more),
                    Modifier.remembersFocus("banner-more", isDefault = playId == null),
                    icon = CoreR.drawable.ic_list
                ) { nav.push(banner.moreRoute) }
            }
        }
    }
}

internal data class RowCard(
    val key: String,
    val title: String,
    val picture: String?,
    val subtitle: String? = null,
    val progress: Float? = null,
    val art: PosterArt = PosterArt.MOVIE,
    val onClick: () -> Unit
)

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = TvColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = modifier.padding(bottom = 8.dp))
}

@Composable
internal fun PosterRow(title: String, cards: List<RowCard>, defaultFocus: Boolean = false) {
    Column {
        SectionTitle(title, Modifier.padding(start = PageGutter))
        // Its own remembered scroll: the state survives leaving for a title page and coming back.
        LazyRow(state = rememberLazyListState(), contentPadding = PaddingValues(horizontal = PageGutter), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(cards, key = { it.key }) { card ->
                PosterCard(
                    title = card.title,
                    picture = card.picture,
                    focusKey = card.key,
                    width = PosterWidth,
                    height = PosterHeight,
                    subtitle = card.subtitle,
                    progress = card.progress,
                    art = card.art,
                    isDefault = defaultFocus && card === cards.first(),
                    onClick = card.onClick
                )
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

private fun listedCard(video: VideoApi, nav: Navigator, item: ListedTitle, keyPrefix: String) = RowCard(
    key = "$keyPrefix/${item.kind}/${item.id}",
    title = item.title,
    picture = item.poster?.let(video::absolute),
    subtitle = item.year?.toString(),
    art = PosterArt.of(item.kind),
    onClick = { nav.push(if (item.kind == "shows") VideoRoute.ShowPage(item.id) else VideoRoute.MoviePage(item.id)) }
)

@Composable
private fun nameOf(name: RowName): String = when (name) {
    is RowName.Given -> name.text
    is RowName.Res -> stringResource(name.id)
    is RowName.Joined -> when (name.kind) {
        "movies" -> "${name.what} · ${stringResource(R.string.video_movies)}"
        "shows" -> "${name.what} · ${stringResource(R.string.video_shows)}"
        else -> name.what
    }
}

private fun summaryCard(video: VideoApi, nav: Navigator, kind: Kind, item: Summary, keyPrefix: String) = RowCard(
    key = "$keyPrefix/${item.id}",
    title = item.title,
    picture = item.poster?.let(video::absolute),
    subtitle = item.year?.toString(),
    art = if (kind == Kind.SHOWS) PosterArt.SHOW else PosterArt.MOVIE,
    onClick = { nav.push(if (kind == Kind.SHOWS) VideoRoute.ShowPage(item.id) else VideoRoute.MoviePage(item.id)) }
)

/** Asks the server each time the page is entered; what's already known stays on screen. */
@Composable
private fun rememberLoad(key: Any, load: suspend () -> Unit): Pair<Boolean, () -> Unit> {
    var failed by remember(key) { mutableStateOf(false) }
    var attempt by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key, attempt) {
        failed = false
        try {
            load()
        } catch (e: ApiException) {
            failed = true
        } catch (e: java.io.IOException) {
            failed = true
        } catch (e: kotlinx.serialization.SerializationException) {
            failed = true
        }
    }
    return failed to { attempt++ }
}

@Composable
private fun episodeLabel(item: ContinueItem): String {
    val code = episodeCode(item.season, item.episode)
    return if (code != null) "$code · ${item.title}" else item.title
}

@Composable
private fun SortControl(current: Sort, onChange: (Sort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var closed by remember { mutableIntStateOf(0) }
    val button = remember { FocusRequester() }
    // A closing popup hands focus to nobody in particular: bring it back here.
    LaunchedEffect(closed) {
        if (closed > 0) {
            withFrameNanos { }
            runCatching { button.requestFocus() }
        }
    }
    Box {
        TvButton(stringResource(current.label) + "  ▾", Modifier.focusRequester(button).remembersFocus("sort")) { open = true }
        if (open) {
            val close: () -> Unit = {
                open = false
                closed++
            }
            Popup(alignment = Alignment.TopEnd, onDismissRequest = close, properties = PopupProperties(focusable = true)) {
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
                Column(Modifier.width(IntrinsicSize.Max).clip(RoundedCornerShape(8.dp)).background(TvColors.Surface).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Sort.entries.forEach { option ->
                        TvButton(
                            stringResource(option.label),
                            Modifier.fillMaxWidth().let { if (option == current) it.focusRequester(first) else it }
                        ) {
                            close()
                            onChange(option)
                        }
                    }
                }
            }
        }
    }
}

/** Movies or Shows (2026-11-09): the banner, the video page's rows narrowed to this kind
 *  (VideoStore.rowFor; a profile's own rows when one is worn), then every title of the kind,
 *  A–Z or as sorted. The app opens on Movies. */
@Composable
internal fun KindPage(video: VideoApi, store: VideoStore, nav: Navigator, kind: Kind) {
    var sortName by rememberSaveable(kind) { mutableStateOf(Sort.TITLE.name) }
    val sort = Sort.valueOf(sortName)
    val (failed, retry) = rememberLoad(kind to sort) { store.refresh(kind, sort) }
    // The old order stays up until the new one arrives: if the page went away, so would focus.
    val shown = remember(kind) { arrayOfNulls<KindData>(1) }
    store.tab(kind, sort)?.let { shown[0] = it }
    val data = shown[0]
    when {
        store.available == false -> PageMessage(stringResource(R.string.video_unavailable), stringResource(R.string.video_retry), retry)
        data == null && failed -> PageMessage(stringResource(R.string.video_load_failed), stringResource(R.string.video_retry), retry)
        data == null -> PageMessage(stringResource(R.string.video_loading))
        data.all.isEmpty() && data.rowsEmpty -> PageMessage(stringResource(R.string.video_empty))
        else -> {
            ShowsBackdrop(data.banner?.backdrop?.let(video::absolute))
            LazyColumn(state = rememberLazyListState(), contentPadding = PaddingValues(top = 6.dp, bottom = 28.dp)) {
                data.banner?.let { banner ->
                    item(key = "banner") {
                        Box(Modifier.padding(horizontal = PageGutter).padding(bottom = 14.dp)) { Banner(banner, nav, video) }
                    }
                }
                // The rows in the page's order; an empty one isn't drawn. With no banner, the
                // first row drawn takes focus.
                val drawn = data.rows.filter {
                    when (it) {
                        is HomeEntry.Resume -> data.resume.isNotEmpty()
                        is HomeEntry.Titles -> it.items.isNotEmpty()
                        is HomeEntry.Sets -> it.sets.isNotEmpty()
                    }
                }
                drawn.forEachIndexed { index, entry ->
                    val first = data.banner == null && index == 0
                    item(key = "row/${entry.key}") {
                        val title = nameOf(entry.name)
                        when (entry) {
                            is HomeEntry.Resume -> PosterRow(title, data.resume.map { item ->
                                RowCard(
                                    key = "resume/${item.id}",
                                    title = item.show?.title ?: item.title,
                                    picture = video.posterOf(item),
                                    subtitle = if (item.show != null) episodeLabel(item) else null,
                                    progress = if (item.resume) fractionOf(item.position, item.duration) else null,
                                    art = if (item.show != null) PosterArt.SHOW else PosterArt.MOVIE,
                                    onClick = { nav.push(VideoRoute.Play(item.id)) }
                                )
                            }, defaultFocus = first)
                            is HomeEntry.Titles -> PosterRow(title, entry.items.map { listedCard(video, nav, it, entry.key) }, defaultFocus = first)
                            is HomeEntry.Sets -> PosterRow(title, entry.sets.map { set ->
                                RowCard("${entry.key}/${set.id}", set.name, set.poster?.let(video::absolute), pluralStringResource(R.plurals.video_set_films, set.films, set.films)) { nav.push(VideoRoute.SetPage(set.id)) }
                            }, defaultFocus = first)
                        }
                    }
                }
                if (data.all.isNotEmpty()) {
                    item(key = "all") {
                        Row(Modifier.padding(horizontal = PageGutter).padding(top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle(stringResource(if (kind == Kind.SHOWS) R.string.video_all_shows else R.string.video_all_movies), Modifier.weight(1f))
                            SortControl(sort) { sortName = it.name }
                        }
                    }
                    val grid = data.all.chunked(GridColumns)
                    grid.forEachIndexed { index, row ->
                        item(key = "grid/${row.first().id}") {
                            Row(Modifier.padding(horizontal = PageGutter).padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                row.forEach { title ->
                                    val card = summaryCard(video, nav, kind, title, "all")
                                    PosterCard(card.title, card.picture, card.key, PosterWidth, PosterHeight, subtitle = card.subtitle, art = card.art, isDefault = index == 0 && title === row.first() && data.banner == null && drawn.isEmpty(), onClick = card.onClick)
                                }
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
internal fun SearchPage(video: VideoApi, nav: Navigator) {
    var query by rememberSaveable { mutableStateOf("") }
    var movies by remember { mutableStateOf<List<Summary>>(emptyList()) }
    var shows by remember { mutableStateOf<List<Summary>>(emptyList()) }
    var people by remember { mutableStateOf<List<PersonSummary>>(emptyList()) }
    var searched by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val memory = LocalFocusMemory.current

    LaunchedEffect(query) {
        val text = query.trim()
        if (text.isEmpty()) {
            movies = emptyList()
            shows = emptyList()
            people = emptyList()
            searched = null
            return@LaunchedEffect
        }
        delay(350)
        try {
            failed = false
            movies = video.search(Kind.MOVIES, text)
            shows = video.search(Kind.SHOWS, text)
            people = video.people(text)
            searched = text
        } catch (_: java.io.IOException) {
            failed = true
        }
    }

    LazyColumn(state = rememberLazyListState(), contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)) {
        item(key = "field") {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = TvColors.Text, fontSize = 20.sp),
                cursorBrush = SolidColor(TvColors.Accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                // The Fire keyboard's Next would otherwise go to whatever comes next on the
                // screen, which is the name in the menu. Finishing typing means the first result.
                keyboardActions = KeyboardActions {
                    keyboard?.hide()
                    val first = people.firstOrNull()?.let { "found-people/${it.id}" }
                        ?: movies.firstOrNull()?.let { "found-movies/${it.id}" } ?: shows.firstOrNull()?.let { "found-shows/${it.id}" }
                    if (first == null || !memory.focus(first)) focus.moveFocus(FocusDirection.Down)
                },
                modifier = Modifier
                    .padding(horizontal = PageGutter)
                    .fillMaxWidth()
                    .remembersFocus("search-field", isDefault = true)
                    .onFocusChanged { fieldFocused = it.isFocused }
                    .clip(RoundedCornerShape(8.dp))
                    .background(TvColors.Surface)
                    .border(2.dp, if (fieldFocused) TvColors.Accent else TvColors.Surface, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text(stringResource(R.string.video_search_hint), color = TvColors.Muted, fontSize = 20.sp)
                    inner()
                }
            )
            Spacer(Modifier.height(18.dp))
        }
        when {
            failed -> item(key = "failed") { Text(stringResource(R.string.video_load_failed), color = TvColors.Text, fontSize = 17.sp, modifier = Modifier.padding(horizontal = PageGutter)) }
            searched != null && movies.isEmpty() && shows.isEmpty() && people.isEmpty() -> item(key = "none") { Text(stringResource(R.string.video_search_none, searched ?: ""), color = TvColors.Text, fontSize = 17.sp, modifier = Modifier.padding(horizontal = PageGutter)) }
        }
        if (people.isNotEmpty()) {
            item(key = "people") { PeopleRow(video, nav, people) }
        }
        if (movies.isNotEmpty()) {
            item(key = "movies") { PosterRow(stringResource(R.string.video_movies), movies.map { summaryCard(video, nav, Kind.MOVIES, it, "found-movies") }) }
        }
        if (shows.isNotEmpty()) {
            item(key = "shows") { PosterRow(stringResource(R.string.video_shows), shows.map { summaryCard(video, nav, Kind.SHOWS, it, "found-shows") }) }
        }
    }
    RestoreFocus(true)
}
