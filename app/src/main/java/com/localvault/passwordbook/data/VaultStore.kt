package com.localvault.passwordbook.data

import android.content.Context
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * 只负责把加密信封读写到应用私有目录（noBackupFilesDir）。
 * 写入采用「临时文件 + fsync + 原子重命名」，避免异常断电导致文件半写。
 */
class VaultStore(context: Context) {
    private val directory = File(context.noBackupFilesDir, "vault")
    private val vaultFile = File(directory, "vault.bin")
    private val tempFile = File(directory, "vault.bin.tmp")

    fun exists(): Boolean = vaultFile.exists()

    @Throws(IOException::class, VaultCorruptedException::class)
    fun read(): VaultEnvelope {
        if (!vaultFile.exists()) throw VaultCorruptedException()
        DataInputStream(BufferedInputStream(FileInputStream(vaultFile))).use { input ->
            val magic = ByteArray(MAGIC.size)
            input.readFully(magic)
            if (!magic.contentEquals(MAGIC)) throw VaultCorruptedException()

            val formatVersion = input.readInt()
            if (formatVersion != FORMAT_VERSION) throw VaultCorruptedException()

            val salt = input.readSizedBytes(MAX_SALT)
            val iterations = input.readInt()
            if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw VaultCorruptedException()

            val passwordNonce = input.readSizedBytes(MAX_NONCE)
            val passwordWrappedKey = input.readSizedBytes(MAX_WRAPPED_KEY)
            val biometricNonce = input.readSizedBytes(MAX_NONCE)
            val biometricWrappedKey = input.readSizedBytes(MAX_WRAPPED_KEY)
            val payloadNonce = input.readSizedBytes(MAX_NONCE)
            val payloadCiphertext = input.readSizedBytes(MAX_PAYLOAD)

            return VaultEnvelope(
                formatVersion = formatVersion,
                salt = salt,
                iterations = iterations,
                passwordNonce = passwordNonce,
                passwordWrappedKey = passwordWrappedKey,
                biometricNonce = biometricNonce.takeIf { it.isNotEmpty() },
                biometricWrappedKey = biometricWrappedKey.takeIf { it.isNotEmpty() },
                payloadNonce = payloadNonce,
                payloadCiphertext = payloadCiphertext
            )
        }
    }

    @Throws(IOException::class)
    fun write(envelope: VaultEnvelope) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("无法创建数据目录")
        }
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { output ->
            output.write(MAGIC)
            output.writeInt(FORMAT_VERSION)
            output.writeSizedBytes(envelope.salt)
            output.writeInt(envelope.iterations)
            output.writeSizedBytes(envelope.passwordNonce)
            output.writeSizedBytes(envelope.passwordWrappedKey)
            output.writeSizedBytes(envelope.biometricNonce ?: ByteArray(0))
            output.writeSizedBytes(envelope.biometricWrappedKey ?: ByteArray(0))
            output.writeSizedBytes(envelope.payloadNonce)
            output.writeSizedBytes(envelope.payloadCiphertext)
            output.flush()
        }

        FileOutputStream(tempFile).use { output ->
            output.write(buffer.toByteArray())
            output.flush()
            output.fd.sync()
        }

        if (!tempFile.renameTo(vaultFile)) {
            if (!vaultFile.delete() || !tempFile.renameTo(vaultFile)) {
                tempFile.delete()
                throw IOException("无法写入数据文件")
            }
        }
    }

    private fun DataInputStream.readSizedBytes(max: Int): ByteArray {
        val size = readInt()
        if (size < 0 || size > max) throw VaultCorruptedException()
        val data = ByteArray(size)
        readFully(data)
        return data
    }

    private fun DataOutputStream.writeSizedBytes(data: ByteArray) {
        writeInt(data.size)
        write(data)
    }

    private companion object {
        val MAGIC = byteArrayOf('P'.code.toByte(), 'W'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
        const val FORMAT_VERSION = 1
        const val MAX_SALT = 64
        const val MAX_NONCE = 32
        const val MAX_WRAPPED_KEY = 128
        const val MAX_PAYLOAD = 8 * 1024 * 1024
        const val MIN_ITERATIONS = 10_000
        const val MAX_ITERATIONS = 5_000_000
    }
}
