-- Fix the dead send-push-poll cron and the misnamed Vault secret.
-- The legacy service_role JWT was stored in vault.secrets.name (plaintext) with the literal label
-- 'SUPABASE_SERVICE_ROLE_KEY' as the encrypted value (arguments swapped). The cron looked up
-- name = 'SUPABASE_SERVICE_ROLE_KEY' (absent) and called supabase_functions.http_request (absent),
-- so it never dispatched. No literal secret appears in this file: values are moved in SQL.
-- APPLIED LIVE 2026-09-22: step 1 only. Steps 2-4 pending user approval (see step 2 note).

-- 1. Move the JWT from the misnamed row's NAME into an encrypted secret under a descriptive name.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM vault.secrets WHERE name = 'push_poll_service_role_key') THEN
    PERFORM vault.create_secret(
      (SELECT name FROM vault.secrets
        WHERE name ~ '^eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$'
        ORDER BY created_at LIMIT 1),
      'push_poll_service_role_key',
      'Legacy service_role JWT used by pg_cron send-push-poll to call the send-push Edge Function'
    );
  END IF;
END;
$$;

-- 2. Retire outbox rows queued while the poll was dead so the first live run does not push
--    months-old trade events to users.
UPDATE public.notification_outbox
SET status = 'skipped',
    processed_at = now(),
    last_error = 'stale: send-push-poll was non-functional until 2026-09-22'
WHERE status = 'pending'
  AND created_at < now() - interval '24 hours';

-- 3. Repoint the cron at the new secret and at pg_net (same schedule, same pending-row gate).
SELECT cron.alter_job(
  job_id  := (SELECT jobid FROM cron.job WHERE jobname = 'send-push-poll'),
  command := $cmd$
    DO $$
    DECLARE
      v_key text;
    BEGIN
      IF NOT EXISTS (SELECT 1 FROM public.notification_outbox WHERE status = 'pending' AND attempts < 3) THEN
        RETURN;
      END IF;

      SELECT decrypted_secret INTO v_key
      FROM vault.decrypted_secrets
      WHERE name = 'push_poll_service_role_key';

      IF v_key IS NULL THEN
        RAISE EXCEPTION 'send-push-poll: vault secret push_poll_service_role_key missing';
      END IF;

      PERFORM net.http_post(
        url := 'https://uimogilwuixgkgfcfmyb.supabase.co/functions/v1/send-push',
        body := '{"trigger": "poll"}'::jsonb,
        headers := jsonb_build_object(
          'Content-Type', 'application/json',
          'Authorization', 'Bearer ' || v_key
        ),
        timeout_milliseconds := 10000
      );
    END;
    $$;
  $cmd$
);

-- 4. Delete the misnamed row (JWT-as-name) once the new secret holds the identical value.
DELETE FROM vault.secrets s
WHERE s.name ~ '^eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$'
  AND EXISTS (
    SELECT 1 FROM vault.decrypted_secrets n
    WHERE n.name = 'push_poll_service_role_key' AND n.decrypted_secret = s.name
  );
