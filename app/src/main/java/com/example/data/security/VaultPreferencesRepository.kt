package com.example.data.security

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom

private val Context.vaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "folionote_vault_prefs")

data class VaultSettings(
    val hasPinConfigured: Boolean = false,
    val vaultLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val lockOnBackground: Boolean = true,
    val cachedAdFree: Boolean = false,
    val savedYoutubeQuery: String = "@GoogleDevelopers"
)

class VaultPreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.vaultDataStore

    private object Keys {
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val VAULT_LOCK_ENABLED = booleanPreferencesKey("vault_lock_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val LOCK_ON_BACKGROUND = booleanPreferencesKey("lock_on_background")
        val CACHED_AD_FREE = booleanPreferencesKey("cached_ad_free")
        val SAVED_YOUTUBE_QUERY = stringPreferencesKey("saved_youtube_query")
    }

    val vaultSettings: Flow<VaultSettings> = dataStore.data.map { prefs ->
        val hash = prefs[Keys.PIN_HASH].orEmpty()
        val salt = prefs[Keys.PIN_SALT].orEmpty()
        val configured = hash.isNotBlank() && salt.isNotBlank()
        VaultSettings(
            hasPinConfigured = configured,
            vaultLockEnabled = configured && (prefs[Keys.VAULT_LOCK_ENABLED] ?: false),
            biometricEnabled = configured && (prefs[Keys.BIOMETRIC_ENABLED] ?: false),
            lockOnBackground = prefs[Keys.LOCK_ON_BACKGROUND] ?: true,
            cachedAdFree = prefs[Keys.CACHED_AD_FREE] ?: false,
            savedYoutubeQuery = prefs[Keys.SAVED_YOUTUBE_QUERY] ?: "@GoogleDevelopers"
        )
    }

    suspend fun setPin(rawPin: String, enableBiometric: Boolean = false) {
        require(rawPin.length in 4..6 && rawPin.all { it.isDigit() }) {
            "PIN must be 4 to 6 digits."
        }
        val saltBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
        val hash = sha256Hex("$salt:$rawPin")

        dataStore.edit { prefs ->
            prefs[Keys.PIN_SALT] = salt
            prefs[Keys.PIN_HASH] = hash
            prefs[Keys.VAULT_LOCK_ENABLED] = true
            prefs[Keys.BIOMETRIC_ENABLED] = enableBiometric
        }
    }

    suspend fun verifyPin(rawPin: String): Boolean {
        val prefs = dataStore.data.first()
        val salt = prefs[Keys.PIN_SALT].orEmpty()
        val storedHash = prefs[Keys.PIN_HASH].orEmpty()
        if (salt.isBlank() || storedHash.isBlank()) return false
        val candidateHash = sha256Hex("$salt:$rawPin")
        return constantTimeEquals(storedHash, candidateHash)
    }

    suspend fun setVaultLockEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            val hasPin = !prefs[Keys.PIN_HASH].isNullOrBlank()
            prefs[Keys.VAULT_LOCK_ENABLED] = enabled && hasPin
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun setLockOnBackground(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.LOCK_ON_BACKGROUND] = enabled
        }
    }

    suspend fun clearPin() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.PIN_HASH)
            prefs.remove(Keys.PIN_SALT)
            prefs[Keys.VAULT_LOCK_ENABLED] = false
            prefs[Keys.BIOMETRIC_ENABLED] = false
        }
    }

    suspend fun setCachedAdFree(adFree: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.CACHED_AD_FREE] = adFree
        }
    }

    suspend fun setSavedYoutubeQuery(query: String) {
        dataStore.edit { prefs ->
            prefs[Keys.SAVED_YOUTUBE_QUERY] = query.trim().take(80)
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].code xor b[i].code)
        }
        return diff == 0
    }
}
