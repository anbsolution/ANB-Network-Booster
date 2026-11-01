package com.anb.networkbooster.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellSignalStrengthLte
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import org.json.JSONArray
import org.json.JSONObject

data class SimSignalSnapshot(
    val label: String,
    val networkDbm: Int?,
    val rsrp: Int?,
    val rsrq: Int?,
    val rssnr: Int?,
    val rssi: Int?,
    val level: Int?,
    val quality: String,
    val radioQuality: String,
    val coverageScore: Int?,
    val recommendation: String,
    val historySummary: String
) {
    fun asDisplayText(): String = buildString {
        appendLine(label)
        appendLine("Coverage: $quality · Radio quality: $radioQuality")
        appendLine("Coverage score: ${coverageScore?.let { "$it / 100" } ?: "Unavailable"} (app estimate)")
        appendLine("Signal: ${networkDbm?.let { "$it dBm" } ?: "Unavailable"}")
        appendLine("LTE RSRP: ${rsrp?.let { "$it dBm" } ?: "Unavailable"}")
        appendLine("LTE RSRQ: ${rsrq?.let { "$it dB" } ?: "Unavailable"}")
        appendLine("LTE SINR/RSSNR: ${rssnr?.let { "$it dB" } ?: "Unavailable"}")
        appendLine("LTE RSSI: ${rssi?.let { "$it dBm" } ?: "Unavailable"}")
        appendLine("Signal level: ${level?.let { "$it / 4" } ?: "Unavailable"}")
        appendLine("Recommendation: $recommendation")
        append("Coverage history: $historySummary")
    }
}

/**
 * Reads modem-reported LTE signal metrics and keeps a small local history.
 * It cannot increase radio power, override carrier policy, or force a tower handover.
 */
class SignalMonitor(
    private val context: Context,
    private val onSnapshot: (SimSignalSnapshot) -> Unit,
    private val onMessage: (String) -> Unit
) {
    private data class Registration(
        val manager: TelephonyManager,
        val listener: PhoneStateListener
    )

    private val registrations = mutableListOf<Registration>()
    private val lastSavedAt = mutableMapOf<String, Long>()

    fun hasRequiredPermissions(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED &&
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    fun start() {
        if (!hasRequiredPermissions()) {
            onMessage("Grant Phone and precise Location permissions to read cellular signal.")
            return
        }

        stop()
        try {
            val baseManager = context.getSystemService(TelephonyManager::class.java)
            val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
            val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList.orEmpty()

            if (activeSubscriptions.isEmpty()) {
                registerForSubscription(baseManager, "Default SIM")
            } else {
                activeSubscriptions.forEachIndexed { index, info ->
                    val manager = baseManager.createForSubscriptionId(info.subscriptionId)
                    val carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                        ?: "SIM ${index + 1}"
                    registerForSubscription(manager, "SIM ${info.simSlotIndex + 1} · $carrier")
                }
            }

            if (registrations.isEmpty()) {
                onMessage("No active SIM signal listener is available.")
            }
        } catch (_: SecurityException) {
            onMessage("Android denied SIM information. Check permissions and retry.")
        } catch (_: Exception) {
            onMessage("Cellular signal information is unavailable on this device.")
        }
    }

    @Suppress("DEPRECATION")
    private fun registerForSubscription(manager: TelephonyManager, label: String) {
        val listener = object : PhoneStateListener() {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                val lte: CellSignalStrengthLte? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        signalStrength.cellSignalStrengths
                            .filterIsInstance<CellSignalStrengthLte>()
                            .firstOrNull()
                    } else null

                val dbmValue = lte?.dbm?.takeUnless { it == Int.MAX_VALUE }
                val rsrpValue = lte?.rsrp?.takeUnless { it == Int.MAX_VALUE }
                val rsrqValue = lte?.rsrq?.takeUnless { it == Int.MAX_VALUE }
                val rssnrValue = lte?.rssnr?.takeUnless { it == Int.MAX_VALUE }
                val rssiValue = lte?.rssi?.takeUnless { it == Int.MAX_VALUE }

                val coverage = classifyCoverage(rsrpValue ?: dbmValue)
                val radio = classifyRadioQuality(rsrqValue, rssnrValue)
                val score = calculateCoverageScore(rsrpValue ?: dbmValue, rsrqValue, rssnrValue)
                val advice = buildRecommendation(rsrpValue ?: dbmValue, rsrqValue, rssnrValue)
                val history = saveAndSummarize(label, rsrpValue ?: dbmValue, rsrqValue, rssnrValue)

                onSnapshot(
                    SimSignalSnapshot(
                        label = label,
                        networkDbm = dbmValue,
                        rsrp = rsrpValue,
                        rsrq = rsrqValue,
                        rssnr = rssnrValue,
                        rssi = rssiValue,
                        level = signalStrength.level,
                        quality = coverage,
                        radioQuality = radio,
                        coverageScore = score,
                        recommendation = advice,
                        historySummary = history
                    )
                )
            }
        }

        try {
            manager.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
            registrations.add(Registration(manager, listener))
        } catch (_: SecurityException) {
            onMessage("$label: permission denied.")
        }
    }

    private fun classifyCoverage(rsrp: Int?): String = when {
        rsrp == null -> "Unavailable"
        rsrp >= -90 -> "Excellent"
        rsrp >= -100 -> "Good"
        rsrp >= -110 -> "Fair"
        else -> "Weak"
    }

    private fun classifyRadioQuality(rsrq: Int?, sinr: Int?): String {
        if (rsrq == null && sinr == null) return "Unavailable"
        val poor = (rsrq != null && rsrq <= -15) || (sinr != null && sinr <= 0)
        val good = (rsrq == null || rsrq >= -10) && (sinr == null || sinr >= 10)
        return when {
            poor -> "Poor"
            good -> "Good"
            else -> "Moderate"
        }
    }

    private fun calculateCoverageScore(rsrp: Int?, rsrq: Int?, sinr: Int?): Int? {
        if (rsrp == null && rsrq == null && sinr == null) return null
        val signalPart = rsrp?.let { ((it + 120).coerceIn(0, 40) / 40.0) * 50.0 } ?: 25.0
        val qualityPart = rsrq?.let { ((it + 20).coerceIn(0, 15) / 15.0) * 25.0 } ?: 12.5
        val sinrPart = sinr?.let { (it.coerceIn(-10, 30) + 10) / 40.0 * 25.0 } ?: 12.5
        return (signalPart + qualityPart + sinrPart).toInt().coerceIn(0, 100)
    }

    private fun buildRecommendation(rsrp: Int?, rsrq: Int?, sinr: Int?): String = when {
        rsrp == null && rsrq == null && sinr == null ->
            "LTE measurements are unavailable; check again where cellular data is active."
        rsrp != null && rsrp < -110 ->
            "Coverage is weak. Compare readings near a window or outdoors; avoid blocking the phone."
        sinr != null && sinr <= 0 ->
            "Signal quality is low. Retest at a different time; congestion or interference may contribute."
        rsrq != null && rsrq <= -15 ->
            "Radio quality is poor. Compare another nearby location and time."
        else ->
            "Keep monitoring. Compare repeated readings; a single sample cannot confirm coverage stability."
    }

    private fun saveAndSummarize(label: String, rsrp: Int?, rsrq: Int?, sinr: Int?): String {
        val now = System.currentTimeMillis()
        val key = label.hashCode().toString()
        val prefs = context.getSharedPreferences("anb_coverage_history", Context.MODE_PRIVATE)
        val raw = prefs.getString(key, "[]") ?: "[]"
        val records = try {
            val old = JSONArray(raw)
            (0 until old.length()).mapNotNull { old.optJSONObject(it) }
        } catch (_: Exception) {
            emptyList()
        }.toMutableList()

        // Save at most one sample per SIM every 30 seconds to avoid noisy duplicate history.
        if (now - (lastSavedAt[label] ?: 0L) >= SAMPLE_INTERVAL_MS) {
            records.add(0, JSONObject().apply {
                put("time", now)
                put("rsrp", rsrp ?: JSONObject.NULL)
                put("rsrq", rsrq ?: JSONObject.NULL)
                put("sinr", sinr ?: JSONObject.NULL)
            })
            lastSavedAt[label] = now
            val array = JSONArray()
            records.take(MAX_SAMPLES).forEach { array.put(it) }
            prefs.edit().putString(key, array.toString()).apply()
        }

        val usable = records.mapNotNull { item ->
            if (item.isNull("rsrp")) null else item.optInt("rsrp")
        }
        val best = usable.maxOrNull()
        val recent = usable.firstOrNull()
        return buildString {
            append("${records.size.coerceAtMost(MAX_SAMPLES)} samples")
            if (best != null) append(" · best RSRP $best dBm")
            if (recent != null) append(" · latest RSRP $recent dBm")
        }
    }

    /** Returns a compact summary of the existing local signal history for one SIM. */
    fun getHistoryAnalytics(label: String): String {
        val key = label.hashCode().toString()
        val prefs = context.getSharedPreferences("anb_coverage_history", Context.MODE_PRIVATE)
        val raw = prefs.getString(key, "[]") ?: "[]"
        val records = try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        } catch (_: Exception) {
            emptyList()
        }
        if (records.isEmpty()) {
            return "$label\nNo saved samples yet. Keep signal monitoring active; samples are stored at most once every 30 seconds."
        }

        fun values(field: String): List<Int> = records.mapNotNull { item ->
            if (item.isNull(field) || !item.has(field)) null else item.optInt(field)
        }
        fun averageText(items: List<Int>, unit: String): String =
            items.takeIf { it.isNotEmpty() }?.average()?.let { "%.1f %s".format(java.util.Locale.US, it, unit) } ?: "Unavailable"

        val rsrp = values("rsrp")
        val rsrq = values("rsrq")
        val sinr = values("sinr")
        val oldest = records.last().optLong("time")
        val newest = records.first().optLong("time")
        val dateFormat = java.text.DateFormat.getDateTimeInstance()
        return buildString {
            appendLine(label)
            appendLine("Saved samples: ${records.size.coerceAtMost(20)} / 20 (local history)")
            appendLine("Period: ${dateFormat.format(java.util.Date(oldest))} – ${dateFormat.format(java.util.Date(newest))}")
            appendLine("RSRP average: ${averageText(rsrp, "dBm")}")
            appendLine("RSRP best / weakest: ${rsrp.maxOrNull()?.let { "$it dBm" } ?: "N/A"} / ${rsrp.minOrNull()?.let { "$it dBm" } ?: "N/A"}")
            appendLine("RSRQ average: ${averageText(rsrq, "dB")}")
            appendLine("SINR average: ${averageText(sinr, "dB")}")
            append("History is local to this device; up to 20 samples are retained per SIM.")
        }
    }

    @Suppress("DEPRECATION")
    fun stop() {
        registrations.forEach { registration ->
            try {
                registration.manager.listen(registration.listener, PhoneStateListener.LISTEN_NONE)
            } catch (_: Exception) {
                // The system may already have removed this listener.
            }
        }
        registrations.clear()
    }

    companion object {
        private const val SAMPLE_INTERVAL_MS = 30_000L
        private const val MAX_SAMPLES = 20
    }
}
