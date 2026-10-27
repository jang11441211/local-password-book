package com.localvault.passwordbook

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.localvault.passwordbook.security.BiometricAuthController
import com.localvault.passwordbook.ui.PasswordBookApp

class MainActivity : FragmentActivity() {

    private val viewModel: VaultViewModel by viewModels()
    private lateinit var biometric: BiometricAuthController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 禁止截屏与最近任务预览，防止密码泄露
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        biometric = BiometricAuthController(this)

        setContent {
            PasswordBookApp(
                viewModel = viewModel,
                onBiometricUnlock = {
                    biometric.authenticate(
                        title = "指纹解锁",
                        subtitle = "验证指纹以解锁本地密码本",
                        onSuccess = viewModel::unlockWithBiometric,
                        onFailure = viewModel::postMessage
                    )
                },
                onBiometricEnable = {
                    biometric.authenticate(
                        title = "开启指纹解锁",
                        subtitle = "验证指纹以绑定本机密钥",
                        onSuccess = viewModel::enableBiometric,
                        onFailure = viewModel::postMessage
                    )
                },
                onBiometricDisable = viewModel::disableBiometric
            )
        }
    }

    override fun onStart() {
        super.onStart()
        // 指纹弹窗期间不作为「离开应用」处理
        if (!biometric.isRunning) {
            viewModel.onAppForeground()
        }
    }

    override fun onStop() {
        viewModel.onAppBackground()
        super.onStop()
    }
}
