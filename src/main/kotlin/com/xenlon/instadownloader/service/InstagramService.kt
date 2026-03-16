package com.xenlon.instadownloader.service

import android.content.Context
import com.xenlon.instadownloader.model.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.IOException

/**
 * Service for fetching Instagram data using session-based authentication.
 * Logs in with username/password and uses the session cookies to access data.
 */
class InstagramService(
    private val appContext: Context? = null,
) {

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
    private var wwwClaim: String = "0"
    var preferHD: Boolean = true
    private val requestMutex = Mutex()
    private val requestTimestamps = ArrayDeque<Long>()
    private val minRequestSpacingMillis = 2500L
    private val softBurstWindowMillis = 60_000L
    private val softBurstLimit = 10
    private var nextAllowedRequestAtMillis = 0L
    private val _requestHealth = MutableStateFlow(RequestHealthState())
    val requestHealth: StateFlow<RequestHealthState> = _requestHealth.asStateFlow()
    private val sessionPrefs = appContext?.getSharedPreferences("instagram_session", Context.MODE_PRIVATE)

    @kotlinx.serialization.Serializable
    private data class StoredCookie(
        val name: String,
        val value: String,
        val domain: String? = null,
        val path: String? = null,
        val secure: Boolean = false,
        val httpOnly: Boolean = false,
        val expiresTimestamp: Long? = null,
    )

    init {
        if (sessionPrefs != null) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                restorePersistedSession()
            }
        }
    }

    private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray

    private fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

    private fun JsonElement?.longOrNull(): Long? = (this as? JsonPrimitive)?.longOrNull

    private fun JsonElement?.booleanOrNull(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.objectAt(key: String): JsonObject? = this[key].asObjectOrNull()

    private fun JsonObject.arrayAt(key: String): JsonArray? = this[key].asArrayOrNull()

    private fun JsonObject.stringAt(key: String): String? = this[key].stringOrNull()

    private fun JsonObject.longAt(key: String): Long? = this[key].longOrNull()

    private fun JsonObject.booleanAt(key: String): Boolean? = this[key].booleanOrNull()

    /**
     * Picks the best image URL from a candidates array based on quality preference.
     * HD = first (largest), SD = last (smallest).
     */
    private fun pickImageCandidate(candidates: JsonArray?): String {
        if (candidates.isNullOrEmpty()) return ""
        val index = if (preferHD) 0 else candidates.size - 1
        return candidates.getOrNull(index).asObjectOrNull()?.stringAt("url") ?: ""
    }

    /**
     * Picks the best video URL from a video_versions array based on quality preference.
     * HD = first (largest), SD = last (smallest).
     */
    private fun pickVideoVersion(versions: JsonArray?): String {
        if (versions.isNullOrEmpty()) return ""
        val index = if (preferHD) 0 else versions.size - 1
        return versions.getOrNull(index).asObjectOrNull()?.stringAt("url") ?: ""
    }

    /**
     * Parses a v1 API media item (used in archive, saved, tagged, reels, shortcode endpoints)
     * and returns (mediaUrls, thumbnailUrl, isVideo, isCarousel).
     */
    private fun parseV1MediaItem(item: JsonObject): FeedPost {
        val mediaType = item.stringAt("media_type")?.toIntOrNull() ?: 1
        val isVideo = mediaType == 2
        val isCarousel = mediaType == 8

        val mediaUrls = mutableListOf<String>()

        if (isCarousel) {
            item.arrayAt("carousel_media")?.forEach { carouselItem ->
                val ci = carouselItem.asObjectOrNull() ?: return@forEach
                val ciType = ci.stringAt("media_type")?.toIntOrNull() ?: 1
                val ciIsVideo = ciType == 2
                val url = if (ciIsVideo) {
                    pickVideoVersion(ci.arrayAt("video_versions"))
                } else {
                    pickImageCandidate(ci.objectAt("image_versions2")?.arrayAt("candidates"))
                }
                if (url.isNotEmpty()) mediaUrls.add(url)
            }
        } else {
            val url = if (isVideo) {
                pickVideoVersion(item.arrayAt("video_versions"))
            } else {
                pickImageCandidate(item.objectAt("image_versions2")?.arrayAt("candidates"))
            }
            if (url.isNotEmpty()) mediaUrls.add(url)
        }

        val thumbnailUrl = pickImageCandidate(
            item.objectAt("image_versions2")?.arrayAt("candidates")
        )

        val caption = item.objectAt("caption")?.stringAt("text") ?: ""
        val code = item.stringAt("code") ?: ""

        return FeedPost(
            id = item.stringAt("id") ?: item.stringAt("pk") ?: "",
            shortcode = code,
            mediaUrls = mediaUrls,
            thumbnailUrl = thumbnailUrl,
            type = when {
                isVideo -> MediaType.VIDEO
                else -> MediaType.IMAGE
            },
            isCarousel = isCarousel,
            caption = caption,
            timestamp = item.longAt("taken_at") ?: 0,
            likeCount = item.longAt("like_count") ?: 0,
            commentCount = item.longAt("comment_count") ?: 0
        )
    }

    companion object {
        private const val BASE_URL = "https://www.instagram.com"
        private const val LOGIN_URL = "$BASE_URL/accounts/login/ajax/"
        private const val IG_APP_ID = "936619743392459"
        private const val SESSION_COOKIES_KEY = "session_cookies"
        private const val SESSION_USERNAME_KEY = "session_username"

        /**
         * Lightweight CDN download - no session, no throttling, no cookie storage needed.
         * Use this for DownloadWorker to avoid creating a full InstagramService per download.
         */
        suspend fun downloadCdnFile(url: String, outputPath: String): DownloadResult<String> = withContext(Dispatchers.IO) {
            val cdnClient = HttpClient(OkHttp) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 60_000
                    connectTimeoutMillis = 15_000
                }
            }
            try {
                val response = cdnClient.get(url) {
                    headers {
                        append(HttpHeaders.UserAgent, USER_AGENTS.random())
                        append(HttpHeaders.Accept, "image/webp,image/apng,image/*,video/*,*/*;q=0.8")
                        append(HttpHeaders.AcceptLanguage, "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
                        append(HttpHeaders.Referrer, "https://www.instagram.com/")
                        append("Sec-Fetch-Dest", "image")
                        append("Sec-Fetch-Mode", "no-cors")
                        append("Sec-Fetch-Site", "cross-site")
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
                response.bodyAsChannel().toInputStream().use { input ->
                    file.outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                }

                DownloadResult.Success(outputPath)
            } catch (e: Exception) {
                DownloadResult.Error("Download-Fehler: ${e.message}")
            } finally {
                cdnClient.close()
            }
        }

        private val USER_AGENTS = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        )
    }

    // Pick a consistent UA per session (not per request - that would be suspicious)
    private val sessionUserAgent = USER_AGENTS.random()

    private suspend fun restorePersistedSession() {
        val prefs = sessionPrefs ?: return
        val storedCookies = prefs.getString(SESSION_COOKIES_KEY, null).orEmpty()
        if (storedCookies.isBlank()) return

        val cookies = runCatching {
            json.decodeFromString<List<StoredCookie>>(storedCookies)
        }.getOrNull().orEmpty()

        if (cookies.isEmpty()) return

        cookies.forEach { stored ->
            cookieStorage.addCookie(
                Url(BASE_URL),
                Cookie(
                    name = stored.name,
                    value = stored.value,
                    domain = stored.domain,
                    path = stored.path ?: "/",
                    secure = stored.secure,
                    httpOnly = stored.httpOnly,
                    expires = stored.expiresTimestamp?.let { GMTDate(it) },
                )
            )
        }

        val restoredCookies = cookieStorage.get(Url(BASE_URL))
        csrfToken = restoredCookies.find { it.name == "csrftoken" }?.value.orEmpty()
        sessionUserId = restoredCookies.find { it.name == "ds_user_id" }?.value.orEmpty()
        isLoggedIn = restoredCookies.any { it.name == "sessionid" && it.value.isNotBlank() }
    }

    private suspend fun persistSession(username: String) {
        val prefs = sessionPrefs ?: return
        val cookies = cookieStorage.get(Url(BASE_URL))
        val serialized = cookies
            .filter { it.value.isNotBlank() }
            .map { cookie ->
                StoredCookie(
                    name = cookie.name,
                    value = cookie.value,
                    domain = cookie.domain,
                    path = cookie.path,
                    secure = cookie.secure,
                    httpOnly = cookie.httpOnly,
                    expiresTimestamp = cookie.expires?.timestamp,
                )
            }

        prefs.edit()
            .putString(SESSION_COOKIES_KEY, json.encodeToString(serialized))
            .putString(SESSION_USERNAME_KEY, username)
            .apply()
    }

    private fun clearPersistedSession() {
        sessionPrefs?.edit()
            ?.remove(SESSION_COOKIES_KEY)
            ?.remove(SESSION_USERNAME_KEY)
            ?.apply()
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

    private fun extractWwwClaim(response: HttpResponse) {
        response.headers["x-ig-set-www-claim"]?.let { claim ->
            if (claim.isNotBlank()) wwwClaim = claim
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
                extractWwwClaim(response)
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
                    append(HttpHeaders.UserAgent, sessionUserAgent)
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
                    append(HttpHeaders.UserAgent, sessionUserAgent)
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
            persistSession(username)

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
                    append(HttpHeaders.UserAgent, sessionUserAgent)
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
                persistSession(username)
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
                    append(HttpHeaders.UserAgent, sessionUserAgent)
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
                    append(HttpHeaders.UserAgent, sessionUserAgent)
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
            clearPersistedSession()
            DownloadResult.Success("Erfolgreich ausgeloggt")
        } catch (e: Exception) {
            isLoggedIn = false
            clearPersistedSession()
            DownloadResult.Success("Ausgeloggt")
        }
    }

    /**
     * Adds common authenticated headers to requests.
     */
    private fun HttpRequestBuilder.addAuthHeaders(referer: String = "$BASE_URL/") {
        headers {
            append(HttpHeaders.UserAgent, sessionUserAgent)
            append(HttpHeaders.Accept, "*/*")
            append(HttpHeaders.AcceptLanguage, "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
            append("X-CSRFToken", csrfToken)
            append("X-IG-App-ID", IG_APP_ID)
            append("X-ASBD-ID", "129477")
            append("X-IG-WWW-Claim", wwwClaim)
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
            append(HttpHeaders.UserAgent, sessionUserAgent)
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

            val userData = jsonResponse.objectAt("data")?.objectAt("user")
                ?: return@withContext DownloadResult.Error("Benutzer nicht gefunden")

            val profile = UserProfile(
                username = userData.stringAt("username") ?: username,
                fullName = userData.stringAt("full_name") ?: "",
                biography = userData.stringAt("biography") ?: "",
                profilePicUrl = userData.stringAt("profile_pic_url") ?: "",
                profilePicUrlHD = userData.stringAt("profile_pic_url_hd") ?: "",
                isPrivate = userData.booleanAt("is_private")
                    ?: userData.stringAt("is_private")?.toBooleanStrictOrNull()
                    ?: false,
                followerCount = userData.objectAt("edge_followed_by")?.longAt("count") ?: 0,
                followingCount = userData.objectAt("edge_follow")?.longAt("count") ?: 0,
                postCount = userData.objectAt("edge_owner_to_timeline_media")?.longAt("count") ?: 0,
                userId = userData.stringAt("id") ?: ""
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

            val reels = jsonResponse.objectAt("reels")
            val reelData = reels?.get(userId).asObjectOrNull()
                ?: jsonResponse.arrayAt("reels_media")?.firstOrNull().asObjectOrNull()

            if (reelData == null) {
                return@withContext DownloadResult.Success(emptyList())
            }

            val items = reelData.arrayAt("items")?.mapNotNull { itemJson ->
                val item = itemJson.asObjectOrNull() ?: return@mapNotNull null
                val mediaType = item.stringAt("media_type")?.toIntOrNull() ?: 1
                val isVideo = mediaType == 2

                val imageUrl = pickImageCandidate(
                    item.objectAt("image_versions2")?.arrayAt("candidates")
                )

                val videoUrl = if (isVideo) {
                    pickVideoVersion(item.arrayAt("video_versions"))
                } else ""

                StoryItem(
                    id = item.stringAt("id") ?: item.stringAt("pk") ?: "",
                    mediaUrl = if (isVideo) videoUrl else imageUrl,
                    thumbnailUrl = imageUrl,
                    type = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                    timestamp = item.longAt("taken_at") ?: 0,
                    expiringAt = item.longAt("expiring_at") ?: 0
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

            val tray = jsonResponse.arrayAt("tray")
            if (tray.isNullOrEmpty()) {
                return@withContext DownloadResult.Success(emptyList())
            }

            val highlights = tray.mapNotNull { highlightJson ->
                val highlight = highlightJson.asObjectOrNull() ?: return@mapNotNull null
                val coverUrl = highlight.objectAt("cover_media")
                    ?.objectAt("cropped_image_version")
                    ?.stringAt("url") ?: ""

                HighlightReel(
                    id = highlight.stringAt("id") ?: "",
                    title = highlight.stringAt("title") ?: "",
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

            val reels = jsonResponse.objectAt("reels")
            val reelData = reels?.get(reelId).asObjectOrNull()
                ?: jsonResponse.arrayAt("reels_media")?.firstOrNull().asObjectOrNull()

            if (reelData == null) {
                return@withContext DownloadResult.Success(emptyList())
            }

            val items = reelData.arrayAt("items")?.mapNotNull { itemJson ->
                val item = itemJson.asObjectOrNull() ?: return@mapNotNull null
                val mediaType = item.stringAt("media_type")?.toIntOrNull() ?: 1
                val isVideo = mediaType == 2

                val imageUrl = pickImageCandidate(
                    item.objectAt("image_versions2")?.arrayAt("candidates")
                )

                val videoUrl = if (isVideo) {
                    pickVideoVersion(item.arrayAt("video_versions"))
                } else ""

                StoryItem(
                    id = item.stringAt("id") ?: item.stringAt("pk") ?: "",
                    mediaUrl = if (isVideo) videoUrl else imageUrl,
                    thumbnailUrl = imageUrl,
                    type = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                    timestamp = item.longAt("taken_at") ?: 0,
                    expiringAt = item.longAt("expiring_at") ?: 0
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
            val maxPages = 15 // Conservative limit to avoid triggering automated behavior detection

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

                val items = jsonResponse.arrayAt("items")
                if (items.isNullOrEmpty()) break

                items.forEach { itemJson ->
                    val item = itemJson.asObjectOrNull() ?: return@forEach
                    val post = parseV1MediaItem(item)
                    if (post.mediaUrls.isNotEmpty()) allPosts.add(post)
                }

                hasMore = jsonResponse.booleanAt("more_available")
                    ?: jsonResponse.stringAt("more_available")?.toBooleanStrictOrNull()
                    ?: (jsonResponse.stringAt("more_available") == "1")
                val newMaxId = jsonResponse.stringAt("next_max_id")
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

            val userData = jsonResponse.objectAt("data")?.objectAt("user")
                ?: return@withContext DownloadResult.Error("Benutzer nicht gefunden")

            val isPrivate = userData.booleanAt("is_private")
                ?: userData.stringAt("is_private")?.toBooleanStrictOrNull()
                ?: false
            if (isPrivate && !isLoggedIn) {
                return@withContext DownloadResult.Error("Privates Profil - Login erforderlich, um Posts zu sehen")
            }

            val edges = userData.objectAt("edge_owner_to_timeline_media")
                ?.arrayAt("edges")

            if (edges.isNullOrEmpty()) {
                return@withContext DownloadResult.Success(emptyList())
            }

            val posts = edges.mapNotNull { edgeJson ->
                val node = edgeJson.asObjectOrNull()?.objectAt("node") ?: return@mapNotNull null
                val typename = node.stringAt("__typename") ?: ""
                val isVideo = node.booleanAt("is_video")
                    ?: node.stringAt("is_video")?.toBooleanStrictOrNull()
                    ?: false
                val isCarousel = typename == "GraphSidecar"

                val mediaUrls = mutableListOf<String>()

                if (isCarousel) {
                    val sidecarEdges = node.objectAt("edge_sidecar_to_children")
                        ?.arrayAt("edges")
                    sidecarEdges?.forEach { sidecarEdge ->
                        val childNode = sidecarEdge.asObjectOrNull()?.objectAt("node")
                        val childIsVideo = childNode?.booleanAt("is_video")
                            ?: childNode?.stringAt("is_video")?.toBooleanStrictOrNull()
                            ?: false
                        val url = if (childIsVideo) {
                            childNode?.stringAt("video_url") ?: ""
                        } else {
                            childNode?.stringAt("display_url") ?: ""
                        }
                        if (url.isNotEmpty()) mediaUrls.add(url)
                    }
                } else {
                    val url = if (isVideo) {
                        node.stringAt("video_url") ?: ""
                    } else {
                        node.stringAt("display_url") ?: ""
                    }
                    if (url.isNotEmpty()) mediaUrls.add(url)
                }

                val caption = node.objectAt("edge_media_to_caption")
                    ?.arrayAt("edges")
                    ?.firstOrNull().asObjectOrNull()
                    ?.objectAt("node")
                    ?.stringAt("text") ?: ""

                FeedPost(
                    id = node.stringAt("id") ?: "",
                    shortcode = node.stringAt("shortcode") ?: "",
                    mediaUrls = mediaUrls,
                    thumbnailUrl = node.stringAt("thumbnail_src")
                        ?: node.stringAt("display_url") ?: "",
                    type = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                    isCarousel = isCarousel,
                    caption = caption,
                    timestamp = node.longAt("taken_at_timestamp") ?: 0,
                    likeCount = node.objectAt("edge_liked_by")?.longAt("count")
                        ?: node.objectAt("edge_media_preview_like")?.longAt("count") ?: 0,
                    commentCount = node.objectAt("edge_media_to_comment")?.longAt("count")
                        ?: node.objectAt("edge_media_preview_comment")?.longAt("count") ?: 0
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
            var pageCount = 0

            while (hasMore && pageCount < 15) {
                pageCount++
                val response = throttledRequest("Archiv laden", "Archiv-Seite $pageCount") {
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

                val items = jsonResponse.arrayAt("items")
                if (items.isNullOrEmpty()) break

                items.forEach { itemJson ->
                    val item = itemJson.asObjectOrNull() ?: return@forEach
                    allPosts.add(parseV1MediaItem(item))
                }

                hasMore = jsonResponse.booleanAt("more_available")
                    ?: jsonResponse.stringAt("more_available")?.toBooleanStrictOrNull()
                    ?: false
                val newMaxId = jsonResponse.stringAt("next_max_id")
                if (newMaxId == maxId || newMaxId == null) break
                maxId = newMaxId

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
            var pageCount = 0

            while (hasMore && pageCount < 10) {
                pageCount++
                val response = throttledRequest("Reels laden", "Reels-Seite $pageCount") {
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

                val items = jsonResponse.arrayAt("items")
                if (items.isNullOrEmpty()) break

                items.forEach { itemJson ->
                    val item = itemJson.asObjectOrNull() ?: return@forEach
                    val media = item.objectAt("media") ?: item
                    val post = parseV1MediaItem(media)
                    if (post.mediaUrls.isNotEmpty()) {
                        allPosts.add(post)
                    }
                }

                hasMore = jsonResponse.objectAt("paging_info")?.booleanAt("more_available")
                    ?: jsonResponse.objectAt("paging_info")?.stringAt("more_available")?.toBooleanStrictOrNull()
                    ?: false
                val newMaxId = jsonResponse.objectAt("paging_info")?.stringAt("max_id")
                if (newMaxId == maxId || newMaxId == null) break
                maxId = newMaxId

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
            var pageCount = 0

            while (hasMore && pageCount < 15) {
                pageCount++
                val response = throttledRequest("Gespeicherte Posts laden", "Gespeicherte Posts $pageCount") {
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

                val items = jsonResponse.arrayAt("items")
                if (items.isNullOrEmpty()) break

                items.forEach { itemJson ->
                    val item = itemJson.asObjectOrNull() ?: return@forEach
                    val media = item.objectAt("media") ?: item
                    allPosts.add(parseV1MediaItem(media))
                }

                hasMore = jsonResponse.booleanAt("more_available")
                    ?: jsonResponse.stringAt("more_available")?.toBooleanStrictOrNull()
                    ?: false
                val newMaxId = jsonResponse.stringAt("next_max_id")
                if (newMaxId == maxId || newMaxId == null) break
                maxId = newMaxId

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
            var pageCount = 0

            while (hasMore && pageCount < 15) {
                pageCount++
                val response = throttledRequest("Markierte Posts laden", "Markierte Posts $pageCount") {
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

                val items = jsonResponse.arrayAt("items")
                if (items.isNullOrEmpty()) break

                items.forEach { itemJson ->
                    val item = itemJson.asObjectOrNull() ?: return@forEach
                    allPosts.add(parseV1MediaItem(item))
                }

                hasMore = jsonResponse.booleanAt("more_available")
                    ?: jsonResponse.stringAt("more_available")?.toBooleanStrictOrNull()
                    ?: false
                val newMaxId = jsonResponse.stringAt("next_max_id")
                if (newMaxId == maxId || newMaxId == null) break
                maxId = newMaxId

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

            val items = jsonResponse.arrayAt("items")
            val item = items?.firstOrNull().asObjectOrNull()
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

            val node = jsonResponse.objectAt("graphql")?.objectAt("shortcode_media")
                ?: jsonResponse.objectAt("data")?.objectAt("shortcode_media")
                ?: return@withContext DownloadResult.Error("Post-Daten nicht gefunden")

            val isVideo = node.booleanAt("is_video")
                ?: node.stringAt("is_video")?.toBooleanStrictOrNull()
                ?: false
            val isCarousel = node.stringAt("__typename") == "GraphSidecar"

            val mediaUrls = mutableListOf<String>()

            if (isCarousel) {
                val edges = node.objectAt("edge_sidecar_to_children")?.arrayAt("edges")
                edges?.forEach { edge ->
                    val childNode = edge.asObjectOrNull()?.objectAt("node")
                    val childIsVideo = childNode?.booleanAt("is_video")
                        ?: childNode?.stringAt("is_video")?.toBooleanStrictOrNull()
                        ?: false
                    val url = if (childIsVideo) {
                        childNode?.stringAt("video_url") ?: ""
                    } else {
                        childNode?.stringAt("display_url") ?: ""
                    }
                    if (url.isNotEmpty()) mediaUrls.add(url)
                }
            } else {
                val url = if (isVideo) {
                    node.stringAt("video_url") ?: ""
                } else {
                    node.stringAt("display_url") ?: ""
                }
                if (url.isNotEmpty()) mediaUrls.add(url)
            }

            val caption = node.objectAt("edge_media_to_caption")
                ?.arrayAt("edges")
                ?.firstOrNull().asObjectOrNull()
                ?.objectAt("node")
                ?.stringAt("text") ?: ""

            DownloadResult.Success(
                FeedPost(
                    id = node.stringAt("id") ?: "",
                    shortcode = shortcode,
                    mediaUrls = mediaUrls,
                    thumbnailUrl = node.stringAt("display_url") ?: "",
                    type = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                    isCarousel = isCarousel,
                    caption = caption,
                    timestamp = node.longAt("taken_at_timestamp") ?: 0,
                    likeCount = node.objectAt("edge_media_preview_like")?.longAt("count") ?: 0,
                    commentCount = node.objectAt("edge_media_preview_comment")?.longAt("count") ?: 0
                )
            )
        } catch (e: Exception) {
            DownloadResult.Error("Fehler beim Laden des Posts: ${e.message}")
        }
    }

    /**
     * Downloads a file from a URL and saves it to the specified path.
     * CDN downloads (images/videos) are NOT throttled - they go to Instagram's CDN
     * servers (scontent-*.cdninstagram.com), not the API, so they don't trigger
     * automated behavior detection.
     */
    suspend fun downloadFile(url: String, outputPath: String): DownloadResult<String> = withContext(Dispatchers.IO) {
        try {
            val response = client.get(url) {
                timeout {
                    requestTimeoutMillis = 60_000
                    connectTimeoutMillis = 15_000
                }
                headers {
                    append(HttpHeaders.UserAgent, sessionUserAgent)
                    append(HttpHeaders.Accept, "image/webp,image/apng,image/*,video/*,*/*;q=0.8")
                    append(HttpHeaders.AcceptLanguage, "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
                    append(HttpHeaders.Referrer, "$BASE_URL/")
                    append("Sec-Fetch-Dest", "image")
                    append("Sec-Fetch-Mode", "no-cors")
                    append("Sec-Fetch-Site", "cross-site")
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
              response.bodyAsChannel().toInputStream().use { input ->
                  file.outputStream().buffered().use { output ->
                      input.copyTo(output)
                  }
              }

              DownloadResult.Success(outputPath)
          } catch (e: Exception) {
              DownloadResult.Error("Download-Fehler: ${e.message}")
        }
    }

    fun close() {
        client.close()
    }

}
