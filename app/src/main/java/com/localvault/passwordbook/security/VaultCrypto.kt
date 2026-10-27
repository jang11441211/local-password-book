package com.localvault.passwordbook.security

import com.localvault.passwordbook.data.VaultCorruptedException
import com.localvault.passwordbook.data.VaultEnvelope
import com.localvault.passwordbook.data.VaultPayload
import com.localvault.passwordbook.data.WrongPasswordException
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import javax.crypto.AEADBadTagException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 密钥体系：
 * - DEK：随机 256 位数据密钥，用来加密整个密码本载荷。
 * - KEK：由主密码经 PBKDF2-HMAC-SHA256（600,000 次迭代）派生，只用于包装 DEK。
 * - 指纹：由 Android Keystore 中「每次使用都要求生物识别」的 AES 密钥包装同一把 DEK。
 *
 * 好处：修改主密码只重新包装 DEK，无需重新加密全部数据；主密码与指纹互不影响。
 */
object VaultCrypto {
    const val KDF_ITERATIONS = 600_000
    const val MIN_PASSWORD_LENGTH = 8

    private const val SALT_BYTES = 32
    private const val KEY_BYTES = 32
    private val AAD_PASSWORD_KEY = "pwbook:master-key:v1".toByteArray(StandardCharsets.UTF_8)
    private val AAD_PAYLOAD = "pwbook:payload:v1".toByteArray(StandardCharsets.UTF_8)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    class UnlockedVault(
        val dek: ByteArray,
        val payload: VaultPayload,
        val envelope: VaultEnvelope
    )

    fun create(password: CharArray): UnlockedVault {
        val salt = Aead.randomBytes(SALT_BYTES)
        val dek = Aead.randomBytes(KEY_BYTES)
        val kek = deriveKey(password, salt, KDF_ITERATIONS)
        try {
            val wrapped = Aead.encrypt(kek, dek, AAD_PASSWORD_KEY)
            val payload = VaultPayload()
            val envelope = VaultEnvelope(
                formatVersion = 1,
                salt = salt,
                iterations = KDF_ITERATIONS,
                passwordNonce = wrapped.iv,
                passwordWrappedKey = wrapped.ciphertext,
                biometricNonce = null,
                biometricWrappedKey = null,
                payloadNonce = ByteArray(0),
                payloadCiphertext = ByteArray(0)
            )
            return UnlockedVault(dek, payload, sealPayload(envelope, dek, payload))
        } finally {
            kek.fill(0)
        }
    }

    fun openWithPassword(envelope: VaultEnvelope, password: CharArray): UnlockedVault {
        val kek = deriveKey(password, envelope.salt, envelope.iterations)
        val dek = try {
            Aead.decrypt(kek, envelope.passwordNonce, envelope.passwordWrappedKey, AAD_PASSWORD_KEY)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        } finally {
            kek.fill(0)
        }
        return UnlockedVault(dek, decryptPayload(envelope, dek), envelope)
    }

    fun decryptPayload(envelope: VaultEnvelope, dek: ByteArray): VaultPayload {
        val plaintext = try {
            Aead.decrypt(dek, envelope.payloadNonce, envelope.payloadCiphertext, AAD_PAYLOAD)
        } catch (e: AEADBadTagException) {
            throw VaultCorruptedException()
        }
        return try {
            json.decodeFromString(VaultPayload.serializer(), String(plaintext, StandardCharsets.UTF_8))
        } catch (e: Exception) {
            throw VaultCorruptedException()
        } finally {
            plaintext.fill(0)
        }
    }

    fun sealPayload(envelope: VaultEnvelope, dek: ByteArray, payload: VaultPayload): VaultEnvelope {
        val plaintext = json.encodeToString(VaultPayload.serializer(), payload)
            .toByteArray(StandardCharsets.UTF_8)
        val encrypted = try {
            Aead.encrypt(dek, plaintext, AAD_PAYLOAD)
        } finally {
            plaintext.fill(0)
        }
        return envelope.copy(
            payloadNonce = encrypted.iv,
            payloadCiphertext = encrypted.ciphertext
        )
    }

    /** 更换主密码：生成新盐、重新派生 KEK，只重新包装 DEK。 */
    fun rewrapPassword(envelope: VaultEnvelope, dek: ByteArray, newPassword: CharArray): VaultEnvelope {
        val salt = Aead.randomBytes(SALT_BYTES)
        val kek = deriveKey(newPassword, salt, KDF_ITERATIONS)
        try {
            val wrapped = Aead.encrypt(kek, dek, AAD_PASSWORD_KEY)
            return envelope.copy(
                salt = salt,
                iterations = KDF_ITERATIONS,
                passwordNonce = wrapped.iv,
                passwordWrappedKey = wrapped.ciphertext
            )
        } finally {
            kek.fill(0)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BYTES * 8)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }
}
