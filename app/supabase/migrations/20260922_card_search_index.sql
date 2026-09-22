-- ============================================================================
-- 20260922_card_search_index.sql
--
-- Server-trusted card metadata for friend-list search (search_friend_cards).
--
-- public.cards (the client CardEntity mirror) has never been populated and has
-- broad table grants, so search uses a lean, purpose-built table instead:
-- typed/normalized columns (WUBRG bitmasks, integer mana value, lowercased
-- search name covering every face + printed name) written ONLY by the
-- hydrate-card-metadata Edge Function from Scryfall. Clients can read it but
-- never write it, so no user can poison names seen in other users' searches.
--
-- Scheduling: pg_cron -> trigger_card_metadata_hydration() -> pg_net POST to
-- the Edge Function, authenticated by a random secret generated inside the DB
-- and stored in Vault (never in the repo). The Edge Function validates it via
-- verify_card_hydration_secret() using its service-role client.
--
-- Applied via the Supabase MCP `apply_migration` tool against project
-- uimogilwuixgkgfcfmyb; this file mirrors that change.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA extensions;
-- pg_net's functions always live in schema `net`; WITH SCHEMA only avoids the extension_in_public lint.
-- ALREADY APPLIED MANUALLY in prod (MCP migration card_search_index_pg_net_schema_and_lean_index):
-- pg_net was first created without a schema (landing in public), then relocated with
--   DROP EXTENSION IF EXISTS pg_net;
--   CREATE EXTENSION pg_net WITH SCHEMA extensions;
--   DROP INDEX IF EXISTS public.idx_card_search_index_hydrated_at;
-- A fresh environment only needs the statement below.
CREATE EXTENSION IF NOT EXISTS pg_net WITH SCHEMA extensions;

CREATE TABLE IF NOT EXISTS public.card_search_index (
    scryfall_id    text PRIMARY KEY,
    -- 'ok' = hydrated; 'not_found' = Scryfall has no such id (retried after 30 days)
    status         text        NOT NULL DEFAULT 'ok' CHECK (status IN ('ok', 'not_found')),
    oracle_id      text,
    name           text,
    printed_name   text,
    -- lowercased: name + printed_name + every face name/printed name
    search_name    text,
    -- lowercased individual face names (plus the full name) for exact-name match
    face_names     text[]      NOT NULL DEFAULT '{}',
    type_line      text,
    oracle_text    text,
    colors         text[]      NOT NULL DEFAULT '{}',
    color_identity text[]      NOT NULL DEFAULT '{}',
    -- W=1 U=2 B=4 R=8 G=16; 0 = colorless
    color_mask     smallint    NOT NULL DEFAULT 0,
    identity_mask  smallint    NOT NULL DEFAULT 0,
    cmc            numeric,
    -- floor(cmc), mirrors the client matcher's cmc.toInt()
    mana_value     integer,
    power_num      integer,
    toughness_num  integer,
    rarity         text,
    set_code       text,
    lang           text,
    legalities     jsonb       NOT NULL DEFAULT '{}'::jsonb,
    hydrated_at    timestamptz NOT NULL DEFAULT now(),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_card_search_index_search_name_trgm
    ON public.card_search_index USING gin (search_name extensions.gin_trgm_ops);

CREATE OR REPLACE FUNCTION public.card_search_index_touch_updated_at()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path = public
AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END;
$$;
REVOKE EXECUTE ON FUNCTION public.card_search_index_touch_updated_at() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS trg_card_search_index_updated_at ON public.card_search_index;
CREATE TRIGGER trg_card_search_index_updated_at
    BEFORE UPDATE ON public.card_search_index
    FOR EACH ROW EXECUTE FUNCTION public.card_search_index_touch_updated_at();

ALTER TABLE public.card_search_index ENABLE ROW LEVEL SECURITY;

-- Public Scryfall data: readable by any signed-in user (incl. anonymous-auth guests,
-- who carry the authenticated role). No write policy: only service_role (bypasses RLS).
DROP POLICY IF EXISTS card_search_index_select ON public.card_search_index;
CREATE POLICY card_search_index_select ON public.card_search_index
    FOR SELECT TO authenticated USING (true);

REVOKE ALL ON public.card_search_index FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.card_search_index TO authenticated;
GRANT ALL ON public.card_search_index TO service_role;

-- Ids referenced by any live collection row or wishlist that are missing or stale.
-- Returns ONE text[] (not SETOF): PostgREST's db-max-rows would silently cap a set at 1000.
-- Runs as service_role (bypasses RLS) from the Edge Function; never exposed to clients.
DROP FUNCTION IF EXISTS public.card_search_index_pending(integer);
CREATE OR REPLACE FUNCTION public.card_search_index_pending(p_limit integer DEFAULT 1500)
 RETURNS text[]
 LANGUAGE sql
 STABLE
 SET search_path = public
AS $$
  WITH referenced AS (
    SELECT ucc.scryfall_id AS id FROM user_card_collection ucc WHERE ucc.is_deleted = false
    UNION
    SELECT w.card_id FROM wishlists w
  ), due AS (
    SELECT r.id, csi.hydrated_at
    FROM referenced r
    LEFT JOIN card_search_index csi ON csi.scryfall_id = r.id
    WHERE r.id ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
      AND (csi.scryfall_id IS NULL OR csi.hydrated_at < now() - interval '30 days')
    ORDER BY csi.hydrated_at NULLS FIRST, r.id
    LIMIT LEAST(GREATEST(COALESCE(p_limit, 1500), 1), 3000)
  )
  SELECT COALESCE(array_agg(d.id ORDER BY d.hydrated_at NULLS FIRST, d.id), '{}') FROM due d;
$$;
REVOKE EXECUTE ON FUNCTION public.card_search_index_pending(integer) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.card_search_index_pending(integer) TO service_role;

-- Cron auth secret: random, generated server-side, stored only in Vault.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM vault.secrets WHERE name = 'hydrate_card_metadata_cron_secret') THEN
    PERFORM vault.create_secret(
      encode(extensions.gen_random_bytes(32), 'hex'),
      'hydrate_card_metadata_cron_secret',
      'Shared secret between pg_cron and the hydrate-card-metadata Edge Function'
    );
  END IF;
END;
$$;

-- service_role already has SELECT on vault.decrypted_secrets, so INVOKER suffices.
CREATE OR REPLACE FUNCTION public.verify_card_hydration_secret(p_secret text)
 RETURNS boolean
 LANGUAGE sql
 STABLE
 SET search_path = public
AS $$
  SELECT COALESCE(
    (SELECT ds.decrypted_secret = p_secret
       FROM vault.decrypted_secrets ds
      WHERE ds.name = 'hydrate_card_metadata_cron_secret'
      LIMIT 1),
    false
  ) AND length(COALESCE(p_secret, '')) >= 32;
$$;
REVOKE EXECUTE ON FUNCTION public.verify_card_hydration_secret(text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.verify_card_hydration_secret(text) TO service_role;

-- Fires the Edge Function only when there is work, so idle ticks cost one cheap query.
CREATE OR REPLACE FUNCTION public.trigger_card_metadata_hydration()
 RETURNS bigint
 LANGUAGE plpgsql
 SET search_path = public
AS $$
DECLARE
  v_secret text;
BEGIN
  IF cardinality(card_search_index_pending(1)) = 0 THEN
    RETURN NULL;
  END IF;

  SELECT ds.decrypted_secret INTO v_secret
    FROM vault.decrypted_secrets ds
   WHERE ds.name = 'hydrate_card_metadata_cron_secret'
   LIMIT 1;

  IF v_secret IS NULL THEN
    RAISE WARNING 'hydrate_card_metadata_cron_secret missing from vault';
    RETURN NULL;
  END IF;

  RETURN net.http_post(
    url                  := 'https://uimogilwuixgkgfcfmyb.supabase.co/functions/v1/hydrate-card-metadata',
    headers              := jsonb_build_object('Content-Type', 'application/json', 'x-cron-secret', v_secret),
    body                 := '{}'::jsonb,
    timeout_milliseconds := 150000
  );
END;
$$;
REVOKE EXECUTE ON FUNCTION public.trigger_card_metadata_hydration() FROM PUBLIC, anon, authenticated;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM cron.job WHERE jobname = 'hydrate-card-metadata') THEN
    PERFORM cron.unschedule('hydrate-card-metadata');
  END IF;
  PERFORM cron.schedule('hydrate-card-metadata', '*/15 * * * *',
                        'SELECT public.trigger_card_metadata_hydration();');
END;
$$;
