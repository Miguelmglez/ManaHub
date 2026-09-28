-- Public clients may read active entries; only the local publisher's service role may write.
CREATE TABLE IF NOT EXISTS public.card_mechanic_catalog (
    key text PRIMARY KEY,
    category text NOT NULL,
    label_en text NOT NULL,
    rules jsonb NOT NULL DEFAULT '{}'::jsonb,
    provenance jsonb NOT NULL DEFAULT '{}'::jsonb,
    revision integer NOT NULL,
    scryfall_query text,
    scryfall_query_verified_at timestamptz,
    review_status text NOT NULL DEFAULT 'pending',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT card_mechanic_catalog_key_format CHECK (key ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT card_mechanic_catalog_category_format CHECK (category ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT card_mechanic_catalog_label_not_blank CHECK (length(btrim(label_en)) > 0),
    CONSTRAINT card_mechanic_catalog_rules_object CHECK (jsonb_typeof(rules) = 'object'),
    CONSTRAINT card_mechanic_catalog_provenance_object CHECK (jsonb_typeof(provenance) = 'object'),
    CONSTRAINT card_mechanic_catalog_revision_positive CHECK (revision > 0),
    CONSTRAINT card_mechanic_catalog_review_status CHECK (review_status IN ('pending', 'active', 'rejected')),
    CONSTRAINT card_mechanic_catalog_query_verified CHECK (
        (scryfall_query IS NULL AND scryfall_query_verified_at IS NULL)
        OR (scryfall_query IS NOT NULL AND length(btrim(scryfall_query)) > 0
            AND scryfall_query_verified_at IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS card_mechanic_catalog_active_category_key
    ON public.card_mechanic_catalog (category, key)
    WHERE review_status = 'active';

CREATE OR REPLACE FUNCTION public.set_card_mechanic_catalog_updated_at()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = ''
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION public.set_card_mechanic_catalog_updated_at() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS trg_card_mechanic_catalog_updated_at ON public.card_mechanic_catalog;
CREATE TRIGGER trg_card_mechanic_catalog_updated_at
    BEFORE UPDATE ON public.card_mechanic_catalog
    FOR EACH ROW EXECUTE FUNCTION public.set_card_mechanic_catalog_updated_at();

ALTER TABLE public.card_mechanic_catalog ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public can read active card mechanics" ON public.card_mechanic_catalog;
CREATE POLICY "Public can read active card mechanics"
    ON public.card_mechanic_catalog FOR SELECT
    TO anon, authenticated
    USING (review_status = 'active');

DROP POLICY IF EXISTS "Service role manages card mechanics" ON public.card_mechanic_catalog;
CREATE POLICY "Service role manages card mechanics"
    ON public.card_mechanic_catalog FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

REVOKE ALL ON public.card_mechanic_catalog FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.card_mechanic_catalog TO anon, authenticated;
GRANT ALL ON public.card_mechanic_catalog TO service_role;
