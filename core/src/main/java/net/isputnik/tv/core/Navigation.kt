package net.isputnik.tv.core

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/** A screen the app can be on. `key` names it for saved scroll and focus. */
interface Route {
    val key: String
}

/** An entry in the top menu. */
data class Destination(val route: Route, @param:StringRes val label: Int, @param:DrawableRes val icon: Int)

/** What a section of the app gives the shell: its menu entries and its screens. Adding one
 *  (photos, audiobooks) changes neither this file nor the other sections. */
interface Feature {
    val destinations: List<Destination>
    fun owns(route: Route): Boolean

    @Composable
    fun Content(route: Route, nav: Navigator)
}

class Navigator(private val home: Route) {
    val stack = mutableStateListOf(home)
    val current: Route get() = stack.last()

    /** False after choosing from the menu, where focus belongs on the menu until the person
     *  presses Down. True on the way into or back to a screen. */
    var focusContent by mutableStateOf(true)
        private set

    val canGoBack: Boolean get() = stack.size > 1 || stack.first() != home

    /** Told when a screen is left for good, so its saved scroll and focus can go too. */
    var onLeft: (Route) -> Unit = {}

    fun push(route: Route) {
        focusContent = true
        stack += route
    }

    /** Back: out of a screen, then from any menu page to Home. */
    fun back() {
        focusContent = true
        if (stack.size > 1) onLeft(stack.removeAt(stack.lastIndex)) else stack[0] = home
    }

    fun select(route: Route) {
        focusContent = false
        stack.clear()
        stack += route
    }
}

/** Which card a screen last had focus on, so Back lands on the one you left. */
class FocusMemory(private val last: MutableState<String?>) {
    private val requesters = mutableMapOf<String, FocusRequester>()
    private var fallback: String? = null

    internal fun register(key: String, requester: FocusRequester, isDefault: Boolean) {
        requesters[key] = requester
        if (isDefault) fallback = key
    }

    internal fun unregister(key: String, requester: FocusRequester) {
        if (requesters[key] === requester) requesters.remove(key)
    }

    internal fun focused(key: String) {
        last.value = key
    }

    /** The key to focus on arriving: the one left, if it's still on screen, else the default. */
    internal fun target(): String? = FocusMemory.choose(last.value, fallback, requesters.keys)

    internal fun restore(): Boolean {
        val requester = requesters[target()] ?: return false
        return runCatching { requester.requestFocus() }.isSuccess
    }

    companion object {
        fun choose(last: String?, fallback: String?, present: Set<String>): String? =
            last?.takeIf { it in present } ?: fallback?.takeIf { it in present }
    }
}

val LocalFocusMemory = staticCompositionLocalOf<FocusMemory> { error("No FocusMemory: screens live inside the shell") }
val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No Navigator: screens live inside the shell") }

/** Marks something focus can return to. `isDefault` is where focus goes on a first visit. */
fun Modifier.remembersFocus(key: String, isDefault: Boolean = false): Modifier = composed {
    val memory = LocalFocusMemory.current
    val requester = remember { FocusRequester() }
    DisposableEffect(memory, key, isDefault) {
        memory.register(key, requester, isDefault)
        onDispose { memory.unregister(key, requester) }
    }
    focusRequester(requester).onFocusChanged { if (it.hasFocus) memory.focused(key) }
}

/** Put this in a screen once its content is on screen. */
@Composable
fun RestoreFocus(ready: Boolean) {
    val memory = LocalFocusMemory.current
    val nav = LocalNavigator.current
    LaunchedEffect(ready) {
        if (ready && nav.focusContent) {
            withFrameNanos { }
            memory.restore()
        }
    }
}
