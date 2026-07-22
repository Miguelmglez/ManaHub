# :tools:tag-pipeline

Offline bulk tag/tribe/theme pipeline for the **Deck Engine Unification plan** (`docs/plans/deck-engine-unification-plan.md`, §5 Phase 5a, decision D5).

A plain Kotlin/JVM CLI — **not** a KMP module — that runs Scryfall's `oracle-cards` bulk dump through
the **exact same production rule engine** the live app uses (`TagDictionary` / `StrategyAnalyzer` /
`SuggestTagsUseCase` from `:shared:core-data`, `TribeDeriver` from `:shared:core-domain`), merges in
Scryfall's community **Oracle Tags** data, **EDHREC** theme-page synergy scores, and an optional
**Archidekt "default category"** enrichment signal, and emits one JSONL row per `oracle_id`. Zero
drift between this pipeline and what a user sees when a card is auto-tagged live in the app — that's
the whole point of D5 (it's why a Python port was rejected).

**IMPORTANT — run everything from the repo root.** `:tools:tag-pipeline:run`'s working directory is
the repo root (`build.gradle.kts` sets `workingDir = rootDir`), so every relative path below
(`--out`, `--cache-dir`, `--in`, `local.properties`) resolves relative to `E:\Projects\ManaHub`
(wherever your checkout lives), not this module's own directory — this is what lets the `upload`
subcommand find the repo-root `local.properties` secrets file.

## Output schema

One JSON object per line (`CardStrategyTagsRow`):

```json
{
  "oracleId": "d0d33d52-3d28-4635-b985-51e126289259",
  "tags": ["removal", "board_wipe"],
  "tribes": ["elf"],
  "themes": {"ARISTOCRATS": 0.42, "VOLTRON": 0.11},
  "archetypes": {},
  "sources": ["edhrec", "oracle_tags", "rule_engine"],
  "generatedAt": "2026-07-20T09:03:00Z",
  "pipelineVersion": 1
}
```

- `tags` — union of the production rule engine's auto-confirmed `CardTag` keys (≥0.90 confidence,
  the SAME threshold the in-app auto-tag flow uses), the curated Scryfall-Tagger-mapped tags, and
  (when the Archidekt enrichment pass ran and a card cleared its confidence bar) the mapped
  Archidekt category tag.
- `tribes` — bare tribe words from `TribeDeriver.tribeKeys()` (the `tribe:` fingerprint prefix
  stripped — that prefix is an internal scoring-engine convention, not meaningful standalone).
- `themes` — `ThemeId.name -> EDHREC synergy weight`. Empty for the (large) majority of cards EDHREC
  never ranks.
- `archetypes` — always empty in this v1 pipeline (reserved in the schema; see
  `model/CardStrategyTagsRow.kt`'s KDoc for why no archetype mapping exists yet).
- `sources` — which stage(s) actually contributed to this row: `rule_engine` (always),
  `oracle_tags`, `edhrec`, `archidekt`.
- `archidektCategory` — the single `CardTag` key Archidekt's crowd-sourced "default category" resolved
  to for this card, or `null` when the Archidekt pass was skipped (default) or the card never cleared
  its minimum-observation/minimum-share bar. See `archidekt/ArchidektCategoryMapping.kt`'s KDoc for
  the full mechanism, why it's a SECONDARY/best-effort signal, and what's deliberately excluded.
- `pipelineVersion` — bump `pipeline.PIPELINE_VERSION` whenever `TagDictionary`, `TribeDeriver`, or
  any curated mapping table changes in a way that could change a card's tags. This is what makes
  `--since` meaningful (see below).

**This is the payload shape the parallel `backend-supabase-expert` agent's `card_strategy_tags`
table (plan §5 Phase 5b) is expected to store as JSONB, keyed by `oracleId`.** Cross-check the two
schemas before wiring the Supabase upload path — see
`docs/plans/deck-engine-unification-progress.md`'s RUN 5 entry for the cross-check note.

## Commands

All commands run from the repo root. The CLI has two subcommands: `run` (generate JSONL, default when
omitted — `./gradlew :tools:tag-pipeline:run --args="--out ..."` still works exactly as before) and
`upload` (push a generated JSONL file to Supabase `card_strategy_tags`).

### Smoke test (small slice, no full download)

Processes the first 200 cards from the real `oracle_cards` bulk file (still downloads the full
~28 MB gzipped bulk files once — Scryfall doesn't offer a partial/paginated bulk download — but
caches them, so a second smoke-test run is instant) and skips the ~20-request EDHREC harvest for
speed:

```bash
./gradlew :tools:tag-pipeline:run --args="run --out build/pipeline-out/smoke.jsonl --limit 200 --skip-edhrec"
```

Full smoke test including EDHREC (adds ~20 polite, paced HTTP requests, a few seconds):

```bash
./gradlew :tools:tag-pipeline:run --args="run --out build/pipeline-out/smoke.jsonl --limit 200"
```

### Real measured throughput (2026-07-21, "Pipeline production-readiness" run)

A moderate-scale real run (3000 cards, live Scryfall + live EDHREC + live Archidekt, 800 sampled
decks) was executed end-to-end to measure actual wall-clock cost per stage — **the earlier estimate
below (RUN 5's "the per-card rule engine pass is the dominant cost") was WRONG; measured, it is by
far the CHEAPEST stage:**

| Stage | Real measured cost |
|---|---|
| Scryfall bulk download (`oracle_cards` + `oracle_tags`, first run only) | ~28 MB total, one-time, cached forever after (`tools/tag-pipeline/.cache/`) |
| Rule engine + Tagger-tag merge, ALL 38,317 real cards (full pool, warm cache) | **~13-15 seconds** |
| EDHREC harvest (21 mapped theme pages, warm cache) | a few seconds (fresh: ~21 requests × 250 ms courtesy delay ≈ 5-10s) |
| **Archidekt enrichment (optional)** — 800 sampled decks | **~8.5 minutes** (≈0.64s per deck: search pagination + `GET /api/decks/{id}/` + `ArchidektRequestQueue`'s 200ms floor + real network/parse latency) |

**Practical consequence:** the core pipeline (no Archidekt) is a 1-5 minute job end-to-end, even on a
cold cache. Archidekt enrichment, when enabled, completely dominates total wall-clock time and scales
linearly with `--archidekt-decks`: ~0.64s/deck, so budget accordingly (see the flags table). It is
**optional/best-effort by design** (default `--archidekt-decks 0`, disabled) — see
`archidekt/ArchidektCategoryMapping.kt`'s KDoc for why a clean per-card lookup doesn't exist and this
had to become a deck-sampling signal instead.

### Full production run

Processes every card in `oracle_cards` (**38,317 real rows as of 2026-07-21** — more than the ~30k
estimate in the plan) plus the full Oracle Tags file plus every mapped EDHREC theme page. Recommended
as TWO separate, copy-pasteable commands (core pipeline first — fast, always run this one; Archidekt
enrichment second — optional, slow, only if you want the extra signal):

```bash
# 1. Core pipeline (Scryfall + Oracle Tags + EDHREC) — ~1-5 minutes total, run this one always.
./gradlew :tools:tag-pipeline:run --args="run --out build/pipeline-out/full.jsonl"

# 2. OPTIONAL: same command + Archidekt enrichment (adds ~0.64s per sampled deck — pick a budget).
#    2000 decks ≈ +21 min · 3000 decks ≈ +32 min · 5000 decks ≈ +53 min. Recommended middle ground:
./gradlew :tools:tag-pipeline:run --args="run --out build/pipeline-out/full.jsonl --archidekt-decks 3000"
```

Not something to run synchronously in an interactive agent session — kick it off as its own
background-tracked process (option 2 in particular).

### Upload to Supabase (`upload` subcommand)

Reads a generated JSONL file and upserts it into the live `card_strategy_tags` table (service-role,
direct write, no RPC — see `.claude/agent-memory/backend-supabase-expert/project_card_strategy_tags_pipeline.md`),
batched, plus `pipeline_runs` bookkeeping (a `status='running'` row inserted at start, updated to
`completed`/`failed` with `cards_processed` at the end).

**Credentials**: set `SUPABASE_SERVICE_ROLE_KEY` as an environment variable (preferred — never
touches disk) **or** add `SUPABASE_SERVICE_ROLE_KEY=...` to the repo-root `local.properties`
(gitignored — the SAME file `SUPABASE_URL`/`SUPABASE_ANON_KEY` already live in for `:app`'s build).
Get the key from the Supabase dashboard: Project Settings > API > `service_role` (secret). **Never
commit this value, never pass it as a bare CLI argument** (shell history / process list exposure) —
the CLI never accepts it as a flag for this reason, only env var or `local.properties`.
`SUPABASE_URL` falls back to the existing `local.properties` entry the same way.

```bash
# Validate batching/shape with ZERO network calls and ZERO credentials required first:
./gradlew :tools:tag-pipeline:run --args="upload --in build/pipeline-out/full.jsonl --dry-run"

# Real upload (needs SUPABASE_SERVICE_ROLE_KEY set):
./gradlew :tools:tag-pipeline:run --args="upload --in build/pipeline-out/full.jsonl"
```

A batch failure is logged and counted but never aborts the remaining batches (the CLI exits non-zero
at the end if any batch failed, so CI/scripted callers can detect it, but a transient failure on one
batch of ~30k+ never loses the rest of the run). Default batch size is 1000 rows (`--batch-size <n>`
to override) — PostgREST has no hard row-count ceiling here, 1000 is a conservative default.

### Incremental run (`--since`)

Given a PREVIOUS run's output file as the watermark manifest, only reprocesses cards absent from it
or last processed at an older `pipelineVersion`:

```bash
./gradlew :tools:tag-pipeline:run --args="run --out build/pipeline-out/delta.jsonl --since build/pipeline-out/full.jsonl"
```

The output of a `--since` run is a **delta** (only new/changed rows) — merge it into the previously
uploaded Supabase state (upsert by `oracle_id`) rather than treating it as the full dataset. Intended
cadence per the plan: once per new Magic set release. Upload a delta the SAME way as a full run
(`upload --in build/pipeline-out/delta.jsonl`) — the upsert is per-row, so a partial file works
identically to a full one.

**Current mechanism (verified, unit-tested):** the watermark is the previous run's own JSONL output
file (every row already carries `oracle_id` + `pipeline_version`, so no separate manifest format is
needed — see `pipeline/SinceWatermark.kt`). A card is reprocessed when it is absent from the manifest
OR the manifest's `pipeline_version` for it is older than the CURRENT run's `pipeline.PIPELINE_VERSION`.
This also transparently covers the Archidekt enrichment step: since the merge happens per-row inside
`buildRows` AFTER the `--since` filter runs, a `--since` run's Archidekt sample only ever gets
attributed to the (already-filtered) small set of cards actually being reprocessed — you do not pay
for re-sampling Archidekt data for cards that were skipped.
**Not yet wired to the live `pipeline_runs` Supabase table** as an automatic watermark source (plan
§5 Phase 5d's fuller vision) — you must pass the previous LOCAL output file explicitly via `--since`.
This is a deliberate, documented scope reduction (file-based watermarking is simpler, works fully
offline, and is equally correct for the "once per set release" cadence) rather than an oversight;
revisit only if a fully-automated CI trigger ever needs to discover the watermark without a local
file.

### Flags reference

| Flag | Subcommand | Required | Meaning |
|---|---|---|---|
| `--out <path>` | `run` | yes | Output JSONL path |
| `--cache-dir <path>` | `run` | no | Disk cache dir for downloaded bulk files + EDHREC pages (default `tools/tag-pipeline/.cache`) |
| `--limit <n>` | `run` | no | Process only the first N cards (smoke-test slice) |
| `--since <path>` | `run` | no | Previous run's output JSONL, used as the watermark manifest |
| `--skip-edhrec` | `run` | no | Skip the EDHREC theme harvest (faster smoke tests; `themes`/`archetypes` end up empty) |
| `--skip-oracle-tags` | `run` | no | Skip the Oracle Tags bulk fetch (`tags` = rule-engine tags only) |
| `--archidekt-decks <n>` | `run` | no | Sample N public Archidekt decks for category enrichment (default `0` = disabled; ~0.64s/deck) |
| `--archidekt-min-observations <n>` | `run` | no | Minimum mapped-category observations before trusting a card's dominant category (default `3`) |
| `--archidekt-min-share <0..1>` | `run` | no | Minimum majority share among observations to accept the top category (default `0.5`) |
| `--in <path>` | `upload` | yes | JSONL file to upload |
| `--batch-size <n>` | `upload` | no | Rows per upsert batch (default `1000`) |
| `--supabase-url <url>` | `upload` | no | Override the Supabase URL (else env var, else `local.properties`) |
| `--dry-run` | `upload` | no | Validate batching/shape only, zero network calls, zero credentials required |

## Data sources (what's real vs. best-effort)

- **Scryfall `oracle_cards` / `oracle_tags` bulk files**: URLs resolved live from
  `https://api.scryfall.com/bulk-data`, verified against real downloaded samples (2026-07-20). The
  `oracle_tags` JSONL shape (`{slug, type, taggings: [{oracle_id, weight}]}`) is a REAL captured
  sample, not a guess — see `scryfall/OracleTagsDto.kt`'s KDoc.
- **EDHREC theme pages** (`https://json.edhrec.com/pages/tags/{slug}.json`): unofficial/undocumented,
  every fetch is fallible by design (cached to disk, degrades to "no theme data for that slug" on any
  failure, never aborts the run). The URL PATTERN and 21 of the 22 taxonomy slugs were verified live
  (2026-07-20) — see `mapping/EdhrecThemeMapping.kt`'s KDoc for the two cases where an obvious-looking
  slug guess was actually wrong (`tribal` → real slug is `typal`; `superfriends` → real slug is
  `planeswalkers`) and for why `ThemeId.CLONES_THEFT` is deliberately left unmapped.
- **Curated mapping tables** (`mapping/TaggerTagMapping.kt`, `mapping/EdhrecThemeMapping.kt`,
  `archidekt/ArchidektCategoryMapping.kt`): hand reviewed, every entry checked against real data,
  unmapped slugs/categories dropped rather than guessed (plan §8 addendum). All small and meant to
  grow over time — add an entry only after verifying the real slug/category exists (see each file's
  KDoc for the verification method).
- **Archidekt "default category" enrichment** (`archidekt/`, secondary/optional signal — 2026-07-21):
  verified live that Archidekt has NO standalone per-card category lookup endpoint (`api/cards/*`
  paths all 404/route-error); the feature is deck-membership data (`GET /api/decks/{id}/`'s
  `cards[].categories`) applied when a card is added to a deck. This pipeline samples a bounded
  number of public decks (`--archidekt-decks`) and takes the majority category per `oracle_id`
  (`archidekt/ArchidektCategorySampler.kt`'s `resolveDominantCategories`), reusing this app's
  EXISTING `ArchidektClient`/`ArchidektRequestQueue` (`:shared:core-data`) rather than a second
  hand-rolled client. `ArchidektOracleCardDto.uid` was verified live to equal Scryfall's `oracle_id`
  exactly (cross-checked "Malcolm, Keen-Eyed Navigator" against `api.scryfall.com`) — this is the
  join key. See `archidekt/ArchidektCategoryMapping.kt`'s KDoc for the full "why secondary, what's
  excluded" reasoning and real example category strings from a live 150-deck sample.

## Testing

```bash
./gradlew :tools:tag-pipeline:test
```

All tests run offline (zero network access) against fixtures and hand-built objects — see
`src/test/kotlin/.../engine/CardTagEngineParityTest.kt` for the most important one (proves the CLI's
tag output is byte-identical to the in-app engine's).
