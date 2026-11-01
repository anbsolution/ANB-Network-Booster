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
import com.anb.networkbooster.network.NetworkMonitor
import com.anb.networkbooster.network.SignalMonitor
import com.anb.networkbooster.network.SimSignalSnapshot

class MainActivity : Activity() {
    private lateinit var networkStatusView: TextView
    private lateinit var permissionStatusView: TextView
    private lateinit var signalContainer: LinearLayout
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var signalMonitor: SignalMonitor
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
            text = "SIGNAL INTELLIGENCE · PHASE 2"
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
            text = "Grant permissions & start monitoring"
            setOnClickListener { requestSignalPermissions() }
        }

        val signalHeading = sectionText("LIVE SIM SIGNAL")
        signalContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(networkStatusView)
        root.addView(signalHeading)
        root.addView(permissionStatusView)
        root.addView(permissionButton)
        root.addView(signalContainer)

        val scroll = ScrollView(this).apply {
            addView(root)
        }
        setContentView(scroll)

        networkMonitor = NetworkMonitor(this) { state ->
            runOnUiThread { networkStatusView.text = "Network status\n$state" }
        }
        signalMonitor = SignalMonitor(
            this,
            onSnapshot = { snapshot ->
                runOnUiThread { showSnapshot(snapshot) }
            },
            onMessage = { message ->
                runOnUiThread { permissionStatusView.text = message }
            }
        )

        if (signalMonitor.hasRequiredPermissions()) {
            startSignalMonitoring()
        } else {
            permissionStatusView.visibility = View.VISIBLE
        }
    }

    private fun sectionText(value: String) = TextView(this).apply {
        text = value
        textSize = 15f
        setTextColor(0xFFD5E4FF.toInt())
        setPadding(0, 14, 0, 14)
    }

    private fun requestSignalPermissions() {
        val missing = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION
        ).filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

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

    override fun onStart() {
        super.onStart()
        networkMonitor.start()
        if (::signalMonitor.isInitialized && signalMonitor.hasRequiredPermissions()) {
            signalMonitor.start()
        }
    }

    override fun onStop() {
        if (::signalMonitor.isInitialized) signalMonitor.stop()
        if (::networkMonitor.isInitialized) networkMonitor.stop()
        super.onStop()
    }

    @Deprecated("Deprecated in Android API; retained for broad device compatibility")
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
    }
}
