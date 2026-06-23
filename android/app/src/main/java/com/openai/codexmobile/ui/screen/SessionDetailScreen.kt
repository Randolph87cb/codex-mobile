package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.openai.codexmobile.AccountQuotaUiState
import com.openai.codexmobile.BackgroundWatchUiState
import com.openai.codexmobile.DraftSessionUiState
import com.openai.codexmobile.PendingImageAttachmentUiState
import com.openai.codexmobile.PendingImageUploadState
import com.openai.codexmobile.PendingApprovalUiState
import com.openai.codexmobile.PendingVideoAttachmentUiState
import com.openai.codexmobile.PendingVideoUploadState
import com.openai.codexmobile.SessionRealtimeUiState
import com.openai.codexmobile.data.ApprovalDecision
import com.openai.codexmobile.model.BridgeConnectionState
import com.openai.codexmobile.model.SessionDetail
import com.openai.codexmobile.ui.TestTags
import com.openai.codexmobile.ui.screen.copyText
import com.openai.codexmobile.ui.screen.prefersExpandedByDefault
import com.openai.codexmobile.ui.screen.summaryLine
import com.openai.codexmobile.ui.screen.buildTranscriptDisplayItems
import com.openai.codexmobile.ui.screen.calculateTranscriptFileDownloadFraction
import com.openai.codexmobile.ui.screen.formatTranscriptFileByteCount
import com.openai.codexmobile.ui.screen.MarkdownTextBlock
import com.openai.codexmobile.ui.screen.rememberTranscriptImageState
import com.openai.codexmobile.ui.screen.saveTranscriptFile
import com.openai.codexmobile.ui.screen.saveTranscriptImage
import com.openai.codexmobile.ui.screen.TranscriptPreviewMaxDimension
import com.openai.codexmobile.ui.screen.TranscriptThumbnailMaxDimension
import com.openai.codexmobile.ui.theme.codexTranscriptColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private enum class SessionConfigEditor {
    Cwd,
    Model,
    ReasoningEffort,
    ServiceTier,
    SandboxMode,
}

private data class ImagePreviewState(
    val title: String,
    val source: String,
)

private data class FileDownloadDialogState(
    val request: TranscriptFileDownloadRequest,
    val progress: TranscriptFileDownloadProgress,
    val isRunning: Boolean = true,
    val resultMessage: String? = null,
    val errorMessage: String? = null,
)

private data class IndexedTranscriptImage(
    val index: Int,
    val part: TranscriptPart.Image,
)

private enum class StatusStripNoticeTone {
    Info,
    Warning,
    Error,
}

private data class StatusStripNotice(
    val title: String?,
    val message: String,
    val icon: ImageVector,
    val tone: StatusStripNoticeTone,
)

private val TranscriptInlineImageWidth = 92.dp
private val TranscriptInlineImageHeight = 118.dp
private val PendingImagePreviewWidth = 92.dp
private val PendingImagePreviewHeight = 104.dp
private val PendingAttachmentMetaTextStyle = TextStyle(
    fontSize = 8.sp,
    lineHeight = 10.sp,
)
private val SessionDetailPanelShape = RoundedCornerShape(16.dp)
private val ConversationAvatarSize = 40.dp
private val ConversationAvatarGap = 10.dp
private val ConversationBubbleMaxWidth = 560.dp
private val CodexAvatarContainer = Color(0xFF2D4B73)
private val UserAvatarContainer = Color(0xFFD94F4F)
private val MessageMenuContainer = Color(0xFF2E3132)
private val MessageMenuContent = Color(0xFFF0F1F2)

@Composable
fun SessionDetailScreen(
    sessionDetail: SessionDetail?,
    draftSession: DraftSessionUiState?,
    connectionState: BridgeConnectionState = BridgeConnectionState.Disconnected,
    accountQuota: AccountQuotaUiState = AccountQuotaUiState(),
    sessionRealtimeState: SessionRealtimeUiState,
    backgroundWatch: BackgroundWatchUiState = BackgroundWatchUiState(),
    queuedInputs: List<String>,
    draftMessage: String,
    pendingImageAttachments: List<PendingImageAttachmentUiState>,
    pendingVideoAttachments: List<PendingVideoAttachmentUiState>,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    isLoading: Boolean,
    onDraftMessageChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onRemovePendingImageAttachment: (String) -> Unit,
    onRetryPendingImageAttachment: (String) -> Unit,
    onRemovePendingVideoAttachment: (String) -> Unit,
    onRetryPendingVideoAttachment: (String) -> Unit,
    onSend: () -> Unit,
    onInterrupt: () -> Unit = {},
    onApprovalDecision: (ApprovalDecision) -> Unit,
    onUpdateCwd: (String) -> Unit,
    onRenameSessionTitle: (String) -> Unit = {},
    onUpdateModel: (String) -> Unit,
    onUpdateReasoningEffort: (String) -> Unit,
    onUpdateServiceTier: (String) -> Unit,
    onUpdateSandboxMode: (String) -> Unit,
    onUpdateGoal: (String, Long?) -> Unit = { _, _ -> },
    onPauseGoal: () -> Unit = {},
    onResumeGoal: () -> Unit = {},
    onClearGoal: () -> Unit = {},
    onRefreshSession: () -> Unit,
    onShowMessage: (String) -> Unit,
    transcriptScrollState: ScrollState? = null,
    autoScrollTranscript: Boolean = true,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    showTopBar: Boolean = true,
    paddingValues: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val detail = remember(sessionDetail, draftSession) {
        sessionDetail ?: draftSession?.toDraftDetail()
    }
    val currentTranscriptScrollState = transcriptScrollState ?: rememberScrollState()
    var activeEditor by rememberSaveable { mutableStateOf<SessionConfigEditor?>(null) }
    var goalEditorVisible by rememberSaveable { mutableStateOf(false) }
    var goalManagerVisible by rememberSaveable { mutableStateOf(false) }
    var queuedDialogVisible by rememberSaveable { mutableStateOf(false) }
    var renameDialogVisible by rememberSaveable { mutableStateOf(false) }
    var previousTranscriptScrollMax by remember { mutableIntStateOf(0) }
    var imagePreviewState by remember { mutableStateOf<ImagePreviewState?>(null) }
    var pendingFileDownloadRequest by remember { mutableStateOf<TranscriptFileDownloadRequest?>(null) }
    var fileDownloadDialogState by remember { mutableStateOf<FileDownloadDialogState?>(null) }
    var activeSelectionBubbleTag by rememberSaveable(detail?.id) { mutableStateOf<String?>(null) }
    var activeSelectionBubbleBounds by remember { mutableStateOf<Rect?>(null) }
    var contentBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    val hasPendingUploadBlockers = pendingImageAttachments.any {
        it.uploadState == PendingImageUploadState.Uploading || it.uploadState == PendingImageUploadState.Failed
    } || pendingVideoAttachments.any {
        it.uploadState == PendingVideoUploadState.Uploading || it.uploadState == PendingVideoUploadState.Failed
    }
    val copyToClipboard: (String, String) -> Unit = remember(clipboardManager, onShowMessage) {
        { content, successMessage ->
            clipboardManager.setText(AnnotatedString(content))
            onShowMessage(successMessage)
        }
    }

    LaunchedEffect(
        detail?.transcriptPreview,
        sessionRealtimeState.lastEventText,
        sessionRealtimeState.pendingApproval?.requestId?.toString(),
        autoScrollTranscript,
    ) {
        if (!autoScrollTranscript) {
            previousTranscriptScrollMax = currentTranscriptScrollState.maxValue
            return@LaunchedEffect
        }
        val shouldAutoScroll = previousTranscriptScrollMax == 0 ||
            previousTranscriptScrollMax - currentTranscriptScrollState.value <= 32
        if (shouldAutoScroll) {
            currentTranscriptScrollState.animateScrollTo(currentTranscriptScrollState.maxValue)
        }
        previousTranscriptScrollMax = currentTranscriptScrollState.maxValue
    }
    LaunchedEffect(activeSelectionBubbleTag) {
        if (activeSelectionBubbleTag == null) {
            activeSelectionBubbleBounds = null
        }
    }

    ConfigEditorDialogs(
        activeEditor = activeEditor,
        detail = detail,
        onDismiss = { activeEditor = null },
        onUpdateCwd = onUpdateCwd,
        onUpdateModel = onUpdateModel,
        onUpdateReasoningEffort = onUpdateReasoningEffort,
        onUpdateServiceTier = onUpdateServiceTier,
        onUpdateSandboxMode = onUpdateSandboxMode,
    )

    if (goalEditorVisible && detail != null && draftSession == null) {
        GoalEditorDialog(
            currentGoal = detail.goal,
            onDismiss = { goalEditorVisible = false },
            onConfirm = { objective, tokenBudget ->
                onUpdateGoal(objective, tokenBudget)
                goalEditorVisible = false
            },
        )
    }
    if (goalManagerVisible) {
        GoalManagerDialog(
            detail = detail,
            isDraft = draftSession != null,
            isLoading = isLoading,
            onDismiss = { goalManagerVisible = false },
            onEditGoal = {
                goalManagerVisible = false
                goalEditorVisible = true
            },
            onPauseGoal = onPauseGoal,
            onResumeGoal = onResumeGoal,
            onClearGoal = onClearGoal,
        )
    }
    if (queuedDialogVisible) {
        QueuedInputsDialog(
            messages = queuedInputs,
            onDismiss = { queuedDialogVisible = false },
        )
    }
    if (renameDialogVisible && sessionDetail != null && draftSession == null) {
        RenameSessionDialog(
            currentTitle = sessionDetail.title,
            onDismiss = { renameDialogVisible = false },
            onConfirm = { nextTitle ->
                onRenameSessionTitle(nextTitle)
                renameDialogVisible = false
            },
        )
    }

    imagePreviewState?.let { preview ->
        ImagePreviewDialog(
            preview = preview,
            bridgeEndpoint = bridgeEndpoint,
            bridgeAuthToken = bridgeAuthToken,
            onDismiss = { imagePreviewState = null },
            onShowMessage = onShowMessage,
        )
    }
    pendingFileDownloadRequest?.let { request ->
        FileDownloadConfirmDialog(
            request = request,
            onDismiss = { pendingFileDownloadRequest = null },
            onConfirm = {
                pendingFileDownloadRequest = null
                fileDownloadDialogState = FileDownloadDialogState(
                    request = request,
                    progress = TranscriptFileDownloadProgress(
                        displayName = request.displayName,
                        stage = TranscriptFileDownloadStage.Preparing,
                    ),
                )
                coroutineScope.launch {
                    val result = runCatching {
                        saveTranscriptFile(
                            context = context,
                            request = request,
                            bridgeAuthToken = bridgeAuthToken,
                            onProgress = { progress ->
                                fileDownloadDialogState = fileDownloadDialogState?.copy(progress = progress)
                            },
                        )
                    }
                    result.onSuccess { message ->
                        onShowMessage(message)
                        fileDownloadDialogState = fileDownloadDialogState?.copy(
                            progress = fileDownloadDialogState?.progress?.copy(
                                stage = TranscriptFileDownloadStage.Saving,
                            ) ?: TranscriptFileDownloadProgress(
                                displayName = request.displayName,
                                stage = TranscriptFileDownloadStage.Saving,
                            ),
                            isRunning = false,
                            resultMessage = message,
                            errorMessage = null,
                        )
                    }.onFailure { error ->
                        fileDownloadDialogState = fileDownloadDialogState?.copy(
                            isRunning = false,
                            errorMessage = error.message ?: "下载失败。",
                            resultMessage = null,
                        )
                    }
                }
            },
        )
    }
    fileDownloadDialogState?.let { state ->
        FileDownloadProgressDialog(
            state = state,
            onDismiss = { fileDownloadDialogState = null },
        )
    }

    Scaffold(
        modifier = Modifier
            .testTag(TestTags.SessionDetailScreen)
            .fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (showTopBar) {
                DetailTopAppBar(
                    title = title ?: detail?.title ?: if (draftSession != null) "草稿线程" else "会话详情",
                    detail = detail,
                    canRename = sessionDetail != null && draftSession == null,
                    accountQuota = accountQuota,
                    onBack = onBack,
                    onRenameSession = { renameDialogVisible = true },
                    onRefreshSession = onRefreshSession,
                    onOpenEditor = { activeEditor = it },
                )
            }
        },
    ) { detailPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(detailPadding)
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .onGloballyPositioned { coordinates ->
                    contentBoundsInRoot = coordinates.boundsInRoot()
                }
                .pointerInput(activeSelectionBubbleTag, activeSelectionBubbleBounds, contentBoundsInRoot) {
                    if (activeSelectionBubbleTag != null) {
                        awaitEachGesture {
                            val downChange = awaitFirstDown(pass = PointerEventPass.Initial)
                            if (shouldClearTranscriptSelection(
                                    activeBubbleBoundsInRoot = activeSelectionBubbleBounds,
                                    containerBoundsInRoot = contentBoundsInRoot,
                                    touchPositionInContainer = downChange.position,
                                )
                            ) {
                                activeSelectionBubbleTag = null
                            }
                        }
                    }
                },
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusStrip(
                detail = detail,
                connectionState = connectionState,
                sessionRealtimeState = sessionRealtimeState,
                backgroundWatch = backgroundWatch,
                queuedInputs = queuedInputs,
                isDraft = draftSession != null,
                onShowQueued = { queuedDialogVisible = true },
                onShowGoal = { goalManagerVisible = true },
            )
            DetailDateChip(text = if (draftSession != null) "草稿" else "今天")
            Box(
                modifier = Modifier
                    .testTag(TestTags.SessionDetailTranscript)
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .testTag(TestTags.SessionDetailTranscriptScroll)
                        .verticalScroll(currentTranscriptScrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    sessionRealtimeState.pendingApproval?.let { approval ->
                        ApprovalActionCard(
                            approval = approval,
                            onApprovalDecision = onApprovalDecision,
                        )
                    }
                    TranscriptBubbleList(
                        transcript = detail?.transcriptPreview.orEmpty(),
                        liveActivities = sessionRealtimeState.liveExecutionActivities,
                        sessionCwd = detail?.cwd,
                        bridgeEndpoint = bridgeEndpoint,
                        bridgeAuthToken = bridgeAuthToken,
                        activeSelectionBubbleTag = activeSelectionBubbleTag,
                        onActivateTextSelection = { bubbleTag ->
                            activeSelectionBubbleTag = bubbleTag
                        },
                        onClearActiveTextSelection = {
                            activeSelectionBubbleTag = null
                        },
                        onActiveSelectionBoundsChanged = { bounds ->
                            activeSelectionBubbleBounds = bounds
                        },
                        onShowMessage = onShowMessage,
                        onFileDownloadRequest = { request ->
                            pendingFileDownloadRequest = request
                        },
                        onCopyText = { copyToClipboard(it, "内容已复制到剪贴板。") },
                        onCopyCode = { copyToClipboard(it, "代码已复制到剪贴板。") },
                        onOpenImagePreview = { title, source ->
                            imagePreviewState = ImagePreviewState(
                                title = title,
                                source = source,
                            )
                        },
                    )
                    val pendingTrayCount = listOf(
                        pendingImageAttachments.isNotEmpty(),
                        pendingVideoAttachments.isNotEmpty(),
                    ).count { it }
                    val bottomInset = when (pendingTrayCount) {
                        0 -> 104.dp
                        1 -> 190.dp
                        else -> 276.dp
                    }
                    Spacer(modifier = Modifier.height(bottomInset))
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (pendingImageAttachments.isNotEmpty()) {
                PendingImageAttachmentTray(
                    attachments = pendingImageAttachments,
                    bridgeEndpoint = bridgeEndpoint,
                    bridgeAuthToken = bridgeAuthToken,
                    onOpenImagePreview = { attachment ->
                        imagePreviewState = ImagePreviewState(
                            title = attachment.displayName,
                            source = attachment.previewSource,
                        )
                    },
                    onRemoveAttachment = onRemovePendingImageAttachment,
                    onRetryAttachment = onRetryPendingImageAttachment,
                )
            }
            if (pendingVideoAttachments.isNotEmpty()) {
                PendingVideoAttachmentTray(
                    attachments = pendingVideoAttachments,
                    onRemoveAttachment = onRemovePendingVideoAttachment,
                    onRetryAttachment = onRetryPendingVideoAttachment,
                )
            }
            DetailInputDock(
                draftMessage = draftMessage,
                isDraft = draftSession != null,
                isLoading = isLoading,
                canUseInput = detail != null,
                showInterruptButton = draftSession == null && shouldShowInterruptButton(detail, sessionRealtimeState),
                isInterrupting = sessionRealtimeState.isInterrupting,
                hasPendingUploadBlockers = hasPendingUploadBlockers,
                hasPendingAttachments = pendingImageAttachments.isNotEmpty() || pendingVideoAttachments.isNotEmpty(),
                onDraftMessageChange = onDraftMessageChange,
                onPickImage = onPickImage,
                onPickVideo = onPickVideo,
                onSend = onSend,
                onInterrupt = onInterrupt,
            )
        }
    }
    }
}

@Composable
private fun DetailDateChip(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
            )
        }
    }
}

@Composable
private fun DetailInputDock(
    draftMessage: String,
    isDraft: Boolean,
    isLoading: Boolean,
    canUseInput: Boolean,
    showInterruptButton: Boolean,
    isInterrupting: Boolean,
    hasPendingUploadBlockers: Boolean,
    hasPendingAttachments: Boolean,
    onDraftMessageChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onSend: () -> Unit,
    onInterrupt: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val sendEnabled = !isLoading &&
        canUseInput &&
        (draftMessage.isNotBlank() || hasPendingAttachments) &&
        !hasPendingUploadBlockers
    Surface(
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp,
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 7.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalIconButton(
                    onClick = onPickImage,
                    enabled = !isLoading && canUseInput,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag(TestTags.SessionDetailAttachImageButton),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = "添加图片",
                        modifier = Modifier.size(18.dp),
                    )
                }
                FilledTonalIconButton(
                    onClick = onPickVideo,
                    enabled = !isLoading && canUseInput,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag(TestTags.SessionDetailAttachVideoButton),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "添加视频",
                        modifier = Modifier.size(18.dp),
                    )
                }
                OutlinedTextField(
                    value = draftMessage,
                    onValueChange = onDraftMessageChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag(TestTags.SessionDetailDraftField),
                    placeholder = {
                        Text(
                            if (isDraft) {
                                "首条消息发送后才真正创建线程"
                            } else {
                                "输入指令..."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    shape = RoundedCornerShape(22.dp),
                    maxLines = 3,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
                if (showInterruptButton) {
                    FilledTonalIconButton(
                        onClick = onInterrupt,
                        enabled = canUseInput && !isInterrupting,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag(TestTags.SessionDetailInterruptButton),
                    ) {
                        if (isInterrupting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.StopCircle,
                                contentDescription = "中断当前轮",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        onSend()
                    },
                    enabled = sendEnabled,
                    modifier = Modifier
                        .size(46.dp)
                        .testTag(TestTags.SessionDetailSendButton),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (isDraft) "开始" else "发送",
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingVideoAttachmentTray(
    attachments: List<PendingVideoAttachmentUiState>,
    onRemoveAttachment: (String) -> Unit,
    onRetryAttachment: (String) -> Unit,
) {
    val uploadingCount = attachments.count { it.uploadState == PendingVideoUploadState.Uploading }
    val failedCount = attachments.count { it.uploadState == PendingVideoUploadState.Failed }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(18.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "已附加视频（${attachments.size}）",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = when {
                            failedCount > 0 -> "$failedCount 个失败，可直接重试或移除。"
                            uploadingCount > 0 -> "$uploadingCount 个上传中，发送前会等待全部完成。"
                            else -> "视频会作为文件链接发送到会话。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                attachments.forEach { attachment ->
                    PendingVideoAttachmentRow(
                        attachment = attachment,
                        onRemove = onRemoveAttachment,
                        onRetry = onRetryAttachment,
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingVideoAttachmentRow(
    attachment: PendingVideoAttachmentUiState,
    onRemove: (String) -> Unit,
    onRetry: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
    ) {
        Row(
            modifier = Modifier
                .widthIn(min = 156.dp, max = 220.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (attachment.uploadState == PendingVideoUploadState.Failed) {
                    Icons.Filled.Error
                } else {
                    Icons.Filled.PlayArrow
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (attachment.uploadState == PendingVideoUploadState.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = attachment.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = when (attachment.uploadState) {
                        PendingVideoUploadState.Uploading -> "上传中"
                        PendingVideoUploadState.Uploaded -> "已就绪"
                        PendingVideoUploadState.Failed -> attachment.uploadError ?: "上传失败"
                    },
                    style = MaterialTheme.typography.labelSmall.merge(PendingAttachmentMetaTextStyle),
                    color = if (attachment.uploadState == PendingVideoUploadState.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (attachment.uploadState == PendingVideoUploadState.Failed) "重试" else "移除",
                style = MaterialTheme.typography.labelSmall.merge(PendingAttachmentMetaTextStyle),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    if (attachment.uploadState == PendingVideoUploadState.Failed) {
                        onRetry(attachment.localId)
                    } else {
                        onRemove(attachment.localId)
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingImageAttachmentTray(
    attachments: List<PendingImageAttachmentUiState>,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    onOpenImagePreview: (PendingImageAttachmentUiState) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onRetryAttachment: (String) -> Unit,
) {
    val uploadingCount = attachments.count { it.uploadState == PendingImageUploadState.Uploading }
    val failedCount = attachments.count { it.uploadState == PendingImageUploadState.Failed }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.SessionDetailPendingImageCard),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .testTag(TestTags.SessionDetailPendingImageTray),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "已附加图片（${attachments.size}）",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = when {
                            failedCount > 0 -> "$failedCount 张失败，点图查看原图，失败项可直接重试。"
                            uploadingCount > 0 -> "$uploadingCount 张上传中，图片窗口已固定尺寸。"
                            else -> "固定预览窗口，点图查看原图。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LazyRow(
                modifier = Modifier.testTag(TestTags.SessionDetailPendingImageRow),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items = attachments, key = { it.localId }) { attachment ->
                    PendingImageThumbnailCard(
                        attachment = attachment,
                        bridgeEndpoint = bridgeEndpoint,
                        bridgeAuthToken = bridgeAuthToken,
                        onOpen = { onOpenImagePreview(attachment) },
                        onRetry = { onRetryAttachment(attachment.localId) },
                        onRemove = { onRemoveAttachment(attachment.localId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingImageThumbnailCard(
    attachment: PendingImageAttachmentUiState,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.width(PendingImagePreviewWidth),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FixedPreviewImageCard(
                source = attachment.previewSource,
                title = attachment.displayName,
                bridgeEndpoint = bridgeEndpoint,
                bridgeAuthToken = bridgeAuthToken,
                previewHeight = PendingImagePreviewHeight,
                modifier = Modifier
                    .testTag(TestTags.SessionDetailPendingImageThumbnailPrefix + attachment.localId),
                onOpen = onOpen,
                showTitle = false,
            )
            Text(
                text = attachment.displayName,
                style = MaterialTheme.typography.labelSmall.merge(PendingAttachmentMetaTextStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TestTags.SessionDetailPendingImageStatusPrefix + attachment.localId),
                ) {
                    when (attachment.uploadState) {
                        PendingImageUploadState.Uploading -> {
                            CircularProgressIndicator(modifier = Modifier.size(8.dp), strokeWidth = 1.4.dp)
                            Text(
                                "上传中",
                                style = MaterialTheme.typography.labelSmall.merge(PendingAttachmentMetaTextStyle),
                                maxLines = 1,
                            )
                        }

                        PendingImageUploadState.Uploaded -> {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(9.dp),
                            )
                            Text(
                                "已就绪",
                                style = MaterialTheme.typography.labelSmall.merge(PendingAttachmentMetaTextStyle),
                                maxLines = 1,
                            )
                        }

                        PendingImageUploadState.Failed -> {
                            Icon(
                                imageVector = Icons.Filled.Error,
                                contentDescription = null,
                                modifier = Modifier.size(9.dp),
                            )
                            Text(
                                "失败",
                                style = MaterialTheme.typography.labelSmall.merge(PendingAttachmentMetaTextStyle),
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                modifier = Modifier.testTag(
                                    TestTags.SessionDetailPendingImageErrorPrefix + attachment.localId,
                                ),
                            )
                        }
                    }
                }
                Text(
                    text = if (attachment.uploadState == PendingImageUploadState.Failed) "重试" else "移除",
                    style = MaterialTheme.typography.labelSmall.merge(PendingAttachmentMetaTextStyle),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(
                            onClick = if (attachment.uploadState == PendingImageUploadState.Failed) {
                                onRetry
                            } else {
                                onRemove
                            },
                        )
                        .testTag(
                            if (attachment.uploadState == PendingImageUploadState.Failed) {
                                TestTags.SessionDetailPendingImageRetryButtonPrefix + attachment.localId
                            } else {
                                TestTags.SessionDetailClearImageButton + "_" + attachment.localId
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun ConversationHeader(
    detail: SessionDetail?,
    sessionRealtimeState: SessionRealtimeUiState,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = detail?.title ?: "等待会话",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = detail?.subtitle ?: "请先从会话列表中选择一个会话。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GoalMetricChip(
                text = if (sessionRealtimeState.isConnected) "在线" else "快照",
                containerColor = if (sessionRealtimeState.isConnected) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (sessionRealtimeState.isConnected) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = detail?.lastUpdated ?: "等待会话元数据",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopAppBar(
    title: String,
    detail: SessionDetail?,
    canRename: Boolean,
    accountQuota: AccountQuotaUiState,
    onBack: (() -> Unit)?,
    onRenameSession: () -> Unit,
    onRefreshSession: () -> Unit,
    onOpenEditor: (SessionConfigEditor) -> Unit,
) {
    var settingsExpanded by rememberSaveable { mutableStateOf(false) }

    TopAppBar(
        expandedHeight = 54.dp,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.primary,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (canRename) {
                    FilledTonalIconButton(
                        onClick = onRenameSession,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag(TestTags.SessionDetailRenameButton),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "修改线程名称",
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        },
        actions = {
            AccountQuotaIndicator(
                accountQuota = accountQuota,
                buttonTestTag = TestTags.SessionDetailQuotaButton,
                menuTestTag = TestTags.SessionDetailQuotaMenu,
            )
            IconButton(
                onClick = onRefreshSession,
                enabled = detail != null,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "刷新",
                    modifier = Modifier.size(19.dp),
                )
            }
            Box {
                IconButton(
                    onClick = { settingsExpanded = true },
                    enabled = detail != null,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag(TestTags.SessionDetailStatusButton),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "模型设置",
                        modifier = Modifier.size(19.dp),
                    )
                }
                DropdownMenu(
                    expanded = settingsExpanded,
                    onDismissRequest = { settingsExpanded = false },
                    modifier = Modifier
                        .width(330.dp)
                        .testTag(TestTags.SessionDetailStatusDetails),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "模型设置",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        )
                        SessionConfigRow(
                            detail = detail,
                            onOpenEditor = {
                                settingsExpanded = false
                                onOpenEditor(it)
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun RenameSessionDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable(currentTitle) { mutableStateOf(currentTitle) }
    val trimmedValue = value.trim()
    val canConfirm = trimmedValue.isNotEmpty() && trimmedValue != currentTitle.trim()

    AlertDialog(
        modifier = Modifier.testTag(TestTags.SessionDetailRenameDialog),
        onDismissRequest = onDismiss,
        title = { Text("修改线程名称") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "仅修改展示名称，不影响当前线程 ID 和上下文。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("线程名称") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.SessionDetailRenameField),
                    supportingText = {
                        Text("修改后会同步更新当前详情页和线程列表。")
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmedValue) },
                enabled = canConfirm,
                modifier = Modifier.testTag(TestTags.SessionDetailRenameConfirmButton),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun StatusStrip(
    detail: SessionDetail?,
    connectionState: BridgeConnectionState,
    sessionRealtimeState: SessionRealtimeUiState,
    backgroundWatch: BackgroundWatchUiState,
    queuedInputs: List<String>,
    isDraft: Boolean,
    onShowQueued: () -> Unit,
    onShowGoal: () -> Unit,
) {
    val bridgeConnected = connectionState is BridgeConnectionState.Connected
    val bridgeStatusIcon = if (bridgeConnected) {
        Icons.Filled.CloudDone
    } else {
        Icons.Filled.CloudOff
    }
    val syncStatusIcon = when {
        isDraft -> Icons.Filled.CloudOff
        sessionRealtimeState.isConnected -> Icons.Filled.Sensors
        else -> Icons.Filled.CloudDone
    }
    val syncStatusText = when {
        isDraft -> "未创建"
        sessionRealtimeState.isConnected -> "实时流"
        else -> "快照"
    }
    val sessionStatus = detail?.status ?: if (isDraft) "draft" else "idle"
    val sessionStatusText = localizedStatusLabel(sessionStatus)
    val queueIcon = Icons.Filled.MarkChatUnread
    val queueStatusText = if (queuedInputs.isEmpty()) "无排队" else "${queuedInputs.size} 条"
    val goalStatusText = when {
        isDraft -> "待开始"
        detail?.goalCapability == "unsupported" -> "不支持"
        detail?.goal == null -> "未设置"
        else -> localizedGoalStatus(detail.goal.status)
    }
    val backgroundWatchStatusText = backgroundWatch.statusText
    val backgroundWatchHealthy = backgroundWatchStatusText == "后台提醒已开启" ||
        backgroundWatchStatusText == "后台提醒可用"
    val backgroundWatchWarning = backgroundWatchStatusText == "通知权限未开启" ||
        backgroundWatchStatusText == "后台监听中断"
    val backgroundWatchTint = when {
        backgroundWatchStatusText == "后台监听中断" -> MaterialTheme.colorScheme.error
        backgroundWatchStatusText == "通知权限未开启" -> MaterialTheme.colorScheme.tertiary
        backgroundWatchHealthy -> Color(0xFF16A34A)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val backgroundWatchIcon = when {
        backgroundWatchStatusText == "后台监听中断" -> Icons.Filled.Error
        backgroundWatchWarning -> Icons.Filled.Schedule
        backgroundWatchHealthy -> Icons.Filled.CheckCircle
        else -> Icons.Filled.HourglassTop
    }
    val backgroundWatchCompactText = when (backgroundWatchStatusText) {
        "后台提醒已开启", "后台提醒可用" -> "已开启"
        "通知权限未开启" -> "未授权"
        "后台监听中断" -> "中断"
        else -> "待确认"
    }
    val statusNotice = buildStatusStripNotice(
        sessionRealtimeState = sessionRealtimeState,
        sessionStatus = sessionStatus,
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.SessionDetailStatusStrip),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SessionStatusMetric(
                    label = "bridge 状态",
                    value = if (bridgeConnected) "已连接" else "未连接",
                    icon = bridgeStatusIcon,
                    iconTint = if (bridgeConnected) Color(0xFF16A34A) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                StatusMetricDivider()
                SessionStatusMetric(
                    label = "同步方式",
                    value = syncStatusText,
                    icon = syncStatusIcon,
                    iconTint = if (sessionRealtimeState.isConnected) {
                        Color(0xFF16A34A)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                StatusMetricDivider()
                SessionStatusMetric(
                    label = "会话状态",
                    value = sessionStatusText,
                    icon = sessionStatusIcon(sessionStatus),
                    iconTint = sessionStatusTint(sessionStatus),
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SessionStatusMetric(
                    label = "排队消息",
                    value = queueStatusText,
                    icon = queueIcon,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onShowQueued() },
                )
                StatusMetricDivider(height = 28.dp)
                SessionStatusMetric(
                    label = "目标状态",
                    value = goalStatusText,
                    icon = Icons.Filled.Flag,
                    iconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onShowGoal() },
                )
                StatusMetricDivider(height = 28.dp)
                SessionStatusMetric(
                    label = "后台提醒",
                    value = backgroundWatchCompactText,
                    icon = backgroundWatchIcon,
                    iconTint = backgroundWatchTint,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TestTags.SessionDetailBackgroundWatchStatus),
                )
            }
            statusNotice?.let { notice ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                SessionStatusNotice(notice = notice)
            }
        }
    }
}

@Composable
private fun SessionStatusNotice(notice: StatusStripNotice) {
    val (containerColor, contentColor) = when (notice.tone) {
        StatusStripNoticeTone.Info ->
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f) to
                MaterialTheme.colorScheme.onPrimaryContainer
        StatusStripNoticeTone.Warning ->
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f) to
                MaterialTheme.colorScheme.onTertiaryContainer
        StatusStripNoticeTone.Error ->
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f) to
                MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.SessionDetailStatusNotice),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = notice.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(16.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                notice.title?.let { title ->
                    Text(
                        text = title,
                        color = contentColor,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = notice.message,
                    color = contentColor,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalManagerDialog(
    detail: SessionDetail?,
    isDraft: Boolean,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onEditGoal: () -> Unit,
    onPauseGoal: () -> Unit,
    onResumeGoal: () -> Unit,
    onClearGoal: () -> Unit,
) {
    val goalStatusText = when {
        isDraft -> "待开始"
        detail?.goalCapability == "unsupported" -> "不支持"
        detail?.goal == null -> "未设置"
        else -> localizedGoalStatus(detail.goal.status)
    }
    val objectiveText = when {
        detail == null -> "等待会话元数据。"
        isDraft -> "先发送首条消息创建真实线程，再管理目标。"
        detail.goalCapability == "unsupported" -> "当前 host 暂不支持目标模式。"
        detail.goal == null -> "还没有目标。可以给当前线程设置一个持续目标。"
        else -> detail.goal.objective
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理目标", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TranscriptLabelChip(
                        text = goalStatusText,
                        icon = Icons.Filled.PendingActions,
                        containerColor = if (detail?.goal == null) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        contentColor = if (detail?.goal == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                    )
                    detail?.goal?.tokenBudget?.let { budget ->
                        GoalMetricChip(text = "预算 $budget")
                    }
                    detail?.goal?.let { goal ->
                        GoalMetricChip(text = formatTokenUsage(goal.tokensUsed))
                    }
                }
                OutlinedCard(
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                ) {
                    Text(
                        text = objectiveText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    )
                }
                if (!isDraft && detail?.goalCapability != "unsupported") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onEditGoal,
                            enabled = !isLoading && detail != null,
                            modifier = Modifier.testTag(
                                if (detail?.goal == null) {
                                    TestTags.SessionDetailGoalStartButton
                                } else {
                                    TestTags.SessionDetailGoalEditButton
                                },
                            ),
                        ) {
                            Icon(imageVector = Icons.Filled.Flag, contentDescription = null)
                            Text(
                                text = if (detail?.goal == null) "开始目标" else "编辑目标",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        detail?.goal?.let { goal ->
                            val paused = goal.status == "paused"
                            OutlinedButton(
                                onClick = if (paused) onResumeGoal else onPauseGoal,
                                enabled = !isLoading,
                                modifier = Modifier.testTag(
                                    if (paused) {
                                        TestTags.SessionDetailGoalResumeButton
                                    } else {
                                        TestTags.SessionDetailGoalPauseButton
                                    },
                                ),
                            ) {
                                Icon(
                                    imageVector = if (paused) Icons.Filled.Bolt else Icons.Filled.StopCircle,
                                    contentDescription = null,
                                )
                                Text(
                                    text = if (paused) "恢复" else "暂停",
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            OutlinedButton(
                                onClick = onClearGoal,
                                enabled = !isLoading,
                                modifier = Modifier.testTag(TestTags.SessionDetailGoalClearButton),
                            ) {
                                Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                                Text(text = "清除", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        },
    )
}

@Composable
private fun QueuedInputsDialog(
    messages: List<String>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("排队消息", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.testTag(TestTags.SessionDetailQueuedInputsCard),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (messages.isEmpty()) {
                    Text(
                        text = "当前没有排队消息。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    messages.forEachIndexed { index, message ->
                        QueuedItemBlock(index = index, text = message)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("确定")
            }
        },
    )
}

@Composable
private fun QueuedItemBlock(
    index: Int,
    text: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "${index + 1}. $text",
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {}) { Text("编辑") }
                TextButton(onClick = {}) { Text("引导") }
                TextButton(onClick = {}) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalCard(
    detail: SessionDetail?,
    isDraft: Boolean,
    isLoading: Boolean,
    onEditGoal: () -> Unit,
    onPauseGoal: () -> Unit,
    onResumeGoal: () -> Unit,
    onClearGoal: () -> Unit,
) {
    if (detail == null) {
        return
    }
    var expanded by rememberSaveable(detail.id, detail.goal?.objective, isDraft) { mutableStateOf(false) }
    val primaryStatusText = when {
        isDraft -> "待开始"
        detail.goalCapability == "unsupported" -> "不支持"
        detail.goal == null -> "未设置"
        else -> localizedGoalStatus(detail.goal.status)
    }
    val objectiveText = when {
        isDraft -> "先开始一次真实会话，再把长期目标挂到这个线程上。"
        detail.goalCapability == "unsupported" -> "当前 host 暂不支持目标模式。"
        detail.goal == null -> "给当前线程设一个明确目标后，手机端就能持续看到目标状态和预算变化。"
        else -> detail.goal.objective
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.SessionDetailGoalCard),
        shape = SessionDetailPanelShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TranscriptLabelChip(
                    text = "目标",
                    icon = Icons.Filled.Flag,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    compact = true,
                )
                Text(
                    text = primaryStatusText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起目标详情" else "展开目标详情",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(14.dp),
                )
            }
            Text(
                text = objectiveText,
                modifier = Modifier.padding(end = 14.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 13.sp),
                maxLines = if (expanded) 3 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (expanded) {
                detail.goal?.let { goal ->
                    FlowRow(
                        modifier = Modifier.padding(end = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        GoalMetricChip(
                            text = primaryStatusText,
                            containerColor = when (primaryStatusText) {
                                "进行中" -> MaterialTheme.colorScheme.primaryContainer
                                "已完成" -> MaterialTheme.colorScheme.tertiaryContainer
                                "待开始", "未设置" -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.secondaryContainer
                            },
                            contentColor = when (primaryStatusText) {
                                "进行中" -> MaterialTheme.colorScheme.onPrimaryContainer
                                "已完成" -> MaterialTheme.colorScheme.onTertiaryContainer
                                "待开始", "未设置" -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            },
                        )
                        GoalMetricChip(text = formatTokenUsage(goal.tokensUsed))
                        goal.tokenBudget?.let { budget ->
                            GoalMetricChip(text = "预算 $budget")
                        }
                        GoalMetricChip(text = formatGoalDuration(goal.timeUsedSeconds))
                    }
                }
            }

            if (expanded && !isDraft && detail.goalCapability != "unsupported") {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onEditGoal,
                        enabled = !isLoading,
                        modifier = Modifier.testTag(
                            if (detail.goal == null) {
                                TestTags.SessionDetailGoalStartButton
                            } else {
                                TestTags.SessionDetailGoalEditButton
                            },
                        ),
                    ) {
                        Icon(imageVector = Icons.Filled.Flag, contentDescription = null)
                        Text(
                            text = if (detail.goal == null) "开始目标" else "编辑目标",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    detail.goal?.let { goal ->
                        val paused = goal.status == "paused"
                        OutlinedButton(
                            onClick = if (paused) onResumeGoal else onPauseGoal,
                            enabled = !isLoading,
                            modifier = Modifier.testTag(
                                if (paused) {
                                    TestTags.SessionDetailGoalResumeButton
                                } else {
                                    TestTags.SessionDetailGoalPauseButton
                                },
                            ),
                        ) {
                            Icon(
                                imageVector = if (paused) Icons.Filled.Bolt else Icons.Filled.StopCircle,
                                contentDescription = null,
                            )
                            Text(
                                text = if (paused) "恢复" else "暂停",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        OutlinedButton(
                            onClick = onClearGoal,
                            enabled = !isLoading,
                            modifier = Modifier.testTag(TestTags.SessionDetailGoalClearButton),
                        ) {
                            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                            Text(text = "清除", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalMetricChip(
    text: String,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
) {
    val resolvedContainerColor = if (containerColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        containerColor
    }
    val resolvedContentColor = if (contentColor == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        contentColor
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = resolvedContainerColor,
        contentColor = resolvedContentColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
        )
    }
}

@Composable
private fun SessionStatusMetric(
    label: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 34.dp)
            .padding(horizontal = 4.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = iconTint,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                maxLines = 1,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, lineHeight = 12.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatusMetricDivider(height: Dp = 30.dp) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(height)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionConfigRow(
    detail: SessionDetail?,
    onOpenEditor: (SessionConfigEditor) -> Unit,
) {
    if (detail == null) {
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.SessionDetailConfigRow),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SessionConfigButton(
                text = detail.model,
                icon = Icons.Filled.Tune,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TestTags.SessionDetailConfigModelButton),
                onClick = { onOpenEditor(SessionConfigEditor.Model) },
            )
            SessionConfigButton(
                text = "推理 ${localizedReasoning(detail.reasoningEffort)}",
                icon = Icons.Filled.Bolt,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TestTags.SessionDetailConfigReasoningButton),
                onClick = { onOpenEditor(SessionConfigEditor.ReasoningEffort) },
            )
            SessionConfigButton(
                text = "速度 ${localizedService(detail.serviceTier)}",
                icon = Icons.Filled.Speed,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TestTags.SessionDetailConfigServiceTierButton),
                onClick = { onOpenEditor(SessionConfigEditor.ServiceTier) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SessionConfigButton(
                text = detail.cwd.ifBlank { "未配置目录" },
                icon = Icons.Filled.Folder,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TestTags.SessionDetailConfigCwdButton),
                onClick = { onOpenEditor(SessionConfigEditor.Cwd) },
            )
            SessionConfigButton(
                text = "权限 ${localizedSandbox(detail.sandboxMode)}",
                icon = Icons.Filled.Security,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TestTags.SessionDetailConfigSandboxButton),
                onClick = { onOpenEditor(SessionConfigEditor.SandboxMode) },
            )
        }
    }
}

@Composable
private fun SessionConfigButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ConfigEditorDialogs(
    activeEditor: SessionConfigEditor?,
    detail: SessionDetail?,
    onDismiss: () -> Unit,
    onUpdateCwd: (String) -> Unit,
    onUpdateModel: (String) -> Unit,
    onUpdateReasoningEffort: (String) -> Unit,
    onUpdateServiceTier: (String) -> Unit,
    onUpdateSandboxMode: (String) -> Unit,
) {
    val currentDetail = detail ?: return

    when (activeEditor) {
        SessionConfigEditor.Cwd -> TextInputConfigDialog(
            title = "更新目录",
            label = "工作目录",
            initialValue = currentDetail.cwd,
            onDismiss = onDismiss,
            onConfirm = {
                onUpdateCwd(it)
                onDismiss()
            },
        )

        SessionConfigEditor.Model -> TextInputConfigDialog(
            title = "更新模型",
            label = "模型",
            initialValue = currentDetail.model,
            onDismiss = onDismiss,
            onConfirm = {
                onUpdateModel(it)
                onDismiss()
            },
        )

        SessionConfigEditor.ReasoningEffort -> ChoiceConfigDialog(
            title = "选择推理强度",
            options = listOf(
                "minimal" to "极低",
                "low" to "低",
                "medium" to "中",
                "high" to "高",
                "xhigh" to "最高",
            ),
            onDismiss = onDismiss,
            onChoose = {
                onUpdateReasoningEffort(it)
                onDismiss()
            },
        )

        SessionConfigEditor.ServiceTier -> ChoiceConfigDialog(
            title = "选择速度",
            options = listOf(
                "default" to "普通",
                "fast" to "快速",
            ),
            onDismiss = onDismiss,
            onChoose = {
                onUpdateServiceTier(it)
                onDismiss()
            },
        )

        SessionConfigEditor.SandboxMode -> ChoiceConfigDialog(
            title = "选择文件权限",
            options = listOf(
                "read-only" to "只读",
                "workspace-write" to "工作区可写",
                "danger-full-access" to "完全访问",
            ),
            onDismiss = onDismiss,
            onChoose = {
                onUpdateSandboxMode(it)
                onDismiss()
            },
        )
        null -> Unit
    }
}

@Composable
private fun TextInputConfigDialog(
    title: String,
    label: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ChoiceConfigDialog(
    title: String,
    options: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (value, label) ->
                    OutlinedButton(
                        onClick = { onChoose(value) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun GoalEditorDialog(
    currentGoal: com.openai.codexmobile.model.SessionGoalSnapshot?,
    onDismiss: () -> Unit,
    onConfirm: (String, Long?) -> Unit,
) {
    var objective by remember(currentGoal?.objective) { mutableStateOf(currentGoal?.objective.orEmpty()) }
    var tokenBudgetInput by remember(currentGoal?.tokenBudget) {
        mutableStateOf(currentGoal?.tokenBudget?.toString().orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (currentGoal == null) "开始目标" else "编辑目标") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = objective,
                    onValueChange = { objective = it },
                    label = { Text("目标内容") },
                    minLines = 3,
                    maxLines = 5,
                )
                OutlinedTextField(
                    value = tokenBudgetInput,
                    onValueChange = { value ->
                        tokenBudgetInput = value.filter { it.isDigit() }
                    },
                    label = { Text("Token 预算（可选）") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(objective.trim(), tokenBudgetInput.toLongOrNull())
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun QueuedInputCard(
    messages: List<String>,
) {
    val queueScrollState = rememberScrollState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.SessionDetailQueuedInputsCard),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TranscriptLabelChip(
                    text = "排队",
                    icon = Icons.Filled.Schedule,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "排队中的消息（${messages.size}）",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = "当前轮结束后会按顺序自动发送。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .verticalScroll(queueScrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                messages.forEachIndexed { index, message ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Text(
                            text = "${index + 1}. $message",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptBubbleList(
    transcript: String,
    liveActivities: List<com.openai.codexmobile.model.SessionActivityEntry>,
    sessionCwd: String?,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    activeSelectionBubbleTag: String?,
    onActivateTextSelection: (String) -> Unit,
    onClearActiveTextSelection: () -> Unit,
    onActiveSelectionBoundsChanged: (Rect?) -> Unit,
    onShowMessage: (String) -> Unit,
    onFileDownloadRequest: (TranscriptFileDownloadRequest) -> Unit,
    onCopyText: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    onOpenImagePreview: (String, String) -> Unit,
) {
    val items = buildTranscriptDisplayItems(
        transcript = transcript,
        liveActivities = liveActivities,
    )
    if (items.isEmpty()) {
        Text(
            text = "这里会显示会话内容、实时回复和结束状态。",
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { index, item ->
            when (item) {
                is TranscriptDisplayItem.BubbleItem -> TranscriptBubbleCard(
                    bubble = item.bubble,
                    toggleTag = TestTags.SessionDetailTranscriptBubbleTogglePrefix + index,
                    sessionCwd = sessionCwd,
                    bridgeEndpoint = bridgeEndpoint,
                    bridgeAuthToken = bridgeAuthToken,
                    isTextSelectionEnabled = activeSelectionBubbleTag ==
                        (TestTags.SessionDetailTranscriptBubbleTogglePrefix + index),
                    onActivateTextSelection = {
                        onActivateTextSelection(TestTags.SessionDetailTranscriptBubbleTogglePrefix + index)
                    },
                    onClearTextSelection = onClearActiveTextSelection,
                    onTextSelectionBoundsChanged = onActiveSelectionBoundsChanged,
                    onShowMessage = onShowMessage,
                    onFileDownloadRequest = onFileDownloadRequest,
                    onCopyText = onCopyText,
                    onCopyCode = onCopyCode,
                    onOpenImagePreview = onOpenImagePreview,
                )

                is TranscriptDisplayItem.ExecutionGroup -> ExecutionProcessCard(
                    index = index,
                    group = item,
                    sessionCwd = sessionCwd,
                    bridgeEndpoint = bridgeEndpoint,
                    bridgeAuthToken = bridgeAuthToken,
                    onShowMessage = onShowMessage,
                    onFileDownloadRequest = onFileDownloadRequest,
                    onCopyText = onCopyText,
                    onCopyCode = onCopyCode,
                    onOpenImagePreview = onOpenImagePreview,
                )
            }
        }
    }
}

@Composable
private fun TranscriptBubbleCard(
    bubble: TranscriptBubble,
    toggleTag: String,
    sessionCwd: String?,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    isTextSelectionEnabled: Boolean,
    onActivateTextSelection: () -> Unit,
    onClearTextSelection: () -> Unit,
    onTextSelectionBoundsChanged: (Rect?) -> Unit,
    onShowMessage: (String) -> Unit,
    onFileDownloadRequest: (TranscriptFileDownloadRequest) -> Unit,
    onCopyText: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    onOpenImagePreview: (String, String) -> Unit,
) {
    val isUser = bubble.speaker == TranscriptSpeaker.User
    val isCollapsible = !bubble.prefersExpandedByDefault
    var expanded by rememberSaveable(toggleTag, bubble.summaryLine, bubble.prefersExpandedByDefault) {
        mutableStateOf(bubble.prefersExpandedByDefault)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            ConversationSpeakerBadge(isUser = false)
        } else {
            Spacer(modifier = Modifier.size(ConversationAvatarSize))
        }

        Spacer(modifier = Modifier.width(ConversationAvatarGap))

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!isCollapsible) {
                ConversationSpeakerHeader(
                    bubble = bubble,
                    isUser = isUser,
                )
            }
            TranscriptBubbleBodyCard(
                bubble = bubble,
                isUser = isUser,
                isCollapsible = isCollapsible,
                expanded = expanded,
                toggleTag = toggleTag,
                sessionCwd = sessionCwd,
                bridgeEndpoint = bridgeEndpoint,
                bridgeAuthToken = bridgeAuthToken,
                isTextSelectionEnabled = isTextSelectionEnabled,
                onActivateTextSelection = onActivateTextSelection,
                onClearTextSelection = onClearTextSelection,
                onTextSelectionBoundsChanged = onTextSelectionBoundsChanged,
                onShowMessage = onShowMessage,
                onFileDownloadRequest = onFileDownloadRequest,
                onCopyText = onCopyText,
                onCopyCode = onCopyCode,
                onOpenImagePreview = onOpenImagePreview,
                onToggle = {
                    if (isTextSelectionEnabled) {
                        onClearTextSelection()
                    }
                    expanded = !expanded
                },
            )
        }

        Spacer(modifier = Modifier.width(ConversationAvatarGap))

        if (isUser) {
            ConversationSpeakerBadge(isUser = true)
        } else {
            Spacer(modifier = Modifier.size(ConversationAvatarSize))
        }
    }
}

@Composable
private fun TranscriptBubbleBodyCard(
    bubble: TranscriptBubble,
    isUser: Boolean,
    isCollapsible: Boolean,
    expanded: Boolean,
    toggleTag: String,
    sessionCwd: String?,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    isTextSelectionEnabled: Boolean,
    onActivateTextSelection: () -> Unit,
    onClearTextSelection: () -> Unit,
    onTextSelectionBoundsChanged: (Rect?) -> Unit,
    onShowMessage: (String) -> Unit,
    onFileDownloadRequest: (TranscriptFileDownloadRequest) -> Unit,
    onCopyText: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    onOpenImagePreview: (String, String) -> Unit,
    onToggle: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val bodyTag = TestTags.SessionDetailTranscriptBubbleBodyPrefix + toggleTag
    val menuModifier = Modifier.pointerInput(toggleTag, isTextSelectionEnabled) {
        if (!isTextSelectionEnabled) {
            awaitEachGesture {
                val downChange = awaitPointerEvent(PointerEventPass.Initial)
                    .changes
                    .firstOrNull { it.pressed }
                    ?: return@awaitEachGesture
                val pointerId = downChange.id
                val releasedBeforeLongPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var released = false
                    while (!released) {
                        val change = awaitPointerEvent(PointerEventPass.Initial)
                            .changes
                            .firstOrNull { it.id == pointerId }
                        released = change == null || !change.pressed
                    }
                    true
                } ?: false
                if (!releasedBeforeLongPress) {
                    menuExpanded = true
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                    } while (event.changes.any { it.id == pointerId && it.pressed })
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .widthIn(max = ConversationBubbleMaxWidth)
            .testTag(bodyTag)
            .semantics { selected = isTextSelectionEnabled }
            .onGloballyPositioned { coordinates ->
                if (isTextSelectionEnabled) {
                    onTextSelectionBoundsChanged(coordinates.boundsInRoot())
                }
            }
            .then(menuModifier),
        contentAlignment = if (isUser) Alignment.TopEnd else Alignment.TopStart,
    ) {
        Card(
            shape = transcriptBubbleShape(isUser),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, bubble.conversationBubbleBorder()),
        colors = CardDefaults.cardColors(
            containerColor = bubble.conversationBubbleContainer(),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        if (isCollapsible) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TranscriptToggleHeader(
                    bubble = bubble,
                    label = bubble.label,
                    title = bubble.summaryLine,
                    expanded = expanded,
                    toggleTag = toggleTag,
                    onToggle = onToggle,
                )

                if (expanded) {
                    TranscriptPartsColumn(
                        parts = bubble.parts,
                        sessionCwd = sessionCwd,
                        bridgeEndpoint = bridgeEndpoint,
                        bridgeAuthToken = bridgeAuthToken,
                        onShowMessage = onShowMessage,
                        onFileDownloadRequest = onFileDownloadRequest,
                        testTagPrefix = toggleTag,
                        onCopyCode = onCopyCode,
                        onOpenImagePreview = onOpenImagePreview,
                        fillTextWidth = false,
                        textSelectionEnabled = isTextSelectionEnabled,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column {
                    TranscriptPartsColumn(
                        parts = bubble.parts,
                        sessionCwd = sessionCwd,
                        bridgeEndpoint = bridgeEndpoint,
                        bridgeAuthToken = bridgeAuthToken,
                        onShowMessage = onShowMessage,
                        onFileDownloadRequest = onFileDownloadRequest,
                        testTagPrefix = toggleTag,
                        onCopyCode = onCopyCode,
                        onOpenImagePreview = onOpenImagePreview,
                        fillTextWidth = false,
                        textSelectionEnabled = isTextSelectionEnabled,
                    )
                }
            }
        }
    }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            containerColor = MessageMenuContainer,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
        ) {
            DropdownMenuItem(
                text = { Text("复制", color = MessageMenuContent) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        tint = MessageMenuContent,
                    )
                },
                onClick = {
                    menuExpanded = false
                    onCopyText(bubble.copyText)
                    onClearTextSelection()
                },
            )
            DropdownMenuItem(
                text = { Text("选择文本", color = MessageMenuContent) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MessageMenuContent,
                    )
                },
                onClick = {
                    menuExpanded = false
                    onActivateTextSelection()
                    onShowMessage("可拖选文本后复制。")
                },
            )
        }
    }
}

@Composable
private fun ConversationSpeakerHeader(
    bubble: TranscriptBubble,
    isUser: Boolean,
) {
    ConversationSpeakerHeaderText(
        label = bubble.label,
        isUser = isUser,
    )
}

@Composable
private fun ConversationSpeakerHeaderText(
    label: String,
    isUser: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp),
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun transcriptBubbleShape(isUser: Boolean): RoundedCornerShape {
    return RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp,
    )
}

@Composable
private fun TranscriptBubble.conversationBubbleContainer(): Color {
    val transcriptColors = codexTranscriptColors()
    return when (speaker) {
        TranscriptSpeaker.User -> transcriptColors.userBubbleContainer
        TranscriptSpeaker.Assistant -> transcriptColors.assistantBubbleContainer
        TranscriptSpeaker.System -> when (kind) {
            TranscriptBubbleKind.ToolRequest -> transcriptColors.toolBubbleContainer
            TranscriptBubbleKind.ToolResult -> transcriptColors.toolBubbleContainer
            TranscriptBubbleKind.Status,
            TranscriptBubbleKind.Message,
            -> transcriptColors.systemBubbleContainer
        }
    }
}

@Composable
private fun TranscriptBubble.conversationBubbleBorder(): Color {
    val transcriptColors = codexTranscriptColors()
    return when (speaker) {
        TranscriptSpeaker.User -> transcriptColors.userBubbleBorder
        TranscriptSpeaker.Assistant -> transcriptColors.assistantBubbleBorder
        TranscriptSpeaker.System -> transcriptColors.systemBubbleBorder
    }
}

@Composable
private fun ConversationSpeakerBadge(isUser: Boolean) {
    val badgeContainerColor = if (isUser) UserAvatarContainer else CodexAvatarContainer
    val badgeContentColor = Color.White
    Surface(
        shape = CircleShape,
        color = badgeContainerColor,
        contentColor = badgeContentColor,
        modifier = Modifier.size(ConversationAvatarSize),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isUser) {
                Text(
                    text = "你",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 15.sp, lineHeight = 18.sp),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = badgeContentColor,
                )
            } else {
                Text(
                    text = ">_",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 15.sp, lineHeight = 18.sp),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = badgeContentColor,
                )
            }
        }
    }
}

@Composable
private fun TranscriptExternalHeader(
    bubble: TranscriptBubble,
    copyTag: String,
    alignEnd: Boolean,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!alignEnd) {
                TranscriptLabelChip(
                    text = bubble.label,
                    icon = bubble.headerIcon(),
                    containerColor = bubble.headerContainerColor(),
                    contentColor = bubble.headerContentColor(),
                    compact = true,
                    plain = true,
                )
            }
            IconButton(
                onClick = onCopy,
                modifier = Modifier
                    .size(14.dp)
                    .testTag(copyTag),
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "复制消息",
                    modifier = Modifier.size(8.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }
            if (alignEnd) {
                TranscriptLabelChip(
                    text = bubble.label,
                    icon = bubble.headerIcon(),
                    containerColor = bubble.headerContainerColor(),
                    contentColor = bubble.headerContentColor(),
                    compact = true,
                    plain = true,
                )
            }
        }
    }
}

@Composable
private fun ExecutionProcessCard(
    index: Int,
    group: TranscriptDisplayItem.ExecutionGroup,
    sessionCwd: String?,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    onShowMessage: (String) -> Unit,
    onFileDownloadRequest: (TranscriptFileDownloadRequest) -> Unit,
    onCopyText: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    onOpenImagePreview: (String, String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        ConversationSpeakerBadge(isUser = false)
        Spacer(modifier = Modifier.width(ConversationAvatarGap))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ConversationSpeakerHeaderText(
                label = "Codex",
                isUser = false,
            )
            ExecutionProcessBubbleCard(
                index = index,
                group = group,
                sessionCwd = sessionCwd,
                bridgeEndpoint = bridgeEndpoint,
                bridgeAuthToken = bridgeAuthToken,
                onShowMessage = onShowMessage,
                onFileDownloadRequest = onFileDownloadRequest,
                onCopyText = onCopyText,
                onCopyCode = onCopyCode,
                onOpenImagePreview = onOpenImagePreview,
            )
        }
        Spacer(modifier = Modifier.width(ConversationAvatarGap))
        Spacer(modifier = Modifier.size(ConversationAvatarSize))
    }
}

@Composable
private fun ExecutionProcessBubbleCard(
    index: Int,
    group: TranscriptDisplayItem.ExecutionGroup,
    sessionCwd: String?,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    onShowMessage: (String) -> Unit,
    onFileDownloadRequest: (TranscriptFileDownloadRequest) -> Unit,
    onCopyText: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    onOpenImagePreview: (String, String) -> Unit,
) {
    var expanded by rememberSaveable(index, group.summaryLine) { mutableStateOf(false) }
    val transcriptColors = codexTranscriptColors()

    Box(
        modifier = Modifier
            .widthIn(max = ConversationBubbleMaxWidth),
        contentAlignment = Alignment.TopStart,
    ) {
        Card(
            modifier = Modifier.align(Alignment.CenterStart),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, transcriptColors.assistantBubbleBorder),
            colors = CardDefaults.cardColors(
                containerColor = transcriptColors.assistantBubbleContainer,
                contentColor = transcriptColors.bubbleContent,
            ),
        ) {
            Column(
                modifier = Modifier.padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TranscriptToggleHeader(
                    bubble = null,
                    label = "执行过程",
                    title = group.summaryLine,
                    expanded = expanded,
                    toggleTag = TestTags.SessionDetailExecutionGroupTogglePrefix + index,
                    onToggle = { expanded = !expanded },
                )

                if (expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        group.activities.forEachIndexed { activityIndex, bubble ->
                            ExecutionActivityCard(
                                toggleTag = TestTags.SessionDetailExecutionEntryTogglePrefix + "${index}_${activityIndex}",
                                bubble = bubble,
                                sessionCwd = sessionCwd,
                                bridgeEndpoint = bridgeEndpoint,
                                bridgeAuthToken = bridgeAuthToken,
                                onShowMessage = onShowMessage,
                                onFileDownloadRequest = onFileDownloadRequest,
                                onCopyText = onCopyText,
                                onCopyCode = onCopyCode,
                                onOpenImagePreview = onOpenImagePreview,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutionActivityCard(
    toggleTag: String,
    bubble: TranscriptBubble,
    sessionCwd: String?,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    onShowMessage: (String) -> Unit,
    onFileDownloadRequest: (TranscriptFileDownloadRequest) -> Unit,
    onCopyText: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    onOpenImagePreview: (String, String) -> Unit,
) {
    var expanded by rememberSaveable(toggleTag, bubble.summaryLine) { mutableStateOf(false) }
    val transcriptColors = codexTranscriptColors()

    Card(
        border = BorderStroke(1.dp, transcriptColors.systemBubbleBorder.copy(alpha = 0.9f)),
        colors = CardDefaults.cardColors(
            containerColor = transcriptColors.systemBubbleContainer,
            contentColor = transcriptColors.bubbleContent,
        ),
        shape = RoundedCornerShape(11.dp),
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TranscriptToggleHeader(
                bubble = bubble,
                label = bubble.label,
                title = bubble.summaryLine,
                expanded = expanded,
                toggleTag = toggleTag,
                onToggle = { expanded = !expanded },
            )

            if (expanded) {
                TranscriptPartsColumn(
                    parts = bubble.parts,
                    sessionCwd = sessionCwd,
                    bridgeEndpoint = bridgeEndpoint,
                    bridgeAuthToken = bridgeAuthToken,
                    onShowMessage = onShowMessage,
                    onFileDownloadRequest = onFileDownloadRequest,
                    testTagPrefix = toggleTag,
                    onCopyCode = onCopyCode,
                    onOpenImagePreview = onOpenImagePreview,
                )
            }
        }
    }
}

@Composable
private fun TranscriptToggleHeader(
    bubble: TranscriptBubble?,
    label: String,
    title: String,
    expanded: Boolean,
    toggleTag: String,
    onToggle: () -> Unit,
) {
    val transcriptColors = codexTranscriptColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .testTag(toggleTag)
                .clickable(onClick = onToggle),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            TranscriptLabelChip(
                text = label,
                icon = bubble?.headerIcon(),
                containerColor = bubble?.headerContainerColor()
                    ?: transcriptColors.systemBubbleContainer,
                contentColor = bubble?.headerContentColor()
                    ?: transcriptColors.bubbleMutedContent,
                compact = true,
                plain = true,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = transcriptColors.bubbleContent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = if (expanded) {
                Icons.Default.KeyboardArrowUp
            } else {
                Icons.Default.KeyboardArrowDown
            },
            contentDescription = if (expanded) "收起消息" else "展开消息",
            modifier = Modifier.size(10.dp),
        )
    }
}

@Composable
private fun TranscriptStaticHeader(
    bubble: TranscriptBubble,
    label: String,
    title: String?,
    copyTag: String,
    onCopy: () -> Unit,
) {
    val transcriptColors = codexTranscriptColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            TranscriptLabelChip(
                text = label,
                icon = bubble.headerIcon(),
                containerColor = bubble.headerContainerColor(),
                contentColor = bubble.headerContentColor(),
                compact = true,
                plain = true,
            )
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = transcriptColors.bubbleContent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            onClick = onCopy,
            modifier = Modifier
                .size(14.dp)
                .testTag(copyTag),
        ) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "复制消息",
                modifier = Modifier.size(8.dp),
                tint = transcriptColors.bubbleMutedContent.copy(alpha = 0.86f),
            )
        }
    }
}

@Composable
private fun TranscriptPartsColumn(
    parts: List<TranscriptPart>,
    sessionCwd: String?,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    onShowMessage: (String) -> Unit,
    onFileDownloadRequest: (TranscriptFileDownloadRequest) -> Unit,
    testTagPrefix: String,
    onCopyCode: (String) -> Unit,
    onOpenImagePreview: (String, String) -> Unit,
    fillTextWidth: Boolean = true,
    textSelectionEnabled: Boolean = true,
) {
    val transcriptColors = codexTranscriptColors()
    val content: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            val bodyTextStyle = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = 20.sp,
                color = transcriptColors.bubbleContent,
            )
            var index = 0
            while (index < parts.size) {
                val part = parts[index]
                when (part) {
                    is TranscriptPart.Text -> {
                        MarkdownTextBlock(
                            text = part.text,
                            style = bodyTextStyle,
                            bridgeEndpoint = bridgeEndpoint,
                            sessionCwd = sessionCwd,
                            onShowMessage = onShowMessage,
                            onFileDownloadRequest = onFileDownloadRequest,
                            fillWidth = fillTextWidth,
                        )
                        index += 1
                    }

                    is TranscriptPart.Image -> {
                        val images = buildList {
                            var nextIndex = index
                            while (nextIndex < parts.size) {
                                val nextPart = parts[nextIndex]
                                if (nextPart !is TranscriptPart.Image) {
                                    break
                                }
                                add(IndexedTranscriptImage(index = nextIndex, part = nextPart))
                                nextIndex += 1
                            }
                        }
                        TranscriptImageGallery(
                            images = images,
                            bridgeEndpoint = bridgeEndpoint,
                            bridgeAuthToken = bridgeAuthToken,
                            testTagPrefix = testTagPrefix,
                            onOpenImagePreview = onOpenImagePreview,
                        )
                        index += images.size
                    }

                    is TranscriptPart.CodeBlock -> {
                        CodeBlockCard(
                            part = part,
                            copyTag = TestTags.SessionDetailCodeBlockCopyPrefix + "${testTagPrefix}_$index",
                            onCopyCode = onCopyCode,
                        )
                        index += 1
                    }
                }
            }
        }
    }
    if (textSelectionEnabled) {
        SelectionContainer {
            content()
        }
    } else {
        content()
    }
}

@Composable
private fun CodeBlockCard(
    part: TranscriptPart.CodeBlock,
    copyTag: String,
    onCopyCode: (String) -> Unit,
) {
    val transcriptColors = codexTranscriptColors()
    Card(
        border = BorderStroke(1.dp, transcriptColors.codeBlockBorder),
        colors = CardDefaults.cardColors(
            containerColor = transcriptColors.codeBlockContainer,
            contentColor = transcriptColors.codeBlockContent,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                part.language?.let { language ->
                    Text(
                        text = language,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } ?: SpacerWidth()
                IconButton(
                    onClick = { onCopyCode(part.code) },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag(copyTag),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "复制代码",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = part.code,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall.copy(
                    lineHeight = 17.sp,
                    color = transcriptColors.codeBlockContent,
                ),
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun SpacerWidth() {
    Box(modifier = Modifier.size(1.dp))
}

@Composable
private fun TranscriptImageGallery(
    images: List<IndexedTranscriptImage>,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    testTagPrefix: String,
    onOpenImagePreview: (String, String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items = images, key = { it.index }) { image ->
            FixedPreviewImageCard(
                source = image.part.source,
                title = image.part.altText,
                bridgeEndpoint = bridgeEndpoint,
                bridgeAuthToken = bridgeAuthToken,
                previewHeight = TranscriptInlineImageHeight,
                modifier = Modifier
                    .width(TranscriptInlineImageWidth)
                    .testTag(TestTags.SessionDetailTranscriptImagePrefix + "${testTagPrefix}_${image.index}"),
                onOpen = { onOpenImagePreview(image.part.altText, image.part.source) },
                showTitle = false,
                containerColor = MaterialTheme.colorScheme.background,
            )
        }
    }
}

@Composable
private fun FixedPreviewImageCard(
    source: String,
    title: String,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    previewHeight: Dp,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    showTitle: Boolean = true,
    containerColor: Color = Color.Unspecified,
) {
    val resolvedContainerColor = if (containerColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surface
    } else {
        containerColor
    }
    val imageState = rememberTranscriptImageState(
        source = source,
        bridgeEndpoint = bridgeEndpoint,
        bridgeAuthToken = bridgeAuthToken,
        maxDimension = TranscriptThumbnailMaxDimension,
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = resolvedContainerColor),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (imageState) {
                TranscriptImageLoadState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(previewHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }

                is TranscriptImageLoadState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(previewHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(imageVector = Icons.Filled.Image, contentDescription = null)
                            Text(
                                text = imageState.message,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                is TranscriptImageLoadState.Success -> {
                    Image(
                        bitmap = imageState.image.bitmap,
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(previewHeight),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            if (showTitle) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ImagePreviewDialog(
    preview: ImagePreviewState,
    bridgeEndpoint: String,
    bridgeAuthToken: String,
    onDismiss: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val imageState = rememberTranscriptImageState(
        source = preview.source,
        bridgeEndpoint = bridgeEndpoint,
        bridgeAuthToken = bridgeAuthToken,
        maxDimension = TranscriptPreviewMaxDimension,
    )
    var isSaving by remember(preview.source) { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.SessionDetailImagePreviewDialog),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = preview.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                when (imageState) {
                    TranscriptImageLoadState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 220.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    is TranscriptImageLoadState.Error -> {
                        Text(
                            text = imageState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    is TranscriptImageLoadState.Success -> {
                        Image(
                            bitmap = imageState.image.bitmap,
                            contentDescription = preview.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 520.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                    Button(
                        onClick = {
                            val loadedImage = (imageState as? TranscriptImageLoadState.Success)?.image ?: return@Button
                            coroutineScope.launch {
                                isSaving = true
                                val message = runCatching {
                                    saveTranscriptImage(
                                        context = context,
                                        image = loadedImage,
                                        displayName = preview.title,
                                    )
                                }.getOrElse { error ->
                                    error.message ?: "保存图片失败。"
                                }
                                onShowMessage(message)
                                isSaving = false
                            }
                        },
                        enabled = imageState is TranscriptImageLoadState.Success && !isSaving,
                        modifier = Modifier.testTag(TestTags.SessionDetailImagePreviewSaveButton),
                    ) {
                        Text(if (isSaving) "保存中" else "保存")
                    }
                }
            }
        }
    }
}

@Composable
private fun FileDownloadConfirmDialog(
    request: TranscriptFileDownloadRequest,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(TestTags.SessionDetailFileDownloadConfirmDialog),
        onDismissRequest = onDismiss,
        title = { Text("下载文件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("确认下载这个文件吗？")
                Text(
                    text = request.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(TestTags.SessionDetailFileDownloadConfirmButton),
            ) {
                Text("下载")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun FileDownloadProgressDialog(
    state: FileDownloadDialogState,
    onDismiss: () -> Unit,
) {
    val progress = state.progress
    val fraction = calculateTranscriptFileDownloadFraction(progress)
    val title = when {
        state.errorMessage != null -> "下载失败"
        state.resultMessage != null -> "下载完成"
        else -> "正在下载"
    }
    val statusText = when {
        state.errorMessage != null -> state.errorMessage
        state.resultMessage != null -> state.resultMessage
        else -> when (progress.stage) {
            TranscriptFileDownloadStage.Preparing -> "准备中"
            TranscriptFileDownloadStage.Downloading -> "下载中"
            TranscriptFileDownloadStage.Saving -> "保存中"
        }
    }

    Dialog(
        onDismissRequest = {
            if (!state.isRunning) {
                onDismiss()
            }
        },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.SessionDetailFileDownloadProgressDialog),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = state.request.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = statusText ?: "处理中",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.errorMessage != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (state.isRunning) {
                    if (fraction != null) {
                        CircularProgressIndicator(progress = { fraction })
                    } else {
                        CircularProgressIndicator()
                    }
                }
                if (progress.stage == TranscriptFileDownloadStage.Downloading || progress.stage == TranscriptFileDownloadStage.Saving) {
                    Text(
                        text = buildString {
                            append(formatTranscriptFileByteCount(progress.bytesDownloaded))
                            progress.totalBytes?.let { totalBytes ->
                                append(" / ")
                                append(formatTranscriptFileByteCount(totalBytes))
                                if (fraction != null) {
                                    append("  (${(fraction * 100).toInt()}%)")
                                }
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !state.isRunning,
                    ) {
                        Text(if (state.isRunning) "下载中" else "关闭")
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalActionCard(
    approval: PendingApprovalUiState,
    onApprovalDecision: (ApprovalDecision) -> Unit,
) {
    val approvalSummaryScrollState = rememberScrollState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.SessionDetailApprovalCard),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TranscriptLabelChip(
                    text = "审批",
                    icon = Icons.Filled.HourglassTop,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = "待审批操作",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(approvalSummaryScrollState)
                    .testTag(TestTags.SessionDetailApprovalSummary),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = approval.method ?: "未知工具请求",
                    style = MaterialTheme.typography.bodyLarge,
                )
                approval.paramsSummary?.let { summary ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        SelectionContainer {
                            Text(
                                text = summary,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            Button(
                onClick = { onApprovalDecision(ApprovalDecision.Approve) },
                enabled = !approval.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.SessionDetailApproveButton),
            ) {
                Text("批准")
            }
            Button(
                onClick = { onApprovalDecision(ApprovalDecision.ApproveForSession) },
                enabled = !approval.isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.SessionDetailApproveForSessionButton),
            ) {
                Text("本会话都批准")
            }
            OutlinedButton(
                onClick = { onApprovalDecision(ApprovalDecision.Reject) },
                enabled = !approval.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.SessionDetailRejectButton),
            ) {
                Text("拒绝")
            }
            Button(
                onClick = { onApprovalDecision(ApprovalDecision.RejectAndInterrupt) },
                enabled = !approval.isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.SessionDetailRejectAndInterruptButton),
            ) {
                Text("拒绝并中断")
            }
        }
    }
}

@Composable
private fun TranscriptLabelChip(
    text: String,
    icon: ImageVector? = null,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    compact: Boolean = false,
    plain: Boolean = false,
) {
    if (plain) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 9.dp else 12.dp),
                    tint = contentColor.copy(alpha = 0.74f),
                )
            }
            Text(
                text = text,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.76f),
            )
        }
    } else {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = containerColor,
            contentColor = contentColor,
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = if (compact) 7.dp else 9.dp,
                    vertical = if (compact) 3.dp else 5.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(if (compact) 12.dp else 13.dp),
                    )
                }
                Text(
                    text = text,
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun TranscriptBubble.headerIcon(): ImageVector {
    return when (speaker) {
        TranscriptSpeaker.User -> Icons.Filled.Person
        TranscriptSpeaker.Assistant -> Icons.Filled.Bolt
        TranscriptSpeaker.System -> when (kind) {
            TranscriptBubbleKind.ToolRequest -> Icons.Filled.HourglassTop
            TranscriptBubbleKind.ToolResult -> Icons.Filled.CheckCircle
            TranscriptBubbleKind.Status,
            TranscriptBubbleKind.Message,
            -> Icons.Filled.Schedule
        }
    }
}

@Composable
private fun TranscriptBubble.headerContainerColor(): Color {
    val transcriptColors = codexTranscriptColors()
    return when (speaker) {
        TranscriptSpeaker.User -> transcriptColors.userBubbleContainer
        TranscriptSpeaker.Assistant -> transcriptColors.assistantBubbleContainer
        TranscriptSpeaker.System -> when (kind) {
            TranscriptBubbleKind.ToolRequest -> transcriptColors.toolBubbleContainer
            TranscriptBubbleKind.ToolResult -> transcriptColors.toolBubbleContainer
            TranscriptBubbleKind.Status,
            TranscriptBubbleKind.Message,
            -> transcriptColors.systemBubbleContainer
        }
    }
}

@Composable
private fun TranscriptBubble.headerContentColor(): Color {
    val transcriptColors = codexTranscriptColors()
    return when (speaker) {
        TranscriptSpeaker.User,
        TranscriptSpeaker.Assistant,
        TranscriptSpeaker.System,
        -> transcriptColors.bubbleMutedContent
    }
}

private fun DraftSessionUiState.toDraftDetail(): SessionDetail {
    return SessionDetail(
        id = localId,
        title = "新会话（草稿）",
        subtitle = "首条消息发送后创建 • ${localizedApprovalMode(approvalMode)}",
        lastUpdated = "尚未创建",
        transcriptPreview = buildString {
            appendLine("工作目录：$cwd")
            appendLine("模型：$model")
            appendLine("推理强度：${localizedReasoning(reasoningEffort)}")
            appendLine("速度：${localizedService(serviceTier)}")
            appendLine("文件权限：${localizedSandbox(sandboxMode)}")
            append("发送首条消息后，bridge 才会真正创建这个会话。")
        },
        cwd = cwd,
        model = model,
        approvalMode = approvalMode,
        reasoningEffort = reasoningEffort,
        serviceTier = serviceTier,
        sandboxMode = sandboxMode,
        status = "draft",
    )
}

private fun localizedReasoning(value: String): String {
    return when (value) {
        "minimal" -> "极低"
        "low" -> "低"
        "high" -> "高"
        "xhigh" -> "最高"
        else -> "中"
    }
}

private fun localizedService(value: String): String {
    return when (value) {
        "default" -> "普通"
        "fast" -> "快速"
        else -> "普通"
    }
}

private fun localizedApprovalMode(mode: String): String {
    return when (mode) {
        "auto" -> "自动"
        else -> "手动"
    }
}

private fun localizedSandbox(mode: String): String {
    return when (mode) {
        "read-only" -> "只读"
        "danger-full-access" -> "完全访问"
        else -> "工作区可写"
    }
}

private fun shouldShowInterruptButton(
    detail: SessionDetail?,
    sessionRealtimeState: SessionRealtimeUiState,
): Boolean {
    if (detail == null || detail.status == "draft") {
        return false
    }
    return sessionRealtimeState.pendingApproval != null ||
        detail.status == "awaiting_approval" ||
        detail.status == "running"
}

private fun sessionStatusIcon(status: String): ImageVector {
    return when (status) {
        "running" -> Icons.Filled.Bolt
        "awaiting_approval" -> Icons.Filled.PendingActions
        "error" -> Icons.Filled.Error
        else -> Icons.Filled.Schedule
    }
}

@Composable
private fun sessionStatusTint(status: String): Color {
    return when (status) {
        "running" -> Color(0xFF2563EB)
        "awaiting_approval" -> MaterialTheme.colorScheme.tertiary
        "error" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun buildStatusStripNotice(
    sessionRealtimeState: SessionRealtimeUiState,
    sessionStatus: String,
): StatusStripNotice? {
    val message = sessionRealtimeState.fallbackNotice?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    val title = sessionRealtimeState.connectionText
        .takeUnless {
            it.isBlank() ||
                it == "未连接实时流" ||
                it == "已连接实时流"
        }
        ?.takeUnless { it == message }
        ?: sessionRealtimeState.lastEventText
            ?.trim()
            ?.takeUnless { it.isEmpty() || it == message }

    val hintText = listOfNotNull(title, message).joinToString(" ").lowercase()
    val tone = when {
        sessionStatus == "error" ||
            hintText.contains("失败") ||
            hintText.contains("错误") ||
            hintText.contains("鉴权") ||
            hintText.contains("不存在") -> StatusStripNoticeTone.Error
        hintText.contains("重连") ||
            hintText.contains("快照") ||
            hintText.contains("前台") ||
            hintText.contains("断开") -> StatusStripNoticeTone.Warning
        else -> StatusStripNoticeTone.Info
    }
    val icon = when {
        hintText.contains("重启") -> Icons.Filled.Refresh
        tone == StatusStripNoticeTone.Warning -> Icons.Filled.Schedule
        tone == StatusStripNoticeTone.Error -> Icons.Filled.Error
        else -> Icons.Filled.CloudOff
    }

    return StatusStripNotice(
        title = title,
        message = message,
        icon = icon,
        tone = tone,
    )
}

private fun localizedStatusLabel(status: String): String {
    return when (status) {
        "draft" -> "草稿"
        "running" -> "进行中"
        "awaiting_approval" -> "待审批"
        "error" -> "出错"
        else -> "空闲"
    }
}

private fun localizedGoalStatus(status: String): String {
    return when (status) {
        "paused" -> "已暂停"
        "complete" -> "已完成"
        "budgetLimited" -> "预算受限"
        "blocked" -> "已阻塞"
        "usageLimited" -> "额度受限"
        else -> "进行中"
    }
}

private fun formatTokenUsage(tokens: Long): String {
    return when {
        tokens >= 10_000 -> {
            val display = tokens / 1000.0 / 10.0
            "${display}万 tokens"
        }

        else -> "$tokens tokens"
    }
}

private fun formatGoalDuration(seconds: Long): String {
    if (seconds <= 0L) {
        return "0s"
    }
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainSeconds = seconds % 60
    return buildString {
        if (hours > 0) {
            append(hours)
            append("h")
        }
        if (minutes > 0) {
            append(minutes)
            append("m")
        }
        if (remainSeconds > 0 || length == 0) {
            append(remainSeconds)
            append("s")
        }
    }
}
