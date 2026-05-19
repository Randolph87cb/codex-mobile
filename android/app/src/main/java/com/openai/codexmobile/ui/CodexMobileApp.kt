package com.openai.codexmobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.openai.codexmobile.AppViewModel
import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.ui.screen.ConnectionScreen
import com.openai.codexmobile.ui.screen.SessionDetailScreen
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodexMobileApp(appViewModel: AppViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by appViewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    prepareImageAttachmentForBridge(context, uri)
                }
            }.onSuccess { prepared ->
                appViewModel.attachPreparedImage(
                    displayName = prepared.displayName,
                    mimeType = prepared.mimeType,
                    contentBase64 = prepared.contentBase64,
                )
            }.onFailure { error ->
                snackbarHostState.showSnackbar(error.message ?: "处理图片失败。")
            }
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(uiState.connectionState, currentRoute) {
        if (uiState.connectionState is BridgeConnectionState.Connected && currentRoute == Routes.Connection) {
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                currentRoute = currentRoute,
                navController = navController,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Routes.Connection,
        ) {
            composable(Routes.Connection) {
                ConnectionScreen(
                    paddingValues = paddingValues,
                    endpoint = uiState.endpointInput,
                    connectionState = uiState.connectionState,
                    isLoading = uiState.isLoading,
                    onEndpointChange = appViewModel::updateEndpointInput,
                    onConnect = appViewModel::connect,
                    onOpenSettings = { navController.navigate(Routes.Settings) },
                )
            }
            composable(Routes.Sessions) {
                SessionListScreen(
                    paddingValues = paddingValues,
                    sessions = uiState.sessions,
                    connectionState = uiState.connectionState,
                    currentCwd = uiState.cwdInput,
                    isLoading = uiState.isLoading,
                    onOpenSession = { sessionId ->
                        navController.navigate("session/$sessionId")
                    },
                    onCreateDraft = { cwd ->
                        appViewModel.startDraftSession(cwd)
                        navController.navigate(Routes.DraftSession)
                    },
                    onDisconnect = {
                        appViewModel.disconnect()
                        navController.popBackStack(Routes.Connection, inclusive = false)
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
                SessionDetailScreen(
                    paddingValues = paddingValues,
                    sessionDetail = null,
                    draftSession = uiState.selectedDraftSession,
                    sessionRealtimeState = uiState.sessionRealtimeState,
                    queuedInputs = uiState.queuedInputs,
                    draftMessage = uiState.draftMessage,
                    pendingImageAttachment = uiState.pendingImageAttachment,
                    isLoading = uiState.isLoading,
                    onDraftMessageChange = appViewModel::updateDraftMessage,
                    onPickImage = { imagePickerLauncher.launch("image/*") },
                    onClearPendingImageAttachment = appViewModel::clearPendingImageAttachment,
                    onSend = appViewModel::sendInput,
                    onApprovalDecision = appViewModel::submitApproval,
                    onUpdateCwd = appViewModel::updateSelectedSessionCwd,
                    onUpdateModel = appViewModel::updateSelectedSessionModel,
                    onUpdateReasoningEffort = appViewModel::updateSelectedSessionReasoningEffort,
                    onUpdateServiceTier = appViewModel::updateSelectedSessionServiceTier,
                    onUpdateSandboxMode = appViewModel::updateSelectedSessionSandboxMode,
                    onRefreshSession = appViewModel::refreshSelectedSession,
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
                    paddingValues = paddingValues,
                    sessionDetail = uiState.selectedSession,
                    draftSession = null,
                    sessionRealtimeState = uiState.sessionRealtimeState,
                    queuedInputs = uiState.queuedInputs,
                    draftMessage = uiState.draftMessage,
                    pendingImageAttachment = uiState.pendingImageAttachment,
                    isLoading = uiState.isLoading,
                    onDraftMessageChange = appViewModel::updateDraftMessage,
                    onPickImage = { imagePickerLauncher.launch("image/*") },
                    onClearPendingImageAttachment = appViewModel::clearPendingImageAttachment,
                    onSend = appViewModel::sendInput,
                    onApprovalDecision = appViewModel::submitApproval,
                    onUpdateCwd = appViewModel::updateSelectedSessionCwd,
                    onUpdateModel = appViewModel::updateSelectedSessionModel,
                    onUpdateReasoningEffort = appViewModel::updateSelectedSessionReasoningEffort,
                    onUpdateServiceTier = appViewModel::updateSelectedSessionServiceTier,
                    onUpdateSandboxMode = appViewModel::updateSelectedSessionSandboxMode,
                    onRefreshSession = appViewModel::refreshSelectedSession,
                )
            }
            composable(Routes.Settings) {
                LaunchedEffect(Unit) {
                    appViewModel.refreshDiagnosticsLog()
                }
                SettingsScreen(
                    paddingValues = paddingValues,
                    items = uiState.settingsItems,
                    endpointInput = uiState.endpointInput,
                    authTokenInput = uiState.authTokenInput,
                    cwdInput = uiState.cwdInput,
                    modelInput = uiState.modelInput,
                    approvalModeInput = uiState.approvalModeInput,
                    reasoningEffortInput = uiState.reasoningEffortInput,
                    serviceTierInput = uiState.serviceTierInput,
                    sandboxModeInput = uiState.sandboxModeInput,
                    diagnosticsLog = uiState.diagnosticsLog,
                    onEndpointChange = appViewModel::updateEndpointInput,
                    onAuthTokenChange = appViewModel::updateAuthTokenInput,
                    onCwdChange = appViewModel::updateCwdInput,
                    onModelChange = appViewModel::updateModelInput,
                    onApprovalModeChange = appViewModel::updateApprovalModeInput,
                    onReasoningEffortChange = appViewModel::updateReasoningEffortInput,
                    onServiceTierChange = appViewModel::updateServiceTierInput,
                    onSandboxModeChange = appViewModel::updateSandboxModeInput,
                    onRefreshLogs = appViewModel::refreshDiagnosticsLog,
                    onClearLogs = appViewModel::clearDiagnosticsLog,
                    onCopyLogs = { logs ->
                        clipboardManager.setText(AnnotatedString(logs))
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("日志已复制到剪贴板。")
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    currentRoute: String?,
    navController: NavHostController,
) {
    val isSessionDetailRoute = currentRoute == Routes.DraftSession ||
        currentRoute == Routes.SessionDetail ||
        currentRoute?.startsWith("session/") == true
    val title = when (currentRoute) {
        Routes.Connection -> "连接"
        Routes.Sessions -> "会话"
        Routes.DraftSession -> "草稿线程"
        Routes.Settings -> "设置"
        else -> "Codex 移动端"
    }
    val resolvedTitle = if (currentRoute?.startsWith("session/") == true) "会话详情" else title

    TopAppBar(
        title = { Text(text = resolvedTitle) },
        navigationIcon = {
            if (isSessionDetailRoute || currentRoute == Routes.Settings) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                    )
                }
            }
        },
        actions = {
            if (currentRoute != Routes.Settings) {
                IconButton(onClick = { navController.navigate(Routes.Settings) }) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "设置",
                    )
                }
            }
        },
    )
}
