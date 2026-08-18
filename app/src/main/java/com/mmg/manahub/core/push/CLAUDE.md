### Push notifications
Outbox pattern: write → trigger `fn_notify_*` → `enqueue_notification` → `notification_outbox` →
pg_cron (60 s) → Edge Function `send-push` → FCM HTTP v1 → device. Must-know:
- Opt-out prefs (missing key = enabled). `enqueue_notification` is **REVOKE-protected** — never grant
  client EXECUTE (accepts arbitrary `recipient_id`). Payload carries no PII.
- `PushDeeplinkRouter` buffers cold-start deeplinks (scheme `manahub://` only); don't store the
  NavController as an Activity field. Feature flag: `pushNotificationsEnabledFlow` (default `false`).
- Channels created once at app start (importance is immutable after first creation).
- → memory: `project_push_notifications`, `feedback_push_enqueue_security`, `feedback_push_deeplink_routing`

