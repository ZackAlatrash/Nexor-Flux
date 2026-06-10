package com.zack.recomptracker.data.preferences

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores the single cloud API key in EncryptedSharedPreferences (AES via the Android Keystore).
 * Base URL and model id are NOT secrets and live in [UiPreferences].
 *
 * [hasKey] is a reactive flow so the routing/coach layers can react to the key being set or
 * cleared without a blocking read.
 */
class SecureKeyStore(context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "secure_ai_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _hasKey = MutableStateFlow(false)
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    init {
        _hasKey.value = getApiKey().isNotBlank()
    }

    fun getApiKey(): String = prefs.getString(KEY_API, "").orEmpty()

    fun setApiKey(value: String) {
        prefs.edit().putString(KEY_API, value.trim()).apply()
        _hasKey.value = value.isNotBlank()
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_API).apply()
        _hasKey.value = false
    }

    private companion object {
        const val KEY_API = "cloud_api_key"
    }
}
