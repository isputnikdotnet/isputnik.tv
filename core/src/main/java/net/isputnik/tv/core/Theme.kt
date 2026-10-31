package net.isputnik.tv.core

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme

/** The look of the owner's picture in docs/tv-app-plan.md: dark navy, one teal accent. */
object TvColors {
    val Background = Color(0xFF0A1420)
    val Surface = Color(0xFF142334)
    val Accent = Color(0xFF16C7C7)
    val OnAccent = Color(0xFF04171A)
    val Text = Color(0xFFF2F6FA)
    val Muted = Color(0xFF93A3B5)
}

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TvColors.Accent,
            onPrimary = TvColors.OnAccent,
            background = TvColors.Background,
            onBackground = TvColors.Text,
            surface = TvColors.Surface,
            onSurface = TvColors.Text
        ),
        content = content
    )
}

/** A button on the remote: focus is what shows, in teal. */
@Composable
fun TvButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .onFocusChanged { focused = it.hasFocus }
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) TvColors.Accent else TvColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(label, color = if (focused) TvColors.OnAccent else TvColors.Text, fontSize = 16.sp)
    }
}
