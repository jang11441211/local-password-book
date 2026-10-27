package com.localvault.passwordbook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localvault.passwordbook.data.VaultEntry
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    entry: VaultEntry,
    groupName: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: (String, String) -> Unit
) {
    var revealedPrimary by remember { mutableStateOf(false) }
    var revealedSub by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(revealedPrimary) {
        if (revealedPrimary) {
            delay(20_000)
            revealedPrimary = false
        }
    }
    LaunchedEffect(revealedSub) {
        if (revealedSub) {
            delay(20_000)
            revealedSub = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("密码详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "编辑")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "删除")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                entry.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    groupName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "更新于 ${formatTimestamp(entry.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(Modifier.height(24.dp))
            DetailField(
                label = "账户",
                value = entry.account.ifBlank { "未填写" },
                valueColor = if (entry.account.isBlank()) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                onCopy = if (entry.account.isBlank()) {
                    null
                } else {
                    { onCopy("账户", entry.account) }
                }
            )

            Spacer(Modifier.height(12.dp))
            DetailField(
                label = "主密码（登录密码等）",
                value = if (revealedPrimary) entry.password else "••••••••••••",
                monospace = true,
                trailing = {
                    IconButton(onClick = { revealedPrimary = !revealedPrimary }) {
                        Icon(
                            imageVector = if (revealedPrimary) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (revealedPrimary) "隐藏密码" else "显示密码"
                        )
                    }
                    IconButton(onClick = { onCopy("密码", entry.password) }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "复制密码")
                    }
                }
            )
            Spacer(Modifier.height(6.dp))
            PasswordStrengthIndicator(entry.password)
            if (revealedPrimary) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "20 秒后自动隐藏",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            if (entry.subPassword.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                DetailField(
                    label = "子密码（支付密码等）",
                    value = if (revealedSub) entry.subPassword else "••••••••••••",
                    monospace = true,
                    trailing = {
                        IconButton(onClick = { revealedSub = !revealedSub }) {
                            Icon(
                                imageVector = if (revealedSub) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (revealedSub) "隐藏子密码" else "显示子密码"
                            )
                        }
                        IconButton(onClick = { onCopy("子密码", entry.subPassword) }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "复制子密码")
                        }
                    }
                )
                Spacer(Modifier.height(6.dp))
                PasswordStrengthIndicator(entry.subPassword)
                if (revealedSub) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "20 秒后自动隐藏",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            if (entry.note.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                DetailField(
                    label = "备注",
                    value = entry.note,
                    singleLine = false
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除密码") },
            text = { Text("确定删除「${entry.name}」吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun DetailField(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    monospace: Boolean = false,
    singleLine: Boolean = true,
    onCopy: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                style = if (monospace) {
                    MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                color = valueColor,
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
            if (onCopy != null) {
                IconButton(onClick = onCopy) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "复制")
                }
            }
            trailing?.invoke(this)
        }
    }
}
