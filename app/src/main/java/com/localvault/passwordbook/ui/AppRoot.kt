package com.localvault.passwordbook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localvault.passwordbook.VaultViewModel
import com.localvault.passwordbook.ui.theme.PasswordBookTheme

@Composable
fun PasswordBookApp(
    viewModel: VaultViewModel,
    onBiometricUnlock: () -> Unit,
    onBiometricEnable: () -> Unit,
    onBiometricDisable: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    PasswordBookTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (state.phase) {
                Phase.Loading -> LoadingScreen()

                Phase.Setup -> SetupScreen(
                    busy = state.busy,
                    error = state.error,
                    onCreate = viewModel::createVault
                )

                Phase.Locked -> LockScreen(
                    busy = state.busy,
                    error = state.error,
                    biometricEnabled = state.biometricEnabled,
                    biometricAvailable = state.biometricAvailable,
                    onUnlock = viewModel::unlockWithPassword,
                    onBiometricUnlock = onBiometricUnlock
                )

                Phase.Unlocked -> UnlockedRoot(
                    viewModel = viewModel,
                    state = state,
                    onBiometricEnable = onBiometricEnable,
                    onBiometricDisable = onBiometricDisable
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }
    }
}
