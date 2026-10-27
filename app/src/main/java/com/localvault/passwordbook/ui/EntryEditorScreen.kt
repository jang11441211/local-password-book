package com.localvault.passwordbook.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.localvault.passwordbook.data.DEFAULT_GROUP_ID
import com.localvault.passwordbook.data.VaultEntry
import com.localvault.passwordbook.data.VaultGroup
import com.localvault.passwordbook.security.PasswordGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorScreen(
    entry: VaultEntry?,
    groups: List<VaultGroup>,
    onBack: () -> Unit,
    onSave: (
        groupId: String,
        name: String,
        account: String,
        password: String,
        subPassword: String,
        note: String
    ) -> Unit
) {
    var groupId by rememberSaveable(entry?.id) {
        mutableStateOf(entry?.groupId ?: DEFAULT_GROUP_ID)
    }
    var name by rememberSaveable(entry?.id) { mutableStateOf(entry?.name ?: "") }
    var account by rememberSaveable(entry?.id) { mutableStateOf(entry?.account ?: "") }
    var password by rememberSaveable(entry?.id) { mutableStateOf(entry?.password ?: "") }
    var subPassword by rememberSaveable(entry?.id) { mutableStateOf(entry?.subPassword ?: "") }
    var note by rememberSaveable(entry?.id) { mutableStateOf(entry?.note ?: "") }
    var revealed by rememberSaveable { mutableStateOf(false) }
    var subRevealed by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        error = when {
            name.isBlank() -> "请填写名称"
            password.isEmpty() -> "请填写密码"
            else -> null
        }
        if (error == null) {
            onSave(groupId, name.trim(), account.trim(), password, subPassword, note.trim())
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (entry == null) "新增密码" else "编辑密码") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { save() }) {
                        Text("保存")
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "分组",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groups.forEach { group ->
                    FilterChip(
                        selected = groupId == group.id,
                        onClick = { groupId = group.id },
                        label = { Text(group.name) }
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("名称") },
                placeholder = { Text("例如：微信、网银") },
                singleLine = true,
                isError = error == "请填写名称",
                shape = RoundedCornerShape(14.dp)
            )

            OutlinedTextField(
                value = account,
                onValueChange = { account = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("账户") },
                placeholder = { Text("用户名 / 手机号 / 邮箱") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            PasswordField(
                value = password,
                onValueChange = { password = it; error = null },
                label = "密码（登录密码等）",
                revealed = revealed,
                onToggleReveal = { revealed = !revealed },
                isError = error == "请填写密码",
                onGenerate = {
                    password = PasswordGenerator.generate()
                    revealed = true
                }
            )
            PasswordStrengthIndicator(password)

            PasswordField(
                value = subPassword,
                onValueChange = { subPassword = it },
                label = "子密码（支付密码等，可选）",
                revealed = subRevealed,
                onToggleReveal = { subRevealed = !subRevealed },
                imeAction = ImeAction.Next,
                onGenerate = {
                    subPassword = PasswordGenerator.generate()
                    subRevealed = true
                }
            )
            PasswordStrengthIndicator(subPassword)

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注") },
                placeholder = { Text("可选") },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(14.dp)
            )

            if (error != null) {
                Text(
                    error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                "点击密码框右侧的 ✨ 可生成随机强密码",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(Modifier.height(4.dp))
            Text(
                "所有内容都会加密后保存在本机",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
