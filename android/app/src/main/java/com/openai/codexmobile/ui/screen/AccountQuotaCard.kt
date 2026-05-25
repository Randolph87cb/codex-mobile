package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openai.codexmobile.AccountQuotaUiState
import com.openai.codexmobile.model.AccountQuotaWindowSnapshot

@Composable
fun AccountQuotaCard(
    accountQuota: AccountQuotaUiState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "全局额度",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        accountQuota.isLoading && accountQuota.snapshot == null -> "读取中"
                        accountQuota.snapshot == null -> "暂无数据"
                        else -> accountQuota.snapshot.planType ?: "已连接"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuotaWindowBlock(
                    label = "5 小时",
                    window = accountQuota.snapshot?.fiveHours,
                    modifier = Modifier.weight(1f),
                )
                QuotaWindowBlock(
                    label = "1 周",
                    window = accountQuota.snapshot?.oneWeek,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun QuotaWindowBlock(
    label: String,
    window: AccountQuotaWindowSnapshot?,
    modifier: Modifier = Modifier,
) {
    val warning = (window?.usedPercent ?: 0) >= 100
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (warning) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (warning) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = window?.let { "${it.usedPercent}%" } ?: "--",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                fontWeight = FontWeight.Bold,
                color = if (warning) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = when {
                    window == null -> "暂无窗口数据"
                    warning -> "已受限"
                    else -> "当前使用占比"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (warning) {
                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.82f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
