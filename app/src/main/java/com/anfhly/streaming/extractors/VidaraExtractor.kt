package com.anfhly.streaming.extractors

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import com.anfhly.streaming.data.model.Video
import com.anfhly.streaming.utils.DnsResolver
import com.anfhly.streaming.utils.FFmpegManager
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.HttpException
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.net.URL
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class VidaraExtractor : Extractor() {

    override val name = "Vidara"
    override val mainUrl = "https://vidara.to"
    override val aliasUrls = listOf("https://vidara.so")

    override suspend fun extract(link: String): Video {
        Log.d("VidaraExtractor", "Extracting from URL: $link")

        val url = URL(link)
        Log.d("VidaraExtractor", "URL path: '${url.path}', file: '${url.file}'")

        val pathParts = url.path.split("/").filter { it.isNotEmpty() }
        Log.d("VidaraExtractor", "Path parts: $pathParts")

        val fileCode = pathParts.lastOrNull { it.isNotEmpty() } ?: ""
        Log.d("VidaraExtractor", "File code extracted: '$fileCode'")

        if (fileCode.isEmpty()) {
            throw Exception("File code not found in URL")
        }

        val baseUrl = URL(link).protocol + "://" + URL(link).host
        Log.d("VidaraExtractor", "Base URL: $baseUrl")

        val service = Service.build(baseUrl)

        val apiKey = "2a8e6e9876426fb5bd5a461a1930b1dde2da0ecd1c1aa2f020b1a5760ee47847"
        Log.d("VidaraExtractor", "Calling API with filecode: $fileCode, apiKey: ${apiKey.take(10)}...")
        Log.d("VidaraExtractor", "API endpoint: ${baseUrl}/api/stream")

        val request = StreamRequest(filecode = fileCode, api_key = apiKey)
        Log.d("VidaraExtractor", "Sending JSON request: filecode=${request.filecode}, api_key=${request.api_key.take(10)}...")

        val responseString = try {
            val responseBody = service.getStream(request)
            responseBody.string()
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e("VidaraExtractor", "HTTP ${e.code()} error: $errorBody")
            throw Exception("API error ${e.code()}: $errorBody")
        }
        Log.d("VidaraExtractor", "API response: ${responseString.take(300)}...")

        val jsonObject = try {
            JsonParser.parseString(responseString).asJsonObject
        } catch (e: Exception) {
            Log.e("VidaraExtractor", "Failed to parse JSON: ${e.message}")
            throw Exception("Failed to parse API response: ${e.message}")
        }

        val streamingUrl = jsonObject.get("streaming_url")?.asString
        Log.d("VidaraExtractor", "Streaming URL: ${streamingUrl?.take(80)}...")

        if (streamingUrl == null) {
            Log.e("VidaraExtractor", "streaming_url not found in response. Keys: ${jsonObject.keySet()}")
            throw Exception("Streaming URL not found in API response")
        }

        val defaultSub = jsonObject.get("default_sub_lang")?.asString ?: ""
        var alreadySelect = false
        val subtitles = try {
            if (jsonObject.has("subtitles")) {
                val subtitlesArray = jsonObject.getAsJsonArray("subtitles")
                subtitlesArray.mapNotNull { elem ->
                    val obj = elem.asJsonObject
                    val label = obj.get("language")?.asString ?: ""
                    val filePath = obj.get("file_path")?.asString ?: ""
                    val absolutePath = if (filePath.startsWith("/")) "$baseUrl$filePath" else filePath
                    Video.Subtitle(
                        label = label,
                        file = absolutePath,
                        default = if (alreadySelect == false && defaultSub.isNotEmpty() && label.contains(defaultSub)) {
                            alreadySelect = true
                            true
                        } else {
                            false
                        }
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.w("VidaraExtractor", "Error parsing subtitles: ${e.message}")
            emptyList()
        }

        // Extraer pistas de audio del M3U8
        Log.d("VidaraExtractor", "Extracting audio tracks from M3U8...")
        val audios = extractAudioTracksFromM3U8(streamingUrl)

        Log.d("VidaraExtractor", "Extracted ${audios.size} audio tracks: ${audios.map { it.label }}")
        Log.d("VidaraExtractor", "Extracted ${subtitles.size} subtitles: ${subtitles.map { it.label }}")

        return Video(
            source = streamingUrl,
            subtitles = subtitles,
            audios = audios,
            expiresAt = null // Vidara sin expiración
        )
    }

    private interface Service {

        companion object {
            fun build(baseUrl: String): Service {
                val client = OkHttpClient.Builder()
                    .dns(DnsResolver.doh)
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("Accept", "application/json")
                            .build()
                        Log.d("VidaraExtractor", "Request: ${request.method} ${request.url}")
                        chain.proceed(request)
                    }
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                return retrofit.create(Service::class.java)
            }
        }

        @POST("api/stream")
        suspend fun getStream(
            @Body request: StreamRequest
        ): ResponseBody
    }

    data class StreamRequest(
        val filecode: String,
        val api_key: String
    )

    private suspend fun extractAudioTracksFromM3U8(m3u8Url: String): List<Video.AudioTrack> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d("VidaraExtractor", "Fetching M3U8 content from: $m3u8Url")
            
            val client = OkHttpClient.Builder()
                .dns(DnsResolver.doh)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("Accept", "application/vnd.apple.mpegurl")
                        .build()
                    chain.proceed(request)
                }
                .build()

            val request = okhttp3.Request.Builder()
                .url(m3u8Url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val m3u8Content = response.body?.string() ?: ""
            
            Log.d("VidaraExtractor", "M3U8 content length: ${m3u8Content.length}")
            
            if (m3u8Content.isEmpty()) {
                Log.w("VidaraExtractor", "Empty M3U8 content")
                return@withContext emptyList()
            }

            val audioTracks = mutableListOf<Video.AudioTrack>()
            val lines = m3u8Content.split("\n")
            
            var currentTrackName = ""
            var currentTrackLanguage = ""
            var currentTrackUri = ""
            var isDefaultTrack = false
            var spanishTrackFound = false
            
            for (i in lines.indices) {
                val line = lines[i].trim()
                
                if (line.startsWith("#EXT-X-MEDIA:TYPE=AUDIO")) {
                    // Extraer información de la pista de audio
                    currentTrackName = extractAttribute(line, "NAME") ?: ""
                    currentTrackLanguage = extractAttribute(line, "LANGUAGE") ?: ""
                    val rawUri = extractAttribute(line, "URI") ?: ""
                    
                    // Resolve relative URI against base M3U8 URL
                    currentTrackUri = if (rawUri.isNotEmpty() && !rawUri.startsWith("http")) {
                        val baseUrl = m3u8Url.substringBeforeLast("/") + "/"
                        baseUrl + rawUri
                    } else {
                        rawUri
                    }
                    
                    isDefaultTrack = extractAttribute(line, "DEFAULT") == "YES"
                    
                    if (currentTrackUri.isNotEmpty() && currentTrackName.isNotEmpty()) {
                        // Verificar si la pista de audio es accesible
                        val isAccessible = isAudioTrackAccessible(currentTrackUri)
                        
                        if (isAccessible) {
                            val isSpanish = currentTrackLanguage.contains("spa", ignoreCase = true) || 
                                           currentTrackLanguage.contains("es", ignoreCase = true) ||
                                           currentTrackName.contains("Spanish", ignoreCase = true)
                            
                            // Marcar como default si es español o si está marcado como default
                            val shouldBeDefault = isSpanish || (isDefaultTrack && !spanishTrackFound)
                            
                            if (isSpanish && !spanishTrackFound) {
                                spanishTrackFound = true
                            }
                            
                            Log.d("VidaraExtractor", "Found accessible audio track: name='$currentTrackName', lang='$currentTrackLanguage', uri='$currentTrackUri', default=$shouldBeDefault")
                            
                            audioTracks.add(
                                Video.AudioTrack(
                                    label = currentTrackName,
                                    language = currentTrackLanguage.ifEmpty { currentTrackName },
                                    file = currentTrackUri,
                                    default = shouldBeDefault
                                )
                            )
                        } else {
                            Log.w("VidaraExtractor", "Skipping inaccessible audio track: $currentTrackName ($currentTrackUri)")
                        }
                    }
                }
            }
            
            // Si no se encontraron pistas de audio accesibles, devolver lista vacía para usar audio embebido
            if (audioTracks.isEmpty()) {
                Log.d("VidaraExtractor", "No accessible audio tracks found, will use embedded audio")
                return@withContext emptyList()
            }
            
            // Reordenar para que el español esté primero si existe
            val sortedTracks = audioTracks.sortedWith { a, b ->
                when {
                    a.language.contains("spa", ignoreCase = true) && !b.language.contains("spa", ignoreCase = true) -> -1
                    !a.language.contains("spa", ignoreCase = true) && b.language.contains("spa", ignoreCase = true) -> 1
                    else -> 0
                }
            }
            
            Log.d("VidaraExtractor", "Extracted ${sortedTracks.size} accessible audio tracks from M3U8")
            sortedTracks
            
        } catch (e: Exception) {
            Log.e("VidaraExtractor", "Error extracting audio tracks from M3U8: ${e.message}", e)
            emptyList()
        }
    }
    
    private suspend fun isAudioTrackAccessible(audioUri: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d("VidaraExtractor", "Checking accessibility of audio track: $audioUri")
            
            val client = OkHttpClient.Builder()
                .dns(DnsResolver.doh)
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val request = okhttp3.Request.Builder()
                .url(audioUri)
                .head()
                .build()

            val response = client.newCall(request).execute()
            val isAccessible = response.isSuccessful
            
            Log.d("VidaraExtractor", "Audio track accessibility check: $audioUri -> ${response.code} ($isAccessible)")
            response.close()
            
            isAccessible
            
        } catch (e: Exception) {
            Log.w("VidaraExtractor", "Audio track not accessible: $audioUri - ${e.message}")
            false
        }
    }
    
    private fun extractAttribute(line: String, attributeName: String): String? {
        val pattern = Pattern.compile("$attributeName=\"([^\"]+)\"")
        val matcher = pattern.matcher(line)
        return if (matcher.find()) matcher.group(1) else null
    }
}
