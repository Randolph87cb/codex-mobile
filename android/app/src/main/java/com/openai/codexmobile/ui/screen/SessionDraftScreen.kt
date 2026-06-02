package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openai.codexmobile.DraftSessionUiState
import com.openai.codexmobile.PendingImageAttachmentUiState
import com.openai.codexmobile.PendingImageUploadState
import com.openai.codexmobile.PendingVideoAttachmentUiState
import com.openai.codexmobile.PendingVideoUploadState
import com.openai.codexmobile.ui.TestTags

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SessionDraftScreen(
    draftSession: DraftSessionUiState?,
    draftMessage: String,
    pendingImageAttachments: List<PendingImageAttachmentUiState>,
    pendingVideoAttachments: List<PendingVideoAttachmentUiState>,
    isLoading: Boolean,
    onDraftMessageChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onRemovePendingImageAttachment: (String) -> Unit,
    onRetryPendingImageAttachment: (String) -> Unit,
    onRemovePendingVideoAttachment: (String) -> Unit,
    onRetryPendingVideoAttachment: (String) -> Unit,
    onSend: () -> Unit,
    onUpdateCwd: (String) -> Unit,
    onUpdateModel: (String) -> Unit,
    onUpdateReasoningEffort: (String) -> Unit,
    onUpdateServiceTier: (String) -> Unit,
    onUpdateSandboxMode: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val hasPendingUploadBlockers = pendingImageAttachments.any {
        it.uploadState == PendingImageUploadState.Uploading || it.uploadState == PendingImageUploadState.Failed
    } || pendingVideoAttachments.any {
        it.uploadState == PendingVideoUploadState.Uploading || it.uploadState == PendingVideoUploadState.Failed
    }
    val canStart = draftSession != null &&
        !isLoading &&
        !hasPendingUploadBlockers &&
        (draftMessage.isNotBlank() || pendingImageAttachments.isNotEmpty() || pendingVideoAttachments.isNotEmpty())

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Codex Mobile",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回线程",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "更多",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = "新会话（草稿）",
                onValueChange = {},
                readOnly = true,
                label = { Text("项目/线程名称") },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Work, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            DraftConfigCard(
                draftSession = draftSession,
                onUpdateCwd = onUpdateCwd,
                onUpdateModel = onUpdateModel,
                onUpdateReasoningEffort = onUpdateReasoningEffort,
                onUpdateServiceTier = onUpdateServiceTier,
                onUpdateSandboxMode = onUpdateSandboxMode,
            )

            DraftPromptCard(
                draftMessage = draftMessage,
                pendingImageAttachments = pendingImageAttachments,
                pendingVideoAttachments = pendingVideoAttachments,
                onDraftMessageChange = onDraftMessageChange,
                onPickImage = onPickImage,
                onPickVideo = onPickVideo,
                onRemovePendingImageAttachment = onRemovePendingImageAttachment,
                onRetryPendingImageAttachment = onRetryPendingImageAttachment,
                onRemovePendingVideoAttachment = onRemovePendingVideoAttachment,
                onRetryPendingVideoAttachment = onRetryPendingVideoAttachment,
            )

            Button(
                onClick = onSend,
                enabled = canStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag(TestTags.SessionDetailSendButton),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.4.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = Color.White,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Start Session",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                    )
                }
            }

            Text(
                text = if (hasPendingUploadBlockers) {
                    "附件上传完成后才能启动会话。"
                } else {
                    "发送首条消息时才会真正创建远端会话。"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            DraftInfoCard()
        }
    }
}

@Composable
private fun DraftConfigCard(
    draftSession: DraftSessionUiState?,
    onUpdateCwd: (String) -> Unit,
    onUpdateModel: (String) -> Unit,
    onUpdateReasoningEffort: (String) -> Unit,
    onUpdateServiceTier: (String) -> Unit,
    onUpdateSandboxMode: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DraftTextFieldBlock(
                label = "目录",
                value = draftSession?.cwd.orEmpty(),
                onValueChange = onUpdateCwd,
                icon = Icons.Filled.Folder,
                testTag = TestTags.SessionDetailConfigCwdButton,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DraftTextFieldBlock(
                    label = "模型",
                    value = draftSession?.model.orEmpty(),
                    onValueChange = onUpdateModel,
                    icon = Icons.Filled.Tune,
                    testTag = TestTags.SessionDetailConfigModelButton,
                    modifier = Modifier.weight(1f),
                )
                DraftSegmentedBlock(
                    label = "速度",
                    icon = Icons.Filled.Speed,
                    currentValue = draftSession?.serviceTier.orEmpty(),
                    options = listOf(
                        DraftOption("快速", "fast", TestTags.SessionDetailConfigServiceTierButton + "_fast"),
                        DraftOption("普通", "default", TestTags.SessionDetailConfigServiceTierButton),
                    ),
                    onValueChange = onUpdateServiceTier,
                    modifier = Modifier.weight(1f),
                )
            }

            DraftSegmentedBlock(
                label = "思考强度",
                icon = Icons.Filled.Tune,
                currentValue = draftSession?.reasoningEffort.orEmpty(),
                options = listOf(
                    DraftOption("极低", "minimal", TestTags.SessionDetailConfigReasoningButton + "_minimal"),
                    DraftOption("低", "low", TestTags.SessionDetailConfigReasoningButton + "_low"),
                    DraftOption("中", "medium", TestTags.SessionDetailConfigReasoningButton),
                    DraftOption("高", "high", TestTags.SessionDetailConfigReasoningButton + "_high"),
                    DraftOption("最高", "xhigh", TestTags.SessionDetailConfigReasoningButton + "_xhigh"),
                ),
                onValueChange = onUpdateReasoningEffort,
            )

            DraftSegmentedBlock(
                label = "文件权限",
                icon = Icons.Filled.Folder,
                currentValue = draftSession?.sandboxMode.orEmpty(),
                options = listOf(
                    DraftOption("只读", "read-only", TestTags.SessionDetailConfigSandboxButton + "_read_only"),
                    DraftOption("工作区可写", "workspace-write", TestTags.SessionDetailConfigSandboxButton + "_workspace"),
                    DraftOption("完全访问", "danger-full-access", TestTags.SessionDetailConfigSandboxButton),
                ),
                onValueChange = onUpdateSandboxMode,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftPromptCard(
    draftMessage: String,
    pendingImageAttachments: List<PendingImageAttachmentUiState>,
    pendingVideoAttachments: List<PendingVideoAttachmentUiState>,
    onDraftMessageChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onRemovePendingImageAttachment: (String) -> Unit,
    onRetryPendingImageAttachment: (String) -> Unit,
    onRemovePendingVideoAttachment: (String) -> Unit,
    onRetryPendingVideoAttachment: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "初始文本",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onPickImage,
                        modifier = Modifier.testTag(TestTags.SessionDetailAttachImageButton),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Image,
                            contentDescription = "添加图片",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onPickVideo,
                        modifier = Modifier.testTag(TestTags.SessionDetailAttachVideoButton),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "添加视频",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = draftMessage,
                onValueChange = onDraftMessageChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(horizontal = 16.dp)
                    .testTag(TestTags.SessionDetailDraftField),
                placeholder = { Text("Type your initial instructions or prompt here...") },
                shape = RoundedCornerShape(12.dp),
                minLines = 5,
            )

            if (pendingImageAttachments.isNotEmpty() || pendingVideoAttachments.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .padding(16.dp)
                        .testTag(TestTags.SessionDetailPendingImageTray),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    pendingImageAttachments.forEach { attachment ->
                        DraftAttachmentCard(
                            attachment = attachment,
                            onRemovePendingImageAttachment = onRemovePendingImageAttachment,
                            onRetryPendingImageAttachment = onRetryPendingImageAttachment,
                        )
                    }
                    pendingVideoAttachments.forEach { attachment ->
                        DraftVideoAttachmentCard(
                            attachment = attachment,
                            onRemovePendingVideoAttachment = onRemovePendingVideoAttachment,
                            onRetryPendingVideoAttachment = onRetryPendingVideoAttachment,
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun DraftTextFieldBlock(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            ),
        )
    }
}

private data class DraftOption(
    val label: String,
    val value: String,
    val testTag: String,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftSegmentedBlock(
    label: String,
    icon: ImageVector,
    currentValue: String,
    options: List<DraftOption>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            options.forEach { option ->
                val active = currentValue == option.value
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable(enabled = !active) { onValueChange(option.value) }
                        .testTag(option.testTag)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.label,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DraftAttachmentCard(
    attachment: PendingImageAttachmentUiState,
    onRemovePendingImageAttachment: (String) -> Unit,
    onRetryPendingImageAttachment: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f))
            .testTag(TestTags.SessionDetailPendingImageCard)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (attachment.uploadState == PendingImageUploadState.Failed) {
                Icons.Filled.Error
            } else {
                Icons.Filled.Image
            },
            contentDescription = null,
            tint = if (attachment.uploadState == PendingImageUploadState.Failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(20.dp),
        )
        Column(
            modifier = Modifier.width(132.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = attachment.displayName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (attachment.uploadState == PendingImageUploadState.Uploading) {
                    CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.4.dp)
                } else if (attachment.uploadState == PendingImageUploadState.Uploaded) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(10.dp),
                    )
                }
                Text(
                    text = when (attachment.uploadState) {
                        PendingImageUploadState.Uploading -> "上传中"
                        PendingImageUploadState.Uploaded -> "已准备"
                        PendingImageUploadState.Failed -> "失败"
                    },
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Text(
            text = if (attachment.uploadState == PendingImageUploadState.Failed) "重试" else "移除",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable {
                    if (attachment.uploadState == PendingImageUploadState.Failed) {
                        onRetryPendingImageAttachment(attachment.localId)
                    } else {
                        onRemovePendingImageAttachment(attachment.localId)
                    }
                }
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

@Composable
private fun DraftVideoAttachmentCard(
    attachment: PendingVideoAttachmentUiState,
    onRemovePendingVideoAttachment: (String) -> Unit,
    onRetryPendingVideoAttachment: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (attachment.uploadState == PendingVideoUploadState.Failed) {
                Icons.Filled.Error
            } else {
                Icons.Filled.PlayArrow
            },
            contentDescription = null,
            tint = if (attachment.uploadState == PendingVideoUploadState.Failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(20.dp),
        )
        Column(
            modifier = Modifier.width(132.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = attachment.displayName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (attachment.uploadState == PendingVideoUploadState.Uploading) {
                    CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.4.dp)
                } else if (attachment.uploadState == PendingVideoUploadState.Uploaded) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(10.dp),
                    )
                }
                Text(
                    text = when (attachment.uploadState) {
                        PendingVideoUploadState.Uploading -> "上传中"
                        PendingVideoUploadState.Uploaded -> "已准备"
                        PendingVideoUploadState.Failed -> "失败"
                    },
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Text(
            text = if (attachment.uploadState == PendingVideoUploadState.Failed) "重试" else "移除",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable {
                if (attachment.uploadState == PendingVideoUploadState.Failed) {
                    onRetryPendingVideoAttachment(attachment.localId)
                } else {
                    onRemovePendingVideoAttachment(attachment.localId)
                }
            },
        )
    }
}

@Composable
private fun DraftInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "i",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Column {
                Text(
                    text = "Session Drafting",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "先配置目录、模型和首条消息；点击 Start Session 后才会通过 bridge 创建远端会话。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}
