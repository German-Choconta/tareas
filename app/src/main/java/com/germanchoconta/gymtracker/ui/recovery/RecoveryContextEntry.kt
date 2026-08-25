package com.germanchoconta.gymtracker.ui.recovery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun RecoveryContextEntry(
    state: RecoveryUiState,
    viewModel: RecoveryContextViewModel,
    onBack: () -> Unit,
) {
    LaunchedEffect(viewModel) {
        viewModel.refresh()
    }
    RecoveryContextRoute(
        state = state,
        viewModel = viewModel,
        onBack = onBack,
    )
}
