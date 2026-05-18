package com.openai.codexmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openai.codexmobile.data.FallbackCodexDataProvider
import com.openai.codexmobile.data.FakeCodexDataProvider
import com.openai.codexmobile.data.RealBridgeDataProvider
import com.openai.codexmobile.ui.CodexMobileApp
import com.openai.codexmobile.ui.theme.CodexMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dataProvider = FallbackCodexDataProvider(
            primary = RealBridgeDataProvider(),
            fallback = FakeCodexDataProvider(),
        )

        setContent {
            CodexMobileTheme {
                val appViewModel: AppViewModel = viewModel(
                    factory = AppViewModelFactory(
                        bridgeApi = dataProvider,
                        sessionRepository = dataProvider,
                    ),
                )
                CodexMobileApp(appViewModel = appViewModel)
            }
        }
    }
}
