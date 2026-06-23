package com.openai.codexmobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class MainActivity : ComponentActivity() {
    private val requestedSessionId = MutableStateFlow<String?>(null)
    private val apkDownloadClient = OkHttpClient()
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
                CodexMobileApp(
                    appViewModel = appViewModel,
                    onInstallDebugApk = ::downloadAndInstallDebugApk,
                )
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

    private suspend fun downloadAndInstallDebugApk(downloadUrl: String, authToken: String): String {
        if (downloadUrl.isBlank()) {
            return "当前没有可用的 APK 下载链接，请先连接 bridge。"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName"),
            )
            startActivity(settingsIntent)
            return "请允许安装未知应用后，再点一次下载安装。"
        }

        val apkFile = withContext(Dispatchers.IO) {
            downloadApkToCache(downloadUrl, authToken)
        }
        openApkInstaller(apkFile)
        return "APK 已下载，已打开系统安装器。"
    }

    private fun downloadApkToCache(downloadUrl: String, authToken: String): File {
        val requestBuilder = Request.Builder()
            .url(downloadUrl)
            .header("Accept", "application/vnd.android.package-archive")
        authToken.trim().takeIf { it.isNotEmpty() }?.let { token ->
            requestBuilder.header("Authorization", "Bearer $token")
        }

        apkDownloadClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                error("APK 下载失败：HTTP ${response.code}")
            }
            val body = response.body ?: error("APK 下载失败：响应内容为空。")
            val apkFile = File(cacheDir, "codex-mobile-debug.apk")
            apkFile.outputStream().use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
            if (apkFile.length() <= 0L) {
                error("APK 下载失败：文件为空。")
            }
            return apkFile
        }
    }

    private fun openApkInstaller(apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            apkFile,
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(installIntent)
    }

    private fun Intent.sessionIdExtra(): String? {
        return getStringExtra(SessionWatchService.EXTRA_SESSION_ID)
            ?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val REQUEST_POST_NOTIFICATIONS = 3201
    }
}
