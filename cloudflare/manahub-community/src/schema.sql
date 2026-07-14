-- D1 schema for manahub-community.
--
-- NOT applied to any real database yet (Worker is not provisioned/deployed — see the
-- PROVISIONING NOTE at the top of wrangler.toml). Apply with:
--   npx wrangler d1 execute manahub-community-db --file=src/schema.sql
-- after the database has been created.
--
-- Privacy: trending_counters carries NO user id and NO IP — only an ISO week, a kind
-- ('card'|'commander'), a card/commander NAME, and a hit count. See D3/3.2 and the trending
-- section of docs/claude-code-prompt-deck-doctor-community.md Phase 3.

CREATE TABLE IF NOT EXISTS trending_counters (
  iso_week TEXT NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN ('card', 'commander')),
  name TEXT NOT NULL,
  count INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (iso_week, kind, name)
);

CREATE INDEX IF NOT EXISTS idx_trending_week_kind ON trending_counters (iso_week, kind);

-- Global per-card inclusion across every materialized 60-card snapshot, used for staple
-- dampening (synergy = inclusionPct - globalInclusionPct). No PII — card names only.
CREATE TABLE IF NOT EXISTS global_card_inclusion (
  card_name TEXT PRIMARY KEY,
  total_decks_seen INTEGER NOT NULL DEFAULT 0,
  decks_including INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL
);
