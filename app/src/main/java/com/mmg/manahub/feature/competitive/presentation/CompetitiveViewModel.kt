package com.mmg.manahub.feature.competitive.presentation

import androidx.lifecycle.ViewModel

class CompetitiveViewModel: ViewModel() {

    data class CompetitiveUiState(
        val isLoading : Boolean = true
    )

    init {

    }


}