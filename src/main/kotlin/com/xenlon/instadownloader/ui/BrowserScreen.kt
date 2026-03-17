package com.xenlon.instadownloader.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * In-App Instagram browser with a floating download button.
 * When the user navigates to a post/reel/profile, they can tap
 * the download button to extract the URL and download it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onBack: () -> Unit,
    onDownloadUrl: (String) -> Unit,
) {
    var currentUrl by remember { mutableStateOf("https://www.instagram.com/") }
    var pageTitle by remember { mutableStateOf("Instagram") }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Detect if current URL is downloadable (post, reel, story, profile)
    val downloadableType = remember(currentUrl) {
        when {
            currentUrl.contains("/p/") -> "Post"
            currentUrl.contains("/reel/") -> "Reel"
            currentUrl.contains("/stories/") -> "Story"
            currentUrl.matches(Regex("https://www\\.instagram\\.com/[^/]+/?$")) &&
                !currentUrl.contains("/accounts/") &&
                !currentUrl.contains("/explore/") &&
                !currentUrl.contains("/direct/") -> "Profil"
            else -> null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        pageTitle,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        currentUrl.removePrefix("https://www.instagram.com"),
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, "Schließen", tint = TextPrimary)
                }
            },
            actions = {
                IconButton(
                    onClick = { webView?.goBack() },
                    enabled = canGoBack
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "Zurück",
                        tint = if (canGoBack) TextPrimary else TextSecondary.copy(alpha = 0.3f)
                    )
                }
                IconButton(
                    onClick = { webView?.goForward() },
                    enabled = canGoForward
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        "Vorwärts",
                        tint = if (canGoForward) TextPrimary else TextSecondary.copy(alpha = 0.3f)
                    )
                }
                IconButton(onClick = { webView?.reload() }) {
                    Icon(Icons.Default.Refresh, "Neu laden", tint = TextSecondary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkSurface,
                titleContentColor = TextPrimary
            )
        )

        // Loading indicator
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = AccentPink,
                trackColor = DarkSurface
            )
        }

        // WebView
        Box(modifier = Modifier.weight(1f)) {
            BrowserWebView(
                onWebViewCreated = { webView = it },
                onUrlChanged = { url ->
                    currentUrl = url
                },
                onTitleChanged = { title ->
                    pageTitle = title
                },
                onNavigationChanged = { back, forward ->
                    canGoBack = back
                    canGoForward = forward
                },
                onLoadingChanged = { loading ->
                    isLoading = loading
                }
            )

            // Floating download button
            androidx.compose.animation.AnimatedVisibility(
                visible = downloadableType != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Button(
                    onClick = { onDownloadUrl(currentUrl) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(InstagramPurple, InstagramPink, InstagramOrange)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Text(
                                "$downloadableType herunterladen",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserWebView(
    onWebViewCreated: (WebView) -> Unit,
    onUrlChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onNavigationChanged: (canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                }

                // Accept cookies for Instagram login
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        url?.let { onUrlChanged(it) }
                        onNavigationChanged(canGoBack(), canGoForward())
                        onLoadingChanged(false)
                    }

                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                        url?.let { onUrlChanged(it) }
                        onNavigationChanged(canGoBack(), canGoForward())
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        title?.let { onTitleChanged(it) }
                    }

                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onLoadingChanged(newProgress < 100)
                    }
                }

                onWebViewCreated(this)
                loadUrl("https://www.instagram.com/")
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
