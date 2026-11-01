package com.anb.networkbooster.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

class NetworkMonitor(
    context: Context,
    private val onStateChanged: (String) -> Unit
) {
    private val connectivity =
        context.getSystemService(ConnectivityManager::class.java)

    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            onStateChanged("Network available. Checking internet access…")
        }

        override fun onLost(network: Network) {
            onStateChanged("Network disconnected or no longer the default network.")
        }

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) {
            val hasInternet =
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val validated =
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val transport = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile network"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                else -> "Other network"
            }
            val status = when {
                validated -> "Internet verified"
                hasInternet -> "Network connected; internet not yet verified"
                else -> "Connected without internet capability"
            }
            onStateChanged("$transport\n$status")
        }
    }

    fun start() {
        if (registered) return
        try {
            connectivity.registerDefaultNetworkCallback(callback)
            registered = true
        } catch (_: Exception) {
            onStateChanged("Unable to monitor network state on this device.")
        }
    }

    fun stop() {
        if (!registered) return
        try {
            connectivity.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
            // Callback may already have been removed by the system.
        } finally {
            registered = false
        }
    }
}
