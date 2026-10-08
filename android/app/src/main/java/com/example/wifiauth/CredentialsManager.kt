package com.example.wifiauth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CredentialProfile(
    val id: String = UUID.randomUUID().toString(),
    val ssid: String,
    val loginUrl: String,
    val username: String,
    val password: String
)

class CredentialsManager(context: Context) {
    private val prefs: SharedPreferences

    init {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            "wifi_auth_profiles",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getProfiles(): List<CredentialProfile> {
        val raw = prefs.getString(PROFILES_KEY, null) ?: return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            List(json.length()) { index ->
                val item = json.getJSONObject(index)
                CredentialProfile(
                    id = item.getString("id"),
                    ssid = item.getString("ssid"),
                    loginUrl = item.getString("loginUrl"),
                    username = item.getString("username"),
                    password = item.getString("password")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveProfile(profile: CredentialProfile) {
        val updated = getProfiles().filterNot { it.id == profile.id } + profile
        writeProfiles(updated)
    }

    fun deleteProfile(id: String) {
        writeProfiles(getProfiles().filterNot { it.id == id })
    }

    fun findForSsid(ssid: String): CredentialProfile? =
        getProfiles().firstOrNull { it.ssid == ssid }

    private fun writeProfiles(profiles: List<CredentialProfile>) {
        val json = JSONArray().apply {
            profiles.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("ssid", item.ssid)
                    put("loginUrl", item.loginUrl)
                    put("username", item.username)
                    put("password", item.password)
                })
            }
        }
        prefs.edit().putString(PROFILES_KEY, json.toString()).apply()
    }

    companion object {
        private const val PROFILES_KEY = "profiles"
    }
}
