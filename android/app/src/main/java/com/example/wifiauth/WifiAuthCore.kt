package com.example.wifiauth

import uniffi.wifi_auth.AuthException
import uniffi.wifi_auth.authenticate

// Change this to match your package name

sealed class AuthResult {
    data class Success(val message: String) : AuthResult()
    data class Error(val errorMessage: String, val retryable: Boolean) : AuthResult()
}

object WifiAuthCore {
    fun safeAuthenticate(loginUrl: String, username: String, password: String): AuthResult {
        return try {
            // This calls the generated UniFFI function from your Rust library
            val result = authenticate(loginUrl, username, password)
            AuthResult.Success(result)
        } catch (e: AuthException.NetworkException) {
            AuthResult.Error("Network request failed", retryable = true)
        } catch (e: AuthException) {
            // Authentication rejection, challenge, parse, and runtime failures
            // are meaningful terminal results for this attempt.
            AuthResult.Error(e.message ?: "Authentication failed", retryable = false)
        } catch (e: Exception) {
            // This is a bridge/library contract failure, not a portal result.
            // Keep the diagnostic limited to the exception type/message; never
            // include credentials or request bodies here.
            val detail = e.message?.take(160) ?: "no diagnostic message"
            AuthResult.Error(
                "Rust bridge error (${e::class.java.simpleName}): $detail",
                retryable = false
            )
        }
    }
}
