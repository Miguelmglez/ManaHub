-- ============================================================================
-- 20260601_remove_alt_art.sql
--
-- Removes the redundant alternative-art columns. Variant selection now lets a
-- user add/track a different printing by navigating to its (genuinely different)
-- scryfall_id, so a per-row "alt art" boolean no longer carries information.
--
-- Real table/column names verified against the client remote data sources:
--   - public.user_card_collection.is_alternative_art
--   - public.wishlists.is_alt_art
--   - public.open_for_trade.is_alternative_art
--   - public.trade_items.is_alt_art
--
-- The trade-suggestion `offer_alt_art` field is produced by the
-- get_trade_suggestions / get_suggestions_for_card RPCs (not a base-table column),
-- so it is dropped from those function definitions separately, not here. After
-- this migration runs, update those functions to stop selecting the removed
-- columns or they will fail.
-- ============================================================================

ALTER TABLE public.user_card_collection DROP COLUMN IF EXISTS is_alternative_art;
ALTER TABLE public.wishlists            DROP COLUMN IF EXISTS is_alt_art;
ALTER TABLE public.open_for_trade       DROP COLUMN IF EXISTS is_alternative_art;
ALTER TABLE public.trade_items          DROP COLUMN IF EXISTS is_alt_art;
