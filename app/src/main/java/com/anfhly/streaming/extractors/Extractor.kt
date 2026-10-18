package com.anfhly.streaming.extractors

import android.util.Log
import com.anfhly.streaming.data.model.Video

abstract class Extractor {

    abstract val name: String
    abstract val mainUrl: String
    open val aliasUrls: List<String> = emptyList()
    open val rotatingDomain: List<Regex> = emptyList()

    // THIS is the main method all subclasses must implement
    abstract suspend fun extract(link: String): Video

    // THIS is a convenience helper
    open suspend fun extract(link: String, server: Video.Server? = null): Video {
        return extract(link)
    }

    companion object {
        var context: android.content.Context? = null
        
        /**
         * Inicializa el contexto para extractores que lo necesiten
         */
        fun initializeContext(appContext: android.content.Context) {
            context = appContext
        }

        private val extractors = listOf(
            RpmvidExtractor(),
            StreamhubExtractor(),
            StreamtapeExtractor(),
            VidozaExtractor(),
            VidplayExtractor(),
            FilemoonExtractor(),
            VidplayExtractor.MyCloud(),
            VidplayExtractor.VidplayOnline(),
            MoflixExtractor(),
            MStreamClickExtractor(),
            MoviesapiExtractor(),
            LuluVdoExtractor(),
            DoodLaExtractor(),
            DoodLaExtractor.DoodLiExtractor(),
            VidPlyExtractor(),
            VidMoLyExtractor(),
            VidMoLyExtractor.ToDomain(),
            VideoSibNetExtractor(),
            SaveFilesExtractor(),
            BigWarpExtractor(),
            DoodLaExtractor.DoodExtractor(),
            LoadXExtractor(),
            VidHideExtractor(),
            VeevExtractor(),
            RidooExtractor(),
            USTRExtractor(),
            VidGuardExtractor(),
            OkruExtractor(),
            GoodstreamExtractor(),
            LamovieExtractor(),
            UqloadExtractor(),
            MailRuExtractor(),
            MixDropExtractor(),
            SupervideoExtractor(),
            DroploadExtractor(),
            YourUploadExtractor(),
            VoeExtractor(),
            PlusPomlaExtractor(),
            OneuploadExtractor(),
            FsvidExtractor(),
            GoogleDriveExtractor(),
            PcloudExtractor(),
            AmazonDriveExtractor(),
            GuploadExtractor(),
            StreamUpExtractor(),
            EinschaltenExtractor(),
            VidrockExtractor(),
            VidzeeExtractor(),
            PrimeSrcExtractor(),
            VidoraExtractor(),
            GxPlayerExtractor(),
            UpZurExtractor(),
            DailymotionExtractor(),
            ApiVoirFilmExtractor(),
            StreamixExtractor(),
            ShareCloudyExtractor(),
            StreamrubyExtractor(),
            VidaraExtractor(),
            VidsonicExtractor(),
            HxfileExtractor(),
            EmbedStExtractor()
        )

        suspend fun extract(link: String, server: Video.Server? = null): Video {
            var finalLink = link
            
            // 1. RISOLUZIONE BRIDGE UNIVERSALE (StreamHG/Sync/Cuevana)
            // Facciamo questo PRIMA di cercare l'estrattore perchÃ© il link bridge (es. mysync.mov)
            // non appartiene a nessun estrattore specifico, ma il link risolto sÃ¬ (es. filemoon).
            if (link.contains("mysync.mov/stream/")) {
                try {
                    val client = okhttp3.OkHttpClient.Builder()
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build()
                    
                    val responseBody = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val request = okhttp3.Request.Builder()
                            .url(link)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                            .build()
                        client.newCall(request).execute().use { it.body?.string() }
                    } ?: ""
                    
                    val redirectUrl = responseBody.substringAfter("window.location.replace(\"", "").substringBefore("\"")
                        .ifEmpty { responseBody.substringAfter("window.location.href = \"", "").substringBefore("\"") }
                        .ifEmpty { responseBody.substringAfter("src=\"", "").substringBefore("\"") }
                    
                    if (redirectUrl.isNotEmpty() && redirectUrl.startsWith("http")) {
                        Log.d("Extractor", "Universal Bridge resolved: $link -> $redirectUrl")
                        finalLink = redirectUrl
                    }
                } catch (e: Exception) {
                    Log.e("Extractor", "Universal Bridge error: ${e.message}")
                }
            }

            val urlRegex = Regex("^(https?://)?(www\\.)?")
            val compareUrl = finalLink.lowercase().replace(urlRegex, "")
            
            Log.d("Extractor", "Finding extractor for URL: $finalLink")
            Log.d("Extractor", "Compare URL: $compareUrl")

            var foundExtractor: Extractor? = null

            for (extractor in extractors) {
                val extractorMain = extractor.mainUrl.replace(urlRegex, "")
                if (compareUrl.startsWith(extractorMain) || compareUrl.contains(".$extractorMain")) {
                    Log.d("Extractor", "Found by mainUrl: ${extractor.name}")
                    foundExtractor = extractor
                    break
                } else {
                    for (aliasUrl in extractor.aliasUrls) {
                        val aliasMain = aliasUrl.lowercase().replace(urlRegex, "")
                        if (compareUrl.startsWith(aliasMain) || compareUrl.contains(".$aliasMain")) {
                            Log.d("Extractor", "Found by aliasUrl: ${extractor.name}")
                            foundExtractor = extractor
                            break
                        }
                    }
                }
                if (foundExtractor != null) break
            }

            if (foundExtractor == null) {
                for (extractor in extractors) {
                    if (compareUrl.startsWith(
                            extractor.mainUrl.replace(
                                Regex("^(https?://)?(www\\.)?(.*?)(\\.[a-z]+)"),
                                "$3"
                            )
                        )
                    ) {
                        foundExtractor = extractor
                        break
                    } else {
                        for (aliasUrl in extractor.aliasUrls) {
                            if (compareUrl.startsWith(
                                    aliasUrl.replace(
                                        Regex("^(https?://)?(www\\.)?(.*?)(\\.[a-z]+)"),
                                        "$3"
                                    )
                                )
                            ) {
                                foundExtractor = extractor
                                break
                            }
                        }
                    }
                    if (foundExtractor != null) break
                }
            }

            if (foundExtractor == null) {
                for (extractor in extractors) {
                    if (extractor.rotatingDomain.any { it.containsMatchIn(compareUrl) }) {
                        foundExtractor = extractor
                        break
                    }
                }
            }

            if (foundExtractor == null) {
                for (extractor in extractors) {
                    if ((server?.name?.lowercase() ?: "").contains(extractor.name.lowercase())) {
                        foundExtractor = extractor
                        break
                    }
                }
            }

            if (foundExtractor != null) {
                Log.i("StreamFlixES", "[EXTRACTOR] -> Starting: ${foundExtractor.name} (URL: $finalLink)")
                val video = foundExtractor.extract(finalLink)
                Log.i("StreamFlixES", "[VIDEO] -> Extracted: ${video.source}")
                return video
            }

            throw Exception("No extractors found for URL: $finalLink")
        }

        fun hasExtractor(link: String): Boolean {
            val urlRegex = Regex("^(https?://)?(www\\.)?")
            val compareUrl = link.lowercase().replace(urlRegex, "")

            for (extractor in extractors) {
                val extractorMain = extractor.mainUrl.replace(urlRegex, "")
                if (compareUrl.startsWith(extractorMain) || compareUrl.contains(".$extractorMain")) {
                    return true
                }
                for (aliasUrl in extractor.aliasUrls) {
                    val aliasMain = aliasUrl.lowercase().replace(urlRegex, "")
                    if (compareUrl.startsWith(aliasMain) || compareUrl.contains(".$aliasMain")) {
                        return true
                    }
                }
            }

            for (extractor in extractors) {
                if (compareUrl.startsWith(
                        extractor.mainUrl.replace(
                            Regex("^(https?://)?(www\\.)?(.*?)(\\.[a-z]+)"),
                            "$3"
                        )
                    )
                ) {
                    return true
                }
                for (aliasUrl in extractor.aliasUrls) {
                    if (compareUrl.startsWith(
                            aliasUrl.replace(
                                Regex("^(https?://)?(www\\.)?(.*?)(\\.[a-z]+)"),
                                "$3"
                            )
                        )
                    ) {
                        return true
                    }
                }
            }

            for (extractor in extractors) {
                if (extractor.rotatingDomain.any { it.containsMatchIn(compareUrl) }) {
                    return true
                }
            }

            return false
        }
    }
}
