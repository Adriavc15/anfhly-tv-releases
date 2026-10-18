package com.anfhly.streaming.extractors

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OkruExtractorTest {

    @Test
    fun testOkruExtractionWithActiveVideo() = runBlocking {
        val extractor = OkruExtractor()
        val link = "https://ok.ru/video/15469616696046"
        
        println("Testing extraction for link: $link")
        val video = extractor.extract(link)
        
        println("Extracted video source: ${video.source}")
        println("Extracted video type: ${video.type}")
        println("Extracted qualities count: ${video.qualities.size}")
        video.qualities.forEach { quality ->
            println(" Quality: ${quality.label} -> ${quality.url.take(60)}...")
        }
        
        assertNotNull("Video source should not be null", video.source)
        assertTrue("Video source should start with http", video.source.startsWith("http"))
    }
}
