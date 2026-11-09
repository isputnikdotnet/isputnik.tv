package net.isputnik.tv

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import net.isputnik.tv.core.BackdropLayer
import net.isputnik.tv.core.Destination
import net.isputnik.tv.core.Feature
import net.isputnik.tv.core.LocalPageBackdrop
import net.isputnik.tv.core.PageBackdrop
import net.isputnik.tv.core.FocusMemory
import net.isputnik.tv.core.LocalFocusMemory
import net.isputnik.tv.core.LocalNavigator
import net.isputnik.tv.core.Navigator
import net.isputnik.tv.core.Person
import net.isputnik.tv.core.Profiles
import net.isputnik.tv.core.RestoreFocus
import net.isputnik.tv.core.Route
import net.isputnik.tv.core.TopBar
import net.isputnik.tv.core.TvColors
import net.isputnik.tv.core.User
import net.isputnik.tv.core.remembersFocus
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Icon
import net.isputnik.tv.core.Avatar
import net.isputnik.tv.core.MenuDivider
import net.isputnik.tv.core.MenuEntry
import net.isputnik.tv.core.profileColor
import java.util.Locale
import net.isputnik.tv.core.R as CoreR

/** The language this person chose in iSputnik, when they've chosen one the app has; else the
 *  TV's own. The strings follow the account, as the website's do. */
@Composable
internal fun InLanguage(language: String?, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val context = remember(base, language) {
        if (language.isNullOrBlank()) base
        else base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) })
    }
    CompositionLocalProvider(
        LocalContext provides context,
        LocalResources provides context.resources,
        LocalConfiguration provides context.resources.configuration,
        content = content
    )
}

private data object WhoRoute : Route {
    override val key = "who"
}

/** Who's watching, behind the profile button in the top bar: this account itself, its
 *  household's profiles, then the other people linked on this TV. Choosing the one already
 *  watching just goes back. Adding a person and unlinking live in the settings menu. */
private class WhoFeature(
    private val user: User,
    private val people: List<Person>,
    private val profiles: Profiles?,
    private val onWear: (String?) -> Unit,
    private val onSwitch: (String) -> Unit
) : Feature {
    override val destinations = emptyList<Destination>()
    override fun owns(route: Route) = route == WhoRoute

    @Composable
    override fun Content(route: Route, nav: Navigator) {
        // In a locked profile every way out needs the PIN; the pad asks when one is chosen.
        val lockedBadge = if (profiles?.locked == true) stringResource(R.string.profile_locked) else null
        Column(Modifier.fillMaxSize().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.who_title), color = TvColors.Text, fontSize = 32.sp, fontFamily = FontFamily.Serif)
            Spacer(Modifier.height(28.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val me = people.firstOrNull { it.id == user.id }
                // This account first: everything it may see, no profile worn.
                if (me != null) {
                    item(key = "person/${me.id}") {
                        val here = profiles?.current == null
                        PersonTile(me.name, CoreR.drawable.ic_person, here, Modifier.remembersFocus("person/${me.id}", isDefault = here), badge = if (here) null else lockedBadge) {
                            if (here) nav.back() else onWear(null)
                        }
                    }
                }
                items(profiles?.profiles.orEmpty(), key = { "profile/${it.id}" }) { profile ->
                    val worn = profile.id == profiles?.current
                    ProfileTile(profile.name, profile.avatar, worn, Modifier.remembersFocus("profile/${profile.id}", isDefault = worn), badge = if (worn) null else lockedBadge) {
                        if (worn) nav.back() else onWear(profile.id)
                    }
                }
                items(people.filter { it.id != user.id }, key = { "person/${it.id}" }) { person ->
                    PersonTile(person.name, CoreR.drawable.ic_person, false, Modifier.remembersFocus("person/${person.id}"), badge = lockedBadge) { onSwitch(person.id) }
                }
            }
        }
        RestoreFocus(true)
    }
}

/** The menu over whichever section is on screen. Every visited screen keeps its scroll and the
 *  card it had focus on, so Back returns to exactly where you were. */
@Composable
internal fun Shell(
    features: List<Feature>,
    user: User,
    people: List<Person>,
    profiles: Profiles?,
    searchRoute: Route?,
    onWear: (String?) -> Unit,
    onSwitch: (String) -> Unit,
    onAdd: () -> Unit,
    onUnlink: () -> Unit,
    onRefresh: () -> Unit,
    onExit: () -> Unit,
    updates: Updates
) {
    val who = remember(user, people, profiles) { WhoFeature(user, people, profiles, onWear, onSwitch) }
    val all = remember(features, who, updates) { features + who + UpdateFeature(updates) }
    val destinations = remember(all) { all.flatMap { it.destinations } }
    val holder = rememberSaveableStateHolder()
    val nav = remember { Navigator(destinations.first().route).apply { onLeft = { holder.removeState(it.key) } } }
    BackHandler(enabled = nav.canGoBack) { nav.back() }
    // Refresh in the settings menu: the page on screen is drawn again, and asks the server again.
    var refreshes by remember { mutableIntStateOf(0) }

    val worn = profiles?.worn
    val locked = profiles?.locked == true
    val route = nav.current
    // The page's backdrop, under the menu as well: one picture behind the whole screen.
    val backdrop = remember { PageBackdrop() }
    Box(Modifier.fillMaxSize()) {
        BackdropLayer(backdrop.url)
        Column(Modifier.fillMaxSize()) {
        if (destinations.any { it.route == route }) {
            val shownName = worn?.name ?: user.displayName
            TopBar(
                logo = R.drawable.ic_launcher_foreground,
                appName = stringResource(R.string.app_name),
                destinations = destinations,
                current = route,
                onSelect = { nav.select(it) },
                // Updating is for a grown-up: not offered in a restricted profile.
                updateLabel = updates.available?.takeIf { worn?.restricted != true }?.let { stringResource(R.string.action_update, it.version) },
                onUpdate = { nav.push(UpdateRoute) },
                searchLabel = stringResource(R.string.action_search),
                onSearch = searchRoute?.let { search -> { nav.push(search) } },
                settingsLabel = stringResource(R.string.action_settings),
                menu = { close ->
                    var confirming by remember { mutableStateOf(false) }
                    MenuEntry(stringResource(R.string.menu_refresh), CoreR.drawable.ic_refresh) {
                        close()
                        refreshes++
                        onRefresh()
                    }
                    MenuEntry(stringResource(R.string.who_add), CoreR.drawable.ic_person_add, locked = locked, lockedLabel = stringResource(R.string.menu_pin)) {
                        close()
                        onAdd()
                    }
                    // Armed only while focus stays on it: moving away and back has to ask again.
                    MenuEntry(
                        if (confirming) stringResource(R.string.menu_unlink_confirm) else stringResource(R.string.account_unlink, user.displayName),
                        CoreR.drawable.ic_unlink,
                        Modifier.onFocusChanged { if (!it.hasFocus) confirming = false },
                        locked = locked,
                        lockedLabel = stringResource(R.string.menu_pin)
                    ) {
                        if (confirming || locked) {
                            close()
                            onUnlink()
                        } else confirming = true
                    }
                    MenuDivider()
                    MenuEntry(stringResource(R.string.menu_exit), CoreR.drawable.ic_exit) {
                        close()
                        onExit()
                    }
                    Text(
                        stringResource(R.string.menu_version, BuildConfig.VERSION_NAME, shownName),
                        color = TvColors.Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp)
                    )
                },
                profileLabel = stringResource(R.string.action_profile, shownName),
                avatar = worn?.let { Avatar(it.name.trim().take(1).uppercase().ifEmpty { "?" }, profileColor(it.avatar)) },
                onProfile = { nav.push(WhoRoute) }
            )
        }
        Box(Modifier.weight(1f)) {
            key(route.key, refreshes) {
                holder.SaveableStateProvider(route.key) {
                    val last = rememberSaveable { mutableStateOf<String?>(null) }
                    val memory = remember { FocusMemory(last) }
                    CompositionLocalProvider(LocalFocusMemory provides memory, LocalNavigator provides nav, LocalPageBackdrop provides backdrop) {
                        all.first { it.owns(route) }.Content(route, nav)
                    }
                }
            }
        }
        }
    }
}
