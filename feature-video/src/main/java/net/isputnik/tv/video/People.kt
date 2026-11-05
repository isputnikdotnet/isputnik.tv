package net.isputnik.tv.video

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import net.isputnik.tv.core.Navigator
import net.isputnik.tv.core.PageMessage
import net.isputnik.tv.core.PosterArt
import net.isputnik.tv.core.RestoreFocus
import net.isputnik.tv.core.TvColors
import net.isputnik.tv.core.remembersFocus

// Cast and crew (home's docs/video-people-plan.md): round faces a remote can move along, each
// opening the person's page — everything of theirs in the library this person may watch.

/** The jobs the engine names, in the order a title's crew is shown. */
private val JOB_ORDER = listOf("creator", "director", "writer", "composer")

@Composable
internal fun jobLabel(job: String): String = when (job) {
    "director" -> stringResource(R.string.video_job_director)
    "writer" -> stringResource(R.string.video_job_writer)
    "composer" -> stringResource(R.string.video_job_composer)
    "creator" -> stringResource(R.string.video_job_creator)
    else -> job
}

/** A face, its name, and a line under it; the initials stand in for a missing picture. */
@Composable
internal fun PersonCard(name: String, photo: String?, sub: String?, focusKey: String, size: Dp = 112.dp, isDefault: Boolean = false, onClick: () -> Unit) {
    var failed by remember(photo) { mutableStateOf(false) }
    Column(Modifier.width(size + 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            onClick = onClick,
            shape = CardDefaults.shape(CircleShape),
            scale = CardDefaults.scale(focusedScale = 1.08f),
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, TvColors.Accent), shape = CircleShape)),
            modifier = Modifier.size(size).remembersFocus(focusKey, isDefault)
        ) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(TvColors.Surface), contentAlignment = Alignment.Center) {
                if (photo != null && !failed) {
                    AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, onError = { failed = true }, modifier = Modifier.fillMaxSize())
                } else {
                    Text(initials(name), color = TvColors.Muted, fontSize = (size.value / 3).sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text(name, color = TvColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        sub?.let { Text(it, color = TvColors.Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
    }
}

/** "Михаил Боярский" → "МБ". */
internal fun initials(name: String): String =
    name.split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }

/** A title's cast in billing order, then its crew by job: one row, so the remote reaches them all. */
@Composable
internal fun CastAndCrewRow(video: VideoApi, nav: Navigator, cast: List<Credit>, crew: List<Credit>) {
    val ordered = crew.sortedBy { JOB_ORDER.indexOf(it.role).let { at -> if (at < 0) JOB_ORDER.size else at } }
    if (cast.isEmpty() && ordered.isEmpty()) return
    Column(Modifier.padding(bottom = 18.dp)) {
        Text(stringResource(R.string.video_cast_crew), color = TvColors.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = PageGutter).padding(bottom = 10.dp))
        LazyRow(state = rememberLazyListState(), contentPadding = PaddingValues(horizontal = PageGutter), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(cast, key = { "cast/${it.id}" }) { credit ->
                PersonCard(credit.name, credit.photo?.let(video::absolute), credit.role, "cast/${credit.id}") { nav.push(VideoRoute.PersonPage(credit.id)) }
            }
            items(ordered, key = { "crew/${it.id}/${it.role}" }) { credit ->
                PersonCard(credit.name, credit.photo?.let(video::absolute), credit.role?.let { jobLabel(it) }, "crew/${credit.id}/${credit.role}") { nav.push(VideoRoute.PersonPage(credit.id)) }
            }
        }
    }
}

/** People a search found, above the titles. */
@Composable
internal fun PeopleRow(video: VideoApi, nav: Navigator, people: List<PersonSummary>) {
    if (people.isEmpty()) return
    Column(Modifier.padding(bottom = 18.dp)) {
        Text(stringResource(R.string.video_people), color = TvColors.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = PageGutter).padding(bottom = 10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = PageGutter), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(people, key = { it.id }) { person ->
                PersonCard(
                    person.name,
                    person.photo?.let(video::absolute),
                    pluralStringResource(R.plurals.video_person_titles, person.titles, person.titles),
                    "found-people/${person.id}"
                ) { nav.push(VideoRoute.PersonPage(person.id)) }
            }
        }
    }
}

/** What someone did in a title: the parts played, else the jobs. */
@Composable
private fun roleIn(title: PersonTitle): String? {
    val parts = title.characters + title.jobs.map { jobLabel(it) }
    return listOfNotNull(title.year?.toString(), parts.joinToString(", ").ifEmpty { null }).joinToString(" · ").ifEmpty { null }
}

/** One person: their face and name over their films and shows, newest first. */
@Composable
internal fun PersonPage(video: VideoApi, nav: Navigator, id: String) {
    var person by remember(id) { mutableStateOf<Person?>(null) }
    var failed by remember(id) { mutableStateOf(false) }
    var attempt by remember(id) { mutableIntStateOf(0) }
    LaunchedEffect(id, attempt) {
        failed = false
        try {
            person = video.person(id)
        } catch (_: java.io.IOException) {
            failed = true
        } catch (_: kotlinx.serialization.SerializationException) {
            failed = true
        }
    }
    val shown = person
    when {
        shown == null && failed -> PageMessage(stringResource(R.string.video_load_failed), stringResource(R.string.video_retry)) { attempt++ }
        shown == null -> PageMessage(stringResource(R.string.video_loading))
        else -> {
            val films = shown.titles.filter { it.kind != "shows" }
            val shows = shown.titles.filter { it.kind == "shows" }
            LazyColumn(state = rememberLazyListState(), contentPadding = PaddingValues(top = 44.dp, bottom = 28.dp)) {
                item(key = "head") {
                    Row(Modifier.padding(horizontal = PageGutter).padding(bottom = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(132.dp).clip(CircleShape).background(TvColors.Surface), contentAlignment = Alignment.Center) {
                            val photo = shown.photo
                            if (photo != null) AsyncImage(model = video.absolute(photo), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Text(initials(shown.name), color = TvColors.Muted, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.width(24.dp))
                        Column {
                            Text(shown.name, color = TvColors.Text, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.7f))
                            Text(pluralStringResource(R.plurals.video_person_titles, shown.titles.size, shown.titles.size), color = TvColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                if (films.isNotEmpty()) {
                    item(key = "films") {
                        PosterRow(stringResource(R.string.video_movies), films.map { title ->
                            RowCard("film/${title.id}", title.title, title.poster?.let(video::absolute), roleIn(title), art = PosterArt.MOVIE) { nav.push(VideoRoute.MoviePage(title.id)) }
                        }, defaultFocus = true)
                    }
                }
                if (shows.isNotEmpty()) {
                    item(key = "shows") {
                        PosterRow(stringResource(R.string.video_shows), shows.map { title ->
                            RowCard("show/${title.id}", title.title, title.poster?.let(video::absolute), roleIn(title), art = PosterArt.SHOW) { nav.push(VideoRoute.ShowPage(title.id)) }
                        }, defaultFocus = films.isEmpty())
                    }
                }
            }
            RestoreFocus(true)
        }
    }
}
