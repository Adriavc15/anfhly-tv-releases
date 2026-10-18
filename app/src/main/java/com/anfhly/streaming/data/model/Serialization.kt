package com.anfhly.streaming.data.model

import java.io.Serializable as JavaSerializable

// Extension to make sure Content can be passed via Intent
@Suppress("UNCHECKED_CAST")
fun <T : JavaSerializable> Content.asSerializable(): T = this as T

@Suppress("UNCHECKED_CAST")
fun <T : JavaSerializable> Episode.asSerializable(): T = this as T
