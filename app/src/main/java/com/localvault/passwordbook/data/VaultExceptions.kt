package com.localvault.passwordbook.data

/** 主密码错误。 */
class WrongPasswordException : Exception("主密码错误")

/** 数据文件缺失、被篡改或无法解析。 */
class VaultCorruptedException : Exception("数据文件损坏，无法读取")
