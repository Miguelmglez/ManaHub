### Draft Simulator (`feature/draft/`, NOT live — rebuild in progress)
Content (tier list, guide, booster, engine) is generated offline and served by the Cloudflare
`manahub-draft-api` Worker from R2. Must-know:
- **Content pipeline lives OUTSIDE this repo**, at `E:/Projects/ManaHub-content/` (private, never
  committed — `.gitignore` guards `scripts/draftsim*/`). One CLI, one set at a time:
  `dossier -> research -> tier -> guide -> booster -> engine -> validate -> publish`. **Always ask the
  user for the per-set source links** (17Lands first, then expert articles) — old sets break every
  canonical URL pattern, so never auto-derive them.
- **Booster = MTGJSON `play` collation.** Keep MTGJSON sheet names verbatim (the generic
  `WeightedBoosterGenerator` matches `variant.contents` keys to `sheets[name]`). Never collapse names —
  matching `"foil"` before `"land"` misfiles `foilLand`/`nonFoilLand` as foil and **deletes the land slot**.
  Booster sheet ids are oracle-collapse-remapped onto the app pool (`set:<code> lang:en unique:cards`) so
  everything resolves on-device. Cross-product uuids resolve via MTGJSON `sourceSetCodes`; a source set the
  tier list RATES joins the pool as top-level `extraPoolSets: [...]` in booster.json (SOS → `["soa"]`
  Mystical Archive — the app widens the pool query to `(set:x or set:y) lang:en`, entries sanitised against
  `^[a-z0-9]{2,6}$` at BOTH parse and query build), while unrated external sheets (`specialGuest`) are
  dropped and their slot re-homed to `common`.
- **Bot/suggested-pick engine must be archetype-based, not 2-color commitment** (the old
  `HeuristicBotDrafter` forces a 2-color pool and breaks 3+ color sets like TDM's wedges). Target: a
  data-driven `engine.json` per set + a generic `ArchetypeAwareBotDrafter`, with `HeuristicBotDrafter` as
  fallback when a set has no engine.json. The suggested pick uses the SAME engine as the bots.
- The draft engine (rotation/packs/rounds) is already generic on `DraftConfig.seatCount` (selector 2–10,
  default 8); SEALED forces 1 seat / 6 packs.
- **Robustness invariants (audit 2026-06-11):** `BotDrafter.pick` must `require(pack.cards.isNotEmpty())`
  (never crash on empty); `DefaultDraftEngine.autoPick` guards the empty human pack → returns state
  unchanged. `parseEngineConfig` SANITISES all engine.json floats (the Worker JSON is untrusted):
  non-finite weight → default, negative → `coerceAtLeast(0f)`, `rating` only trusted when finite & in
  `0f..1f`, negative/NaN `archetypeWeights` filtered out — a NaN breaks `maxWithOrNull`'s comparator and a
  negative weight anti-picks the committed lane. `getEngineConfig` must NOT hold `engineCacheMutex` across
  the network fetch (lock→check→release→fetch→re-lock→store). `DraftRatingNormalizer` guards `rank > 0`
  (rank 0 must not score 1.0f). Suggested-pick scoring runs on `@DefaultDispatcher`, not Main.
- → memory: `project_draftsim_redesign`, `feedback_draftsim_audit_2026-06-11`, `feedback_draft_booster_land_slot`

