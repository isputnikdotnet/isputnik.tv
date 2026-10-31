package net.isputnik.tv.core

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

@Composable
private fun MenuItem(
    label: String,
    @DrawableRes icon: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val tint = when {
        focused -> TvColors.OnAccent
        selected -> TvColors.Accent
        else -> TvColors.Muted
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .onFocusChanged { focused = it.hasFocus }
                .clip(RoundedCornerShape(8.dp))
                .background(if (focused) TvColors.Accent else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(7.dp))
                Text(label, color = tint, fontSize = 14.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.width(44.dp).height(2.dp).background(if (selected) TvColors.Accent else Color.Transparent))
    }
}

/** The owner's picture: logo and name on the left, the sections in the middle, the person on
 *  the right. Every screen with a menu entry wears it; a title page or the player doesn't. */
@Composable
fun TopBar(
    @DrawableRes logo: Int,
    appName: String,
    destinations: List<Destination>,
    current: Route,
    personName: String,
    onSelect: (Route) -> Unit,
    onPerson: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 28.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Launcher art keeps to a safe zone in the middle of its canvas, so it's drawn larger here.
        Image(painterResource(logo), contentDescription = null, modifier = Modifier.size(40.dp).scale(1.45f))
        Spacer(Modifier.width(8.dp))
        Text(appName, color = TvColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
            destinations.forEach { destination ->
                MenuItem(stringResource(destination.label), destination.icon, destination.route == current) { onSelect(destination.route) }
                Spacer(Modifier.width(6.dp))
            }
        }
        var focused by remember { mutableStateOf(false) }
        Row(
            Modifier
                .onFocusChanged { focused = it.hasFocus }
                .clip(RoundedCornerShape(8.dp))
                .background(if (focused) TvColors.Accent else Color.Transparent)
                .clickable(onClick = onPerson)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(TvColors.SurfaceRaised), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_person), contentDescription = null, tint = TvColors.Text, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(personName, color = if (focused) TvColors.OnAccent else TvColors.Text, fontSize = 14.sp, maxLines = 1)
        }
    }
}
