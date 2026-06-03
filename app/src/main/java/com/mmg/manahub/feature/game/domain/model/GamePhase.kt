package com.mmg.manahub.feature.game.domain.model

import androidx.annotation.StringRes
import com.mmg.manahub.R

enum class GamePhase(
    @StringRes val displayNameRes: Int,
    @StringRes val shortNameRes: Int,
) {
    UNTAP(R.string.game_phase_untap, R.string.game_phase_untap),
    UPKEEP(R.string.game_phase_upkeep, R.string.game_phase_upkeep),
    DRAW(R.string.game_phase_draw, R.string.game_phase_draw),
    MAIN1(R.string.game_phase_main1, R.string.game_phase_short_main1),
    BEGIN_COMBAT(R.string.game_phase_begin_combat, R.string.game_phase_short_begin_combat),
    DECLARE_ATTACKERS(R.string.game_phase_declare_attackers, R.string.game_phase_short_declare_attackers),
    DECLARE_BLOCKERS(R.string.game_phase_declare_blockers, R.string.game_phase_short_declare_blockers),
    COMBAT_DAMAGE(R.string.game_phase_combat_damage, R.string.game_phase_short_combat_damage),
    END_COMBAT(R.string.game_phase_end_combat, R.string.game_phase_end_combat),
    MAIN2(R.string.game_phase_main2, R.string.game_phase_short_main2),
    END_STEP(R.string.game_phase_end_step, R.string.game_phase_short_end_step),
    CLEANUP(R.string.game_phase_cleanup, R.string.game_phase_cleanup),
}
