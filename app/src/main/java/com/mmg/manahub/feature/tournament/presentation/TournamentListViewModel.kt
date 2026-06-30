package com.mmg.manahub.feature.tournament.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.model.Tournament
import com.mmg.manahub.feature.tournament.domain.repository.TournamentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. Plain (non-Hilt) ViewModel resolved by Koin via
 * `koinViewModel()`; constructed in `tournamentKoinModule`. Behaviour is unchanged from the Hilt
 * version — only the DI annotations were removed.
 */
class TournamentListViewModel(
    private val repository: TournamentRepository,
) : ViewModel() {

    val tournaments: StateFlow<List<Tournament>> =
        repository.observeTournaments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
