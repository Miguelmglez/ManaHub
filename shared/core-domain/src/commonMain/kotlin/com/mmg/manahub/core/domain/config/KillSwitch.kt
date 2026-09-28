package com.mmg.manahub.core.domain.config

/**
 * Registry of remotely controllable kill switches.
 *
 * Each entry maps to a Remote Config boolean key `kill_<feature>`; `true` means the feature is
 * disabled. [killedByDefault] is the value used when no remote value exists and must stay `false`
 * for fail-open semantics.
 */
enum class KillSwitch(
    /** Remote Config key, snake_case with the `kill_` prefix. */
    val remoteKey: String,
    /** Value used before (or without) a successful fetch. */
    val killedByDefault: Boolean = false,
) {
    // Add a switch with one line, e.g. `COMMUNITY_DECKS("kill_community_decks"),`

    /** Emergency brake for the gamification UI and backend (engine, workers, sync). */
    GAMIFICATION("kill_gamification"),
    ;
}
