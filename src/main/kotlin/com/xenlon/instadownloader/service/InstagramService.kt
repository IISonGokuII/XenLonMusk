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
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.IOException

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
    var preferHD: Boolean = true
    private val requestMutex = Mutex()
    private val requestTimestamps = ArrayDeque<Long>()
    private val minRequestSpacingMillis = 1200L
    private val softBurstWindowMillis = 60_000L
    private val softBurstLimit = 18
    private var nextAllowedRequestAtMillis = 0L
    private val _requestHealth = MutableStateFlow(RequestHealthState())
    val requestHealth: StateFlow<RequestHealthState> = _requestHealth.asStateFlow()

    /**
     * Picks the best image URL from a candidates array based on quality preference.
     * HD = first (largest), SD = last (smallest).
     */
    private fun pickImageCandidate(candidates: JsonArray?): String {
        if (candidates.isNullOrEmpty()) return ""
        val index = if (preferHD) 0 else candidates.size - 1
        return candidates[index].jsonObject["url"]?.jsonPrimitive?.content ?: ""
    }

    /**
     * Picks the best video URL from a video_versions array based on quality preference.
     * HD = first (largest), SD = last (smallest).
     */
    private fun pickVideoVersion(versions: JsonArray?): String {
        if (versions.isNullOrEmpty()) return ""
        val index = if (preferHD) 0 else versions.size - 1
        return versions[index].jsonObject["url"]?.jsonPrimitive?.content ?: ""
    }

    /**
     * Parses a v1 API media item (used in archive, saved, tagged, reels, shortcode endpoints)
     * and returns (mediaUrls, thumbnailUrl, isVideo, isCarousel).
     */
    private fun parseV1MediaItem(item: JsonObject): FeedPost {
        val mediaType = item["media_type"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        val isVideo = mediaType == 2
        val isCarousel = mediaType == 8

        val mediaUrls = mutableListOf<String>()

        if (isCarousel) {
            item["carousel_media"]?.jsonArray?.forEach { carouselItem ->
                val ci = carouselItem.jsonObject
                val ciType = ci["media_type"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
                val ciIsVideo = ciType == 2
                val url = if (ciIsVideo) {
                    pickVideoVersion(ci["video_versions"]?.jsonArray)
                } else {
                    pickImageCandidate(ci["image_versions2"]?.jsonObject?.get("candidates")?.jsonArray)
                }
                if (url.isNotEmpty()) mediaUrls.add(url)
            }
        } else {
            val url = if (isVideo) {
                pickVideoVersion(item["video_versions"]?.jsonArray)
            } else {
                pickImageCandidate(item["image_versions2"]?.jsonObject?.get("candidates")?.jsonArray)
            }
            if (url.isNotEmpty()) mediaUrls.add(url)
        }

        val thumbnailUrl = pickImageCandidate(
            item["image_versions2"]?.jsonObject?.get("candidates")?.jsonArray
        )

        val captionObj = item["caption"]?.jsonObject
        val caption = captionObj?.get("text")?.jsonPrimitive?.content ?: ""
        val code = item["code"]?.jsonPrimitive?.content ?: ""

        return FeedPost(
            id = item["id"]?.jsonPrimitive?.content ?: item["pk"]?.jsonPrimitive?.content ?: "",
            shortcode = code,
            mediaUrls = mediaUrls,
            thumbnailUrl = thumbnailUrl,
            type = when {
                isVideo -> MediaType.VIDEO
                else -> MediaType.IMAGE
            },
            isCarousel = isCarousel,
            caption = caption,
            timestamp = item["taken_at"]?.jsonPrimitive?.longOrNull ?: 0,
            likeCount = item["like_count"]?.jsonPrimitive?.longOrNull ?: 0,
            commentCount = item["comment_count"]?.jsonPrimitive?.longOrNull ?: 0
        )
    }

    companion object {
        private const val BASE_URL = "https://www.instagram.com"
        private const val LOGIN_URL = "$BASE_URL/accounts/login/ajax/"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val IG_APP_ID = "936619743392459"
    }

    private suspend fun awaitRequestSlot(reason: String) {
        var waitMillis: Long
        var recentCount: Int

        requestMutex.withLock {
            val now = System.currentTimeMillis()
            while (requestTimestamps.isNotEmpty() && now - requestTimestamps.first() > softBurstWindowMillis) {
                requestTimestamps.removeFirst()
            }

            recentCount = requestTimestamps.size
            val spacingWait = (nextAllowedRequestAtMillis - now).coerceAtLeast(0L)
            val burstWait = if (recentCount >= softBurstLimit) 10_000L else 0L
            waitMillis = maxOf(spacingWait, burstWait)

            if (waitMillis > 0L) {
                val cooldownUntil = now + waitMillis
                _requestHealth.value = RequestHealthState(
                    level = RequestHealthLevel.COOLDOWN,
                    message = "Schonmodus aktiv, um Login und Requests zu entschärfen.",
                    recentRequestCount = recentCount,
                    cooldownUntilMillis = cooldownUntil,
                    lastUpdatedMillis = now
                )
            } else {
                _requestHealth.value = RequestHealthState(
                    level = RequestHealthLevel.ACTIVE,
                    message = "Lädt mit reduziertem Tempo: $reason",
                    recentRequestCount = recentCount,
                    cooldownUntilMillis = 0L,
                    lastUpdatedMillis = now
                )
            }
        }

        if (waitMillis > 0L) {
            delay(waitMillis)
        }

        requestMutex.withLock {
            val now = System.currentTimeMillis()
            requestTimestamps.addLast(now)
            nextAllowedRequestAtMillis = now + minRequestSpacingMillis
            _requestHealth.value = RequestHealthState(
                level = RequestHealthLevel.ACTIVE,
                message = "Lädt vorsichtig, um Sperren und Checkpoints zu vermeiden.",
                recentRequestCount = requestTimestamps.size,
                cooldownUntilMillis = 0L,
                lastUpdatedMillis = now
            )
        }
    }

    private suspend fun updateHealthFromStatus(status: HttpStatusCode, responseLabel: String) {
        val now = System.currentTimeMillis()
        when (status) {
            HttpStatusCode.TooManyRequests -> {
                requestMutex.withLock {
                    nextAllowedRequestAtMillis = maxOf(nextAllowedRequestAtMillis, now + 60_000L)
                }
                _requestHealth.value = RequestHealthState(
                    level = RequestHealthLevel.COOLDOWN,
                    message = "Instagram limitiert gerade Anfragen. Die App wartet automatisch kurz.",
                    recentRequestCount = requestTimestamps.size,
                    cooldownUntilMillis = now + 60_000L,
                    lastUpdatedMillis = now
                )
            }
            HttpStatusCode.Forbidden, HttpStatusCode.Unauthorized -> {
                requestMutex.withLock {
                    nextAllowedRequestAtMillis = maxOf(nextAllowedRequestAtMillis, now + 30_000L)
                }
                _requestHealth.value = RequestHealthState(
                    level = RequestHealthLevel.WARNING,
                    message = "$responseLabel wurde von Instagram eingeschränkt. Bitte langsamer weiterarbeiten.",
                    recentRequestCount = requestTimestamps.size,
                    cooldownUntilMillis = now + 30_000L,
                    lastUpdatedMillis = now
                )
            }
            else -> if (status.value in 200..299) {
                _requestHealth.value = _requestHealth.value.copy(
                    level = RequestHealthLevel.ACTIVE,
                    message = "Verbindung stabil, Requests bleiben gedrosselt.",
                    cooldownUntilMillis = 0L,
                    lastUpdatedMillis = now
                )
            }
        }
    }

    private suspend fun throttledRequest(
        reason: String,
        responseLabel: String = reason,
        block: suspend () -> HttpResponse
    ): HttpResponse {
        var lastError: Exception? = null
        var lastResponse: HttpResponse? = null

        repeat(3) { attempt ->
            awaitRequestSlot(reason)
            try {
                val response = block()
                lastResponse = response
                updateHealthFromStatus(response.status, responseLabel)

                if (response.status == HttpStatusCode.TooManyRequests && attempt < 2) {
                    delay((attempt + 1) * 20_000L)
                    return@repeat
                }

                return response
            } catch (e: HttpRequestTimeoutException) {
                lastError = e
                _requestHealth.value = RequestHealthState(
                    level = RequestHealthLevel.WARNING,
                    message = "Request-Timeout bei $responseLabel. Neuer Versuch mit mehr Abstand.",
                    recentRequestCount = requestTimestamps.size,
                    cooldownUntilMillis = System.currentTimeMillis() + ((attempt + 1) * 5_000L),
                    lastUpdatedMillis = System.currentTimeMillis()
                )
                delay((attempt + 1) * 5_000L)
            } catch (e: IOException) {
                lastError = e
                delay((attempt + 1) * 3_000L)
            }
        }

        lastResponse?.let { return it }
        throw lastError ?: IllegalStateException("Unbekannter Netzwerkfehler bei $responseLabel")
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
            val initialResponse = throttledRequest("Startseite laden", "Login vorbereiten") {
                client.get(BASE_URL) {
                headers {
                    append(HttpHeaders.UserAgent, USER_AGENT)
                    append(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    append(HttpHeaders.AcceptLanguage, "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
                }
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

            val loginResponse = throttledRequest("Login senden", "Instagram-Login") {
                client.post(LOGIN_URL) {
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
                    val totpEnabled = twoFactorInfo?.get("totp_two_factor_on")?.jsonPrimitive?.let {
                        it.booleanOrNull ?: it.content.toBooleanStrictOrNull() ?: (it.content == "1")
                    } ?: false
                    val smsEnabled = twoFactorInfo?.get("sms_two_factor_on")?.jsonPrimitive?.let {
                        it.booleanOrNull ?: it.content.toBooleanStrictOrNull() ?: (it.content == "1")
                    } ?: false

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
            val verificationMethod = if (useTOTP) "3" else "1"

            val response = throttledRequest("2FA senden", "Zwei-Faktor-Bestätigung") {
                client.post("$BASE_URL/accounts/login/ajax/two_factor/") {
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
     * Requests a new SMS code for 2FA verification.
     */
    suspend fun requestSmsCode(
        username: String,
        identifier: String
    ): DownloadResult<String> = withContext(Dispatchers.IO) {
        try {
            val response = throttledRequest("2FA-SMS anfordern", "SMS-Code anfordern") {
                client.post("$BASE_URL/accounts/send_two_factor_login_sms/") {
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
                    append("identifier", identifier)
                }))
                }
            }

            val body = response.bodyAsText()
            val jsonResponse = json.decodeFromString<JsonObject>(body)

            val phoneNumber = jsonResponse["obfuscated_phone_number"]?.jsonPrimitive?.content
                ?: jsonResponse["phone_number_preview"]?.jsonPrimitive?.content
                ?: ""

            if (phoneNumber.isNotEmpty()) {
                DownloadResult.Success("SMS-Code gesendet an $phoneNumber")
            } else {
                DownloadResult.Success("SMS-Code wurde angefordert")
            }
        } catch (e: Exception) {
            DownloadResult.Error("SMS konnte nicht gesendet werden: ${e.message}")
        }
    }

    /**
     * Logs out of the current session.
     */
    suspend fun logout(): DownloadResult<String> = withContext(Dispatchers.IO) {
        try {
            throttledRequest("Logout", "Instagram-Logout") {
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
            val response = throttledRequest("Profil laden", "Profilinformationen") {
                client.get("$BASE_URL/api/v1/users/web_profile_info/") {
                parameter("username", username)
                if (isLoggedIn) addAuthHeaders("$BASE_URL/$username/")
                else addAnonHeaders("$BASE_URL/$username/")
                }
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
            val response = throttledRequest("Stories laden", "Story-Abruf") {
                client.get("$BASE_URL/api/v1/feed/reels_media/") {
                parameter("reel_ids", userId)
                addAuthHeaders()
                }
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

                val imageUrl = pickImageCandidate(
                    item["image_versions2"]?.jsonObject?.get("candidates")?.jsonArray
                )

                val videoUrl = if (isVideo) {
                    pickVideoVersion(item["video_versions"]?.jsonArray)
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
            val response = throttledRequest("Highlights laden", "Highlight-Abruf") {
                client.get("$BASE_URL/api/v1/highlights/$userId/highlights_tray/") {
                addAuthHeaders()
                }
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

            val response = throttledRequest("Highlight-Inhalte laden", "Highlight-Inhalte") {
                client.get("$BASE_URL/api/v1/feed/reels_media/") {
                parameter("reel_ids", reelId)
                addAuthHeaders()
                }
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

                val imageUrl = pickImageCandidate(
                    item["image_versions2"]?.jsonObject?.get("candidates")?.jsonArray
                )

                val videoUrl = if (isVideo) {
                    pickVideoVersion(item["video_versions"]?.jsonArray)
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
     * Uses v1 API with pagination to fetch all posts.
     * Falls back to web_profile_info for anonymous access if v1 API fails.
     */
    suspend fun fetchFeedPosts(username: String, userId: String = ""): DownloadResult<List<FeedPost>> = withContext(Dispatchers.IO) {
        try {
            // Get userId if not provided
            val effectiveUserId = userId.ifEmpty {
                val profileResult = fetchUserProfile(username)
                if (profileResult is DownloadResult.Success) profileResult.data.userId else ""
            }

            if (effectiveUserId.isEmpty()) {
                return@withContext fetchFeedPostsFromWebProfile(username)
            }

            // Use v1 API with pagination
            val allPosts = mutableListOf<FeedPost>()
            var maxId: String? = null
            var hasMore = true
            var pageCount = 0
            val maxPages = 20 // Safety limit to prevent infinite loops

            while (hasMore && pageCount < maxPages) {
                pageCount++
                val response = throttledRequest("Posts laden", "Feed-Seite $pageCount") {
                    client.get("$BASE_URL/api/v1/feed/user/$effectiveUserId/") {
                    parameter("count", "33")
                    if (maxId != null) parameter("max_id", maxId)
                    if (isLoggedIn) addAuthHeaders("$BASE_URL/$username/")
                    else addAnonHeaders("$BASE_URL/$username/")
                    }
                }

                if (response.status != HttpStatusCode.OK) {
                    if (allPosts.isEmpty()) {
                        // Fallback to web_profile_info
                        return@withContext fetchFeedPostsFromWebProfile(username)
                    }
                    break
                }

                val body = response.bodyAsText()
                val jsonResponse = json.decodeFromString<JsonObject>(body)

                val items = jsonResponse["items"]?.jsonArray
                if (items.isNullOrEmpty()) break

                items.forEach { itemJson ->
                    val post = parseV1MediaItem(itemJson.jsonObject)
                    if (post.mediaUrls.isNotEmpty()) allPosts.add(post)
                }

                hasMore = jsonResponse["more_available"]?.jsonPrimitive?.let {
                    it.booleanOrNull ?: it.content.toBooleanStrictOrNull() ?: (it.content == "1")
                } ?: false
                val newMaxId = jsonResponse["next_max_id"]?.jsonPrimitive?.content
                if (newMaxId == maxId) break // Prevent infinite loop with same max_id
                maxId = newMaxId

                if (allPosts.size >= 500) break
            }

            DownloadResult.Success(allPosts)
        } catch (e: Exception) {
            DownloadResult.Error("Fehler beim Laden der Posts: ${e.message}")
        }
    }

    /**
     * Fallback: fetches feed posts from web_profile_info (limited to first page).
     */
    private suspend fun fetchFeedPostsFromWebProfile(username: String): DownloadResult<List<FeedPost>> = withContext(Dispatchers.IO) {
        try {
            val response = throttledRequest("Öffentliche Posts laden", "Öffentlicher Feed") {
                client.get("$BASE_URL/api/v1/users/web_profile_info/") {
                parameter("username", username)
                if (isLoggedIn) addAuthHeaders("$BASE_URL/$username/")
                else addAnonHeaders("$BASE_URL/$username/")
                }
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

                val mediaUrls = mutableListOf<String>()

                if (isCarousel) {
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
                val response = throttledRequest("Archiv laden", "Archiv-Seite") {
                    client.get("$BASE_URL/api/v1/feed/only_me_feed/") {
                    if (maxId != null) {
                        parameter("max_id", maxId)
                    }
                    addAuthHeaders()
                    }
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
                    allPosts.add(parseV1MediaItem(itemJson.jsonObject))
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
     * Fetches reels (short videos) for a user using the clips endpoint.
     */
    suspend fun fetchReels(userId: String): DownloadResult<List<FeedPost>> = withContext(Dispatchers.IO) {
        if (!isLoggedIn) {
            return@withContext DownloadResult.Error("Login erforderlich, um Reels zu laden")
        }

        try {
            val allPosts = mutableListOf<FeedPost>()
            var maxId: String? = null
            var hasMore = true

            while (hasMore) {
                val response = throttledRequest("Reels laden", "Reels-Seite") {
                    client.post("$BASE_URL/api/v1/clips/user/") {
                    addAuthHeaders()
                    headers {
                        append(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
                    }
                    setBody(FormDataContent(Parameters.build {
                        append("target_user_id", userId)
                        append("page_size", "18")
                        if (maxId != null) append("max_id", maxId!!)
                        append("include_feed_video", "true")
                    }))
                    }
                }

                if (response.status != HttpStatusCode.OK) {
                    if (allPosts.isEmpty()) {
                        return@withContext DownloadResult.Error(
                            "Reels konnten nicht geladen werden (HTTP ${response.status.value})",
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
                    val media = itemJson.jsonObject["media"]?.jsonObject ?: itemJson.jsonObject
                    val post = parseV1MediaItem(media)
                    if (post.mediaUrls.isNotEmpty()) {
                        allPosts.add(post)
                    }
                }

                hasMore = jsonResponse["paging_info"]?.jsonObject
                    ?.get("more_available")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                maxId = jsonResponse["paging_info"]?.jsonObject
                    ?.get("max_id")?.jsonPrimitive?.content

                if (allPosts.size >= 200) break
            }

            DownloadResult.Success(allPosts)
        } catch (e: Exception) {
            DownloadResult.Error("Fehler beim Laden der Reels: ${e.message}")
        }
    }

    /**
     * Fetches saved/bookmarked posts for the logged-in user.
     */
    suspend fun fetchSavedPosts(): DownloadResult<List<FeedPost>> = withContext(Dispatchers.IO) {
        if (!isLoggedIn) {
            return@withContext DownloadResult.Error("Login erforderlich, um gespeicherte Posts zu laden")
        }

        try {
            val allPosts = mutableListOf<FeedPost>()
            var maxId: String? = null
            var hasMore = true

            while (hasMore) {
                val response = throttledRequest("Gespeicherte Posts laden", "Gespeicherte Posts") {
                    client.get("$BASE_URL/api/v1/feed/saved/posts/") {
                    if (maxId != null) parameter("max_id", maxId)
                    addAuthHeaders()
                    }
                }

                if (response.status != HttpStatusCode.OK) {
                    if (allPosts.isEmpty()) {
                        return@withContext DownloadResult.Error(
                            "Gespeicherte Posts konnten nicht geladen werden (HTTP ${response.status.value})",
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
                    val media = itemJson.jsonObject["media"]?.jsonObject ?: itemJson.jsonObject
                    allPosts.add(parseV1MediaItem(media))
                }

                hasMore = jsonResponse["more_available"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                maxId = jsonResponse["next_max_id"]?.jsonPrimitive?.content

                if (allPosts.size >= 500) break
            }

            DownloadResult.Success(allPosts)
        } catch (e: Exception) {
            DownloadResult.Error("Fehler beim Laden der gespeicherten Posts: ${e.message}")
        }
    }

    /**
     * Fetches posts where the user is tagged.
     */
    suspend fun fetchTaggedPosts(userId: String): DownloadResult<List<FeedPost>> = withContext(Dispatchers.IO) {
        if (!isLoggedIn) {
            return@withContext DownloadResult.Error("Login erforderlich, um markierte Posts zu laden")
        }

        try {
            val allPosts = mutableListOf<FeedPost>()
            var maxId: String? = null
            var hasMore = true

            while (hasMore) {
                val response = throttledRequest("Markierte Posts laden", "Markierte Posts") {
                    client.get("$BASE_URL/api/v1/usertags/$userId/feed/") {
                    if (maxId != null) parameter("max_id", maxId)
                    addAuthHeaders()
                    }
                }

                if (response.status != HttpStatusCode.OK) {
                    if (allPosts.isEmpty()) {
                        return@withContext DownloadResult.Error(
                            "Markierte Posts konnten nicht geladen werden (HTTP ${response.status.value})",
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
                    allPosts.add(parseV1MediaItem(itemJson.jsonObject))
                }

                hasMore = jsonResponse["more_available"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                maxId = jsonResponse["next_max_id"]?.jsonPrimitive?.content

                if (allPosts.size >= 500) break
            }

            DownloadResult.Success(allPosts)
        } catch (e: Exception) {
            DownloadResult.Error("Fehler beim Laden der markierten Posts: ${e.message}")
        }
    }

    /**
     * Extracts a post shortcode from an Instagram URL.
     * Supports: /p/SHORTCODE/, /reel/SHORTCODE/, /tv/SHORTCODE/
     */
    fun extractShortcodeFromUrl(url: String): String? {
        val regex = Regex("""instagram\.com/(?:p|reel|tv)/([A-Za-z0-9_-]+)""")
        return regex.find(url)?.groupValues?.get(1)
    }

    /**
     * Extracts a username from an Instagram profile URL.
     */
    fun extractUsernameFromUrl(url: String): String? {
        val regex = Regex("""instagram\.com/([A-Za-z0-9._]+)/?(?:\?|$)""")
        val match = regex.find(url) ?: return null
        val name = match.groupValues[1]
        if (name in listOf("p", "reel", "tv", "stories", "explore", "accounts")) return null
        return name
    }

    /**
     * Fetches a single post by its shortcode (for shared links).
     */
    suspend fun fetchPostByShortcode(shortcode: String): DownloadResult<FeedPost> = withContext(Dispatchers.IO) {
        try {
            val response = throttledRequest("Geteilten Post laden", "Post per Shortcode") {
                client.get("$BASE_URL/api/v1/media/${shortcode}/info/") {
                if (isLoggedIn) addAuthHeaders("$BASE_URL/p/$shortcode/")
                else addAnonHeaders("$BASE_URL/p/$shortcode/")
                }
            }

            if (response.status != HttpStatusCode.OK) {
                // Fallback: try GraphQL endpoint
                return@withContext fetchPostByShortcodeGraphQL(shortcode)
            }

            val body = response.bodyAsText()
            val jsonResponse = json.decodeFromString<JsonObject>(body)

            val items = jsonResponse["items"]?.jsonArray
            val item = items?.firstOrNull()?.jsonObject
                ?: return@withContext DownloadResult.Error("Post nicht gefunden")

            val post = parseV1MediaItem(item).copy(shortcode = shortcode)
            DownloadResult.Success(post)
        } catch (e: Exception) {
            DownloadResult.Error("Fehler beim Laden des Posts: ${e.message}")
        }
    }

    private suspend fun fetchPostByShortcodeGraphQL(shortcode: String): DownloadResult<FeedPost> = withContext(Dispatchers.IO) {
        try {
            val response = throttledRequest("GraphQL-Fallback laden", "Post-Fallback") {
                client.get("$BASE_URL/p/$shortcode/?__a=1&__d=dis") {
                if (isLoggedIn) addAuthHeaders("$BASE_URL/p/$shortcode/")
                else addAnonHeaders("$BASE_URL/p/$shortcode/")
                }
            }

            if (response.status != HttpStatusCode.OK) {
                return@withContext DownloadResult.Error("Post nicht gefunden (HTTP ${response.status.value})")
            }

            val body = response.bodyAsText()
            val jsonResponse = json.decodeFromString<JsonObject>(body)

            val node = jsonResponse["graphql"]?.jsonObject?.get("shortcode_media")?.jsonObject
                ?: jsonResponse["data"]?.jsonObject?.get("shortcode_media")?.jsonObject
                ?: return@withContext DownloadResult.Error("Post-Daten nicht gefunden")

            val isVideo = node["is_video"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val isCarousel = node["__typename"]?.jsonPrimitive?.content == "GraphSidecar"

            val mediaUrls = mutableListOf<String>()

            if (isCarousel) {
                val edges = node["edge_sidecar_to_children"]?.jsonObject?.get("edges")?.jsonArray
                edges?.forEach { edge ->
                    val childNode = edge.jsonObject["node"]?.jsonObject
                    val childIsVideo = childNode?.get("is_video")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val url = if (childIsVideo) {
                        childNode?.get("video_url")?.jsonPrimitive?.content ?: ""
                    } else {
                        childNode?.get("display_url")?.jsonPrimitive?.content ?: ""
                    }
                    if (url.isNotEmpty()) mediaUrls.add(url)
                }
            } else {
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

            DownloadResult.Success(
                FeedPost(
                    id = node["id"]?.jsonPrimitive?.content ?: "",
                    shortcode = shortcode,
                    mediaUrls = mediaUrls,
                    thumbnailUrl = node["display_url"]?.jsonPrimitive?.content ?: "",
                    type = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                    isCarousel = isCarousel,
                    caption = caption,
                    timestamp = node["taken_at_timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                    likeCount = node["edge_media_preview_like"]?.jsonObject?.get("count")?.jsonPrimitive?.longOrNull ?: 0,
                    commentCount = node["edge_media_preview_comment"]?.jsonObject?.get("count")?.jsonPrimitive?.longOrNull ?: 0
                )
            )
        } catch (e: Exception) {
            DownloadResult.Error("Fehler beim Laden des Posts: ${e.message}")
        }
    }

    /**
     * Downloads a file from a URL and saves it to the specified path.
     */
    suspend fun downloadFile(url: String, outputPath: String): DownloadResult<String> = withContext(Dispatchers.IO) {
        try {
            val response = throttledRequest("Datei herunterladen", "Mediendownload") {
                client.get(url) {
                headers {
                    append(HttpHeaders.UserAgent, USER_AGENT)
                    append(HttpHeaders.Accept, "image/webp,image/apng,image/*,video/*,*/*;q=0.8")
                    append(HttpHeaders.AcceptLanguage, "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
                    append(HttpHeaders.Referrer, "$BASE_URL/")
                    append("Sec-Fetch-Dest", "image")
                    append("Sec-Fetch-Mode", "no-cors")
                    append("Sec-Fetch-Site", "cross-site")
                }
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
