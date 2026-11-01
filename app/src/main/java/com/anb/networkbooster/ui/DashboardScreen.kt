package com.anb.networkbooster.ui

/**
 * Stable destinations for the ANB Network Booster dashboard.
 *
 * This class deliberately contains no network logic. Existing NetworkMonitor,
 * SignalMonitor and InternetPerformanceTester remain owned by MainActivity.
 */
enum class DashboardScreen(
    val title: String,
    val sectionLabel: String,
    val description: String
) {
    HOME(
        "Home",
        "NETWORK CONNECTION",
        "Connection overview and network status"
    ),
    SIGNAL(
        "Signal",
        "LIVE SIM SIGNAL",
        "Live cellular signal readings for available SIMs"
    ),
    SPEED(
        "Speed",
        "SMART SPEED ENGINE",
        "Three-test speed check and current results"
    ),
    HISTORY(
        "History",
        "RECENT TEST HISTORY",
        "Previously saved network test results"
    ),
    SETTINGS(
        "Settings",
        "APP SETTINGS",
        "Appearance, permissions and app information"
    ),
    GUIDE(
        "Guide",
        "OFFLINE NETWORK GUIDE",
        "Searchable Hindi and English network troubleshooting guide"
    );

    companion object {
        fun fromName(value: String?): DashboardScreen =
            entries.firstOrNull { it.name == value } ?: HOME
    }
}

/**
 * Small state holder used by the Activity while switching dashboard content.
 * It does not persist transient navigation state or change measurement data.
 */
class DashboardNavigation(
    initialScreen: DashboardScreen = DashboardScreen.HOME
) {
    var currentScreen: DashboardScreen = initialScreen
        private set

    fun select(screen: DashboardScreen): Boolean {
        if (currentScreen == screen) return false
        currentScreen = screen
        return true
    }
}
