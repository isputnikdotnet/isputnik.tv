package net.isputnik.tv

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.isputnik.tv.core.AvailableUpdate
import net.isputnik.tv.core.Destination
import net.isputnik.tv.core.Feature
import net.isputnik.tv.core.Navigator
import net.isputnik.tv.core.Releases
import net.isputnik.tv.core.RestoreFocus
import net.isputnik.tv.core.Route
import net.isputnik.tv.core.TvButton
import net.isputnik.tv.core.TvColors
import net.isputnik.tv.core.remembersFocus
import okhttp3.OkHttpClient
import java.io.File

internal data object UpdateRoute : Route {
    override val key = "update"
}

/** Asks once each time the app opens. Failing to ask says nothing: a TV without internet, or
 *  with GitHub down, has no business hearing about it. */
internal class Updates(private val context: Context, private val http: OkHttpClient) {
    var available by mutableStateOf<AvailableUpdate?>(null)
        private set

    private val folder get() = File(context.cacheDir, "updates")

    suspend fun check() {
        folder.deleteRecursively()
        available = runCatching { Releases.check(http, BuildConfig.RELEASES_URL, BuildConfig.VERSION_NAME) }.getOrNull()
    }

    suspend fun download(update: AvailableUpdate, progress: (Int) -> Unit): File {
        val apk = File(folder, "iSputnik-TV-${update.version}.apk")
        Releases.download(http, update.url, apk, progress)
        return apk
    }

    fun mayInstall() = context.packageManager.canRequestPackageInstalls()

    /** Hands the file to the system's installer, which shows its own screen. */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    /** The nearest the TV has: Android TV opens the app's own switch, Fire TV only My Fire TV,
     *  so the page spells out the path. A TV with no such screen just has the words. */
    fun openInstallSetting() {
        try {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))
        } catch (_: ActivityNotFoundException) {
        }
    }
}

private sealed interface UpdateState {
    data object Ready : UpdateState
    data object Downloading : UpdateState
    data object NeedsPermission : UpdateState
    data object Failed : UpdateState
}

/** The one page about updating, reached from the notice in the top bar. Never in the way of a
 *  film: nothing here opens by itself. */
internal class UpdateFeature(private val updates: Updates) : Feature {
    override val destinations = emptyList<Destination>()
    override fun owns(route: Route) = route == UpdateRoute

    @Composable
    override fun Content(route: Route, nav: Navigator) {
        val update = updates.available ?: return
        val scope = rememberCoroutineScope()
        var state by remember { mutableStateOf<UpdateState>(UpdateState.Ready) }
        var percent by remember { mutableIntStateOf(0) }

        fun start() {
            if (!updates.mayInstall()) {
                state = UpdateState.NeedsPermission
                return
            }
            state = UpdateState.Downloading
            percent = 0
            scope.launch {
                try {
                    val apk = updates.download(update) { percent = it }
                    state = UpdateState.Ready
                    updates.install(apk)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    state = UpdateState.Failed
                }
            }
        }

        Column(Modifier.fillMaxSize().padding(56.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.update_title, update.version), color = TvColors.Text, fontSize = 28.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            val message = when (state) {
                UpdateState.Ready -> stringResource(R.string.update_body, BuildConfig.VERSION_NAME)
                UpdateState.Downloading -> stringResource(R.string.update_downloading, percent)
                UpdateState.NeedsPermission -> stringResource(R.string.update_permission)
                UpdateState.Failed -> stringResource(R.string.update_failed)
            }
            Text(message, color = TvColors.Muted, fontSize = 18.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            when (state) {
                UpdateState.Downloading -> Unit
                UpdateState.NeedsPermission ->
                    TvButton(stringResource(R.string.update_open_settings), Modifier.remembersFocus("action", isDefault = true), primary = true) {
                        if (updates.mayInstall()) start()
                        else updates.openInstallSetting()
                    }
                UpdateState.Ready, UpdateState.Failed ->
                    TvButton(stringResource(R.string.update_action), Modifier.remembersFocus("action", isDefault = true), primary = true) { start() }
            }
        }
        RestoreFocus(true)
    }
}
