package com.anb.networkbooster

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.anb.networkbooster.network.InternetPerformanceTester
import com.anb.networkbooster.network.InternetTestResult
import com.anb.networkbooster.network.NetworkMonitor
import com.anb.networkbooster.network.SignalMonitor
import com.anb.networkbooster.network.SimSignalSnapshot
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private lateinit var networkStatusView: TextView
    private lateinit var permissionStatusView: TextView
    private lateinit var signalContainer: LinearLayout
    private lateinit var internetStatusView: TextView
    private lateinit var historyView: TextView
    private lateinit var speedTestButton: Button
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var signalMonitor: SignalMonitor
    private lateinit var internetTester: InternetPerformanceTester
    private val simViews = mutableMapOf<String, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(0xFF0B1730.toInt())
        }

        val title = TextView(this).apply {
            text = "ANB NETWORK BOOSTER"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
        }

        val subtitle = TextView(this).apply {
            text = "INTERNET INTELLIGENCE · PHASE 3"
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 20)
            setTextColor(0xFF75A9FF.toInt())
        }

        networkStatusView = sectionText("Network status: checking…")
        permissionStatusView = sectionText(
            "Signal monitoring needs Phone and precise Location permissions. " +
                "Location is used by Android to expose nearby cell information."
        )

        val permissionButton = Button(this).apply {
            text = "Grant permissions & start signal monitoring"
            setOnClickListener { requestSignalPermissions() }
        }

        val signalHeading = sectionText("LIVE SIM SIGNAL")
        signalContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val internetHeading = sectionText("INTERNET PERFORMANCE TEST")
        internetStatusView = sectionText(
            "Test runs on the currently selected mobile-data SIM. " +
                "Turn Wi-Fi off and select Vi or Airtel in Android SIM settings."
        )
        speedTestButton = Button(this).apply {
            text = "START INTERNET TEST"
            setOnClickListener { startInternetTest() }
        }
        val dataNotice = sectionText(
            "Test traffic: approx. 1 MB download + 256 KB upload. " +
                "Generated test bytes are sent to Cloudflare speed-test servers; " +
                "no personal files are uploaded. Mobile data charges may apply."
        )
        historyView = sectionText(loadHistoryText())

        root.addView(title)
        root.addView(subtitle)
        root.addView(networkStatusView)
        root.addView(signalHeading)
        root.addView(permissionStatusView)
        root.addView(permissionButton)
        root.addView(signalContainer)
        root.addView(internetHeading)
        root.addView(internetStatusView)
        root.addView(speedTestButton)
        root.addView(dataNotice)
        root.addView(sectionText("RECENT TEST HISTORY"))
        root.addView(historyView)

        setContentView(ScrollView(this).apply { addView(root) })

        networkMonitor = NetworkMonitor(this) { state ->
            runOnUiThread { networkStatusView.text = "Network status\n$state" }
        }
        signalMonitor = SignalMonitor(
            this,
            onSnapshot = { snapshot -> runOnUiThread { showSnapshot(snapshot) } },
            onMessage = { message -> runOnUiThread { permissionStatusView.text = message } }
        )
        internetTester = InternetPerformanceTester(this)

        if (signalMonitor.hasRequiredPermissions()) {
            startSignalMonitoring()
        } else {
            permissionStatusView.visibility = View.VISIBLE
        }
    }

    private fun sectionText(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(0xFFD5E4FF.toInt())
        setPadding(0, 12, 0, 12)
    }

    private fun requestSignalPermissions() {
        val missing = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()

        if (missing.isEmpty()) {
            startSignalMonitoring()
        } else {
            requestPermissions(missing, REQUEST_SIGNAL_PERMISSIONS)
        }
    }

    private fun startSignalMonitoring() {
        permissionStatusView.text =
            "Monitoring modem-reported signal. Some LTE metrics may be unavailable."
        signalMonitor.start()
    }

    private fun showSnapshot(snapshot: SimSignalSnapshot) {
        val card = simViews.getOrPut(snapshot.label) {
            TextView(this).also {
                it.textSize = 14f
                it.setTextColor(0xFFFFFFFF.toInt())
                it.setPadding(16, 18, 16, 18)
                it.setBackgroundColor(0xFF172B49.toInt())
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = 12
                signalContainer.addView(it, params)
            }
        }
        card.text = snapshot.asDisplayText()
    }

    private fun startInternetTest() {
        speedTestButton.isEnabled = false
        internetStatusView.text = "Preparing test…"
        internetTester.run(
            onStatus = { status ->
                runOnUiThread { internetStatusView.text = status }
            },
            onComplete = { result ->
                runOnUiThread {
                    speedTestButton.isEnabled = true
                    internetStatusView.text = result.toDisplayText()
                    saveResult(result)
                    historyView.text = loadHistoryText()
                }
            },
            onError = { error ->
                runOnUiThread {
                    speedTestButton.isEnabled = true
                    internetStatusView.text = "Test could not complete\n$error"
                }
            }
        )
    }

    private fun saveResult(result: InternetTestResult) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val previous = prefs.getString(KEY_HISTORY, "") ?: ""
        val timestamp = DateFormat.getDateTimeInstance().format(Date(result.completedAt))
        val line = "$timestamp | ${result.networkLabel} | " +
            "Down ${result.downloadMbps?.let { "%.2f".format(it) } ?: "N/A"} Mbps | " +
            "Up ${result.uploadMbps?.let { "%.2f".format(it) } ?: "N/A"} Mbps | " +
            "Ping ${result.pingMs?.let { "$it ms" } ?: "N/A"}"
        val updated = (listOf(line) + previous.lines().filter { it.isNotBlank() })
            .take(MAX_HISTORY)
            .joinToString("\n")
        prefs.edit().putString(KEY_HISTORY, updated).apply()
    }

    private fun loadHistoryText(): String =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_HISTORY, null)
            ?: "No internet tests saved yet."

    override fun onStart() {
        super.onStart()
        if (::networkMonitor.isInitialized) networkMonitor.start()
        if (::signalMonitor.isInitialized && signalMonitor.hasRequiredPermissions()) {
            signalMonitor.start()
        }
    }

    override fun onStop() {
        if (::signalMonitor.isInitialized) signalMonitor.stop()
        if (::networkMonitor.isInitialized) networkMonitor.stop()
        super.onStop()
    }

    @Deprecated("Deprecated Android permission callback retained for compatibility")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_SIGNAL_PERMISSIONS) {
            if (signalMonitor.hasRequiredPermissions()) {
                startSignalMonitoring()
            } else {
                permissionStatusView.text =
                    "Permissions not granted. Network connectivity still works, but cellular signal metrics are hidden."
            }
        }
    }

    companion object {
        private const val REQUEST_SIGNAL_PERMISSIONS = 210
        private const val PREFS_NAME = "anb_network_history"
        private const val KEY_HISTORY = "internet_test_history"
        private const val MAX_HISTORY = 10
    }
}
