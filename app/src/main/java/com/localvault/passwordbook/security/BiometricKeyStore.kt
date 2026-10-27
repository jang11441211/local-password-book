package com.localvault.passwordbook.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class BiometricKeyInvalidatedException : Exception("指纹密钥已失效")

/**
 * 指纹解锁使用的 Android Keystore 密钥：
 * - 硬件/系统级保护，密钥不可导出；
 * - setUserAuthenticationRequired(true)，只有通过生物识别后的 30 秒内可用；
 * - 新增指纹会使旧密钥永久失效（更安全，失效后需用主密码解锁并重新开启）。
 *
 * 说明：这里刻意不使用 BiometricPrompt.CryptoObject 的「每次操作都要认证」模式。
 * 该模式在部分机型（尤其国内厂商系统）上，认证成功后调用 doFinal 会被 keystore
 * 判为「Key user not authenticated」，表现为 IllegalBlockSizeException
 * （Google issue 65578763）。改为认证后短时效密钥，兼容性更稳定。
 */
object BiometricKeyStore {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "pwbook.biometric.wrap.v2"
    private const val LEGACY_ALIAS = "pwbook.biometric.wrap.v1"
    private const val AUTH_TIMEOUT_SECONDS = 30
    private val AAD = "pwbook:bio-wrap:v1".toByteArray(Charsets.UTF_8)

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** 需在指纹验证成功后调用。 */
    fun encryptCipher(): Cipher {
        ensureKey()
        return try {
            newEncryptCipher()
        } catch (e: KeyPermanentlyInvalidatedException) {
            deleteKey()
            ensureKey()
            newEncryptCipher()
        }
    }

    /** 需在指纹验证成功后调用。 */
    fun decryptCipher(iv: ByteArray): Cipher {
        val key = getKey() ?: throw BiometricKeyInvalidatedException()
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                updateAAD(AAD)
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw BiometricKeyInvalidatedException()
        }
    }

    fun deleteKey() {
        runCatching {
            val store = keyStore()
            if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
            if (store.containsAlias(LEGACY_ALIAS)) store.deleteEntry(LEGACY_ALIAS)
        }
    }

    private fun newEncryptCipher(): Cipher {
        val key = getKey() ?: throw BiometricKeyInvalidatedException()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key)
            updateAAD(AAD)
        }
    }

    private fun getKey(): SecretKey? = try {
        (keyStore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    } catch (e: UnrecoverableKeyException) {
        null
    }

    private fun ensureKey() {
        if (getKey() != null) return

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                AUTH_TIMEOUT_SECONDS,
                KeyProperties.AUTH_BIOMETRIC_STRONG
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(AUTH_TIMEOUT_SECONDS)
        }

        generator.init(builder.build())
        generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
}
