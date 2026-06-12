package com.mmg.manahub.feature.gamification.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.gamification.domain.model.AchievementUiModel
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the global achievement-unlock celebration queue (ADR-002, Phase 1, Chunk B).
 *
 * The DB is the source of truth for the queue: rows with `unlocked_at` set but `celebrated_at` null
 * are pending (see [GamificationRepository.observePendingCelebrations]). This means an unlock that
 * happened off-screen is still celebrated the next time the host is visible. The queue plays
 * SEQUENTIALLY — only the FIRST pending item is exposed as [current]; after the overlay finishes (or
 * is skipped) the host calls [onCelebrationShown], which stamps `celebrated_at` and the next pending
 * item naturally surfaces as the flow re-emits.
 *
 * ### Master toggle
 * When gamification is disabled, [current] is forced to null and [onCelebrationShown] is a no-op —
 * pending unlocks are NOT marked, so they remain queued and will celebrate if the user re-enables
 * gamification later. (Backfilled retroactive unlocks already have `celebrated_at` set, so they never
 * enter the queue at all — no special-casing during backfill.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GamificationCelebrationViewModel @Inject constructor(
    private val repository: GamificationRepository,
    userPreferencesDataStore: UserPreferencesDataStore,
) : ViewModel() {

    /**
     * The single celebration currently being shown (the oldest pending unlock), or null when the
     * queue is empty OR gamification is disabled. The host renders the overlay iff this is non-null.
     */
    val current: StateFlow<AchievementUiModel?> =
        combine(
            repository.observePendingCelebrations().catch { emit(emptyList()) },
            userPreferencesDataStore.gamificationEnabledFlow.catch { emit(true) },
        ) { pending, enabled ->
            // Disabled → suppress entirely (do not consume the queue). Else show the oldest.
            if (!enabled) null else pending.firstOrNull()
        }
            .catch { emit(null) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    /**
     * Marks [id] as celebrated so it drops out of the queue and the next pending item surfaces.
     * Called by the host after the overlay finished playing or was dismissed by tap.
     *
     * Safe to call even if the item is no longer current (idempotent at the DB level). When
     * gamification is disabled the host never shows the overlay, so this is not reached in that case.
     */
    fun onCelebrationShown(id: String) {
        viewModelScope.launch {
            runCatching { repository.markCelebrated(id) }
        }
    }
}
