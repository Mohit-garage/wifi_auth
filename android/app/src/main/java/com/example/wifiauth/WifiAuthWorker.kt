package com.example.wifiauth // Change this to match your package name

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

class WifiAuthWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val creds = CredentialsManager(applicationContext)
        val ssid = WifiConnection.currentSsid(applicationContext)
            ?: return Result.failure(workDataOf("error" to "No active Wi-Fi SSID detected. Grant Wi-Fi/location permission and enable Location."))
        val profile = creds.findForSsid(ssid)
        if (profile == null) {
            Log.i("WifiAuth", "No saved credentials for connected SSID: $ssid")
            return Result.failure(workDataOf("error" to "No saved credentials for connected SSID: $ssid"))
        }
        Log.i("WifiAuth", "Authenticating profile for SSID: $ssid")

        // Execute the safe Rust FFI wrapper
        return when (val result = WifiAuthCore.safeAuthenticate(
            profile.loginUrl,
            profile.username,
            profile.password
        )) {
            is AuthResult.Success -> {
                // Authentication succeeded
                Log.i("WifiAuth", "Authentication succeeded")
                Result.success()
            }
            is AuthResult.Error -> {
                Log.e("WifiAuth", "Authentication failed: ${result.errorMessage}")
                if (result.retryable) {
                    Result.retry()
                } else {
                    Result.failure(workDataOf("error" to result.errorMessage))
                }
            }
        }
    }
}
