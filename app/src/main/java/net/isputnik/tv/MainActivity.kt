package net.isputnik.tv

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.launch
import net.isputnik.tv.core.ApiClient
import net.isputnik.tv.core.Empty
import net.isputnik.tv.core.KeystoreTextStore
import net.isputnik.tv.core.TvButton
import net.isputnik.tv.core.TvColors
import net.isputnik.tv.core.TvTheme
import net.isputnik.tv.core.User
import net.isputnik.tv.core.currentUser
import net.isputnik.tv.core.Feature
import net.isputnik.tv.video.VideoApi
import net.isputnik.tv.video.VideoFeature

private sealed interface Stage {
    data object Checking : Stage
    data object Unreachable : Stage
    data object Connect : Stage
    data object Link : Stage
    data class Signed(val user: User) : Stage
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val userAgent = "iSputnikTV/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})"
        val api = ApiClient(KeystoreTextStore(this), userAgent)
        // Posters come through the same client, so they carry the session too.
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context).components { add(OkHttpNetworkFetcherFactory(callFactory = { api.http })) }.build()
        }
        setContent {
            TvTheme {
                Box(Modifier.fillMaxSize().background(TvColors.Background)) { Root(api) }
            }
        }
    }
}

@Composable
private fun Root(api: ApiClient) {
    var stage by remember { mutableStateOf<Stage>(Stage.Checking) }
    var attempt by remember { mutableIntStateOf(0) }
    val features = remember { listOf<Feature>(VideoFeature(VideoApi(api))) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(attempt) {
        if (api.server == null) {
            stage = Stage.Connect
            return@LaunchedEffect
        }
        stage = Stage.Checking
        stage = try {
            api.currentUser()?.let { Stage.Signed(it) } ?: Stage.Link
        } catch (_: Exception) {
            Stage.Unreachable
        }
    }
    // Revoked in Profile, or expired: back to a fresh code, no stale session kept.
    LaunchedEffect(Unit) {
        api.signedOut.collect {
            api.clearSession()
            stage = Stage.Link
        }
    }

    when (val current = stage) {
        Stage.Checking -> Centered { Text(stringResource(R.string.loading), color = TvColors.Text) }
        Stage.Unreachable -> {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            Centered {
                Text(stringResource(R.string.startup_failed), color = TvColors.Text, fontSize = 22.sp)
                Text(api.server.orEmpty(), color = TvColors.Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
                Row {
                    TvButton(stringResource(R.string.retry), Modifier.focusRequester(focus)) { attempt++ }
                    Spacer(Modifier.width(16.dp))
                    TvButton(stringResource(R.string.link_change_server)) { stage = Stage.Connect }
                }
            }
        }
        Stage.Connect -> ConnectScreen(api) { attempt++ }
        Stage.Link -> LinkScreen(api, onChangeServer = { stage = Stage.Connect }, onLinked = { attempt++ })
        is Stage.Signed -> InLanguage(current.user.language) {
            Shell(features, current.user, onUnlink = {
                scope.launch {
                    // Off the server first, so it's gone from Profile too; signed out here either way.
                    runCatching { api.send("POST", "/api/auth/logout", api.json.encodeToString(Empty.serializer(), Empty)) }
                    api.clearSession()
                    stage = Stage.Link
                }
            })
        }
    }
}

@Composable
internal fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        content()
    }
}
