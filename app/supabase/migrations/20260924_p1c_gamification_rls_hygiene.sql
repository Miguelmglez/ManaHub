-- ============================================================================
-- 20260924_p1c_gamification_rls_hygiene.sql   (drift audit G-24)
--
-- NOT APPLIED. Least-privilege hygiene on the five gamification tables; no behaviour
-- change for any client:
--   * the *_owner FOR ALL policies were roles = PUBLIC -> now TO authenticated
--     (guests use anonymous sign-in, which runs as `authenticated`, so they are unaffected);
--   * anon held arwdDxtm on every table (Supabase default privileges) -> revoked. RLS
--     already hid every row from anon (auth.uid() is NULL), and anon cannot execute any
--     gamification RPC.
-- Idempotent (ALTER POLICY / REVOKE). Run get_advisors afterwards.
-- ============================================================================

ALTER POLICY player_progression_owner   ON public.player_progression   TO authenticated;
ALTER POLICY xp_transactions_owner      ON public.xp_transactions      TO authenticated;
ALTER POLICY achievement_progress_owner ON public.achievement_progress TO authenticated;
ALTER POLICY entitlements_owner         ON public.entitlements         TO authenticated;
ALTER POLICY streaks_owner              ON public.streaks              TO authenticated;

REVOKE ALL ON TABLE public.player_progression   FROM anon;
REVOKE ALL ON TABLE public.xp_transactions      FROM anon;
REVOKE ALL ON TABLE public.achievement_progress FROM anon;
REVOKE ALL ON TABLE public.entitlements         FROM anon;
REVOKE ALL ON TABLE public.streaks              FROM anon;

-- Post-apply check (read-only):
--   SELECT c.relname, has_table_privilege('anon', c.oid, 'SELECT') AS anon_select,
--          (SELECT array_agg(rolname) FROM pg_roles r
--            WHERE r.oid = ANY (p.polroles)) AS policy_roles
--     FROM pg_class c JOIN pg_policy p ON p.polrelid = c.oid
--    WHERE c.relnamespace = 'public'::regnamespace
--      AND c.relname IN ('player_progression', 'xp_transactions', 'achievement_progress',
--                        'entitlements', 'streaks');
--   Expect anon_select = false and policy_roles = {authenticated} for all five.
