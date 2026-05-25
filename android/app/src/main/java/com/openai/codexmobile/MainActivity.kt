package com.openai.codexmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import com.openai.codexmobile.data.AppSettingsDefaults
import com.openai.codexmobile.data.RealBridgeDataProvider
import com.openai.codexmobile.data.SharedPreferencesAppSettingsStore
import com.openai.codexmobile.data.defaultEndpointForCurrentDevice
import com.openai.codexmobile.diagnostics.FileAppLogger
import com.openai.codexmobile.ui.CodexMobileApp
import com.openai.codexmobile.ui.theme.CodexMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                ),
            )
            val uiState by appViewModel.uiState.collectAsStateWithLifecycle()
            CodexMobileTheme(typeScale = uiState.fontSizeTypeScale) {
                CodexMobileApp(appViewModel = appViewModel)
            }
        }
    }
}
