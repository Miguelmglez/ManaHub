package com.mmg.manahub.feature.addcard.presentation

/** A deck whose cards AddCard preloads as a local, searchable browse list. */
sealed interface AddCardDeckSource {
    /** A deck of the user's own library, resolved through `DeckRepository`. */
    data class Local(val deckId: String) : AddCardDeckSource

    /** An Archidekt community deck, resolved through `CommunityDecksRepository`. */
    data class Community(val archidektId: Int) : AddCardDeckSource
}

/**
 * Navigation arguments of the AddCard destination (`collection/add?multi=&source=&sourceId=`).
 *
 * @property multi opens the screen directly in "Select multiple" mode.
 * @property deckSource preloads a deck's cards as the browse list; any source also forces multi mode.
 */
data class AddCardLaunchArgs(
    val multi: Boolean = false,
    val deckSource: AddCardDeckSource? = null,
) {
    companion object {
        const val ARG_MULTI = "multi"
        const val ARG_SOURCE = "source"
        const val ARG_SOURCE_ID = "sourceId"
        const val SOURCE_DECK = "deck"
        const val SOURCE_COMMUNITY = "community"

        /** Parses the raw nav arguments; an unknown source or an unusable id yields no source. */
        fun from(multi: Boolean, source: String?, sourceId: String?): AddCardLaunchArgs {
            val id = sourceId?.takeIf { it.isNotBlank() }
            val deckSource = when (source) {
                SOURCE_DECK -> id?.let { AddCardDeckSource.Local(it) }
                SOURCE_COMMUNITY -> id?.toIntOrNull()?.let { AddCardDeckSource.Community(it) }
                else -> null
            }
            return AddCardLaunchArgs(multi = multi, deckSource = deckSource)
        }
    }
}
