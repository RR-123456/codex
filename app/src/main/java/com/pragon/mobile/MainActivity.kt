package com.pragon.mobile

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/** PRAGON Mobile pairing/settings screen.
 *  The actual main UI is the real PRAGON PhoneView page shown by WebViewActivity.
 */
class MainActivity : AppCompatActivity() {
    private val cCyan = Color.parseColor("#00D4FF")
    private val cBg = Color.parseColor("#0A0A0F")
    private val cPanel = Color.parseColor("#12161F")
    private val cText = Color.parseColor("#DDE3ED")
    private val cDim = Color.parseColor("#5E6A7E")

    private lateinit var status: TextView
    private lateinit var hostEt: EditText
    private lateinit var keyEt: EditText
    private var launched = false

    private val scan = registerForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@registerForActivityResult
        parseQr(text)
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!intent.getBooleanExtra(EXTRA_SETTINGS, false) && Prefs.token(this).isNotBlank()) {
            ContextCompat.startForegroundService(this, Intent(this, PragonService::class.java))
            openPhoneView()
            return
        }
        buildSettingsUi()
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun buildSettingsUi() {
        val dp = resources.displayMetrics.density
        val p = (18 * dp).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(p, p, p, p)
            setBackgroundColor(cBg)
        }
        root.addView(TextView(this).apply {
            text = "PRAGON MOBILE"; textSize = 22f; setTextColor(cCyan)
            setTypeface(typeface, android.graphics.Typeface.BOLD); letterSpacing = .08f
        })
        root.addView(TextView(this).apply {
            text = "Settings"; textSize = 12f; setTextColor(cDim); letterSpacing = .18f
            setPadding(0, 0, 0, p)
        })

        status = TextView(this).apply { setTextColor(cText); textSize = 13f; setPadding(0, 0, 0, p/2) }
        root.addView(status)

        hostEt = EditText(this).apply {
            hint = "PC address, e.g. 192.168.1.2:8000"
            setTextColor(cText); setHintTextColor(cDim); setSingleLine(true)
            val h = Prefs.host(this@MainActivity)
            if (h.isNotBlank()) setText("$h:${Prefs.port(this@MainActivity)}")
        }
        keyEt = EditText(this).apply {
            hint = "Pairing key"
            setTextColor(cText); setHintTextColor(cDim); setSingleLine(true)
        }
        root.addView(hostEt, fieldLp(dp))
        root.addView(keyEt, fieldLp(dp))

        root.addView(action("Scan QR") {
            scan.launch(ScanOptions().setPrompt("Scan the QR shown in Pragon PhoneView").setBeepEnabled(false).setOrientationLocked(false))
        }, actionLp(dp))
        root.addView(action("Connect manually") { connectManual() }, actionLp(dp))

        root.addView(TextView(this).apply {
            text = "Permissions"; textSize = 14f; setTextColor(cCyan); setPadding(0, p, 0, p/2)
        })
        val perms = TextView(this).apply { setTextColor(cDim); textSize = 12f }
        root.addView(perms)

        root.addView(action("Enable accessibility service") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, actionLp(dp))
        root.addView(action("Allow display over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }, actionLp(dp))
        root.addView(action("Battery: don't restrict this app") {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }, actionLp(dp))

        root.addView(TextView(this).apply {
            text = ""; minHeight = p
        })
        root.addView(action("Disconnect and forget this PC") { forgetPc() }, actionLp(dp))
        setContentView(ScrollView(this).apply { setBackgroundColor(cBg); addView(root) })

        status.text = if (Prefs.token(this).isBlank()) "Not paired" else "Connected PC: ${Prefs.host(this)}:${Prefs.port(this)}"
        refreshPerms(perms)
    }

    private fun fieldLp(dp: Float) = LinearLayout.LayoutParams(-1, (52 * dp).toInt()).apply { bottomMargin = (8 * dp).toInt() }
    private fun actionLp(dp: Float) = LinearLayout.LayoutParams(-1, (48 * dp).toInt()).apply { topMargin = (5 * dp).toInt(); bottomMargin = (5 * dp).toInt() }

    private fun action(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; textSize = 12f; setTextColor(cCyan)
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 18f; setColor(cPanel); setStroke(1, Color.parseColor("#3300D4FF"))
        }
        setOnClickListener { onClick() }
    }

    private fun refreshPerms(tv: TextView) {
        val a11y = PragonAccessibilityService.instance != null
        val overlay = Settings.canDrawOverlays(this)
        val pm = getSystemService(PowerManager::class.java)
        val batt = pm.isIgnoringBatteryOptimizations(packageName)
        tv.text = "Accessibility: ${onOff(a11y)}\nDisplay over other apps: ${onOff(overlay)}\nBattery unrestricted: ${onOff(batt)}"
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized && Prefs.token(this).isNotBlank()) status.text = "Connected PC: ${Prefs.host(this)}:${Prefs.port(this)}"
    }

    private fun onOff(v: Boolean) = if (v) "ON" else "OFF"

    private fun parseQr(raw: String) {
        try {
            val uri = Uri.parse(raw.trim())
            val host = uri.host ?: ""
            val port = if (uri.port > 0) uri.port else 8000
            val key = uri.getQueryParameter("key").orEmpty()
            if (host.isBlank() || key.isBlank()) throw IllegalArgumentException()
            startPairing(host, port, key)
        } catch (_: Exception) {
            toast("That QR code isn't a Pragon pairing QR.")
        }
    }

    private fun connectManual() {
        val raw = hostEt.text.toString().trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        val parts = raw.split(":", limit = 2)
        val host = parts.getOrNull(0).orEmpty()
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 8000
        val key = keyEt.text.toString().trim()
        if (host.isBlank() || key.isBlank()) toast("Enter the PC address and pairing key.")
        else startPairing(host, port, key)
    }

    private fun startPairing(host: String, port: Int, key: String) {
        Prefs.save(this, host, port, "")
        ContextCompat.startForegroundService(this, Intent(this, PragonService::class.java).putExtra(PragonService.EXTRA_KEY, key.uppercase()))
        status.text = "Pairing with $host:$port …"
        Toast.makeText(this, "Connecting to Pragon…", Toast.LENGTH_SHORT).show()
        window.decorView.postDelayed({ waitForPairing() }, 500)
    }

    private fun waitForPairing() {
        if (launched || isFinishing) return
        if (Prefs.token(this).isNotBlank()) { openPhoneView(); return }
        window.decorView.postDelayed({ waitForPairing() }, 700)
    }

    private fun openPhoneView() {
        if (launched || Prefs.token(this).isBlank()) return
        launched = true
        val host = Prefs.host(this)
        val port = Prefs.port(this)
        val url = "http://$host:$port/mobile-login?token=${Uri.encode(Prefs.token(this))}"
        startActivity(Intent(this, WebViewActivity::class.java).putExtra(WebViewActivity.EXTRA_URL, url))
        finish()
    }

    private fun forgetPc() {
        RemoteBridge.forgetPc()
        ContextCompat.startForegroundService(this, Intent(this, PragonService::class.java).setAction(PragonService.ACTION_STOP))
        Prefs.clear(this)
        toast("Disconnected and forgot this PC.")
        finishAffinity()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    companion object {
        const val EXTRA_SETTINGS = "open_settings"
    }
}
