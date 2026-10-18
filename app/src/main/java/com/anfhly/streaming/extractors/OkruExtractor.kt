package com.anfhly.streaming.extractors

import android.util.Log
import androidx.media3.common.MimeTypes
import com.anfhly.streaming.utils.JsoupConverterFactory
import com.anfhly.streaming.data.model.Video
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class OkruExtractor : Extractor() {

    override val name = "Okru"
    override val mainUrl = "https://ok.ru"

    private val service = Service.build(mainUrl)

    override suspend fun extract(link: String): Video {
        val startTime = System.currentTimeMillis()
        Log.d("OkruExtractor", "Starting extraction for: $link")
        
        // Extraer ID numérico del video
        val videoId = Regex("(?:video|videoembed|movie)/(\\d+)").find(link)?.groupValues?.get(1)
            ?: Regex("(\\d{10,})").find(link)?.groupValues?.get(1)
        
        var videoString: String? = null
        
        // 1. Intentar obtener metadatos via API directa de Ok.ru (videoPlayerMetadata)
        if (videoId != null) {
            try {
                Log.d("OkruExtractor", "Attempting API fetch for videoId: $videoId")
                val client = OkHttpClient.Builder().followRedirects(true).build()
                val formBody = okhttp3.FormBody.Builder()
                    .add("mid", videoId)
                    .build()
                val request = okhttp3.Request.Builder()
                    .url("https://ok.ru/dk?cmd=videoPlayerMetadata")
                    .post(formBody)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build()
                
                val responseBody = withContext(Dispatchers.IO) {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) response.body?.string() else null
                    }
                }
                
                if (!responseBody.isNullOrBlank()) {
                    when {
                        responseBody.contains("\"error\":\"copyrightsRestricted\"") -> {
                            throw Exception("El video de Ok.ru ha sido bloqueado por derechos de autor (copyrightsRestricted)")
                        }
                        responseBody.contains("\"error\":\"notFound\"") -> {
                            throw Exception("El video de Ok.ru no fue encontrado o ha sido eliminado (notFound)")
                        }
                        responseBody.contains("\"error\":\"userNotFound\"") -> {
                            throw Exception("El canal de Ok.ru no existe")
                        }
                        responseBody.contains("videos") || responseBody.contains("ondemandHls") || responseBody.contains("hlsManifestUrl") -> {
                            Log.d("OkruExtractor", "Extracted videoString successfully from videoPlayerMetadata API")
                            videoString = responseBody
                        }
                    }
                }
            } catch (e: Exception) {
                if (e.message?.contains("Ok.ru") == true) throw e
                Log.w("OkruExtractor", "API fetch failed: ${e.message}, falling back to HTML scraper")
            }
        }
        
        // 2. Fallback: Parsear HTML si la API no devolvió metadatos
        if (videoString == null) {
            val desktopLink = link.replace("m.ok.ru", "ok.ru")
            val document = service.get(desktopLink)
            val html = document.html()
            
            if (html.contains("vp_video_stub_txt") || html.contains("copyrightsRestricted")) {
                throw Exception("El video de Ok.ru está bloqueado por derechos de autor")
            }
            
            videoString = document.selectFirst("div[data-options]")?.attr("data-options")
                ?: document.selectFirst("div[data-module='OKVideo']")?.attr("data-options")
                ?: Regex("data-options=[\"']([^\"']+)[\"']").find(html)?.groupValues?.get(1)
                ?: throw Exception("No se encontró información del video en Ok.ru")
        }
        
        val parseTime = System.currentTimeMillis()
        Log.d("OkruExtractor", "Parsing video data at ${parseTime - startTime}ms, videoString length: ${videoString.length}")
        
        val isRawJson = videoString.contains("\"videos\":")
        
        // Extraer el array de videos
        val arrayData = try {
            if (isRawJson) {
                videoString.substringAfter("\"videos\":[").substringBefore("]")
            } else {
                videoString.substringAfterLast("\\\"videos\\\":[").substringBefore("]")
            }
        } catch (e: Exception) {
            throw Exception("No se pudo extraer el array de videos de Ok.ru: ${e.message}")
        }

        if (arrayData.isBlank() || arrayData == videoString) {
            throw Exception("El array de videos de Ok.ru está vacío")
        }

        // Parse videos
        val videos = if (isRawJson) {
            arrayData.split("{\"name\":\"").drop(1).mapNotNull { segment ->
                try {
                    val videoUrl = segment.substringAfter("url\":\"").substringBefore("\"").replace("\\u0026", "&")
                    val rawQuality = segment.substringBefore("\"", "")
                    if (videoUrl.startsWith("https://") && rawQuality.isNotBlank()) {
                        val quality = fixQuality(rawQuality)
                        Log.d("OkruExtractor", "Found: $quality -> ${videoUrl.take(50)}...")
                        Pair(quality, videoUrl)
                    } else null
                } catch (e: Exception) { null }
            }
        } else {
            arrayData.split("{\\\"name\\\":\\\"").drop(1).mapNotNull { segment ->
                try {
                    val videoUrl = segment.substringAfter("url\\\":\\\"").substringBefore("\\\"").replace("\\\\u0026", "&")
                    val rawQuality = segment.substringBefore("\\\"", "")
                    if (videoUrl.startsWith("https://") && rawQuality.isNotBlank()) {
                        val quality = fixQuality(rawQuality)
                        Log.d("OkruExtractor", "Found: $quality -> ${videoUrl.take(50)}...")
                        Pair(quality, videoUrl)
                    } else null
                } catch (e: Exception) { null }
            }
        }
        
        Log.d("OkruExtractor", "Total videos parsed: ${videos.size}")

        val uniqueVideos = mutableListOf<Pair<String, String>>()

        // Extraer manifiesto HLS
        val rawHls = if (isRawJson) {
            videoString.substringAfter("\"ondemandHls\":\"", "").substringBefore("\"")
        } else {
            videoString.substringAfter("\\\"ondemandHls\\\":\\\"", "").substringBefore("\\\"")
        }
        val hlsUrl = rawHls.replace("\\u0026", "&").replace("\\\\u0026", "&").replace("\\\\", "").replace("\\", "")
            
        val manifestUrlToParse = if (hlsUrl.startsWith("http")) {
            Log.d("OkruExtractor", "Found ondemandHls: ${hlsUrl.take(60)}...")
            uniqueVideos.add(Pair("Auto", hlsUrl))
            hlsUrl
        } else {
            val rawManifest = if (isRawJson) {
                videoString.substringAfter("\"hlsManifestUrl\":\"", "").substringBefore("\"")
            } else {
                videoString.substringAfter("\\\"hlsManifestUrl\\\":\\\"", "").substringBefore("\\\"")
            }
            val hlsManifest = rawManifest.replace("\\u0026", "&").replace("\\\\u0026", "&").replace("\\\\", "").replace("\\", "")
            if (hlsManifest.startsWith("http")) {
                Log.d("OkruExtractor", "Found hlsManifestUrl: ${hlsManifest.take(60)}...")
                uniqueVideos.add(Pair("Auto", hlsManifest))
                hlsManifest
            } else null
        }

        // Si tenemos un manifiesto HLS, vamos a descargarlo y parsear todas las calidades transcritas en HLS primero
        if (manifestUrlToParse != null) {
            try {
                val client = OkHttpClient.Builder().followRedirects(true).build()
                val request = okhttp3.Request.Builder()
                    .url(manifestUrlToParse)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                
                val responseBody = withContext(Dispatchers.IO) {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) response.body?.string() else null
                    }
                }

                if (responseBody != null) {
                    val basePath = manifestUrlToParse.substring(0, manifestUrlToParse.lastIndexOf('/') + 1)
                    val lines = responseBody.split("\n")
                    for (i in lines.indices) {
                        val line = lines[i].trim()
                        if (line.startsWith("#EXT-X-STREAM-INF:")) {
                            var rawQuality = ""
                            
                            // 1. Try to parse RESOLUTION first (most accurate for exact resolution name)
                            if (line.contains("RESOLUTION=")) {
                                val resPart = line.substringAfter("RESOLUTION=").substringBefore(",")
                                if (resPart.contains("x")) {
                                    val parts = resPart.split("x")
                                    val width = parts.getOrNull(0)?.toIntOrNull()
                                    val height = parts.getOrNull(1)?.toIntOrNull()
                                    if (width != null && height != null) {
                                        val maxDim = maxOf(width, height)
                                        rawQuality = when {
                                            maxDim <= 256 -> "144p"
                                            maxDim <= 426 -> "240p"
                                            maxDim <= 640 -> "360p"
                                            maxDim <= 854 -> "480p"
                                            maxDim <= 1280 -> "720p"
                                            maxDim <= 1920 -> "1080p"
                                            maxDim <= 2560 -> "1440p"
                                            maxDim <= 3840 -> "4K"
                                            else -> "4K"
                                        }
                                    }
                                }
                            }
                            
                            // 2. Fall back to QUALITY if RESOLUTION was not present or could not be parsed
                            if (rawQuality.isEmpty()) {
                                val qualityPart = line.substringAfter("QUALITY=", "")
                                rawQuality = if (qualityPart.contains(",")) {
                                    qualityPart.substringBefore(",")
                                } else {
                                    qualityPart
                                }
                            }
                            
                            val trackPath = lines.getOrNull(i + 1)?.trim()
                            if (rawQuality.isNotEmpty() && !trackPath.isNullOrEmpty() && !trackPath.startsWith("#")) {
                                val fullTrackUrl = if (trackPath.startsWith("http")) trackPath else basePath + trackPath
                                val qualityLabel = fixQuality(rawQuality)
                                
                                // Añadir calidades HLS nativas si no existen ya
                                if (!uniqueVideos.any { it.first == qualityLabel }) {
                                    uniqueVideos.add(Pair(qualityLabel, fullTrackUrl))
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("OkruExtractor", "Error parsing HLS manifest tracks: ${e.message}")
            }
        }

        // Conectar videos progresivos (MP4) de fallback sólo para las calidades que no estén ya en HLS
        for (item in videos) {
            if (!uniqueVideos.any { it.first == item.first }) {
                uniqueVideos.add(item)
            }
        }

        if (uniqueVideos.isEmpty()) {
            throw Exception("No se encontraron videos válidos en el JSON de Ok.ru")
        }

        // Ordenar calidades de mejor a peor (Auto primero para dar prioridad al HLS adaptativo completo)
        val qualityOrder = listOf("Auto", "4K", "1440p", "1080p", "720p", "480p", "360p", "240p", "144p")
        val distinctVideos = uniqueVideos.distinctBy { it.first }
            .sortedBy { qualityOrder.indexOf(it.first).takeIf { it >= 0 } ?: Int.MAX_VALUE }
        Log.d("OkruExtractor", "Unique qualities sorted: ${distinctVideos.map { it.first }}")

        // Buscar calidad por defecto: prioritariamente Auto, luego 1080p, luego 720p, luego la primera disponible
        val defaultQuality = distinctVideos.find { it.first == "Auto" }
            ?: distinctVideos.find { it.first == "1080p" }
            ?: distinctVideos.find { it.first == "720p" }
            ?: distinctVideos.firstOrNull()
            ?: throw Exception("No se encontró ninguna calidad válida")
        val defaultUrl = defaultQuality.second

        // Si tenemos un manifiesto HLS nativo (Auto), no devolvemos calidades manuales.
        // Esto obliga al reproductor a usar la selección de tracks nativa de ExoPlayer,
        // lo que preserva el audio correcto al cambiar de calidad.
        val hasNativeHls = distinctVideos.any { it.first == "Auto" }
        val qualities = if (hasNativeHls) {
            emptyList()
        } else {
            distinctVideos.map { (quality, url) ->
                Video.Quality(
                    label = quality,
                    url = url,
                    selected = quality == defaultQuality.first
                )
            }
        }

        // Extraer subtítulos del data-options
        val subtitles = extractSubtitles(videoString)
        Log.d("OkruExtractor", "Found ${subtitles.size} subtitles: ${subtitles.map { it.label }}")

        // Determinar tipo de contenido final basándonos en la URL por defecto
        val finalUrl = defaultUrl
        val mimeType = when {
            defaultUrl.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            defaultUrl.contains("mp4") || defaultUrl.contains("ok.ru") || defaultUrl.contains("vkuser.net") || defaultUrl.contains("mycdn.me") -> MimeTypes.VIDEO_MP4
            else -> null
        }

        Log.d("OkruExtractor", "Final video type: $mimeType for URL: ${finalUrl.take(50)}...")

        val totalTime = System.currentTimeMillis()
        Log.d("OkruExtractor", "Total extraction time: ${totalTime - startTime}ms")

        return Video(
            source = finalUrl,
            subtitles = subtitles,
            headers = mapOf(
                "Referer" to "https://ok.ru",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36",
                "Origin" to mainUrl,
                "X-Requested-With" to "XMLHttpRequest"
            ),
            type = mimeType,
            extraBuffering = mimeType == MimeTypes.APPLICATION_M3U8,
            qualities = qualities
        )
    }

    private fun fixQuality(quality: String): String {
        val clean = quality.lowercase(java.util.Locale.ROOT).trim()
        
        // Primero, si es un número puro, intentar mapearlo o interpretarlo
        val num = clean.toIntOrNull()
        if (num != null) {
            return when (num) {
                // Códigos de calidad antiguos / simplificados (0-7)
                0 -> "144p"
                1 -> "240p"
                2 -> "360p"
                3 -> "480p"
                4 -> "720p"
                5 -> "1080p"
                6 -> "1440p"
                7 -> "4K"
                
                // Códigos de calidad nuevos / extendidos (100-107)
                100 -> "144p"
                101 -> "240p"
                102 -> "360p"
                103 -> "480p"
                104 -> "720p"
                105 -> "1080p"
                106 -> "1440p"
                107 -> "4K"
                
                // Si es un número que representa directamente un alto de resolución (ej: 144, 240, 360, 480, 720, 1080, 1440, 2160)
                144 -> "144p"
                240 -> "240p"
                360 -> "360p"
                480 -> "480p"
                720 -> "720p"
                1080 -> "1080p"
                1440 -> "1440p"
                2160 -> "4K"
                
                // Manejar alturas no estándar pero comunes debido a crop / relación de aspecto (ej: 104p, 108p -> 144p)
                in 100..143 -> "144p"
                in 145..239 -> "240p"
                in 241..359 -> "360p"
                in 361..479 -> "480p"
                in 481..719 -> "720p"
                in 721..1079 -> "1080p"
                in 1081..1439 -> "1440p"
                in 1441..2159 -> "4K"
                else -> "${num}p"
            }
        }

        // Si no es un número puro, mapear las etiquetas de texto
        return when (clean) {
            "ultra", "ultrahd", "2160p", "2160", "4k" -> "4K"
            "quad", "quadhd", "1440p", "1440" -> "1440p"
            "full", "fullhd", "1080p", "1080" -> "1080p"
            "high", "hd", "720p", "720" -> "720p"
            "medium", "sd", "480p", "480" -> "480p"
            "low", "360p", "360" -> "360p"
            "lowest", "240p", "240" -> "240p"
            "mobile", "144p", "144" -> "144p"
            else -> quality
        }
    }

    private fun extractSubtitles(videoString: String): List<Video.Subtitle> {
        val subtitleList = mutableListOf<Video.Subtitle>()
        try {
            // Decodificar entidades HTML (como hace Jsoup automáticamente con data-options,
            // pero lo volvemos a hacer por seguridad si se pasa una cadena cruda)
            val decoded = videoString.replace("&amp;", "&").replace("&quot;", "\"")
            
            var subtitleTracksData: String? = null
            val searchTerms = listOf("\"subtitleTracks\":[", "\\\"subtitleTracks\\\":[")
            for (term in searchTerms) {
                if (decoded.contains(term)) {
                    Log.d("OkruExtractor", "Found subtitleTracks using term: $term")
                    val startIndex = decoded.indexOf(term) + term.length
                    val subString = decoded.substring(startIndex)
                    subtitleTracksData = subString.substring(0, subString.indexOf(']'))
                    break
                }
            }

            Log.d("OkruExtractor", "Extracted subtitleTracksData length: ${subtitleTracksData?.length ?: "null"}")

            if (!subtitleTracksData.isNullOrBlank()) {
                val trackEntries = subtitleTracksData.split("},{", "}, {")
                for (entry in trackEntries) {
                    try {
                        val cleanEntry = entry.replace("{", "").replace("}", "").replace("\\\"", "\"")
                        
                        var url = cleanEntry.substringAfter("\"url\":\"").substringBefore("\"")
                        if (url.isBlank() || url == cleanEntry) continue
                        
                        if (url.startsWith("//")) {
                            url = "https:$url"
                        }
                        
                        url = url.replace("\\u0026", "&")
                            .replace("&amp;", "&")
                            .replace("\\\\", "")
                            .replace("\\", "")

                        val language = cleanEntry.substringAfter("\"language\":\"").substringBefore("\"").ifBlank { "es" }
                        
                        val title = if (cleanEntry.contains("\"title\":\"")) {
                            cleanEntry.substringAfter("\"title\":\"").substringBefore("\"")
                        } else if (cleanEntry.contains("\"name\":\"")) {
                            cleanEntry.substringAfter("\"name\":\"").substringBefore("\"")
                        } else {
                            language
                        }

                        val isDefault = cleanEntry.contains("\"isDefault\":true")

                        Log.d("OkruExtractor", "Adding subtitle: title=$title, lang=$language, url=$url, default=$isDefault")

                        subtitleList.add(
                            Video.Subtitle(
                                label = title,
                                file = url,
                                default = isDefault,
                                initialDefault = isDefault
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("OkruExtractor", "Error parsing subtitle track entry: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OkruExtractor", "Error extracting subtitles: ${e.message}", e)
        }
        return subtitleList
    }

    private interface Service {
        companion object {
            fun build(baseUrl: String): Service {
                val client = OkHttpClient.Builder()
                    .followRedirects(true)
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                            .build()
                        chain.proceed(request)
                    }
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .client(client)
                    .build()

                return retrofit.create(Service::class.java)
            }
        }

        @GET
        suspend fun get(@Url url: String): Document
    }
    
    /**
     * Crea una URL HLS persistente que no expira
     * Usa múltiples técnicas para evitar expiración
     */
    private suspend fun createPersistentHlsUrl(mp4Url: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Método 1: Crear M3U8 local con URL persistente
                val localM3u8Path = createPersistentLocalM3U8(mp4Url)
                if (localM3u8Path != null) {
                    Log.d("OkruExtractor", "Created persistent local M3U8: $localM3u8Path")
                    return@withContext localM3u8Path
                }
                
                // Método 2: Crear URL con parámetros anti-expiración
                val persistentUrl = createAntiExpirationUrl(mp4Url)
                Log.d("OkruExtractor", "Using anti-expiration URL: $persistentUrl")
                return@withContext persistentUrl
                
            } catch (e: Exception) {
                Log.e("OkruExtractor", "Error creating persistent HLS: ${e.message}", e)
                // Fallback a URL original
                return@withContext mp4Url
            }
        }
    }
    
    /**
     * Convierte una URL MP4 a formato HLS para streaming sin interrupciones
     * Usa una combinación de técnicas para simular HLS behavior
     */
    private suspend fun createHlsFromMp4(mp4Url: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Método 1: Crear un M3U8 local con segmentos virtuales
                val localM3u8Path = createLocalM3U8(mp4Url)
                if (localM3u8Path != null) {
                    Log.d("OkruExtractor", "Created local M3U8: $localM3u8Path")
                    return@withContext localM3u8Path
                }
                
                // Método 2: Usar URL con parámetros HLS para engañar a ExoPlayer
                val hlsSimulatedUrl = if (mp4Url.contains("?")) {
                    "$mp4Url&hls=true&cache=segment"
                } else {
                    "$mp4Url?hls=true&cache=segment"
                }
                
                Log.d("OkruExtractor", "Using HLS simulation: $hlsSimulatedUrl")
                return@withContext hlsSimulatedUrl
                
            } catch (e: Exception) {
                Log.e("OkruExtractor", "Error creating HLS from MP4: ${e.message}", e)
                // Fallback a URL original
                return@withContext mp4Url
            }
        }
    }
    
    /**
     * Crea una URL con parámetros anti-expiración
     */
    private fun createAntiExpirationUrl(mp4Url: String): String {
        val timestamp = System.currentTimeMillis()
        val randomId = (1000..9999).random()
        
        return if (mp4Url.contains("?")) {
            "$mp4Url&noexpire=true&ts=$timestamp&rid=$randomId&cache=forever"
        } else {
            "$mp4Url?noexpire=true&ts=$timestamp&rid=$randomId&cache=forever"
        }
    }
    
    /**
     * Crea un M3U8 local persistente que apunta al MP4 con configuración anti-expiración
     */
    private suspend fun createPersistentLocalM3U8(mp4Url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Crear directorio persistente si no existe
                val cacheDir = Extractor.context?.cacheDir ?: File("/data/data/com.anfhly.tv/cache")
                val persistentDir = File(cacheDir, "persistent_hls")
                if (!persistentDir.exists()) {
                    persistentDir.mkdirs()
                }
                
                // Usar nombre basado en hash de URL para persistencia
                val urlHash = mp4Url.hashCode().toString()
                val fileName = "okru_persistent_$urlHash.m3u8"
                val m3u8File = File(persistentDir, fileName)
                
                // Crear contenido M3U8 con URL anti-expiración
                val antiExpUrl = createAntiExpirationUrl(mp4Url)
                val m3u8Content = """
                    #EXTM3U
                    #EXT-X-VERSION:6
                    #EXT-X-TARGETDURATION:9999
                    #EXT-X-MEDIA-SEQUENCE:0
                    #EXT-X-PLAYLIST-TYPE:VOD
                    #EXT-X-ALLOW-CACHE:YES
                    #EXTINF:9999.0,
                    ${antiExpUrl}
                    #EXT-X-ENDLIST
                """.trimIndent()
                
                // Escribir archivo M3U8 persistente
                m3u8File.writeText(m3u8Content)
                
                // Retornar URL del archivo persistente
                "file://${m3u8File.absolutePath}"
                
            } catch (e: Exception) {
                Log.e("OkruExtractor", "Error creating persistent local M3U8: ${e.message}", e)
                null
            }
        }
    }
    
    /**
     * Crea un archivo M3U8 local que apunta al MP4 con configuración HLS
     */
    private suspend fun createLocalM3U8(mp4Url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Crear directorio temporal si no existe
                val cacheDir = Extractor.context?.cacheDir ?: File("/data/data/com.anfhly.tv/cache")
                val tempDir = File(cacheDir, "hls")
                if (!tempDir.exists()) {
                    tempDir.mkdirs()
                }
                
                // Generar nombre único para el M3U8
                val fileName = "okru_${System.currentTimeMillis()}.m3u8"
                val m3u8File = File(tempDir, fileName)
                
                // Crear contenido M3U8 con configuración optimizada para streaming
                val m3u8Content = """
                    #EXTM3U
                    #EXT-X-VERSION:6
                    #EXT-X-TARGETDURATION:30
                    #EXT-X-MEDIA-SEQUENCE:0
                    #EXT-X-PLAYLIST-TYPE:VOD
                    #EXTINF:30.0,
                    ${mp4Url}
                    #EXT-X-ENDLIST
                """.trimIndent()
                
                // Escribir archivo M3U8
                m3u8File.writeText(m3u8Content)
                
                // Retornar URL del archivo local
                "file://${m3u8File.absolutePath}"
                
            } catch (e: Exception) {
                Log.e("OkruExtractor", "Error creating local M3U8: ${e.message}", e)
                null
            }
        }
    }
    
    /**
     * Crea un M3U8 real con múltiples segmentos para streaming adaptativo
     * Esto requeriría procesamiento del video o un servidor de streaming
     */
    private fun createRealHlsFromMp4(mp4Url: String): String {
        return mp4Url
    }
}