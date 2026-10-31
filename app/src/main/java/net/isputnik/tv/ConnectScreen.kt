package net.isputnik.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import net.isputnik.tv.core.ApiClient
import net.isputnik.tv.core.ApiException
import net.isputnik.tv.core.ServerAddress
import net.isputnik.tv.core.TvButton
import net.isputnik.tv.core.TvColors
import net.isputnik.tv.core.currentUser

/** The one thing a new TV can't work out: where home is. Asked once and remembered. */
@Composable
internal fun ConnectScreen(api: ApiClient, onConnected: () -> Unit) {
    var address by remember { mutableStateOf(api.server.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var fieldFocused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun connect() {
        if (busy) return
        val normalized = ServerAddress.normalize(address)
        if (normalized == null) {
            error = R.string.connect_invalid
            return
        }
        busy = true
        error = null
        scope.launch {
            val previous = api.server
            api.useServer(normalized)
            try {
                // Anything but home's own answer means this isn't home: asking who's signed in
                // answers with JSON there, and rarely anywhere else.
                api.currentUser()
                onConnected()
            } catch (e: ApiException) {
                api.useServer(previous)
                error = R.string.connect_not_isputnik
            } catch (e: kotlinx.serialization.SerializationException) {
                api.useServer(previous)
                error = R.string.connect_not_isputnik
            } catch (e: Exception) {
                api.useServer(previous)
                error = R.string.connect_unreachable
            }
            busy = false
        }
    }

    Centered {
        Text(stringResource(R.string.connect_title), color = TvColors.Text, fontSize = 30.sp)
        Text(stringResource(R.string.connect_hint), color = TvColors.Muted, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        BasicTextField(
            value = address,
            onValueChange = { address = it; error = null },
            singleLine = true,
            textStyle = TextStyle(color = TvColors.Text, fontSize = 22.sp),
            cursorBrush = SolidColor(TvColors.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { connect() }),
            modifier = Modifier
                .width(520.dp)
                .focusRequester(focus)
                .onFocusChanged { fieldFocused = it.isFocused }
                .background(TvColors.Surface)
                .border(2.dp, if (fieldFocused) TvColors.Accent else TvColors.Surface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
        error?.let { Text(stringResource(it), color = TvColors.Text, fontSize = 16.sp, modifier = Modifier.padding(top = 14.dp)) }
        Spacer(Modifier.height(24.dp))
        TvButton(stringResource(if (busy) R.string.connect_busy else R.string.connect_action)) { connect() }
    }
}
