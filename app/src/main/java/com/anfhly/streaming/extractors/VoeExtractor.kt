package com.anfhly.streaming.extractors

import android.util.Log
import com.anfhly.streaming.data.model.Video
import com.anfhly.streaming.data.model.Video.Subtitle
import com.anfhly.streaming.utils.JsUnpacker
import com.anfhly.streaming.utils.JsoupConverterFactory
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Url
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class VoeExtractor : Extractor() {

    override val name = "Voe"
    override val mainUrl = "https://voe.sx"
    override val aliasUrls = listOf(
        "https://voe-unblock.com",
        "https://voe-unblock.net",
        "https://voe-unblock.org",
        "https://voe-unblock.biz",
        "https://voe-unblock.info",
        "https://voe-unblock.xyz",
        "https://voe.bar",
        "https://voe.link",
        "https://voe.ac",
        "https://voe.lu"
    )

    companion object {
        private const val TAG = "VoeExtractor"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    override suspend fun extract(link: String): Video {
        Log.d(TAG, "========================================")
        Log.d(TAG, "Input URL: $link")

        val service = Service.build(mainUrl)
        
        // Fetch the page with retries
        var document: Document? = null
        var retryCount = 0
        val maxRetries = 3
        
        while (document == null && retryCount < maxRetries) {
            try {
                document = service.getSource(link)
            } catch (e: Exception) {
                retryCount++
                Log.e(TAG, "Error fetching page (attempt $retryCount): ${e.message}")
                if (retryCount >= maxRetries) throw e
                delay(1000)
            }
        }
        
        val html = document!!.html()
        Log.d(TAG, "HTML fetched, length: ${html.length}")

        // Voe typically stores video URL in packed/obfuscated JavaScript
        // Look for packed JS with the video URL
        val videoUrl = extractVideoUrl(html)
            ?: throw Exception("No se pudo extraer el enlace de video de Voe")

        Log.d(TAG, "Video URL found: $videoUrl")

        // Extract subtitles if available
        val subtitles = extractSubtitles(html)
        Log.d(TAG, "Subtitles found: ${subtitles.size}")

        Log.d(TAG, "========================================")

        return Video(
            source = videoUrl,
            headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Accept" to "*/*",
                "Referer" to mainUrl,
                "Origin" to mainUrl
            ),
            subtitles = subtitles
        )
    }

    private fun extractVideoUrl(html: String): String? {
        // Method 1: Look for packed JavaScript (common in Voe)
        val packedJsRegex = Regex("eval\\(function\\(p,a,c,k,e,d\\)[\\s\\S]*?</script>")
        val packedMatch = packedJsRegex.find(html)
        
        if (packedMatch != null) {
            Log.d(TAG, "Found packed JS, attempting to unpack...")
            try {
                val unpacked = JsUnpacker(packedMatch.value).unpack()
                if (unpacked != null) {
                    // Look for video URL in unpacked JS
                    val urlRegex = Regex("https?://[^\"'\\s<>]+\\.(?:m3u8|mp4)[^\"'\\s<>]*")
                    val match = urlRegex.find(unpacked)
                    if (match != null) {
                        Log.d(TAG, "Found video URL in unpacked JS")
                        return match.value
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unpack JS: ${e.message}")
            }
        }

        // Method 2: Look for direct video URL in various patterns
        val patterns = listOf(
            // Pattern 1: Direct video URL in quotes
            Regex("['\"](https?://[^\"']+\\.(?:m3u8|mp4)[^\"']*)['\"]"),
            // Pattern 2: URL with 'hls' or 'video' variable
            Regex("(?:var|let|const)\\s+(?:hls|video|source|url)\\s*=\\s*['\"]([^\"']+\\.(?:m3u8|mp4)[^\"']*)['\"]"),
            // Pattern 3: Data attribute
            Regex("data-video-url=\"([^\"]+)\""),
            // Pattern 4: Source tag
            Regex("<source[^>]+src=\"([^\"]+\\.(?:m3u8|mp4)[^\"]*)\""),
            // Pattern 5: JSON encoded URL
            Regex("\\\"video_url\\\":\\\"([^\\\"]+)\\\""),
            // Pattern 6: Voe specific pattern
            Regex("https?://[a-z0-9]+\\.voe[^/\\s\"']+/[^/\\s\"']+\\.(?:m3u8|mp4)"),
            // Pattern 7: Base64 encoded URL (sometimes used)
            Regex("['\"]([A-Za-z0-9+/]{50,}={0,2})['\"]")
        )

        for ((index, pattern) in patterns.withIndex()) {
            val matches = pattern.findAll(html).toList()
            for (match in matches) {
                val url = match.groupValues.getOrNull(1) ?: match.value
                
                // Skip if it's a relative URL or not a video URL
                if (!url.contains("http")) continue
                
                // Try to decode if it looks like base64
                val finalUrl = if (url.matches(Regex("[A-Za-z0-9+/]+={0,2}"))) {
                    try {
                        String(android.util.Base64.decode(url, android.util.Base64.DEFAULT))
                    } catch (e: Exception) {
                        url
                    }
                } else url

                if (finalUrl.contains(".m3u8") || finalUrl.contains(".mp4")) {
                    Log.d(TAG, "Found video URL with pattern $index: ${finalUrl.take(80)}...")
                    return finalUrl
                }
            }
        }

        // Method 3: Look for window.location or redirects
        val redirectRegex = Regex("window\\.location\\s*=\\s*['\"]([^'\"]+)['\"]")
        val redirectMatch = redirectRegex.find(html)
        if (redirectMatch != null) {
            Log.d(TAG, "Found redirect URL, might need to follow: ${redirectMatch.groupValues[1]}")
        }

        return null
    }

    private fun extractSubtitles(html: String): List<Subtitle> {
        val subtitles = mutableListOf<Subtitle>()
        
        // Look for subtitle tracks in various formats
        val patterns = listOf(
            // JSON format
            Regex("""["']?label["']?\s*:\s*["']([^"']+)["']\s*,\s*["']?(?:file|src)["']?\s*:\s*["']([^"']+\.(?:vtt|srt|ass))["']""", RegexOption.IGNORE_CASE),
            // track element
            Regex("""<track[^>]+label="([^"]+)"[^>]+src="([^"]+\.vtt)""""),
            // URL pattern
            Regex("""https?://[^\s"'<>]+\.(?:vtt|srt|ass)(?:\?[^\s"'<>]*)?""")
        )

        for ((index, pattern) in patterns.withIndex()) {
            when (index) {
                0, 1 -> {
                    pattern.findAll(html).forEach { match ->
                        val label = match.groupValues[1]
                        val file = match.groupValues[2]
                        if (file.isNotBlank()) {
                            val fixedFile = if (file.startsWith("//")) "https:$file" else file
                            if (!fixedFile.startsWith("http")) return@forEach
                            subtitles.add(Subtitle(
                                label = label.ifBlank { "Subtítulo" },
                                file = fixedFile,
                                default = label.contains("spa", ignoreCase = true) || 
                                         label.contains("es", ignoreCase = true) ||
                                         label.contains("spanish", ignoreCase = true)
                            ))
                        }
                    }
                }
                2 -> {
                    pattern.findAll(html).forEach { match ->
                        val url = match.value
                        if (subtitles.none { it.file == url }) {
                            val label = when {
                                url.contains("es", ignoreCase = true) -> "Español"
                                url.contains("en", ignoreCase = true) -> "English"
                                url.contains("fr", ignoreCase = true) -> "Français"
                                url.contains("pt", ignoreCase = true) -> "Português"
                                else -> "Subtítulo"
                            }
                            subtitles.add(Subtitle(label = label, file = url))
                        }
                    }
                }
            }
        }
        
        return subtitles.distinctBy { it.file }
    }

    private interface Service {
        companion object {
            fun build(baseUrl: String): Service {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()

                return retrofit.create(Service::class.java)
            }
        }

        @GET
        @Headers(
            "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
            "Accept-Language: en-US,en;q=0.5",
            "Accept-Encoding: gzip, deflate, br"
        )
        suspend fun getSource(@Url url: String): Document
    }
}
