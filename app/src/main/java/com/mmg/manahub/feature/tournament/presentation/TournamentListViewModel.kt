package com.mmg.manahub.feature.tournament.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.entity.TournamentEntity
import com.mmg.manahub.core.domain.repository.TournamentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TournamentListViewModel @Inject constructor(
    private val repository: TournamentRepository,
) : ViewModel() {

    val tournaments: StateFlow<List<TournamentEntity>> =
        repository.observeTournaments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
