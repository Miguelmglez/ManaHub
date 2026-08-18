### Tournament
`TournamentViewModel` imports `PlayerConfig` from `feature.game.presentation` (not
`feature.tournament.domain.model`) by design. Must-know:
- Never do a DB write inside a `combine {}` transformer (Room re-emission → infinite loop).
- Multi-round Swiss/Single-Elim: `generateMatches` does round 1 only; use `GenerateNextRoundUseCase`
  for subsequent rounds — never `isFinished()` from the ViewModel.
- Draw = `finishMatch(winnerId = null, status = "FINISHED")` (1 point). Bye = finished match,
  `playerIds="[id]"`, `winnerId=id`. Tiebreakers: Points → OMW%(floor 33%) → GW% → OGW% (life total is
  display only).
- `insertTournamentAtomically`/`insertTournamentAtomically` writes need `@Transaction`; `tournamentId`
  validated > 0L at construction; create on `viewModelScope` not `rememberCoroutineScope`.
- **SINGLE finish-and-advance write path (audit C1/C2/C3).** Recording a result, advancing the round,
  and finishing the tournament are ONE atomic path: `RecordMatchResultUseCase` → `repository.finishMatch`
  → `TournamentDao.finishMatchAndAdvanceAtomically(@Transaction)`. BOTH the game-played flow
  (`GameViewModel.recordTournamentResultIfNeeded`) and the manual dialog (`TournamentViewModel`) route
  through it — the VM must NEVER generate rounds or call `finishTournament` itself (it only recomputes
  standings + reflects the returned `MatchResultOutcome`). The finish is **first-writer-wins**:
  `finishMatchGuarded` is `UPDATE … WHERE id=:matchId AND status != 'FINISHED'` returning rowcount; 0 rows
  → `NO_OP`, no advancement (a repeated/concurrent finish never double-advances or double-grants XP).
  Round advancement is **round-aware** (H2): `GenerateNextRoundUseCase.plan` (pure, DB-free) advances the
  LOWEST fully-finished round with no successor, never `maxOf { round }`; next-round `scheduledOrder` is
  offset past every existing match (M1, globally monotonic). M4: SINGLE_ELIM draws soft-lock the bracket,
  so the Draw button is hidden when `structure == "SINGLE_ELIM"` (`RecordResultDialog.allowDraw`).
- **`TournamentCompleted` XP is tournament-scoped + device-scoped.** Emitted only on the transition to
  FINISHED (inside the atomic path, post-commit), key `tournament:{id}` with `isDeviceScoped = true`
  (→ `dev:{deviceId}:tournament:{id}`) so two guest devices can't collide on the server PK and a re-finish
  is ledger-deduped. `finishTournament` no-ops when already FINISHED (no double-emit). `isLocalWinner` is
  hard-coded false (no per-seat local flag on tournaments yet) — base completion XP grants, won-bonus does
  not. The hand-rolled match encoding is centralized in `TournamentIdCodec` (M2) — never re-inline
  `json.trim('[',']').split(",")`.
- Phase 2 pending: see `docs/plan-torneos.md`.
- → memory: `feedback_tournament_bugs_2026-05-24`, `feedback_tournament_phase1_2026-06-02`,
  `feedback_tournament_single_write_path_2026-06-16`

