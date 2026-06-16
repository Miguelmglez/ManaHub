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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the global celebration queue (ADR-002, Phase 1 + Phase 3, Chunk B).
 *
 * Two celebration kinds share the one global host: achievement unlocks (Phase 1) and level-ups
 * (Phase 3). [current] exposes a [CelebrationItem] (or null). ACHIEVEMENTS TAKE PRIORITY: while any
 * achievement unlock is pending (`unlocked_at` set, `celebrated_at` null — see
 * [GamificationRepository.observePendingCelebrations]) the host shows that; only when the achievement
 * queue is empty does a due level-up surface.
 *
 * ### Level-up queue
 * A level-up is "due" when the player's current level exceeds the last celebrated level. The baseline
 * is persisted in DataStore ([UserPreferencesDataStore.lastCelebratedLevelFlow]) with a sentinel of -1
 * meaning "uninitialized". The combine SUPPRESSES level-ups while the baseline is -1 (so existing
 * players never get a spurious burst); [init] separately seeds the baseline to the current level the
 * first time it sees -1. After a level-up is shown, [onLevelUpShown] advances the baseline by one due
 * level, so a multi-level jump surfaces each level in turn.
 *
 * ### Master toggle
 * When gamification is disabled, [current] is forced to null and the dismiss handlers are no-ops —
 * nothing is consumed, so pending celebrations remain queued for if the user re-enables gamification.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GamificationCelebrationViewModel @Inject constructor(
    private val repository: GamificationRepository,
    private val userPreferencesDataStore: UserPreferencesDataStore,
) : ViewModel() {

    /**
     * The single celebration currently being shown, or null when nothing is due OR gamification is
     * disabled. The host renders the matching overlay.
     */
    sealed interface CelebrationItem {
        /** An achievement unlock celebration (Phase 1). */
        data class Achievement(val model: AchievementUiModel) : CelebrationItem

        /** A level-up celebration for [level] (Phase 3). */
        data class LevelUp(val level: Int) : CelebrationItem
    }

    /**
     * The current celebration to show (achievement unlock OR a due level-up), or null. Achievements
     * win ties. Level-ups are suppressed while the last-celebrated baseline is the -1 sentinel.
     */
    val current: StateFlow<CelebrationItem?> =
        combine(
            repository.observePendingCelebrations().catch { emit(emptyList()) },
            repository.observeProgression().map { it.level }.catch { emit(1) },
            userPreferencesDataStore.lastCelebratedLevelFlow.catch { emit(-1) },
            userPreferencesDataStore.gamificationEnabledFlow.catch { emit(false) },
        ) { pending, currentLevel, lastCelebrated, enabled ->
            when {
                !enabled -> null
                // Achievements take priority over level-ups.
                pending.isNotEmpty() -> CelebrationItem.Achievement(pending.first())
                // Only celebrate level-ups once the baseline is initialised (>= 0) AND we've advanced.
                lastCelebrated >= 0 && currentLevel > lastCelebrated ->
                    CelebrationItem.LevelUp(lastCelebrated + 1)
                else -> null
            }
        }
            .catch { emit(null) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    init {
        // One-shot silent seed: if the baseline was never initialised (-1), set it to the player's
        // current level so EXISTING players don't get a spurious level-up burst on first run. Done
        // OUTSIDE the combine (no side effects in a transform).
        viewModelScope.launch {
            runCatching {
                if (userPreferencesDataStore.getLastCelebratedLevel() == -1) {
                    val currentLevel = repository.observeProgression().first().level
                    userPreferencesDataStore.setLastCelebratedLevel(currentLevel)
                }
            }
        }
    }

    /**
     * Marks an achievement [id] as celebrated so it drops out of [observePendingCelebrations]. Called
     * by the host after the achievement overlay finished (or was dismissed). Idempotent at the DB
     * level. When gamification is disabled the host never shows the overlay, so this is not reached.
     */
    fun onCelebrationShown(id: String) {
        viewModelScope.launch {
            runCatching { repository.markCelebrated(id) }
        }
    }

    /**
     * Advances the level-up baseline to [level] after that level-up was shown, so it drops out and the
     * NEXT due level (on a multi-level jump) surfaces. Called by the host after the level-up overlay
     * was dismissed.
     */
    fun onLevelUpShown(level: Int) {
        viewModelScope.launch {
            runCatching { userPreferencesDataStore.setLastCelebratedLevel(level) }
        }
    }
}
