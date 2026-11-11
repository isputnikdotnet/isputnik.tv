package net.isputnik.tv

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Text
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.launch
import net.isputnik.tv.core.ApiClient
import net.isputnik.tv.core.ApiException
import net.isputnik.tv.core.Empty
import net.isputnik.tv.core.Feature
import net.isputnik.tv.core.KeystoreTextStore
import net.isputnik.tv.core.Profiles
import net.isputnik.tv.core.TvButton
import net.isputnik.tv.core.TvColors
import net.isputnik.tv.core.TvTheme
import net.isputnik.tv.core.User
import net.isputnik.tv.core.checkPin
import net.isputnik.tv.core.currentUser
import net.isputnik.tv.core.profiles
import net.isputnik.tv.core.wearProfile
import net.isputnik.tv.games.GamesFeature
import net.isputnik.tv.photos.PhotosApi
import net.isputnik.tv.photos.PhotosFeature
import net.isputnik.tv.video.VideoApi
import net.isputnik.tv.video.VideoFeature
import okhttp3.CookieJar

private sealed interface Stage {
    data object Checking : Stage
    data object Unreachable : Stage
    data object Connect : Stage
    data object Who : Stage
    data class Link(val backTo: String?) : Stage
    /** Signed in; [profiles] is null on a home without viewing profiles. */
    data class Signed(val user: User, val profiles: Profiles? = null) : Stage
    /** Which of this account's viewing profiles is watching (home's Profile › TV profiles). */
    data class ChooseProfile(val user: User, val profiles: Profiles) : Stage
    /** The PIN pad over a locked profile; [submit] throws for a wrong PIN, [back] is Stay here. */
    data class Pin(val purpose: PinPurpose, val submit: suspend (String) -> Unit, val back: Stage) : Stage
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
        setContent { Root(api, onExit = { finish() }) }
    }
}

@Composable
private fun Root(api: ApiClient, onExit: () -> Unit) {
    // With several people on this TV it asks who's watching when opened; with one it just opens.
    // It asks only once it knows the TV wasn't left in a locked profile (phase P2): from Kids,
    // the picker would be a way to everyone else linked here.
    var stage by remember { mutableStateOf<Stage>(Stage.Checking) }
    var askPerson by remember { mutableStateOf(api.people.size > 1) }
    var attempt by remember { mutableIntStateOf(0) }
    // Which profile is watching is asked once when the app opens, and again after choosing
    // another person; never when the TV was left in a restricted profile (Kids stays Kids).
    var askProfile by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Through the app's client for its user agent, but none of the session goes to GitHub.
    val updates = remember { Updates(context, api.http.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()) }
    // Each time the app comes to the screen, not only when it starts (Updates.checkIfStale).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { updates.checkIfStale() } }

    /** With this person gone: whoever's left, or a new code. */
    fun afterLeaving() {
        stage = if (api.people.isNotEmpty()) Stage.Who else Stage.Link(null)
    }

    LaunchedEffect(attempt) {
        if (stage == Stage.Who) return@LaunchedEffect
        if (api.server == null) {
            stage = Stage.Connect
            return@LaunchedEffect
        }
        stage = Stage.Checking
        try {
            val user = api.currentUser()
            if (user != null) {
                api.remember(user)
                // An older home has no profiles, and a failure here only means none are offered.
                val profiles = runCatching { api.profiles() }.getOrNull()
                if (askPerson && api.people.size > 1 && profiles?.locked != true) {
                    askPerson = false
                    stage = Stage.Who
                    return@LaunchedEffect
                }
                askPerson = false
                val ask = askProfile && profiles != null && profiles.profiles.isNotEmpty() && profiles.worn?.restricted != true
                askProfile = false
                stage = if (ask) Stage.ChooseProfile(user, profiles!!) else Stage.Signed(user, profiles)
            } else {
                api.clearSession()
                afterLeaving()
            }
        } catch (_: Exception) {
            stage = Stage.Unreachable
        }
    }
    // Revoked in Profile, or expired: this person's session goes, and nobody else's.
    LaunchedEffect(Unit) {
        api.signedOut.collect {
            api.clearSession()
            afterLeaving()
        }
    }

    // A TV app sits in the background for days, so coming back is when a change made in Profile
    // reaches it: the theme, the language, the name. Failing here says nothing: the next real
    // request is the one that reports a lost server or session.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val signed = stage as? Stage.Signed ?: return@LifecycleEventEffect
        scope.launch {
            val user = runCatching { api.currentUser() }.getOrNull()
            // A profile renamed or deleted in Profile reaches the TV the same way.
            val profiles = runCatching { api.profiles() }.getOrNull() ?: signed.profiles
            if (stage == signed && user != null && user.id == signed.user.id && (user != signed.user || profiles != signed.profiles)) {
                api.remember(user)
                stage = Stage.Signed(user, profiles)
            }
        }
    }

    fun check() {
        stage = Stage.Checking
        attempt++
    }

    // The account's theme once it's known; before that the one it had last time. The picker and
    // the sign-in screens are nobody's in particular.
    val theme = when (val current = stage) {
        is Stage.Signed -> current.user.theme
        is Stage.ChooseProfile -> current.user.theme
        is Stage.Pin -> (current.back as? Stage.Signed)?.user?.theme ?: api.activeTheme
        Stage.Who, Stage.Connect -> null
        else -> api.activeTheme
    }
    TvTheme(theme) {
        Box(Modifier.fillMaxSize().background(TvColors.Background)) {
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
                Stage.Connect -> ConnectScreen(api) { check() }
                Stage.Who -> WhoScreen(
                    people = api.people,
                    onChoose = {
                        api.switchTo(it)
                        askProfile = true
                        check()
                    },
                    onAdd = {
                        val back = api.activePerson
                        api.startLinking()
                        stage = Stage.Link(back)
                    }
                )
                is Stage.Link -> {
                    val leave = current.backTo?.let { back ->
                        {
                            api.switchTo(back)
                            check()
                        }
                    } ?: if (api.people.isNotEmpty()) ({ stage = Stage.Who }) else null
                    BackHandler(enabled = leave != null) { leave?.invoke() }
                    LinkScreen(
                        api,
                        onChangeServer = if (api.people.isEmpty()) ({ stage = Stage.Connect }) else null,
                        onCancel = leave,
                        onLinked = { check() }
                    )
                }
                is Stage.ChooseProfile -> InLanguage(current.user.language) {
                    ProfileScreen(current.user, current.profiles) { id ->
                        scope.launch {
                            // Home keeps the choice on this TV's session; if it can't, carry on as before.
                            val worn = runCatching { api.wearProfile(id) }.getOrNull() ?: current.profiles
                            stage = Stage.Signed(current.user, worn)
                        }
                    }
                }
                is Stage.Pin -> InLanguage((current.back as? Stage.Signed)?.user?.language) {
                    PinScreen(current.purpose, current.submit) { stage = current.back }
                }
                // A new profile is a new set of screens: its rows, its libraries, its history.
                is Stage.Signed -> key(current.user.id, current.profiles?.current) {
                    /** Every way out of a locked profile goes through the pad first; [action]
                     *  gets the PIN typed (null when no lock), and throws if home refuses it. */
                    fun guarded(purpose: PinPurpose, action: suspend (String?) -> Unit) {
                        if (current.profiles?.locked == true) {
                            stage = Stage.Pin(purpose, { pin -> action(pin) }, current)
                        } else {
                            scope.launch {
                                try {
                                    action(null)
                                } catch (e: ApiException) {
                                    // A PIN set on the website since this screen loaded: ask for it now.
                                    if (e.reason == "pin_needed") stage = Stage.Pin(purpose, { pin -> action(pin) }, current)
                                } catch (_: Exception) {
                                    // Unreachable server: the next real request says so.
                                }
                            }
                        }
                    }
                    val video = remember { VideoFeature(VideoApi(api), current.user.id) }
                    // Games joins the menu once the server says this person may play (as the
                    // website decides its Games entry); video is there from the start.
                    var games by remember { mutableStateOf<Feature?>(null) }
                    LaunchedEffect(Unit) { if (GamesFeature.available(api)) games = GamesFeature(api, current.user.id) }
                    // Photos joins it when an admin has put something on the TV for this
                    // person and profile (home's docs/tv-photos-plan.md); none, no section.
                    var photos by remember { mutableStateOf<Feature?>(null) }
                    // Asked again on Refresh, so a pick added on the website shows without a restart;
                    // never taken away while the app runs (the page itself says when it's empty).
                    var photoChecks by remember { mutableIntStateOf(0) }
                    LaunchedEffect(photoChecks) { if (photos == null && PhotosApi.available(api)) photos = PhotosFeature(api) }
                    // Movies and Shows, Photos, then Games.
                    val features = remember(photos, games) { listOfNotNull(video, photos, games) }
                    InLanguage(current.user.language) {
                        Shell(
                            features,
                            current.user,
                            people = api.people,
                            profiles = current.profiles,
                            searchRoute = video.searchRoute,
                            onWear = { id ->
                                guarded(PinPurpose.Profile) { pin -> stage = Stage.Signed(current.user, api.wearProfile(id, pin)) }
                            },
                            onSwitch = { person ->
                                guarded(PinPurpose.Person) { pin ->
                                    // Home can't see the other people on this TV; the check is the pad.
                                    if (pin != null) api.checkPin(pin)
                                    api.switchTo(person)
                                    askProfile = true
                                    check()
                                }
                            },
                            onAdd = {
                                guarded(PinPurpose.Add) { pin ->
                                    if (pin != null) api.checkPin(pin)
                                    api.startLinking()
                                    stage = Stage.Link(current.user.id)
                                }
                            },
                            onUnlink = {
                                guarded(PinPurpose.Unlink) { pin ->
                                    // Home refuses signing out of a locked profile: leave it first.
                                    if (pin != null) api.wearProfile(null, pin)
                                    // Off the server first, so it's gone from Profile too; forgotten here either way.
                                    runCatching { api.send("POST", "/api/auth/logout", api.json.encodeToString(Empty.serializer(), Empty)) }
                                    api.clearSession()
                                    afterLeaving()
                                }
                            },
                            // The page redraws itself; this brings the name, theme and
                            // profiles up to date and asks for an update at once.
                            onRefresh = {
                                scope.launch {
                                    val user = runCatching { api.currentUser() }.getOrNull()
                                    val profiles = runCatching { api.profiles() }.getOrNull() ?: current.profiles
                                    if (user != null && user.id == current.user.id && stage == current) {
                                        api.remember(user)
                                        stage = Stage.Signed(user, profiles)
                                    }
                                    photoChecks++
                                    updates.checkNow()
                                }
                            },
                            onExit = onExit,
                            updates = updates,
                            api = api
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        content()
    }
}
