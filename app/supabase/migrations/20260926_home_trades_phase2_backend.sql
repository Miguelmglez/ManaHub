-- Review against the deployed ManaHub schema before applying. Existing RPC signatures remain available.
-- Cross-device collection application requires an authoritative operation ledger and is excluded here.

ALTER TABLE public.trade_proposals
    ADD COLUMN IF NOT EXISTS client_request_id uuid,
    ADD COLUMN IF NOT EXISTS client_request_hash text;

CREATE UNIQUE INDEX IF NOT EXISTS trade_proposals_request_once
    ON public.trade_proposals (proposer_id, client_request_id)
    WHERE client_request_id IS NOT NULL;

CREATE OR REPLACE FUNCTION public.validate_trade_item_reference()
RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    v_proposer uuid;
    v_receiver uuid;
BEGIN
    SELECT proposer_id, receiver_id INTO v_proposer, v_receiver
      FROM public.trade_proposals WHERE id = NEW.trade_proposal_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'TRADE_NOT_FOUND'; END IF;
    IF NOT ((NEW.from_user_id = v_proposer AND NEW.to_user_id = v_receiver)
         OR (NEW.from_user_id = v_receiver AND NEW.to_user_id = v_proposer)) THEN
        RAISE EXCEPTION 'INVALID_TRADE_DIRECTION';
    END IF;
    IF NEW.quantity IS NOT NULL AND NEW.quantity <= 0 THEN
        RAISE EXCEPTION 'INVALID_QUANTITY';
    END IF;
    IF NEW.is_review_collection_placeholder AND NEW.user_card_id_ref IS NOT NULL THEN
        RAISE EXCEPTION 'INVALID_REVIEW_REFERENCE';
    END IF;
    IF NEW.user_card_id_ref IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM public.user_card_collection c
         WHERE c.id = NEW.user_card_id_ref
           AND c.user_id = NEW.from_user_id
           AND c.scryfall_id = NEW.card_id
           AND c.is_deleted = false
           AND c.quantity >= COALESCE(NEW.quantity, 1)
    ) THEN
        RAISE EXCEPTION 'INVALID_COLLECTION_REFERENCE';
    END IF;
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION public.validate_trade_item_reference() FROM PUBLIC, anon, authenticated;
DROP TRIGGER IF EXISTS trg_validate_trade_item_reference ON public.trade_items;
CREATE TRIGGER trg_validate_trade_item_reference
    BEFORE INSERT OR UPDATE OF trade_proposal_id, from_user_id, to_user_id,
                               user_card_id_ref, card_id, quantity, is_review_collection_placeholder
    ON public.trade_items FOR EACH ROW
    EXECUTE FUNCTION public.validate_trade_item_reference();

CREATE OR REPLACE FUNCTION public.resolve_shared_list(p_share_id uuid)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    v_shared public.shared_lists;
    v_owner public.user_profiles;
    v_items jsonb;
BEGIN
    SELECT * INTO v_shared FROM public.shared_lists WHERE id = p_share_id;
    IF NOT FOUND THEN RETURN '{"status":"not_found"}'::jsonb; END IF;
    IF NOT EXISTS (SELECT 1 FROM auth.users WHERE id = v_shared.user_id) THEN
        RETURN '{"status":"private"}'::jsonb;
    END IF;
    SELECT * INTO v_owner FROM public.user_profiles WHERE id = v_shared.user_id;
    IF NOT FOUND THEN RETURN '{"status":"private"}'::jsonb; END IF;

    IF v_shared.list_type = 'WISHLIST' THEN
        IF NOT v_owner.wishlist_public THEN RETURN '{"status":"private"}'::jsonb; END IF;
        SELECT COALESCE(jsonb_agg(jsonb_build_object(
            'card_id', w.card_id,
            'quantity', w.quantity,
            'match_any_variant', w.match_any_variant,
            'is_foil', w.is_foil,
            'condition', w.condition,
            'language', w.language
        ) ORDER BY w.created_at, w.id), '[]'::jsonb)
          INTO v_items FROM public.wishlists w WHERE w.user_id = v_shared.user_id;
    ELSE
        IF NOT v_owner.trade_list_public THEN RETURN '{"status":"private"}'::jsonb; END IF;
        SELECT COALESCE(jsonb_agg(jsonb_build_object(
            'user_card_id', oft.user_card_id,
            'card_id', ucc.scryfall_id,
            'is_foil', ucc.is_foil,
            'condition', ucc.condition,
            'language', ucc.language,
            'quantity', LEAST(oft.quantity, ucc.quantity)
        ) ORDER BY oft.created_at, oft.id), '[]'::jsonb)
          INTO v_items
          FROM public.open_for_trade oft
          JOIN public.user_card_collection ucc
            ON ucc.id = oft.user_card_id
           AND ucc.user_id = oft.user_id
           AND ucc.is_deleted = false
         WHERE oft.user_id = v_shared.user_id;
    END IF;

    RETURN jsonb_build_object(
        'status', 'ok', 'list_type', v_shared.list_type,
        'user_id', v_shared.user_id,
        'owner_nickname', COALESCE(v_owner.nickname, ''), 'items', v_items
    );
END;
$$;

REVOKE ALL ON FUNCTION public.resolve_shared_list(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.resolve_shared_list(uuid) TO anon, authenticated;

CREATE OR REPLACE FUNCTION public.revoke_acceptance(p_trade_proposal_id uuid)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    v_uid uuid := auth.uid();
    v_proposal public.trade_proposals;
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED'; END IF;
    SELECT * INTO v_proposal FROM public.trade_proposals
     WHERE id = p_trade_proposal_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'TRADE_NOT_FOUND'; END IF;
    IF v_uid NOT IN (v_proposal.proposer_id, v_proposal.receiver_id) THEN
        RAISE EXCEPTION 'UNAUTHORIZED';
    END IF;
    IF v_proposal.status <> 'ACCEPTED'
       OR v_proposal.proposer_marked_completed_at IS NOT NULL
       OR v_proposal.receiver_marked_completed_at IS NOT NULL THEN
        RAISE EXCEPTION 'INVALID_STATE';
    END IF;
    DELETE FROM public.trade_card_locks WHERE trade_proposal_id = p_trade_proposal_id;
    UPDATE public.trade_proposals SET status = 'REVOKED', updated_at = now()
     WHERE id = p_trade_proposal_id;
END;
$$;

REVOKE ALL ON FUNCTION public.revoke_acceptance(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.revoke_acceptance(uuid) TO authenticated;

CREATE OR REPLACE FUNCTION public.create_proposal(
    p_receiver_id uuid, p_items jsonb,
    p_includes_review_from_proposer boolean,
    p_includes_review_from_receiver boolean,
    p_auto_send boolean, p_client_request_id uuid
)
RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    v_uid uuid := auth.uid();
    v_id uuid := gen_random_uuid();
    v_existing public.trade_proposals;
    v_hash text;
    v_item jsonb;
BEGIN
    IF v_uid IS NULL OR p_client_request_id IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED'; END IF;
    IF p_receiver_id IS NULL OR p_receiver_id = v_uid
       OR jsonb_typeof(p_items) IS DISTINCT FROM 'array' THEN
        RAISE EXCEPTION 'INVALID_PROPOSAL';
    END IF;
    v_hash := md5(jsonb_build_array(p_receiver_id, p_items,
        p_includes_review_from_proposer, p_includes_review_from_receiver, p_auto_send)::text);
    PERFORM pg_advisory_xact_lock(hashtextextended(v_uid::text || p_client_request_id::text, 0));
    SELECT * INTO v_existing FROM public.trade_proposals
     WHERE proposer_id = v_uid AND client_request_id = p_client_request_id;
    IF FOUND THEN
        IF v_existing.client_request_hash IS DISTINCT FROM v_hash THEN
            RAISE EXCEPTION 'REQUEST_KEY_REUSED';
        END IF;
        RETURN v_existing.id;
    END IF;
    IF NOT public.are_mutual_friends(v_uid, p_receiver_id) THEN
        RAISE EXCEPTION 'NOT_FRIENDS';
    END IF;
    IF NOT p_includes_review_from_proposer AND NOT EXISTS (
        SELECT 1 FROM jsonb_array_elements(p_items) item
         WHERE item->>'from_user_id' = v_uid::text
           AND COALESCE((item->>'is_review_collection_placeholder')::boolean, false) = false
    ) THEN RAISE EXCEPTION 'INITIAL_ASYMMETRY'; END IF;
    IF NOT p_includes_review_from_receiver AND NOT EXISTS (
        SELECT 1 FROM jsonb_array_elements(p_items) item
         WHERE item->>'from_user_id' = p_receiver_id::text
           AND COALESCE((item->>'is_review_collection_placeholder')::boolean, false) = false
    ) THEN RAISE EXCEPTION 'INITIAL_ASYMMETRY'; END IF;

    INSERT INTO public.trade_proposals (
        id, status, proposer_id, receiver_id, root_proposal_id,
        includes_review_collection_from_proposer,
        includes_review_collection_from_receiver,
        client_request_id, client_request_hash
    ) VALUES (
        v_id, CASE WHEN p_auto_send THEN 'PROPOSED'::public.trade_status
                   ELSE 'DRAFT'::public.trade_status END,
        v_uid, p_receiver_id, v_id,
        p_includes_review_from_proposer, p_includes_review_from_receiver,
        p_client_request_id, v_hash
    );
    INSERT INTO public.trade_participants (trade_proposal_id, user_id, role)
    VALUES (v_id, v_uid, 'PROPOSER'), (v_id, p_receiver_id, 'RECEIVER');
    FOR v_item IN SELECT value FROM jsonb_array_elements(p_items) LOOP
        INSERT INTO public.trade_items (
            trade_proposal_id, from_user_id, to_user_id, user_card_id_ref,
            quantity, is_foil, condition, language, card_id,
            is_review_collection_placeholder
        ) VALUES (
            v_id, (v_item->>'from_user_id')::uuid, (v_item->>'to_user_id')::uuid,
            NULLIF(v_item->>'user_card_id_ref', '')::uuid,
            NULLIF(v_item->>'quantity', '')::integer,
            NULLIF(v_item->>'is_foil', '')::boolean,
            NULLIF(v_item->>'condition', ''), NULLIF(v_item->>'language', ''),
            v_item->>'card_id',
            COALESCE((v_item->>'is_review_collection_placeholder')::boolean, false)
        );
    END LOOP;
    RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.create_proposal(
    p_receiver_id uuid, p_items jsonb,
    p_includes_review_from_proposer boolean,
    p_includes_review_from_receiver boolean,
    p_auto_send boolean
)
RETURNS uuid
LANGUAGE sql SECURITY DEFINER SET search_path = public
AS $$
    SELECT public.create_proposal($1, $2, $3, $4, $5, gen_random_uuid())
$$;

REVOKE ALL ON FUNCTION public.create_proposal(uuid,jsonb,boolean,boolean,boolean,uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.create_proposal(uuid,jsonb,boolean,boolean,boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_proposal(uuid,jsonb,boolean,boolean,boolean,uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_proposal(uuid,jsonb,boolean,boolean,boolean) TO authenticated;

CREATE OR REPLACE FUNCTION public.counter_proposal(
    p_parent_proposal_id uuid, p_items jsonb,
    p_new_review_flags jsonb, p_client_request_id uuid
)
RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    v_uid uuid := auth.uid();
    v_parent public.trade_proposals;
    v_existing public.trade_proposals;
    v_child_id uuid;
    v_item jsonb;
    v_hash text;
    v_has_review_proposer boolean := false;
    v_has_review_receiver boolean := false;
BEGIN
    IF v_uid IS NULL OR p_client_request_id IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED'; END IF;
    IF jsonb_typeof(p_items) IS DISTINCT FROM 'array' THEN
        RAISE EXCEPTION 'INVALID_PROPOSAL';
    END IF;
    v_hash := md5(jsonb_build_array(p_parent_proposal_id, p_items, p_new_review_flags)::text);
    PERFORM pg_advisory_xact_lock(hashtextextended(v_uid::text || p_client_request_id::text, 0));
    SELECT * INTO v_existing FROM public.trade_proposals
     WHERE proposer_id = v_uid AND client_request_id = p_client_request_id;
    IF FOUND THEN
        IF v_existing.client_request_hash IS DISTINCT FROM v_hash THEN
            RAISE EXCEPTION 'REQUEST_KEY_REUSED';
        END IF;
        RETURN v_existing.id;
    END IF;
    SELECT * INTO v_parent FROM public.trade_proposals
     WHERE id = p_parent_proposal_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'TRADE_NOT_FOUND'; END IF;
    IF v_parent.receiver_id <> v_uid THEN RAISE EXCEPTION 'UNAUTHORIZED'; END IF;
    IF v_parent.status <> 'PROPOSED' THEN RAISE EXCEPTION 'INVALID_STATE'; END IF;

    FOR v_item IN SELECT value FROM jsonb_array_elements(p_items) LOOP
        IF COALESCE((v_item->>'is_review_collection_placeholder')::boolean, false) THEN
            IF (v_item->>'from_user_id')::uuid = v_parent.proposer_id
               AND v_parent.includes_review_collection_from_proposer THEN
                RAISE EXCEPTION 'REVIEW_COLLECTION_SAME_DIRECTION';
            END IF;
            IF (v_item->>'from_user_id')::uuid = v_parent.receiver_id
               AND v_parent.includes_review_collection_from_receiver THEN
                RAISE EXCEPTION 'REVIEW_COLLECTION_SAME_DIRECTION';
            END IF;
            v_has_review_receiver := v_has_review_receiver
                OR (v_item->>'from_user_id')::uuid = v_uid;
            v_has_review_proposer := v_has_review_proposer
                OR (v_item->>'from_user_id')::uuid <> v_uid;
        END IF;
    END LOOP;

    UPDATE public.trade_proposals SET status = 'COUNTERED', updated_at = now()
     WHERE id = p_parent_proposal_id;
    INSERT INTO public.trade_proposals (
        status, proposer_id, receiver_id, parent_proposal_id, root_proposal_id,
        includes_review_collection_from_proposer,
        includes_review_collection_from_receiver,
        client_request_id, client_request_hash
    ) VALUES (
        'PROPOSED', v_uid, v_parent.proposer_id,
        p_parent_proposal_id, v_parent.root_proposal_id,
        COALESCE((p_new_review_flags->>'from_proposer')::boolean, v_has_review_proposer),
        COALESCE((p_new_review_flags->>'from_receiver')::boolean, v_has_review_receiver),
        p_client_request_id, v_hash
    ) RETURNING id INTO v_child_id;
    INSERT INTO public.trade_participants (trade_proposal_id, user_id, role)
    VALUES (v_child_id, v_uid, 'PROPOSER'), (v_child_id, v_parent.proposer_id, 'RECEIVER');
    FOR v_item IN SELECT value FROM jsonb_array_elements(p_items) LOOP
        INSERT INTO public.trade_items (
            trade_proposal_id, from_user_id, to_user_id, user_card_id_ref,
            quantity, is_foil, condition, language, card_id,
            is_review_collection_placeholder
        ) VALUES (
            v_child_id, (v_item->>'from_user_id')::uuid, (v_item->>'to_user_id')::uuid,
            NULLIF(v_item->>'user_card_id_ref', '')::uuid,
            NULLIF(v_item->>'quantity', '')::integer,
            NULLIF(v_item->>'is_foil', '')::boolean,
            NULLIF(v_item->>'condition', ''), NULLIF(v_item->>'language', ''),
            v_item->>'card_id',
            COALESCE((v_item->>'is_review_collection_placeholder')::boolean, false)
        );
    END LOOP;
    RETURN v_child_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.counter_proposal(
    p_parent_proposal_id uuid, p_items jsonb,
    p_new_review_flags jsonb DEFAULT '{}'::jsonb
)
RETURNS uuid
LANGUAGE sql SECURITY DEFINER SET search_path = public
AS $$
    SELECT public.counter_proposal($1, $2, $3, gen_random_uuid())
$$;

REVOKE ALL ON FUNCTION public.counter_proposal(uuid,jsonb,jsonb,uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.counter_proposal(uuid,jsonb,jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.counter_proposal(uuid,jsonb,jsonb,uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.counter_proposal(uuid,jsonb,jsonb) TO authenticated;

DROP POLICY IF EXISTS "Participants can read trade proposals" ON public.trade_proposals;
CREATE POLICY "Participants can read trade proposals" ON public.trade_proposals
    FOR SELECT TO authenticated USING (
        (SELECT public.is_profile_complete())
        AND (proposer_id = (SELECT auth.uid())
             OR (receiver_id = (SELECT auth.uid()) AND status <> 'DRAFT'))
    );

DROP POLICY IF EXISTS "Participants can read trade items" ON public.trade_items;
CREATE POLICY "Participants can read trade items" ON public.trade_items
    FOR SELECT TO authenticated USING (
        (SELECT public.is_profile_complete()) AND EXISTS (
            SELECT 1 FROM public.trade_proposals p
             WHERE p.id = trade_items.trade_proposal_id
               AND (p.proposer_id = (SELECT auth.uid())
                    OR (p.receiver_id = (SELECT auth.uid()) AND p.status <> 'DRAFT'))
        )
    );

DROP POLICY IF EXISTS "Participants can read trade participants" ON public.trade_participants;
CREATE POLICY "Participants can read trade participants" ON public.trade_participants
    FOR SELECT TO authenticated USING (
        (SELECT public.is_profile_complete()) AND EXISTS (
            SELECT 1 FROM public.trade_proposals p
             WHERE p.id = trade_participants.trade_proposal_id
               AND (p.proposer_id = (SELECT auth.uid())
                    OR (p.receiver_id = (SELECT auth.uid()) AND p.status <> 'DRAFT'))
        )
    );

CREATE OR REPLACE FUNCTION public.send_proposal(p_trade_proposal_id uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_uid uuid := auth.uid(); v_proposal public.trade_proposals;
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED'; END IF;
    SELECT * INTO v_proposal FROM public.trade_proposals
     WHERE id = p_trade_proposal_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'TRADE_NOT_FOUND'; END IF;
    IF v_proposal.proposer_id <> v_uid THEN RAISE EXCEPTION 'UNAUTHORIZED'; END IF;
    IF v_proposal.status <> 'DRAFT' THEN RAISE EXCEPTION 'INVALID_STATE'; END IF;
    UPDATE public.trade_proposals SET status = 'PROPOSED', updated_at = now()
     WHERE id = p_trade_proposal_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.cancel_proposal(p_trade_proposal_id uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_uid uuid := auth.uid(); v_proposal public.trade_proposals;
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED'; END IF;
    SELECT * INTO v_proposal FROM public.trade_proposals
     WHERE id = p_trade_proposal_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'TRADE_NOT_FOUND'; END IF;
    IF v_proposal.proposer_id <> v_uid THEN RAISE EXCEPTION 'UNAUTHORIZED'; END IF;
    IF v_proposal.status <> 'PROPOSED' THEN RAISE EXCEPTION 'INVALID_STATE'; END IF;
    UPDATE public.trade_proposals
       SET status = 'CANCELLED', cancellation_reason = 'USER_CANCELLED', updated_at = now()
     WHERE id = p_trade_proposal_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.decline_proposal(p_trade_proposal_id uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_uid uuid := auth.uid(); v_proposal public.trade_proposals;
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED'; END IF;
    SELECT * INTO v_proposal FROM public.trade_proposals
     WHERE id = p_trade_proposal_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'TRADE_NOT_FOUND'; END IF;
    IF v_proposal.receiver_id <> v_uid THEN RAISE EXCEPTION 'UNAUTHORIZED'; END IF;
    IF v_proposal.status <> 'PROPOSED' THEN RAISE EXCEPTION 'INVALID_STATE'; END IF;
    UPDATE public.trade_proposals SET status = 'DECLINED', updated_at = now()
     WHERE id = p_trade_proposal_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.accept_proposal(p_trade_proposal_id uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    v_uid uuid := auth.uid();
    v_proposal public.trade_proposals;
    v_conflict_cards text;
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED'; END IF;
    SELECT * INTO v_proposal FROM public.trade_proposals
     WHERE id = p_trade_proposal_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'TRADE_NOT_FOUND'; END IF;
    IF v_proposal.receiver_id <> v_uid THEN RAISE EXCEPTION 'UNAUTHORIZED'; END IF;
    IF v_proposal.status <> 'PROPOSED' THEN RAISE EXCEPTION 'INVALID_STATE'; END IF;
    PERFORM 1 FROM public.user_card_collection c
      JOIN public.trade_items ti ON ti.user_card_id_ref = c.id
     WHERE ti.trade_proposal_id = p_trade_proposal_id
     ORDER BY c.id FOR SHARE OF c;
    IF EXISTS (
        SELECT 1 FROM public.trade_items ti
        LEFT JOIN public.user_card_collection c ON c.id = ti.user_card_id_ref
         WHERE ti.trade_proposal_id = p_trade_proposal_id
           AND ti.user_card_id_ref IS NOT NULL
           AND (c.id IS NULL OR c.user_id <> ti.from_user_id
                OR c.scryfall_id <> ti.card_id OR c.is_deleted
                OR c.quantity < COALESCE(ti.quantity, 1))
    ) OR EXISTS (
        SELECT 1 FROM public.trade_items ti
        JOIN public.user_card_collection c ON c.id = ti.user_card_id_ref
         WHERE ti.trade_proposal_id = p_trade_proposal_id
         GROUP BY c.id, c.quantity
        HAVING SUM(COALESCE(ti.quantity, 1)) > c.quantity
    ) THEN RAISE EXCEPTION 'INVALID_COLLECTION_REFERENCE'; END IF;
    SELECT string_agg(DISTINCT ti.card_id, ',') INTO v_conflict_cards
      FROM public.trade_items ti
      JOIN public.trade_card_locks l ON l.user_card_id = ti.user_card_id_ref
     WHERE ti.trade_proposal_id = p_trade_proposal_id
       AND ti.is_review_collection_placeholder = false;
    IF v_conflict_cards IS NOT NULL THEN
        RAISE EXCEPTION 'CARD_ALREADY_LOCKED: %', v_conflict_cards;
    END IF;
    BEGIN
        INSERT INTO public.trade_card_locks (user_card_id, trade_proposal_id)
        SELECT DISTINCT ti.user_card_id_ref, p_trade_proposal_id
          FROM public.trade_items ti
         WHERE ti.trade_proposal_id = p_trade_proposal_id
           AND ti.user_card_id_ref IS NOT NULL
           AND ti.is_review_collection_placeholder = false;
    EXCEPTION WHEN unique_violation THEN
        SELECT string_agg(DISTINCT ti.card_id, ',') INTO v_conflict_cards
          FROM public.trade_items ti
          JOIN public.trade_card_locks l ON l.user_card_id = ti.user_card_id_ref
         WHERE ti.trade_proposal_id = p_trade_proposal_id
           AND ti.is_review_collection_placeholder = false;
        RAISE EXCEPTION 'CARD_ALREADY_LOCKED: %', COALESCE(v_conflict_cards, '');
    END;
    UPDATE public.trade_proposals SET status = 'ACCEPTED', updated_at = now()
     WHERE id = p_trade_proposal_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.mark_completed(p_trade_proposal_id uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    v_uid uuid := auth.uid();
    v_proposal public.trade_proposals;
    v_now timestamptz := now();
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED'; END IF;
    SELECT * INTO v_proposal FROM public.trade_proposals
     WHERE id = p_trade_proposal_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'TRADE_NOT_FOUND'; END IF;
    IF v_uid NOT IN (v_proposal.proposer_id, v_proposal.receiver_id) THEN
        RAISE EXCEPTION 'UNAUTHORIZED';
    END IF;
    IF v_proposal.status <> 'ACCEPTED' THEN RAISE EXCEPTION 'INVALID_STATE'; END IF;
    IF v_uid = v_proposal.proposer_id THEN
        UPDATE public.trade_proposals
           SET proposer_marked_completed_at = COALESCE(proposer_marked_completed_at, v_now),
               updated_at = v_now
         WHERE id = p_trade_proposal_id;
    ELSE
        UPDATE public.trade_proposals
           SET receiver_marked_completed_at = COALESCE(receiver_marked_completed_at, v_now),
               updated_at = v_now
         WHERE id = p_trade_proposal_id;
    END IF;
    SELECT * INTO v_proposal FROM public.trade_proposals WHERE id = p_trade_proposal_id;
    IF v_proposal.proposer_marked_completed_at IS NOT NULL
       AND v_proposal.receiver_marked_completed_at IS NOT NULL THEN
        DELETE FROM public.trade_card_locks WHERE trade_proposal_id = p_trade_proposal_id;
        UPDATE public.trade_proposals SET status = 'COMPLETED', updated_at = v_now
         WHERE id = p_trade_proposal_id;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.edit_proposal(
    p_trade_proposal_id uuid, p_expected_version integer,
    p_new_items jsonb, p_new_review_flags jsonb DEFAULT '{}'::jsonb
)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    v_uid uuid := auth.uid();
    v_proposal public.trade_proposals;
    v_item jsonb;
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED'; END IF;
    IF jsonb_typeof(p_new_items) IS DISTINCT FROM 'array' THEN
        RAISE EXCEPTION 'INVALID_PROPOSAL';
    END IF;
    SELECT * INTO v_proposal FROM public.trade_proposals
     WHERE id = p_trade_proposal_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'TRADE_NOT_FOUND'; END IF;
    IF v_proposal.proposer_id <> v_uid THEN RAISE EXCEPTION 'UNAUTHORIZED'; END IF;
    IF v_proposal.status <> 'PROPOSED' THEN RAISE EXCEPTION 'INVALID_STATE'; END IF;
    IF v_proposal.proposal_version <> p_expected_version THEN
        RAISE EXCEPTION 'PROPOSAL_VERSION_MISMATCH';
    END IF;
    DELETE FROM public.trade_items WHERE trade_proposal_id = p_trade_proposal_id;
    FOR v_item IN SELECT value FROM jsonb_array_elements(p_new_items) LOOP
        INSERT INTO public.trade_items (
            trade_proposal_id, from_user_id, to_user_id, user_card_id_ref,
            quantity, is_foil, condition, language, card_id,
            is_review_collection_placeholder
        ) VALUES (
            p_trade_proposal_id,
            (v_item->>'from_user_id')::uuid, (v_item->>'to_user_id')::uuid,
            NULLIF(v_item->>'user_card_id_ref', '')::uuid,
            NULLIF(v_item->>'quantity', '')::integer,
            NULLIF(v_item->>'is_foil', '')::boolean,
            NULLIF(v_item->>'condition', ''), NULLIF(v_item->>'language', ''),
            v_item->>'card_id',
            COALESCE((v_item->>'is_review_collection_placeholder')::boolean, false)
        );
    END LOOP;
    UPDATE public.trade_proposals
       SET proposal_version = proposal_version + 1,
           includes_review_collection_from_proposer = COALESCE(
               (p_new_review_flags->>'from_proposer')::boolean,
               includes_review_collection_from_proposer),
           includes_review_collection_from_receiver = COALESCE(
               (p_new_review_flags->>'from_receiver')::boolean,
               includes_review_collection_from_receiver),
           updated_at = now()
     WHERE id = p_trade_proposal_id;
END;
$$;
