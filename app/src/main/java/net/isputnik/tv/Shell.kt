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
import net.isputnik.tv.core.Destination
import net.isputnik.tv.core.Feature
import net.isputnik.tv.core.FocusMemory
import net.isputnik.tv.core.LocalFocusMemory
import net.isputnik.tv.core.LocalNavigator
import net.isputnik.tv.core.Navigator
import net.isputnik.tv.core.Person
import net.isputnik.tv.core.RestoreFocus
import net.isputnik.tv.core.Route
import net.isputnik.tv.core.TopBar
import net.isputnik.tv.core.TvButton
import net.isputnik.tv.core.TvColors
import net.isputnik.tv.core.User
import net.isputnik.tv.core.remembersFocus
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

private data object AccountRoute : Route {
    override val key = "account"
}

/** What's reached from the name at the top right: who's watching, adding someone, leaving. */
private class AccountFeature(
    private val user: User,
    private val people: List<Person>,
    private val onSwitch: (String) -> Unit,
    private val onAdd: () -> Unit,
    private val onUnlink: () -> Unit
) : Feature {
    override val destinations = emptyList<Destination>()
    override fun owns(route: Route) = route == AccountRoute

    @Composable
    override fun Content(route: Route, nav: Navigator) {
        var confirming by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.who_title), color = TvColors.Text, fontSize = 26.sp)
            Spacer(Modifier.height(20.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(people, key = { it.id }) { person ->
                    val mine = person.id == user.id
                    PersonTile(person.name, CoreR.drawable.ic_person, mine, Modifier.remembersFocus("person/${person.id}", isDefault = mine)) {
                        if (mine) nav.back() else onSwitch(person.id)
                    }
                }
                item(key = "add") { PersonTile(stringResource(R.string.who_add), R.drawable.ic_add, false, Modifier.remembersFocus("add"), onAdd) }
            }
            Spacer(Modifier.height(26.dp))
            // Armed only while focus stays here: moving away and back has to ask again.
            TvButton(stringResource(R.string.account_unlink, user.displayName), Modifier.remembersFocus("unlink").onFocusChanged { if (!it.hasFocus) confirming = false }) {
                if (confirming) onUnlink() else confirming = true
            }
            if (confirming) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.account_unlink_confirm), color = TvColors.Muted, fontSize = 15.sp)
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
    onSwitch: (String) -> Unit,
    onAdd: () -> Unit,
    onUnlink: () -> Unit
) {
    val account = remember(user, people) { AccountFeature(user, people, onSwitch, onAdd, onUnlink) }
    val all = remember(features, account) { features + account }
    val destinations = remember(all) { all.flatMap { it.destinations } }
    val holder = rememberSaveableStateHolder()
    val nav = remember { Navigator(destinations.first().route).apply { onLeft = { holder.removeState(it.key) } } }
    BackHandler(enabled = nav.canGoBack) { nav.back() }

    val route = nav.current
    Column(Modifier.fillMaxSize()) {
        if (destinations.any { it.route == route }) {
            TopBar(
                logo = R.drawable.ic_launcher_foreground,
                appName = stringResource(R.string.app_name),
                destinations = destinations,
                current = route,
                personName = user.displayName,
                onSelect = { nav.select(it) },
                onPerson = { nav.push(AccountRoute) }
            )
        }
        Box(Modifier.weight(1f)) {
            key(route.key) {
                holder.SaveableStateProvider(route.key) {
                    val last = rememberSaveable { mutableStateOf<String?>(null) }
                    val memory = remember { FocusMemory(last) }
                    CompositionLocalProvider(LocalFocusMemory provides memory, LocalNavigator provides nav) {
                        all.first { it.owns(route) }.Content(route, nav)
                    }
                }
            }
        }
    }
}
