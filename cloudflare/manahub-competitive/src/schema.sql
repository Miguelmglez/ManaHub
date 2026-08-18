-- D1 schema for manahub-competitive.
--
-- NOT applied to any real database yet (Worker is not provisioned/deployed — see the
-- PROVISIONING NOTE at the top of wrangler.toml). Apply with:
--   npx wrangler d1 execute manahub-competitive-db --file=src/schema.sql
-- after the database has been created.
--
-- Privacy: both tables carry NO user id, NO IP, NO player names — only an ISO week, a format,
-- an archetype-key/card-name, and an aggregate percentage. Same privacy shape as
-- manahub-community's trending_counters table.

CREATE TABLE IF NOT EXISTS format_archetype_weekly (
  iso_week TEXT NOT NULL,
  format TEXT NOT NULL,
  archetype_key TEXT NOT NULL,
  label TEXT NOT NULL,
  deck_count INTEGER NOT NULL DEFAULT 0,
  meta_share_pct REAL NOT NULL DEFAULT 0,
  PRIMARY KEY (iso_week, format, archetype_key)
);

CREATE INDEX IF NOT EXISTS idx_archetype_weekly_week_format ON format_archetype_weekly (iso_week, format);

CREATE TABLE IF NOT EXISTS card_playrate_weekly (
  iso_week TEXT NOT NULL,
  format TEXT NOT NULL,
  card_name TEXT NOT NULL,
  play_rate_pct REAL NOT NULL DEFAULT 0,
  PRIMARY KEY (iso_week, format, card_name)
);

CREATE INDEX IF NOT EXISTS idx_card_playrate_weekly_week_format ON card_playrate_weekly (iso_week, format);
