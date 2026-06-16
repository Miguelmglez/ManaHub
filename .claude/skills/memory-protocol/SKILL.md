---
name: memory-protocol
description: >
  How to record a learning after a bug fix, security finding, or architectural/design decision.
  Use when about to write/update a memory file or decide whether a learning belongs in CLAUDE.md
  vs memory.
---

# Memory protocol

How ManaHub records durable learnings. Apply this after any bug fix, security finding, or
architectural/design decision — and before writing or updating any memory file.

## 1. Where knowledge lives

- **Broadly-applicable rules** (a new architectural pattern, a cross-cutting constraint) → `CLAUDE.md`.
- **Feature-specific / non-obvious detail and judgment aids** → a **memory file**.
- **Never duplicate** between the two. If it's in CLAUDE.md, don't repeat it in memory, and vice-versa.
  If a CLAUDE.md feature section grows past a few lines, move the detail to its memory file and leave a
  `→ memory:` pointer.

## 2. File types

- `feedback_<topic>` — corrected approaches / bug fixes. Type `feedback`.
- `project_<topic>` — architectural / design decisions. Type `project`.
- `reference_<topic>` — pointers to external systems (Supabase, Cloudflare, Firebase, Scryfall). Type
  `reference`.
- `user` — user profile (role, expertise, preferences). Type `user`.

## 3. Body structure

Lead with the **rule** (for feedback) or the **decision** (for project). Then:

- `**Why:**` — the rationale, so a future agent can apply judgment in edge cases instead of just
  pattern-matching.
- `**How to apply:**` — concrete guidance.

Link related memories with `[[name]]` (the other memory's `name:` slug). A `[[name]]` that doesn't
exist yet is fine — it marks something worth writing later.

## 4. Frontmatter

```markdown
---
name: <short-kebab-case-slug>
description: <one-line summary — used to decide relevance during recall>
metadata:
  type: user | feedback | project | reference
---
```

## 5. Index step

Add a **one-line pointer** to the relevant `MEMORY.md`:

- Main agent: `C:\Users\Miguel\.claude\projects\E--Projects-ManaHub\memory\MEMORY.md`
- Agents: `.claude/agent-memory/<agent>/MEMORY.md`

Format: `- [Title](file.md) — hook`. One line only — **no memory content in the index**.

## 6. Saving rules

- **Convert relative dates to absolute** when saving (e.g. "today" → `2026-06-14`).
- **De-dup before writing:** update an existing file rather than creating a near-duplicate; delete
  memories that turn out to be wrong.
- **Before recommending from memory:** verify the named file/function/flag still exists — recalled
  memories reflect what was true when written.
