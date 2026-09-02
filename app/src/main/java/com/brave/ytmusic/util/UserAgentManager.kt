package com.brave.ytmusic.util

import android.content.Context
import android.webkit.WebSettings

/**
 * Manages User-Agent configuration to ensure full Google OAuth login compatibility
 * without triggering the Google 403 "disallowed_useragent" security block.
 *
 * Specific target: Samsung Galaxy S24 FE (SM-S711B, Android 14 / One UI 6).
 */
object UserAgentManager {

    private const val CHROME_VERSION = "124.0.6367.179"
    private const val DEVICE_MODEL = "SM-S711B"
    private const val ANDROID_VERSION = "14"

    /**
     * Builds a clean standard Mobile Chrome user agent string.
     * Strips "wv", "Version/4.0", and other embedded WebView identifiers that Google uses
     * to block OAuth logins.
     */
    fun getCustomMobileUserAgent(context: Context): String {
        return "Mozilla/5.0 (Linux; Android $ANDROID_VERSION; $DEVICE_MODEL) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/$CHROME_VERSION Mobile Safari/537.36"
    }

    /**
     * Applies the sanitized User-Agent and security configurations to the provided WebSettings.
     */
    fun applyUserAgent(settings: WebSettings, context: Context) {
        settings.userAgentString = getCustomMobileUserAgent(context)
    }
}
