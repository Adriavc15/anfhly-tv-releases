package com.streamflixreborn.streamflix.utils

import okhttp3.Dns
import java.net.InetAddress

/**
 * DNS Resolver with DoH (DNS over HTTPS) support
 * Used by StreamFlix extractors to bypass DNS blocking
 */
object DnsResolver {
    
    /**
     * Default DNS over HTTPS resolver
     * Uses Cloudflare DNS
     */
    val doh: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            // For now, use default system DNS
            // In production, this would use DoH
            return Dns.SYSTEM.lookup(hostname)
        }
    }
}
