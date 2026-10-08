package com.example.wifiauth // Change this to match your package name

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.location.LocationManager
import android.os.Build
import android.net.wifi.WifiManager
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import java.util.concurrent.TimeUnit

class WifiNetworkMonitor(
    private val context: Context,
    private val onSsidChanged: (String?) -> Unit = {}
) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var registered = false

    fun startMonitoring() {
        if (registered) return
        registered = true
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                enqueueForCurrentWifi("onAvailable")
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, capabilities)
                enqueueForCurrentWifi("onCapabilitiesChanged")
            }
        })

        // Also check a Wi-Fi network that was connected before the app opened.
        enqueueForCurrentWifi("startup")
    }

    private fun enqueueForCurrentWifi(source: String) {
        val ssid = WifiConnection.currentSsid(context) ?: run {
            onSsidChanged(null)
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val fineGranted = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            val nearbyGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.checkSelfPermission("android.permission.NEARBY_WIFI_DEVICES") ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            val locationEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
                locationManager.isLocationEnabled
            Log.i(
                "WifiAuth",
                "No active Wi-Fi SSID detected ($source); " +
                    "wifiEnabled=${wifiManager.isWifiEnabled}, " +
                    "rawSsid=${wifiManager.connectionInfo.ssid}, " +
                    "fineLocation=$fineGranted, nearbyWifi=$nearbyGranted, " +
                    "locationEnabled=$locationEnabled"
            )
            return
        }
        onSsidChanged(ssid)
        val profile = CredentialsManager(context).findForSsid(ssid)
        Log.i("WifiAuth", "Connected Wi-Fi: $ssid; saved profile: ${profile != null}")
        if (profile == null) return

        WorkManager.getInstance(context).enqueueUniqueWork(
            "wifi_auth_network",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WifiAuthWorker>().build()
        )
    }

    fun schedulePeriodicCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val work = PeriodicWorkRequestBuilder<WifiAuthWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "wifi_auth_periodic",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }
}
