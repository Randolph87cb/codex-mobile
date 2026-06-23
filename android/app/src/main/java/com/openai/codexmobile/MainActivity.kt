package com.openai.codexmobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.openai.codexmobile.data.AppSettingsDefaults
import com.openai.codexmobile.data.RealBridgeDataProvider
import com.openai.codexmobile.data.SharedPreferencesAppSettingsStore
import com.openai.codexmobile.data.defaultEndpointForCurrentDevice
import com.openai.codexmobile.diagnostics.FileAppLogger
import com.openai.codexmobile.service.SessionWatchService
import com.openai.codexmobile.ui.CodexMobileApp
import com.openai.codexmobile.ui.theme.CodexMobileTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val requestedSessionId = MutableStateFlow<String?>(null)
    private var appViewModel: AppViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedSessionId.value = intent.sessionIdExtra()
        requestNotificationPermissionIfNeeded()

        val settingsStore = SharedPreferencesAppSettingsStore(
            context = applicationContext,
            defaults = AppSettingsDefaults(
                endpoint = defaultEndpointForCurrentDevice(),
                cwd = "D:\\workspace\\codex-mobile",
            ),
        )
        val appLogger = FileAppLogger(applicationContext).also {
            it.installCrashHandler()
            it.info("MainActivity", "应用启动。")
        }
        val dataProvider = RealBridgeDataProvider(appLogger)

        setContent {
            val appViewModel: AppViewModel = viewModel(
                factory = AppViewModelFactory(
                    bridgeApi = dataProvider,
                    sessionRepository = dataProvider,
                    settingsStore = settingsStore,
                    appLogger = appLogger,
                    startSessionWatch = { sessionId ->
                        SessionWatchService.startWatching(applicationContext, sessionId)
                    },
                ),
            )
            this@MainActivity.appViewModel = appViewModel
            val uiState by appViewModel.uiState.collectAsStateWithLifecycle()
            val sessionIdFromNotification by requestedSessionId.collectAsStateWithLifecycle()
            LaunchedEffect(appViewModel) {
                appViewModel.setNotificationPermissionGranted(canPostNotifications())
            }
            LaunchedEffect(sessionIdFromNotification) {
                val sessionId = sessionIdFromNotification ?: return@LaunchedEffect
                appViewModel.openSessionFromNotification(sessionId)
                requestedSessionId.value = null
            }
            CodexMobileTheme(typeScale = uiState.fontSizeTypeScale) {
                CodexMobileApp(appViewModel = appViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appViewModel?.setNotificationPermissionGranted(canPostNotifications())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedSessionId.value = intent.sessionIdExtra()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_POST_NOTIFICATIONS) {
            appViewModel?.setNotificationPermissionGranted(canPostNotifications())
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_POST_NOTIFICATIONS,
        )
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun Intent.sessionIdExtra(): String? {
        return getStringExtra(SessionWatchService.EXTRA_SESSION_ID)
            ?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val REQUEST_POST_NOTIFICATIONS = 3201
    }
}
