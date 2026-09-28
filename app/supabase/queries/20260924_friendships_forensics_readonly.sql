-- ============================================================================
-- 20260924_friendships_forensics_readonly.sql — READ-ONLY, counts only, no PII.
--
-- Looks for friendships that may have been forged through the F-01 hole (an ACCEPTED row
-- created by direct INSERT). Legitimate ACCEPTED rows leave a notification_outbox trace:
--   request flow  -> 'friend_accepted'      (entity_id = friendship id, actor = user_id_2)
--   invite flow   -> 'friend_invite_joined' (entity_id = friendship id, actor = user_id_2)
-- A forged row leaves NONE: the attacker is user_id_1 = actor, so the self-notify guard
-- drops the invite_joined row and no friend_request row is ever written.
-- Caveat: notification_outbox only exists since 2026-06-13 (and has a retention job), so
-- rows older than the outbox (or than its retention window) are unpaired by construction.
-- Result 2026-09-24: 2 rows, both ACCEPTED, created 2026-05-15 / 2026-05-30 — both predate
-- the outbox, so "unpaired" is expected; nothing suspicious after 2026-06-13.
-- ============================================================================

WITH f AS (SELECT * FROM public.friendships),
paired AS (
  SELECT f.id,
         f.status,
         f.created_at,
         EXISTS (SELECT 1 FROM public.notification_outbox o
                  WHERE o.entity_id = f.id AND o.event_type = 'friend_accepted'
                    AND o.actor_id = f.user_id_2)                        AS via_request,
         EXISTS (SELECT 1 FROM public.notification_outbox o
                  WHERE o.entity_id = f.id AND o.event_type = 'friend_invite_joined'
                    AND o.actor_id = f.user_id_2)                        AS via_invite,
         EXISTS (SELECT 1 FROM public.notification_outbox o
                  WHERE o.entity_id = f.id)                              AS any_notification
    FROM f
),
outbox AS (SELECT min(created_at) AS oldest FROM public.notification_outbox)
SELECT
  (SELECT count(*) FROM f)                                                 AS total_rows,
  (SELECT count(*) FROM f WHERE status = 'PENDING')                        AS pending,
  (SELECT count(*) FROM f WHERE status = 'ACCEPTED')                       AS accepted,
  (SELECT count(*) FROM paired WHERE status = 'ACCEPTED' AND via_request)  AS accepted_via_request,
  (SELECT count(*) FROM paired WHERE status = 'ACCEPTED' AND via_invite)   AS accepted_via_invite,
  (SELECT count(*) FROM paired WHERE status = 'ACCEPTED' AND NOT any_notification)
                                                                           AS accepted_no_trace_total,
  (SELECT count(*) FROM paired, outbox
    WHERE status = 'ACCEPTED' AND NOT any_notification
      AND paired.created_at >= outbox.oldest)                              AS accepted_no_trace_since_outbox,
  (SELECT count(*) FROM f a JOIN f b
      ON a.user_id_1 = b.user_id_2 AND a.user_id_2 = b.user_id_1 AND a.id < b.id)
                                                                           AS reverse_duplicate_pairs,
  (SELECT oldest::date FROM outbox)                                        AS outbox_oldest,
  (SELECT min(created_at)::date FROM f)                                    AS friendships_oldest,
  (SELECT max(created_at)::date FROM f)                                    AS friendships_newest;

-- `accepted_no_trace_since_outbox` > 0 is the actionable signal. To triage such rows,
-- list ONLY their ids + created_at (no user ids) and decide per row with the user; never
-- delete here — any deletion needs explicit approval (plan D15).
