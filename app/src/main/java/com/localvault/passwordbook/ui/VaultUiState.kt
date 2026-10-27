package com.localvault.passwordbook.ui

import com.localvault.passwordbook.data.VaultEntry
import com.localvault.passwordbook.data.VaultGroup

enum class Phase { Loading, Setup, Locked, Unlocked }

sealed interface Screen {
    data object Vault : Screen
    data class Detail(val id: String) : Screen
    data class Editor(val id: String?) : Screen
    data object Settings : Screen
    data object Groups : Screen
}

data class VaultUiState(
    val phase: Phase = Phase.Loading,
    val busy: Boolean = false,
    val error: String? = null,
    val groups: List<VaultGroup> = emptyList(),
    val entries: List<VaultEntry> = emptyList(),
    val query: String = "",
    /** null 表示「全部」分组 */
    val groupFilter: String? = null,
    val screen: Screen = Screen.Vault,
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val autoLockMillis: Long = 0L
)
