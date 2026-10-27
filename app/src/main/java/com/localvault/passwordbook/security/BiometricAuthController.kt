package com.localvault.passwordbook.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * 只负责弹出系统指纹验证，并在成功后回调。
 * 密钥操作由调用方在验证成功后立即执行（Keystore 密钥有 30 秒使用窗口）。
 */
class BiometricAuthController(private val activity: FragmentActivity) {

    var isRunning: Boolean = false
        private set

    fun authenticate(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (isRunning) return

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    isRunning = false
                    try {
                        onSuccess()
                    } catch (e: Exception) {
                        onFailure("指纹验证后处理失败：${e.readableMessage()}")
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    isRunning = false
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED -> Unit

                        BiometricPrompt.ERROR_LOCKOUT ->
                            onFailure("指纹尝试次数过多，请稍后再试或使用主密码解锁")

                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                            onFailure("指纹已被系统锁定，请使用主密码解锁")

                        BiometricPrompt.ERROR_HW_NOT_PRESENT,
                        BiometricPrompt.ERROR_HW_UNAVAILABLE,
                        BiometricPrompt.ERROR_NO_BIOMETRICS ->
                            onFailure("当前设备无法使用指纹解锁")

                        else -> onFailure(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    // 单次指纹不匹配，系统提示会自动保持并允许重试。
                }
            }
        )

        isRunning = true
        try {
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .setNegativeButtonText("使用主密码")
                    .setConfirmationRequired(false)
                    .build()
            )
        } catch (e: Exception) {
            isRunning = false
            onFailure("无法显示指纹验证：${e.readableMessage()}")
        }
    }

    private fun Exception.readableMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
}
