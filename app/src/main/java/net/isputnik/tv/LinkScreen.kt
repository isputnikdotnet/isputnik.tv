package net.isputnik.tv

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import net.isputnik.tv.core.ApiClient
import net.isputnik.tv.core.ApiException
import net.isputnik.tv.core.Empty
import net.isputnik.tv.core.LinkPoll
import net.isputnik.tv.core.LinkPollBody
import net.isputnik.tv.core.LinkStart
import net.isputnik.tv.core.ServerAddress
import net.isputnik.tv.core.TvButton
import net.isputnik.tv.core.TvColors

private fun qrCode(text: String, size: Int = 480): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size) {
        bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    return bitmap.asImageBitmap()
}

/** Home's Link a device, seen from the TV: a code and a QR. The phone does the rest, against
 *  home's own /link page, with the person's password. The TV never sees a password. */
@Composable
internal fun LinkScreen(api: ApiClient, onChangeServer: () -> Unit, onLinked: () -> Unit) {
    var link by remember { mutableStateOf<LinkStart?>(null) }
    var notice by remember { mutableStateOf<Int?>(null) }
    var refused by remember { mutableStateOf<Int?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    LaunchedEffect(Unit) {
        while (true) {
            val started = try {
                api.post<LinkStart, Empty>("/api/auth/device/start", Empty)
            } catch (e: ApiException) {
                if (e.code == 403) {
                    refused = if (e.reason == "proxy") R.string.link_refused_proxy else R.string.link_refused_network
                    link = null
                    delay(30_000)
                    continue
                }
                notice = R.string.link_failed
                delay(15_000)
                continue
            } catch (_: Exception) {
                notice = R.string.link_failed
                delay(15_000)
                continue
            }
            refused = null
            link = started
            var waiting = true
            var interval = started.interval
            while (waiting) {
                delay(interval * 1000L)
                val answer = try {
                    api.post<LinkPoll, LinkPollBody>("/api/auth/device/poll", LinkPollBody(started.deviceCode))
                } catch (e: ApiException) {
                    // 404: the server no longer knows this code. A new one is the answer.
                    if (e.code == 404) LinkPoll("expired") else continue
                } catch (_: Exception) {
                    continue
                }
                interval = answer.interval
                when (answer.status) {
                    "pending" -> notice = null
                    "approved" -> {
                        onLinked()
                        return@LaunchedEffect
                    }
                    "denied" -> {
                        notice = R.string.link_denied
                        waiting = false
                    }
                    else -> waiting = false
                }
            }
        }
    }

    val current = link
    // A 1080p TV is only 540dp tall: everything here has to fit in that.
    Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 28.dp), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.link_title), color = TvColors.Text, fontSize = 30.sp)
        Spacer(Modifier.height(16.dp))
        val blocked = refused
        when {
            blocked != null -> Text(stringResource(blocked), color = TvColors.Text, fontSize = 20.sp)
            current == null -> Text(stringResource(notice ?: R.string.loading), color = TvColors.Text, fontSize = 20.sp)
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                val qr = remember(current.verificationUrlComplete) { qrCode(current.verificationUrlComplete) }
                Image(qr, contentDescription = null, modifier = Modifier.size(240.dp).background(Color.White).padding(8.dp))
                Spacer(Modifier.width(40.dp))
                Column {
                    // The whole address: home's /link page starts a device of its own, with
                    // nowhere to type a code, so the bare address would be a dead end.
                    Text(stringResource(R.string.link_steps), color = TvColors.Text, fontSize = 20.sp)
                    Text(current.verificationUrlComplete, color = TvColors.Text, fontSize = 18.sp, modifier = Modifier.padding(top = 6.dp))
                    Text(current.userCodeDisplay, color = TvColors.Accent, fontSize = 52.sp, modifier = Modifier.padding(vertical = 12.dp))
                    Text(stringResource(R.string.link_confirm), color = TvColors.Muted, fontSize = 17.sp)
                    notice?.let { Text(stringResource(it), color = TvColors.Text, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp)) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (ServerAddress.isPlainHttp(api.server.orEmpty())) {
            Text(stringResource(R.string.link_plain_http), color = TvColors.Muted, fontSize = 15.sp, modifier = Modifier.padding(bottom = 12.dp))
        }
        TvButton(stringResource(R.string.link_change_server), Modifier.focusRequester(focus), onClick = onChangeServer)
    }
}
