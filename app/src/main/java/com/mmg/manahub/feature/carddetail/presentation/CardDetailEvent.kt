package com.mmg.manahub.feature.carddetail.presentation

/** Severity used for toast feedback from [CardDetailViewModel]. */
enum class ToastSeverity { SUCCESS, INFO, ERROR }

/** One-shot UI events emitted by [CardDetailViewModel]. */
sealed class CardDetailEvent {
    data class ShowToast(
        val message: String,
        val severity: ToastSeverity = ToastSeverity.SUCCESS,
    ) : CardDetailEvent()
}
