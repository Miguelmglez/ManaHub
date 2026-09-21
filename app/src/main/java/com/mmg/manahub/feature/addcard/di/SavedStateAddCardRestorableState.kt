package com.mmg.manahub.feature.addcard.di

import androidx.lifecycle.SavedStateHandle
import com.mmg.manahub.feature.addcard.presentation.AddCardRestorableState

/** [AddCardRestorableState] persisted in the AddCard destination's [SavedStateHandle]. */
class SavedStateAddCardRestorableState(
    private val savedStateHandle: SavedStateHandle,
) : AddCardRestorableState {

    override var isDeckSourceCleared: Boolean
        get() = savedStateHandle.get<Boolean>(KEY_DECK_SOURCE_CLEARED) ?: false
        set(value) { savedStateHandle[KEY_DECK_SOURCE_CLEARED] = value }

    override var isEntryPointLogged: Boolean
        get() = savedStateHandle.get<Boolean>(KEY_ENTRY_POINT_LOGGED) ?: false
        set(value) { savedStateHandle[KEY_ENTRY_POINT_LOGGED] = value }

    private companion object {
        // Prefixed so they can never collide with the destination's nav-arg keys.
        const val KEY_DECK_SOURCE_CLEARED = "addcard_state_deck_source_cleared"
        const val KEY_ENTRY_POINT_LOGGED = "addcard_state_entry_point_logged"
    }
}
