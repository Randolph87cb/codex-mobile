package com.openai.codexmobile.ui

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
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.openai.codexmobile.AppViewModel
import com.openai.codexmobile.ui.screen.ConnectionScreen
import com.openai.codexmobile.ui.screen.SessionDetailScreen
import com.openai.codexmobile.ui.screen.SessionListScreen
import com.openai.codexmobile.ui.screen.SettingsScreen

private object Routes {
    const val Connection = "connection"
    const val Sessions = "sessions"
    const val SessionDetail = "session/{sessionId}"
    const val Settings = "settings"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodexMobileApp(appViewModel: AppViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by appViewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(uiState.connectionState, currentRoute) {
        if (uiState.connectionState is com.openai.codexmobile.model.BridgeConnectionState.Connected &&
            currentRoute == Routes.Connection
        ) {
            navController.navigate(Routes.Sessions) {
                popUpTo(Routes.Connection) {
                    inclusive = false
                }
            }
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
                    isLoading = uiState.isLoading,
                    onOpenSession = { sessionId ->
                        navController.navigate("session/$sessionId")
                    },
                    onCreateSession = {
                        appViewModel.createSession()
                        if (currentRoute != Routes.SessionDetail) {
                            navController.navigate(Routes.Sessions)
                        }
                    },
                    onDisconnect = {
                        appViewModel.disconnect()
                        navController.popBackStack(Routes.Connection, inclusive = false)
                    },
                    onOpenSettings = { navController.navigate(Routes.Settings) },
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
                    sessionRealtimeState = uiState.sessionRealtimeState,
                    queuedInputs = uiState.queuedInputs,
                    draftMessage = uiState.draftMessage,
                    isLoading = uiState.isLoading,
                    onDraftMessageChange = appViewModel::updateDraftMessage,
                    onSend = appViewModel::sendInput,
                    onApprovalDecision = appViewModel::submitApproval,
                    onBack = {
                        appViewModel.closeSessionDetail(sessionId)
                        navController.popBackStack()
                    },
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    paddingValues = paddingValues,
                    items = uiState.settingsItems,
                    endpointInput = uiState.endpointInput,
                    authTokenInput = uiState.authTokenInput,
                    cwdInput = uiState.cwdInput,
                    modelInput = uiState.modelInput,
                    approvalModeInput = uiState.approvalModeInput,
                    onEndpointChange = appViewModel::updateEndpointInput,
                    onAuthTokenChange = appViewModel::updateAuthTokenInput,
                    onCwdChange = appViewModel::updateCwdInput,
                    onModelChange = appViewModel::updateModelInput,
                    onApprovalModeChange = appViewModel::updateApprovalModeInput,
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
    val isSessionDetailRoute = currentRoute == Routes.SessionDetail ||
        currentRoute?.startsWith("session/") == true
    val title = when (currentRoute) {
        Routes.Connection -> "连接"
        Routes.Sessions -> "会话"
        Routes.Settings -> "设置"
        else -> "Codex 移动端"
    }
    val resolvedTitle = if (isSessionDetailRoute) "会话详情" else title

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
