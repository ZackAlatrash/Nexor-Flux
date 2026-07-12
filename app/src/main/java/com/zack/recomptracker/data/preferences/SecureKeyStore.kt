package com.zack.recomptracker.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException
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

    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        openEncryptedPrefsWithRecovery(
            build = { buildEncryptedPrefs(appContext) },
            deleteCorruptStore = { appContext.deleteSharedPreferences(PREFS_FILE) },
        )
    }

    private val _hasKey = MutableStateFlow(false)
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    private val _hasWebSearchKey = MutableStateFlow(false)
    val hasWebSearchKey: StateFlow<Boolean> = _hasWebSearchKey.asStateFlow()

    init {
        _hasKey.value = getApiKey().isNotBlank()
        _hasWebSearchKey.value = getWebSearchKey().isNotBlank()
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

    fun getWebSearchKey(): String = prefs.getString(KEY_WEB_SEARCH, "").orEmpty()

    fun setWebSearchKey(value: String) {
        prefs.edit().putString(KEY_WEB_SEARCH, value.trim()).apply()
        _hasWebSearchKey.value = value.isNotBlank()
    }

    fun clearWebSearchKey() {
        prefs.edit().remove(KEY_WEB_SEARCH).apply()
        _hasWebSearchKey.value = false
    }

    private companion object {
        const val PREFS_FILE = "secure_ai_prefs"
        const val KEY_API = "cloud_api_key"
        const val KEY_WEB_SEARCH = "web_search_api_key"
    }
}

private fun buildEncryptedPrefs(appContext: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        appContext,
        "secure_ai_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}

/**
 * Opens the encrypted store via [build], recovering from an undecryptable keyset. After a
 * device-to-device restore the encrypted prefs file can come back without the (never-backed-up)
 * Keystore master key, so [build] throws [GeneralSecurityException]/[IOException]. Because this runs
 * eagerly in `Application.onCreate`, an unhandled throw is a launch crash-loop whose only escape is
 * Clear Data — wiping every log, the exact wipe the shared-keystore scheme exists to prevent. On
 * such a failure we drop the unreadable store via [deleteCorruptStore] and rebuild once; the user
 * re-enters their key. Any other (unexpected) exception propagates untouched.
 */
internal fun openEncryptedPrefsWithRecovery(
    build: () -> SharedPreferences,
    deleteCorruptStore: () -> Unit,
): SharedPreferences =
    try {
        build()
    } catch (e: Exception) {
        // Only an undecryptable/corrupt keyset is recoverable by dropping the store; anything else
        // (e.g. a programming error) must surface, not silently wipe the user's saved key.
        if (e !is GeneralSecurityException && e !is IOException) throw e
        deleteCorruptStore()
        build()
    }
