package com.mmg.manahub.feature.tournament.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.model.Tournament
import com.mmg.manahub.core.util.recordNonFatal
import com.mmg.manahub.feature.tournament.domain.repository.TournamentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. Plain (non-Hilt) ViewModel resolved by Koin via
 * `koinViewModel()`; constructed in `tournamentKoinModule`. Behaviour is unchanged from the Hilt
 * version — only the DI annotations were removed.
 */
class TournamentListViewModel(
    private val repository: TournamentRepository,
) : ViewModel() {

    /** Null until the first DB emission: an empty initial value flashed the empty state on every open. */
    val tournaments: StateFlow<List<Tournament>?> =
        repository.observeTournaments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Deletes a tournament and everything under it (players/matches cascade). Failures are non-fatal. */
    fun delete(tournamentId: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteTournament(tournamentId) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    recordNonFatal("tournament_delete_failed", e)
                }
        }
    }
}
