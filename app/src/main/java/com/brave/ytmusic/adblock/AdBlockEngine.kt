package com.brave.ytmusic.adblock

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance, in-memory Brave-grade request interceptor and filter list evaluator.
 * Identifies and drops ad network requests, tracking beacons, and mid-roll telemetry
 * before network sockets are opened.
 */
class AdBlockEngine(private val context: Context) {

    private val blockedDomains = ConcurrentHashMap.newKeySet<String>()
    private val blockedUrlPatterns = mutableListOf<String>()

    @Volatile
    private var isInitialized = false

    init {
        loadFilterList()
    }

    private fun loadFilterList() {
        try {
            context.assets.open("adblock_filter.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            if (trimmed.contains("/")) {
                                blockedUrlPatterns.add(trimmed)
                            } else {
                                blockedDomains.add(trimmed.lowercase())
                            }
                        }
                        line = reader.readLine()
                    }
                }
            }
            isInitialized = true
        } catch (e: Exception) {
            // Fallback default set if asset read fails
            blockedDomains.addAll(
                setOf(
                    "googleads.g.doubleclick.net",
                    "pagead2.googlesyndication.com",
                    "pubads.g.doubleclick.net",
                    "securepubads.g.doubleclick.net",
                    "adservice.google.com",
                    "ads.youtube.com",
                    "ad.youtube.com",
                    "googleadservices.com"
                )
            )
            isInitialized = true
        }
    }

    private fun isWhitelisted(host: String): Boolean {
        return host == "music.youtube.com" ||
                host == "accounts.google.com" ||
                host.endsWith(".googlevideo.com") ||
                host.endsWith(".ytimg.com") ||
                host.endsWith(".gstatic.com")
    }

    /**
     * Determines whether an outgoing Web resource request should be blocked.
     */
    fun shouldBlock(uri: Uri): Boolean {
        if (!isInitialized) return false

        val host = uri.host?.lowercase() ?: return false
        val fullUrl = uri.toString().lowercase()

        // Inspect googlevideo media streams for ad segments
        if (host.endsWith(".googlevideo.com")) {
            val query = uri.query?.lowercase() ?: ""
            if (
                query.contains("adformat=") ||
                query.contains("ad_type=") ||
                query.contains("ctier=l") ||
                query.contains("ad_cpn=") ||
                query.contains("ad_v=") ||
                query.contains("gis=")
            ) {
                return true // Drop video/audio ad segments
            }
            return false // Allow genuine music stream
        }

        // Critical Whitelist: Never block essential YouTube streaming and account domains
        if (isWhitelisted(host)) {
            // Check if specific blocked URL sub-path is present even on whitelisted hosts
            for (pattern in blockedUrlPatterns) {
                if (fullUrl.contains(pattern)) {
                    return true
                }
            }
            return false
        }

        // Direct domain match
        if (blockedDomains.contains(host)) {
            return true
        }

        // Subdomain matching (e.g., ad.doubleclick.net -> doubleclick.net)
        for (blocked in blockedDomains) {
            if (host.endsWith(".$blocked")) {
                return true
            }
        }

        // Pattern matching
        for (pattern in blockedUrlPatterns) {
            if (fullUrl.contains(pattern)) {
                return true
            }
        }

        return false
    }

    /**
     * Returns an empty HTTP 204 response to cleanly neutralize blocked requests.
     */
    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            204,
            "No Content",
            mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(ByteArray(0))
        )
    }
}
