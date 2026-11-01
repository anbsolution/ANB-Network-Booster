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
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {
    private lateinit var networkStatusView: TextView
    private lateinit var permissionStatusView: TextView
    private lateinit var signalContainer: LinearLayout
    private lateinit var internetStatusView: TextView
    private lateinit var historyView: TextView
    private lateinit var intelligenceView: TextView
    private lateinit var alertView: TextView
    private lateinit var speedTestButton: Button
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var signalMonitor: SignalMonitor
    private lateinit var internetTester: InternetPerformanceTester
    private val simViews = mutableMapOf<String, TextView>()
    private var latestAirtelSignal: SimSignalSnapshot? = null

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
            text = "AIRTEL NETWORK INTELLIGENCE · PHASE 4"
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

        val intelligenceHeading = sectionText("AIRTEL NETWORK INTELLIGENCE")
        intelligenceView = sectionText(loadIntelligenceText())
        alertView = sectionText("Network alerts will appear here after signal and speed measurements.")

        val internetHeading = sectionText("INTERNET PERFORMANCE TEST")
        internetStatusView = sectionText(
            "Keep Airtel selected as the default mobile-data SIM and turn Wi-Fi off before testing."
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
        root.addView(intelligenceHeading)
        root.addView(intelligenceView)
        root.addView(alertView)
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
        refreshIntelligence()
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

        if (snapshot.label.contains("airtel", ignoreCase = true)) {
            latestAirtelSignal = snapshot
            refreshIntelligence()
        }
    }

    private fun startInternetTest() {
        speedTestButton.isEnabled = false
        internetStatusView.text = "Preparing Airtel mobile-data test…"
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
                    refreshIntelligence()
                }
            },
            onError = { error ->
                runOnUiThread {
                    speedTestButton.isEnabled = true
                    internetStatusView.text = "Test could not complete\n$error"
                    refreshIntelligence()
                }
            }
        )
    }

    private fun saveResult(result: InternetTestResult) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val previous = prefs.getString(KEY_HISTORY, "") ?: ""
        val timestamp = DateFormat.getDateTimeInstance().format(Date(result.completedAt))
        val line = "$timestamp | ${result.networkLabel} | " +
            "Down ${result.downloadMbps?.let { "%.2f".format(Locale.US, it) } ?: "N/A"} Mbps | " +
            "Up ${result.uploadMbps?.let { "%.2f".format(Locale.US, it) } ?: "N/A"} Mbps | " +
            "Ping ${result.pingMs?.let { "$it ms" } ?: "N/A"}"
        val updated = (listOf(line) + previous.lines().filter { it.isNotBlank() })
            .take(MAX_HISTORY)
            .joinToString("\n")
        prefs.edit().putString(KEY_HISTORY, updated).apply()

        val records = loadRecords().toMutableList()
        records.add(0, JSONObject().apply {
            put("time", result.completedAt)
            put("network", result.networkLabel)
            put("dns", result.dnsMs ?: JSONObject.NULL)
            put("ping", result.pingMs ?: JSONObject.NULL)
            put("jitter", result.jitterMs ?: JSONObject.NULL)
            put("failures", result.probeFailures)
            put("probes", result.probeCount)
            put("down", result.downloadMbps ?: JSONObject.NULL)
            put("up", result.uploadMbps ?: JSONObject.NULL)
        })
        val array = JSONArray()
        records.take(MAX_HISTORY).forEach { array.put(it) }
        prefs.edit().putString(KEY_STRUCTURED_HISTORY, array.toString()).apply()
    }

    private fun loadRecords(): List<JSONObject> {
        val raw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_STRUCTURED_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadHistoryText(): String =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_HISTORY, null)
            ?: "No internet tests saved yet."

    private fun loadIntelligenceText(): String {
        val records = loadRecords()
        if (records.isEmpty()) {
            return "Airtel dashboard ready.\nRun 3 or more Airtel tests to build averages and a stability trend.\nKeep Airtel as the default mobile-data SIM."
        }

        val latest = records.first()
        val lastThree = records.take(3)
        val downValues = lastThree.mapNotNull { numberOrNull(it, "down") }
        val upValues = lastThree.mapNotNull { numberOrNull(it, "up") }
        val avgDown = downValues.takeIf { it.isNotEmpty() }?.average()
        val avgUp = upValues.takeIf { it.isNotEmpty() }?.average()
        val score = calculateStabilityScore(latest)
        val label = when {
            score >= 80 -> "Very stable"
            score >= 65 -> "Good"
            score >= 45 -> "Fair"
            else -> "Needs attention"
        }

        return buildString {
            appendLine("Latest network: ${latest.optString("network", "Mobile data")}")
            appendLine("NETWORK STABILITY SCORE: $score / 100 · $label")
            appendLine("Latest download: ${formatMbps(numberOrNull(latest, "down"))}")
            appendLine("Latest upload: ${formatMbps(numberOrNull(latest, "up"))}")
            appendLine("TCP latency: ${formatMs(latest, "ping")}")
            appendLine("Jitter: ${formatMs(latest, "jitter")}")
            appendLine("Last ${lastThree.size} test average download: ${avgDown?.let { "%.2f Mbps".format(Locale.US, it) } ?: "N/A"}")
            appendLine("Last ${lastThree.size} test average upload: ${avgUp?.let { "%.2f Mbps".format(Locale.US, it) } ?: "N/A"}")
            append("History: ${records.size} saved structured test(s)")
        }
    }

    private fun refreshIntelligence() {
        if (!::intelligenceView.isInitialized) return
        intelligenceView.text = loadIntelligenceText()
        alertView.text = buildAlerts()
    }

    private fun buildAlerts(): String {
        val alerts = mutableListOf<String>()
        val signal = latestAirtelSignal
        if (signal != null) {
            signal.rsrp?.let {
                if (it <= -110) alerts.add("Airtel signal is weak (RSRP $it dBm). Try testing near a window or in a different location.")
            }
            signal.rssnr?.let {
                if (it <= 0) alerts.add("Airtel SINR is low ($it dB); radio interference or cell load may affect speed.")
            }
            signal.rsrq?.let {
                if (it <= -15) alerts.add("Airtel RSRQ is weak ($it dB); radio quality may be affected.")
            }
        }

        val latest = loadRecords().firstOrNull()
        if (latest != null) {
            numberOrNull(latest, "up")?.let {
                if (it < 0.5) alerts.add("Upload speed is low (${formatMbps(it)}). Repeat the test and compare at another time.")
            }
            numberOrNull(latest, "ping")?.let {
                if (it > 150) alerts.add("TCP latency is high (${it} ms).")
            }
            numberOrNull(latest, "jitter")?.let {
                if (it > 30) alerts.add("Jitter is high (${it} ms); real-time calls may feel less stable.")
            }
        }
        if (alerts.isEmpty()) return "NETWORK ALERTS\nNo current warning from the latest available measurements. Continue monitoring."
        return "NETWORK ALERTS\n• " + alerts.joinToString("\n• ")
    }

    private fun calculateStabilityScore(record: JSONObject): Int {
        val down = numberOrNull(record, "down") ?: 0.0
        val up = numberOrNull(record, "up") ?: 0.0
        val ping = numberOrNull(record, "ping") ?: 250.0
        val jitter = numberOrNull(record, "jitter") ?: 100.0
        val probes = record.optInt("probes", 5).coerceAtLeast(1)
        val failures = record.optInt("failures", probes).coerceIn(0, probes)

        val downloadScore = min(30.0, down / 10.0 * 30.0)
        val uploadScore = min(20.0, up / 3.0 * 20.0)
        val latencyScore = max(0.0, 20.0 - max(0.0, ping - 50.0) * 0.2)
        val jitterScore = max(0.0, 15.0 - jitter * 0.3)
        val reliabilityScore = 15.0 * (1.0 - failures.toDouble() / probes)
        return (downloadScore + uploadScore + latencyScore + jitterScore + reliabilityScore)
            .toInt().coerceIn(0, 100)
    }

    private fun numberOrNull(obj: JSONObject, key: String): Double? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = obj.optDouble(key, Double.NaN)
        return value.takeIf { !it.isNaN() && it.isFinite() }
    }

    private fun formatMbps(value: Double?): String =
        value?.let { "%.2f Mbps".format(Locale.US, it) } ?: "N/A"

    private fun formatMs(obj: JSONObject, key: String): String =
        if (obj.has(key) && !obj.isNull(key)) "${obj.optLong(key)} ms" else "N/A"

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
        private const val KEY_STRUCTURED_HISTORY = "internet_test_history_structured"
        private const val MAX_HISTORY = 10
    }
}
