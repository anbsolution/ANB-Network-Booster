package com.anb.networkbooster

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.anb.networkbooster.network.NetworkMonitor

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var monitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(0xFF0B1730.toInt())
        }

        val title = TextView(this).apply {
            text = "ANB NETWORK BOOSTER"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        statusView = TextView(this).apply {
            text = "Checking connectivity…"
            textSize = 16f
            setTextColor(0xFFB8D4FF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 0)
        }

        layout.addView(title)
        layout.addView(statusView)
        setContentView(layout)

        monitor = NetworkMonitor(this) { state ->
            runOnUiThread { statusView.text = state }
        }
    }

    override fun onStart() {
        super.onStart()
        monitor.start()
    }

    override fun onStop() {
        monitor.stop()
        super.onStop()
    }
}
