-- ============================================================================
-- 20260908_add_posture_override_to_decks.sql
--
-- Deck Wizard Commander v3, Phase 0 item 0.4 E3 (D5): persist a deck's posture
-- strategy pin (Voltron/Ramp/Tempo/Toolbox/Group Hug/...) alongside the
-- existing strategy_locked flag on public.decks.
--
-- Additive only: nullable text column, no default, no data migration, no
-- behaviour change for decks that have no posture pinned.
--
-- Applied directly via the Supabase MCP `apply_migration` tool against project
-- uimogilwuixgkgfcfmyb (ManaHub); this file mirrors that change for the repo's
-- migration history. Room-side counterpart is a SEPARATE delegation
-- (MIGRATION_53_54 on the Android side) — not part of this file.
-- ============================================================================

ALTER TABLE public.decks
    ADD COLUMN IF NOT EXISTS posture_override text;

-- batch_upsert_decks: accept + write posture_override, backward compatible with
-- OLD CLIENTS that do not send the key at all. jsonb `?` (key-exists) tells
-- apart "key absent" (old client -> preserve on UPDATE / NULL on INSERT) from
-- "key present with JSON null" (new client explicitly clearing the pin -> write
-- NULL). A bare `->>'posture_override'` cannot make that distinction (both
-- cases evaluate to SQL NULL) -- which would otherwise silently wipe the column
-- on every old-client sync once it existed.
CREATE OR REPLACE FUNCTION public.batch_upsert_decks(p_rows jsonb)
 RETURNS void
 LANGUAGE plpgsql
 SET search_path TO 'public'
AS $function$
DECLARE
    v_row JSONB;
BEGIN
    FOR v_row IN SELECT * FROM jsonb_array_elements(p_rows) LOOP
        INSERT INTO decks (
            id, user_id, name, description, format, cover_card_id,
            commander_card_id, is_deleted, updated_at, created_at, strategy_locked,
            posture_override
        )
        VALUES (
            (v_row->>'id')::UUID,
            auth.uid(),
            v_row->>'name',
            COALESCE(v_row->>'description', ''),
            COALESCE(v_row->>'format', ''),
            v_row->>'cover_card_id',
            v_row->>'commander_card_id',
            (v_row->>'is_deleted')::BOOLEAN,
            (v_row->>'updated_at')::BIGINT,
            (v_row->>'created_at')::BIGINT,
            COALESCE((v_row->>'strategy_locked')::BOOLEAN, false),
            CASE WHEN v_row ? 'posture_override' THEN v_row->>'posture_override' ELSE NULL END
        )
        ON CONFLICT (id)
        DO UPDATE SET
            name               = EXCLUDED.name,
            description        = EXCLUDED.description,
            format             = EXCLUDED.format,
            cover_card_id      = EXCLUDED.cover_card_id,
            commander_card_id  = EXCLUDED.commander_card_id,
            is_deleted         = EXCLUDED.is_deleted,
            updated_at         = EXCLUDED.updated_at,
            strategy_locked    = EXCLUDED.strategy_locked,
            posture_override   = CASE
                                      WHEN v_row ? 'posture_override' THEN EXCLUDED.posture_override
                                      ELSE decks.posture_override
                                  END
        WHERE EXCLUDED.updated_at > decks.updated_at;
    END LOOP;
END;
$function$;

-- Explicit REVOKE/GRANT per Supabase invariant #8 (arity unchanged, but
-- re-asserted for clarity -- CREATE OR REPLACE preserves prior grants anyway).
REVOKE EXECUTE ON FUNCTION public.batch_upsert_decks(jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.batch_upsert_decks(jsonb) TO authenticated;

-- No changes needed to get_deck_with_cards / get_deck_changes_page /
-- get_deck_changes_since: they select `decks.*` / `to_jsonb(d.*)`, so the new
-- column flows through automatically on the read path.
