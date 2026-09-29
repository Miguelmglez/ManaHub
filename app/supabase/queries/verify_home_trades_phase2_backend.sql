-- Run after applying 20260926_home_trades_phase2_backend.sql to an isolated database.
-- Collection application is not covered by this migration.
DO $$
DECLARE
    v_function text;
    v_table text;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
         WHERE schemaname = 'public' AND tablename = 'trade_proposals'
           AND indexname = 'trade_proposals_request_once'
           AND indexdef LIKE '%UNIQUE INDEX%'
           AND indexdef LIKE '%(proposer_id, client_request_id)%'
           AND indexdef LIKE '%client_request_id IS NOT NULL%'
    ) THEN RAISE EXCEPTION 'missing unique request key'; END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
         WHERE tgrelid = 'public.trade_items'::regclass
           AND tgname = 'trg_validate_trade_item_reference'
           AND tgenabled <> 'D' AND NOT tgisinternal
    ) THEN RAISE EXCEPTION 'missing trade item validation'; END IF;

    FOREACH v_table IN ARRAY ARRAY['trade_proposals', 'trade_items', 'trade_participants'] LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_policies
             WHERE schemaname = 'public' AND tablename = v_table
               AND policyname = 'Participants can read trade ' ||
                   CASE v_table
                       WHEN 'trade_proposals' THEN 'proposals'
                       WHEN 'trade_items' THEN 'items'
                       ELSE 'participants'
                   END
               AND cmd = 'SELECT' AND roles = ARRAY['authenticated']::name[]
               AND qual LIKE '%DRAFT%'
        ) THEN RAISE EXCEPTION 'draft read policy missing on %', v_table; END IF;
    END LOOP;

    FOREACH v_function IN ARRAY ARRAY[
        'public.create_proposal(uuid,jsonb,boolean,boolean,boolean,uuid)',
        'public.counter_proposal(uuid,jsonb,jsonb,uuid)',
        'public.create_proposal(uuid,jsonb,boolean,boolean,boolean)',
        'public.counter_proposal(uuid,jsonb,jsonb)'
    ] LOOP
        IF to_regprocedure(v_function) IS NULL
           OR has_function_privilege('anon', to_regprocedure(v_function), 'EXECUTE')
           OR NOT has_function_privilege('authenticated', to_regprocedure(v_function), 'EXECUTE')
        THEN RAISE EXCEPTION 'incorrect proposal RPC access: %', v_function; END IF;
    END LOOP;

    IF position('wishlist_public' IN pg_get_functiondef(
           'public.resolve_shared_list(uuid)'::regprocedure)) = 0
       OR position('trade_list_public' IN pg_get_functiondef(
           'public.resolve_shared_list(uuid)'::regprocedure)) = 0
       OR position('w.quantity' IN pg_get_functiondef(
           'public.resolve_shared_list(uuid)'::regprocedure)) = 0
    THEN RAISE EXCEPTION 'shared list privacy or quantity missing'; END IF;

    FOREACH v_function IN ARRAY ARRAY[
        'public.send_proposal(uuid)', 'public.cancel_proposal(uuid)',
        'public.decline_proposal(uuid)', 'public.accept_proposal(uuid)',
        'public.mark_completed(uuid)', 'public.revoke_acceptance(uuid)',
        'public.edit_proposal(uuid,integer,jsonb,jsonb)'
    ] LOOP
        IF position('FOR UPDATE' IN pg_get_functiondef(to_regprocedure(v_function))) = 0
        THEN RAISE EXCEPTION 'state RPC lacks row lock: %', v_function; END IF;
    END LOOP;

    IF position('FOR SHARE' IN pg_get_functiondef(
           'public.accept_proposal(uuid)'::regprocedure)) = 0
       OR position('INVALID_COLLECTION_REFERENCE' IN pg_get_functiondef(
           'public.accept_proposal(uuid)'::regprocedure)) = 0
    THEN RAISE EXCEPTION 'acceptance lacks legacy reference validation'; END IF;
END;
$$;

SELECT count(*) AS legacy_invalid_collection_references
  FROM public.trade_items ti
  LEFT JOIN public.user_card_collection c ON c.id = ti.user_card_id_ref
 WHERE ti.user_card_id_ref IS NOT NULL
   AND (c.id IS NULL OR c.user_id <> ti.from_user_id
        OR c.scryfall_id <> ti.card_id OR c.is_deleted);
