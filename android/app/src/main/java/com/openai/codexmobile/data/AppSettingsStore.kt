package com.openai.codexmobile.data

import android.content.Context
import android.os.Build

data class AppSettings(
    val endpoint: String,
    val authToken: String,
    val cwd: String,
    val model: String,
    val approvalMode: String,
)

data class AppSettingsDefaults(
    val endpoint: String,
    val cwd: String,
    val model: String = "gpt-5.5",
    val approvalMode: String = "manual",
)

interface AppSettingsStore {
    fun load(): AppSettings

    fun save(settings: AppSettings)
}

class SharedPreferencesAppSettingsStore(
    context: Context,
    private val defaults: AppSettingsDefaults,
) : AppSettingsStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): AppSettings {
        return AppSettings(
            endpoint = preferences.getString(KEY_ENDPOINT, defaults.endpoint).orEmpty(),
            authToken = preferences.getString(KEY_AUTH_TOKEN, "").orEmpty(),
            cwd = preferences.getString(KEY_CWD, defaults.cwd).orEmpty(),
            model = preferences.getString(KEY_MODEL, defaults.model).orEmpty(),
            approvalMode = preferences.getString(KEY_APPROVAL_MODE, defaults.approvalMode)
                ?.takeIf { it == "manual" || it == "auto" }
                ?: defaults.approvalMode,
        )
    }

    override fun save(settings: AppSettings) {
        preferences.edit()
            .putString(KEY_ENDPOINT, settings.endpoint)
            .putString(KEY_AUTH_TOKEN, settings.authToken)
            .putString(KEY_CWD, settings.cwd)
            .putString(KEY_MODEL, settings.model)
            .putString(KEY_APPROVAL_MODE, settings.approvalMode)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "codex_mobile_settings"
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_AUTH_TOKEN = "auth_token"
        const val KEY_CWD = "cwd"
        const val KEY_MODEL = "model"
        const val KEY_APPROVAL_MODE = "approval_mode"
    }
}

fun defaultEndpointForCurrentDevice(): String {
    return if (isProbablyEmulator()) {
        "http://10.0.2.2:8787"
    } else {
        ""
    }
}

private fun isProbablyEmulator(): Boolean {
    val fingerprint = Build.FINGERPRINT.lowercase()
    val model = Build.MODEL.lowercase()
    val manufacturer = Build.MANUFACTURER.lowercase()
    val product = Build.PRODUCT.lowercase()

    return fingerprint.contains("generic") ||
        fingerprint.contains("emulator") ||
        model.contains("emulator") ||
        model.contains("android sdk") ||
        manufacturer.contains("genymotion") ||
        product.contains("sdk") ||
        product.contains("emulator")
}
