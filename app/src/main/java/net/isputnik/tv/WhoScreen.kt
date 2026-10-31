package net.isputnik.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import net.isputnik.tv.core.Person
import net.isputnik.tv.core.TvColors
import net.isputnik.tv.core.requestWhenReady
import net.isputnik.tv.core.R as CoreR

/** One person on the row of who can watch. Also used for adding someone. */
@Composable
internal fun PersonTile(name: String, icon: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .width(120.dp)
            .onFocusChanged { focused = it.hasFocus }
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(TvColors.SurfaceRaised)
                .border(3.dp, if (focused) TvColors.Accent else if (selected) TvColors.Muted else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = TvColors.Text, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(name, color = if (focused) TvColors.Accent else TvColors.Text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Who's watching, when a TV has several people on it. Anyone holding the remote can be any of
 *  them: a shared TV is shared on purpose, and the guide says so. */
@Composable
internal fun WhoScreen(people: List<Person>, onChoose: (String) -> Unit, onAdd: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestWhenReady() }
    Centered {
        Text(stringResource(R.string.who_title), color = TvColors.Text, fontSize = 30.sp)
        Spacer(Modifier.height(28.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
            items(people, key = { it.id }) { person ->
                PersonTile(person.name, CoreR.drawable.ic_person, false, if (person === people.first()) Modifier.focusRequester(first) else Modifier) { onChoose(person.id) }
            }
            item(key = "add") { PersonTile(stringResource(R.string.who_add), R.drawable.ic_add, false, onClick = onAdd) }
        }
    }
}
