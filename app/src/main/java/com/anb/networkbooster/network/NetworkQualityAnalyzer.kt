package com.anb.networkbooster.network

import java.util.Locale
import kotlin.math.roundToInt

data class NetworkQualityReport(
    val score: Int?,
    val grade: String,
    val headline: String,
    val diagnostics: List<String>,
    val recommendations: List<String>,
    val dataNote: String
) {
    fun toDisplayText(): String = buildString {
        appendLine("NETWORK QUALITY INTELLIGENCE")
        appendLine("Quality score: ${score?.let { "$it / 100" } ?: "Unavailable"}")
        appendLine("Grade: $grade")
        appendLine(headline)

        if (diagnostics.isNotEmpty()) {
            appendLine()
            appendLine("DIAGNOSTICS")
            diagnostics.forEach { appendLine("• $it") }
        }

        if (recommendations.isNotEmpty()) {
            appendLine()
            appendLine("RECOMMENDATIONS")
            recommendations.forEach { appendLine("• $it") }
        }

        appendLine()
        append(dataNote)
    }
}

/**
 * Combines the latest available cellular signal and internet test metrics.
 * The score is an app estimate, not a carrier metric.
 * A single test cannot establish statistical correlation or prove congestion.
 */
object NetworkQualityAnalyzer {

    fun analyze(
        signal: SimSignalSnapshot?,
        speed: InternetTestResult?
    ): NetworkQualityReport {
        val components = mutableListOf<Pair<Int, Int>>()

        signal?.rsrp?.let {
            components += rsrpScore(it) to 30
        }
        signal?.rsrq?.let {
            components += rsrqScore(it) to 15
        }
        signal?.rssnr?.let {
            components += sinrScore(it) to 15
        }
        speed?.pingMs?.let {
            components += latencyScore(it) to 15
        }
        speed?.jitterMs?.let {
            components += jitterScore(it) to 10
        }
        speed?.downloadMbps?.let {
            components += downloadScore(it) to 10
        }
        speed?.uploadMbps?.let {
            components += uploadScore(it) to 5
        }

        val totalWeight = components.sumOf { it.second }
        val score = if (totalWeight > 0) {
            (components.sumOf { it.first * it.second }.toDouble() /
                totalWeight).roundToInt().coerceIn(0, 100)
        } else null

        val grade = when {
            score == null -> "Insufficient data"
            score >= 85 -> "Excellent"
            score >= 70 -> "Good"
            score >= 50 -> "Fair"
            score >= 30 -> "Poor"
            else -> "Very poor"
        }

        val diagnostics = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        val weakSignal = signal?.rsrp?.let { it <= -110 } == true
        val poorRadio = signal?.let {
            (it.rsrq != null && it.rsrq <= -13) ||
                (it.rssnr != null && it.rssnr < 5)
        } == true
        val slowDownload = speed?.downloadMbps?.let { it < 5.0 } == true
        val highLatency = speed?.pingMs?.let { it >= 150 } == true
        val highJitter = speed?.jitterMs?.let { it >= 40 } == true

        if (weakSignal && slowDownload) {
            diagnostics +=
                "Weak LTE signal and low download speed were observed together. Signal may be a contributing factor."
            recommendations +=
                "Compare readings at another safe location with better reception."
        } else if (weakSignal) {
            diagnostics += "LTE RSRP is weak in the available signal sample."
            recommendations +=
                "Compare signal readings at different locations and times."
        }

        if (poorRadio) {
            diagnostics +=
                "RSRQ or SINR indicates possible radio interference or a busy radio environment."
            recommendations +=
                "Repeat measurements at another time to compare radio quality."
        }

        if (slowDownload && !weakSignal && !poorRadio) {
            diagnostics +=
                "Download speed is low despite no clear weak-signal indication. Congestion, backhaul, server or device conditions may contribute; this test cannot isolate the cause."
            recommendations +=
                "Repeat the speed test later using the same test conditions."
        }

        if (highLatency) {
            diagnostics += "TCP latency is high in this test."
            recommendations +=
                "Avoid latency-sensitive activity while the connection is unstable."
        }

        if (highJitter) {
            diagnostics += "Latency variation (jitter) is high."
            recommendations +=
                "Repeat the test to check whether the variation is persistent."
        }

        if (speed == null) {
            diagnostics += "No internet performance result is available yet."
        }
        if (signal == null) {
            diagnostics += "No cellular signal snapshot is available yet."
        }

        if (diagnostics.isEmpty() && score != null) {
            diagnostics +=
                "No major issue was identified in the available measurements."
            recommendations +=
                "Continue comparing results across different times."
        }

        val dataNote = when {
            signal != null && speed != null ->
                "Based on the latest available signal snapshot and speed test. " +
                    "This is an estimate, not a guaranteed diagnosis."
            else ->
                "Partial data only. Run signal monitoring and an internet test " +
                    "to improve the estimate."
        }

        return NetworkQualityReport(
            score = score,
            grade = grade,
            headline = score?.let {
                "Combined network quality estimate: $it / 100"
            } ?: "Not enough measurements to calculate a score.",
            diagnostics = diagnostics.distinct(),
            recommendations = recommendations.distinct(),
            dataNote = dataNote
        )
    }

    private fun rsrpScore(value: Int): Int = when {
        value >= -85 -> 100
        value >= -95 -> 85
        value >= -105 -> 65
        value >= -115 -> 40
        else -> 20
    }

    private fun rsrqScore(value: Int): Int = when {
        value >= -5 -> 100
        value >= -9 -> 80
        value >= -13 -> 55
        value >= -17 -> 30
        else -> 15
    }

    private fun sinrScore(value: Int): Int = when {
        value >= 20 -> 100
        value >= 13 -> 85
        value >= 5 -> 65
        value >= 0 -> 45
        else -> 20
    }

    private fun latencyScore(value: Long): Int = when {
        value <= 40 -> 100
        value <= 80 -> 80
        value <= 150 -> 60
        value <= 250 -> 35
        else -> 15
    }

    private fun jitterScore(value: Long): Int = when {
        value <= 10 -> 100
        value <= 20 -> 80
        value <= 40 -> 60
        value <= 80 -> 35
        else -> 15
    }

    private fun downloadScore(value: Double): Int = when {
        value >= 50.0 -> 100
        value >= 20.0 -> 85
        value >= 5.0 -> 65
        value >= 1.0 -> 40
        else -> 20
    }

    private fun uploadScore(value: Double): Int = when {
        value >= 20.0 -> 100
        value >= 10.0 -> 85
        value >= 3.0 -> 65
        value >= 1.0 -> 40
        else -> 20
    }
}
