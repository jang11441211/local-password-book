package com.localvault.passwordbook.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.localvault.passwordbook.VaultViewModel
import com.localvault.passwordbook.data.DEFAULT_GROUP_ID

@Composable
fun UnlockedRoot(
    viewModel: VaultViewModel,
    state: VaultUiState,
    onBiometricEnable: () -> Unit,
    onBiometricDisable: () -> Unit
) {
    BackHandler(enabled = state.screen != Screen.Vault) { viewModel.back() }

    when (val screen = state.screen) {
        Screen.Vault -> VaultListScreen(
            entries = state.entries,
            groups = state.groups,
            query = state.query,
            groupFilter = state.groupFilter,
            onQueryChange = viewModel::setQuery,
            onGroupFilterChange = viewModel::setGroupFilter,
            onOpenEntry = { viewModel.navigate(Screen.Detail(it)) },
            onAddEntry = { viewModel.navigate(Screen.Editor(null)) },
            onOpenSettings = { viewModel.navigate(Screen.Settings) },
            onLock = viewModel::lock
        )

        is Screen.Detail -> {
            val entry = state.entries.firstOrNull { it.id == screen.id }
            if (entry == null) {
                LaunchedEffect(screen.id) { viewModel.navigate(Screen.Vault) }
            } else {
                val groupName = state.groups
                    .firstOrNull { it.id == entry.groupId }?.name
                    ?: state.groups.firstOrNull { it.id == DEFAULT_GROUP_ID }?.name
                    ?: "默认"
                EntryDetailScreen(
                    entry = entry,
                    groupName = groupName,
                    onBack = viewModel::back,
                    onEdit = { viewModel.navigate(Screen.Editor(entry.id)) },
                    onDelete = {
                        viewModel.deleteEntry(entry.id)
                        viewModel.navigate(Screen.Vault)
                    },
                    onCopy = viewModel::copyToClipboard
                )
            }
        }

        is Screen.Editor -> {
            val entry = screen.id?.let { id -> state.entries.firstOrNull { it.id == id } }
            EntryEditorScreen(
                entry = entry,
                groups = state.groups,
                onBack = viewModel::back,
                onSave = { groupId, name, account, password, subPassword, note ->
                    val savedId = viewModel.saveEntry(
                        screen.id, groupId, name, account, password, subPassword, note
                    )
                    viewModel.navigate(Screen.Detail(savedId))
                }
            )
        }

        Screen.Settings -> SettingsScreen(
            state = state,
            onBack = viewModel::back,
            onAutoLockChange = viewModel::setAutoLock,
            onBiometricEnable = onBiometricEnable,
            onBiometricDisable = onBiometricDisable,
            onChangeMasterPassword = viewModel::changeMasterPassword,
            onManageGroups = { viewModel.navigate(Screen.Groups) },
            onLock = viewModel::lock
        )

        Screen.Groups -> GroupManagerScreen(
            groups = state.groups,
            entries = state.entries,
            onBack = viewModel::back,
            onAddGroup = viewModel::addGroup,
            onRenameGroup = viewModel::renameGroup,
            onDeleteGroup = viewModel::deleteGroup
        )
    }
}
