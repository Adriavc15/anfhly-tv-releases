package com.streamflixreborn.streamflix.utils

import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * Simple Jsoup Converter Factory for Retrofit
 * Converts ResponseBody to Jsoup Document
 */
class JsoupConverterFactory : Converter.Factory() {

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        return if (type == Document::class.java) {
            Converter<ResponseBody, Document> { body ->
                Jsoup.parse(body.string())
            }
        } else {
            null
        }
    }

    companion object {
        fun create(): JsoupConverterFactory {
            return JsoupConverterFactory()
        }
    }
}
