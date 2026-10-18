package com.anfhly.streaming.utils

import android.util.Base64
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Decrypt helper for encrypted video sources
 */
object DecryptHelper {
    
    fun findEncodedRegex(html: String): String? {
        // Look for patterns like "var ... = 'base64encodedstring'"
        val patterns = listOf(
            Regex("""var\s+\w+\s*=\s*['\"]([A-Za-z0-9+/=]{50,})['\"]"""),
            Regex("""['\"]([A-Za-z0-9+/=]{100,})['\"]""")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(html)
            if (match != null) {
                val candidate = match.groupValues[1]
                // Verify it's valid base64
                if (isValidBase64(candidate)) {
                    return candidate
                }
            }
        }
        return null
    }
    
    private fun isValidBase64(str: String): Boolean {
        return try {
            Base64.decode(str, Base64.DEFAULT)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun decrypt(encodedData: String): JsonElement {
        val decoded = String(Base64.decode(encodedData, Base64.DEFAULT))
        return JsonParser.parseString(decoded)
    }
    
    fun decryptAES(encryptedData: String, key: String, iv: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(key.toByteArray(), "AES")
        val ivSpec = IvParameterSpec(iv.toByteArray())
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        val decoded = Base64.decode(encryptedData, Base64.DEFAULT)
        val decrypted = cipher.doFinal(decoded)
        return String(decrypted)
    }
    
    fun decryptAESHex(encryptedData: String, key: String, iv: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(hexToBytes(key), "AES")
        val ivSpec = IvParameterSpec(hexToBytes(iv))
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        val decoded = hexToBytes(encryptedData)
        val decrypted = cipher.doFinal(decoded)
        return String(decrypted)
    }
    
    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }
}
