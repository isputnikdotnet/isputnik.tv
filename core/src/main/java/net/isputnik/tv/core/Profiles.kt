package net.isputnik.tv.core

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

// Viewing profiles (home's docs/tv-profiles-plan.md, phase P1): views of one account for a
// shared TV — Kids, Russian, Classic. Made in Profile on the web; this TV only wears them. The
// one worn is kept on this TV's session by home, so the app opens in the profile it was left in,
// and home narrows every answer to it (video libraries, sections) whatever this app asks.

@Serializable
data class ViewingProfile(
    val id: String,
    val name: String,
    val avatar: String = "blue",
    val restricted: Boolean = false
)

/** Which sections the TV should offer as the session is now (home's Sections). */
@Serializable
data class ProfileSections(val video: Boolean = true, val games: Boolean = true)

/** GET /api/profiles */
@Serializable
data class Profiles(
    val profiles: List<ViewingProfile> = emptyList(),
    val current: String? = null,
    val sections: ProfileSections = ProfileSections()
) {
    val worn: ViewingProfile? get() = profiles.firstOrNull { it.id == current }
}

@Serializable private data class SwitchBody(val profileId: String?)

/** This account's profiles; null from a home too old to have them (or one that can't say). */
suspend fun ApiClient.profiles(): Profiles? = try {
    get<Profiles>("/api/profiles")
} catch (e: ApiException) {
    if (e.code == 404) null else throw e
}

/** Wear a profile on this TV, or (null) go back to the account itself. */
suspend fun ApiClient.wearProfile(id: String?): Profiles = post("/api/profiles/switch", SwitchBody(id))

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
