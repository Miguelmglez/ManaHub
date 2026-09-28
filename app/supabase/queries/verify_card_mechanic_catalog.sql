-- Run after applying 20260927_card_mechanic_catalog.sql.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relname = 'card_mechanic_catalog'
          AND c.relrowsecurity
    ) THEN RAISE EXCEPTION 'catalog RLS is not enabled'; END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'public' AND tablename = 'card_mechanic_catalog'
          AND policyname = 'Public can read active card mechanics'
          AND cmd = 'SELECT' AND qual LIKE '%review_status%active%'
    ) THEN RAISE EXCEPTION 'active-only public read policy is missing'; END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'public' AND tablename = 'card_mechanic_catalog'
          AND policyname = 'Service role manages card mechanics'
          AND cmd = 'ALL'
    ) THEN RAISE EXCEPTION 'service role write policy is missing'; END IF;

    IF NOT has_table_privilege('anon', 'public.card_mechanic_catalog', 'SELECT')
       OR NOT has_table_privilege('authenticated', 'public.card_mechanic_catalog', 'SELECT')
       OR NOT has_table_privilege('service_role', 'public.card_mechanic_catalog', 'INSERT')
       OR NOT has_table_privilege('service_role', 'public.card_mechanic_catalog', 'UPDATE')
       OR NOT has_table_privilege('service_role', 'public.card_mechanic_catalog', 'DELETE')
       OR has_table_privilege('anon', 'public.card_mechanic_catalog', 'INSERT')
       OR has_table_privilege('anon', 'public.card_mechanic_catalog', 'UPDATE')
       OR has_table_privilege('anon', 'public.card_mechanic_catalog', 'DELETE')
       OR has_table_privilege('authenticated', 'public.card_mechanic_catalog', 'INSERT')
       OR has_table_privilege('authenticated', 'public.card_mechanic_catalog', 'UPDATE')
       OR has_table_privilege('authenticated', 'public.card_mechanic_catalog', 'DELETE')
    THEN RAISE EXCEPTION 'catalog privileges differ from public-read service-write contract'; END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgrelid = 'public.card_mechanic_catalog'::regclass
          AND tgname = 'trg_card_mechanic_catalog_updated_at' AND NOT tgisinternal
    ) THEN RAISE EXCEPTION 'catalog updated_at trigger is missing'; END IF;
END;
$$;
