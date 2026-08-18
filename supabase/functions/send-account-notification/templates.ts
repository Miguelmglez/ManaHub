// HTML templates for send-account-notification.
//
// House style mirrors supabase/email-templates/confirm-signup.html (ADR-003 precedent):
// dark gradient header, #1A1A2E body panel, gold accent, amber-bordered security callout.
// These 4 events are notify-only (no GoTrue native template exists for them) — there is
// deliberately no action link, just a "wasn't you?" notice, per the account-management spec.
//
// All interpolated values (displayName, timestamp, provider) MUST already be HTML-escaped
// by the caller (see escapeHtml in index.ts) before being passed in here — this module does
// no escaping of its own.

export type NotificationEvent =
  | "password_changed"
  | "email_changed"
  | "identity_linked"
  | "identity_removed";

export interface TemplateData {
  /** Pre-escaped display name (nickname, falling back to the email's local part). */
  displayName: string;
  /** Pre-escaped best-effort human-readable send time (e.g. "Aug 11, 2026, 3:04 PM UTC"). */
  timestamp: string;
  /** Pre-escaped provider label (e.g. "Google"), only set for identity_linked/identity_removed. */
  provider?: string;
}

const HEADER = `
          <tr>
            <td style="background:linear-gradient(135deg,#3D1B6E 0%,#1A1A4E 100%);border-radius:12px 12px 0 0;padding:36px 40px;text-align:center;">
              <p style="margin:0 0 8px 0;font-size:28px;font-weight:700;color:#D4A017;letter-spacing:2px;">⚔ MANAHUB</p>
              <p style="margin:0;font-size:13px;color:#A090C0;letter-spacing:1px;text-transform:uppercase;">Magic: The Gathering Companion</p>
            </td>
          </tr>`;

const FOOTER = `
          <tr>
            <td style="background-color:#13132A;border-radius:0 0 12px 12px;padding:20px 40px;text-align:center;">
              <p style="margin:0;font-size:12px;color:#404060;">
                © 2026 ManaHub · Magic: The Gathering is © Wizards of the Coast
              </p>
            </td>
          </tr>`;

function wrap(title: string, bodyHtml: string): string {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>${title}</title>
</head>
<body style="margin:0;padding:0;background-color:#0D0D1A;font-family:'Segoe UI',Arial,sans-serif;">
  <table width="100%" cellpadding="0" cellspacing="0" style="background-color:#0D0D1A;padding:40px 20px;">
    <tr>
      <td align="center">
        <table width="100%" cellpadding="0" cellspacing="0" style="max-width:560px;">
${HEADER}
          <tr>
            <td style="background-color:#1A1A2E;padding:40px;">
${bodyHtml}
            </td>
          </tr>
${FOOTER}
        </table>
      </td>
    </tr>
  </table>
</body>
</html>`;
}

function securityNotice(): string {
  return `
              <table width="100%" cellpadding="0" cellspacing="0" style="margin-top:32px;">
                <tr>
                  <td style="background-color:#13132A;border-left:3px solid #D4A017;border-radius:0 6px 6px 0;padding:14px 16px;">
                    <p style="margin:0;font-size:13px;color:#A0A0B8;line-height:1.5;">
                      ⚠ <strong style="color:#E8E8F0;">Wasn't you?</strong> If you didn't make this change, your account may be compromised. Change your password immediately and review the sign-in methods linked to your account.
                    </p>
                  </td>
                </tr>
              </table>`;
}

const RENDERERS: Record<NotificationEvent, (d: TemplateData) => { subject: string; html: string }> = {
  password_changed: (d) => {
    const subject = "Your ManaHub password was changed";
    return {
      subject,
      html: wrap(
        subject,
        `              <p style="margin:0 0 20px 0;font-size:22px;font-weight:600;color:#E8E8F0;">Password Changed</p>
              <p style="margin:0 0 16px 0;font-size:15px;color:#A0A0B8;line-height:1.6;">
                Hi ${d.displayName}, this is a confirmation that the password for your ManaHub account was changed on ${d.timestamp}.
              </p>
              <p style="margin:0 0 8px 0;font-size:15px;color:#A0A0B8;line-height:1.6;">
                If you made this change, no further action is needed.
              </p>
${securityNotice()}
              <p style="margin:24px 0 0 0;font-size:13px;color:#6060A0;line-height:1.6;">
                🔒 ManaHub will never ask for your password via email or chat.
              </p>`,
      ),
    };
  },
  email_changed: (d) => {
    const subject = "An email change was requested for your ManaHub account";
    return {
      subject,
      html: wrap(
        subject,
        `              <p style="margin:0 0 20px 0;font-size:22px;font-weight:600;color:#E8E8F0;">Email Change Requested</p>
              <p style="margin:0 0 16px 0;font-size:15px;color:#A0A0B8;line-height:1.6;">
                Hi ${d.displayName}, an email change was requested for your ManaHub account on ${d.timestamp}. No change takes effect until it's confirmed via the links sent to both your old and new email address.
              </p>
              <p style="margin:0 0 8px 0;font-size:15px;color:#A0A0B8;line-height:1.6;">
                If you requested this, no further action is needed here — just confirm via those links. If you didn't request this, you can safely ignore this message: without your confirmation, the change will not go through.
              </p>
${securityNotice()}`,
      ),
    };
  },
  identity_linked: (d) => {
    const providerLabel = d.provider ?? "A new sign-in method";
    const subject = "A new sign-in method was linked to your ManaHub account";
    return {
      subject,
      html: wrap(
        subject,
        `              <p style="margin:0 0 20px 0;font-size:22px;font-weight:600;color:#E8E8F0;">Sign-In Method Linked</p>
              <p style="margin:0 0 16px 0;font-size:15px;color:#A0A0B8;line-height:1.6;">
                Hi ${d.displayName}, <strong style="color:#E8E8F0;">${providerLabel}</strong> was linked to your ManaHub account on ${d.timestamp}. You can now use it to sign in.
              </p>
              <p style="margin:0 0 8px 0;font-size:15px;color:#A0A0B8;line-height:1.6;">
                If you made this change, no further action is needed.
              </p>
${securityNotice()}`,
      ),
    };
  },
  identity_removed: (d) => {
    const providerLabel = d.provider ?? "A sign-in method";
    const subject = "A sign-in method was removed from your ManaHub account";
    return {
      subject,
      html: wrap(
        subject,
        `              <p style="margin:0 0 20px 0;font-size:22px;font-weight:600;color:#E8E8F0;">Sign-In Method Removed</p>
              <p style="margin:0 0 16px 0;font-size:15px;color:#A0A0B8;line-height:1.6;">
                Hi ${d.displayName}, <strong style="color:#E8E8F0;">${providerLabel}</strong> was unlinked from your ManaHub account on ${d.timestamp}.
              </p>
              <p style="margin:0 0 8px 0;font-size:15px;color:#A0A0B8;line-height:1.6;">
                If you made this change, no further action is needed.
              </p>
${securityNotice()}`,
      ),
    };
  },
};

export function renderTemplate(event: NotificationEvent, data: TemplateData): { subject: string; html: string } {
  return RENDERERS[event](data);
}
