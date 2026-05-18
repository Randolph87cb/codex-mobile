package com.openai.codexmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openai.codexmobile.data.AppSettingsDefaults
import com.openai.codexmobile.data.RealBridgeDataProvider
import com.openai.codexmobile.data.SharedPreferencesAppSettingsStore
import com.openai.codexmobile.data.defaultEndpointForCurrentDevice
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
        val dataProvider = RealBridgeDataProvider()

        setContent {
            CodexMobileTheme {
                val appViewModel: AppViewModel = viewModel(
                    factory = AppViewModelFactory(
                        bridgeApi = dataProvider,
                        sessionRepository = dataProvider,
                        settingsStore = settingsStore,
                    ),
                )
                CodexMobileApp(appViewModel = appViewModel)
            }
        }
    }
}
