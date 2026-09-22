-- ============================================================================
-- 20260922_list_tables_rls_initplan.sql
--
-- Perf-only RLS rewrite on user_card_collection / wishlists / open_for_trade:
-- is_profile_complete() takes no row input, so wrapping it as
-- (SELECT is_profile_complete()) turns a per-row SECURITY DEFINER call into a
-- once-per-statement InitPlan. are_mutual_friends(..., user_id) depends on the
-- row and stays per-row. Policy names, commands, roles and semantics are
-- unchanged (ALTER POLICY in place).
--
-- Measured (friend reading a 1344-row collection, EXPLAIN ANALYZE): 49 ms -> 18 ms.
-- Owner select/insert/update/delete re-verified under impersonation.
--
-- Applied via the Supabase MCP `apply_migration` tool against project
-- uimogilwuixgkgfcfmyb; this file mirrors that change.
-- ============================================================================

ALTER POLICY user_card_collection_select ON public.user_card_collection
  USING (((SELECT auth.uid()) = user_id)
         OR ((SELECT public.is_profile_complete()) AND public.are_mutual_friends((SELECT auth.uid()), user_id)));

ALTER POLICY wishlists_select ON public.wishlists
  USING ((SELECT public.is_profile_complete())
         AND (((SELECT auth.uid()) = user_id) OR public.are_mutual_friends((SELECT auth.uid()), user_id)));
ALTER POLICY wishlists_insert ON public.wishlists
  WITH CHECK (((SELECT auth.uid()) = user_id) AND (SELECT public.is_profile_complete()));
ALTER POLICY wishlists_update ON public.wishlists
  USING (((SELECT auth.uid()) = user_id) AND (SELECT public.is_profile_complete()))
  WITH CHECK (((SELECT auth.uid()) = user_id) AND (SELECT public.is_profile_complete()));
ALTER POLICY wishlists_delete ON public.wishlists
  USING (((SELECT auth.uid()) = user_id) AND (SELECT public.is_profile_complete()));

ALTER POLICY open_for_trade_select ON public.open_for_trade
  USING ((SELECT public.is_profile_complete())
         AND (((SELECT auth.uid()) = user_id) OR public.are_mutual_friends((SELECT auth.uid()), user_id)));
ALTER POLICY open_for_trade_insert ON public.open_for_trade
  WITH CHECK (((SELECT auth.uid()) = user_id) AND (SELECT public.is_profile_complete()));
ALTER POLICY open_for_trade_update ON public.open_for_trade
  USING (((SELECT auth.uid()) = user_id) AND (SELECT public.is_profile_complete()))
  WITH CHECK (((SELECT auth.uid()) = user_id) AND (SELECT public.is_profile_complete()));
ALTER POLICY open_for_trade_delete ON public.open_for_trade
  USING (((SELECT auth.uid()) = user_id) AND (SELECT public.is_profile_complete()));
