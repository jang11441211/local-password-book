package com.localvault.passwordbook.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

private val AUTO_LOCK_OPTIONS = listOf(
    0L to "立即",
    15_000L to "15 秒",
    30_000L to "30 秒",
    60_000L to "1 分钟",
    300_000L to "5 分钟"
)

private fun autoLockLabel(millis: Long): String =
    AUTO_LOCK_OPTIONS.firstOrNull { it.first == millis }?.second ?: "立即"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: VaultUiState,
    onBack: () -> Unit,
    onAutoLockChange: (Long) -> Unit,
    onBiometricEnable: () -> Unit,
    onBiometricDisable: () -> Unit,
    onChangeMasterPassword: (String) -> Unit,
    onManageGroups: () -> Unit,
    onLock: () -> Unit
) {
    var showAutoLockDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            SectionHeader("安全")

            SettingItem(
                title = "自动锁定",
                subtitle = "离开应用超过该时间后自动锁定",
                value = autoLockLabel(state.autoLockMillis),
                onClick = { showAutoLockDialog = true }
            )

            BiometricRow(
                enabled = state.biometricEnabled,
                available = state.biometricAvailable,
                onCheckedChange = { checked ->
                    if (checked) onBiometricEnable() else onBiometricDisable()
                }
            )

            SettingItem(
                title = "修改主密码",
                subtitle = "修改后请牢记新主密码",
                onClick = { showPasswordDialog = true }
            )

            SettingItem(
                title = "分组管理",
                subtitle = "新增、重命名或删除密码分组",
                value = "${state.groups.size} 个分组",
                onClick = onManageGroups
            )

            SettingItem(
                title = "立即锁定",
                subtitle = "清除内存中的密钥并返回锁屏",
                onClick = onLock
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(20.dp))

            SectionHeader("数据与隐私")
            PrivacyNotes()

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(20.dp))

            SectionHeader("关于")
            Text("本地密码本 1.0.0", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "AES-256-GCM 加密 · PBKDF2-HMAC-SHA256 600,000 次迭代 · " +
                    "指纹密钥由 Android Keystore 保护",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showAutoLockDialog) {
        AutoLockDialog(
            current = state.autoLockMillis,
            onDismiss = { showAutoLockDialog = false },
            onSelect = { onAutoLockChange(it) }
        )
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onConfirm = { onChangeMasterPassword(it) }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 10.dp)
    )
}

@Composable
private fun SettingItem(
    title: String,
    subtitle: String,
    value: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun BiometricRow(
    enabled: Boolean,
    available: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("指纹解锁", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                if (available) "使用系统指纹快速解锁" else "本机未录入可用的指纹",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onCheckedChange,
            enabled = available
        )
    }
}

@Composable
private fun PrivacyNotes() {
    val notes = listOf(
        "未申请网络权限，应用无法连接互联网",
        "数据文件位于应用私有目录，并已排除系统备份与设备迁移",
        "密码内容以 AES-256-GCM 加密，主密钥不会写入磁盘",
        "界面禁止截图，最近任务列表中不显示内容",
        "复制的密码会在 30 秒后自动从剪贴板清除"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        notes.forEach {
            Text(
                "· $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AutoLockDialog(
    current: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自动锁定") },
        text = {
            Column {
                AUTO_LOCK_OPTIONS.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelect(value)
                                onDismiss()
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = current == value,
                            onClick = {
                                onSelect(value)
                                onDismiss()
                            }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var revealed by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改主密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "修改后请牢记新主密码，遗忘将无法恢复数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PasswordField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = "新主密码",
                    revealed = revealed,
                    onToggleReveal = { revealed = !revealed },
                    imeAction = ImeAction.Next
                )
                PasswordField(
                    value = confirm,
                    onValueChange = { confirm = it; error = null },
                    label = "确认新主密码",
                    revealed = revealed,
                    onToggleReveal = { revealed = !revealed }
                )
                PasswordStrengthIndicator(password)
                if (error != null) {
                    Text(
                        error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = when {
                        password.length < 8 -> "主密码至少需要 8 位字符"
                        password != confirm -> "两次输入不一致"
                        else -> null
                    }
                    if (error == null) {
                        onConfirm(password)
                        onDismiss()
                    }
                }
            ) {
                Text("确认修改")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
