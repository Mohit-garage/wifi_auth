package com.example.wifiauth

import android.content.Context
import android.os.Build
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager

object WifiConnection {
    fun currentSsid(context: Context): String? {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val candidates = buildList {
            connectivity.activeNetwork?.let { add(it) }
            addAll(connectivity.allNetworks.toList())
        }
        val networkAndCapabilities = candidates.asSequence()
            .mapNotNull { network ->
                connectivity.getNetworkCapabilities(network)?.let { network to it }
            }
            .firstOrNull { (_, capabilities) ->
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        val transportInfo = networkAndCapabilities?.second?.let { capabilities ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                capabilities.transportInfo as? WifiInfo
            } else null
        }
        fun validSsid(info: WifiInfo?): String? = info?.ssid
            ?.removePrefix("\"")
            ?.removeSuffix("\"")
            ?.takeUnless { it.isBlank() || it == "<unknown ssid>" }

        return validSsid(transportInfo) ?: validSsid(wifiManager.connectionInfo)
    }
}
