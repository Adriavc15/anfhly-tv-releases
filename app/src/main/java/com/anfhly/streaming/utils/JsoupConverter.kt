package com.anfhly.streaming.utils

import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import retrofit2.Converter
import java.nio.charset.Charset
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import android.util.Log

class JsoupConverter(
    private val baseUri: String,
) : Converter<ResponseBody, Document?> {

    override fun convert(value: ResponseBody): Document? {
        val contentType = value.contentType()
        val isXml = contentType?.subtype?.contains("xml", ignoreCase = true) == true
        val parser = if (isXml) Parser.xmlParser() else Parser.htmlParser()

        val sourceBytes = value.bytes()
        Log.d("JsoupConverter", "Got ${sourceBytes.size} bytes")
        
        // Log first few bytes for debugging
        if (sourceBytes.size >= 10) {
            val hexPreview = sourceBytes.take(10).joinToString("") { 
                String.format("%02x", it.toInt() and 0xFF) 
            }
            Log.d("JsoupConverter", "First 10 bytes: $hexPreview")
        }
        
        // Try multiple detection methods
        val body = try {
            // Method 1: Check for gzip magic bytes
            if (sourceBytes.size >= 2 && 
                sourceBytes[0].toInt() and 0xFF == 0x1f && 
                sourceBytes[1].toInt() and 0xFF == 0x8b) {
                
                Log.d("JsoupConverter", "Detected gzip content, decompressing...")
                decompressGzip(sourceBytes)
                
            } else {
                // Method 2: Try different character encodings first
                Log.d("JsoupConverter", "Trying different character encodings...")
                
                val encodings = listOf(
                    Charsets.UTF_8,
                    Charsets.ISO_8859_1,
                    Charsets.UTF_16,
                    Charsets.UTF_16BE,
                    Charsets.UTF_16LE,
                    Charsets.US_ASCII
                )
                
                for (encoding in encodings) {
                    try {
                        val decoded = String(sourceBytes, encoding)
                        val hasValidHtml = decoded.contains("<html") || decoded.contains("<HTML") || 
                                         decoded.contains("<!DOCTYPE") || decoded.contains("vidmoly")
                        
                        if (hasValidHtml) {
                            Log.d("JsoupConverter", "Successfully decoded with ${encoding.toString()}")
                            return Jsoup.parse(decoded)
                        }
                    } catch (e: Exception) {
                        Log.d("JsoupConverter", "Failed to decode with ${encoding.toString()}: ${e.message}")
                    }
                }
                
                // Method 3: Check if content contains non-printable characters (likely compressed)
                val textSample = String(sourceBytes.take(100).toByteArray(), Charsets.UTF_8)
                val hasNonPrintableChars = textSample.any { char -> 
                    char.code < 32 && char.code != 9 && char.code != 10 && char.code != 13 
                }
                
                if (hasNonPrintableChars) {
                    Log.d("JsoupConverter", "Content has non-printable chars, trying multiple decompression methods...")
                    
                    // Try gzip anyway (some servers don't set proper headers)
                    try {
                        decompressGzip(sourceBytes)
                    } catch (e: Exception) {
                        Log.d("JsoupConverter", "Gzip decompression failed: ${e.message}")
                        
                        // Try deflate (zlib)
                        try {
                            decompressDeflate(sourceBytes)
                        } catch (e2: Exception) {
                            Log.d("JsoupConverter", "Deflate decompression failed: ${e2.message}")
                            
                            // Try raw deflate
                            try {
                                decompressRawDeflate(sourceBytes)
                            } catch (e3: Exception) {
                                Log.d("JsoupConverter", "Raw deflate decompression failed: ${e3.message}")
                                Log.d("JsoupConverter", "Content appears to be compressed but decompression failed, treating as plain text")
                                String(sourceBytes, Charsets.UTF_8)
                            }
                        }
                    }
                } else {
                    Log.d("JsoupConverter", "Content appears to be plain text")
                    String(sourceBytes, Charsets.UTF_8)
                }
            }
        } catch (e: Exception) {
            Log.e("JsoupConverter", "Decompression failed: ${e.message}, trying as plain text")
            String(sourceBytes, Charsets.UTF_8)
        }

        return Jsoup.parse(body, baseUri, parser)
    }
    
    private fun decompressGzip(sourceBytes: ByteArray): String {
        try {
            // Decompress the gzip content
            val bais = ByteArrayInputStream(sourceBytes)
            val gzis = GZIPInputStream(bais)
            val baos = ByteArrayOutputStream()
            
            val buffer = ByteArray(1024)
            var len: Int
            while (gzis.read(buffer).also { len = it } != -1) {
                baos.write(buffer, 0, len)
            }
            
            gzis.close()
            bais.close()
            
            val decompressedBytes = baos.toByteArray()
            Log.d("JsoupConverter", "Successfully decompressed ${sourceBytes.size} -> ${decompressedBytes.size} bytes")
            
            return String(decompressedBytes, Charsets.UTF_8)
            
        } catch (e: Exception) {
            Log.e("JsoupConverter", "Failed to decompress gzip content: ${e.message}")
            throw e
        }
    }
    
    private fun decompressDeflate(sourceBytes: ByteArray): String {
        try {
            // Try deflate (zlib format)
            val bais = ByteArrayInputStream(sourceBytes)
            val inflater = java.util.zip.Inflater(true) // true for zlib format
            inflater.setInput(sourceBytes)
            val baos = ByteArrayOutputStream()
            
            val buffer = ByteArray(1024)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                baos.write(buffer, 0, count)
            }
            
            inflater.end()
            bais.close()
            
            val decompressedBytes = baos.toByteArray()
            Log.d("JsoupConverter", "Successfully decompressed deflate ${sourceBytes.size} -> ${decompressedBytes.size} bytes")
            
            return String(decompressedBytes, Charsets.UTF_8)
            
        } catch (e: Exception) {
            Log.e("JsoupConverter", "Failed to decompress deflate content: ${e.message}")
            throw e
        }
    }
    
    private fun decompressRawDeflate(sourceBytes: ByteArray): String {
        try {
            // Try raw deflate (without zlib headers)
            val bais = ByteArrayInputStream(sourceBytes)
            val inflater = java.util.zip.Inflater(false) // false for raw deflate
            inflater.setInput(sourceBytes)
            val baos = ByteArrayOutputStream()
            
            val buffer = ByteArray(1024)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                baos.write(buffer, 0, count)
            }
            
            inflater.end()
            bais.close()
            
            val decompressedBytes = baos.toByteArray()
            Log.d("JsoupConverter", "Successfully decompressed raw deflate ${sourceBytes.size} -> ${decompressedBytes.size} bytes")
            
            return String(decompressedBytes, Charsets.UTF_8)
            
        } catch (e: Exception) {
            Log.e("JsoupConverter", "Failed to decompress raw deflate content: ${e.message}")
            throw e
        }
    }
}
