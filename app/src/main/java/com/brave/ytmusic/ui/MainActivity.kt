package com.brave.ytmusic.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.brave.ytmusic.adblock.AdBlockEngine
import com.brave.ytmusic.bridge.WebInterfaceBridge
import com.brave.ytmusic.service.PlaybackService
import com.brave.ytmusic.timer.SleepTimerManager
import com.brave.ytmusic.ui.components.SleepTimerSheet
import com.brave.ytmusic.ui.theme.AmoledBlack
import com.brave.ytmusic.ui.theme.BraveMusicTheme
import com.brave.ytmusic.ui.theme.YtmRed
import com.brave.ytmusic.util.CookieSyncManager
import com.brave.ytmusic.util.UserAgentManager
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {

    private lateinit var adBlockEngine: AdBlockEngine
    private var webView: WebView? = null
    private var webBridge: WebInterfaceBridge? = null
    private var playbackService: PlaybackService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PlaybackService.LocalBinder
            playbackService = binder.getService()
            webBridge?.let { playbackService?.setBridge(it) }
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            isServiceBound = false
        }
    }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Permission result handled
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        adBlockEngine = AdBlockEngine(applicationContext)
        checkNotificationPermission()
        startAndBindPlaybackService()

        setContent {
            BraveMusicTheme {
                MainScreen()
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startAndBindPlaybackService() {
        val serviceIntent = Intent(this, PlaybackService::class.java)
        try {
            startService(serviceIntent)
        } catch (e: Exception) {
            // Service startup handled by framework
        }
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    @Composable
    private fun MainScreen() {
        var showSleepTimerSheet by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val sleepTimerManager = remember {
            SleepTimerManager(scope) { webBridge }
        }
        val timerState by sleepTimerManager.timerState.collectAsState()

        BackHandler(enabled = webView?.canGoBack() == true) {
            webView?.goBack()
        }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(AmoledBlack),
            containerColor = AmoledBlack
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AndroidView(
                    factory = { context ->
                        createConfiguredWebView(context)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Sleep Timer Quick Access Floating Action Button
                FloatingActionButton(
                    onClick = { showSleepTimerSheet = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 72.dp, end = 16.dp)
                        .size(44.dp),
                    shape = CircleShape,
                    containerColor = if (timerState.isActive) YtmRed else Color(0x99222222),
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = "Sleep Timer",
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (showSleepTimerSheet) {
                    SleepTimerSheet(
                        sleepTimerManager = sleepTimerManager,
                        onDismissRequest = { showSleepTimerSheet = false }
                    )
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                CookieSyncManager.flushCookies()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createConfiguredWebView(context: Context): WebView {
        return WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)

            val bridge = WebInterfaceBridge(this)
            webBridge = bridge
            playbackService?.setBridge(bridge)

            // Setup Cookies & Auth Persistence
            CookieSyncManager.setupCookies(this)

            // Configure Hardened WebSettings
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                safeBrowsingEnabled = false // Prevent adblock false alarms
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                loadsImagesAutomatically = true
                useWideViewPort = true
                loadWithOverviewMode = true
                displayZoomControls = false
                builtInZoomControls = false
                setSupportZoom(false)

                // Enforce Mobile Chrome on Samsung S24 FE (SM-S711B) for Google OAuth
                UserAgentManager.applyUserAgent(this, context)
            }

            // Expose Native Bridge
            addJavascriptInterface(bridge, "AndroidBridge")

            // Inject DOM Shield & AdBlock Interception Client
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url ?: return null
                    if (adBlockEngine.shouldBlock(url)) {
                        return adBlockEngine.createEmptyResponse()
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    injectShieldScript(this@apply)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectShieldScript(this@apply)
                    CookieSyncManager.flushCookies()
                }
            }

            webChromeClient = object : WebChromeClient() {
                // Allows modern video DOM elements and title tracking
            }

            loadUrl("https://music.youtube.com")
            webView = this
        }
    }

    private fun injectShieldScript(targetWebView: WebView) {
        try {
            val inputStream = assets.open("inject.js")
            val script = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            targetWebView.evaluateJavascript(script, null)
        } catch (e: Exception) {
            // Script load fallback handled silently
        }
    }

    override fun onPause() {
        super.onPause()
        CookieSyncManager.flushCookies()
    }

    override fun onDestroy() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        webView?.destroy()
        webView = null
        super.onDestroy()
    }
}
