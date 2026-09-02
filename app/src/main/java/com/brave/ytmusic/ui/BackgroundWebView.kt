package com.brave.ytmusic.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView

/**
 * Hardened WebView that permanently prevents Chromium's native C++ rendering pipeline
 * from detecting window focus loss, screen off, or minimization.
 *
 * Chromium's RenderWidgetHostViewAndroid suspends HTML5 audio/video decoders and throttles
 * JavaScript timers whenever onWindowVisibilityChanged or onVisibilityChanged receives
 * View.GONE or View.INVISIBLE. By forcing View.VISIBLE at the Android View boundary,
 * audio decoding and streaming continue uninterrupted when the screen turns off.
 */
class BackgroundWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    override fun onWindowVisibilityChanged(visibility: Int) {
        // ALWAYS inform Chromium that the window is VISIBLE
        super.onWindowVisibilityChanged(View.VISIBLE)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        // ALWAYS inform Chromium that the view is VISIBLE
        super.onVisibilityChanged(changedView, View.VISIBLE)
    }

    override fun dispatchWindowVisibilityChanged(visibility: Int) {
        // Prevent window visibility change events from propagating to child renderers
        super.dispatchWindowVisibilityChanged(View.VISIBLE)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        // ALWAYS report true so Chromium never pauses HTML5 media elements
        super.onWindowFocusChanged(true)
    }

    override fun hasWindowFocus(): Boolean {
        return true
    }
}
