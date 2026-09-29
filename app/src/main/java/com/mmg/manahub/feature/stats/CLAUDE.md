### Stats — key on the local seat, not playerName
Win/loss, win-rate, per-deck performance, session-history badges, and "most frequent loss" all resolve
against `player_sessions.is_local = 1`, **never** a `winnerName == playerName` match (the stored seat
name can diverge from UserPreferences, silently zeroing win-rate). Use `observeLocalWins()`,
`observeLocalSessionHistory()`, `observeLocalDeckGameStats()`, `observeMostFrequentElimination()`,
`observeArchetypeMatchups()`, `observeAvgWinTurn()`, `observeCurrentStreak()`,
`observeSingleDeckStats(deckId)`. The name-keyed overloads were deleted on 2026-09-22 (G9) — do not
reintroduce a `playerName` parameter on any stats query. Tournament seats carry no `is_local` flag
(`isAppUser = false` for every seat), so those sessions never count toward personal stats.
Draws display as "D", do not enter the win-rate denominator (wins / decisive local games), and
neither extend nor break a win streak. Total games may include draws but only for sessions with a
local seat. A draw is identified by `winnerId = -1` only; `winnerName = "Draw"` can be a real
player name. Keep these rules aligned across Stats, Profile, Home, and derived gamification counts.
- → memory: `feedback_survey_winloss_isLocal`, `project_per_seat_stats_model`
