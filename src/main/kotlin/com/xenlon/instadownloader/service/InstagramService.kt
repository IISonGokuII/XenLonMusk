package com.xenlon.instadownloader.service

import com.xenlon.instadownloader.model.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * Service for fetching Instagram data using session-based authentication.
 * Logs in with username/password and uses the session cookies to access data.
 */
class InstagramService {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val cookieStorage = AcceptAllCookiesStorage()

    private val client = HttpClient(OkHttp) {
        install(HttpCookies) {
            storage = cookieStorage
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
        }
        followRedirects = true
    }

    private var csrfToken: String = ""
    private var isLoggedIn: Boolean = false
    private var sessionUserId: String = ""

    companion object {
        private const val BASE_URL = "https://www.instagram.com"
        private const val LOGIN_URL = "$BASE_URL/accounts/login/ajax/"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val IG_APP_ID = "936619743392459"
    }

    /**
     * Returns whether the user is currently logged in.
     */
    fun isAuthenticated(): Boolean = isLoggedIn

    /**
     * Returns the logged-in user's ID (for own-account operations like archive).
     */
    fun getSessionUserId(): String = sessionUserId

    /**
     * Performs login to Instagram with username and password.
     * Establishes a session via cookies for subsequent requests.
     */
    suspend fun login(username: String, password: String): DownloadResult<String> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Get initial page to obtain CSRF token
            val initialResponse = client.get(BASE_URL) {
                headers {
                    append(HttpHeaders.UserAgent, USER_AGENT)
                    append(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    append(HttpHeaders.AcceptLanguage, "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
                }
            }

            // Extract CSRF token from cookies
            val cookies = cookieStorage.get(Url(BASE_URL))
            csrfToken = cookies.find { it.name == "csrftoken" }?.value ?: ""

            if (csrfToken.isEmpty()) {
                // Try extracting from response body
                val body = initialResponse.bodyAsText()
                val csrfRegex = Regex(""""csrf_token":"([^"]+)"""")
                csrfToken = csrfRegex.find(body)?.groupValues?.get(1) ?: ""
            }

            if (csrfToken.isEmpty()) {
                return@withContext DownloadResult.Error("CSRF-Token konnte nicht abgerufen werden")
            }

            // Step 2: Perform login
            val timestamp = System.currentTimeMillis() / 1000

            val loginResponse = client.post(LOGIN_URL) {
                headers {
                    append(HttpHeaders.UserAgent, USER_AGENT)
                    append(HttpHeaders.Accept, "*/*")
                    append(HttpHeaders.AcceptLanguage, "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
                    append(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
                    append("X-CSRFToken", csrfToken)
                    append("X-IG-App-ID", IG_APP_ID)
                    append("X-ASBD-ID", "129477")
                    append("X-IG-WWW-Claim", "0")
                    append("X-Requested-With", "XMLHttpRequest")
                    append(HttpHeaders.Referrer, "$BASE_URL/accounts/login/")
                    append(HttpHeaders.Origin, BASE_URL)
                    append("Sec-Fetch-Dest", "empty")
                    append("Sec-Fetch-Mode", "cors")
                    append("Sec-Fetch-Site", "same-origin")
                }
                setBody(FormDataContent(Parameters.build {
                    append("username", username)
                    append("enc_password", "#PWD_INSTAGRAM_BROWSER:0:$timestamp:$password")
                    append("queryParams", "{}")
                    append("optIntoOneTap", "false")
                    append("stopDeletionNonce", "")
                    append("trustedDeviceRecords", "{}")
                }))
            }

            val loginBody = loginResponse.bodyAsText()
            val loginJson = json.decodeFromString<JsonObject>(loginBody)

            val authenticated = loginJson["authenticated"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val userId = loginJson["userId"]?.jsonPrimitive?.content ?: ""

            if (!authenticated) {
                val message = loginJson["message"]?.jsonPrimitive?.content
                val twoFactorRequired = loginJson["two_factor_required"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

                if (twoFactorRequired) {
                    val twoFactorInfo = loginJson["two_factor_info"]?.jsonObject
                    val identifier = twoFactorInfo?.get("two_factor_identifier")?.jsonPrimitive?.content ?: ""
                    val obfuscatedPhone = twoFactorInfo?.get("obfuscated_phone_number")?.jsonPrimitive?.content ?: ""
                    val totpEnabled = twoFactorInfo?.get("totp_two_factor_on")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val smsEnabled = twoFactorInfo?.get("sms_two_factor_on")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

                    // Update CSRF token from response cookies
                    val postCookies = cookieStorage.get(Url(BASE_URL))
                    csrfToken = postCookies.find { it.name == "csrftoken" }?.value ?: csrfToken

                    return@withContext DownloadResult.TwoFactorRequired(
                        TwoFactorInfo(
                            identifier = identifier,
                            username = username,
                            obfuscatedPhone = obfuscatedPhone,
                            totpEnabled = totpEnabled,
                            smsEnabled = smsEnabled
                        )
                    )
                }

                return@withContext when {
                    message != null -> DownloadResult.Error("Login fehlgeschlagen: $message")
                    else -> DownloadResult.Error("Login fehlgeschlagen: Ungültiger Benutzername oder Passwort")
                }
            }

            // Update CSRF token after login
            val postLoginCookies = cookieStorage.get(Url(BASE_URL))
            csrfToken = postLoginCookies.find { it.name == "csrftoken" }?.value ?: csrfToken
            sessionUserId = userId
            isLoggedIn = true

            DownloadResult.Success("Erfolgreich eingeloggt als $username")
        } catch (e: Exception) {
            DownloadResult.Error("Login-Fehler: ${e.message}")
        }
    }

    /**
     * Verifies the 2FA code to complete login.
     * Supports both TOTP (authenticator app) and SMS codes.
     */
    suspend fun verifyTwoFactor(
        code: String,
        identifier: String,
        username: String,
        useTOTP: Boolean = true
    ): DownloadResult<String> = withContext(Dispatchers.IO) {
        try {
            val verificationMethod = if (useTOTP) "1" else "1" // 1 = TOTP/SMS code

            val response = client.post("$BASE_URL/accounts/login/ajax/two_factor/") {
                headers {
                    append(HttpHeaders.UserAgent, USER_AGENT)
                    append(HttpHeaders.Accept, "*/*")
                    append(HttpHeaders.AcceptLanguage, "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
                    append(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
                    append("X-CSRFToken", csrfToken)
                    append("X-IG-App-ID", IG_APP_ID)
                    append("X-ASBD-ID", "129477")
                    append("X-IG-WWW-Claim", "0")
                    append("X-Requested-With", "XMLHttpRequest")
                    append(HttpHeaders.Referrer, "$BASE_URL/accounts/login/two_factor/")
                    append(HttpHeaders.Origin, BASE_URL)
                    append("Sec-Fetch-Dest", "empty")
                    append("Sec-Fetch-Mode", "cors")
                    append("Sec-Fetch-Site", "same-origin")
                }
                setBody(FormDataContent(Parameters.build {
                    append("username", username)
                    append("verificationCode", code.replace("\\s".toRegex(), ""))
                    append("identifier", identifier)
                    append("queryParams", "{}")
                    append("trust_signal", "true")
                    append("verification_method", verificationMethod)
                }))
            }

            val body = response.bodyAsText()
            val jsonResponse = json.decodeFromString<JsonObject>(body)

            val authenticated = jsonResponse["authenticated"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val userId = jsonResponse["userId"]?.jsonPrimitive?.content ?: ""

            if (authenticated) {
                val postLoginCookies = cookieStorage.get(Url(BASE_URL))
                csrfToken = postLoginCookies.find { it.name == "csrftoken" }?.value ?: csrfToken
                sessionUserId = userId
                isLoggedIn = true
                DownloadResult.Success("Erfolgreich eingeloggt als $username")
            } else {
                val message = jsonResponse["message"]?.jsonPrimitive?.content
                DownloadResult.Error(message ?: "Ungültiger Bestätigungscode")
            }
        } catch (e: Exception) {
            DownloadResult.Error("2FA-Fehler: ${e.message}")
        }
    }

    /**
     * Logs out of the current session.
     */
    suspend fun logout(): DownloadResult<String> = withContext(Dispatchers.IO) {
        try {
            client.post("$BASE_URL/accounts/logout/ajax/") {
                headers {
                    append(HttpHeaders.UserAgent, USER_AGENT)
                    append("X-CSRFToken", csrfToken)
                    append("X-IG-App-ID", IG_APP_ID)
                    append("X-Requested-With", "XMLHttpRequest")
                    append(HttpHeaders.Referrer, "$BASE_URL/")
                }
                setBody(FormDataContent(Parameters.build {
                    append("one_tap_app_login", "0")
                }))
            }
            isLoggedIn = false
            sessionUserId = ""
            csrfToken = ""
            DownloadResult.Success("Erfolgreich ausgeloggt")
        } catch (e: Exception) {
            isLoggedIn = false
            DownloadResult.Success("Ausgeloggt")
        }
    }

    /**
     * Adds common authenticated headers to requests.
     */
    private fun HttpRequestBuilder.addAuthHeaders(referer: String = "$BASE_URL/") {
        headers {
            append(HttpHeaders.UserAgent, USER_AGENT)
            append(HttpHeaders.Accept, "*/*")
            append(HttpHeaders.AcceptLanguage, "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
            append("X-CSRFToken", csrfToken)
            append("X-IG-App-ID", IG_APP_ID)
            append("X-ASBD-ID", "129477")
            append("X-IG-WWW-Claim", "hmac.AR3W0DThY2Mu6Fl51JGrmuxBPmcavRHhGp1VhDSLynajYhY7")
            append("X-Requested-With", "XMLHttpRequest")
            append(HttpHeaders.Referrer, referer)
            append("Sec-Fetch-Dest", "empty")
            append("Sec-Fetch-Mode", "cors")
            append("Sec-Fetch-Site", "same-origin")
        }
    }

    /**
     * Adds anonymous (unauthenticated) headers for public data access.
     */
    private fun HttpRequestBuilder.addAnonHeaders(referer: String = "$BASE_URL/") {
        headers {
            append(HttpHeaders.UserAgent, USER_AGENT)
            append(HttpHeaders.Accept, "*/*")
            append(HttpHeaders.AcceptLanguage, "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
            append("X-IG-App-ID", IG_APP_ID)
            append("X-Requested-With", "XMLHttpRequest")
            append(HttpHeaders.Referrer, referer)
            append("Sec-Fetch-Dest", "empty")
            append("Sec-Fetch-Mode", "cors")
            append("Sec-Fetch-Site", "same-origin")
        }
    }

    /**
     * Fetches user profile information. Works both anonymously and authenticated.
     * Anonymous mode can access public profiles only.
     */
    suspend fun fetchUserProfile(username: String): DownloadResult<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$BASE_URL/api/v1/users/web_profile_info/") {
                parameter("username", username)
                if (isLoggedIn) addAuthHeaders("$BASE_URL/$username/")
                else addAnonHeaders("$BASE_URL/$username/")
            }

            if (response.status != HttpStatusCode.OK) {
                return@withContext DownloadResult.Error(
                    "Profil konnte nicht geladen werden (HTTP ${response.status.value})",
                    response.status.value
                )
            }

            val body = response.bodyAsText()
            val jsonResponse = json.decodeFromString<JsonObject>(body)

            val userData = jsonResponse["data"]?.jsonObject?.get("user")?.jsonObject
                ?: return@withContext DownloadResult.Error("Benutzer nicht gefunden")

            val profile = UserProfile(
                username = userData["username"]?.jsonPrimitive?.content ?: username,
                fullName = userData["full_name"]?.jsonPrimitive?.content ?: "",
                biography = userData["biography"]?.jsonPrimitive?.content ?: "",
                profilePicUrl = userData["profile_pic_url"]?.jsonPrimitive?.content ?: "",
                profilePicUrlHD = userData["profile_pic_url_hd"]?.jsonPrimitive?.content ?: "",
                isPrivate = userData["is_private"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                followerCount = userData["edge_followed_by"]?.jsonObject?.get("count")?.jsonPrimitive?.longOrNull ?: 0,
                followingCount = userData["edge_follow"]?.jsonObject?.get("count")?.jsonPrimitive?.longOrNull ?: 0,
                postCount = userData["edge_owner_to_timeline_media"]?.jsonObject?.get("count")?.jsonPrimitive?.longOrNull ?: 0,
                userId = userData["id"]?.jsonPrimitive?.content ?: ""
            )

            DownloadResult.Success(profile)
        } catch (e: Exception) {
            DownloadResult.Error("Fehler beim Laden des Profils: ${e.message}")
        }
    }

    /**
     * Fetches stories for a user using the authenticated session.
     */
    suspend fun fetchStories(userId: String): DownloadResult<List<StoryItem>> = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$BASE_URL/api/v1/feed/reels_media/") {
                parameter("reel_ids", userId)
                addAuthHeaders()
            }

            if (response.status != HttpStatusCode.OK) {
                return@withContext DownloadResult.Error(
                    "Stories konnten nicht geladen werden (HTTP ${response.status.value})",
                    response.status.value
                )
            }

            val body = response.bodyAsText()
            val jsonResponse = json.decodeFromString<JsonObject>(body)

            val reels = jsonResponse["reels"]?.jsonObject
            val reelData = reels?.get(userId)?.jsonObject
                ?: jsonResponse["reels_media"]?.jsonArray?.firstOrNull()?.jsonObject

            if (reelData == null) {
                return@withContext DownloadResult.Success(emptyList())
            }

            val items = reelData["items"]?.jsonArray?.map { itemJson ->
                val item = itemJson.jsonObject
                val mediaType = item["media_type"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
                val isVideo = mediaType == 2

                val imageUrl = item["image_versions2"]?.jsonObject
                    ?.get("candidates")?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("url")?.jsonPrimitive?.content ?: ""

                val videoUrl = if (isVideo) {
                    item["video_versions"]?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?.get("url")?.jsonPrimitive?.content ?: ""
                } else ""

                StoryItem(
                    id = item["id"]?.jsonPrimitive?.content ?: item["pk"]?.jsonPrimitive?.content ?: "",
                    mediaUrl = if (isVideo) videoUrl else imageUrl,
                    thumbnailUrl = imageUrl,
                    type = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                    timestamp = item["taken_at"]?.jsonPrimitive?.longOrNull ?: 0,
                    expiringAt = item["expiring_at"]?.jsonPrimitive?.longOrNull ?: 0
                )
            } ?: emptyList()

            DownloadResult.Success(items)
        } catch (e: Exception) {
            DownloadResult.Error("Fehler beim Laden der Stories: ${e.message}")
        }
    }

    /**
     * Fetches highlight reels for a user using the authenticated session.
     */
    suspend fun fetchHighlights(userId: String): DownloadResult<List<HighlightReel>> = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$BASE_URL/api/v1/highlights/$userId/highlights_tray/") {
                addAuthHeaders()
            }

            if (response.status != HttpStatusCode.OK) {
                return@withContext DownloadResult.Error(
                    "Highlights konnten nicht geladen werden (HTTP ${response.status.value})",
                    response.status.value
                )
            }

            val body = response.bodyAsText()
            val jsonResponse = json.decodeFromString<JsonObject>(body)

            val tray = jsonResponse["tray"]?.jsonArray
            if (tray.isNullOrEmpty()) {
                return@withContext DownloadResult.Success(emptyList())
            }

            val highlights = tray.map { highlightJson ->
                val highlight = highlightJson.jsonObject
                val coverUrl = highlight["cover_media"]?.jsonObject
                    ?.get("cropped_image_version")?.jsonObject
                    ?.get("url")?.jsonPrimitive?.content ?: ""

                HighlightReel(
                    id = highlight["id"]?.jsonPrimitive?.content ?: "",
                    title = highlight["title"]?.jsonPrimitive?.content ?: "",
                    coverImageUrl = coverUrl
                )
            }

            DownloadResult.Success(highlights)
        } catch (e: Exception) {
            DownloadResult.Error("Fehler beim Laden der Highlights: ${e.message}")
        }
    }

    /**
     * Fetches items within a highlight reel using the authenticated session.
     */
    suspend fun fetchHighlightItems(highlightId: String): DownloadResult<List<StoryItem>> = withContext(Dispatchers.IO) {
        try {
            val reelId = if (highlightId.startsWith("highlight:")) highlightId else "highlight:$highlightId"

            val response = client.get("$BASE_URL/api/v1/feed/reels_media/") {
                parameter("reel_ids", reelId)
                addAuthHeaders()
            }

            if (response.status != HttpStatusCode.OK) {
                return@withContext DownloadResult.Error(
                    "Highlight-Inhalte konnten nicht geladen werden (HTTP ${response.status.value})",
                    response.status.value
                )
            }

            val body = response.bodyAsText()
            val jsonResponse = json.decodeFromString<JsonObject>(body)

            val reels = jsonResponse["reels"]?.jsonObject
            val reelData = reels?.get(reelId)?.jsonObject
                ?: jsonResponse["reels_media"]?.jsonArray?.firstOrNull()?.jsonObject

            if (reelData == null) {
                return@withContext DownloadResult.Success(emptyList())
            }

            val items = reelData["items"]?.jsonArray?.map { itemJson ->
                val item = itemJson.jsonObject
                val mediaType = item["media_type"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
                val isVideo = mediaType == 2

                val imageUrl = item["image_versions2"]?.jsonObject
                    ?.get("candidates")?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("url")?.jsonPrimitive?.content ?: ""

                val videoUrl = if (isVideo) {
                    item["video_versions"]?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?.get("url")?.jsonPrimitive?.content ?: ""
                } else ""

                StoryItem(
                    id = item["id"]?.jsonPrimitive?.content ?: item["pk"]?.jsonPrimitive?.content ?: "",
                    mediaUrl = if (isVideo) videoUrl else imageUrl,
                    thumbnailUrl = imageUrl,
                    type = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                    timestamp = item["taken_at"]?.jsonPrimitive?.longOrNull ?: 0,
                    expiringAt = item["expiring_at"]?.jsonPrimitive?.longOrNull ?: 0
                )
            } ?: emptyList()

            DownloadResult.Success(items)
        } catch (e: Exception) {
            DownloadResult.Error("Fehler beim Laden der Highlight-Inhalte: ${e.message}")
        }
    }

    /**
     * Fetches feed posts (posted images/videos) for a user.
     * Works both anonymously (public profiles) and authenticated.
     */
    suspend fun fetchFeedPosts(username: String): DownloadResult<List<FeedPost>> = withContext(Dispatchers.IO) {
        try {
            // Use the web profile endpoint which includes edge_owner_to_timeline_media
            val response = client.get("$BASE_URL/api/v1/users/web_profile_info/") {
                parameter("username", username)
                if (isLoggedIn) addAuthHeaders("$BASE_URL/$username/")
                else addAnonHeaders("$BASE_URL/$username/")
            }

            if (response.status != HttpStatusCode.OK) {
                return@withContext DownloadResult.Error(
                    "Feed konnte nicht geladen werden (HTTP ${response.status.value})",
                    response.status.value
                )
            }

            val body = response.bodyAsText()
            val jsonResponse = json.decodeFromString<JsonObject>(body)

            val userData = jsonResponse["data"]?.jsonObject?.get("user")?.jsonObject
                ?: return@withContext DownloadResult.Error("Benutzer nicht gefunden")

            val isPrivate = userData["is_private"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            if (isPrivate && !isLoggedIn) {
                return@withContext DownloadResult.Error("Privates Profil - Login erforderlich, um Posts zu sehen")
            }

            val edges = userData["edge_owner_to_timeline_media"]?.jsonObject
                ?.get("edges")?.jsonArray

            if (edges.isNullOrEmpty()) {
                return@withContext DownloadResult.Success(emptyList())
            }

            val posts = edges.mapNotNull { edgeJson ->
                val node = edgeJson.jsonObject["node"]?.jsonObject ?: return@mapNotNull null
                val typename = node["__typename"]?.jsonPrimitive?.content ?: ""
                val isVideo = node["is_video"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                val isCarousel = typename == "GraphSidecar"

                // Collect media URLs
                val mediaUrls = mutableListOf<String>()

                if (isCarousel) {
                    // Carousel: multiple images/videos
                    val sidecarEdges = node["edge_sidecar_to_children"]?.jsonObject
                        ?.get("edges")?.jsonArray
                    sidecarEdges?.forEach { sidecarEdge ->
                        val childNode = sidecarEdge.jsonObject["node"]?.jsonObject
                        val childIsVideo = childNode?.get("is_video")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                        val url = if (childIsVideo) {
                            childNode?.get("video_url")?.jsonPrimitive?.content ?: ""
                        } else {
                            childNode?.get("display_url")?.jsonPrimitive?.content ?: ""
                        }
                        if (url.isNotEmpty()) mediaUrls.add(url)
                    }
                } else {
                    // Single image or video
                    val url = if (isVideo) {
                        node["video_url"]?.jsonPrimitive?.content ?: ""
                    } else {
                        node["display_url"]?.jsonPrimitive?.content ?: ""
                    }
                    if (url.isNotEmpty()) mediaUrls.add(url)
                }

                val caption = node["edge_media_to_caption"]?.jsonObject
                    ?.get("edges")?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("node")?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content ?: ""

                FeedPost(
                    id = node["id"]?.jsonPrimitive?.content ?: "",
                    shortcode = node["shortcode"]?.jsonPrimitive?.content ?: "",
                    mediaUrls = mediaUrls,
                    thumbnailUrl = node["thumbnail_src"]?.jsonPrimitive?.content
                        ?: node["display_url"]?.jsonPrimitive?.content ?: "",
                    type = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                    isCarousel = isCarousel,
                    caption = caption,
                    timestamp = node["taken_at_timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                    likeCount = node["edge_liked_by"]?.jsonObject?.get("count")?.jsonPrimitive?.longOrNull
                        ?: node["edge_media_preview_like"]?.jsonObject?.get("count")?.jsonPrimitive?.longOrNull ?: 0,
                    commentCount = node["edge_media_to_comment"]?.jsonObject?.get("count")?.jsonPrimitive?.longOrNull
                        ?: node["edge_media_preview_comment"]?.jsonObject?.get("count")?.jsonPrimitive?.longOrNull ?: 0
                )
            }

            DownloadResult.Success(posts)
        } catch (e: Exception) {
            DownloadResult.Error("Fehler beim Laden der Posts: ${e.message}")
        }
    }

    /**
     * Fetches archived posts of the currently logged-in user.
     * Archive is only accessible for your own account.
     */
    suspend fun fetchArchivedPosts(): DownloadResult<List<FeedPost>> = withContext(Dispatchers.IO) {
        if (!isLoggedIn) {
            return@withContext DownloadResult.Error("Login erforderlich, um auf das Archiv zuzugreifen")
        }

        try {
            val allPosts = mutableListOf<FeedPost>()
            var maxId: String? = null
            var hasMore = true

            // Paginate through archived posts
            while (hasMore) {
                val response = client.get("$BASE_URL/api/v1/feed/only_me_feed/") {
                    if (maxId != null) {
                        parameter("max_id", maxId)
                    }
                    addAuthHeaders()
                }

                if (response.status != HttpStatusCode.OK) {
                    if (allPosts.isEmpty()) {
                        return@withContext DownloadResult.Error(
                            "Archiv konnte nicht geladen werden (HTTP ${response.status.value})",
                            response.status.value
                        )
                    }
                    break
                }

                val body = response.bodyAsText()
                val jsonResponse = json.decodeFromString<JsonObject>(body)

                val items = jsonResponse["items"]?.jsonArray
                if (items.isNullOrEmpty()) break

                items.forEach { itemJson ->
                    val item = itemJson.jsonObject
                    val mediaType = item["media_type"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
                    val isVideo = mediaType == 2
                    val isCarousel = mediaType == 8

                    val mediaUrls = mutableListOf<String>()

                    if (isCarousel) {
                        val carouselMedia = item["carousel_media"]?.jsonArray
                        carouselMedia?.forEach { carouselItem ->
                            val ci = carouselItem.jsonObject
                            val ciType = ci["media_type"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
                            val ciIsVideo = ciType == 2

                            val url = if (ciIsVideo) {
                                ci["video_versions"]?.jsonArray
                                    ?.firstOrNull()?.jsonObject
                                    ?.get("url")?.jsonPrimitive?.content ?: ""
                            } else {
                                ci["image_versions2"]?.jsonObject
                                    ?.get("candidates")?.jsonArray
                                    ?.firstOrNull()?.jsonObject
                                    ?.get("url")?.jsonPrimitive?.content ?: ""
                            }
                            if (url.isNotEmpty()) mediaUrls.add(url)
                        }
                    } else {
                        val url = if (isVideo) {
                            item["video_versions"]?.jsonArray
                                ?.firstOrNull()?.jsonObject
                                ?.get("url")?.jsonPrimitive?.content ?: ""
                        } else {
                            item["image_versions2"]?.jsonObject
                                ?.get("candidates")?.jsonArray
                                ?.firstOrNull()?.jsonObject
                                ?.get("url")?.jsonPrimitive?.content ?: ""
                        }
                        if (url.isNotEmpty()) mediaUrls.add(url)
                    }

                    val thumbnailUrl = item["image_versions2"]?.jsonObject
                        ?.get("candidates")?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?.get("url")?.jsonPrimitive?.content ?: ""

                    val captionObj = item["caption"]?.jsonObject
                    val caption = captionObj?.get("text")?.jsonPrimitive?.content ?: ""

                    val code = item["code"]?.jsonPrimitive?.content ?: ""

                    allPosts.add(
                        FeedPost(
                            id = item["id"]?.jsonPrimitive?.content ?: item["pk"]?.jsonPrimitive?.content ?: "",
                            shortcode = code,
                            mediaUrls = mediaUrls,
                            thumbnailUrl = thumbnailUrl,
                            type = when {
                                isVideo -> MediaType.VIDEO
                                isCarousel -> MediaType.IMAGE
                                else -> MediaType.IMAGE
                            },
                            isCarousel = isCarousel,
                            caption = caption,
                            timestamp = item["taken_at"]?.jsonPrimitive?.longOrNull ?: 0,
                            likeCount = item["like_count"]?.jsonPrimitive?.longOrNull ?: 0,
                            commentCount = item["comment_count"]?.jsonPrimitive?.longOrNull ?: 0
                        )
                    )
                }

                // Check for more pages
                hasMore = jsonResponse["more_available"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                maxId = jsonResponse["next_max_id"]?.jsonPrimitive?.content

                // Safety limit to avoid infinite loop
                if (allPosts.size >= 500) break
            }

            DownloadResult.Success(allPosts)
        } catch (e: Exception) {
            DownloadResult.Error("Fehler beim Laden des Archivs: ${e.message}")
        }
    }

    /**
     * Downloads a file from a URL and saves it to the specified path.
     */
    suspend fun downloadFile(url: String, outputPath: String): DownloadResult<String> = withContext(Dispatchers.IO) {
        try {
            val response = client.get(url) {
                headers {
                    append(HttpHeaders.UserAgent, USER_AGENT)
                    append(HttpHeaders.Referrer, "$BASE_URL/")
                }
            }

            if (response.status != HttpStatusCode.OK) {
                return@withContext DownloadResult.Error(
                    "Download fehlgeschlagen (HTTP ${response.status.value})",
                    response.status.value
                )
            }

            val file = java.io.File(outputPath)
            file.parentFile?.mkdirs()
            file.writeBytes(response.readBytes())

            DownloadResult.Success(outputPath)
        } catch (e: Exception) {
            DownloadResult.Error("Download-Fehler: ${e.message}")
        }
    }

    fun close() {
        client.close()
    }
}
