package com.anfhly.streaming.utils

import okhttp3.Dns
import java.net.InetAddress

/**
 * DNS Resolver - simplified version without DoH
 */
object DnsResolver {
    val doh: Dns = Dns.SYSTEM
}
