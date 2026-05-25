package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openai.codexmobile.AccountQuotaUiState
import com.openai.codexmobile.model.AccountQuotaWindowSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val QuotaMenuTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")

@Composable
fun AccountQuotaIndicator(
    accountQuota: AccountQuotaUiState,
    buttonTestTag: String? = null,
    menuTestTag: String? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val buttonModifier = if (buttonTestTag != null) {
        modifier.testTag(buttonTestTag)
    } else {
        modifier
    }
    val menuModifier = if (menuTestTag != null) {
        Modifier.testTag(menuTestTag)
    } else {
        Modifier
    }

    Box(modifier = buttonModifier) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(999.dp))
                .clickable { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuotaDot(
                    window = accountQuota.snapshot?.fiveHours,
                    isLoading = accountQuota.isLoading,
                )
                QuotaDot(
                    window = accountQuota.snapshot?.oneWeek,
                    isLoading = accountQuota.isLoading,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = menuModifier.widthIn(min = 236.dp, max = 280.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "剩余额度",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            accountQuota.isLoading && accountQuota.snapshot == null -> "读取中"
                            accountQuota.snapshot == null -> "暂无数据"
                            else -> "已连接"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                QuotaWindowRow(
                    label = "5 小时",
                    window = accountQuota.snapshot?.fiveHours,
                )
                QuotaWindowRow(
                    label = "1 周",
                    window = accountQuota.snapshot?.oneWeek,
                )

                val footer = when {
                    !accountQuota.errorMessage.isNullOrBlank() && accountQuota.snapshot != null ->
                        "${accountQuota.errorMessage} 当前显示上次同步结果。"
                    !accountQuota.errorMessage.isNullOrBlank() -> accountQuota.errorMessage
                    !accountQuota.lastUpdatedAt.isNullOrBlank() -> "更新于 ${formatQuotaTime(accountQuota.lastUpdatedAt)}"
                    else -> null
                }
                if (!footer.isNullOrBlank()) {
                    Text(
                        text = footer,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuotaDot(
    window: AccountQuotaWindowSnapshot?,
    isLoading: Boolean,
) {
    val fillColor = quotaUsageColor(window?.usedPercent)
    val hasData = window != null
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(
                color = when {
                    hasData -> fillColor
                    isLoading -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)
                    else -> Color.Transparent
                },
                shape = CircleShape,
            ),
    ) {
        if (!hasData && !isLoading) {
            Surface(
                modifier = Modifier.size(8.dp),
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {}
        }
    }
}

@Composable
private fun QuotaWindowRow(
    label: String,
    window: AccountQuotaWindowSnapshot?,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                QuotaDot(window = window, isLoading = false)
            }
            Text(
                text = window?.let { "剩余 ${remainingPercent(it.usedPercent)}%" } ?: "--",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = when {
                    window == null -> "暂无窗口数据"
                    window.resetsAt != null -> "已用 ${window.usedPercent}% · 重置 ${formatQuotaTime(window.resetsAt)}"
                    else -> "已用 ${window.usedPercent}%"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun remainingPercent(usedPercent: Int): Int {
    return (100 - usedPercent).coerceIn(0, 100)
}

@Composable
private fun quotaUsageColor(usedPercent: Int?): Color {
    return when {
        usedPercent == null -> MaterialTheme.colorScheme.outlineVariant
        usedPercent >= 90 -> MaterialTheme.colorScheme.error
        usedPercent >= 70 -> Color(0xFFD36D2D)
        usedPercent >= 40 -> Color(0xFFC58A13)
        else -> Color(0xFF228B5A)
    }
}

private fun formatQuotaTime(value: String): String {
    return runCatching {
        QuotaMenuTimeFormatter.format(Instant.parse(value).atZone(ZoneId.systemDefault()))
    }.getOrDefault(value)
}
