package net.isputnik.tv.core

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

// Viewing profiles (home's docs/tv-profiles-plan.md): the household's ways of watching video —
// Kids, Russian, Classic — set up by an admin on the website's video page. Every TV offers the
// same ones; this TV only wears them. The one worn is kept on this TV's session by home, so the
// app opens in the profile it was left in, and home narrows video to it (libraries, rows)
// whatever this app asks. Games and everything else stay the account's.

@Serializable
data class ViewingProfile(
    val id: String,
    val name: String,
    val avatar: String = "blue",
    val restricted: Boolean = false
)

/** GET /api/profiles */
@Serializable
data class Profiles(
    val profiles: List<ViewingProfile> = emptyList(),
    val current: String? = null,
    /** Leaving the profile worn now needs the household PIN. */
    val locked: Boolean = false
) {
    val worn: ViewingProfile? get() = profiles.firstOrNull { it.id == current }
}

@Serializable private data class SwitchBody(val profileId: String?, val pin: String? = null)
@Serializable private data class PinBody(val pin: String)
@Serializable private data class PinOk(val ok: Boolean = false)

/** This account's profiles; null from a home too old to have them (or one that can't say). */
suspend fun ApiClient.profiles(): Profiles? = try {
    get<Profiles>("/api/profiles")
} catch (e: ApiException) {
    if (e.code == 404) null else throw e
}

/** Wear a profile on this TV, or (null) go back to the account itself. Leaving a locked
 *  profile needs [pin]; a wrong one throws an [ApiException] whose reason is pin_wrong
 *  (with triesLeft), pin_locked (429) or pin_needed. */
suspend fun ApiClient.wearProfile(id: String?, pin: String? = null): Profiles = post("/api/profiles/switch", SwitchBody(id, pin))

/** The PIN, checked before what the TV does on its own side of a locked profile: choosing
 *  another person, linking someone, unlinking. Throws like [wearProfile] when it's wrong. */
suspend fun ApiClient.checkPin(pin: String) {
    post<PinOk, PinBody>("/api/profiles/pin/check", PinBody(pin))
}

/** A profile's colour, the same as on the web (styles/about.css). */
fun profileColor(avatar: String): Color = when (avatar) {
    "green" -> Color(0xFF2E9D57)
    "orange" -> Color(0xFFD9771F)
    "pink" -> Color(0xFFD14D8C)
    "purple" -> Color(0xFF7B55C7)
    "teal" -> Color(0xFF1A948F)
    "red" -> Color(0xFFC9433B)
    "yellow" -> Color(0xFFB8900F)
    else -> Color(0xFF2F6FD6)
}
