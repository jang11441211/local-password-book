package com.localvault.passwordbook.data

/**
 * 磁盘上的加密信封。信封中只保存密文与 KDF 参数，任何明文都不会写入磁盘。
 *
 * 结构：
 * - 主密码派生密钥（KEK）包装的数据密钥（DEK）
 * - 指纹（Android Keystore）包装的同一把 DEK
 * - 使用 DEK 加密的载荷密文
 */
data class VaultEnvelope(
    val formatVersion: Int = 1,
    val salt: ByteArray,
    val iterations: Int,
    val passwordNonce: ByteArray,
    val passwordWrappedKey: ByteArray,
    val biometricNonce: ByteArray?,
    val biometricWrappedKey: ByteArray?,
    val payloadNonce: ByteArray,
    val payloadCiphertext: ByteArray
)
