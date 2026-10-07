"""Bounded offline metadata inventory. No images, matching or application writes."""

from __future__ import annotations

import argparse
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime, timezone
import gzip
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import sqlite3
import sys
import time
import zlib
from urllib.parse import urlsplit
from uuid import UUID

VERSION = "2.0.0"
SCHEMA = 2
REPO = Path(__file__).resolve().parents[2]
BULK_TYPES = ("default_cards", "all_cards", "unique_artwork")
SEPARATE_LAYOUTS = {"transform", "modal_dfc", "double_faced_token", "reversible_card", "art_series"}
ROOT_LAYOUTS = {
    "normal", "split", "flip", "adventure", "meld", "leveler", "class", "saga",
    "planar", "scheme", "vanguard", "token", "emblem", "augment", "host",
    "mutate", "prototype", "case", "prepare", "front_card"}
NONPLAYABLE = {"token", "double_faced_token", "emblem", "art_series", "planar", "scheme", "vanguard"}
NEGATIVE_LAYOUTS = {"front_card"}
MAX_PARTS = 1024


class CatalogError(Exception):
    pass


@dataclass(frozen=True)
class Bounds:
    compressed: int = 512 * 1024**2
    decompressed: int = 8 * 1024**3
    record: int = 2 * 1024**2
    records: int = 2_000_000
    seconds: float = 3600
    reserve: int = 512 * 1024**2

    def __post_init__(self):
        for value in (self.compressed, self.decompressed, self.record, self.records, self.seconds):
            if not isinstance(value, (int, float)) or not 0 < value < float("inf"):
                raise CatalogError("Bounds must be positive and finite")
        if self.reserve < 0:
            raise CatalogError("Disk reserve must be nonnegative")


def deadline_check(deadline):
    if time.monotonic() >= deadline:
        raise CatalogError("Operation deadline exceeded")


def external(path):
    path = Path(path).resolve()
    if path == REPO or REPO in path.parents:
        raise CatalogError("Generated outputs/cache must be outside the repository")
    return path


def disk_check(path, needed, bounds):
    if shutil.disk_usage(path).free < needed + bounds.reserve:
        raise CatalogError("Insufficient disk space including reserve")


def atomic_json(path, value):
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("w", encoding="utf-8", newline="\n") as stream:
        json.dump(value, stream, indent=2, ensure_ascii=False, allow_nan=False)
        stream.write("\n")
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temporary, path)


@contextmanager
def writer_lock(directory):
    directory = external(directory)
    directory.mkdir(parents=True, exist_ok=True)
    with (directory / ".writer.lock").open("a+b") as lock:
        lock.seek(0, 2)
        if lock.tell() == 0:
            lock.write(b"0")
            lock.flush()
        lock.seek(0)
        try:
            if os.name == "nt":
                import msvcrt
                msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as error:
            raise CatalogError("Another writer owns this output/cache directory") from error
        try:
            yield
        finally:
            lock.seek(0)
            if os.name == "nt":
                msvcrt.locking(lock.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(lock, fcntl.LOCK_UN)


def sha_file(path, bounds, deadline):
    digest = hashlib.sha256()
    length = 0
    with path.open("rb") as source:
        while chunk := source.read(1024 * 1024):
            deadline_check(deadline)
            length += len(chunk)
            if length > bounds.compressed:
                raise CatalogError("Compressed byte bound exceeded")
            digest.update(chunk)
    return digest.hexdigest(), length


def lines(path, bounds, deadline):
    total = 0
    count = 0
    try:
        with gzip.open(path, "rb") as source:
            while raw := source.readline(bounds.record + 1):
                deadline_check(deadline)
                count += 1
                total += len(raw)
                if len(raw) > bounds.record:
                    raise CatalogError("JSONL record byte bound exceeded")
                if total > bounds.decompressed:
                    raise CatalogError("Decompressed byte bound exceeded")
                if count > bounds.records:
                    raise CatalogError("Record count bound exceeded")
                if not raw.endswith(b"\n"):
                    raise CatalogError("JSONL record lacks final newline; snapshot may be truncated")
                yield count, raw, total
    except (OSError, EOFError, zlib.error) as error:
        raise CatalogError("Invalid/truncated gzip snapshot") from error


def verify_snapshot(path, bounds, deadline):
    digest, size = sha_file(path, bounds, deadline)
    count = total = 0
    for count, _, total in lines(path, bounds, deadline):
        pass
    if not count:
        raise CatalogError("Empty snapshot")
    if sha_file(path, bounds, deadline) != (digest, size):
        raise CatalogError("Snapshot changed during validation")
    return {"sha256": digest, "compressed_bytes": size, "decompressed_bytes": total,
            "source_records": count, "gzip_crc_verified": True,
            "authenticity": "not_authenticated_by_self_computed_sha256"}


def strict_json(raw):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError("Duplicate JSON key")
            result[key] = value
        return result

    def invalid_constant(_):
        raise ValueError("Nonfinite JSON constant")

    def finite_float(value):
        result = float(value)
        if not math.isfinite(result):
            raise ValueError("Nonfinite JSON number")
        return result

    return json.loads(raw, object_pairs_hook=pairs, parse_constant=invalid_constant, parse_float=finite_float)


def compact(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), allow_nan=False)


def uuid(value):
    if not isinstance(value, str) or str(UUID(value)) != value:
        raise ValueError("Expected canonical UUID")
    return value


def text_field(obj, key, required=False):
    value = obj.get(key)
    if value is None and not required:
        return None
    if not isinstance(value, str) or (required and not value):
        raise ValueError("Expected text field")
    return value


def optional_uuid(obj, key):
    value = obj.get(key)
    return None if value is None else uuid(value)


def presence(obj, key):
    return "absent" if key not in obj else "null" if obj[key] is None else "value"


def image_url_allowed(value):
    if not isinstance(value, str):
        return False
    parsed = urlsplit(value)
    try:
        return (parsed.scheme == "https" and parsed.hostname == "cards.scryfall.io"
                and parsed.port in (None, 443) and parsed.username is None
                and parsed.password is None and bool(parsed.path) and not parsed.fragment
                and not any(c in value for c in ("\\", "\r", "\n", "\t")))
    except ValueError:
        return False


DDL = """
CREATE TABLE IF NOT EXISTS run(key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS source_records(
 seq INTEGER PRIMARY KEY, printing_id TEXT, raw_sha256 TEXT NOT NULL,
 raw BLOB NOT NULL, disposition TEXT NOT NULL, reason TEXT);
CREATE TABLE IF NOT EXISTS printings(
 id TEXT PRIMARY KEY, seq INTEGER NOT NULL UNIQUE REFERENCES source_records(seq),
 name TEXT NOT NULL, oracle_id TEXT, oracle_state TEXT NOT NULL,
 identity_key TEXT NOT NULL, set_code TEXT NOT NULL, collector_number TEXT NOT NULL,
 lang TEXT NOT NULL, finishes_json TEXT NOT NULL, layout TEXT NOT NULL,
 games_json TEXT NOT NULL, image_status TEXT, destination TEXT NOT NULL,
 illustration_id TEXT, illustration_state TEXT NOT NULL);
CREATE INDEX IF NOT EXISTS printing_lookup ON printings(name,set_code,collector_number,lang);
CREATE TABLE IF NOT EXISTS identities(key TEXT PRIMARY KEY, kind TEXT NOT NULL, oracle_id TEXT);
CREATE TABLE IF NOT EXISTS faces(
 printing_id TEXT NOT NULL REFERENCES printings(id), face_index INTEGER NOT NULL,
 name TEXT, oracle_id TEXT, oracle_state TEXT NOT NULL, identity_key TEXT NOT NULL REFERENCES identities(key),
 illustration_id TEXT, illustration_state TEXT NOT NULL, has_image_uris INTEGER NOT NULL,
 PRIMARY KEY(printing_id,face_index));
CREATE TABLE IF NOT EXISTS image_references(
 key TEXT PRIMARY KEY, printing_id TEXT NOT NULL REFERENCES printings(id),
 face_index INTEGER, illustration_id TEXT, illustration_state TEXT NOT NULL,
 image_status TEXT, full_image_urls_json TEXT NOT NULL, eligible INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS image_printing ON image_references(printing_id);
CREATE TABLE IF NOT EXISTS reference_identities(
 reference_key TEXT NOT NULL REFERENCES image_references(key),
 identity_key TEXT NOT NULL REFERENCES identities(key), relation TEXT NOT NULL,
 PRIMARY KEY(reference_key,identity_key,relation));
CREATE TABLE IF NOT EXISTS related_parts(
 printing_id TEXT NOT NULL REFERENCES printings(id), part_index INTEGER NOT NULL,
 target_printing_id TEXT NOT NULL, component TEXT NOT NULL, name TEXT,
 PRIMARY KEY(printing_id,part_index));
CREATE INDEX IF NOT EXISTS part_target ON related_parts(target_printing_id);
CREATE TABLE IF NOT EXISTS issues(
 seq INTEGER NOT NULL REFERENCES source_records(seq), scope TEXT NOT NULL,
 reason TEXT NOT NULL, PRIMARY KEY(seq,scope,reason));
"""


def run_get(db, key):
    row = db.execute("SELECT value FROM run WHERE key=?", (key,)).fetchone()
    return json.loads(row[0]) if row else None


def run_set(db, key, value):
    db.execute("INSERT INTO run VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
               (key, compact(value)))


def issue(db, seq, scope, reason):
    db.execute("INSERT OR IGNORE INTO issues VALUES(?,?,?)", (seq, scope, reason))


def identity(db, oracle, fallback):
    key = "oracle:" + oracle if oracle else fallback
    db.execute("INSERT OR IGNORE INTO identities VALUES(?,?,?)",
               (key, "oracle" if oracle else "printing_surface", oracle))
    return key


def normalize(db, seq, card):
    if not isinstance(card, dict) or card.get("object") != "card":
        raise ValueError("Not a card object")
    pid = uuid(card.get("id"))
    name = text_field(card, "name", True)
    layout = text_field(card, "layout", True)
    oracle = optional_uuid(card, "oracle_id")
    illustration = optional_uuid(card, "illustration_id")
    set_code = text_field(card, "set", True)
    collector = text_field(card, "collector_number", True)
    language = text_field(card, "lang", True)
    status = text_field(card, "image_status")
    finishes, games = card.get("finishes"), card.get("games")
    if not isinstance(finishes, list) or not all(isinstance(x, str) for x in finishes):
        raise ValueError("Invalid finishes")
    if not isinstance(games, list) or not all(isinstance(x, str) for x in games):
        raise ValueError("Invalid games")
    faces = card.get("card_faces", [])
    parts = card.get("all_parts", [])
    if not isinstance(faces, list) or not all(isinstance(x, dict) for x in faces):
        raise ValueError("Invalid card_faces")
    if not isinstance(parts, list) or not all(isinstance(x, dict) for x in parts):
        raise ValueError("Invalid all_parts")
    if len(faces) > 32 or len(parts) > MAX_PARTS:
        raise ValueError("Relationship bound exceeded")
    if db.execute("SELECT 1 FROM printings WHERE id=?", (pid,)).fetchone():
        previous = db.execute("SELECT s.raw_sha256 FROM printings p JOIN source_records s ON s.seq=p.seq WHERE p.id=?", (pid,)).fetchone()[0]
        current = db.execute("SELECT raw_sha256 FROM source_records WHERE seq=?", (seq,)).fetchone()[0]
        return pid, "duplicate" if previous == current else "conflict", "duplicate_printing" if previous == current else "conflicting_printing"
    parent_key = identity(db, oracle, "printing:" + pid)
    destination = "explicit_negative" if layout in NEGATIVE_LAYOUTS else "manual_nonplayable" if layout in NONPLAYABLE else "unsupported_layout" if layout not in ROOT_LAYOUTS | SEPARATE_LAYOUTS else "printing_selection_required"
    db.execute("INSERT INTO printings VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
               (pid, seq, name, oracle, presence(card, "oracle_id"), parent_key, set_code, collector,
                language, compact(finishes), layout, compact(games), status, destination,
                illustration, presence(card, "illustration_id")))
    face_keys = []
    face_images = []
    for index, face in enumerate(faces):
        face_oracle = optional_uuid(face, "oracle_id")
        face_illustration = optional_uuid(face, "illustration_id")
        key = identity(db, face_oracle, f"face:{pid}:{index}")
        face_keys.append(key)
        has_image = bool(face.get("image_uris"))
        face_images.append(has_image)
        db.execute("INSERT INTO faces VALUES(?,?,?,?,?,?,?,?,?)",
                   (pid, index, text_field(face, "name"), face_oracle, presence(face, "oracle_id"),
                    key, face_illustration, presence(face, "illustration_id"), int(has_image)))
    if not oracle:
        issue(db, seq, "parent", "missing_root_oracle")
    if layout not in ROOT_LAYOUTS | SEPARATE_LAYOUTS:
        issue(db, seq, "parent", "unsupported_layout")
    if destination == "manual_nonplayable":
        issue(db, seq, "parent", "nonplayable_requires_declared_manual_destination")
    if destination == "explicit_negative":
        issue(db, seq, "parent", "nonplayable_negative_reference")
    if "paper" not in games:
        issue(db, seq, "parent", "not_paper")
    root_image = bool(card.get("image_uris"))
    if layout in SEPARATE_LAYOUTS and (root_image or len(faces) < 2):
        issue(db, seq, "parent", "layout_image_mapping_conflict")
    elif layout in ROOT_LAYOUTS and (not root_image or any(face_images)):
        issue(db, seq, "parent", "layout_image_mapping_conflict")
    if layout in SEPARATE_LAYOUTS:
        for index, has_image in enumerate(face_images):
            if not has_image:
                issue(db, seq, f"face:{index}", "missing_face_image")
    surfaces = [(None, card)] + list(enumerate(faces))
    reference_count = 0
    for index, surface in surfaces:
        if not surface.get("image_uris"):
            continue
        urls = surface["image_uris"]
        if not isinstance(urls, dict):
            raise ValueError("Invalid image_uris")
        scope = "root" if index is None else f"face:{index}"
        full = {key: value for key, value in urls.items() if key in ("normal", "large", "png")}
        reasons = []
        if not full:
            reasons.append("missing_full_image")
        if any(not image_url_allowed(value) for value in full.values()):
            reasons.append("unsafe_full_image_url")
        if status != "highres_scan":
            reasons.append("image_status_" + (status if status in ("missing", "placeholder", "lowres") else "unknown"))
        if layout not in ROOT_LAYOUTS | SEPARATE_LAYOUTS:
            reasons.append("unsupported_layout")
        if "paper" not in games:
            reasons.append("not_paper")
        if layout in NEGATIVE_LAYOUTS:
            reasons.append("nonplayable_negative_reference")
        # Conflicts are retained as references but require review before downstream selection.
        if db.execute("SELECT 1 FROM issues WHERE seq=? AND reason='layout_image_mapping_conflict'", (seq,)).fetchone():
            reasons.append("layout_image_mapping_conflict")
        for reason in reasons:
            issue(db, seq, scope, reason)
        ref = f"{pid}:{scope}"
        db.execute("INSERT INTO image_references VALUES(?,?,?,?,?,?,?,?)",
                   (ref, pid, index, optional_uuid(surface, "illustration_id"), presence(surface, "illustration_id"),
                    status, compact(full), int(not reasons)))
        links = [(parent_key, "parent_printing_identity")]
        links.extend((key, "included_face_identity") for key in face_keys) if index is None else links.append((face_keys[index], "visible_face_identity"))
        for key, relation in links:
            db.execute("INSERT OR IGNORE INTO reference_identities VALUES(?,?,?)", (ref, key, relation))
        reference_count += 1
    if not reference_count:
        issue(db, seq, "parent", "missing_image_reference")
    for index, part in enumerate(parts):
        db.execute("INSERT INTO related_parts VALUES(?,?,?,?,?)",
                   (pid, index, uuid(part.get("id")), text_field(part, "component", True), text_field(part, "name")))
    return pid, "accepted", None


def ingest_record(db, seq, raw):
    digest = hashlib.sha256(raw).hexdigest()
    db.execute("INSERT INTO source_records VALUES(?,NULL,?,?,'pending',NULL)", (seq, digest, raw))
    db.execute("SAVEPOINT record")
    try:
        card = strict_json(raw)
        pid, disposition, reason = normalize(db, seq, card)
    except (ValueError, TypeError, RecursionError, UnicodeError, OverflowError):
        db.execute("ROLLBACK TO record")
        pid, disposition, reason = None, "rejected", "invalid_card_metadata_or_json"
    finally:
        db.execute("RELEASE record")
    db.execute("UPDATE source_records SET printing_id=?,disposition=?,reason=? WHERE seq=?",
               (pid, disposition, reason, seq))
    if reason:
        issue(db, seq, "record", reason)


def counts(db, query):
    result = {}
    key_bytes = 0
    for key, count in db.execute(query):
        key_bytes += len(str(key).encode("utf-8"))
        if len(result) >= 4096 or key_bytes > 8 * 1024**2:
            raise CatalogError("Coverage dimension exceeds 4096-group/8 MiB key bound; inspect source metadata")
        result[key] = count
    return result


def safe_coverage(db):
    try:
        return coverage(db)
    except CatalogError:
        return {"status": "INCOMPLETE", "schema_version": SCHEMA, "generator_version": VERSION,
                "coverage_error": "dimension_group_bound_exceeded", "checkpoint": run_get(db, "checkpoint")}


def coverage(db):
    state = {key: json.loads(value) for key, value in db.execute("SELECT key,value FROM run")}
    totals = {table: db.execute(f"SELECT count(*) FROM {table}").fetchone()[0] for table in
              ("source_records", "printings", "identities", "faces", "image_references", "reference_identities", "related_parts", "issues")}
    totals["eligible_image_references"] = db.execute("SELECT count(*) FROM image_references WHERE eligible=1").fetchone()[0]
    totals["illustration_ids"] = db.execute("SELECT count(DISTINCT illustration_id) FROM image_references WHERE illustration_id IS NOT NULL").fetchone()[0]
    totals["oracle_ids"] = db.execute("SELECT count(*) FROM identities WHERE kind='oracle'").fetchone()[0]
    return {"generator_version": VERSION, "schema_version": SCHEMA, "state": state,
            "status": state.get("status", "INCOMPLETE"), "metadata_only": True,
            "all_language_printing_coverage": "source_all_cards_only_not_independently_audited" if state.get("bulk_type") == "all_cards" else "NOT_ALL_LANGUAGES",
            "source_claim": "source-snapshot inventory only; no visual coverage or recognition certification",
            "counts": totals,
            "layouts": counts(db, "SELECT layout,count(*) FROM printings GROUP BY layout"),
            "languages": counts(db, "SELECT lang,count(*) FROM printings GROUP BY lang"),
            "destinations": counts(db, "SELECT destination,count(*) FROM printings GROUP BY destination"),
            "dispositions": counts(db, "SELECT disposition,count(*) FROM source_records GROUP BY disposition"),
            "issue_occurrences": counts(db, "SELECT reason,count(*) FROM issues GROUP BY reason"),
            "records_per_issue": counts(db, "SELECT reason,count(DISTINCT seq) FROM issues GROUP BY reason"),
            "image_statuses": counts(db, "SELECT coalesce(image_status,'unknown'),count(*) FROM image_references GROUP BY image_status"),
            "printing_image_statuses": counts(db, "SELECT coalesce(image_status,'unknown'),count(*) FROM printings GROUP BY image_status"),
            "parent_oracle_presence": counts(db, "SELECT oracle_state,count(*) FROM printings GROUP BY oracle_state"),
            "face_oracle_presence": counts(db, "SELECT oracle_state,count(*) FROM faces GROUP BY oracle_state"),
            "reference_illustration_presence": counts(db, "SELECT illustration_state,count(*) FROM image_references GROUP BY illustration_state"),
            "unresolved_related_targets": db.execute("SELECT count(*) FROM related_parts r LEFT JOIN printings p ON p.id=r.target_printing_id WHERE p.id IS NULL").fetchone()[0],
            "meld_links": db.execute("SELECT count(*) FROM related_parts WHERE component IN ('meld_part','meld_result')").fetchone()[0]}


def process_peak_memory():
    if os.name == "nt":
        import ctypes
        from ctypes import wintypes

        class Counters(ctypes.Structure):
            _fields_ = [("cb", wintypes.DWORD), ("PageFaultCount", wintypes.DWORD)] + [
                (name, ctypes.c_size_t) for name in ("PeakWorkingSetSize", "WorkingSetSize",
                "QuotaPeakPagedPoolUsage", "QuotaPagedPoolUsage", "QuotaPeakNonPagedPoolUsage",
                "QuotaNonPagedPoolUsage", "PagefileUsage", "PeakPagefileUsage")]

        counters = Counters()
        counters.cb = ctypes.sizeof(counters)
        kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        kernel.GetCurrentProcess.restype = wintypes.HANDLE
        psapi = ctypes.WinDLL("psapi", use_last_error=True)
        psapi.GetProcessMemoryInfo.argtypes = (wintypes.HANDLE, ctypes.POINTER(Counters), wintypes.DWORD)
        if psapi.GetProcessMemoryInfo(kernel.GetCurrentProcess(), ctypes.byref(counters), counters.cb):
            return {"method": "Windows GetProcessMemoryInfo lifetime peak working set", "bytes": counters.PeakWorkingSetSize}
    elif sys.platform in ("linux", "darwin"):
        import resource
        return {"method": "getrusage lifetime maxrss", "bytes": resource.getrusage(resource.RUSAGE_SELF).ru_maxrss * (1 if sys.platform == "darwin" else 1024)}
    return {"method": "unavailable", "bytes": None}


def ingest(snapshot, out, bulk_type="local_unknown", limit=None, batch_size=250, bounds=Bounds(), interrupt_hook=None):
    with writer_lock(out):
        return _ingest(snapshot, out, bulk_type, limit, batch_size, bounds, interrupt_hook)


def _ingest(snapshot, out, bulk_type, limit, batch_size, bounds, interrupt_hook):
    if bulk_type not in BULK_TYPES + ("local_unknown",):
        raise CatalogError("Invalid bulk type")
    if limit is not None and limit <= 0 or not 0 < batch_size <= 10_000:
        raise CatalogError("Invalid limit/batch size")
    out = external(out)
    out.mkdir(parents=True, exist_ok=True)
    deadline = time.monotonic() + bounds.seconds
    db = sqlite3.connect(out / "catalog.sqlite", timeout=1)
    try:
        db.execute("PRAGMA foreign_keys=ON")
        db.execute("PRAGMA cache_size=-8192")
        db.execute("PRAGMA journal_mode=DELETE")
        db.executescript(DDL)
    except BaseException:
        db.close()
        raise
    snapshot = Path(snapshot).resolve()
    try:
        with db:
            run_set(db, "status", "INCOMPLETE")
            run_set(db, "last_error", None)
        atomic_json(out / "coverage.json", safe_coverage(db))
        evidence = verify_snapshot(snapshot, bounds, deadline)
        fingerprint = {"snapshot_sha256": evidence["sha256"], "schema": SCHEMA,
                       "generator_version": VERSION, "bulk_type": bulk_type}
        old = run_get(db, "fingerprint")
        if old is not None and old != fingerprint:
            raise CatalogError("Snapshot/schema/source fingerprint differs; use a new output directory")
        disk_check(out, max(64 * 1024**2, evidence["decompressed_bytes"] * 3), bounds)
        with db:
            run_set(db, "fingerprint", fingerprint)
            run_set(db, "snapshot", evidence)
            run_set(db, "bulk_type", bulk_type)
            run_set(db, "limit", limit)
            run_set(db, "language_boundary", "all_cards_source" if bulk_type == "all_cards" else "not_all_language_printings")
            provenance = {"source": "local_supplied_snapshot", "bulk_type_is_local_declaration": True}
            marker = snapshot.with_suffix(snapshot.suffix + ".verified.json")
            if marker.exists():
                if marker.stat().st_size > bounds.record:
                    raise CatalogError("Source marker byte bound exceeded")
                verified = strict_json(marker.read_bytes())
                if not isinstance(verified, dict) or not isinstance(verified.get("snapshot"), dict):
                    raise CatalogError("Invalid source verification marker")
                if verified["snapshot"].get("sha256") != evidence["sha256"]:
                    raise CatalogError("Source verification marker does not match snapshot")
                source = verified.get("source_metadata", {})
                if not isinstance(source, dict) or source.get("type") != bulk_type:
                    raise CatalogError("Declared bulk type disagrees with source marker")
                provenance = verified
            run_set(db, "provenance", provenance)
        checkpoint = run_get(db, "checkpoint") or 0
        inventory_count, maximum_seq = db.execute("SELECT count(*),coalesce(max(seq),0) FROM source_records").fetchone()
        if inventory_count != checkpoint or maximum_seq != checkpoint:
            raise CatalogError("Inventory rows and durable checkpoint disagree")
        if limit is not None and checkpoint > limit:
            raise CatalogError("Requested limit precedes checkpoint; use a new output directory")
        last = checkpoint
        prefix = iter(db.execute("SELECT seq,raw_sha256 FROM source_records WHERE seq<=? ORDER BY seq", (checkpoint,)))
        db.execute("BEGIN IMMEDIATE")
        try:
            for seq, raw, _ in lines(snapshot, bounds, deadline):
                if seq <= checkpoint:
                    expected = next(prefix, None)
                    if expected != (seq, hashlib.sha256(raw).hexdigest()):
                        raise CatalogError("Committed source prefix differs from verified snapshot")
                    continue
                if limit is not None and seq > limit:
                    break
                ingest_record(db, seq, raw)
                last = seq
                if interrupt_hook:
                    interrupt_hook(seq)
                if seq % batch_size == 0:
                    run_set(db, "checkpoint", seq)
                    db.commit()
                    disk_check(out, 16 * 1024**2, bounds)
                    db.execute("BEGIN IMMEDIATE")
            if sha_file(snapshot, bounds, deadline)[0] != evidence["sha256"]:
                raise CatalogError("Snapshot changed during ingestion")
            run_set(db, "checkpoint", last)
            run_set(db, "status", "COMPLETE_SOURCE" if limit is None and last == evidence["source_records"] else "INCOMPLETE")
            run_set(db, "updated_at", datetime.now(timezone.utc).isoformat())
            db.commit()
        except BaseException:
            db.rollback()
            raise
        report = coverage(db)
        atomic_json(out / "coverage.json", report)
        return report
    except BaseException as error:
        db.rollback()
        with db:
            run_set(db, "status", "INCOMPLETE")
            run_set(db, "last_error", type(error).__name__)
        atomic_json(out / "coverage.json", safe_coverage(db))
        raise
    finally:
        db.close()


def main():
    started = time.monotonic()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("ingest", "fetch"))
    parser.add_argument("--snapshot", type=Path)
    parser.add_argument("--out", type=Path)
    parser.add_argument("--cache", type=Path)
    parser.add_argument("--bulk-type", choices=BULK_TYPES)
    parser.add_argument("--limit", type=int)
    parser.add_argument("--batch-size", type=int, default=250)
    parser.add_argument("--max-compressed", type=int, default=Bounds.compressed)
    parser.add_argument("--max-decompressed", type=int, default=Bounds.decompressed)
    parser.add_argument("--max-record", type=int, default=Bounds.record)
    parser.add_argument("--max-records", type=int, default=Bounds.records)
    parser.add_argument("--max-seconds", type=float, default=Bounds.seconds)
    args = parser.parse_args()
    try:
        bounds = Bounds(args.max_compressed, args.max_decompressed, args.max_record, args.max_records, args.max_seconds)
        snapshot = args.snapshot
        if args.command == "fetch" or snapshot is None:
            if not args.bulk_type or not args.cache:
                parser.error("Fetching requires --bulk-type and external --cache")
            from fetch import fetch_snapshot
            snapshot = fetch_snapshot(args.bulk_type, args.cache, bounds)
        if args.command == "ingest":
            if args.out is None:
                parser.error("Ingestion requires external --out")
            report = ingest(snapshot, args.out, args.bulk_type or "local_unknown", args.limit, args.batch_size, bounds)
            runtime = {"status": report["status"], "snapshot_sha256": report["state"]["snapshot"]["sha256"],
                       "elapsed_seconds": time.monotonic() - started, "process_peak_memory": process_peak_memory(),
                       "python": sys.version, "platform": sys.platform,
                       "scope": "whole CLI invocation; desktop process, not Android memory or retained heap"}
            atomic_json(external(args.out) / "runtime.json", runtime)
            print(compact({"status": report["status"], "counts": report["counts"], "snapshot": report["state"]["snapshot"], "runtime": runtime}))
        else:
            print(compact({"snapshot": str(snapshot), "images_downloaded": 0}))
        return 0
    except (CatalogError, OSError, sqlite3.Error, ValueError) as error:
        print(f"Catalog failed ({type(error).__name__}): {error}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("Cancelled; committed checkpoint retained, output INCOMPLETE", file=sys.stderr)
        return 130


if __name__ == "__main__":
    sys.exit(main())
