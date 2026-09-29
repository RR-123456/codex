package com.pragon.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/** Hosts the real PRAGON PhoneView app.html so the mobile UI is identical to PhoneView. */
class WebViewActivity : AppCompatActivity() {
    companion object { const val EXTRA_URL = "url" }

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) { Toast.makeText(this, "Couldn't open PhoneView.", Toast.LENGTH_LONG).show(); finish(); return }

        web = WebView(this)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            builtInZoomControls = false
            displayZoomControls = false
            loadWithOverviewMode = false
            useWideViewPort = false
            userAgentString = userAgentString + " PRAGONMobile/1.0"
        }
        web.webViewClient = object : WebViewClient() {}
        web.webChromeClient = WebChromeClient()
        web.addJavascriptInterface(MobileJsBridge(), "PragonMobileBridge")

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            addView(web, FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(root)
        requestMediaPermissions()
        web.loadUrl(url)
    }

    private fun requestMediaPermissions() {
        val ask = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) ask += Manifest.permission.RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) ask += Manifest.permission.CAMERA
        if (ask.isNotEmpty()) requestPermissions(ask.toTypedArray(), 901)
    }

    private inner class MobileJsBridge {
        @JavascriptInterface fun openRemote() = runOnUiThread { web.evaluateJavascript("window.__pragonMobileOpenRemote && window.__pragonMobileOpenRemote()", null) }
        @JavascriptInterface fun openSettings() = runOnUiThread {
            startActivity(Intent(this@WebViewActivity, MainActivity::class.java).putExtra(MainActivity.EXTRA_SETTINGS, true))
        }
        @JavascriptInterface fun typeOnPc(text: String) { RemoteBridge.pcControl("type_text", value = text) }
        @JavascriptInterface fun remote(action: String, value: String?, dx: Double, dy: Double) {
            val safeValue = value?.takeIf { it.isNotEmpty() }
            if (action == "wake") RemoteBridge.wakePc() else RemoteBridge.pcControl(action, safeValue, dx, dy)
        }
        @JavascriptInterface fun closeRemote() = Unit
        @JavascriptInterface fun forgetPc() = runOnUiThread {
            RemoteBridge.forgetPc()
            ContextCompat.startForegroundService(this@WebViewActivity, Intent(this@WebViewActivity, PragonService::class.java).setAction(PragonService.ACTION_STOP))
            Prefs.clear(this@WebViewActivity)
            finish()
        }
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
