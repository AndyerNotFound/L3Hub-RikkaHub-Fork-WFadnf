package me.rerere.rikkahub.ui.pages.webview

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.richtext.OfflinePreviewTheme
import me.rerere.rikkahub.ui.components.richtext.buildOfflinePreviewHtml
import me.rerere.rikkahub.ui.navigation.appearancePageContainerColor
import me.rerere.rikkahub.ui.style.TopAppBar
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.toCssHex
import me.rerere.ui.webview.WebViewContentCache
import java.io.ByteArrayInputStream

@Composable
internal fun OfflineMessagePreviewPage(contentId: String) {
    val cacheDir = LocalContext.current.cacheDir
    val scheme = MaterialTheme.colorScheme
    val dark = LocalDarkMode.current
    val truncated = stringResource(R.string.message_preview_truncated)
    val missing = stringResource(R.string.message_preview_missing)
    val theme = OfflinePreviewTheme(
        background = scheme.background.toCssHex(),
        onBackground = scheme.onBackground.toCssHex(),
        surface = scheme.surfaceContainer.toCssHex(),
        onSurfaceVariant = scheme.onSurfaceVariant.toCssHex(),
        primary = scheme.primary.toCssHex(),
        outlineVariant = scheme.outlineVariant.toCssHex(),
        codeBackground = scheme.surfaceContainerHigh.toCssHex(),
    )
    val html by key(contentId) {
        produceState<String?>(null, contentId, cacheDir, dark, theme, truncated, missing) {
            value = withContext(Dispatchers.Default) {
                val source = withContext(Dispatchers.IO) {
                    WebViewContentCache.loadOfflineMessage(cacheDir, contentId)
                } ?: missing
                buildOfflinePreviewHtml(source, dark, theme, truncatedLabel = truncated)
            }
        }
    }
    Scaffold(
        containerColor = appearancePageContainerColor(),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.message_preview)) }, navigationIcon = { BackButton() })
        },
    ) { padding ->
        if (html != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize().padding(padding),
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        settings.apply {
                            javaScriptEnabled = false
                            domStorageEnabled = false
                            allowFileAccess = false
                            allowContentAccess = false
                            blockNetworkLoads = true
                            blockNetworkImage = true
                            builtInZoomControls = true
                            displayZoomControls = false
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                                WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf()))
                        }
                    }
                },
                update = { view ->
                    val content = html
                    if (content != null && view.tag != content) {
                        view.tag = content
                        view.loadDataWithBaseURL(null, content, "text/html", "UTF-8", null)
                    }
                },
                onRelease = { it.stopLoading(); it.destroy() },
            )
        }
    }
}
