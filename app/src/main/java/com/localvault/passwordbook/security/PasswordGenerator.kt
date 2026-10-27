package com.localvault.passwordbook.security

import java.security.SecureRandom

/**
 * 使用 SecureRandom 生成高强度随机密码。
 * 字符集排除了容易混淆的 0/O、1/l/I，避免抄写错误。
 */
object PasswordGenerator {

    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{}<>?"

    private val random = SecureRandom()

    const val DEFAULT_LENGTH = 16

    fun generate(length: Int = DEFAULT_LENGTH, includeSymbols: Boolean = true): String {
        require(length >= 8) { "长度至少为 8" }

        val pools = buildList {
            add(LOWER)
            add(UPPER)
            add(DIGITS)
            if (includeSymbols) add(SYMBOLS)
        }
        val all = pools.joinToString("")
        val chars = CharArray(length)

        // 每个字符类至少出现一次，保证强度
        pools.forEachIndexed { index, pool ->
            chars[index] = pool[random.nextInt(pool.length)]
        }
        for (i in pools.size until length) {
            chars[i] = all[random.nextInt(all.length)]
        }

        // 打乱顺序，避免密码前几位总是固定字符类
        for (i in chars.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return String(chars)
    }
}
