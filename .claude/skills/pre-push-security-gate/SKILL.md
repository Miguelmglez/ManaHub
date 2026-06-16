---
name: pre-push-security-gate
description: >
  Run before ANY commit or push (even "docs-only"). Secret-scans the staged diff and blocks on
  any critical finding. Use whenever the user asks to commit, push, or open a PR.
---

# Pre-push security gate

This is the MANDATORY pre-push secret-scan gate for ManaHub. Run it before any commit or push —
**including "docs-only" changes** — and before opening any PR. Do not skip it because a change
"looks harmless"; the 2026-06 incident that required a full `git filter-branch --all` history
rewrite started as a planning `.md` file.

## Procedure

1. **Delegate the scan to the `android-security-auditor` agent**, run against the **staged** diff
   (`git diff --cached`), not the whole working tree.
2. **Scan for secrets** — keys/tokens/private keys/passwords/connection strings. Patterns to flag:
   - `AIzaSy` (Google/Firebase API keys)
   - `-----BEGIN` (private keys)
   - `secret`, `password`, `api_key`
   - `Authorization`, `Bearer`
   - Supabase URLs + service-role keys
3. **Confirm `google-services.json` is git-ignored and not staged.**
4. **Confirm no `.md`/`.txt`/`.json` file contains literal key *values*** — referencing a key *by
   name* is fine; embedding the value is not.
5. **Confirm nothing bypassed `.gitignore` via `git add -f`.**
6. **Confirm temporary planning `.md` files are gitignored and deleted** (see CLAUDE.md → "AI
   planning documents"). The 2026-06 incident required a `git filter-branch --all` history rewrite —
   a planning doc must never reach a commit.
7. **Block the push on any critical finding.** Do not proceed; surface the finding to the user.

Never store or echo literal secret values when reporting findings — reference them by name and
location only.

→ memory: `feedback_secret_leak_prevention`
