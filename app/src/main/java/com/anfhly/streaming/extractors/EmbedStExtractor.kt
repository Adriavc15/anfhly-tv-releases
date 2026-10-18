package com.anfhly.streaming.extractors

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.anfhly.streaming.data.model.Video
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Extractor for embed.st live streaming embeds.
 *
 * embed.st uses heavily obfuscated JavaScript + WebAssembly to decrypt
 * and load HLS streams via JWPlayer/Clappr. Because the decryption logic
 * lives in WASM it cannot be replicated in pure Kotlin.
 *
 * Strategy: load the embed URL inside a headless WebView, let the page's
 * own JS decrypt the source, and intercept the resulting .m3u8 request
 * from the network layer.
 */
class EmbedStExtractor : Extractor() {

    override val name = "EmbedSt"
    override val mainUrl = "https://embed.st"
    override val aliasUrls = listOf(
        "https://embedme.top"
    )

    companion object {
        private const val TAG = "EmbedStExtractor"
        private const val TIMEOUT_MS = 30_000L // 30 seconds to find the stream
    }

    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun extract(link: String): Video {
        val ctx = Extractor.context
            ?: throw Exception("[$name] Context not available – cannot create WebView")

        Log.d(TAG, "Starting WebView extraction for: $link")

        // Deferred that will be completed when we find the m3u8 URL
        val streamDeferred = CompletableDeferred<String>()

        withContext(Dispatchers.Main) {
            val webView = WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.userAgentString = userAgent
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                // Allow WASM and other resources to load
                settings.allowContentAccess = true
                settings.blockNetworkImage = false
            }

            webView.webViewClient = object : WebViewClient() {

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null

                    // Intercept the HLS playlist request
                    if (url.contains("playlist.m3u8") || url.contains(".m3u8")) {
                        Log.i(TAG, "🎯 Found m3u8 stream: $url")
                        if (!streamDeferred.isCompleted) {
                            streamDeferred.complete(url)
                        }
                    }
                    return null // Let the request proceed normally
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "Page loaded: $url")

                    // After page loads, try to auto-play the player by
                    // clicking the play button if present
                    view?.evaluateJavascript("""
                        (function() {
                            // Try clicking JWPlayer play button
                            var playBtn = document.querySelector('.jw-icon-playback');
                            if (playBtn) { playBtn.click(); return 'clicked jw-play'; }
                            
                            // Try clicking Clappr play button  
                            playBtn = document.querySelector('[data-player-play]');
                            if (playBtn) { playBtn.click(); return 'clicked clappr-play'; }
                            
                            // Try clicking any visible play button
                            playBtn = document.querySelector('.play-button, .vjs-big-play-button, [class*="play"]');
                            if (playBtn) { playBtn.click(); return 'clicked generic-play'; }
                            
                            // Try triggering play via JWPlayer API
                            if (typeof jwplayer !== 'undefined') {
                                try { jwplayer().play(); return 'jwplayer.play()'; } catch(e) {}
                            }
                            
                            return 'no play button found';
                        })();
                    """.trimIndent()) { result ->
                        Log.d(TAG, "Auto-play attempt result: $result")
                    }
                }
            }

            webView.loadUrl(link)

            // Also schedule a fallback: after a few seconds, try executing
            // JS to extract the source directly from the player object
            Handler(Looper.getMainLooper()).postDelayed({
                if (!streamDeferred.isCompleted) {
                    webView.evaluateJavascript("""
                        (function() {
                            // Try JWPlayer API
                            if (typeof jwplayer !== 'undefined') {
                                try {
                                    var pl = jwplayer();
                                    if (pl && pl.getPlaylistItem) {
                                        var item = pl.getPlaylistItem();
                                        if (item && item.file) return item.file;
                                        if (item && item.sources && item.sources.length > 0) {
                                            return item.sources[0].file || item.sources[0].src;
                                        }
                                    }
                                } catch(e) {}
                            }
                            
                            // Try Clappr
                            if (typeof Clappr !== 'undefined' && window.player) {
                                try {
                                    var src = window.player.options && window.player.options.source;
                                    if (src) return src;
                                } catch(e) {}
                            }
                            
                            // Try video element
                            var video = document.querySelector('video');
                            if (video && video.src) return video.src;
                            
                            // Try source element
                            var source = document.querySelector('video source');
                            if (source && source.src) return source.src;
                            
                            return '';
                        })();
                    """.trimIndent()) { result ->
                        val cleanResult = result?.trim()?.removeSurrounding("\"") ?: ""
                        if (cleanResult.isNotEmpty() && cleanResult != "null" &&
                            (cleanResult.contains(".m3u8") || cleanResult.contains("strmd.st"))
                        ) {
                            Log.i(TAG, "🎯 Found stream via JS fallback: $cleanResult")
                            if (!streamDeferred.isCompleted) {
                                streamDeferred.complete(cleanResult)
                            }
                        }
                    }
                }
            }, 8000) // Wait 8 seconds before trying JS fallback

            // Cleanup when done or timed out
            streamDeferred.invokeOnCompletion {
                Handler(Looper.getMainLooper()).post {
                    webView.stopLoading()
                    webView.destroy()
                    Log.d(TAG, "WebView destroyed")
                }
            }
        }

        // Wait for the stream URL with timeout
        val streamUrl = withTimeout(TIMEOUT_MS) {
            streamDeferred.await()
        }

        Log.i(TAG, "✅ Extracted stream: $streamUrl")

        val headers = mapOf(
            "User-Agent" to userAgent,
            "Referer" to "$mainUrl/",
            "Origin" to mainUrl
        )

        return Video(
            source = streamUrl,
            headers = headers
        )
    }
}
