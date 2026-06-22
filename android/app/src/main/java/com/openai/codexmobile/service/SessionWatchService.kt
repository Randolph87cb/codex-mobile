package com.openai.codexmobile.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
    private lateinit var appLogger: FileAppLogger

    override fun onCreate() {
        super.onCreate()
        appLogger = FileAppLogger(applicationContext)
        ensureNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID)?.takeIf { it.isNotBlank() }
        if (sessionId == null) {
            appLogger.warn(TAG, "启动后台监听失败：缺少 sessionId。")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startAsForeground(sessionId)
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
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startAsForeground(sessionId: String) {
        val notification = buildNotification(
            channelId = WATCH_CHANNEL_ID,
            sessionId = sessionId,
            title = "Codex 后台监听中",
            text = "正在等待 Codex 回复",
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
        sessionId: String,
        title: String,
        text: String,
        ongoing: Boolean,
        priority: Int,
    ): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(buildOpenSessionPendingIntent(sessionId))
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(if (ongoing) 0 else NotificationCompat.DEFAULT_ALL)
            .build()
    }

    private fun buildOpenSessionPendingIntent(sessionId: String): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SESSION_ID, sessionId)
        }
        return PendingIntent.getActivity(
            this,
            sessionId.hashCode(),
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
        private const val WATCH_CHANNEL_ID = "codex_session_watch"
        private const val RESULT_CHANNEL_ID = "codex_session_result_alerts"
        private const val WATCH_NOTIFICATION_ID = 4101
        private const val RESULT_NOTIFICATION_ID = 4102

        fun startWatching(context: Context, sessionId: String) {
            if (sessionId.isBlank()) {
                return
            }
            val intent = Intent(context, SessionWatchService::class.java)
                .putExtra(EXTRA_SESSION_ID, sessionId)
            ContextCompat.startForegroundService(context, intent)
        }
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
