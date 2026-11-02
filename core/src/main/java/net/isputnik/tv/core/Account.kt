package net.isputnik.tv.core

import kotlinx.serialization.Serializable

@Serializable data class User(val id: String, val displayName: String = "", val language: String? = null, val theme: String? = null)
@Serializable data class Me(val user: User? = null)

/** POST /api/auth/device/start */
@Serializable
data class LinkStart(
    val deviceCode: String,
    val userCode: String,
    val userCodeDisplay: String,
    val verificationUrl: String,
    val verificationUrlComplete: String,
    val expiresAt: String,
    val interval: Int = 5
)

/** POST /api/auth/device/poll: pending, approved, denied, expired or consumed. */
@Serializable data class LinkPoll(val status: String, val interval: Int = 5)

@Serializable data class LinkPollBody(val deviceCode: String)
@Serializable data object Empty

/** Who this TV is signed in as: null when nobody; throws when the server can't say. */
suspend fun ApiClient.currentUser(): User? = try {
    get<Me>(ApiClient.ME).user
} catch (e: ApiException) {
    if (e.code == 401) null else throw e
}
