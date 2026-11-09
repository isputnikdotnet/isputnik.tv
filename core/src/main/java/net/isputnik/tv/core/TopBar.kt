package net.isputnik.tv.core

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import androidx.compose.foundation.layout.IntrinsicSize

/** A section in the middle of the bar: icon and name, an accent line under the one you're in. */
@Composable
private fun SectionItem(label: String, @DrawableRes icon: Int, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val tint = when {
        focused -> TvColors.OnAccent
        selected -> TvColors.Accent
        else -> TvColors.Muted
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .onFocusChanged { focused = it.hasFocus }
                .clip(RoundedCornerShape(8.dp))
                .background(if (focused) TvColors.Accent else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = tint, fontSize = 16.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.width(52.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(if (selected) TvColors.Accent else Color.Transparent))
    }
}

/** A round button on the right of the bar. [dot] marks something new (an update). */
@Composable
private fun RoundButton(
    description: String,
    modifier: Modifier = Modifier,
    background: Color = TvColors.SurfaceRaised,
    dot: Boolean = false,
    onClick: () -> Unit,
    content: @Composable (tint: Color) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier.size(36.dp)) {
        Box(
            Modifier
                .size(36.dp)
                .onFocusChanged { focused = it.hasFocus }
                .clip(CircleShape)
                .background(if (focused) TvColors.Accent else background)
                .border(2.dp, if (focused) TvColors.Text else Color.Transparent, CircleShape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            content(if (focused) TvColors.OnAccent else TvColors.Text)
        }
        if (dot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(TvColors.Accent)
                    .border(2.dp, TvColors.Background, CircleShape)
            )
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.width(1.dp).height(24.dp).background(TvColors.Muted.copy(alpha = 0.3f)))
}

/** One line of the settings menu. A [locked] one needs the household PIN first (a restricted
 *  profile); it says so, and the PIN pad opens when it is chosen. */
@Composable
fun MenuEntry(
    label: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    lockedLabel: String = "PIN",
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    // Acts only on a press that started here: the menu opens on the gear's press, and that same
    // press's release must not choose whatever the menu focused first (it chose Exit app once).
    var pressed by remember { mutableStateOf(false) }
    val tint = if (focused) TvColors.OnAccent else if (locked) TvColors.Muted else TvColors.Text
    Row(
        modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.hasFocus
                if (!it.hasFocus) pressed = false
            }
            .onPreviewKeyEvent { event ->
                if (event.key != Key.DirectionCenter && event.key != Key.Enter && event.key != Key.NumPadEnter) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> pressed = true
                    KeyEventType.KeyUp -> {
                        if (pressed) onClick()
                        pressed = false
                    }
                }
                true
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) TvColors.Accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(if (locked) R.drawable.ic_lock else icon), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = tint, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (locked) {
            Spacer(Modifier.width(10.dp))
            Text(lockedLabel, color = tint, fontSize = 11.sp, maxLines = 1)
        }
    }
}

/** A thin line between groups of the settings menu. */
@Composable
fun MenuDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).height(1.dp).background(TvColors.Muted.copy(alpha = 0.2f)))
}

/** What the profile button shows: the worn profile's letter on its colour, else the person icon. */
data class Avatar(val letter: String, val color: Color)

/**
 * The owner's mock-up (2026-11-09): logo and name on the left; the sections in the middle;
 * on the right round buttons for an update (only when one is out), search, settings (a small
 * menu, [menu]) and who's watching. Every screen with a menu entry wears it; a title page
 * or the player doesn't.
 */
@Composable
fun TopBar(
    @DrawableRes logo: Int,
    appName: String,
    destinations: List<Destination>,
    current: Route,
    onSelect: (Route) -> Unit,
    updateLabel: String?,
    onUpdate: () -> Unit,
    searchLabel: String,
    onSearch: (() -> Unit)?,
    settingsLabel: String,
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
    profileLabel: String,
    avatar: Avatar?,
    onProfile: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var menuClosed by remember { mutableIntStateOf(0) }
    val gear = remember { FocusRequester() }
    // A closing popup hands focus to nobody in particular: bring it back to the gear.
    LaunchedEffect(menuClosed) {
        if (menuClosed > 0) {
            withFrameNanos { }
            runCatching { gear.requestFocus() }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Launcher art keeps to a safe zone in the middle of its canvas, so it's drawn larger here.
        Image(painterResource(logo), contentDescription = null, modifier = Modifier.size(36.dp).scale(1.15f))
        Spacer(Modifier.width(8.dp))
        Text(appName, color = TvColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.width(16.dp))
        Divider()
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
            destinations.forEach { destination ->
                SectionItem(androidx.compose.ui.res.stringResource(destination.label), destination.icon, destination.route == current) { onSelect(destination.route) }
                Spacer(Modifier.width(10.dp))
            }
        }
        Divider()
        Spacer(Modifier.width(14.dp))
        if (updateLabel != null) {
            RoundButton(updateLabel, dot = true, onClick = onUpdate) { tint ->
                Icon(painterResource(R.drawable.ic_update), contentDescription = null, tint = if (tint == TvColors.Text) TvColors.Accent else tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
        }
        if (onSearch != null) {
            RoundButton(searchLabel, onClick = onSearch) { tint ->
                Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
        }
        Box {
            RoundButton(settingsLabel, Modifier.focusRequester(gear), onClick = { menuOpen = true }) { tint ->
                Icon(painterResource(R.drawable.ic_settings), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            if (menuOpen) {
                val close: () -> Unit = {
                    menuOpen = false
                    menuClosed++
                }
                Popup(
                    alignment = Alignment.TopEnd,
                    offset = IntOffset(0, 120),
                    onDismissRequest = close,
                    properties = PopupProperties(focusable = true)
                ) {
                    // Focus starts on the first line (Refresh), whatever the popup would pick.
                    val first = remember { FocusRequester() }
                    LaunchedEffect(Unit) {
                        withFrameNanos { }
                        runCatching { first.requestFocus() }
                    }
                    Column(
                        Modifier
                            .focusRequester(first)
                            .width(IntrinsicSize.Max)
                            .clip(RoundedCornerShape(10.dp))
                            .background(TvColors.Surface)
                            .border(1.dp, TvColors.Muted.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) { menu(close) }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        RoundButton(profileLabel, background = avatar?.color ?: TvColors.SurfaceRaised, onClick = onProfile) { tint ->
            if (avatar != null) {
                Text(avatar.letter, color = if (tint == TvColors.Text) Color.White else tint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Icon(painterResource(R.drawable.ic_person), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
        }
    }
}
