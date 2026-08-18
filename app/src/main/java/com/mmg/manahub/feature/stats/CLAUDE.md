### Stats — key on the local seat, not playerName
Win/loss, win-rate, per-deck performance, session-history badges, and "most frequent loss" all resolve
against `player_sessions.is_local = 1`, **never** a `winnerName == playerName` match (the stored seat
name can diverge from UserPreferences, silently zeroing win-rate). Use `observeLocalWins()`,
`observeLocalSessionHistory()`, `observeLocalDeckGameStats()`, `observeMostFrequentElimination()`,
`observeArchetypeMatchups()`. Legacy `observeWins(playerName)` kept only for Profile/
`GetDeckGameStatsUseCase`.
- → memory: `feedback_survey_winloss_isLocal`, `project_per_seat_stats_model`

