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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
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
import net.isputnik.tv.core.PosterCard
import net.isputnik.tv.core.ProgressLine
import net.isputnik.tv.core.RestoreFocus
import net.isputnik.tv.core.TvButton
import net.isputnik.tv.core.TvColors
import net.isputnik.tv.core.remembersFocus
import net.isputnik.tv.core.R as CoreR

internal val PosterWidth = 136.dp
internal val PosterHeight = 190.dp
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

/** The owner's banner: the title in wide letters, a line about it, how far you got, two buttons. */
@Composable
private fun Banner(video: VideoApi, banner: BannerModel, nav: Navigator) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(shape)
            .background(TvColors.Surface)
            .border(1.dp, Color(0x33FFFFFF), shape)
    ) {
        banner.backdrop?.let {
            AsyncImage(model = video.absolute(it), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to TvColors.Background.copy(alpha = 0.95f), 0.55f to TvColors.Background.copy(alpha = 0.65f), 1f to TvColors.Background.copy(alpha = 0f))))
        Column(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 16.dp), verticalArrangement = Arrangement.Center) {
            Text(
                banner.title.uppercase(),
                color = TvColors.Text,
                fontSize = 30.sp,
                letterSpacing = 6.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.72f)
            )
            banner.line?.let {
                Text(it, color = TvColors.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp).fillMaxWidth(0.62f))
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
                    isDefault = defaultFocus && card === cards.first(),
                    onClick = card.onClick
                )
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

private fun summaryCard(video: VideoApi, nav: Navigator, kind: Kind, item: Summary, keyPrefix: String) = RowCard(
    key = "$keyPrefix/${item.id}",
    title = item.title,
    picture = item.poster?.let(video::absolute),
    subtitle = item.year?.toString(),
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
internal fun HomePage(video: VideoApi, store: VideoStore, nav: Navigator) {
    val (failed, retry) = rememberLoad(Unit) { store.refreshHome() }
    val data = store.home
    when {
        store.available == false -> PageMessage(stringResource(R.string.video_unavailable), stringResource(R.string.video_retry), retry)
        data == null && failed -> PageMessage(stringResource(R.string.video_load_failed), stringResource(R.string.video_retry), retry)
        data == null -> PageMessage(stringResource(R.string.video_loading))
        data.resume.isEmpty() && data.movies.isEmpty() && data.shows.isEmpty() -> PageMessage(stringResource(R.string.video_empty))
        else -> {
            LazyColumn(state = rememberLazyListState(), contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)) {
                data.banner?.let { banner ->
                    item(key = "banner") {
                        Box(Modifier.padding(horizontal = PageGutter).padding(bottom = 18.dp)) { Banner(video, banner, nav) }
                    }
                }
                if (data.resume.isNotEmpty()) {
                    item(key = "resume") {
                        PosterRow(stringResource(R.string.video_continue), data.resume.map { item ->
                            RowCard(
                                key = "resume/${item.id}",
                                title = item.show?.title ?: item.title,
                                picture = video.posterOf(item),
                                subtitle = if (item.show != null) episodeLabel(item) else null,
                                progress = if (item.resume) fractionOf(item.position, item.duration) else null,
                                onClick = { nav.push(VideoRoute.Play(item.id)) }
                            )
                        }, defaultFocus = data.banner == null)
                    }
                }
                if (data.movies.isNotEmpty()) {
                    item(key = "movies") { PosterRow(stringResource(R.string.video_recent_movies), data.movies.take(30).map { summaryCard(video, nav, Kind.MOVIES, it, "movies") }, defaultFocus = data.banner == null && data.resume.isEmpty()) }
                }
                if (data.shows.isNotEmpty()) {
                    item(key = "shows") { PosterRow(stringResource(R.string.video_recent_shows), data.shows.take(30).map { summaryCard(video, nav, Kind.SHOWS, it, "shows") }, defaultFocus = data.banner == null && data.resume.isEmpty() && data.movies.isEmpty()) }
                }
            }
            RestoreFocus(true)
        }
    }
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

/** Movies or Shows, as in the owner's picture: heading and sort, the banner, then everything. */
@Composable
internal fun LibraryPage(video: VideoApi, store: VideoStore, nav: Navigator, kind: Kind) {
    var sortName by rememberSaveable(kind) { mutableStateOf(Sort.ADDED.name) }
    val sort = Sort.valueOf(sortName)
    val (failed, retry) = rememberLoad(kind to sort) { store.refreshLibrary(kind, sort) }
    // The old order stays up until the new one arrives: if the page went away, so would focus.
    val shown = remember(kind) { arrayOfNulls<LibraryData>(1) }
    store.library(kind, sort)?.let { shown[0] = it }
    val data = shown[0]
    when {
        data == null && failed -> PageMessage(stringResource(R.string.video_load_failed), stringResource(R.string.video_retry), retry)
        data == null -> PageMessage(stringResource(R.string.video_loading))
        data.items.isEmpty() -> PageMessage(stringResource(R.string.video_empty))
        else -> {
            LazyColumn(state = rememberLazyListState(), contentPadding = PaddingValues(top = 2.dp, bottom = 28.dp)) {
                item(key = "head") {
                    Row(Modifier.padding(horizontal = PageGutter).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(if (kind == Kind.SHOWS) R.string.video_heading_shows else R.string.video_heading_movies), color = TvColors.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(stringResource(R.string.video_private_collection), color = TvColors.Muted, fontSize = 13.sp, maxLines = 1)
                        }
                        SortControl(sort) { sortName = it.name }
                    }
                }
                data.banner?.let { banner ->
                    item(key = "banner") {
                        Box(Modifier.padding(horizontal = PageGutter).padding(bottom = 16.dp)) { Banner(video, banner, nav) }
                    }
                }
                if (data.sets.isNotEmpty()) {
                    item(key = "sets") {
                        PosterRow(stringResource(R.string.video_sets), data.sets.map { set ->
                            RowCard("set/${set.id}", set.name, set.poster?.let(video::absolute), pluralStringResource(R.plurals.video_set_films, set.films, set.films)) { nav.push(VideoRoute.SetPage(set.id)) }
                        }, defaultFocus = data.banner == null)
                    }
                }
                item(key = "all") { SectionTitle(stringResource(if (kind == Kind.SHOWS) R.string.video_all_shows else R.string.video_all_movies), Modifier.padding(start = PageGutter)) }
                val rows = data.items.chunked(GridColumns)
                rows.forEachIndexed { index, row ->
                    item(key = "row/${row.first().id}") {
                        Row(Modifier.padding(horizontal = PageGutter).padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            row.forEach { title ->
                                val card = summaryCard(video, nav, kind, title, "all")
                                PosterCard(card.title, card.picture, card.key, PosterWidth, PosterHeight, subtitle = card.subtitle, isDefault = index == 0 && title === row.first() && data.banner == null && data.sets.isEmpty(), onClick = card.onClick)
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
            searched = null
            return@LaunchedEffect
        }
        delay(350)
        try {
            failed = false
            movies = video.search(Kind.MOVIES, text)
            shows = video.search(Kind.SHOWS, text)
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
                    val first = movies.firstOrNull()?.let { "found-movies/${it.id}" } ?: shows.firstOrNull()?.let { "found-shows/${it.id}" }
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
            searched != null && movies.isEmpty() && shows.isEmpty() -> item(key = "none") { Text(stringResource(R.string.video_search_none, searched ?: ""), color = TvColors.Text, fontSize = 17.sp, modifier = Modifier.padding(horizontal = PageGutter)) }
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
