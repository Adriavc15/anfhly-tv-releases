package com.anfhly.streaming.extractors

import com.anfhly.streaming.utils.JsoupConverterFactory
import com.anfhly.streaming.data.model.Video
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.net.URL

class GoodstreamExtractor : Extractor() {

    override val name = "Goodstream"
    override val mainUrl = "https://goodstream.one"
    override val aliasUrls = listOf("https://gscdn.cam")

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    override suspend fun extract(link: String): Video {
        val linkHost = try {
            val u = java.net.URL(link)
            "${u.protocol}://${u.host}"
        } catch (e: Exception) { mainUrl }

        val service = Service.build(linkHost)
        val document = service.get(link)
        val scriptTags = document.select("script[type*=javascript]")

        val referer = "$linkHost/"
        val headers = mapOf(
            "User-Agent" to userAgent,
            "Referer" to referer,
            "Origin" to linkHost
        )

        // Extraer múltiples sources del jwplayer (file + label)
        data class Source(val file: String, val label: String?)
        val sources = mutableListOf<Source>()

        for (script in scriptTags) {
            val scriptData = script.data()
            if ("jwplayer" !in scriptData || "sources" !in scriptData) continue

            val sourcesBlock = Regex("""sources\s*:\s*\[([\s\S]*?)]""")
                .find(scriptData)?.groupValues?.get(1)

            if (sourcesBlock != null) {
                val fileRegex = Regex("""file\s*:\s*["']([^"']+)["']""")
                val labelRegex = Regex("""label\s*:\s*["']([^"']+)["']""")
                val files = fileRegex.findAll(sourcesBlock).map { it.groupValues[1] }.toList()
                val labels = labelRegex.findAll(sourcesBlock).map { it.groupValues[1] }.toList()
                files.forEachIndexed { i, file ->
                    sources.add(Source(file, labels.getOrNull(i)))
                }
                if (sources.isNotEmpty()) break
            } else {
                val match = Regex("""file\s*:\s*["']([^"']+)["']""").find(scriptData)
                if (match != null) {
                    sources.add(Source(match.groupValues[1], null))
                    break
                }
            }
        }

        if (sources.isEmpty()) throw Exception("Can't retrieve source from Goodstream")

        val primaryUrl = sources.first().file

        if (sources.size > 1) {
            val qualities = sources.mapIndexed { i, src ->
                Video.Quality(
                    label = src.label ?: "${i + 1}",
                    url = src.file,
                    selected = i == 0
                )
            }
            return Video(source = primaryUrl, headers = headers, qualities = qualities)
        }

        return Video(source = primaryUrl, headers = headers)
    }

    private interface Service {

        companion object {
            fun build(baseUrl: String): Service {
                val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                
                val client = OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .addHeader("User-Agent", userAgent)
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
}
