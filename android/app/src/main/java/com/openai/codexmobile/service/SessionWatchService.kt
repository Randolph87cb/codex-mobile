package com.openai.codexmobile.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.openai.codexmobile.MainActivity
import com.openai.codexmobile.R
import com.openai.codexmobile.data.AppSettingsDefaults
import com.openai.codexmobile.data.RealBridgeDataProvider
import com.openai.codexmobile.data.SessionStreamEvent
import com.openai.codexmobile.data.SharedPreferencesAppSettingsStore
import com.openai.codexmobile.data.defaultEndpointForCurrentDevice
import com.openai.codexmobile.diagnostics.FileAppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class SessionWatchService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchJob: Job? = null
    private var activeSessionId: String? = null
    private var presenceActive = false
    private var appInForeground = false
    private var visibleSessionId: String? = null
    private lateinit var appLogger: FileAppLogger
    private val notificationDismissedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_WATCH_NOTIFICATION_DISMISSED) {
                return
            }
            if (!SessionWatchDismissPolicy.shouldRestorePresenceNotification(presenceActive)) {
                return
            }
            appLogger.info(TAG, "常驻通知被用户滑掉，重新显示后台接收通知。")
            startOrUpdateForegroundNotification(activeSessionId)
        }
    }

    override fun onCreate() {
        super.onCreate()
        appLogger = FileAppLogger(applicationContext)
        ensureNotificationChannels()
        ContextCompat.registerReceiver(
            this,
            notificationDismissedReceiver,
            IntentFilter(ACTION_WATCH_NOTIFICATION_DISMISSED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_START_PRESENCE -> {
                presenceActive = true
                startOrUpdateForegroundNotification(activeSessionId)
                START_STICKY
            }
            ACTION_STOP_PRESENCE -> {
                stopServiceCompletely()
                START_NOT_STICKY
            }
            ACTION_UPDATE_VISIBLE_SESSION -> {
                appInForeground = intent.getBooleanExtra(EXTRA_APP_IN_FOREGROUND, false)
                visibleSessionId = intent.getStringExtra(EXTRA_VISIBLE_SESSION_ID)
                    ?.takeIf { it.isNotBlank() }
                START_STICKY
            }
            ACTION_WATCH_SESSION, null -> {
                startSessionWatchFromIntent(intent, startId)
            }
            else -> {
                appLogger.warn(TAG, "收到未知后台监听指令：action=${intent.action}")
                START_NOT_STICKY
            }
        }
    }

    private fun startSessionWatchFromIntent(intent: Intent?, startId: Int): Int {
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID)?.takeIf { it.isNotBlank() }
        if (sessionId == null) {
            appLogger.warn(TAG, "启动后台监听失败：缺少 sessionId。")
            if (!presenceActive) {
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }

        presenceActive = true
        startOrUpdateForegroundNotification(sessionId)
        if (activeSessionId == sessionId && watchJob?.isActive == true) {
            appLogger.info(TAG, "后台监听已在运行：sessionId=$sessionId")
            return START_REDELIVER_INTENT
        }

        watchJob?.cancel()
        activeSessionId = sessionId
        watchJob = serviceScope.launch {
            watchSession(sessionId)
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        watchJob?.cancel()
        serviceScope.cancel()
        runCatching {
            unregisterReceiver(notificationDismissedReceiver)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopServiceCompletely()
        super.onTaskRemoved(rootIntent)
    }

    private suspend fun watchSession(sessionId: String) {
        val provider = RealBridgeDataProvider(appLogger)
        try {
            val settingsStore = SharedPreferencesAppSettingsStore(
                context = applicationContext,
                defaults = AppSettingsDefaults(
                    endpoint = defaultEndpointForCurrentDevice(),
                    cwd = "D:\\workspace\\codex-mobile",
                ),
            )
            val settings = settingsStore.load()
            val endpoint = settings.endpoint.trim()
            if (endpoint.isBlank()) {
                throw IllegalStateException("未配置 bridge 地址，无法后台监听。")
            }

            provider.updateAuthToken(settings.authToken)
            provider.connect(endpoint)
            appLogger.info(TAG, "后台监听已连接 bridge：sessionId=$sessionId")

            provider.observeSessionEvents(sessionId).collect { event ->
                val decision = SessionWatchEventReducer.reduce(event)
                if (decision.result != null) {
                    postResultNotification(
                        sessionId = sessionId,
                        result = resolveResultNotification(provider, sessionId, decision.result),
                    )
                }
                if (decision.stopWatching) {
                    appLogger.info(TAG, "后台监听收到结束事件：sessionId=$sessionId, result=${decision.result}")
                    stopWatching()
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            appLogger.error(TAG, "后台监听中断：sessionId=$sessionId", error)
            postResultNotification(
                sessionId = sessionId,
                result = SessionWatchResult.BackgroundInterrupted(error.message),
            )
            stopWatching()
        }
    }

    private suspend fun resolveResultNotification(
        provider: RealBridgeDataProvider,
        sessionId: String,
        result: SessionWatchResult,
    ): SessionWatchResult {
        if (result != SessionWatchResult.Done) {
            return result
        }

        return try {
            val detail = provider.getSessionDetail(sessionId)
            val summary = detail?.transcriptPreview
                ?.let(SessionWatchNotificationSummary::extractLastCodexReply)
            if (summary.isNullOrBlank()) {
                SessionWatchResult.Done
            } else {
                SessionWatchResult.DoneWithSummary(summary)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            appLogger.warn(
                TAG,
                "补拉会话详情生成通知摘要失败：sessionId=$sessionId, error=${error.message ?: "unknown"}",
            )
            SessionWatchResult.Done
        }
    }

    private fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
        activeSessionId = null
        if (presenceActive) {
            startOrUpdateForegroundNotification(null)
        } else {
            stopServiceCompletely()
        }
    }

    private fun stopServiceCompletely() {
        watchJob?.cancel()
        watchJob = null
        activeSessionId = null
        presenceActive = false
        appInForeground = false
        visibleSessionId = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startOrUpdateForegroundNotification(sessionId: String?) {
        val hasActiveSession = sessionId != null
        val notification = buildNotification(
            channelId = WATCH_CHANNEL_ID,
            sessionId = sessionId,
            title = if (hasActiveSession) "Codex 后台监听中" else "Codex Mobile 正在运行",
            text = if (hasActiveSession) {
                "正在等待 Codex 回复"
            } else {
                "保持连接，线程完成后会提醒你"
            },
            ongoing = true,
            priority = NotificationCompat.PRIORITY_LOW,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                WATCH_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING,
            )
        } else {
            startForeground(WATCH_NOTIFICATION_ID, notification)
        }
    }

    private fun postResultNotification(
        sessionId: String,
        result: SessionWatchResult,
    ) {
        if (!SessionWatchNotificationPolicy.shouldPostResultNotification(
                appInForeground = appInForeground,
                visibleSessionId = visibleSessionId,
                resultSessionId = sessionId,
            )
        ) {
            appLogger.info(TAG, "用户正在查看当前会话，跳过结果通知：sessionId=$sessionId, result=$result")
            return
        }
        if (!canPostNotifications()) {
            appLogger.warn(TAG, "通知权限未授予，跳过结果通知：sessionId=$sessionId, result=$result")
            return
        }

        val notification = buildNotification(
            channelId = RESULT_CHANNEL_ID,
            sessionId = sessionId,
            title = result.title,
            text = result.text,
            ongoing = false,
            priority = NotificationCompat.PRIORITY_HIGH,
        )
        try {
            NotificationManagerCompat.from(this).notify(RESULT_NOTIFICATION_ID, notification)
        } catch (error: SecurityException) {
            appLogger.warn(TAG, "发送系统通知失败，可能缺少通知权限：${error.message ?: "unknown"}")
        }
    }

    private fun buildNotification(
        channelId: String,
        sessionId: String?,
        title: String,
        text: String,
        ongoing: Boolean,
        priority: Int,
    ): Notification {
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(buildOpenAppPendingIntent(sessionId))
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(if (ongoing) 0 else NotificationCompat.DEFAULT_ALL)
            .setOnlyAlertOnce(ongoing)
        if (ongoing) {
            builder.setDeleteIntent(buildNotificationDismissedPendingIntent())
        }
        return builder.build()
    }

    private fun buildOpenAppPendingIntent(sessionId: String?): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (sessionId != null) {
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
        }
        return PendingIntent.getActivity(
            this,
            sessionId?.hashCode() ?: OPEN_APP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildNotificationDismissedPendingIntent(): PendingIntent {
        val intent = Intent(ACTION_WATCH_NOTIFICATION_DISMISSED)
            .setPackage(packageName)
        return PendingIntent.getBroadcast(
            this,
            WATCH_DISMISSED_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun ensureNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val watchChannel = NotificationChannel(
            WATCH_CHANNEL_ID,
            "Codex 后台监听",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Codex 会话后台监听常驻状态"
        }
        val resultChannel = NotificationChannel(
            RESULT_CHANNEL_ID,
            "Codex 线程结果提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Codex 线程结束、等待审批、中断和出错提醒"
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(watchChannel)
        notificationManager.createNotificationChannel(resultChannel)
    }

    companion object {
        const val EXTRA_SESSION_ID = "com.openai.codexmobile.extra.SESSION_ID"

        private const val TAG = "SessionWatchService"
        private const val ACTION_START_PRESENCE = "com.openai.codexmobile.action.START_PRESENCE"
        private const val ACTION_STOP_PRESENCE = "com.openai.codexmobile.action.STOP_PRESENCE"
        private const val ACTION_WATCH_SESSION = "com.openai.codexmobile.action.WATCH_SESSION"
        private const val ACTION_UPDATE_VISIBLE_SESSION = "com.openai.codexmobile.action.UPDATE_VISIBLE_SESSION"
        private const val ACTION_WATCH_NOTIFICATION_DISMISSED =
            "com.openai.codexmobile.action.WATCH_NOTIFICATION_DISMISSED"
        private const val EXTRA_APP_IN_FOREGROUND = "com.openai.codexmobile.extra.APP_IN_FOREGROUND"
        private const val EXTRA_VISIBLE_SESSION_ID = "com.openai.codexmobile.extra.VISIBLE_SESSION_ID"
        private const val WATCH_CHANNEL_ID = "codex_session_watch"
        private const val RESULT_CHANNEL_ID = "codex_session_result_alerts"
        private const val WATCH_NOTIFICATION_ID = 4101
        private const val RESULT_NOTIFICATION_ID = 4102
        private const val OPEN_APP_REQUEST_CODE = 4103
        private const val WATCH_DISMISSED_REQUEST_CODE = 4104

        fun startPresence(context: Context) {
            val intent = Intent(context, SessionWatchService::class.java)
                .setAction(ACTION_START_PRESENCE)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopPresence(context: Context) {
            val intent = Intent(context, SessionWatchService::class.java)
                .setAction(ACTION_STOP_PRESENCE)
            context.startService(intent)
        }

        fun updateVisibleSession(
            context: Context,
            appInForeground: Boolean,
            sessionId: String?,
        ) {
            val intent = Intent(context, SessionWatchService::class.java)
                .setAction(ACTION_UPDATE_VISIBLE_SESSION)
                .putExtra(EXTRA_APP_IN_FOREGROUND, appInForeground)
            sessionId?.takeIf { it.isNotBlank() }?.let { visibleSessionId ->
                intent.putExtra(EXTRA_VISIBLE_SESSION_ID, visibleSessionId)
            }
            context.startService(intent)
        }

        fun startWatching(context: Context, sessionId: String) {
            if (sessionId.isBlank()) {
                return
            }
            val intent = Intent(context, SessionWatchService::class.java)
                .setAction(ACTION_WATCH_SESSION)
                .putExtra(EXTRA_SESSION_ID, sessionId)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

internal object SessionWatchDismissPolicy {
    fun shouldRestorePresenceNotification(presenceActive: Boolean): Boolean {
        return presenceActive
    }
}

internal object SessionWatchNotificationPolicy {
    fun shouldPostResultNotification(
        appInForeground: Boolean,
        visibleSessionId: String?,
        resultSessionId: String,
    ): Boolean {
        return !(appInForeground && visibleSessionId == resultSessionId)
    }
}

internal object SessionWatchEventReducer {
    fun reduce(event: SessionStreamEvent): SessionWatchDecision {
        return when (event) {
            is SessionStreamEvent.AssistantDone -> reduceAssistantDone(event)
            is SessionStreamEvent.RunStatus -> reduceRunStatus(event)
            is SessionStreamEvent.RunInterrupted -> SessionWatchDecision(
                result = SessionWatchResult.Interrupted,
                stopWatching = true,
            )
            is SessionStreamEvent.ToolRequest -> SessionWatchDecision(
                result = SessionWatchResult.AwaitingApproval(event.method),
                stopWatching = true,
            )
            is SessionStreamEvent.Error -> SessionWatchDecision(
                result = SessionWatchResult.Error(event.message),
                stopWatching = true,
            )
            else -> SessionWatchDecision.Continue
        }
    }

    private fun reduceAssistantDone(event: SessionStreamEvent.AssistantDone): SessionWatchDecision {
        val status = event.turnStatus?.lowercase()
        val errorMessage = event.errorMessage?.takeIf { it.isNotBlank() }
        return when {
            errorMessage != null -> SessionWatchDecision(
                result = SessionWatchResult.Error(errorMessage),
                stopWatching = true,
            )
            status == "interrupted" || status == "cancelled" || status == "canceled" -> SessionWatchDecision(
                result = SessionWatchResult.Interrupted,
                stopWatching = true,
            )
            status == "error" || status == "failed" -> SessionWatchDecision(
                result = SessionWatchResult.Error(null),
                stopWatching = true,
            )
            else -> SessionWatchDecision(
                result = SessionWatchResult.Done,
                stopWatching = true,
            )
        }
    }

    private fun reduceRunStatus(event: SessionStreamEvent.RunStatus): SessionWatchDecision {
        return when (event.status.lowercase()) {
            "idle" -> SessionWatchDecision(
                result = SessionWatchResult.Done,
                stopWatching = true,
            )
            "error" -> SessionWatchDecision(
                result = SessionWatchResult.Error(null),
                stopWatching = true,
            )
            else -> SessionWatchDecision.Continue
        }
    }
}

internal data class SessionWatchDecision(
    val result: SessionWatchResult?,
    val stopWatching: Boolean,
) {
    companion object {
        val Continue = SessionWatchDecision(result = null, stopWatching = false)
    }
}

internal sealed class SessionWatchResult(
    val title: String,
    val text: String,
) {
    data object Done : SessionWatchResult(
        title = "Codex 回复已结束",
        text = "回复已结束，点按返回 App 查看会话。",
    )

    data class DoneWithSummary(
        val summary: String,
    ) : SessionWatchResult(
        title = "Codex 回复已结束",
        text = summary,
    )

    data class AwaitingApproval(
        val method: String?,
    ) : SessionWatchResult(
        title = "Codex 等待审批",
        text = method?.takeIf { it.isNotBlank() }?.let { "等待审批：$it" }
            ?: "等待审批，请回到 App 处理。",
    )

    data object Interrupted : SessionWatchResult(
        title = "Codex 已中断",
        text = "当前会话运行已中断。",
    )

    data class Error(
        val message: String?,
    ) : SessionWatchResult(
        title = "Codex 出错",
        text = message?.takeIf { it.isNotBlank() } ?: "当前会话运行出错，请回到 App 查看。",
    )

    data class BackgroundInterrupted(
        val reason: String?,
    ) : SessionWatchResult(
        title = "后台监听中断",
        text = reason?.takeIf { it.isNotBlank() } ?: "后台监听已中断，请回到 App 查看最新状态。",
    )
}

internal object SessionWatchNotificationSummary {
    private const val MaxSummaryChars = 180
    private const val MaxSummaryLines = 2
    private val replyMarkers = listOf("Codex：", "Codex:")
    private val blockBoundaryRegex = Regex(
        pattern = """(?m)^\s*(你：|你:|系统：|系统:|等待审批：|审批结果：|Codex：|Codex:)""",
    )
    private val whitespaceRegex = Regex("""\s+""")

    fun extractLastCodexReply(transcript: String): String? {
        val markerMatch = replyMarkers
            .mapNotNull { marker ->
                transcript.lastIndexOf(marker)
                    .takeIf { it >= 0 }
                    ?.let { index -> index to marker }
            }
            .maxByOrNull { it.first }
            ?: return null

        val rawBlock = transcript.substring(markerMatch.first + markerMatch.second.length)
        val boundaryStart = blockBoundaryRegex.find(rawBlock)?.range?.first
        val replyBlock = boundaryStart
            ?.let { rawBlock.substring(0, it) }
            ?: rawBlock
        val lines = replyBlock
            .lineSequence()
            .map { line -> whitespaceRegex.replace(line.trim(), " ") }
            .filter { it.isNotBlank() }
            .take(MaxSummaryLines)
            .toList()

        if (lines.isEmpty()) {
            return null
        }

        return limitSummary(lines.joinToString("\n"))
    }

    private fun limitSummary(value: String): String {
        if (value.length <= MaxSummaryChars) {
            return value
        }
        return value.take(MaxSummaryChars - 3).trimEnd() + "..."
    }
}
