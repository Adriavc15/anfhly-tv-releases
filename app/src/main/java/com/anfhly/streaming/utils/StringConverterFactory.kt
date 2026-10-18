package com.anfhly.streaming.utils

import okhttp3.MediaType
import okhttp3.ResponseBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * String converter factory for Retrofit
 */
class StringConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        return if (type == String::class.java) {
            Converter<ResponseBody, String> { value -> value.string() }
        } else null
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<*, okhttp3.RequestBody>? {
        return if (type == String::class.java) {
            Converter<String, okhttp3.RequestBody> { value ->
                okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), value)
            }
        } else null
    }

    companion object {
        fun create(): StringConverterFactory = StringConverterFactory()
    }
}
