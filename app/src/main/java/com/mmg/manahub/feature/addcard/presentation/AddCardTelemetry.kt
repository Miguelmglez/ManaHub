package com.mmg.manahub.feature.addcard.presentation

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.util.recordSafeNonFatal

/** Where "Select multiple" was switched on from; [id] is the logged enum id. */
enum class MultiSelectEntryPoint(val id: String) {
    HOME("home"),
    DECK("deck"),
    COMMUNITY("community"),
    TOOLBAR("toolbar"),
}

/** Why a deck source could not be turned into a browse list; [id] is the logged enum id. */
enum class DeckSourceLoadFailure(val id: String) {
    NOT_FOUND("not_found"),
    UNRESOLVED("unresolved"),
    EXCEPTION("exception"),
}

/** Crashlytics breadcrumbs of AddCard's multi-select mode. Counts are bucketed; no card data is logged. */
internal object AddCardTelemetry {

    fun multiSelectToggled(enabled: Boolean) =
        log("addcard_multiselect_toggled: ${if (enabled) "on" else "off"}")

    fun multiSelectOpenedFrom(entryPoint: MultiSelectEntryPoint) =
        log("addcard_multiselect_opened_from: ${entryPoint.id}")

    fun queueOpened(count: Int) = log("addcard_multiselect_queue_opened: ${countBucket(count)}")

    fun selectAll(addedCount: Int) = log("addcard_multiselect_select_all: ${countBucket(addedCount)}")

    fun selectMissing(addedCount: Int) =
        log("addcard_multiselect_select_missing: ${countBucket(addedCount)}")

    fun deckSourceLoadFailed(source: AddCardDeckSource, failure: DeckSourceLoadFailure, cause: Throwable?) {
        val sourceId = when (source) {
            is AddCardDeckSource.Local -> "local"
            is AddCardDeckSource.Community -> "community"
        }
        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("addcard_deck_source", sourceId)
            setCustomKey("addcard_deck_load_failure", failure.id)
            log("addcard_deck_source_load_failed: $sourceId/${failure.id}")
        }
        recordSafeNonFatal(
            tag = "addcard_deck_source_load",
            e = cause ?: IllegalStateException("addcard_deck_source_${failure.id}"),
        )
    }

    fun countBucket(count: Int): String = when {
        count <= 0 -> "0"
        count == 1 -> "1"
        count <= 5 -> "2_5"
        count <= 10 -> "6_10"
        count <= 25 -> "11_25"
        count <= 50 -> "26_50"
        count <= 100 -> "51_100"
        else -> "100_plus"
    }

    private fun log(message: String) = FirebaseCrashlytics.getInstance().log(message)
}
