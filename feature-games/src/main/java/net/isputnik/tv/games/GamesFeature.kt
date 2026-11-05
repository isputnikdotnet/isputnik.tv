package net.isputnik.tv.games

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import net.isputnik.tv.core.ApiClient
import net.isputnik.tv.core.Destination
import net.isputnik.tv.core.Feature
import net.isputnik.tv.core.Navigator
import net.isputnik.tv.core.Route

sealed interface GamesRoute : Route {
    data object Lobby : GamesRoute { override val key = "games/lobby" }
    data class Setup(val game: String) : GamesRoute { override val key = "games/setup/$game" }
    /** The game kept on this TV for `game`; `session` tells one visit from the next. */
    data class Local(val game: String, val session: Long) : GamesRoute { override val key = "games/local/$game/$session" }
    data class Match(val id: String) : GamesRoute { override val key = "games/match/$id" }
    data object Sputnik : GamesRoute { override val key = "games/sputnik" }
    data object Torpedo : GamesRoute { override val key = "games/torpedo" }
}

/** The family's games, as far as a remote suits them: Chess and Checkers against the computer,
 *  two on the sofa or someone on their own screen; Sputnik Catch and Torpedo Run for one. The
 *  shell shows it only when the household has the games and this person may play
 *  (`/api/games/status`, as the website decides its Games entry). */
class GamesFeature(api: ApiClient, private val userId: String) : Feature {
    private val games = GamesApi(api)

    override val destinations = listOf(Destination(GamesRoute.Lobby, R.string.menu_games, R.drawable.ic_games))

    override fun owns(route: Route) = route is GamesRoute

    @Composable
    override fun Content(route: Route, nav: Navigator) {
        val context = LocalContext.current
        val saves = remember(userId) { Saves(context, userId) }
        when (val page = route as GamesRoute) {
            GamesRoute.Lobby -> LobbyPage(games, saves, userId, nav)
            is GamesRoute.Setup -> SetupPage(games, saves, userId, page.game, nav)
            is GamesRoute.Local -> {
                val scope = rememberCoroutineScope()
                val saved = remember(page) { saves.load(page.game) }
                if (saved == null) {
                    androidx.compose.runtime.LaunchedEffect(Unit) { nav.back() }
                    return
                }
                val session = remember(page) { LocalSession(games, saves, saved, scope) }
                val kind = BoardKind.of(page.game) ?: BoardKind.CHESS
                BoardScreen(
                    session,
                    title = stringResource(kindName(kind)),
                    subtitle = if (saved.mode == "computer") stringResource(R.string.games_vs_computer_level, stringResource(levelName(saved.level)))
                    else stringResource(R.string.games_sofa),
                    onNewGame = { nav.back() },
                    onLeave = { nav.back() }
                )
            }
            is GamesRoute.Match -> {
                val scope = rememberCoroutineScope()
                val session = remember(page) { MatchSession(games, page.id, userId, scope) }
                val kind = session.view?.let { session.kind } ?: BoardKind.CHESS
                BoardScreen(
                    session,
                    title = stringResource(kindName(kind)),
                    subtitle = stringResource(R.string.games_online),
                    onNewGame = null,
                    onLeave = { nav.back() }
                )
            }
            GamesRoute.Sputnik -> SputnikScreen(games, onLeave = { nav.back() })
            GamesRoute.Torpedo -> TorpedoScreen(games, onLeave = { nav.back() })
        }
    }

    companion object {
        /** Whether this person gets the Games section; false when the server can't say. */
        suspend fun available(api: ApiClient): Boolean = runCatching { GamesApi(api).status().enabled }.getOrDefault(false)
    }
}

internal fun kindName(kind: BoardKind) = if (kind == BoardKind.CHESS) R.string.games_chess else R.string.games_checkers

internal fun levelName(level: String) = when (level) {
    "easy" -> R.string.games_level_easy
    "hard" -> R.string.games_level_hard
    else -> R.string.games_level_normal
}

/** The games a TV plays, by the games server's id. */
internal val TV_BOARD_GAMES = setOf("chess", "checkers")
