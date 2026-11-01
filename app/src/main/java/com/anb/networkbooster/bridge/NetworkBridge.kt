package com.anb.networkbooster.bridge

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.anb.networkbooster.network.InternetPerformanceTester
import com.anb.networkbooster.network.InternetTestResult
import com.anb.networkbooster.network.SignalMonitor
import org.json.JSONObject

class NetworkBridge(
    private val webView: WebView,
    private val signalMonitor: SignalMonitor,
    private val internetTester: InternetPerformanceTester,
    private val snapshotProvider: () -> String
) {

    @JavascriptInterface
    fun getNetworkSnapshot(): String {
        return try {
            val snapshot = snapshotProvider()
            JSONObject(snapshot).toString()
        } catch (error: Exception) {
            JSONObject()
                .put("error", "Network snapshot unavailable")
                .toString()
        }
    }

    @JavascriptInterface
    fun getSignalHistory(): String {
        return try {
            JSONObject()
                .put(
                    "summary",
                    signalMonitor.getHistoryAnalytics("All SIMs")
                )
                .toString()
        } catch (error: Exception) {
            JSONObject()
                .put("error", "Signal history unavailable")
                .toString()
        }
    }

    @JavascriptInterface
    fun startSpeedTest(requestId: String) {
        if (!requestId.matches(Regex("[A-Za-z0-9_-]{1,80}"))) {
            return
        }

        try {
            internetTester.run(
                onStatus = { status ->
                    // Status updates can be added to the UI in a later step.
                },
                onComplete = { result ->
                    deliverResult(requestId, result)
                },
                onError = { message ->
                    deliverError(requestId, message)
                }
            )
        } catch (error: Exception) {
            deliverError(
                requestId,
                error.message ?: "Unable to start speed test"
            )
        }
    }

    private fun deliverResult(
        requestId: String,
        result: InternetTestResult
    ) {
        val payload = JSONObject()
            .put("networkLabel", result.networkLabel)
            .put("dnsMs", result.dnsMs ?: JSONObject.NULL)
            .put("pingMs", result.pingMs ?: JSONObject.NULL)
            .put("jitterMs", result.jitterMs ?: JSONObject.NULL)
            .put("probeFailures", result.probeFailures)
            .put("probeCount", result.probeCount)
            .put("downloadMbps", result.downloadMbps ?: JSONObject.NULL)
            .put("uploadMbps", result.uploadMbps ?: JSONObject.NULL)
            .put("completedAt", result.completedAt)
            .put("note", result.note)
            .toString()

        deliverToJavaScript(requestId, payload)
    }

    private fun deliverError(requestId: String, message: String) {
        val payload = JSONObject()
            .put("error", message)
            .toString()

        deliverToJavaScript(requestId, payload)
    }

    private fun deliverToJavaScript(
        requestId: String,
        payload: String
    ) {
        val safeRequestId = JSONObject.quote(requestId)
        val safePayload = JSONObject.quote(payload)

        webView.post {
            webView.evaluateJavascript(
                "window.ANB && window.ANB.onNativeResult(" +
                    "$safeRequestId, $safePayload)",
                null
            )
        }
    }
}
