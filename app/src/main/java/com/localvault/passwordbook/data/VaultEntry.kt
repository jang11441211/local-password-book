package com.localvault.passwordbook.data

import kotlinx.serialization.Serializable

/** 默认分组，无法删除。 */
const val DEFAULT_GROUP_ID = "default"

/** 密码分组，例如「父亲」「母亲」「其他」。 */
@Serializable
data class VaultGroup(
    val id: String,
    val name: String,
    val createdAt: Long = 0L
)

/**
 * 一条密码记录。整个列表会被整体加密后写入磁盘。
 *
 * - password：主密码（如登录密码）
 * - subPassword：子密码（如支付密码），可为空
 */
@Serializable
data class VaultEntry(
    val id: String,
    val name: String,
    val account: String = "",
    val password: String,
    val subPassword: String = "",
    val note: String = "",
    val groupId: String = DEFAULT_GROUP_ID,
    val createdAt: Long,
    val updatedAt: Long
)

/** 加密载荷（明文仅存在于内存中）。 */
@Serializable
data class VaultPayload(
    val schema: Int = 2,
    val groups: List<VaultGroup> = defaultGroups(),
    val entries: List<VaultEntry> = emptyList(),
    val autoLockMillis: Long = 0L
)

fun defaultGroups(): List<VaultGroup> = listOf(
    VaultGroup(id = DEFAULT_GROUP_ID, name = "默认")
)
