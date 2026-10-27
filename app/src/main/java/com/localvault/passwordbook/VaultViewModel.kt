package com.localvault.passwordbook

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localvault.passwordbook.data.DEFAULT_GROUP_ID
import com.localvault.passwordbook.data.VaultEnvelope
import com.localvault.passwordbook.data.VaultEntry
import com.localvault.passwordbook.data.VaultGroup
import com.localvault.passwordbook.data.VaultPayload
import com.localvault.passwordbook.data.VaultStore
import com.localvault.passwordbook.data.WrongPasswordException
import com.localvault.passwordbook.security.BiometricKeyInvalidatedException
import com.localvault.passwordbook.security.BiometricKeyStore
import com.localvault.passwordbook.security.VaultCrypto
import com.localvault.passwordbook.ui.Phase
import com.localvault.passwordbook.ui.Screen
import com.localvault.passwordbook.ui.VaultUiState
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.IllegalBlockSizeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val store = VaultStore(application)
    private val clipboardManager =
        application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    /** 仅存在于内存中的数据密钥，锁定时立即清零。 */
    private var dek: ByteArray? = null
    private var envelope: VaultEnvelope? = null
    private var payload: VaultPayload = VaultPayload()

    private var backgroundAt = 0L
    private var clipboardOwner: String? = null
    private var clipboardClearJob: Job? = null
    private val saveMutex = Mutex()

    private val _state = MutableStateFlow(VaultUiState())
    val state: StateFlow<VaultUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    if (store.exists()) LoadResult.Ready(store.read()) else LoadResult.Empty
                } catch (t: Throwable) {
                    LoadResult.Broken
                }
            }
            val available = BiometricKeyStore.isAvailable(application)
            when (loaded) {
                is LoadResult.Ready -> {
                    envelope = loaded.envelope
                    _state.update {
                        it.copy(
                            phase = Phase.Locked,
                            biometricEnabled = loaded.envelope.biometricWrappedKey != null,
                            biometricAvailable = available
                        )
                    }
                }

                LoadResult.Empty -> {
                    _state.update {
                        it.copy(phase = Phase.Setup, biometricAvailable = available)
                    }
                }

                LoadResult.Broken -> {
                    _state.update {
                        it.copy(
                            phase = Phase.Locked,
                            biometricAvailable = available,
                            error = "数据文件损坏或无法读取"
                        )
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 解锁 / 创建

    fun createVault(password: String) {
        validateMasterPassword(password)?.let { showError(it); return }
        if (_state.value.busy) return

        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val chars = password.toCharArray()
                val created = withContext(Dispatchers.Default) {
                    try {
                        VaultCrypto.create(chars)
                    } finally {
                        chars.fill('\u0000')
                    }
                }
                withContext(Dispatchers.IO) { store.write(created.envelope) }

                envelope = created.envelope
                dek?.fill(0)
                dek = created.dek
                payload = created.payload
                _state.update {
                    it.copy(
                        phase = Phase.Unlocked,
                        busy = false,
                        error = null,
                        groups = created.payload.groups,
                        entries = created.payload.entries,
                        autoLockMillis = created.payload.autoLockMillis,
                        biometricEnabled = false,
                        query = "",
                        groupFilter = null,
                        screen = Screen.Vault
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(busy = false, error = "创建失败：${t.readable()}") }
            }
        }
    }

    fun unlockWithPassword(password: String) {
        if (_state.value.busy) return
        val currentEnvelope = envelope
        if (currentEnvelope == null) {
            showError("数据尚未就绪，请稍后重试")
            return
        }

        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val chars = password.toCharArray()
                val unlocked = withContext(Dispatchers.Default) {
                    try {
                        VaultCrypto.openWithPassword(currentEnvelope, chars)
                    } finally {
                        chars.fill('\u0000')
                    }
                }
                enterVault(unlocked.dek, unlocked.payload, biometricEnabled = currentEnvelope.biometricWrappedKey != null)
            } catch (e: WrongPasswordException) {
                _state.update { it.copy(busy = false, error = "主密码错误") }
            } catch (t: Throwable) {
                _state.update { it.copy(busy = false, error = "解锁失败：${t.readable()}") }
            }
        }
    }

    /** 指纹验证成功后调用：用 Keystore 密钥解开 DEK。 */
    fun unlockWithBiometric() {
        if (_state.value.busy) return
        val currentEnvelope = envelope ?: return
        val wrappedKey = currentEnvelope.biometricWrappedKey ?: return
        val nonce = currentEnvelope.biometricNonce ?: return

        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val cipher = BiometricKeyStore.decryptCipher(nonce)
                    val key = try {
                        cipher.doFinal(wrappedKey)
                    } catch (e: AEADBadTagException) {
                        throw BiometricKeyInvalidatedException()
                    }
                    key to VaultCrypto.decryptPayload(currentEnvelope, key)
                }
                enterVault(result.first, result.second, biometricEnabled = true)
            } catch (e: BiometricKeyInvalidatedException) {
                clearInvalidBiometric()
                _state.update {
                    it.copy(busy = false, error = "指纹密钥已失效，请使用主密码解锁")
                }
            } catch (t: Throwable) {
                if (t.isUnusableKeystoreKey()) {
                    clearInvalidBiometric()
                    _state.update {
                        it.copy(busy = false, error = "指纹密钥不可用，请使用主密码解锁后重新开启指纹")
                    }
                } else {
                    _state.update { it.copy(busy = false, error = "指纹解锁失败：${t.readable()}") }
                }
            }
        }
    }

    private fun enterVault(key: ByteArray, loaded: VaultPayload, biometricEnabled: Boolean) {
        dek?.fill(0)
        dek = key
        payload = loaded
        _state.update {
            it.copy(
                phase = Phase.Unlocked,
                busy = false,
                error = null,
                groups = loaded.groups,
                entries = loaded.entries,
                autoLockMillis = loaded.autoLockMillis,
                biometricEnabled = biometricEnabled,
                query = "",
                groupFilter = null,
                screen = Screen.Vault
            )
        }
    }

    // ---------------------------------------------------------------- 指纹开关

    /** 指纹验证成功后调用：用 Keystore 密钥包装 DEK 并保存绑定。 */
    fun enableBiometric() {
        val currentDek = dek ?: return
        val currentEnvelope = envelope ?: return

        viewModelScope.launch {
            try {
                val wrapped = withContext(Dispatchers.Default) {
                    val cipher = BiometricKeyStore.encryptCipher()
                    val copy = currentDek.copyOf()
                    try {
                        cipher.doFinal(copy) to cipher.iv
                    } finally {
                        copy.fill(0)
                    }
                }
                val updated = currentEnvelope.copy(
                    biometricNonce = wrapped.second,
                    biometricWrappedKey = wrapped.first
                )
                withContext(Dispatchers.IO) { store.write(updated) }
                envelope = updated
                _state.update { it.copy(biometricEnabled = true) }
                emitMessage("已开启指纹解锁")
            } catch (t: Throwable) {
                if (t.isUnusableKeystoreKey()) {
                    // 该机型的 Keystore 指纹密钥不可用：删除重建，等用户下次重试
                    BiometricKeyStore.deleteKey()
                    emitMessage("开启指纹解锁失败：本机指纹密钥不可用，请稍后重试或重启手机")
                } else {
                    emitMessage("开启指纹解锁失败：${t.readable()}")
                }
            }
        }
    }

    fun disableBiometric() {
        val currentEnvelope = envelope ?: return
        if (currentEnvelope.biometricWrappedKey == null) {
            _state.update { it.copy(biometricEnabled = false) }
            return
        }
        viewModelScope.launch {
            try {
                val updated = currentEnvelope.copy(
                    biometricNonce = null,
                    biometricWrappedKey = null
                )
                withContext(Dispatchers.IO) { store.write(updated) }
                envelope = updated
                BiometricKeyStore.deleteKey()
                _state.update { it.copy(biometricEnabled = false) }
                emitMessage("已关闭指纹解锁")
            } catch (t: Throwable) {
                emitMessage("关闭指纹解锁失败：${t.readable()}")
            }
        }
    }

    private fun clearInvalidBiometric() {
        val currentEnvelope = envelope ?: return
        val updated = currentEnvelope.copy(
            biometricNonce = null,
            biometricWrappedKey = null
        )
        envelope = updated
        _state.update { it.copy(biometricEnabled = false) }
        BiometricKeyStore.deleteKey()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { store.write(updated) }
        }
    }

    // ---------------------------------------------------------------- 记录操作

    fun saveEntry(
        id: String?,
        groupId: String,
        name: String,
        account: String,
        password: String,
        subPassword: String,
        note: String
    ): String {
        val now = System.currentTimeMillis()
        val existing = id?.let { entryId -> payload.entries.firstOrNull { it.id == entryId } }
        val validGroupId = groupId.takeIf { candidate ->
            payload.groups.any { it.id == candidate }
        } ?: DEFAULT_GROUP_ID
        val entry = if (existing != null) {
            existing.copy(
                groupId = validGroupId,
                name = name,
                account = account,
                password = password,
                subPassword = subPassword,
                note = note,
                updatedAt = now
            )
        } else {
            VaultEntry(
                id = UUID.randomUUID().toString(),
                groupId = validGroupId,
                name = name,
                account = account,
                password = password,
                subPassword = subPassword,
                note = note,
                createdAt = now,
                updatedAt = now
            )
        }

        val entries = if (existing != null) {
            payload.entries.map { if (it.id == entry.id) entry else it }
        } else {
            payload.entries + entry
        }
        persist(payload.copy(entries = entries))
        return entry.id
    }

    fun deleteEntry(id: String) {
        persist(payload.copy(entries = payload.entries.filterNot { it.id == id }))
    }

    // ---------------------------------------------------------------- 分组

    fun setGroupFilter(groupId: String?) = _state.update { it.copy(groupFilter = groupId) }

    fun addGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            emitMessage("分组名称不能为空")
            return
        }
        if (payload.groups.any { it.name.equals(trimmed, ignoreCase = true) }) {
            emitMessage("已存在同名分组")
            return
        }
        val group = VaultGroup(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            createdAt = System.currentTimeMillis()
        )
        persist(payload.copy(groups = payload.groups + group))
        emitMessage("已添加分组「$trimmed」")
    }

    fun renameGroup(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            emitMessage("分组名称不能为空")
            return
        }
        val target = payload.groups.firstOrNull { it.id == id } ?: return
        if (target.name == trimmed) return
        if (payload.groups.any { it.id != id && it.name.equals(trimmed, ignoreCase = true) }) {
            emitMessage("已存在同名分组")
            return
        }
        persist(payload.copy(groups = payload.groups.map {
            if (it.id == id) it.copy(name = trimmed) else it
        }))
        emitMessage("分组已重命名为「$trimmed」")
    }

    /** 删除分组，其中的记录会移动到默认分组，不会丢失。 */
    fun deleteGroup(id: String) {
        if (id == DEFAULT_GROUP_ID) {
            emitMessage("默认分组不能删除")
            return
        }
        if (payload.groups.none { it.id == id }) return

        val now = System.currentTimeMillis()
        val moved = payload.entries.map {
            if (it.groupId == id) it.copy(groupId = DEFAULT_GROUP_ID, updatedAt = now) else it
        }
        if (_state.value.groupFilter == id) {
            _state.update { it.copy(groupFilter = null) }
        }
        persist(payload.copy(groups = payload.groups.filterNot { it.id == id }, entries = moved))
        emitMessage("分组已删除，其中的记录已移到默认分组")
    }

    fun setAutoLock(millis: Long) {
        persist(payload.copy(autoLockMillis = millis))
    }

    private fun persist(updated: VaultPayload) {
        payload = updated
        _state.update {
            it.copy(
                entries = updated.entries,
                groups = updated.groups,
                autoLockMillis = updated.autoLockMillis
            )
        }

        val currentDek = dek?.copyOf() ?: return
        viewModelScope.launch {
            try {
                saveMutex.withLock {
                    val latestEnvelope = envelope ?: return@withLock
                    val encrypted = withContext(Dispatchers.Default) {
                        VaultCrypto.sealPayload(latestEnvelope, currentDek, updated)
                    }
                    withContext(Dispatchers.IO) { store.write(encrypted) }
                    envelope = encrypted
                }
            } catch (t: Throwable) {
                emitMessage("保存失败：${t.readable()}")
            } finally {
                currentDek.fill(0)
            }
        }
    }

    // ---------------------------------------------------------------- 主密码

    fun changeMasterPassword(newPassword: String) {
        validateMasterPassword(newPassword)?.let { emitMessage(it); return }
        val currentDek = dek ?: return

        viewModelScope.launch {
            try {
                saveMutex.withLock {
                    val latestEnvelope = envelope ?: return@withLock
                    val chars = newPassword.toCharArray()
                    val rewritten = withContext(Dispatchers.Default) {
                        val keyCopy = currentDek.copyOf()
                        try {
                            VaultCrypto.rewrapPassword(latestEnvelope, keyCopy, chars)
                        } finally {
                            keyCopy.fill(0)
                            chars.fill('\u0000')
                        }
                    }
                    withContext(Dispatchers.IO) { store.write(rewritten) }
                    envelope = rewritten
                }
                emitMessage("主密码已更新")
            } catch (t: Throwable) {
                emitMessage("修改主密码失败：${t.readable()}")
            }
        }
    }

    // ---------------------------------------------------------------- 锁定

    fun lock() {
        dek?.fill(0)
        dek = null
        payload = VaultPayload()
        clipboardClearJob?.cancel()
        clearClipboardIfOwned()
        _state.update {
            VaultUiState(
                phase = if (envelope != null) Phase.Locked else Phase.Setup,
                biometricEnabled = envelope?.biometricWrappedKey != null,
                biometricAvailable = it.biometricAvailable
            )
        }
    }

    fun onAppBackground() {
        if (_state.value.phase == Phase.Unlocked) {
            backgroundAt = SystemClock.elapsedRealtime()
        }
    }

    fun onAppForeground() {
        if (_state.value.phase != Phase.Unlocked) return
        val since = backgroundAt
        if (since == 0L) return
        backgroundAt = 0L
        if (SystemClock.elapsedRealtime() - since >= _state.value.autoLockMillis) {
            lock()
        }
    }

    // ---------------------------------------------------------------- 导航

    fun navigate(screen: Screen) = _state.update { it.copy(screen = screen) }

    fun back() {
        when (val current = _state.value.screen) {
            is Screen.Detail -> navigate(Screen.Vault)
            is Screen.Editor -> navigate(
                if (current.id != null) Screen.Detail(current.id) else Screen.Vault
            )
            Screen.Settings -> navigate(Screen.Vault)
            Screen.Groups -> navigate(Screen.Settings)
            Screen.Vault -> Unit
        }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    // ---------------------------------------------------------------- 剪贴板

    fun copyToClipboard(label: String, text: String) {
        if (text.isEmpty()) return
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboardManager.setPrimaryClip(clip)
        clipboardOwner = text

        clipboardClearJob?.cancel()
        clipboardClearJob = viewModelScope.launch {
            delay(CLIPBOARD_CLEAR_DELAY_MS)
            clearClipboardIfOwned()
        }
        emitMessage("已复制，30 秒后自动清除")
    }

    private fun clearClipboardIfOwned() {
        val owned = clipboardOwner ?: return
        clipboardOwner = null
        val current = runCatching {
            clipboardManager.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.text
                ?.toString()
        }.getOrNull()
        if (current != owned) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboardManager.clearPrimaryClip()
        } else {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    // ---------------------------------------------------------------- 工具

    fun postMessage(message: String) {
        _messages.tryEmit(message)
    }

    private fun emitMessage(message: String) {
        _messages.tryEmit(message)
    }

    private fun showError(message: String) {
        _state.update { it.copy(error = message) }
    }

    private fun validateMasterPassword(password: String): String? = when {
        password.isBlank() -> "主密码不能为空"
        password.length < VaultCrypto.MIN_PASSWORD_LENGTH ->
            "主密码至少需要 ${VaultCrypto.MIN_PASSWORD_LENGTH} 位字符"
        else -> null
    }

    private fun Throwable.readable(): String =
        message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

    /**
     * 指纹 Keystore 密钥不可用（被指纹变更失效、认证未生效等）。
     * 注意：部分机型会把 android.security.KeyStoreException("Key user not authenticated")
     * 包装成 IllegalBlockSizeException，所以这里一并识别。
     */
    private fun Throwable.isUnusableKeystoreKey(): Boolean =
        this is BiometricKeyInvalidatedException ||
            this is KeyPermanentlyInvalidatedException ||
            this is UserNotAuthenticatedException ||
            this is IllegalBlockSizeException

    override fun onCleared() {
        dek?.fill(0)
        dek = null
        clipboardClearJob?.cancel()
        clipboardOwner = null
        super.onCleared()
    }

    private sealed interface LoadResult {
        data class Ready(val envelope: VaultEnvelope) : LoadResult
        data object Empty : LoadResult
        data object Broken : LoadResult
    }

    private companion object {
        const val CLIPBOARD_CLEAR_DELAY_MS = 30_000L
    }
}
