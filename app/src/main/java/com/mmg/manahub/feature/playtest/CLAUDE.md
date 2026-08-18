### Deck Playtest (`feature/playtest/`, Phase 1 + Phase 2 battlefield complete)
Hidden for release 2026-07-14 via `DeckFeatureFlags.PLAYTEST_ENABLED = false`, then **re-enabled
2026-07-22** after an edge-case audit fixed a card-conservation data-loss bug (see
`feedback_playtest_edge_case_audit_2026-07-22` memory). Room v35→v36 added `playtest_sessions`,
`playtest_card_stats`, `playtest_survey_answers`. Must-know:
- **`PlayZone.LIBRARY` is a draw-only source — never a drag-and-drop drop target.** It is still
  registered in `BattlefieldContent`'s `zoneCoords`/`zoneBounds` (needed for the draw-animation
  start point and tap-to-draw), but every drop-target hit-test explicitly excludes it
  (`it.key != PlayZone.LIBRARY`), and `PlaytestHandViewModel.moveCard` early-returns a no-op on
  `toZone == PlayZone.LIBRARY` as defense-in-depth. A card moved onto Library previously vanished
  entirely (removed from its origin zone, never re-added anywhere) — see the memory above for the
  full root cause. Any NEW UI-position-tracked-but-not-a-drop-target zone must follow the same
  exclude-from-hit-test pattern.
- `moveCard` resets a card's free-form `xOffset`/`yOffset` to `0f` whenever it enters
  `LANDS`/`PERMANENTS` (every `moveCard` call is an inter-zone transition — same-zone repositioning
  goes through `updateCardOffset` instead), so a card that detours through GRAVEYARD/EXILE/HAND and
  back onto the field re-triggers `FreeFormFieldZone`'s auto-cascade placement instead of rendering
  at stale pre-detour coordinates.
- **Explicit-save-only**: redraw/mulligan loops are in-memory; nothing persists until "Save test" via
  `PlaytestDao.saveTestAtomically(@Transaction)` (the only sanctioned write path).
- `deck_id` is plain indexed TEXT, **not** a FK (decks are soft-deleted). Card stats are INT counts,
  not booleans.
- `PlaytestModule` only `@Binds` the repo — `PlaytestDao` already comes from `DatabaseModule`; don't
  add a duplicate `@Provides`.
- **PLAY phase (battlefield) lives in the SAME screen + SAME ViewModel** as MULLIGAN — it is conditional
  content (`PlaytestHandUiState.phase`), NOT a second nav destination or second `pendingX` handoff
  (avoids a second process-death-fragile in-memory handoff). Battlefield composables sit in
  `presentation/battle/` but are driven by `PlaytestHandViewModel`.
- **Battlefield is 100% ephemeral — zero DB writes** (same explicit-save rule). `Keep` no longer opens a
  save sheet; it calls `enterPlayPhase()`. The entire save+survey flow (`PlaytestSaveSheet`,
  `PlaytestSurveySheet`, `SavePlaytest(Survey)UseCase`, `save()`) is **DORMANT but intact** (re-wire when
  stats tracking returns) — do not delete or call it.
- **`End Test` never persists**: confirmation `AlertDialog` (copy must NOT mention saving) → `NavigateBack`.
  System Back in PLAY opens that same dialog via `BackHandler`.
- **`PlayCard.instanceId`** (monotonic Long from the VM, NOT scryfallId) is the stable key for every
  battlefield `LazyRow` — repeated copies of a card would otherwise crash on duplicate keys.
- **Cross-zone drag&drop**: each zone registers root bounds via `Modifier.onGloballyPositioned`; a
  long-press lifts a floating ghost in the root `Box` at `zIndex(Float.MAX_VALUE)`; on drop the pointer
  position is hit-tested against the bounds to resolve the target zone. Highlight the hovered zone with a
  dashed `drawBehind` stroke (NOT `Modifier.border`, which would affect layout). `onDragStart` must
  cancel if the card's `centerInRoot` is still `Offset.Zero` (not laid out yet); `onDragEnd` must cancel
  if `zoneBounds` is empty (first frame after rotation) — otherwise the ghost snaps to (0,0)/mis-drops.
- **Battlefield mutations are atomic**: `drawCard`/`moveCard`/`toggleTap` read AND write `battlefield`
  from the SAME `_uiState.update { state -> ... }` snapshot — never pre-capture `_uiState.value.battlefield`
  outside the lambda (stale capture lets two rapid draws mint a phantom deck+1 card). Conservation
  invariant: `hand+lands+permanents+graveyard+library` size is constant. Returning a card to `HAND`
  untaps it. Emit toasts OUTSIDE the update lambda (it may re-run).
- **One-shot events use a buffered `Channel` (`receiveAsFlow()`), never a nullable `MutableStateFlow`**: a
  StateFlow equality-collapses repeated events (2nd `NavigateBack`/`ShowInfo` lost) and drops them if the
  lifecycle pauses. Collect via `LaunchedEffect(Unit) { vm.events.collect { } }`, not
  `collectAsStateWithLifecycle`; there is no `onEventConsumed()`.
- Edge cases (null setup on process death, fixed `sessionStartedAt`, mulligan ≥1 floor, commander in
  mainboard, LazyRow key-by-index for duplicates, adaptive hand fan + arc rotation) → memory. The
  `PlaytestHandViewModelTest` needs `mockkStatic(FirebaseCrashlytics::class)` (logs outside runCatching).
- → memory: `project_playtest_persistence`, `project_playtest_battlefield_phase2`,
  `feedback_playtest_bugs_2026-05-28`, `feedback_playtest_edge_case_audit_2026-07-22`

