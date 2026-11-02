package net.isputnik.tv.core

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/** The picture behind the whole screen, menu included: a page asks for one with
 *  [ShowsBackdrop] while it is on screen, and the shell draws it under everything. */
class PageBackdrop {
    var url by mutableStateOf<String?>(null)
}

val LocalPageBackdrop = staticCompositionLocalOf<PageBackdrop?> { null }

/** While this is composed, the screen behind the page is `url` (none when null). */
@Composable
fun ShowsBackdrop(url: String?) {
    val backdrop = LocalPageBackdrop.current ?: return
    DisposableEffect(backdrop, url) {
        backdrop.url = url
        onDispose { if (backdrop.url == url) backdrop.url = null }
    }
}

/** The backdrop drawn full-screen, darkened towards the bottom and the left so the
 *  menu, the words and the posters over it stay the loudest things on screen. */
@Composable
fun BackdropLayer(url: String?) {
    val base = TvColors.Background
    Crossfade(url, label = "backdrop") { picture ->
        Box(Modifier.fillMaxSize()) {
            if (picture != null) {
                AsyncImage(model = picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to base.copy(alpha = 0.88f), 0.45f to base.copy(alpha = 0.45f), 1f to base.copy(alpha = 0.1f))))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to base.copy(alpha = 0.5f), 0.15f to base.copy(alpha = 0.15f), 0.42f to base.copy(alpha = 0.3f), 0.72f to base)))
            }
        }
    }
}
