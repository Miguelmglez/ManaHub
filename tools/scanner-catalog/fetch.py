"""Single-lane official Scryfall bulk fetch with finite retries and exact-source resume."""

from __future__ import annotations

import email.utils
import json
import os
from pathlib import Path
import re
import time
import urllib.error
import urllib.request
from urllib.parse import urlsplit

from catalog import (BULK_TYPES, Bounds, CatalogError, atomic_json, deadline_check,
                     disk_check, external, strict_json, verify_snapshot, writer_lock)

INDEX_URL = "https://api.scryfall.com/bulk-data"
AGENT = "ManaHub-ScannerV2-Catalog/2.0 (private offline metadata research)"
ATTEMPTS = 3
TIMEOUT = 30
SPACING = 0.15
MAX_METADATA = 1024 * 1024


def allowed_url(url, host):
    parsed = urlsplit(url)
    try:
        if (parsed.scheme != "https" or parsed.hostname != host or parsed.port not in (None, 443)
                or parsed.username is not None or parsed.password is not None or parsed.fragment
                or parsed.query or any(c in url for c in ("\\", "\r", "\n", "\t"))):
            return False
    except ValueError:
        return False
    if host == "api.scryfall.com":
        return parsed.path == "/bulk-data"
    return host == "data.scryfall.io" and re.fullmatch(
        r"/(default-cards|all-cards|unique-artwork)/\1-[0-9]{14}\.jsonl\.gz", parsed.path) is not None


def retry_delay(headers, attempt):
    raw = headers.get("Retry-After")
    if raw is None:
        return min(2**attempt, 8)
    try:
        delay = float(raw)
    except ValueError:
        try:
            delay = email.utils.parsedate_to_datetime(raw).timestamp() - time.time()
        except (TypeError, ValueError, OverflowError) as error:
            raise CatalogError("Invalid Retry-After; stopping rather than retrying early") from error
    if not 0 <= delay < float("inf"):
        raise CatalogError("Invalid Retry-After")
    if delay > 60:
        raise CatalogError("Retry-After exceeds retry budget; retry later")
    return max(delay, SPACING)


class Http:
    def __init__(self, deadline):
        self.deadline = deadline
        self.last_request = 0.0
        client = self

        class Redirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, request, response, code, message, headers, newurl):
                host = urlsplit(request.full_url).hostname
                if not allowed_url(newurl, host):
                    raise CatalogError("Redirect outside official source allowlist")
                client.pace()
                return super().redirect_request(request, response, code, message, headers, newurl)

        self.opener = urllib.request.build_opener(Redirect())

    def pace(self):
        delay = max(0, self.last_request + SPACING - time.monotonic())
        self.pause(delay)
        self.last_request = time.monotonic()

    def pause(self, delay):
        deadline_check(self.deadline)
        if time.monotonic() + delay >= self.deadline:
            raise CatalogError("Retry exceeds operation deadline")
        time.sleep(delay)

    def open(self, url, headers=None):
        host = urlsplit(url).hostname
        if not allowed_url(url, host):
            raise CatalogError("URL outside official source allowlist")
        self.pace()
        values = {"User-Agent": AGENT, "Accept": "application/json,application/gzip;q=0.9,*/*;q=0.5",
                  "Accept-Encoding": "identity"}
        values.update(headers or {})
        response = self.opener.open(urllib.request.Request(url, headers=values),
                                    timeout=min(TIMEOUT, max(0.1, self.deadline - time.monotonic())))
        if not allowed_url(response.url, host):
            response.close()
            raise CatalogError("Final URL outside official source allowlist")
        return response

    def index(self):
        for attempt in range(ATTEMPTS):
            try:
                with self.open(INDEX_URL) as response:
                    raw = response.read(MAX_METADATA + 1)
                    if len(raw) > MAX_METADATA:
                        raise CatalogError("Bulk metadata byte bound exceeded")
                    return strict_json(raw)
            except urllib.error.HTTPError as error:
                error.close()
                if error.code not in (429, 500, 502, 503, 504) or attempt + 1 == ATTEMPTS:
                    raise CatalogError("Official bulk metadata request failed") from error
                self.pause(retry_delay(error.headers, attempt))
            except (urllib.error.URLError, TimeoutError, OSError) as error:
                if attempt + 1 == ATTEMPTS:
                    raise CatalogError("Official bulk metadata transport exhausted") from error
                self.pause(min(2**attempt, 8))
        raise CatalogError("Metadata retry budget exhausted")


def select_metadata(index, bulk_type, bounds):
    if not isinstance(index, dict) or index.get("object") != "list" or index.get("has_more") is not False:
        raise CatalogError("Unsupported bulk index envelope")
    rows = index.get("data")
    if not isinstance(rows, list) or len(rows) > 100:
        raise CatalogError("Invalid bulk metadata list")
    matches = [row for row in rows if isinstance(row, dict) and row.get("type") == bulk_type]
    if len(matches) != 1:
        raise CatalogError("Requested bulk type missing or duplicated")
    row = matches[0]
    url, size = row.get("jsonl_download_uri"), row.get("compressed_size")
    if row.get("object") != "bulk_data" or not isinstance(url, str) or not allowed_url(url, "data.scryfall.io"):
        raise CatalogError("Unsupported current bulk download URI")
    expected_family = bulk_type.replace("_", "-")
    if urlsplit(url).path.split("/")[1] != expected_family:
        raise CatalogError("Bulk type and URI disagree")
    if type(size) is not int or not 0 < size <= bounds.compressed:
        raise CatalogError("Bulk compressed size exceeds bounds")
    if not isinstance(row.get("updated_at"), str) or not row["updated_at"]:
        raise CatalogError("Missing source snapshot date")
    return row


def download(client, metadata, target, bounds):
    url = metadata["jsonl_download_uri"]
    size = metadata["compressed_size"]
    part = target.with_suffix(target.suffix + ".part")
    journal = part.with_suffix(part.suffix + ".json")
    source_key = {key: metadata[key] for key in ("type", "updated_at", "jsonl_download_uri", "compressed_size")}
    for attempt in range(ATTEMPTS):
        deadline_check(client.deadline)
        saved = None
        if journal.exists():
            if journal.stat().st_size > MAX_METADATA:
                raise CatalogError("Invalid download journal size")
            saved = strict_json(journal.read_bytes())
        offset = part.stat().st_size if part.exists() else 0
        if offset > size:
            raise CatalogError("Partial download exceeds expected length")
        etag = saved.get("etag") if isinstance(saved, dict) and saved.get("source") == source_key else None
        if etag and (not isinstance(etag, str) or not etag.startswith('"') or not etag.endswith('"') or len(etag) > 512):
            etag = None
        if offset and not etag:
            with part.open("wb"):
                pass
            offset = 0
        if offset == size:
            return part
        disk_check(target.parent, size - offset, bounds)
        headers = {"Range": f"bytes={offset}-", "If-Range": etag} if offset else {}
        try:
            with client.open(url, headers) as response:
                code = response.status
                received_etag = response.headers.get("ETag")
                if response.headers.get("Content-Encoding", "identity") != "identity":
                    raise CatalogError("Unexpected HTTP content encoding")
                if offset and code == 206:
                    expected = f"bytes {offset}-{size - 1}/{size}"
                    if response.headers.get("Content-Range") != expected or received_etag != etag:
                        raise CatalogError("Range/ETag mismatch; never concatenate different objects")
                elif code == 200:
                    offset = 0
                else:
                    raise CatalogError("Unexpected download status")
                length = response.headers.get("Content-Length")
                if length is not None and (not length.isdecimal() or int(length) != size - offset):
                    raise CatalogError("Content-Length differs from official metadata")
                atomic_json(journal, {"source": source_key, "etag": received_etag})
                with part.open("ab" if offset else "wb") as sink:
                    while True:
                        deadline_check(client.deadline)
                        chunk = response.read(min(1024 * 1024, size - offset + 1))
                        if not chunk:
                            break
                        offset += len(chunk)
                        if offset > size or offset > bounds.compressed:
                            raise CatalogError("Download byte bound exceeded")
                        sink.write(chunk)
                        sink.flush()
                        os.fsync(sink.fileno())
                if offset != size:
                    raise OSError("Short download")
                return part
        except urllib.error.HTTPError as error:
            error.close()
            if error.code == 416:
                raise CatalogError("Range rejected; inspect partial cache before retry") from error
            if error.code not in (429, 500, 502, 503, 504) or attempt + 1 == ATTEMPTS:
                raise CatalogError("Bulk download HTTP retry budget exhausted") from error
            client.pause(retry_delay(error.headers, attempt))
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            if attempt + 1 == ATTEMPTS:
                raise CatalogError("Bulk download transport retry budget exhausted") from error
            client.pause(min(2**attempt, 8))
    raise CatalogError("Bulk download retries exhausted")


def fetch_snapshot(bulk_type, cache, bounds=Bounds(), client_factory=Http):
    with writer_lock(cache):
        return _fetch_snapshot(bulk_type, cache, bounds, client_factory)


def _fetch_snapshot(bulk_type, cache, bounds, client_factory):
    if bulk_type not in BULK_TYPES:
        raise CatalogError("Unsupported bulk type")
    cache = external(cache)
    cache.mkdir(parents=True, exist_ok=True)
    deadline = time.monotonic() + bounds.seconds
    client = client_factory(deadline)
    index = client.index()
    metadata = select_metadata(index, bulk_type, bounds)
    # The filename is generated from validated official metadata, never a user-provided URL.
    target = cache / Path(urlsplit(metadata["jsonl_download_uri"]).path).name
    atomic_json(cache / "bulk-index.json", index)
    if target.exists():
        evidence = verify_snapshot(target, bounds, deadline)
        if evidence["compressed_bytes"] != metadata["compressed_size"]:
            raise CatalogError("Cached snapshot differs from official compressed size")
        marker = target.with_suffix(target.suffix + ".verified.json")
        if marker.exists():
            if marker.stat().st_size > MAX_METADATA:
                raise CatalogError("Invalid cache verification marker size")
            previous = strict_json(marker.read_bytes())
            if not isinstance(previous, dict) or not isinstance(previous.get("snapshot"), dict):
                raise CatalogError("Invalid cached verification marker")
            if previous["snapshot"].get("sha256") != evidence["sha256"]:
                raise CatalogError("Cached snapshot changed after verification")
            prior_source = previous.get("source_metadata")
            keys = ("type", "updated_at", "jsonl_download_uri", "compressed_size")
            if not isinstance(prior_source, dict) or any(prior_source.get(key) != metadata[key] for key in keys):
                raise CatalogError("Cached snapshot source metadata changed")
    else:
        partial = download(client, metadata, target, bounds)
        evidence = verify_snapshot(partial, bounds, deadline)
        if evidence["compressed_bytes"] != metadata["compressed_size"]:
            raise CatalogError("Downloaded snapshot size mismatch")
        os.replace(partial, target)
    atomic_json(target.with_suffix(target.suffix + ".verified.json"),
                {"source_metadata": metadata, "snapshot": evidence,
                 "source_transport": "official_https_allowlist", "authenticity": "no_signed_source_manifest"})
    return target
