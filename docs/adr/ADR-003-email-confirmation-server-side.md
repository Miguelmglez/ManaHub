# ADR-003: Device-independent server-side email confirmation

Status: Accepted — 2026-06-15
Project: Supabase `ManaHub` (ref `uimogilwuixgkgfcfmyb`, region eu-west-2)

## Context

Sign-up email confirmation was broken for **cross-device** confirmation.

`core/di/SupabaseModule.kt` installs Auth with `scheme="manahub"`, `host="auth"` and
**no `flowType` override**, so the Supabase Kotlin SDK defaults to `FlowType.PKCE`.

PKCE stores a `code_verifier` on the **signup device**. The previous email template used
`{{ .ConfirmationURL }}`, which (under a PKCE project) produces a link that redirects to
`manahub://auth?code=...`. The account is only finalized when the **app** exchanges that
`code` against the locally-stored `code_verifier`.

Failure mode: open the email on a **different device** (e.g. a desktop browser) and:
- `manahub://` does not resolve (no app),
- no `code_verifier` exists on that device,
- so the `code` is never exchanged and `email_confirmed_at` is never set.

Verified against the live project: `confirm_signup` is enabled (email users carry a
`confirmation_sent_at`); existing email signups were only confirmed because they happened
to click on the **same** device.

## Decision

Decouple **confirmation** (must be server-side, device-independent) from **session login**
(a convenience that only the original device can complete).

1. **Confirmation link points at GoTrue's `GET /auth/v1/verify`** using the hashed token,
   not `{{ .ConfirmationURL }}`:

   ```
   https://uimogilwuixgkgfcfmyb.supabase.co/auth/v1/verify
     ?token={{ .TokenHash }}
     &type=signup
     &redirect_to=https://miguelmglez.github.io/auth-confirmed.html
   ```

   `GET /auth/v1/verify` sets `email_confirmed_at` **server-side, before** issuing the 302
   to `redirect_to`. It does NOT depend on a client-side PKCE code exchange — so the click
   works from any browser on any device.

   NOTE: the verify endpoint lives on the **API host**
   (`https://uimogilwuixgkgfcfmyb.supabase.co`), NOT on `{{ .SiteURL }}` (which is the
   app/site Site URL). The base is therefore hardcoded in the template.

2. **`redirect_to` targets a plain https landing page**, `auth-confirmed.html`, served from
   the existing GitHub Pages site (`https://miguelmglez.github.io/`, repo
   `Miguelmglez/Miguelmglez.github.io`, mirrored locally in `github-pages/`). The page:
   - shows "Email confirmed ✓",
   - builds a `manahub://auth<original-query><original-hash>` deeplink and offers an
     "Open ManaHub" button (best-effort auto-open on mobile),
   - on the **original device**, GoTrue forwards the PKCE `code` onto the redirect URL, so
     the deeplink carries it into the app, which finishes the exchange and logs in,
   - on **any other device**, the deeplink is inert and the page tells the user to open the
     app on their phone.

   The desktop case works because confirmation already happened at `/verify` **before** the
   redirect; the landing page is plain https and never needs the custom scheme to resolve.

## Why not keep `{{ .ConfirmationURL }}`?

`{{ .ConfirmationURL }}` honors the project flowType. Under PKCE it bakes the cross-device
failure in. The explicit `/auth/v1/verify?token={{ .TokenHash }}` form makes confirmation
**independent of flowType** and of any client-side exchange. This is the supported pattern
for "confirm-on-the-server" email links.

## Dashboard / config steps (MANUAL — not expressible as a migration)

GoTrue auth config is not in the Postgres schema and cannot be changed via `apply_migration`.
The user must apply these in the Supabase dashboard (Authentication):

1. **URL Configuration → Redirect URLs (allow list)** — add:
   - `https://miguelmglez.github.io/auth-confirmed.html`
   - keep the existing `manahub://auth` (and `manahub://**` if present) for the app's own
     PKCE exchange / OAuth.
   `redirect_to` is validated against this allow list; if the landing page URL is not on it,
   GoTrue rejects the redirect.
2. **Authentication → Emails → Templates → "Confirm signup"** — paste the contents of
   `supabase/email-templates/confirm-signup.html`. (Templates are managed in the dashboard,
   not via migration; the repo file is the source of truth to copy from.)
3. Confirm **Authentication → Providers → Email → "Confirm email"** stays ENABLED.
4. (No change needed to Site URL for this flow.)

## Deployment of the landing page

`github-pages/` is a separate clone of `Miguelmglez.github.io`. Commit & push
`auth-confirmed.html` there (its own git remote) so it is live at
`https://miguelmglez.github.io/auth-confirmed.html`.

## Android contract (for android-kotlin-architect)

- The app receives the deeplink **`manahub://auth?code=<pkce_code>`** (the original signup
  device only). It may also, in implicit/edge cases, receive token data in the **hash
  fragment** (`#access_token=...&refresh_token=...`) — the landing page forwards both the
  query string and the hash verbatim.
- **The account is already confirmed server-side before any deeplink fires.** The app must
  NOT treat "no deeplink received" as "not confirmed".
- On receiving `manahub://auth?...`, the app should hand the **full URI** to the Supabase
  Kotlin SDK so it can complete the PKCE exchange and establish a session (the SDK already
  listens on `scheme="manahub" host="auth"`). This is the existing behavior — no change to
  the exchange path is required.
- **Cross-device reality:** when the user confirmed on another device, the phone never
  receives a deeplink. The app must therefore allow the user to **sign in normally with
  email + password** after confirmation (the password flow must not be gated behind having
  consumed a deeplink). This is the primary path the user lands on in the desktop case.
- No PKCE/flowType change is required in `SupabaseModule.kt`. (Optionally, the team could
  switch to `flowType = FlowType.IMPLICIT` to drop PKCE entirely, but that is NOT necessary
  with this design and is out of scope.)

## Supabase invariants

No new SQL objects (RPC / table / view / trigger / SECURITY DEFINER function) were created,
so the migration-level invariants (search_path, REVOKE/GRANT, security_invoker, FK indexes,
`get_advisors`) do not apply. The change is config + static HTML only.
