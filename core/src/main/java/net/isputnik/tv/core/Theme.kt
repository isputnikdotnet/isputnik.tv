package net.isputnik.tv.core

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import coil3.compose.AsyncImage

/** One theme on a TV. Always dark, whatever the website wears: a white screen is hard on a dark room. */
data class TvPalette(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val accent: Color,
    val onAccent: Color,
    val text: Color,
    val muted: Color
)

/** The look of the owner's picture in docs/tv-app-plan.md: dark navy, one teal accent. Also what
 *  a screen wears when it's nobody's in particular: connecting, linking, who's watching. */
val OrbitPalette = TvPalette(
    background = Color(0xFF0A1420),
    surface = Color(0xFF142334),
    surfaceRaised = Color(0xFF1E3145),
    accent = Color(0xFF16C7C7),
    onAccent = Color(0xFF04171A),
    text = Color(0xFFF2F6FA),
    muted = Color(0xFF93A3B5)
)

/** Home's tokens.css as it stands. */
private val DarkPalette = TvPalette(
    background = Color(0xFF020B0E),
    surface = Color(0xFF07191D),
    surfaceRaised = Color(0xFF0D2528),
    accent = Color(0xFFD14B2D),
    onAccent = Color(0xFF020B0E),
    text = Color(0xFFF2D9AD),
    muted = Color(0xFFB78F5D)
)

/** Home's tokens.css as it stands. */
private val PlainDarkPalette = TvPalette(
    background = Color(0xFF04080C),
    surface = Color(0xFF0C141A),
    surfaceRaised = Color(0xFF141F27),
    accent = Color(0xFF45BED2),
    onAccent = Color(0xFF04080C),
    text = Color(0xFFEDF2F4),
    muted = Color(0xFF9FA9AD)
)

/** Home's tokens.css as it stands. */
private val ThanksgivingPalette = TvPalette(
    background = Color(0xFF170B06),
    surface = Color(0xFF27140B),
    surfaceRaised = Color(0xFF351D10),
    accent = Color(0xFFF0A23C),
    onAccent = Color(0xFF170B06),
    text = Color(0xFFF7EAD9),
    muted = Color(0xFFC7A98E)
)

/** Light's sage green, at night. */
private val LightPalette = TvPalette(
    background = Color(0xFF101C1B),
    surface = Color(0xFF192927),
    surfaceRaised = Color(0xFF233633),
    accent = Color(0xFF6FB3A8),
    onAccent = Color(0xFF0B1716),
    text = Color(0xFFF1EFE3),
    muted = Color(0xFFA8B0A3)
)

/** Plain Light's blue, at night. */
private val PlainLightPalette = TvPalette(
    background = Color(0xFF0F172A),
    surface = Color(0xFF1A2438),
    surfaceRaised = Color(0xFF263349),
    accent = Color(0xFF60A5FA),
    onAccent = Color(0xFF0B1A33),
    text = Color(0xFFF1F5F9),
    muted = Color(0xFF94A3B8)
)

/** Minimalist's greys, at night. The accent stays short of white, or the white ring on a focused
 *  button wouldn't show. */
private val MinimalistPalette = TvPalette(
    background = Color(0xFF161617),
    surface = Color(0xFF222224),
    surfaceRaised = Color(0xFF2F2F32),
    accent = Color(0xFFC4C4BF),
    onAccent = Color(0xFF1D1D1F),
    text = Color(0xFFF2F2EF),
    muted = Color(0xFFA5A5A1)
)

/** A theme name from home's account (THEME_PREFERENCES in its db.ts). "system" follows the device
 *  on the website, and a TV has no such preference. A name this version doesn't know gets the default. */
fun paletteFor(theme: String?): TvPalette = when (theme) {
    "dark" -> DarkPalette
    "light" -> LightPalette
    "plain-dark", "system" -> PlainDarkPalette
    "plain-light" -> PlainLightPalette
    "minimalist" -> MinimalistPalette
    "thanksgiving" -> ThanksgivingPalette
    else -> OrbitPalette
}

private val LocalTvPalette = staticCompositionLocalOf { OrbitPalette }

/** The colours of the theme in force, for use inside [TvTheme]. */
object TvColors {
    val Background: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.surface
    val SurfaceRaised: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.surfaceRaised
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.accent
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.onAccent
    val Text: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.text
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalTvPalette.current.muted
    val Track = Color(0x55FFFFFF)
}

/** `theme` is the name on the account in use; null is the default. */
@Composable
fun TvTheme(theme: String? = null, content: @Composable () -> Unit) {
    val palette = paletteFor(theme)
    CompositionLocalProvider(LocalTvPalette provides palette) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = palette.accent,
                onPrimary = palette.onAccent,
                background = palette.background,
                onBackground = palette.text,
                surface = palette.surface,
                onSurface = palette.text
            ),
            content = content
        )
    }
}

/** A button on the remote. `primary` is the teal one a screen leads with; focus always shows,
 *  as a white ring on teal and as teal on anything else. */
@Composable
fun TvButton(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    @DrawableRes icon: Int? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val filled = primary || focused
    val shape = RoundedCornerShape(8.dp)
    val content = if (filled) TvColors.OnAccent else TvColors.Text
    Box(
        modifier
            .onFocusChanged { focused = it.hasFocus }
            .clip(shape)
            .background(if (filled) TvColors.Accent else TvColors.SurfaceRaised)
            .let { if (primary && focused) it.border(BorderStroke(3.dp, Color.White), shape) else it }
            .clickable(onClick = onClick)
            .height(40.dp)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(label, color = content, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

/** How far through, as the thin bar on a banner or a card. */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    Box(modifier.height(height).clip(RoundedCornerShape(height / 2)).background(TvColors.Track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).background(TvColors.Accent))
    }
}

/** A title's picture with its name under it. With no picture, or one that won't load, the name
 *  stands on the card: a blank card tells nobody anything. */
@Composable
fun PosterCard(
    title: String,
    picture: String?,
    focusKey: String,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    progress: Float? = null,
    isDefault: Boolean = false,
    onClick: () -> Unit
) {
    var failed by remember(picture) { mutableStateOf(false) }
    Column(modifier.width(width)) {
        Card(
            onClick = onClick,
            scale = CardDefaults.scale(focusedScale = 1.05f),
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, TvColors.Accent))),
            modifier = Modifier.size(width, height).remembersFocus(focusKey, isDefault)
        ) {
            Box(Modifier.fillMaxSize().background(TvColors.Surface)) {
                if (picture == null || failed) {
                    Text(
                        title,
                        color = TvColors.Muted,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.Center).padding(12.dp)
                    )
                }
                if (picture != null && !failed) {
                    AsyncImage(
                        model = picture,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onError = { failed = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                progress?.let { ProgressLine(it, Modifier.align(Alignment.BottomStart).fillMaxWidth()) }
            }
        }
        Text(title, color = TvColors.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        if (subtitle != null) {
            Text(subtitle, color = TvColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Something to say instead of a page, with a way to try again. */
@Composable
fun PageMessage(text: String, retryLabel: String? = null, onRetry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = TvColors.Text, fontSize = 19.sp, textAlign = TextAlign.Center)
        if (retryLabel != null && onRetry != null) {
            Spacer(Modifier.height(20.dp))
            TvButton(retryLabel, Modifier.remembersFocus("retry", isDefault = true), onClick = onRetry)
            RestoreFocus(true)
        }
    }
}
