package com.brave.ytmusic.util

import android.webkit.CookieManager
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Ensures session tokens, OAuth credentials, and YouTube Music state
 * remain persistent across app restarts and process lifecycles.
 */
object CookieSyncManager {

    /**
     * Configures the CookieManager for the WebView instance.
     */
    fun setupCookies(webView: WebView) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
    }

    /**
     * Asynchronously flushes all in-memory cookies to persistent storage.
     */
    fun flushCookies() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                CookieManager.getInstance().flush()
            } catch (e: Exception) {
                // Ignore transient sync failures
            }
        }
    }
}
