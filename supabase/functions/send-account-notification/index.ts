// send-account-notification
//
// Sends one of 4 best-effort, notify-only transactional emails for sensitive account
// changes that GoTrue itself has no native template/event for (password_changed,
// email_changed, identity_linked, identity_removed — GoTrue's 6 fixed templates cover
// signup/invite/magic-link/recovery/email-change-confirmation/reauthentication only).
//
// Auth model: the caller must present their OWN valid user access token (verify_jwt is
// enabled at the platform level AND re-checked here via auth.getUser() — the anon key
// alone resolves no user and is rejected, matching the pattern used by
// set-google-account-password / delete-current-user in this project). The recipient
// email is resolved server-side from the VERIFIED caller's identity via the admin API —
// body.user_id is only used as a defense-in-depth equality check against the verified
// caller, never trusted as the source of the recipient.
//
// This is a best-effort side-channel: any failure past the auth/validation stage (SMTP
// down, secrets not yet configured, profile lookup failure) returns HTTP 200 with
// delivered:false so the calling app's already-completed sensitive operation never looks
// failed because of this notification.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { SMTPClient } from "https://deno.land/x/denomailer@1.6.0/mod.ts";
import { renderTemplate, type NotificationEvent } from "./templates.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const GMX_SMTP_USER = Deno.env.get("GMX_SMTP_USER") ?? "";
const GMX_SMTP_PASSWORD = Deno.env.get("GMX_SMTP_PASSWORD") ?? "";

// Same single-allowed-origin CORS pattern as request-account-deletion in this project —
// exact match only, never a wildcard (this endpoint requires credentials/JWT).
const ALLOWED_ORIGIN = Deno.env.get("ALLOWED_ORIGIN") ?? "";

const getCorsHeaders = (requestOrigin: string | null) => ({
  "Access-Control-Allow-Origin": requestOrigin === ALLOWED_ORIGIN ? ALLOWED_ORIGIN : "",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
});

const ALLOWED_EVENTS: NotificationEvent[] = [
  "password_changed",
  "email_changed",
  "identity_linked",
  "identity_removed",
];

// Allowlisted provider keys only — metadata.provider is never interpolated verbatim.
const ALLOWED_PROVIDERS: Record<string, string> = {
  google: "Google",
  email: "Email & Password",
};

function escapeHtml(input: string): string {
  return input
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function jsonResponse(data: unknown, status: number, corsHeaders: Record<string, string>) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

// Admin client — service role, used ONLY to resolve the verified caller's own email/
// nickname (bypassing RLS the caller wouldn't otherwise have on auth.users). Never used
// to act on any id other than the one auth.getUser() already verified below.
const adminClient = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
  auth: { autoRefreshToken: false, persistSession: false },
});

Deno.serve(async (req: Request) => {
  const corsHeaders = getCorsHeaders(req.headers.get("origin"));

  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return jsonResponse({ error: "Method not allowed" }, 405, corsHeaders);
  }

  // --- 1. Authenticate the caller via their own JWT ---
  const authHeader = req.headers.get("Authorization");
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    return jsonResponse({ error: "Missing or invalid Authorization header" }, 401, corsHeaders);
  }

  const userClient = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    global: { headers: { Authorization: authHeader } },
    auth: { autoRefreshToken: false, persistSession: false },
  });

  const {
    data: { user },
    error: userErr,
  } = await userClient.auth.getUser();

  if (userErr || !user) {
    return jsonResponse({ error: "Unauthorized: invalid JWT" }, 401, corsHeaders);
  }

  // --- 2. Parse + validate body ---
  let body: { user_id?: string; event?: string; metadata?: Record<string, unknown> };
  try {
    body = await req.json();
  } catch {
    return jsonResponse({ error: "Invalid JSON body" }, 400, corsHeaders);
  }

  const { user_id, event, metadata } = body ?? {};

  if (typeof user_id !== "string" || user_id.length === 0) {
    return jsonResponse({ error: "user_id is required" }, 400, corsHeaders);
  }

  // Defense-in-depth only: the recipient is resolved from the VERIFIED session below,
  // never from this field. A mismatch here means the caller is asking to notify someone
  // else, which is always rejected outright.
  if (user_id !== user.id) {
    return jsonResponse(
      { error: "Forbidden: user_id does not match the authenticated caller" },
      403,
      corsHeaders,
    );
  }

  if (typeof event !== "string" || !ALLOWED_EVENTS.includes(event as NotificationEvent)) {
    return jsonResponse({ error: `event must be one of: ${ALLOWED_EVENTS.join(", ")}` }, 400, corsHeaders);
  }

  let providerLabel: string | undefined;
  if (event === "identity_linked" || event === "identity_removed") {
    const rawProvider = typeof metadata?.provider === "string" ? metadata.provider : undefined;
    if (rawProvider && ALLOWED_PROVIDERS[rawProvider]) {
      providerLabel = ALLOWED_PROVIDERS[rawProvider];
    }
  }

  // --- 3. Resolve the recipient identity server-side from the verified caller's own id ---
  const { data: authUserData, error: authUserErr } = await adminClient.auth.admin.getUserById(user.id);
  const recipientEmail = authUserData?.user?.email;

  if (authUserErr || !recipientEmail) {
    console.error("[send-account-notification] could not resolve recipient email:", authUserErr?.message);
    return jsonResponse(
      { success: true, delivered: false, error: "Could not resolve recipient email" },
      200,
      corsHeaders,
    );
  }

  let nickname: string | null = null;
  try {
    const { data: profile } = await adminClient
      .from("user_profiles")
      .select("nickname")
      .eq("id", user.id)
      .maybeSingle();
    nickname = profile?.nickname ?? null;
  } catch (profileErr) {
    // Non-fatal — fall back to the email's local part below.
    console.warn("[send-account-notification] profile lookup failed:", profileErr);
  }

  const displayName = escapeHtml(nickname || recipientEmail.split("@")[0]);
  const timestamp = escapeHtml(
    `${new Date().toLocaleString("en-US", { dateStyle: "medium", timeStyle: "short", timeZone: "UTC" })} UTC`,
  );

  const { subject, html } = renderTemplate(event as NotificationEvent, {
    displayName,
    timestamp,
    provider: providerLabel ? escapeHtml(providerLabel) : undefined,
  });

  // --- 4. Best-effort send via GMX SMTP ---
  // Port 465 (implicit TLS) is the only outbound port Supabase Edge Functions allow;
  // ports 25/587 are blocked at the platform level (supabase/edge-runtime#6255, #21977).
  if (!GMX_SMTP_USER || !GMX_SMTP_PASSWORD) {
    console.warn("[send-account-notification] GMX_SMTP_USER/GMX_SMTP_PASSWORD not configured — skipping send");
    return jsonResponse({ success: true, delivered: false, error: "SMTP not configured" }, 200, corsHeaders);
  }

  const client = new SMTPClient({
    connection: {
      hostname: "mail.gmx.net",
      port: 465,
      tls: true,
      auth: { username: GMX_SMTP_USER, password: GMX_SMTP_PASSWORD },
    },
  });

  try {
    await client.send({
      from: `ManaHub <${GMX_SMTP_USER}>`,
      to: recipientEmail,
      subject,
      content: "auto",
      html,
    });
    return jsonResponse({ success: true, delivered: true }, 200, corsHeaders);
  } catch (sendErr) {
    console.error("[send-account-notification] SMTP send failed:", sendErr);
    return jsonResponse(
      { success: true, delivered: false, error: "Notification email could not be delivered" },
      200,
      corsHeaders,
    );
  } finally {
    try {
      await client.close();
    } catch {
      // ignore close errors — the send already resolved above
    }
  }
});
