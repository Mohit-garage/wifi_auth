package com.example.wifiauth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager

class MainActivity : ComponentActivity() {

    private val locationPermissionCode = 100
    private var connectedSsid by mutableStateOf<String?>(null)
    private var networkMonitor: WifiNetworkMonitor? = null

    companion object {
        init {
            System.loadLibrary("uniffi_wifi_auth")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkLocationPermission()

        networkMonitor = WifiNetworkMonitor(this) { ssid -> connectedSsid = ssid }
        networkMonitor?.startMonitoring()
        networkMonitor?.schedulePeriodicCheck()

        setContent {
            val manager = remember { CredentialsManager(this@MainActivity) }
            var profiles by remember { mutableStateOf(manager.getProfiles()) }
            var editing by remember { mutableStateOf<CredentialProfile?>(null) }
            var statusText by remember { mutableStateOf("Automatic authentication is active") }

            if (editing != null) {
                ProfileEditor(
                    initial = editing!!,
                    onCancel = { editing = null },
                    onSave = { profile ->
                        manager.saveProfile(profile)
                        profiles = manager.getProfiles()
                        editing = null
                        statusText = "Profile saved"
                        WorkManager.getInstance(this@MainActivity).enqueueUniqueWork(
                            "wifi_auth_network",
                            ExistingWorkPolicy.KEEP,
                            OneTimeWorkRequestBuilder<WifiAuthWorker>().build()
                        )
                    }
                )
            } else {
                ProfileList(
                    profiles = profiles,
                    connectedSsid = connectedSsid,
                    statusText = statusText,
                    onAdd = { editing = CredentialProfile(ssid = "", loginUrl = "", username = "", password = "") },
                    onEdit = { editing = it },
                    onDelete = {
                        manager.deleteProfile(it.id)
                        profiles = manager.getProfiles()
                    },
                    onAuthenticateNow = {
                        val request = OneTimeWorkRequestBuilder<WifiAuthWorker>().build()
                        statusText = "Checking connected Wi-Fi..."
                        WorkManager.getInstance(this@MainActivity).enqueueUniqueWork(
                            "wifi_auth_manual",
                            ExistingWorkPolicy.REPLACE,
                            request
                        )
                        WorkManager.getInstance(this@MainActivity)
                            .getWorkInfoByIdLiveData(request.id)
                            .observe(this@MainActivity) { info ->
                                statusText = when (info?.state) {
                                    WorkInfo.State.SUCCEEDED -> "Authentication successful or not required"
                                    WorkInfo.State.FAILED -> info.outputData.getString("error") ?: "Authentication failed"
                                    WorkInfo.State.RUNNING -> "Authenticating..."
                                    else -> statusText
                                }
                            }
                    }
                )
            }
        }
    }

    private fun checkLocationPermission() {
        val required = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missing.toTypedArray(),
                locationPermissionCode
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionCode && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            networkMonitor?.startMonitoring()
        }
    }
}

@androidx.compose.runtime.Composable
private fun ProfileList(
    profiles: List<CredentialProfile>,
    connectedSsid: String?,
    statusText: String,
    onAdd: () -> Unit,
    onEdit: (CredentialProfile) -> Unit,
    onDelete: (CredentialProfile) -> Unit,
    onAuthenticateNow: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Wi-Fi authentication")
        Spacer(Modifier.height(8.dp))
        Text("Connected Wi-Fi: ${connectedSsid ?: "None detected"}")
        Spacer(Modifier.height(8.dp))
        Text(statusText)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Add Wi-Fi credentials") }
        OutlinedButton(onClick = onAuthenticateNow, modifier = Modifier.fillMaxWidth()) {
            Text("Authenticate connected Wi-Fi now")
        }
        Spacer(Modifier.height(12.dp))
        if (profiles.isEmpty()) {
            Text("No saved profiles. Add credentials for a Wi-Fi network.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(profiles, key = { it.id }) { profile ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(profile.ssid)
                            Text(profile.username)
                            Text(if (profile.ssid == connectedSsid) "Available and connected" else "Not connected")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onEdit(profile) }) { Text("Edit") }
                                OutlinedButton(onClick = { onDelete(profile) }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ProfileEditor(
    initial: CredentialProfile,
    onCancel: () -> Unit,
    onSave: (CredentialProfile) -> Unit
) {
    var ssid by remember(initial.id) { mutableStateOf(initial.ssid) }
    var loginUrl by remember(initial.id) { mutableStateOf(initial.loginUrl) }
    var username by remember(initial.id) { mutableStateOf(initial.username) }
    var password by remember(initial.id) { mutableStateOf(initial.password) }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (initial.ssid.isBlank()) "Add credentials" else "Edit credentials")
        OutlinedTextField(ssid, { ssid = it }, label = { Text("Wi-Fi SSID") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(loginUrl, { loginUrl = it }, label = { Text("Login URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(username, { username = it }, label = { Text("User ID") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            password,
            { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = ssid.isNotBlank() && loginUrl.isNotBlank() && username.isNotBlank() && password.isNotEmpty(),
                onClick = { onSave(initial.copy(ssid = ssid.trim(), loginUrl = loginUrl.trim(), username = username.trim(), password = password)) }
            ) { Text("Save") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
