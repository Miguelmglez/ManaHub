"""Publish a set's generated artifacts to R2 and bump the sets index.

Uploads whatever exists in ``output/<code>/`` among ``booster.json`` / ``engine.json`` to the
``manahub-assets`` bucket (keys ``draft/<code>/<name>.json``), bumps the matching
``content_versions`` entries in ``cloudflare/manahub-draft-api/sets_index.json`` so clients
invalidate their cache, and re-uploads the index.

Requires ``npx wrangler`` authenticated against the bucket (same contract as the legacy
``scripts/draftsim/upload_to_r2.mjs``). Tier list + guide publishing stays in the Node
pipeline — this module only covers the Python-generated artifacts.
"""

from __future__ import annotations

import json
import subprocess
from datetime import date
from pathlib import Path
from typing import Any

from . import config

BUCKET = "manahub-assets"
ARTIFACTS = ("booster", "engine")


def _wrangler_put(key: str, file: Path, *, dry_run: bool) -> None:
    cmd = (
        f'npx wrangler r2 object put "{BUCKET}/{key}" --file "{file}" '
        f"--remote --content-type application/json"
    )
    print(f"[upload] {'DRY-RUN ' if dry_run else ''}PUT {key}  <- {file}")
    if dry_run:
        return
    result = subprocess.run(cmd, shell=True)
    if result.returncode != 0:
        raise RuntimeError(f"wrangler put failed for {key} (exit {result.returncode})")


def _bump_index(set_code: str, artifacts: list[str], *, dry_run: bool) -> dict[str, Any]:
    index = json.loads(config.SETS_INDEX_PATH.read_text(encoding="utf-8"))
    entry = next((s for s in index.get("sets", []) if s.get("code") == set_code), None)
    if entry is None:
        raise ValueError(
            f"Set '{set_code}' is not in {config.SETS_INDEX_PATH.name}. A set needs its guide "
            f"+ tier list published first (Node pipeline: build_sets_index.mjs)."
        )
    today = date.today().isoformat()
    for name in artifacts:
        entry.setdefault("content_versions", {})[name] = today
    index["index_version"] = str(int(index.get("index_version", "0")) + 1)
    index["last_updated"] = today
    if not dry_run:
        config.SETS_INDEX_PATH.write_text(
            json.dumps(index, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
        )
    print(
        f"[upload] {'DRY-RUN ' if dry_run else ''}sets_index: {set_code} "
        f"content_versions{{{', '.join(f'{a}: {today}' for a in artifacts)}}}, "
        f"index_version -> {index['index_version']}"
    )
    return index


def upload(set_code: str, *, dry_run: bool = False) -> dict[str, Any]:
    """Upload the set's generated artifacts + the bumped index to R2."""
    code = set_code.lower()
    set_dir = config.OUTPUT_DIR / code

    present = [n for n in ARTIFACTS if (set_dir / f"{n}.json").exists()]
    if not present:
        raise ValueError(
            f"Nothing to upload: no {' / '.join(f'{n}.json' for n in ARTIFACTS)} in {set_dir}."
        )

    for name in present:
        _wrangler_put(f"draft/{code}/{name}.json", set_dir / f"{name}.json", dry_run=dry_run)

    _bump_index(code, present, dry_run=dry_run)
    _wrangler_put("draft/sets-index.json", config.SETS_INDEX_PATH, dry_run=dry_run)

    return {"set": code, "uploaded": present, "dry_run": dry_run}
