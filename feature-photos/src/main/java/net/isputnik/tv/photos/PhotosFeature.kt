package net.isputnik.tv.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import net.isputnik.tv.core.ApiClient
import net.isputnik.tv.core.Destination
import net.isputnik.tv.core.Feature
import net.isputnik.tv.core.Navigator
import net.isputnik.tv.core.PageMessage
import net.isputnik.tv.core.PosterCard
import net.isputnik.tv.core.RestoreFocus
import net.isputnik.tv.core.Route
import net.isputnik.tv.core.TvButton
import net.isputnik.tv.core.TvColors
import net.isputnik.tv.core.remembersFocus
import net.isputnik.tv.core.R as CoreR

sealed interface PhotosRoute : Route {
    data object Home : PhotosRoute { override val key = "photos" }
    /** Watching one pick; it carries the pick as the list said it, so the viewer needn't ask. */
    data class View(val item: TvPhotoItem) : PhotosRoute { override val key = "photos/view/${item.pickId}" }
}

/**
 * Photos on the TV (home's docs/tv-photos-plan.md): only the slideshows and albums an admin put
 * on the TV for the profile watching, never the gallery itself. The shell shows it only when
 * that list has something on it ([PhotosApi.available]); home keeps the TV to what's listed.
 */
class PhotosFeature(api: ApiClient) : Feature {
    private val photos = PhotosApi(api)

    override val destinations = listOf(Destination(PhotosRoute.Home, R.string.menu_photos, CoreR.drawable.ic_photo))

    override fun owns(route: Route) = route is PhotosRoute

    @Composable
    override fun Content(route: Route, nav: Navigator) {
        when (val page = route as PhotosRoute) {
            PhotosRoute.Home -> PhotosPage(photos, nav)
            is PhotosRoute.View -> PhotoViewer(photos, page.item)
        }
    }
}

private val CardWidth = 232.dp
private val CardHeight = 150.dp
private val Gutter = 32.dp

/** The banner (the list's first pick, to start it at once), then a row of slideshows and a row
 *  of albums, each in the list's order. */
@Composable
private fun PhotosPage(photos: PhotosApi, nav: Navigator) {
    var items by remember { mutableStateOf<List<TvPhotoItem>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        failed = false
        runCatching { photos.list() }.onSuccess { items = it }.onFailure { failed = true }
    }
    val list = items
    when {
        list == null && failed -> PageMessage(stringResource(R.string.photos_load_failed), stringResource(R.string.photos_retry)) { attempt++ }
        list == null -> PageMessage(stringResource(R.string.photos_loading))
        list.isEmpty() -> PageMessage(stringResource(R.string.photos_empty))
        else -> {
            val slideshows = list.filter { it.isSlideshow }
            val albums = list.filterNot { it.isSlideshow }
            LazyColumn(state = rememberLazyListState(), contentPadding = PaddingValues(top = 6.dp, bottom = 28.dp)) {
                item(key = "banner") {
                    Box(Modifier.padding(horizontal = Gutter).padding(bottom = 18.dp)) { Banner(photos, list.first()) { nav.push(PhotosRoute.View(it)) } }
                }
                if (slideshows.isNotEmpty()) item(key = "slideshows") { PickRow(photos, stringResource(R.string.photos_row_slideshows), slideshows) { nav.push(PhotosRoute.View(it)) } }
                if (albums.isNotEmpty()) item(key = "albums") { PickRow(photos, stringResource(R.string.photos_row_albums), albums) { nav.push(PhotosRoute.View(it)) } }
            }
            RestoreFocus(true)
        }
    }
}

/** "142 photos · 9 min": how long a slideshow runs is only known for one. */
@Composable
private fun aboutLine(item: TvPhotoItem): String {
    val count = pluralStringResource(R.plurals.photos_count, item.itemCount, item.itemCount)
    if (!item.isSlideshow) return count
    val minutes = Math.round(item.itemCount * secondsFor(item, null) / 60.0).toInt()
    return if (minutes >= 1) "$count · ${stringResource(R.string.photos_minutes, minutes)}" else count
}

@Composable
private fun Banner(photos: PhotosApi, item: TvPhotoItem, onPlay: (TvPhotoItem) -> Unit) {
    Row(Modifier.fillMaxWidth().height(190.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 250.dp, height = 166.dp).clip(RoundedCornerShape(8.dp)).background(TvColors.SurfaceRaised)) {
            item.coverUrl?.let { AsyncImage(model = photos.absolute(it), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
        }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (item.isSlideshow) R.string.photos_banner_slideshow else R.string.photos_banner_album).uppercase(),
                color = TvColors.Muted,
                fontSize = 11.sp,
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Medium
            )
            Box(Modifier.padding(top = 6.dp, bottom = 8.dp).size(width = 26.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(TvColors.Accent))
            Text(item.name, color = TvColors.Text, fontSize = 34.sp, fontFamily = FontFamily.Serif, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.85f))
            Text(aboutLine(item), color = TvColors.Muted, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(14.dp))
            TvButton(
                stringResource(R.string.photos_play),
                Modifier.remembersFocus("banner-play", isDefault = true),
                primary = true,
                icon = CoreR.drawable.ic_play
            ) { onPlay(item) }
        }
    }
}

@Composable
private fun PickRow(photos: PhotosApi, title: String, items: List<TvPhotoItem>, onOpen: (TvPhotoItem) -> Unit) {
    Column {
        Text(title, color = TvColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(start = Gutter, bottom = 8.dp))
        LazyRow(state = rememberLazyListState(), contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items, key = { it.pickId }) { item ->
                PosterCard(
                    title = item.name,
                    picture = item.coverUrl?.let(photos::absolute),
                    focusKey = "pick/${item.pickId}",
                    width = CardWidth,
                    height = CardHeight,
                    subtitle = aboutLine(item),
                    onClick = { onOpen(item) }
                )
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}
