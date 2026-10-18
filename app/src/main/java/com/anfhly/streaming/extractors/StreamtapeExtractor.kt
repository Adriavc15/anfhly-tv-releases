package com.anfhly.streaming.extractors

import android.util.Log
import com.anfhly.streaming.data.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class StreamtapeExtractor : Extractor() {

    override val name = "Streamtape"
    override val mainUrl = "https://streamtape.com"
    override val aliasUrls = listOf("https://streamta.site", "https://stape.fun", "https://scloud.online", "https://streamtape.net", "https://shavetape.cash", "https://nupload.me")

    override suspend fun extract(link: String): Video = withContext(Dispatchers.IO) {
        Log.d("StreamtapeExtractor", "Starting extraction for link: $link")

        // Convertir diferentes formatos - nupload.me usa /watch/ directamente
        var fixedLink = link
        when {
            link.contains("nupload.me") && link.contains("/watch/") -> {
                // nupload.me mantiene /watch/ - no convertir
                fixedLink = link
                Log.d("StreamtapeExtractor", "nupload.me detected, keeping /watch/ format")
            }
            link.contains("/e/") -> fixedLink = link.replace("/e/", "/v/")
            link.contains("/d/") -> fixedLink = link.replace("/d/", "/v/")
        }
        Log.d("StreamtapeExtractor", "Fixed link: $fixedLink")

        // Usar Jsoup directamente para obtener la pagina
        Log.d("StreamtapeExtractor", "Fetching page with Jsoup...")
        val doc: Document = Jsoup.connect(fixedLink)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.5")
            .header("Accept-Encoding", "gzip, deflate")
            .header("DNT", "1")
            .header("Connection", "keep-alive")
            .header("Upgrade-Insecure-Requests", "1")
            .timeout(30000)
            .followRedirects(true)
            .get()

        val htmlSource = doc.html()
        Log.d("StreamtapeExtractor", "HTML loaded, length: ${htmlSource.length}")
        Log.d("StreamtapeExtractor", "HTML preview (first 1000 chars): ${htmlSource.take(1000)}...")
        
        // Para nupload.me, buscar diferentes patrones
        if (link.contains("nupload.me")) {
            Log.d("StreamtapeExtractor", "Processing nupload.me - checking for alternative patterns...")
            Log.d("StreamtapeExtractor", "HTML contains 'norobotlink': ${htmlSource.contains("norobotlink")}")
            Log.d("StreamtapeExtractor", "HTML contains 'ideoooolink': ${htmlSource.contains("ideoooolink")}")
            Log.d("StreamtapeExtractor", "HTML contains 'token': ${htmlSource.contains("token", ignoreCase = true)}")
            Log.d("StreamtapeExtractor", "HTML contains 'video': ${htmlSource.contains("video", ignoreCase = true)}")
            Log.d("StreamtapeExtractor", "HTML contains 'stream': ${htmlSource.contains("stream", ignoreCase = true)}")
        }

        // Buscar el norobotlink JavaScript
        val norobotLinkPattern = Regex("document\\.getElementById\\('norobotlink'\\)\\.innerHTML = (.+?);")
        val norobotLinkMatch = norobotLinkPattern.find(htmlSource)
        if (norobotLinkMatch == null) {
            Log.e("StreamtapeExtractor", "norobotlink JavaScript not found in HTML")
            Log.d("StreamtapeExtractor", "HTML preview (first 2000 chars): ${htmlSource.take(2000)}")
            
            // Para nupload.me, intentar patrones alternativos
            if (link.contains("nupload.me")) {
                Log.d("StreamtapeExtractor", "Trying alternative patterns for nupload.me...")
                
                // Buscar cualquier token en el HTML
                val tokenPattern = Regex("token[\"']?\\s*[:=]\\s*[\"']([^\"']+)[\"']")
                val tokenMatch = tokenPattern.find(htmlSource)
                if (tokenMatch != null) {
                    Log.d("StreamtapeExtractor", "Found alternative token: ${tokenMatch.groupValues[1]}")
                    // Aquí podríamos implementar lógica alternativa para nupload.me
                }
                
                // Buscar URLs de video directamente
                val urlPattern = Regex("https?://[^\\s\"'<>]+\\.(?:m3u8|mp4)[^\\s\"'<>]*")
                val urlMatches = urlPattern.findAll(htmlSource).toList()
                if (urlMatches.isNotEmpty()) {
                    Log.d("StreamtapeExtractor", "Found direct video URLs: ${urlMatches.map { it.value }.take(3)}")
                    // Podríamos retornar una URL directa si se encuentra
                }
            }
            
            throw Exception("norobotlink JavaScript not found")
        }

        val norobotLinkContent = norobotLinkMatch.groupValues[1]
        Log.d("StreamtapeExtractor", "norobotlink content found: $norobotLinkContent")

        // Extraer token
        val tokenPattern = Regex("token=([^&']+)")
        val tokenMatch = tokenPattern.find(norobotLinkContent)
        if (tokenMatch == null) {
            Log.e("StreamtapeExtractor", "Token not found in norobotlink content: $norobotLinkContent")
            throw Exception("token not found")
        }
        val token = tokenMatch.groupValues[1]
        Log.d("StreamtapeExtractor", "Token extracted: $token")

        // Buscar el div ideoooolink
        val divElements = doc.select("div#ideoooolink[style=display:none;]")
        Log.d("StreamtapeExtractor", "Found ${divElements.size} elements matching div#ideoooolink[style=display:none;]")

        if (divElements.isEmpty()) {
            // Intentar buscar sin el atributo style
            val divElementsNoStyle = doc.select("div#ideoooolink")
            Log.d("StreamtapeExtractor", "Found ${divElementsNoStyle.size} elements matching div#ideoooolink (no style filter)")
            if (divElementsNoStyle.isNotEmpty()) {
                Log.d("StreamtapeExtractor", "First element HTML: ${divElementsNoStyle.first()?.outerHtml()}")
            }
            throw Exception("ideoooolink div not found")
        }

        var streamtapePath = divElements.first()?.text()
        if (streamtapePath.isNullOrEmpty()) {
            Log.e("StreamtapeExtractor", "ideoooolink content is empty or null")
            throw Exception("ideoooolink content is empty")
        }
        Log.d("StreamtapeExtractor", "Streamtape path extracted: $streamtapePath")

        // Limpiar el path - remover slash inicial si existe
        if (streamtapePath.startsWith("/")) {
            streamtapePath = streamtapePath.substring(1)
        }

        // El token en el path del div está modificado (ofuscado), necesitamos reemplazarlo
        // con el token real extraído del norobotlink
        // El path tiene formato: streamtape.com/get_video?id=XXX&expires=YYY&ip=ZZZ&token=MODIFIED
        // Necesitamos reemplazar el token modificado por el token real

        val tokenRegex = Regex("token=[^&]+")
        val correctedPath = tokenRegex.replace(streamtapePath, "token=$token")
        Log.d("StreamtapeExtractor", "Corrected path: $correctedPath")

        // Construir URL final con el token correcto
        val initialVideoUrl = "https://$correctedPath&dl=1s"
        Log.d("StreamtapeExtractor", "Initial video URL: $initialVideoUrl")

        // Seguir la redirección para obtener la URL final del video
        Log.d("StreamtapeExtractor", "Following redirect...")
        val connection = java.net.URL(initialVideoUrl).openConnection() as java.net.HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.requestMethod = "HEAD"
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        connection.setRequestProperty("Accept", "*/*")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        val responseCode = connection.responseCode
        Log.d("StreamtapeExtractor", "Response code: $responseCode")

        val finalVideoUrl = if (responseCode == 302 || responseCode == 301) {
            val location = connection.getHeaderField("Location")
            Log.d("StreamtapeExtractor", "Redirect location: $location")
            location ?: initialVideoUrl
        } else {
            initialVideoUrl
        }
        connection.disconnect()

        Log.d("StreamtapeExtractor", "Final video URL after redirect: $finalVideoUrl")

        val video = Video(
            source = finalVideoUrl,
            subtitles = listOf()
        )
        video
    }
}