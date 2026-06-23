package com.openai.codexmobile.data

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

data class SavedBridgeConnection(
    val id: String,
    val name: String,
    val endpoint: String,
    val authToken: String,
)

data class AppSettings(
    val endpoint: String,
    val authToken: String,
    val cwd: String,
    val model: String,
    val approvalMode: String,
    val reasoningEffort: String,
    val serviceTier: String,
    val sandboxMode: String,
    val fontSize: String = "standard",
    val avatarShape: String = "circle",
    val userAvatarStyle: String = "text",
    val userAvatarImagePath: String = "",
    val savedConnections: List<SavedBridgeConnection> = emptyList(),
    val selectedConnectionId: String? = null,
)

data class AppSettingsDefaults(
    val endpoint: String,
    val cwd: String,
    val model: String = "gpt-5.5",
    val approvalMode: String = "auto",
    val reasoningEffort: String = "medium",
    val serviceTier: String = "default",
    val sandboxMode: String = "danger-full-access",
    val fontSize: String = "standard",
    val avatarShape: String = "circle",
    val userAvatarStyle: String = "text",
    val userAvatarImagePath: String = "",
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
        val savedConnections = decodeSavedConnections(
            preferences.getString(KEY_SAVED_CONNECTIONS, null),
        )
        return AppSettings(
            endpoint = preferences.getString(KEY_ENDPOINT, defaults.endpoint).orEmpty(),
            authToken = preferences.getString(KEY_AUTH_TOKEN, "").orEmpty(),
            cwd = preferences.getString(KEY_CWD, defaults.cwd).orEmpty(),
            model = preferences.getString(KEY_MODEL, defaults.model).orEmpty(),
            approvalMode = preferences.getString(KEY_APPROVAL_MODE, defaults.approvalMode)
                ?.takeIf { it == "manual" || it == "auto" }
                ?: defaults.approvalMode,
            reasoningEffort = preferences.getString(KEY_REASONING_EFFORT, defaults.reasoningEffort)
                ?.takeIf { it in setOf("minimal", "low", "medium", "high", "xhigh") }
                ?: defaults.reasoningEffort,
            serviceTier = preferences.getString(KEY_SERVICE_TIER, defaults.serviceTier)
                ?.takeIf { it == "default" || it == "fast" }
                ?: defaults.serviceTier,
            sandboxMode = preferences.getString(KEY_SANDBOX_MODE, defaults.sandboxMode)
                ?.takeIf { it == "read-only" || it == "workspace-write" || it == "danger-full-access" }
                ?: defaults.sandboxMode,
            fontSize = preferences.getString(KEY_FONT_SIZE, defaults.fontSize)
                ?.takeIf { it == "small" || it == "standard" || it == "large" }
                ?: defaults.fontSize,
            avatarShape = preferences.getString(KEY_AVATAR_SHAPE, defaults.avatarShape)
                ?.takeIf { it == "circle" || it == "rounded" }
                ?: defaults.avatarShape,
            userAvatarStyle = preferences.getString(KEY_USER_AVATAR_STYLE, defaults.userAvatarStyle)
                ?.takeIf { it == "text" || it == "app-icon" || it == "image" }
                ?: defaults.userAvatarStyle,
            userAvatarImagePath = preferences.getString(KEY_USER_AVATAR_IMAGE_PATH, defaults.userAvatarImagePath)
                .orEmpty(),
            savedConnections = savedConnections,
            selectedConnectionId = preferences.getString(KEY_SELECTED_CONNECTION_ID, null),
        )
    }

    override fun save(settings: AppSettings) {
        preferences.edit()
            .putString(KEY_ENDPOINT, settings.endpoint)
            .putString(KEY_AUTH_TOKEN, settings.authToken)
            .putString(KEY_CWD, settings.cwd)
            .putString(KEY_MODEL, settings.model)
            .putString(KEY_APPROVAL_MODE, settings.approvalMode)
            .putString(KEY_REASONING_EFFORT, settings.reasoningEffort)
            .putString(KEY_SERVICE_TIER, settings.serviceTier)
            .putString(KEY_SANDBOX_MODE, settings.sandboxMode)
            .putString(KEY_FONT_SIZE, settings.fontSize)
            .putString(KEY_AVATAR_SHAPE, settings.avatarShape)
            .putString(KEY_USER_AVATAR_STYLE, settings.userAvatarStyle)
            .putString(KEY_USER_AVATAR_IMAGE_PATH, settings.userAvatarImagePath)
            .putString(KEY_SAVED_CONNECTIONS, encodeSavedConnections(settings.savedConnections))
            .putString(KEY_SELECTED_CONNECTION_ID, settings.selectedConnectionId)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "codex_mobile_settings"
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_AUTH_TOKEN = "auth_token"
        const val KEY_CWD = "cwd"
        const val KEY_MODEL = "model"
        const val KEY_APPROVAL_MODE = "approval_mode"
        const val KEY_REASONING_EFFORT = "reasoning_effort"
        const val KEY_SERVICE_TIER = "service_tier"
        const val KEY_SANDBOX_MODE = "sandbox_mode"
        const val KEY_FONT_SIZE = "font_size"
        const val KEY_AVATAR_SHAPE = "avatar_shape"
        const val KEY_USER_AVATAR_STYLE = "user_avatar_style"
        const val KEY_USER_AVATAR_IMAGE_PATH = "user_avatar_image_path"
        const val KEY_SAVED_CONNECTIONS = "saved_connections"
        const val KEY_SELECTED_CONNECTION_ID = "selected_connection_id"
    }
}

private fun decodeSavedConnections(raw: String?): List<SavedBridgeConnection> {
    if (raw.isNullOrBlank()) {
        return emptyList()
    }
    return runCatching {
        val json = JSONArray(raw)
        buildList {
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                add(
                    SavedBridgeConnection(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        endpoint = item.optString("endpoint"),
                        authToken = item.optString("authToken"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun encodeSavedConnections(connections: List<SavedBridgeConnection>): String {
    val json = JSONArray()
    connections.forEach { connection ->
        json.put(
            JSONObject().apply {
                put("id", connection.id)
                put("name", connection.name)
                put("endpoint", connection.endpoint)
                put("authToken", connection.authToken)
            },
        )
    }
    return json.toString()
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
