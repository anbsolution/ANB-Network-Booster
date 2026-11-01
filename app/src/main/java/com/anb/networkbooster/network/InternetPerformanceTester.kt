package com.anb.networkbooster.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.util.UUID
import kotlin.math.abs

data class InternetTestResult(
    val networkLabel: String,
    val dnsMs: Long?,
    val pingMs: Long?,
    val jitterMs: Long?,
    val probeFailures: Int,
    val probeCount: Int,
    val downloadMbps: Double?,
    val uploadMbps: Double?,
    val completedAt: Long,
    val note: String
) {
    fun toDisplayText(): String = buildString {
        appendLine("NETWORK: $networkLabel")
        appendLine("DNS lookup: ${dnsMs?.let { "$it ms" } ?: "Unavailable"}")
        appendLine("TCP latency: ${pingMs?.let { "$it ms" } ?: "Unavailable"}")
        appendLine("Jitter: ${jitterMs?.let { "$it ms" } ?: "Unavailable"}")
        appendLine("TCP probe failures: $probeFailures / $probeCount")
        appendLine("Download: ${downloadMbps?.let { "%.2f Mbps".format(it) } ?: "Unavailable"}")
        appendLine("Upload: ${uploadMbps?.let { "%.2f Mbps".format(it) } ?: "Unavailable"}")
        appendLine(note)
    }
}

/**
 * User-triggered diagnostics on Android's current default cellular network.
 * Speed measurements use Cloudflare's public speed-test endpoints. No personal
 * files or user content are uploaded; the upload phase sends generated test bytes.
 */
class InternetPerformanceTester(context: Context) {
    private val appContext = context.applicationContext
    private val connectivity =
        appContext.getSystemService(ConnectivityManager::class.java)

    @Volatile
    private var running = false

    fun isRunning(): Boolean = running

    fun run(
        onStatus: (String) -> Unit,
        onComplete: (InternetTestResult) -> Unit,
        onError: (String) -> Unit
    ) {
        if (running) {
            onError("A test is already running.")
            return
        }

        val network: Network = connectivity.activeNetwork
            ?: run {
                onError("No active network. Connect mobile data and retry.")
                return
            }

        val caps = connectivity.getNetworkCapabilities(network)
        if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            onError("Select a mobile-data SIM and turn Wi-Fi off before testing.")
            return
        }
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            onError("The active mobile network does not report internet capability.")
            return
        }

        running = true
        Thread {
            try {
                onStatus("Testing DNS and TCP latency…")
                val probe = measureLatency(network)

                onStatus("Measuring download speed (about 2 MB)…")
                val download = measureDownload(network)

                onStatus("Measuring upload speed (about 512 KB)…")
                val upload = measureUpload(network)

                val carrier = try {
                    val tm = appContext.getSystemService(android.telephony.TelephonyManager::class.java)
                    tm.networkOperatorName?.takeIf { it.isNotBlank() } ?: "Current data SIM"
                } catch (_: Exception) {
                    "Current data SIM"
                }

                val result = InternetTestResult(
                    networkLabel = "$carrier · Mobile data",
                    dnsMs = probe.first,
                    pingMs = probe.second,
                    jitterMs = probe.third,
                    probeFailures = probe.fourth,
                    probeCount = PROBE_COUNT,
                    downloadMbps = download,
                    uploadMbps = upload,
                    completedAt = System.currentTimeMillis(),
                    note = "TCP probe failures are not ICMP packet-loss measurement."
                )
                onComplete(result)
            } catch (e: Exception) {
                onError(e.message ?: "Internet test failed. Please retry.")
            } finally {
                running = false
            }
        }.start()
    }

    private fun measureLatency(network: Network): Quadruple<Long?, Long?, Long?, Int> {
        val host = "speed.cloudflare.com"
        val dnsStart = System.nanoTime()
        val addresses = network.getAllByName(host)
        val dnsMs = elapsedMs(dnsStart)
        val address = addresses.firstOrNull()
            ?: throw IllegalStateException("DNS returned no server address.")

        val samples = mutableListOf<Long>()
        var failures = 0
        repeat(PROBE_COUNT) {
            val start = System.nanoTime()
            try {
                network.socketFactory.createSocket().use { socket ->
                    socket.connect(InetSocketAddress(address, 443), SOCKET_TIMEOUT_MS)
                }
                samples.add(elapsedMs(start))
            } catch (_: Exception) {
                failures++
            }
        }

        val ping = if (samples.isNotEmpty()) samples.average().toLong() else null
        val jitter = if (samples.size >= 2) {
            samples.zipWithNext { a, b -> abs(b - a) }.average().toLong()
        } else null
        return Quadruple(dnsMs, ping, jitter, failures)
    }

    private fun measureDownload(network: Network): Double {
        val url = URL("https://speed.cloudflare.com/__down?bytes=$DOWNLOAD_BYTES&measId=${UUID.randomUUID()}")
        val connection = network.openConnection(url) as HttpURLConnection
        connection.connectTimeout = HTTP_TIMEOUT_MS
        connection.readTimeout = HTTP_TIMEOUT_MS
        connection.requestMethod = "GET"
        connection.useCaches = false
        connection.setRequestProperty("Cache-Control", "no-cache")
        try {
            val start = System.nanoTime()
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("Download test server returned HTTP $code.")
            var total = 0L
            BufferedInputStream(connection.inputStream).use { input ->
                val buffer = ByteArray(16 * 1024)
                while (total < DOWNLOAD_BYTES) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                }
            }
            val seconds = elapsedNanos(start) / 1_000_000_000.0
            if (total == 0L || seconds <= 0.0) throw IllegalStateException("No download data received.")
            return total * 8.0 / seconds / 1_000_000.0
        } finally {
            connection.disconnect()
        }
    }

    private fun measureUpload(network: Network): Double {
        val payload = ByteArray(UPLOAD_BYTES) { (it % 251).toByte() }
        val url = URL("https://speed.cloudflare.com/__up?measId=${UUID.randomUUID()}")
        val connection = network.openConnection(url) as HttpURLConnection
        connection.connectTimeout = HTTP_TIMEOUT_MS
        connection.readTimeout = HTTP_TIMEOUT_MS
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.useCaches = false
        connection.setFixedLengthStreamingMode(payload.size)
        connection.setRequestProperty("Content-Type", "application/octet-stream")
        try {
            val start = System.nanoTime()
            connection.outputStream.use { it.write(payload) }
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("Upload test server returned HTTP $code.")
            try {
                connection.inputStream.use { input ->
                    val buffer = ByteArray(1024)
                    while (input.read(buffer) >= 0) { /* consume response */ }
                }
            } catch (_: Exception) {
                // The successful response code is the upload acknowledgement.
            }
            val seconds = elapsedNanos(start) / 1_000_000_000.0
            if (seconds <= 0.0) throw IllegalStateException("Upload timing unavailable.")
            return payload.size * 8.0 / seconds / 1_000_000.0
        } finally {
            connection.disconnect()
        }
    }

    private fun elapsedMs(start: Long): Long =
        (System.nanoTime() - start) / 1_000_000L

    private fun elapsedNanos(start: Long): Long =
        System.nanoTime() - start

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    companion object {
        private const val PROBE_COUNT = 5
        private const val SOCKET_TIMEOUT_MS = 2500
        private const val HTTP_TIMEOUT_MS = 15000
        private const val DOWNLOAD_BYTES = 2_000_000
        private const val UPLOAD_BYTES = 512_000
    }
}
