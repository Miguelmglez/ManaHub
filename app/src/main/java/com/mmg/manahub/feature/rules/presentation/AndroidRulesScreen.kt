package com.mmg.manahub.feature.rules.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.rules.*
import com.mmg.manahub.core.model.rules.*
import com.mmg.manahub.feature.rules.RulesScreen
import com.mmg.manahub.feature.rules.RulesStateHolder
import org.koin.androidx.compose.koinViewModel

class RulesViewModel(
    private val saved: SavedStateHandle,
    load: LoadRulesUseCase,
    search: SearchRulesUseCase,
    update: UpdateRulesUseCase,
    reporter: CrashReporter,
) : ViewModel() {
    val holder = RulesStateHolder(load, search, update, reporter, viewModelScope, saved.get<String>("entry") ?: "navigation")
    init { open() }
    fun open() = holder.open((saved.get<String>("pinnedEdition") ?: saved.get<String>("edition"))?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }, saved.get<String>("reference")?.take(256)?.takeIf { it.isNotEmpty() }, (saved.get<String>("searchQuery") ?: saved.get<String>("query").orEmpty()).take(200), saved.get<Int>("resultOffset")?.coerceAtLeast(0) ?: 0)
    fun query(value: String) { saved["searchQuery"] = value.take(200); saved["resultOffset"] = 0; holder.changeQuery(value) }
    fun offset(value: Int) { saved["resultOffset"] = value }
    fun pin(id: String) { saved["pinnedEdition"] = id }
    fun reload() { saved["reference"] = ""; saved["resultOffset"] = 0; holder.reload(); holder.state.value.edition?.id?.let(::pin) }
}

@Composable
fun AndroidRulesScreen(
    onBack: () -> Unit,
    onReference: (RulesDestination.Reference) -> Unit,
    onBrowse: () -> Unit,
    viewModel: RulesViewModel = koinViewModel(),
) {
    val state by viewModel.holder.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.edition?.id) { state.edition?.id?.let(viewModel::pin) }
    LaunchedEffect(state.results.offset, state.loading, state.searching) { if (!state.loading && !state.searching) viewModel.offset(state.results.offset) }
    BackHandler(onBack = onBack)
    RulesScreen(state, viewModel::query, onReference, onBrowse, onBack, viewModel::open,
        viewModel.holder::checkForUpdates, viewModel::reload, viewModel.holder::page)
}
