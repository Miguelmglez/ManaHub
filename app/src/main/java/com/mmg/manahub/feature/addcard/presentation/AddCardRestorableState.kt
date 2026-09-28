package com.mmg.manahub.feature.addcard.presentation

/**
 * AddCard screen state that must survive process death but cannot live in the (immutable) nav
 * args. Android backs it with the destination's `SavedStateHandle`.
 */
interface AddCardRestorableState {
    /** True once "Clear deck cards" ran: a restored screen must not reload the nav-arg deck. */
    var isDeckSourceCleared: Boolean

    /** True once `addcard_multiselect_opened_from` was logged for this destination. */
    var isEntryPointLogged: Boolean
}

/** Non-persistent [AddCardRestorableState]; the default outside a nav destination and in tests. */
class InMemoryAddCardRestorableState(
    override var isDeckSourceCleared: Boolean = false,
    override var isEntryPointLogged: Boolean = false,
) : AddCardRestorableState
