package com.mmg.manahub.feature.competitive.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.koin.androidx.compose.koinViewModel

@Composable
fun CompetitiveScreen(
    viewModel: CompetitiveViewModel = koinViewModel(),
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) {}

    //Scaffold(Surface() {}) { paddingValues -> }
}