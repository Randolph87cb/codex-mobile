package com.openai.codexmobile

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.model.SessionGoalSnapshot
import com.openai.codexmobile.ui.TestTags
import com.openai.codexmobile.ui.screen.SessionDetailScreen
import com.openai.codexmobile.ui.theme.CodexMobileTheme
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class SessionDetailScreenshotTest {
    private val screenshotRootTag = "session_detail_screenshot_root"

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun exportSessionDetailReferenceScreenshots() {
        val portraitImage = createSampleImageDataUrl(
            width = 720,
            height = 1280,
            startColor = AndroidColor.parseColor("#F3D2CA"),
            endColor = AndroidColor.parseColor("#A76D63"),
        )
        val squareImage = createSampleImageDataUrl(
            width = 960,
            height = 960,
            startColor = AndroidColor.parseColor("#D6E3F3"),
            endColor = AndroidColor.parseColor("#567698"),
        )
        val landscapeImage = createSampleImageDataUrl(
            width = 1440,
            height = 900,
            startColor = AndroidColor.parseColor("#E5E9CF"),
            endColor = AndroidColor.parseColor("#69784D"),
        )
        val darkPortraitImage = createSampleImageDataUrl(
            width = 760,
            height = 1280,
            startColor = AndroidColor.parseColor("#D8D0F4"),
            endColor = AndroidColor.parseColor("#594C7E"),
        )

        composeRule.setContent {
            CodexMobileTheme(darkTheme = false) {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(screenshotRootTag),
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = { ScreenshotReferenceTopBar() },
                ) { paddingValues ->
                    SessionDetailScreen(
                        paddingValues = paddingValues,
                        sessionDetail = SessionDetail(
                            id = "session-screenshot",
                            title = "支付系统改造",
                            subtitle = "修复支付回调签名失败并验证图片上传链路",
                            lastUpdated = "10:17 更新",
                            transcriptPreview = """
                                你：请帮我排查支付回调失败，并确认这几张图片的上传展示不要再挡住消息。

                                Codex：我先接入项目环境，再把图片预览改成固定窗口。

                                Codex：已定位到签名串拼接顺序不一致，接下来调整回调验签并复测上传链路。
                            """.trimIndent(),
                            cwd = "D:\\workspace\\codex-mobile",
                            model = "gpt-5.5",
                            approvalMode = "manual",
                            reasoningEffort = "medium",
                            serviceTier = "default",
                            sandboxMode = "workspace-write",
                            status = "running",
                            goalCapability = "supported",
                            goal = SessionGoalSnapshot(
                                objective = "修复支付回调签名失败并验证图片上传链路",
                                status = "active",
                                tokenBudget = 180000L,
                                tokensUsed = 124000L,
                                timeUsedSeconds = 1080L,
                                createdAt = "2026-05-23T00:12:00Z",
                                updatedAt = "2026-05-23T00:30:00Z",
                            ),
                        ),
                        draftSession = null,
                        sessionRealtimeState = SessionRealtimeUiState(
                            isConnected = true,
                            connectionText = "Bridge 已连接",
                            statusText = "正在处理支付回调日志",
                            lastEventText = "最近一条事件：已完成图片上传预检查。",
                        ),
                        queuedInputs = emptyList(),
                        draftMessage = "",
                        pendingImageAttachments = listOf(
                            PendingImageAttachmentUiState(
                                localId = "pending-portrait",
                                displayName = "girl-portrait.png",
                                mimeType = "image/png",
                                previewSource = portraitImage,
                                uploadState = PendingImageUploadState.Uploaded,
                                stagedPath = "D:\\workspace\\codex-mobile\\.tmp\\girl-portrait.png",
                            ),
                            PendingImageAttachmentUiState(
                                localId = "pending-square",
                                displayName = "avatar-square.png",
                                mimeType = "image/png",
                                previewSource = squareImage,
                                uploadState = PendingImageUploadState.Uploaded,
                                stagedPath = "D:\\workspace\\codex-mobile\\.tmp\\avatar-square.png",
                            ),
                            PendingImageAttachmentUiState(
                                localId = "pending-landscape",
                                displayName = "wide-banner.png",
                                mimeType = "image/png",
                                previewSource = landscapeImage,
                                uploadState = PendingImageUploadState.Failed,
                                uploadError = "上传失败，请重试。",
                            ),
                            PendingImageAttachmentUiState(
                                localId = "pending-dark",
                                displayName = "dark-portrait.png",
                                mimeType = "image/png",
                                previewSource = darkPortraitImage,
                                uploadState = PendingImageUploadState.Uploading,
                            ),
                        ),
                        bridgeEndpoint = "",
                        bridgeAuthToken = "",
                        isLoading = false,
                        onDraftMessageChange = {},
                        onPickImage = {},
                        onRemovePendingImageAttachment = {},
                        onRetryPendingImageAttachment = {},
                        onSend = {},
                        onApprovalDecision = {},
                        onUpdateCwd = {},
                        onUpdateModel = {},
                        onUpdateReasoningEffort = {},
                        onUpdateServiceTier = {},
                        onUpdateSandboxMode = {},
                        onRefreshSession = {},
                        onShowMessage = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()

        saveCapture(
            fileName = "session-detail-refactor-full.png",
            bitmap = composeRule.onNodeWithTag(screenshotRootTag).captureToImage().asAndroidBitmap(),
        )
        saveCapture(
            fileName = "session-detail-refactor-pending-tray.png",
            bitmap = composeRule.onNodeWithTag(TestTags.SessionDetailPendingImageTray).captureToImage().asAndroidBitmap(),
        )
    }

    private fun saveCapture(
        fileName: String,
        bitmap: Bitmap,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outputDir = File(context.getExternalFilesDir(null), "test-screenshots").apply { mkdirs() }
        val outputFile = File(outputDir, fileName)
        FileOutputStream(outputFile).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        saveCaptureToDownloads(context, fileName, bitmap)
    }

    private fun saveCaptureToDownloads(
        context: android.content.Context,
        fileName: String,
        bitmap: Bitmap,
    ) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/codex-mobile-ui",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val itemUri = resolver.insert(collection, values) ?: return
        resolver.openOutputStream(itemUri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(itemUri, values, null, null)
    }

    private fun createSampleImageDataUrl(
        width: Int,
        height: Int,
        startColor: Int,
        endColor: Int,
    ): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                startColor,
                endColor,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(90, 255, 255, 255)
        }
        canvas.drawCircle(width * 0.5f, height * 0.35f, minOf(width, height) * 0.18f, circlePaint)
        canvas.drawCircle(width * 0.5f, height * 0.68f, minOf(width, height) * 0.26f, circlePaint)

        val byteStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream)
        val payload = Base64.encodeToString(byteStream.toByteArray(), Base64.NO_WRAP)
        return "data:image/png;base64,$payload"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenshotReferenceTopBar() {
    TopAppBar(
        expandedHeight = 58.dp,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.primary,
        ),
        navigationIcon = {
            IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(text = "支付系统改造", style = MaterialTheme.typography.titleLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(modifier = Modifier.size(6.dp)) {
                        drawCircle(
                            color = Color(0xFF54A66E),
                            radius = size.minDimension / 2,
                        )
                    }
                    Text(
                        text = "在线",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "刷新",
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.OpenInFull,
                    contentDescription = "展开",
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "复制",
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "更多",
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    )
}
