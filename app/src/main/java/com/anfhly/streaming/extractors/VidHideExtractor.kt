package com.anfhly.streaming.extractors

import com.anfhly.streaming.utils.JsoupConverterFactory
import com.anfhly.streaming.data.model.Video
import com.anfhly.streaming.utils.JsUnpacker
import com.anfhly.streaming.utils.UserPreferences
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url
import android.util.Log
import java.net.URL

class VidHideExtractor: Extractor() {
    override val name = "VidHide"
    override val mainUrl = "https://dhtpre.com"

    override val aliasUrls = listOf(
        "https://peytonepre.com",
        "https://vidhideplus.com/",
        "https://mivalyo",
        "https://dinisglows",
        "https://dingtezuni.com",
        "https://dintezuvio.com",
        "https://minochinos.com",
        "https://moflix-stream.click",
        "https://filelions.to",
        "https://morencius"
    )

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/115.0"
    }

    override suspend fun extract(link: String): Video {
        val mainLink = URL(link).protocol + "://" + URL(link).host
        val service = Service.build(mainLink)
        val fallback = URL(link).protocol + "://" + URL(link).host
        val referer = try {
            UserPreferences.currentProvider?.baseUrl ?: fallback
        } catch (_: Throwable) {
            fallback
        }
        val origin = referer
        
        val source = service.getSource(
            url = link,
            referer = referer,
            origin = origin,
            userAgent = DEFAULT_USER_AGENT
        )
        Log.d("VidHideExtractor", "Source HTML length: ${source.toString().length}")
        
        val packedJS = Regex("(eval\\(function\\(p,a,c,k,e,[rd]\\)(.|\\n)*?)</script>")
            .find(source.toString())?.let { it.groupValues[1] }
            ?: run {
                Log.e("VidHideExtractor", "Packed JS not found in source")
                throw Exception("Packed JS not found")
            }

        Log.d("VidHideExtractor", "Packed JS found, length: ${packedJS.length}")

        val unPacked = JsUnpacker(packedJS).unpack()
            ?: run {
                Log.e("VidHideExtractor", "Failed to unpack JS")
                throw Exception("Unpacked is null")
            }
        
        Log.d("VidHideExtractor", "Unpacked JS length: ${unPacked.length}")
        Log.d("VidHideExtractor", "Unpacked preview: ${unPacked.take(200)}...")

        val links = mutableMapOf<String, String>()
        
        // Patrones para VidHide
        
        // Buscar enlaces HLS con formato moderno
        Regex("""["']?(hls\d*|stream\d*|playlist\d*)["']?\s*[:=]\s*["']?(https?://[^"']+\.m3u8[^"']*?)["']?""")
            .findAll(unPacked)
            .forEach {
                links[it.groupValues[1]] = it.groupValues[2]
            }
        
        // Buscar enlaces MP4 directos
        Regex("""["']?(mp4\d*|video\d*|file\d*)["']?\s*[:=]\s*["']?(https?://[^"']+\.mp4[^"']*?)["']?""")
            .findAll(unPacked)
            .forEach {
                links[it.groupValues[1]] = it.groupValues[2]
            }
        
        // Buscar URLs directas que parezcan streams
        Regex("""https?://[^\s"'<>]+\.(?:m3u8|mp4)[^\s"'<>]*""")
            .findAll(unPacked)
            .forEach {
                val url = it.value
                if (url.contains(".m3u8") || url.contains(".mp4")) {
                    links["direct_${links.size}"] = url
                }
            }
        
        // Buscar patrón específico de minochinos (base64 + m3u8)
        Regex("""([a-zA-Z0-9+/]{20,}={0,2}[^"']*m3u8)""")
            .findAll(unPacked)
            .forEach {
                val possibleUrl = it.groupValues[1]
                if (possibleUrl.startsWith("http") || possibleUrl.startsWith("/")) {
                    links["base64_hls"] = possibleUrl
                } else {
                    try {
                        val decoded = String(android.util.Base64.decode(possibleUrl, android.util.Base64.DEFAULT))
                        if (decoded.startsWith("http") || decoded.startsWith("/")) {
                            links["base64_hls"] = decoded
                        }
                    } catch (_: Exception) {
                        // Ignorar
                    }
                }
            }
        
        // Nuevo patrón para VidHide ofuscado: buscar arrays de caracteres
        Regex("""var\s+(\w+)\s*=\s*["']([^"']+)["'].*?var\s+(\w+)\s*=\s*(\d+).*?var\s+(\w+)\s*=\s*(\d+).*?var\s+(\w+)\s*=\s*\1\.split\(\2\)\.slice\(\3,\4\)\.join\(\2\)""")
            .findAll(unPacked)
            .forEach { match ->
                val arrayName = match.groupValues[1]
                val separator = match.groupValues[2]
                val start = match.groupValues[4].toInt()
                val end = match.groupValues[5].toInt()
                
                // Intentar reconstruir la URL
                if (arrayName.contains("http") || arrayName.contains("m3u8") || arrayName.contains("mp4")) {
                    try {
                        val parts = arrayName.split(separator)
                        if (parts.size > end) {
                            val url = parts.slice(start until end).joinToString(separator)
                            if (url.contains("http") && (url.contains(".m3u8") || url.contains(".mp4"))) {
                                links["reconstructed_url"] = url
                            }
                        }
                    } catch (e: Exception) {
                        // Ignorar errores
                    }
                }
            }
        
        // Patrón para buscar URLs codificadas con caracteres Unicode
        Regex("""\\u([0-9a-fA-F]{4})""")
            .findAll(unPacked)
            .forEach { match ->
                val char = match.groupValues[1].toInt(16).toChar().toString()
                val decoded = unPacked.replace(match.value, char)
                Regex("""https?://[^\s"'<>]+\.(?:m3u8|mp4)[^\s"'<>]*""")
                     .findAll(decoded)
                     .forEach {
                         links["unicode_url"] = it.value
                     }
            }
        
        // Buscar enlaces en el array split
        Regex("""\.split\(['"]\|['"]\).*?([a-zA-Z0-9]{20,})""")
            .findAll(unPacked)
            .forEach {
                val encoded = it.groupValues[1]
                try {
                    val decoded = String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT))
                    if (decoded.contains("http") && (decoded.contains(".m3u8") || decoded.contains(".mp4"))) {
                        links["decoded_url"] = decoded
                    }
                } catch (e: Exception) {
                    // Ignorar
                }
            }
        
        // Buscar patrón específico de minochinos: o={"1g":"url","1r":"url"}
        Regex("""o\s*=\s*\{[^}]*["']?(1g|1r|hls[0-9]*)["']?\s*:\s*["'](https?://[^"']+)["'][^}]*\}""")
            .findAll(unPacked)
            .forEach {
                val key = it.groupValues[1]
                val url = it.groupValues[2]
                links[key] = url
            }
        
        // Buscar URLs en el objeto 'o' con decodificación de caracteres
        Regex("""["'](1g|1r|hls[0-9]*)["']\s*:\s*["']([a-zA-Z0-9+/]{20,}={0,2})["']""")
            .findAll(unPacked)
            .forEach {
                val key = it.groupValues[1]
                val encoded = it.groupValues[2]
                try {
                    val decoded = String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT))
                    if (decoded.contains("http") && (decoded.contains(".m3u8") || decoded.contains(".mp4"))) {
                        Log.d("VidHideExtractor", "Found decoded_$key: $decoded")
                        links["decoded_$key"] = decoded
                    }
                } catch (e: Exception) {
                    // Ignorar
                }
            }
        
        Log.d("VidHideExtractor", "Links found: ${links.keys}")
        links.forEach { (key, value) ->
            Log.d("VidHideExtractor", "$key -> ${value.take(100)}...")
        }

        // Helper to validate url candidates
        val getValidUrl = { url: String? ->
            if (url != null && (url.startsWith("http") || url.startsWith("/"))) url else null
        }

        // Helper to prefer URL with token (t= parameter)
        val preferTokenUrl = { candidates: List<String?> ->
            val withToken = candidates.firstOrNull { url -> url?.contains("?t=") == true || url?.contains("&t=") == true }
            withToken ?: candidates.firstOrNull { it != null }
        }

        // Prioridad: reconstructed_url > unicode_url > decoded_1r > decoded_1g > 1r > 1g > decoded_url > base64_hls > direct_1 (con token) > hls4 > hls2 (con token) > otros hls > direct/mp4
        val finalUrl = getValidUrl(links["reconstructed_url"])
            ?: getValidUrl(links["unicode_url"])
            ?: getValidUrl(links["decoded_1r"]) 
            ?: getValidUrl(links["decoded_1g"])
            ?: getValidUrl(links["1r"]) 
            ?: getValidUrl(links["1g"])
            ?: getValidUrl(links["decoded_url"])
            ?: getValidUrl(links["base64_hls"])
            ?: preferTokenUrl(listOf(
                getValidUrl(links["direct_1"]),
                getValidUrl(links["direct_2"]),
                getValidUrl(links["direct_0"]),
                getValidUrl(links["direct_premium"]),
            ))
            ?: getValidUrl(links["hls4"]) 
            ?: preferTokenUrl(listOf(
                getValidUrl(links["hls2"]),
                getValidUrl(links["hls3"]),
                getValidUrl(links["hls1"]),
                getValidUrl(links["hls"]),
            ))
            ?: links.keys.find { it.startsWith("hls") }?.let { getValidUrl(links[it]) }
            ?: links.keys.find { it.startsWith("direct") || it.startsWith("mp4") }?.let { getValidUrl(links[it]) }
            ?: links.values.mapNotNull { getValidUrl(it) }.firstOrNull()
            ?: run {
                Log.e("VidHideExtractor", "No video link found. Found keys: ${links.keys}")
                throw Exception("No video link found. Found: ${links.keys}")
            }
        
        Log.d("VidHideExtractor", "Selected finalUrl: $finalUrl")
        
        val completeUrl = if (finalUrl.startsWith("/")) {
            val baseUrl = URL(link).protocol + "://" + URL(link).host
            baseUrl + finalUrl
        } else {
            finalUrl
        }
        
        if (!completeUrl.startsWith("http") || (!completeUrl.contains(".m3u8") && !completeUrl.contains(".mp4"))) {
            Log.e("VidHideExtractor", "Invalid final URL: $completeUrl")
            throw Exception("Invalid video URL: $completeUrl")
        }

        val videoType = when {
            completeUrl.contains(".m3u8") -> "application/x-mpegURL"
            completeUrl.contains(".mp4") -> "video/mp4"
            else -> "application/x-mpegURL" // Default to HLS
        }
        
        val headers = mapOf(
            "Referer" to link,
            "Origin" to mainLink,
            "User-Agent" to DEFAULT_USER_AGENT,
            "Accept" to "application/x-mpegURL, video/mp4, video/*, */*",
            "Accept-Language" to "en-US,en;q=0.5"
        )

        val hasToken = completeUrl.contains("?t=") || completeUrl.contains("&t=")

        // Construir lista de calidades desde los links hls encontrados
        // Si la URL final ya tiene token (direct_*), las hls* sin token daran 403
        val finalQualities = if (hasToken) {
            listOf(Video.Quality(label = "Auto", url = completeUrl, selected = true))
        } else {
            val qualityMap = mapOf(
                "hls4" to "1080p",
                "hls3" to "720p",
                "hls2" to "480p",
                "hls1" to "360p"
            )
            val hlsQualities = qualityMap.entries
                .filter { (key, _) -> links.containsKey(key) && getValidUrl(links[key]) != null }
                .map { (key, label) ->
                    val url = if (links[key]!!.startsWith("/")) {
                        URL(link).protocol + "://" + URL(link).host + links[key]!!
                    } else links[key]!!
                    Video.Quality(
                        label = label,
                        url = url,
                        selected = url == completeUrl
                    )
                }

            if (hlsQualities.isNotEmpty()) {
                val hasSelected = hlsQualities.any { it.selected }
                if (!hasSelected) hlsQualities.mapIndexed { i, q -> q.copy(selected = i == 0) }
                else hlsQualities
            } else {
                listOf(Video.Quality(label = "Auto", url = completeUrl, selected = true))
            }
        }

        Log.d("VidHideExtractor", "About to return Video object")
        Log.d("VidHideExtractor", "Final extracted URL: $completeUrl")
        
        return Video(
            source = completeUrl,
            type = videoType,
            headers = headers,
            qualities = finalQualities
        )
    }

    private interface Service {
        @GET
        suspend fun getSource(
            @Url url: String,
            @Header("Referer") referer: String,
            @Header("Origin") origin: String,
            @Header("User-Agent") userAgent: String = DEFAULT_USER_AGENT
        ): Document

        companion object {
            fun build(baseUrl: String): Service {
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()
                return retrofit.create(Service::class.java)
            }
        }
    }
}
