package com.openai.codexmobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.openai.codexmobile.AppViewModel
import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.ui.screen.ConnectionScreen
import com.openai.codexmobile.ui.screen.SessionDetailScreen
import com.openai.codexmobile.ui.screen.SessionDraftScreen
import com.openai.codexmobile.ui.screen.SessionListScreen
import com.openai.codexmobile.ui.screen.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private object Routes {
    const val Connection = "connection"
    const val Sessions = "sessions"
    const val DraftSession = "draft"
    const val SessionDetail = "session/{sessionId}"
    const val Settings = "settings"
}

@Composable
fun CodexMobileApp(
    appViewModel: AppViewModel,
    onInstallDebugApk: suspend (String, String) -> String = { _, _ -> "当前版本不支持直接安装 APK。" },
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by appViewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val navigateToSessionsAfterConnect = remember { mutableStateOf(false) }
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) {
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch {
            val prepared = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        prepareImageAttachmentForBridge(context, uri)
                    }.getOrNull()
                }
            }
            if (prepared.isNotEmpty()) {
                appViewModel.attachPreparedImages(prepared)
            }
            if (prepared.size != uris.size) {
                snackbarHostState.showSnackbar("部分图片处理失败，请确认文件是可用图片。")
            }
        }
    }
    val videoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) {
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch {
            val prepared = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        prepareVideoAttachmentForBridge(context, uri)
                    }.getOrNull()
                }
            }
            if (prepared.isNotEmpty()) {
                appViewModel.attachPreparedVideos(prepared)
            }
            if (prepared.size != uris.size) {
                snackbarHostState.showSnackbar("部分视频处理失败，请确认文件是可用视频。")
            }
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(uiState.notificationNavigationSessionId) {
        val sessionId = uiState.notificationNavigationSessionId ?: return@LaunchedEffect
        navController.navigate("session/$sessionId") {
            launchSingleTop = true
        }
        appViewModel.consumeNotificationNavigationSession(sessionId)
    }

    LaunchedEffect(uiState.connectionState, currentRoute, navigateToSessionsAfterConnect.value) {
        if (navigateToSessionsAfterConnect.value &&
            uiState.connectionState is BridgeConnectionState.Connected &&
            currentRoute == Routes.Connection
        ) {
            navigateToSessionsAfterConnect.value = false
            navController.navigate(Routes.Sessions) {
                popUpTo(Routes.Connection) {
                    inclusive = false
                }
            }
        }
    }

    LaunchedEffect(currentRoute, uiState.selectedDraftSession, uiState.selectedSession?.id) {
        if (currentRoute == Routes.DraftSession &&
            uiState.selectedDraftSession == null &&
            uiState.selectedSession != null
        ) {
            navController.navigate("session/${uiState.selectedSession?.id}") {
                popUpTo(Routes.DraftSession) {
                    inclusive = true
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, appViewModel) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> appViewModel.onAppForegrounded()
                Lifecycle.Event.ON_STOP -> appViewModel.onAppBackgrounded()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            appViewModel.onAppForegrounded()
        }
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.Connection,
        ) {
            composable(Routes.Connection) {
                ConnectionScreen(
                    currentConnectionName = uiState.selectedConnection?.name.orEmpty(),
                    savedConnections = uiState.savedConnections,
                    selectedConnectionId = uiState.selectedConnectionId,
                    endpoint = uiState.endpointInput,
                    authToken = uiState.authTokenInput,
                    connectionState = uiState.connectionState,
                    isLoading = uiState.isLoading,
                    onConnectionNameChange = appViewModel::updateSelectedConnectionName,
                    onEndpointChange = appViewModel::updateEndpointInput,
                    onAuthTokenChange = appViewModel::updateAuthTokenInput,
                    onAddSavedConnection = appViewModel::addSavedConnection,
                    onSelectSavedConnection = appViewModel::selectSavedConnection,
                    onDeleteSavedConnection = appViewModel::deleteSavedConnection,
                    onConnect = {
                        navigateToSessionsAfterConnect.value = true
                        appViewModel.connect()
                    },
                    onOpenSettings = { navController.navigate(Routes.Settings) },
                    onOpenSessions = {
                        navController.navigate(Routes.Sessions) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.Sessions) {
                LaunchedEffect(uiState.connectionState) {
                    if (uiState.connectionState is BridgeConnectionState.Connected) {
                        appViewModel.refreshSessionList()
                    }
                }
                SessionListScreen(
                    sessions = uiState.sessions,
                    showArchivedSessions = uiState.showArchivedSessions,
                    connectionState = uiState.connectionState,
                    accountQuota = uiState.accountQuota,
                    currentCwd = uiState.cwdInput,
                    isLoading = uiState.isLoading,
                    onOpenSession = { sessionId ->
                        navController.navigate("session/$sessionId")
                    },
                    onShowArchivedSessionsChange = appViewModel::setShowArchivedSessions,
                    onArchiveSession = appViewModel::archiveSession,
                    onUnarchiveSession = appViewModel::unarchiveSession,
                    onCreateDraft = { cwd ->
                        appViewModel.startDraftSession(cwd)
                        navController.navigate(Routes.DraftSession)
                    },
                    onNavigateToConnect = {
                        navController.navigate(Routes.Connection) {
                            launchSingleTop = true
                        }
                    },
                    onOpenSettings = { navController.navigate(Routes.Settings) },
                )
            }
            composable(Routes.DraftSession) {
                DisposableEffect(Unit) {
                    onDispose {
                        if (uiState.selectedDraftSession != null) {
                            appViewModel.discardDraftSession()
                        }
                    }
                }
                SessionDraftScreen(
                    draftSession = uiState.selectedDraftSession,
                    draftMessage = uiState.draftMessage,
                    pendingImageAttachments = uiState.pendingImageAttachments,
                    pendingVideoAttachments = uiState.pendingVideoAttachments,
                    isLoading = uiState.isLoading,
                    onDraftMessageChange = appViewModel::updateDraftMessage,
                    onPickImage = { imagePickerLauncher.launch("image/*") },
                    onPickVideo = { videoPickerLauncher.launch("video/*") },
                    onRemovePendingImageAttachment = appViewModel::removePendingImageAttachment,
                    onRetryPendingImageAttachment = appViewModel::retryPendingImageAttachment,
                    onRemovePendingVideoAttachment = appViewModel::removePendingVideoAttachment,
                    onRetryPendingVideoAttachment = appViewModel::retryPendingVideoAttachment,
                    onSend = appViewModel::sendInput,
                    onUpdateCwd = appViewModel::updateSelectedSessionCwd,
                    onUpdateModel = appViewModel::updateSelectedSessionModel,
                    onUpdateReasoningEffort = appViewModel::updateSelectedSessionReasoningEffort,
                    onUpdateServiceTier = appViewModel::updateSelectedSessionServiceTier,
                    onUpdateSandboxMode = appViewModel::updateSelectedSessionSandboxMode,
                    onOpenSettings = { navController.navigate(Routes.Settings) },
                    onBack = {
                        appViewModel.discardDraftSession()
                        navController.popBackStack()
                    },
                )
            }
            composable(Routes.SessionDetail) { entry ->
                val sessionId = entry.arguments?.getString("sessionId").orEmpty()
                LaunchedEffect(sessionId) {
                    appViewModel.openSessionDetail(sessionId)
                }
                DisposableEffect(sessionId) {
                    onDispose {
                        appViewModel.closeSessionDetail(sessionId)
                    }
                }
                SessionDetailScreen(
                    sessionDetail = uiState.selectedSession,
                    draftSession = null,
                    connectionState = uiState.connectionState,
                    accountQuota = uiState.accountQuota,
                    sessionRealtimeState = uiState.sessionRealtimeState,
                    backgroundWatch = uiState.backgroundWatch,
                    queuedInputs = uiState.queuedInputs,
                    draftMessage = uiState.draftMessage,
                    pendingImageAttachments = uiState.pendingImageAttachments,
                    pendingVideoAttachments = uiState.pendingVideoAttachments,
                    bridgeEndpoint = uiState.endpointInput,
                    bridgeAuthToken = uiState.authTokenInput,
                    isLoading = uiState.isLoading,
                    onDraftMessageChange = appViewModel::updateDraftMessage,
                    onPickImage = { imagePickerLauncher.launch("image/*") },
                    onPickVideo = { videoPickerLauncher.launch("video/*") },
                    onRemovePendingImageAttachment = appViewModel::removePendingImageAttachment,
                    onRetryPendingImageAttachment = appViewModel::retryPendingImageAttachment,
                    onRemovePendingVideoAttachment = appViewModel::removePendingVideoAttachment,
                    onRetryPendingVideoAttachment = appViewModel::retryPendingVideoAttachment,
                    onSend = appViewModel::sendInput,
                    onInterrupt = appViewModel::interruptSelectedSession,
                    onApprovalDecision = appViewModel::submitApproval,
                    onUpdateCwd = appViewModel::updateSelectedSessionCwd,
                    onRenameSessionTitle = appViewModel::renameSelectedSessionTitle,
                    onUpdateModel = appViewModel::updateSelectedSessionModel,
                    onUpdateReasoningEffort = appViewModel::updateSelectedSessionReasoningEffort,
                    onUpdateServiceTier = appViewModel::updateSelectedSessionServiceTier,
                    onUpdateSandboxMode = appViewModel::updateSelectedSessionSandboxMode,
                    onUpdateGoal = appViewModel::updateSelectedSessionGoal,
                    onPauseGoal = appViewModel::pauseSelectedSessionGoal,
                    onResumeGoal = appViewModel::resumeSelectedSessionGoal,
                    onClearGoal = appViewModel::clearSelectedSessionGoal,
                    onRefreshSession = appViewModel::refreshSelectedSession,
                    onShowMessage = { message ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(message)
                        }
                    },
                    title = uiState.selectedSession?.title ?: "会话详情",
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.Settings) {
                LaunchedEffect(Unit) {
                    appViewModel.refreshDiagnosticsLog()
                }
                SettingsScreen(
                    items = uiState.settingsItems,
                    selectedConnectionName = uiState.selectedConnection?.name.orEmpty(),
                    savedConnections = uiState.savedConnections,
                    selectedConnectionId = uiState.selectedConnectionId,
                    endpointInput = uiState.endpointInput,
                    authTokenInput = uiState.authTokenInput,
                    cwdInput = uiState.cwdInput,
                    modelInput = uiState.modelInput,
                    approvalModeInput = uiState.approvalModeInput,
                    reasoningEffortInput = uiState.reasoningEffortInput,
                    serviceTierInput = uiState.serviceTierInput,
                    sandboxModeInput = uiState.sandboxModeInput,
                    fontSizeInput = uiState.fontSizeInput,
                    latestDebugApkPath = uiState.latestDebugApkPath,
                    latestDebugApkDownloadUrl = uiState.latestDebugApkDownloadUrl,
                    latestDebugApkDownloadHint = uiState.latestDebugApkDownloadHint,
                    diagnosticsLog = uiState.diagnosticsLog,
                    onConnectionNameChange = appViewModel::updateSelectedConnectionName,
                    onAddSavedConnection = appViewModel::addSavedConnection,
                    onSelectSavedConnection = appViewModel::selectSavedConnection,
                    onDeleteSavedConnection = appViewModel::deleteSavedConnection,
                    onEndpointChange = appViewModel::updateEndpointInput,
                    onAuthTokenChange = appViewModel::updateAuthTokenInput,
                    onCwdChange = appViewModel::updateCwdInput,
                    onModelChange = appViewModel::updateModelInput,
                    onApprovalModeChange = appViewModel::updateApprovalModeInput,
                    onReasoningEffortChange = appViewModel::updateReasoningEffortInput,
                    onServiceTierChange = appViewModel::updateServiceTierInput,
                    onSandboxModeChange = appViewModel::updateSandboxModeInput,
                    onFontSizeChange = appViewModel::updateFontSizeInput,
                    onRefreshLogs = appViewModel::refreshDiagnosticsLog,
                    onClearLogs = appViewModel::clearDiagnosticsLog,
                    onRestartBridge = appViewModel::restartBridge,
                    onCopyLogs = { logs ->
                        clipboardManager.setText(AnnotatedString(logs))
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("日志已复制到剪贴板。")
                        }
                    },
                    onCopyApkDownloadUrl = { url ->
                        clipboardManager.setText(AnnotatedString(url))
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("APK 下载链接已复制到剪贴板。")
                        }
                    },
                    onInstallDebugApk = { url ->
                        coroutineScope.launch {
                            val message = runCatching {
                                onInstallDebugApk(url, uiState.authTokenInput)
                            }.getOrElse { error ->
                                error.localizedMessage ?: "APK 下载或安装启动失败。"
                            }
                            snackbarHostState.showSnackbar(message)
                        }
                    },
                    onBack = { navController.popBackStack() },
                    onNavigateToConnect = {
                        navController.navigate(Routes.Connection) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSessions = {
                        navController.navigate(Routes.Sessions) {
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
