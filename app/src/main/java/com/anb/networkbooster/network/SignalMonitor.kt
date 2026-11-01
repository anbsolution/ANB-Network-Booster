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

data class SimSignalSnapshot(
    val label: String,
    val networkDbm: Int?,
    val rsrp: Int?,
    val rsrq: Int?,
    val rssnr: Int?,
    val rssi: Int?,
    val level: Int?,
    val quality: String
) {
    fun asDisplayText(): String = buildString {
        appendLine(label)
        appendLine("Signal: ${networkDbm?.let { "$it dBm" } ?: "Unavailable"}")
        appendLine("LTE RSRP: ${rsrp?.let { "$it dBm" } ?: "Unavailable"}")
        appendLine("LTE RSRQ: ${rsrq?.let { "$it dB" } ?: "Unavailable"}")
        appendLine("LTE SINR/RSSNR: ${rssnr?.let { "$it dB" } ?: "Unavailable"}")
        appendLine("LTE RSSI: ${rssi?.let { "$it dBm" } ?: "Unavailable"}")
        append("Signal level: ${level?.let { "$it / 4" } ?: "Unavailable"} · $quality")
    }
}

/**
 * Reads modem-reported signal information. It cannot increase radio power
 * or force a handset to connect to a particular cell tower.
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
            val subscriptionManager =
                context.getSystemService(SubscriptionManager::class.java)
            val subscriptions = subscriptionManager?.activeSubscriptionInfoList.orEmpty()
            val baseManager = context.getSystemService(TelephonyManager::class.java)

            if (subscriptions.isEmpty()) {
                registerForSubscription(baseManager, "Default SIM")
                return
            }

            subscriptions.forEachIndexed { index, info ->
                val manager = baseManager.createForSubscriptionId(info.subscriptionId)
                val carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                    ?: "SIM ${index + 1}"
                registerForSubscription(
                    manager,
                    "SIM ${info.simSlotIndex + 1} · $carrier"
                )
            }
            if (registrations.isEmpty()) {
                onMessage("No active SIM signal listener is available.")
            }
        } catch (_: SecurityException) {
            onMessage("Android denied SIM information. Check app permissions and retry.")
        } catch (_: Exception) {
            onMessage("Cellular signal information is unavailable on this device.")
        }
    }

    @Suppress("DEPRECATION")
    private fun registerForSubscription(manager: TelephonyManager, label: String) {
        val listener = object : PhoneStateListener() {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                val lte = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    signalStrength.cellSignalStrengths
                        .filterIsInstance<CellSignalStrengthLte>()
                        .firstOrNull()
                } else null

                val dbm = lte?.dbm?.takeUnless { it == Int.MAX_VALUE }
                    ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        signalStrength.dbm.takeUnless { it == Int.MAX_VALUE }
                    } else null

                val rsrp = lte?.rsrp?.takeUnless { it == Int.MAX_VALUE }
                val rsrq = lte?.rsrq?.takeUnless { it == Int.MAX_VALUE }
                val rssnr = lte?.rssnr?.takeUnless { it == Int.MAX_VALUE }
                val rssi = lte?.rssi?.takeUnless { it == Int.MAX_VALUE }

                val quality = when {
                    dbm == null -> "Unavailable"
                    dbm >= -85 -> "Excellent"
                    dbm >= -100 -> "Good"
                    dbm >= -110 -> "Fair"
                    else -> "Weak"
                }

                onSnapshot(
                    SimSignalSnapshot(
                        label = label,
                        networkDbm = dbm,
                        rsrp = rsrp,
                        rsrq = rsrq,
                        rssnr = rssnr,
                        rssi = rssi,
                        level = signalStrength.level,
                        quality = quality
                    )
                )
            }
        }

        try {
            manager.listen(listener, LISTEN_SIGNAL_STRENGTHS)
            registrations.add(Registration(manager, listener))
        } catch (_: SecurityException) {
            onMessage("$label: permission denied.")
        }
    }

    @Suppress("DEPRECATION")
    fun stop() {
        registrations.forEach { registration ->
            try {
                registration.manager.listen(registration.listener, LISTEN_NONE)
            } catch (_: Exception) {
                // The system may already have removed this listener.
            }
        }
        registrations.clear()
    }
}
