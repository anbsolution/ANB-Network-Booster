package com.anb.networkbooster

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private lateinit var analyzerView: TextView
    private lateinit var recommendationsView: TextView
    private lateinit var trendsView: TextView
    private lateinit var speedTestButton: Button
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var signalMonitor: SignalMonitor
    private lateinit var internetTester: InternetPerformanceTester
    private val simViews = mutableMapOf<String, TextView>()
    private var latestAirtelSignal: SimSignalSnapshot? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var batchRunning = false
    private val batchResults = mutableListOf<InternetTestResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(28))
            setBackgroundColor(0xFF081426.toInt())
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(22), dp(16), dp(22))
            background = panelBackground(0xFF102747.toInt(), 0xFF24466F.toInt(), 22)
        }
        val title = TextView(this).apply {
            text = "ANB NETWORK BOOSTER"
            textSize = 22f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
        }
        val subtitle = TextView(this).apply {
            text = "NETWORK INTELLIGENCE  ·  PHASE 5"
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, 0)
            setTextColor(0xFF8DBBFF.toInt())
        }
        header.addView(title)
        header.addView(subtitle)
        root.addView(header, matchWrap().apply { bottomMargin = dp(18) })

        networkStatusView = sectionText("Network status: checking…")
        permissionStatusView = sectionText(
            "Signal monitoring needs Phone and precise Location permissions. " +
                "Location is used by Android to expose nearby cell information."
        )
        val permissionButton = actionButton("Grant permissions & start monitoring") {
            requestSignalPermissions()
        }

        root.addView(sectionHeading("NETWORK CONNECTION"))
        root.addView(networkStatusView)
        root.addView(permissionStatusView)
        root.addView(permissionButton, matchWrap().apply {
            topMargin = dp(4)
            bottomMargin = dp(18)
        })

        root.addView(sectionHeading("LIVE SIM SIGNAL"))
        signalContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(signalContainer, matchWrap().apply { bottomMargin = dp(10) })

        root.addView(sectionHeading("AIRTEL NETWORK INTELLIGENCE"))
        intelligenceView = sectionText(loadIntelligenceText())
        alertView = sectionText("Network alerts will appear here after signal and speed measurements.")
        root.addView(intelligenceView)
        root.addView(alertView)

        root.addView(sectionHeading("NETWORK DIAGNOSTIC ANALYZER"))
        analyzerView = sectionText("Waiting for available signal and speed measurements.")
        root.addView(analyzerView)

        root.addView(sectionHeading("SMART OPTIMIZATION RECOMMENDATIONS"))
        recommendationsView = sectionText("Recommendations will appear when measurements are available.")
        root.addView(recommendationsView)

        root.addView(sectionHeading("PERFORMANCE DASHBOARD"))
        trendsView = sectionText(buildTrendDashboard())
        root.addView(trendsView)

        root.addView(sectionHeading("SMART SPEED ENGINE · 3 TESTS"))
        internetStatusView = sectionText(
            "Keep Airtel selected as the default mobile-data SIM and turn Wi-Fi off. " +
                "The smart check runs 3 measurements and calculates their averages."
        )
        root.addView(internetStatusView)
        speedTestButton = actionButton("Run 3-test smart speed check") {
            startInternetTestBatch()
        }
        root.addView(speedTestButton, matchWrap().apply {
            topMargin = dp(5)
            bottomMargin = dp(10)
        })
        val dataNotice = captionText(
            "Each test uses about 1 MB download + 256 KB upload. A 3-test check uses about 3.75 MB total. " +
                "Generated test bytes are sent to Cloudflare speed-test servers; no personal files are uploaded. " +
                "Mobile data charges may apply."
        )
        root.addView(dataNotice)

        root.addView(sectionHeading("RECENT TEST HISTORY"))
        historyView = sectionText(loadHistoryText())
        root.addView(historyView)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 0, 0, dp(8))
            isVerticalScrollBarEnabled = true
            addView(root)
            setBackgroundColor(0xFF081426.toInt())
        }
        setContentView(scroll)

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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun panelBackground(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun sectionHeading(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        letterSpacing = 0.045f
        setTextColor(0xFF82B6FF.toInt())
        setPadding(dp(3), dp(18), dp(3), dp(9))
    }

    private fun sectionText(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(0xFFE4EDFA.toInt())
        setLineSpacing(dp(3).toFloat(), 1.04f)
        setPadding(dp(15), dp(14), dp(15), dp(14))
        background = panelBackground(0xFF10213A.toInt(), 0xFF1E385A.toInt(), 15)
        val params = matchWrap()
        params.bottomMargin = dp(10)
        layoutParams = params
    }

    private fun captionText(value: String) = TextView(this).apply {
        text = value
        textSize = 12f
        setTextColor(0xFF9FB2CB.toInt())
        setLineSpacing(dp(2).toFloat(), 1.0f)
        setPadding(dp(4), dp(5), dp(4), dp(12))
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setTextColor(0xFFFFFFFF.toInt())
        setPadding(dp(14), dp(11), dp(14), dp(11))
        minHeight = dp(50)
        background = panelBackground(0xFF1769D2.toInt(), 0xFF4B98FF.toInt(), 14)
        setOnClickListener { action() }
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
                it.setLineSpacing(dp(3).toFloat(), 1.03f)
                it.setPadding(dp(15), dp(15), dp(15), dp(15))
                it.background = panelBackground(0xFF132B49.toInt(), 0xFF31547C.toInt(), 16)
                val params = matchWrap()
                params.bottomMargin = dp(11)
                signalContainer.addView(it, params)
            }
        }
        card.text = snapshot.asDisplayText()

        if (snapshot.label.contains("airtel", ignoreCase = true)) {
            latestAirtelSignal = snapshot
            refreshIntelligence()
        }
    }

    private fun startInternetTestBatch() {
        if (batchRunning) return
        batchRunning = true
        batchResults.clear()
        speedTestButton.isEnabled = false
        speedTestButton.alpha = 0.55f
        runBatchTest(1)
    }

    private fun runBatchTest(testNumber: Int) {
        internetStatusView.text = "Smart speed check: test $testNumber of $BATCH_TESTS…"
        internetTester.run(
            onStatus = { status ->
                runOnUiThread {
                    internetStatusView.text = "Test $testNumber/$BATCH_TESTS · $status"
                }
            },
            onComplete = { result ->
                runOnUiThread {
                    batchResults.add(result)
                    saveResult(result)
                    historyView.text = loadHistoryText()
                    refreshIntelligence()
                    if (testNumber < BATCH_TESTS) {
                        internetStatusView.text =
                            "Test $testNumber complete. Preparing test ${testNumber + 1}/$BATCH_TESTS…"
                        mainHandler.postDelayed({ runBatchTest(testNumber + 1) }, NEXT_TEST_DELAY_MS)
                    } else {
                        finishBatch()
                    }
                }
            },
            onError = { error ->
                runOnUiThread {
                    batchRunning = false
                    speedTestButton.isEnabled = true
                    speedTestButton.alpha = 1f
                    internetStatusView.text =
                        "Smart speed check stopped at test $testNumber/$BATCH_TESTS.\n$error\nCompleted tests: ${batchResults.size}"
                    refreshIntelligence()
                }
            }
        )
    }

    private fun finishBatch() {
        batchRunning = false
        speedTestButton.isEnabled = true
        speedTestButton.alpha = 1f
        val down = batchResults.mapNotNull { it.downloadMbps }
        val up = batchResults.mapNotNull { it.uploadMbps }
        val ping = batchResults.mapNotNull { it.pingMs }
        val jitter = batchResults.mapNotNull { it.jitterMs }
        internetStatusView.text = buildString {
            appendLine("SMART SPEED CHECK COMPLETE · ${batchResults.size}/$BATCH_TESTS tests")
            appendLine("Network: ${batchResults.lastOrNull()?.networkLabel ?: "Mobile data"}")
            appendLine("Average download: ${averageText(down)}")
            appendLine("Average upload: ${averageText(up)}")
            appendLine("Average TCP latency: ${ping.takeIf { it.isNotEmpty() }?.average()?.let { "%.0f ms".format(Locale.US, it) } ?: "N/A"}")
            appendLine("Average jitter: ${jitter.takeIf { it.isNotEmpty() }?.average()?.let { "%.0f ms".format(Locale.US, it) } ?: "N/A"}")
            appendLine("Individual tests saved to local history.")
            append("Note: results vary with cell load, radio conditions and server path.")
        }
        refreshIntelligence()
    }

    private fun averageText(values: List<Double>): String =
        values.takeIf { it.isNotEmpty() }?.average()
            ?.let { "%.2f Mbps".format(Locale.US, it) } ?: "N/A"

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
            (0 until array.length()).mapNotNull { index -> array.optJSONObject(index) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadHistoryText(): String =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_HISTORY, null) ?: "No internet tests saved yet."

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
        if (::analyzerView.isInitialized) analyzerView.text = buildNetworkDiagnosis()
        if (::recommendationsView.isInitialized) recommendationsView.text = buildSmartRecommendations()
        if (::trendsView.isInitialized) trendsView.text = buildTrendDashboard()
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

    private fun buildNetworkDiagnosis(): String {
        val signal = latestAirtelSignal
        val latest = loadRecords().firstOrNull()
        val findings = mutableListOf<String>()
        var available = 0

        if (signal != null) {
            available++
            if (signal.quality == "Weak" || (signal.rsrp ?: 0) <= -110) {
                findings.add("Coverage: weak LTE signal is a likely contributor.")
            } else if (signal.quality == "Fair") {
                findings.add("Coverage: fair signal; indoor attenuation or distance may limit performance.")
            } else {
                findings.add("Coverage: ${signal.quality.lowercase(Locale.US)} based on modem signal readings.")
            }
            if (signal.radioQuality == "Poor") {
                findings.add("Radio quality: RSRQ/SINR indicates poor radio conditions; interference or cell load may contribute.")
            } else if (signal.radioQuality == "Moderate") {
                findings.add("Radio quality: moderate; compare repeated readings at another time.")
            } else if (signal.radioQuality == "Good") {
                findings.add("Radio quality: good on the latest available sample.")
            }
        }

        if (latest != null) {
            available++
            val down = numberOrNull(latest, "down")
            val up = numberOrNull(latest, "up")
            val ping = numberOrNull(latest, "ping")
            val jitter = numberOrNull(latest, "jitter")
            val failures = latest.optInt("failures", 0)
            val probes = latest.optInt("probes", 5).coerceAtLeast(1)
            if (down != null) {
                findings.add(
                    if (down < 5.0) "Throughput: download is currently low (${formatMbps(down)}); this can reflect radio conditions, congestion, or server path."
                    else "Throughput: latest download measured ${formatMbps(down)}."
                )
            }
            if (up != null && up < 0.5) findings.add("Upload: low at ${formatMbps(up)}; repeat at another time to compare.")
            if (ping != null && ping > 150) findings.add("Responsiveness: TCP latency is high (${ping.toInt()} ms).")
            if (jitter != null && jitter > 30) findings.add("Consistency: jitter is elevated (${jitter.toInt()} ms).")
            if (failures > 0) findings.add("Probe reliability: $failures of $probes TCP connection probes failed.")
        }

        val confidence = when (available) {
            2 -> "Medium · signal + speed data available"
            1 -> "Low · only one measurement group available"
            else -> "Insufficient · waiting for measurements"
        }
        return buildString {
            appendLine("RULE-BASED DIAGNOSTIC · NOT CLOUD AI")
            appendLine("Confidence: $confidence")
            if (findings.isEmpty()) appendLine("No diagnosis yet. Wait for a signal sample and run the speed check.")
            else findings.forEach { appendLine("• $it") }
            append("Next step: compare 2–3 readings at different times. One sample cannot prove tower congestion.")
        }
    }

    private fun buildSmartRecommendations(): String {
        val signal = latestAirtelSignal
        val latest = loadRecords().firstOrNull()
        val suggestions = mutableListOf<String>()
        var evidenceCount = 0

        if (signal != null) {
            evidenceCount++
            val rsrp = signal.rsrp ?: signal.networkDbm
            val rsrq = signal.rsrq
            val sinr = signal.rssnr
            if (rsrp != null && rsrp <= -110) {
                suggestions.add("Coverage: compare signal near a window and outdoors. Keep the phone unobstructed; an app cannot increase tower coverage.")
            } else if (rsrp != null && rsrp <= -100) {
                suggestions.add("Coverage: compare nearby spots and keep the position with consistently stronger RSRP.")
            }
            if ((sinr != null && sinr <= 0) || (rsrq != null && rsrq <= -15)) {
                suggestions.add("Radio quality: repeat measurements later and at another location. Poor RSRQ/SINR can reflect interference or cell load.")
            }
        }

        if (latest != null) {
            evidenceCount++
            val down = numberOrNull(latest, "down")
            val up = numberOrNull(latest, "up")
            val ping = numberOrNull(latest, "ping")
            val jitter = numberOrNull(latest, "jitter")
            val failures = latest.optInt("failures", 0)
            if (down != null && down < 5.0) suggestions.add("Download: run another 3-test check later and compare at the same location.")
            if (up != null && up < 0.5) suggestions.add("Upload: pause large background uploads during the next measurement, then retest.")
            if (ping != null && ping > 150) suggestions.add("Latency: compare at another time and avoid heavy simultaneous downloads while testing.")
            if (jitter != null && jitter > 30) suggestions.add("Jitter: repeat the test when the connection is otherwise idle.")
            if (failures > 0) suggestions.add("Connection reliability: retry once. Failed TCP probes are not ICMP packet loss.")
            if (down != null && down >= 5.0 && (ping == null || ping <= 150.0) &&
                (jitter == null || jitter <= 30.0) && failures == 0) {
                suggestions.add("Current test: no major speed-test warning detected. Continue monitoring.")
            }
        }

        if (suggestions.isEmpty()) suggestions.add("Collect a live LTE signal sample and run the 3-test Smart Speed Check.")
        val confidence = when (evidenceCount) {
            2 -> "Recommendation confidence: Medium · signal + speed measurements"
            1 -> "Recommendation confidence: Low · one measurement group only"
            else -> "Recommendation confidence: Not available · waiting for measurements"
        }
        return buildString {
            appendLine(confidence)
            suggestions.distinct().forEachIndexed { index, item -> appendLine("${index + 1}. $item") }
            append("Suggestions are diagnostic only; no modem, band, carrier or tower settings are changed.")
        }
    }

    private fun buildTrendDashboard(): String {
        val records = loadRecords().take(5).reversed()
        if (records.isEmpty()) {
            return "No performance history yet.\nRun the 3-test Smart Speed Check to create trend data."
        }
        fun values(key: String): List<Double> = records.mapNotNull { numberOrNull(it, key) }
        fun sparkline(items: List<Double>): String {
            if (items.isEmpty()) return "No data"
            val glyphs = "▁▂▃▄▅▆▇█"
            val minValue = items.minOrNull() ?: 0.0
            val maxValue = items.maxOrNull() ?: minValue
            if (maxValue == minValue) return items.joinToString(" ") { "▄" }
            return items.joinToString(" ") { value ->
                val index = (((value - minValue) / (maxValue - minValue)) * (glyphs.length - 1))
                    .toInt().coerceIn(0, glyphs.length - 1)
                glyphs[index].toString()
            }
        }
        val down = values("down")
        val up = values("up")
        val ping = values("ping")
        val jitter = values("jitter")
        return buildString {
            appendLine("Last ${records.size} saved test record(s) · oldest → newest")
            appendLine("Download: ${sparkline(down)}")
            appendLine("  ${down.lastOrNull()?.let { formatMbps(it) } ?: "N/A"} latest")
            appendLine("Upload:   ${sparkline(up)}")
            appendLine("  ${up.lastOrNull()?.let { formatMbps(it) } ?: "N/A"} latest")
            appendLine("TCP ping: ${sparkline(ping)}")
            appendLine("  ${ping.lastOrNull()?.let { "%.0f ms".format(Locale.US, it) } ?: "N/A"} latest")
            appendLine("Jitter:   ${sparkline(jitter)}")
            appendLine("  ${jitter.lastOrNull()?.let { "%.0f ms".format(Locale.US, it) } ?: "N/A"} latest")
            append("Bars are relative within each metric's displayed history, not a shared scale.")
        }
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
        private const val BATCH_TESTS = 3
        private const val NEXT_TEST_DELAY_MS = 1200L
    }
}
