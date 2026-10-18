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
import org.json.JSONArray
import org.json.JSONObject

class OkruExtractor : Extractor() {

    override val name = "Okru"
    override val mainUrl = "https://ok.ru"

    private val service = Service.build(mainUrl)

    // User-Agent compartido para todas las peticiones
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    override suspend fun extract(link: String): Video {
        val startTime = System.currentTimeMillis()
        Log.d("OkruExtractor", "Starting extraction for: $link")

        // Extraer ID numérico del video desde la URL
        val videoId = Regex("(?:video|videoembed|movie)/(\\d+)").find(link)?.groupValues?.get(1)
            ?: Regex("(\\d{10,})").find(link)?.groupValues?.get(1)

        var rawJson: JSONObject? = null

        // ── Paso 1: API directa de ok.ru (videoPlayerMetadata) ──────────────────
        if (videoId != null) {
            rawJson = fetchMetadataApi(videoId)
        }

        // ── Paso 2: Fallback — scraping de HTML ──────────────────────────────────
        if (rawJson == null) {
            rawJson = fetchMetadataHtml(link)
        }

        checkJsonForErrors(rawJson)

        val elapsed1 = System.currentTimeMillis() - startTime
        Log.d("OkruExtractor", "Metadata obtained in ${elapsed1}ms")

        // ── Extraer HLS y calidades MP4 del JSON ─────────────────────────────────
        val uniqueVideos = mutableListOf<Pair<String, String>>() // (label, url)

        // Buscar manifiesto HLS (ondemandHls o hlsManifestUrl)
        val hlsUrl = rawJson.optString("ondemandHls").ifBlank { null }
            ?: rawJson.optString("hlsManifestUrl").ifBlank { null }

        var manifestUrlToParse: String? = null
        if (!hlsUrl.isNullOrBlank()) {
            Log.d("OkruExtractor", "Found HLS manifest: ${hlsUrl.take(60)}...")
            uniqueVideos.add(Pair("Auto", hlsUrl))
            manifestUrlToParse = hlsUrl
        }

        // Si hay manifiesto HLS, descargarlo y extraer pistas individuales
        if (manifestUrlToParse != null) {
            parseHlsManifest(manifestUrlToParse, uniqueVideos)
        }

        // Extraer calidades MP4 progresivas desde el array "videos"
        val videosArray: JSONArray? = rawJson.optJSONArray("videos")
        if (videosArray != null) {
            for (i in 0 until videosArray.length()) {
                val item = videosArray.optJSONObject(i) ?: continue
                val rawName = item.optString("name")
                val url = item.optString("url")
                if (url.isBlank() || !url.startsWith("http")) continue
                val label = fixQuality(rawName)
                if (uniqueVideos.none { it.first == label }) {
                    uniqueVideos.add(Pair(label, url))
                    Log.d("OkruExtractor", "MP4 quality: $label -> ${url.take(50)}...")
                }
            }
        }

        if (uniqueVideos.isEmpty()) {
            throw Exception("No se encontraron videos válidos en Ok.ru")
        }

        // Ordenar calidades de mejor a peor (Auto primero para HLS adaptativo)
        val qualityOrder = listOf("Auto", "4K", "1440p", "1080p", "720p", "480p", "360p", "240p", "144p")
        val sortedVideos = uniqueVideos.distinctBy { it.first }
            .sortedBy { qualityOrder.indexOf(it.first).let { idx -> if (idx >= 0) idx else Int.MAX_VALUE } }

        Log.d("OkruExtractor", "Qualities: ${sortedVideos.map { it.first }}")

        // Seleccionar calidad por defecto
        // Priorizar 720p para evitar lag/congelamiento por decodificación de hardware
        // 1080p/4K puede causar lag en dispositivos con hardware limitado
        val default = sortedVideos.find { it.first == "Auto" }
            ?: sortedVideos.find { it.first == "720p" }
            ?: sortedVideos.find { it.first == "480p" }
            ?: sortedVideos.find { it.first == "1080p" }
            ?: sortedVideos.first()

        // Si hay HLS nativo, no devolvemos calidades manuales (ExoPlayer usa su propio track selector)
        val hasNativeHls = sortedVideos.any { it.first == "Auto" }
        val qualities = if (hasNativeHls) {
            emptyList()
        } else {
            sortedVideos.map { (label, url) ->
                Video.Quality(label = label, url = url, selected = label == default.first)
            }
        }

        // Extraer subtítulos
        val subtitles = extractSubtitles(rawJson)
        Log.d("OkruExtractor", "Found ${subtitles.size} subtitles")

        // Determinar MIME type de la URL por defecto
        val mimeType = when {
            default.first == "Auto" ||
            default.second.contains(".m3u8", ignoreCase = true) ||
            default.second.contains("hls", ignoreCase = true) ||
            default.second.contains("ondemandhls", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            else -> MimeTypes.VIDEO_MP4
        }

        val totalMs = System.currentTimeMillis() - startTime
        Log.d("OkruExtractor", "Extraction completed in ${totalMs}ms. mimeType=$mimeType")

        return Video(
            source = default.second,
            subtitles = subtitles,
            headers = mapOf(
                "Referer"    to "https://ok.ru/",
                "User-Agent" to userAgent,
                "Origin"     to "https://ok.ru"
            ),
            type = mimeType,
            extraBuffering = true, // Activar buffering extra para OK.ru (HLS y MP4) para evitar congelamientos
            qualities = qualities
        )
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Métodos privados de extracción
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * Intenta obtener los metadatos del video mediante la API JSON de ok.ru.
     * Retorna un JSONObject si tiene éxito, null si la API no responde o no devuelve
     * datos de video utilizables.
     */
    private suspend fun fetchMetadataApi(videoId: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("OkruExtractor", "Trying API for videoId=$videoId")
                val client = OkHttpClient.Builder().followRedirects(true).build()
                val body = okhttp3.FormBody.Builder()
                    .add("mid", videoId)
                    .build()
                val request = okhttp3.Request.Builder()
                    .url("https://ok.ru/dk?cmd=videoPlayerMetadata")
                    .post(body)
                    .header("User-Agent", userAgent)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build()

                val responseText = client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                }

                if (responseText.isNullOrBlank()) return@withContext null

                val json = JSONObject(responseText)

                // Si el JSON tiene error, lanzar excepción descriptiva ahora mismo
                val error = json.optString("error")
                if (error.isNotBlank()) {
                    val msg = when (error) {
                        "copyrightsRestricted" -> "El video de Ok.ru ha sido bloqueado por derechos de autor"
                        "notFound"             -> "El video de Ok.ru no fue encontrado o ha sido eliminado"
                        "userNotFound"         -> "El canal de Ok.ru no existe"
                        else                   -> "Error de Ok.ru: $error"
                    }
                    throw Exception(msg)
                }

                // Solo considerar útil si tiene videos o manifest HLS
                if (json.has("videos") || json.has("ondemandHls") || json.has("hlsManifestUrl")) {
                    Log.d("OkruExtractor", "API returned valid metadata")
                    json
                } else {
                    Log.w("OkruExtractor", "API returned JSON but no video data, falling back to HTML")
                    null
                }
            } catch (e: Exception) {
                if (e.message?.startsWith("El video") == true ||
                    e.message?.startsWith("El canal") == true ||
                    e.message?.startsWith("Error de Ok.ru") == true) throw e
                Log.w("OkruExtractor", "API fetch failed: ${e.message}")
                null
            }
        }
    }

    /**
     * Fallback: descarga el HTML de la página de ok.ru y extrae el JSON de metadatos
     * del atributo data-options del elemento de video.
     */
    private suspend fun fetchMetadataHtml(link: String): JSONObject {
        val desktopLink = link.replace("m.ok.ru", "ok.ru")
        val document = service.get(desktopLink)
        val html = document.html()

        if (html.contains("vp_video_stub_txt") || html.contains("copyrightsRestricted")) {
            throw Exception("El video de Ok.ru está bloqueado por derechos de autor")
        }

        // data-options contiene el JSON de configuración del reproductor, URL-encoded
        val rawOptions = document.selectFirst("div[data-options]")?.attr("data-options")
            ?: document.selectFirst("div[data-module='OKVideo']")?.attr("data-options")
            ?: Regex("""data-options=["']([^"']+)["']""").find(html)?.groupValues?.get(1)
            ?: throw Exception("No se encontró información del video en Ok.ru")

        // Jsoup decodifica automáticamente entidades HTML en atributos, pero el valor
        // a su vez contiene JSON escapado dentro de un campo "flashvars" o similar.
        // Intentar parsear directamente como JSON y si falla, buscar el sub-objeto de video.
        return try {
            val outerJson = JSONObject(rawOptions)
            // En data-options el JSON de video suele estar bajo "flashvars.metadata" o directamente
            val flashvars = outerJson.optJSONObject("flashvars")
            val metadataStr = flashvars?.optString("metadata")
            if (!metadataStr.isNullOrBlank()) {
                JSONObject(metadataStr)
            } else {
                outerJson
            }
        } catch (e: Exception) {
            // Si el raw contiene el JSON de video directamente (escapado con backslash)
            val unescaped = rawOptions
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\u0026", "&")
            try {
                JSONObject(unescaped)
            } catch (e2: Exception) {
                // Última opción: extraer el sub-JSON que empieza en "videos"
                val startIndex = unescaped.indexOf("{\"videos\"")
                    .takeIf { it >= 0 }
                    ?: unescaped.indexOf("{\\\"videos\\\"")
                        .takeIf { it >= 0 }
                    ?: throw Exception("No se pudo parsear el JSON de Ok.ru: ${e2.message}")
                JSONObject(unescaped.substring(startIndex))
            }
        }
    }

    /**
     * Lanza una excepción descriptiva si el JSON contiene un campo "error" de ok.ru.
     */
    private fun checkJsonForErrors(json: JSONObject?) {
        val error = json?.optString("error") ?: return
        if (error.isBlank()) return
        throw when (error) {
            "copyrightsRestricted" -> Exception("El video de Ok.ru ha sido bloqueado por derechos de autor")
            "notFound"             -> Exception("El video de Ok.ru no fue encontrado o ha sido eliminado")
            "userNotFound"         -> Exception("El canal de Ok.ru no existe")
            else                   -> Exception("Error de Ok.ru: $error")
        }
    }

    /**
     * Descarga un manifiesto HLS maestro y extrae las pistas de calidad individuales,
     * añadiéndolas a [uniqueVideos] si no existen ya.
     */
    private suspend fun parseHlsManifest(
        manifestUrl: String,
        uniqueVideos: MutableList<Pair<String, String>>
    ) = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder().followRedirects(true).build()
            val request = okhttp3.Request.Builder()
                .url(manifestUrl)
                .header("User-Agent", userAgent)
                .build()

            val body = client.newCall(request).execute().use { r ->
                if (r.isSuccessful) r.body?.string() else null
            } ?: return@withContext

            val basePath = manifestUrl.substring(0, manifestUrl.lastIndexOf('/') + 1)
            val lines = body.lines()

            for (i in lines.indices) {
                val line = lines[i].trim()
                if (!line.startsWith("#EXT-X-STREAM-INF:")) continue

                // Determinar calidad desde RESOLUTION (más preciso) o BANDWIDTH
                val label = run {
                    val resPart = Regex("RESOLUTION=(\\d+)x(\\d+)").find(line)
                    if (resPart != null) {
                        val h = resPart.groupValues[2].toIntOrNull() ?: 0
                        heightToLabel(h)
                    } else {
                        // Fallback: BANDWIDTH en bps → calidad aproximada
                        val bps = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                        bitrateToLabel(bps)
                    }
                }

                val trackPath = lines.getOrNull(i + 1)?.trim()
                if (label.isNotBlank() && !trackPath.isNullOrBlank() && !trackPath.startsWith("#")) {
                    val fullUrl = if (trackPath.startsWith("http")) trackPath else basePath + trackPath
                    if (uniqueVideos.none { it.first == label }) {
                        uniqueVideos.add(Pair(label, fullUrl))
                        Log.d("OkruExtractor", "HLS track: $label -> ${fullUrl.take(50)}...")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OkruExtractor", "Error parsing HLS manifest: ${e.message}")
        }
    }

    /**
     * Extrae los subtítulos del JSONObject de metadatos.
     */
    private fun extractSubtitles(json: JSONObject): List<Video.Subtitle> {
        val result = mutableListOf<Video.Subtitle>()
        val tracks = json.optJSONArray("subtitleTracks") ?: return result
        for (i in 0 until tracks.length()) {
            val track = tracks.optJSONObject(i) ?: continue
            val rawUrl = track.optString("url")
            if (rawUrl.isBlank()) continue
            var url = rawUrl
            if (url.startsWith("//")) url = "https:$url"
            url = url.replace("\\u0026", "&").replace("&amp;", "&")

            val language = track.optString("language").ifBlank { "es" }
            val title = track.optString("title").ifBlank {
                track.optString("name").ifBlank { language }
            }
            val isDefault = track.optBoolean("isDefault", false)

            Log.d("OkruExtractor", "Subtitle: title=$title lang=$language default=$isDefault")
            result.add(
                Video.Subtitle(
                    label = title,
                    file = url,
                    default = isDefault,
                    initialDefault = isDefault
                )
            )
        }
        return result
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Utilidades de calidad
    // ────────────────────────────────────────────────────────────────────────────

    /** Convierte etiquetas de calidad de ok.ru a etiquetas legibles. */
    private fun fixQuality(quality: String): String {
        val clean = quality.lowercase(java.util.Locale.ROOT).trim()
        val num = clean.toIntOrNull()
        if (num != null) {
            return when (num) {
                0, 100         -> "144p"
                1, 101         -> "240p"
                2, 102         -> "360p"
                3, 103         -> "480p"
                4, 104         -> "720p"
                5, 105         -> "1080p"
                6, 106         -> "1440p"
                7, 107         -> "4K"
                144            -> "144p"
                240            -> "240p"
                360            -> "360p"
                480            -> "480p"
                720            -> "720p"
                1080           -> "1080p"
                1440           -> "1440p"
                2160           -> "4K"
                in 100..143    -> "144p"
                in 145..239    -> "240p"
                in 241..359    -> "360p"
                in 361..479    -> "480p"
                in 481..719    -> "720p"
                in 721..1079   -> "1080p"
                in 1081..1439  -> "1440p"
                in 1440..9999  -> "4K"
                else           -> "${num}p"
            }
        }
        return when (clean) {
            "ultra", "ultrahd", "2160p", "2160", "4k" -> "4K"
            "quad",  "quadhd",  "1440p", "1440"        -> "1440p"
            "full",  "fullhd",  "1080p", "1080"        -> "1080p"
            "high",  "hd",      "720p",  "720"         -> "720p"
            "medium","sd",      "480p",  "480"         -> "480p"
            "low",              "360p",  "360"         -> "360p"
            "lowest",           "240p",  "240"         -> "240p"
            "mobile",           "144p",  "144"         -> "144p"
            else                                        -> quality
        }
    }

    /** Convierte altura de píxeles a etiqueta de calidad legible. */
    private fun heightToLabel(height: Int): String = when {
        height <= 0    -> ""
        height <= 180  -> "144p"
        height <= 270  -> "240p"
        height <= 400  -> "360p"
        height <= 540  -> "480p"
        height <= 810  -> "720p"
        height <= 1200 -> "1080p"
        height <= 1600 -> "1440p"
        else           -> "4K"
    }

    /** Convierte bitrate (bps) a etiqueta de calidad aproximada. */
    private fun bitrateToLabel(bps: Long): String = when {
        bps <= 0           -> ""
        bps <= 300_000     -> "144p"
        bps <= 600_000     -> "240p"
        bps <= 1_000_000   -> "360p"
        bps <= 2_000_000   -> "480p"
        bps <= 4_000_000   -> "720p"
        bps <= 8_000_000   -> "1080p"
        bps <= 15_000_000  -> "1440p"
        else               -> "4K"
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Retrofit service (solo para HTML scraping fallback)
    // ────────────────────────────────────────────────────────────────────────────

    private interface Service {
        companion object {
            fun build(baseUrl: String): Service {
                val client = OkHttpClient.Builder()
                    .followRedirects(true)
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header(
                                    "User-Agent",
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                                )
                                .build()
                        )
                    }
                    .build()

                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .client(client)
                    .build()
                    .create(Service::class.java)
            }
        }

        @GET
        suspend fun get(@Url url: String): Document
    }
}