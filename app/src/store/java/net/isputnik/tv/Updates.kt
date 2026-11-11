package net.isputnik.tv

import android.content.Context
import androidx.compose.runtime.Composable
import net.isputnik.tv.core.AvailableUpdate
import net.isputnik.tv.core.Destination
import net.isputnik.tv.core.Feature
import net.isputnik.tv.core.Navigator
import net.isputnik.tv.core.Route
import okhttp3.OkHttpClient

/* The store build. Amazon and Google update the app themselves and forbid an app that installs
 * its own updates, so this flavor never asks GitHub, never downloads, and carries no install
 * permission or FileProvider (those are in src/github). The same names as the github flavor's
 * Updates.kt, so the shell compiles against either. */

internal data object UpdateRoute : Route {
    override val key = "update"
}

@Suppress("UNUSED_PARAMETER")
internal class Updates(context: Context, http: OkHttpClient) {
    val available: AvailableUpdate? = null
    suspend fun checkIfStale() = Unit
    suspend fun checkNow() = Unit
}

@Suppress("UNUSED_PARAMETER")
internal class UpdateFeature(updates: Updates) : Feature {
    override val destinations = emptyList<Destination>()
    override fun owns(route: Route) = false

    @Composable
    override fun Content(route: Route, nav: Navigator) = Unit
}
