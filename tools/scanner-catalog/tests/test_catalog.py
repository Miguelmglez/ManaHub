import gzip
from contextlib import closing
import io
import json
import os
from pathlib import Path
import sqlite3
import sys
import tempfile
import time
import unittest
from unittest.mock import patch
import urllib.error
import urllib.request

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import catalog
import fetch


def uid(number):
    return f"00000000-0000-0000-0000-{number:012d}"


def image(number=1):
    return {"normal": f"https://cards.scryfall.io/normal/front/0/0/{uid(number)}.jpg"}


def card(number=1, layout="normal"):
    return {"object": "card", "id": uid(number), "oracle_id": uid(1000 + number),
            "name": f"Synthetic {number}", "layout": layout, "set": "tst",
            "collector_number": str(number), "lang": "en", "finishes": ["nonfoil", "foil"],
            "games": ["paper"], "image_status": "highres_scan", "image_uris": image(number)}


class Fixture(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="scanner-catalog-test-", dir=os.environ.get("SCANNER_CATALOG_TEST_TMP"))
        self.root = Path(self.temp.name)
        self.source = self.root / "source.jsonl.gz"
        self.out = self.root / "out"
        self.bounds = catalog.Bounds(reserve=0)

    def tearDown(self):
        self.temp.cleanup()

    def write(self, records=None, raw=None):
        if raw is None:
            raw = b"".join(json.dumps(row).encode() + b"\n" for row in records)
        with self.source.open("wb") as sink:
            with gzip.GzipFile(filename="", mode="wb", fileobj=sink, mtime=0) as stream:
                stream.write(raw)
        return self.source

    def ingest(self, **options):
        return catalog.ingest(self.source, self.out, bounds=self.bounds, **options)

    def query(self, sql):
        with closing(sqlite3.connect(self.out / "catalog.sqlite")) as db:
            return db.execute(sql).fetchall()


class InventoryTests(Fixture):
    def test_complete_raw_and_finishes(self):
        original = card()
        original["nullable_field"] = None
        self.write([original])
        report = self.ingest()
        self.assertEqual("COMPLETE_SOURCE", report["status"])
        self.assertEqual(1, report["counts"]["eligible_image_references"])
        self.assertEqual("NOT_ALL_LANGUAGES", report["all_language_printing_coverage"])
        raw = self.query("SELECT raw FROM source_records")[0][0]
        self.assertEqual(original, json.loads(raw))
        self.assertEqual('["nonfoil","foil"]', self.query("SELECT finishes_json FROM printings")[0][0])

    def test_layouts_use_actual_image_surfaces_and_parent_mapping(self):
        rows = [card(i + 1, layout) for i, layout in enumerate(("normal", "transform", "split", "adventure", "flip", "meld"))]
        for row in rows[1:5]:
            row["card_faces"] = [{"name": "Face A"}, {"name": "Face B"}]
        rows[1].pop("image_uris")
        for i, face in enumerate(rows[1]["card_faces"]):
            face["image_uris"] = image(i + 50)
        rows[-1]["all_parts"] = [{"id": uid(6), "component": "meld_result", "name": "Combined"},
                                 {"id": uid(100), "component": "meld_part", "name": "Part A"},
                                 {"id": uid(101), "component": "meld_part", "name": "Part B"}]
        self.write(rows)
        report = self.ingest()
        self.assertEqual(7, report["counts"]["image_references"])
        self.assertEqual(8, report["counts"]["faces"])
        self.assertEqual(3, report["meld_links"])
        self.assertEqual(2, report["unresolved_related_targets"])
        self.assertNotIn("layout_image_mapping_conflict", report["issue_occurrences"])
        reverse = self.query("SELECT identity_key FROM reference_identities WHERE reference_key='" + uid(2) + ":face:1' AND relation='parent_printing_identity'")
        self.assertEqual([("oracle:" + uid(1002),)], reverse)

    def test_missing_parent_oracle_preserves_distinct_face_oracles(self):
        row = card(layout="reversible_card")
        row.pop("oracle_id")
        row.pop("image_uris")
        row["card_faces"] = [{"name": "A", "oracle_id": uid(50), "image_uris": image(50)},
                             {"name": "B", "oracle_id": uid(51), "image_uris": image(51)}]
        self.write([row])
        report = self.ingest()
        self.assertEqual({"absent": 1}, report["parent_oracle_presence"])
        self.assertEqual(3, report["counts"]["identities"])
        self.assertEqual([("oracle:" + uid(51),)], self.query("SELECT identity_key FROM reference_identities WHERE relation='visible_face_identity' AND reference_key LIKE '%face:1'"))

    def test_no_illustration_deduplication(self):
        rows = [card(1), card(2)]
        for row in rows:
            row["illustration_id"] = uid(80)
        self.write(rows)
        report = self.ingest()
        self.assertEqual(2, report["counts"]["image_references"])
        self.assertEqual(1, report["counts"]["illustration_ids"])

    def test_reprints_keep_panel_surrogates_but_share_authoritative_parent_oracle(self):
        rows = [card(1, "split"), card(2, "split")]
        for row in rows:
            row["oracle_id"] = uid(900)
            row["card_faces"] = [{"name": "A"}, {"name": "B"}]
        self.write(rows)
        report = self.ingest()
        self.assertEqual(5, report["counts"]["identities"])
        self.assertEqual(1, report["counts"]["oracle_ids"])
        parents = self.query("SELECT identity_key FROM reference_identities WHERE relation='parent_printing_identity' ORDER BY reference_key")
        self.assertEqual([("oracle:" + uid(900),)] * 2, parents)
        self.assertEqual(4, len(self.query("SELECT identity_key FROM reference_identities WHERE relation='included_face_identity'")))

    def test_null_and_absent_are_distinguished(self):
        rows = [card(1), card(2)]
        rows[0].pop("oracle_id")
        rows[1]["oracle_id"] = None
        rows[1]["illustration_id"] = None
        self.write(rows)
        self.ingest()
        self.assertEqual([("absent", "absent"), ("null", "null")], self.query("SELECT oracle_state,illustration_state FROM printings ORDER BY seq"))

    def test_duplicate_and_conflicting_printings_remain_auditable(self):
        original = card()
        changed = dict(original, name="Conflict")
        self.write([original, original, changed])
        report = self.ingest()
        self.assertEqual({"accepted": 1, "conflict": 1, "duplicate": 1}, report["dispositions"])
        self.assertEqual(3, report["counts"]["source_records"])
        self.assertEqual(1, report["counts"]["printings"])

    def test_quality_and_mapping_exclusions_not_dropped(self):
        rows = [card(i) for i in range(1, 6)]
        rows[0]["image_status"] = "placeholder"
        rows[1]["image_status"] = "lowres"
        rows[2].pop("image_uris")
        rows[3]["layout"] = "future_layout"
        rows[4]["layout"] = "transform"
        self.write(rows)
        report = self.ingest()
        self.assertEqual(5, report["counts"]["printings"])
        self.assertEqual(4, report["counts"]["image_references"])
        self.assertEqual(0, report["counts"]["eligible_image_references"])
        for reason in ("image_status_placeholder", "image_status_lowres", "missing_image_reference", "unsupported_layout", "layout_image_mapping_conflict"):
            self.assertIn(reason, report["issue_occurrences"])

    def test_nonplayable_destinations_declared(self):
        self.write([card(1, "token"), card(2, "emblem"), card(3, "art_series")])
        report = self.ingest()
        self.assertEqual({"manual_nonplayable": 3}, report["destinations"])

    def test_art_series_actual_double_faces_and_missing_images_not_corruption(self):
        rows = [card(i, "art_series") for i in (1, 2, 3)]
        for i, row in enumerate(rows):
            row.pop("image_uris")
            row["card_faces"] = [{"name": "Art"}, {"name": "Details"}]
            for index in range(i):
                row["card_faces"][index]["image_uris"] = image(50 + index)
        self.write(rows)
        report = self.ingest()
        self.assertEqual({"manual_nonplayable": 3}, report["destinations"])
        self.assertEqual(3, report["counts"]["image_references"])
        self.assertEqual(3, report["counts"]["eligible_image_references"])
        self.assertNotIn("layout_image_mapping_conflict", report["issue_occurrences"])
        self.assertEqual(3, report["issue_occurrences"]["missing_face_image"])

    def test_prepared_spell_is_included_panel_not_separate_image(self):
        row = card(1, "prepare")
        row["card_faces"] = [{"name": "Creature"}, {"name": "Prepared spell"}]
        self.write([row])
        report = self.ingest()
        self.assertEqual(1, report["counts"]["image_references"])
        self.assertEqual(2, report["counts"]["faces"])
        self.assertEqual(1, report["counts"]["oracle_ids"])
        self.assertEqual(0, report["counts"]["issues"])

    def test_front_card_surface_is_explicit_negative_not_playable_or_malformed(self):
        self.write([card(1, "front_card")])
        report = self.ingest()
        self.assertEqual({"accepted": 1}, report["dispositions"])
        self.assertEqual({"explicit_negative": 1}, report["destinations"])
        self.assertEqual(1, report["counts"]["image_references"])
        self.assertEqual(0, report["counts"]["eligible_image_references"])
        self.assertNotIn("layout_image_mapping_conflict", report["issue_occurrences"])

    def test_real_maximum_related_part_count_supported_with_finite_headroom(self):
        row = card(1, "token")
        row["all_parts"] = [{"id": uid(5000+i), "component": "token", "name": "Synthetic"} for i in range(374)]
        self.write([row])
        report = self.ingest()
        self.assertEqual({"accepted": 1}, report["dispositions"])
        self.assertEqual(374, report["counts"]["related_parts"])
        row["all_parts"] *= 3
        self.write([row])
        report = catalog.ingest(self.source, self.root / "bounded", bounds=self.bounds)
        self.assertEqual({"rejected": 1}, report["dispositions"])

    def test_unsafe_image_and_art_crop_only_retained(self):
        rows = [card(1), card(2)]
        rows[0]["image_uris"]["normal"] = "http://localhost/private"
        rows[1]["image_uris"] = {"art_crop": "https://cards.scryfall.io/art_crop/test.jpg"}
        self.write(rows)
        report = self.ingest()
        self.assertEqual(0, report["counts"]["eligible_image_references"])
        self.assertEqual(1, report["records_per_issue"]["unsafe_full_image_url"])
        self.assertEqual(1, report["records_per_issue"]["missing_full_image"])

    def test_malformed_and_overflow_json_records_rejected_and_preserved(self):
        good = json.dumps(card()).encode() + b"\n"
        self.write(raw=good + b'{"broken":\n' + b'{"x":NaN}\n' + b'{"x":1e999}\n' + b'{"id":1,"id":2}\n' + b'\xff\n')
        report = self.ingest()
        self.assertEqual({"accepted": 1, "rejected": 5}, report["dispositions"])
        self.assertEqual(6, report["counts"]["source_records"])

    def test_failed_record_savepoint_removes_partial_normalization(self):
        bad = card()
        bad["card_faces"] = [{"name": "A", "oracle_id": "invalid"}]
        self.write([bad, card(2)])
        report = self.ingest()
        self.assertEqual(1, report["counts"]["identities"])
        self.assertEqual({"accepted": 1, "rejected": 1}, report["dispositions"])

    def test_relationship_count_bound(self):
        row = card()
        row["card_faces"] = [{}] * 33
        self.write([row])
        self.assertEqual({"rejected": 1}, self.ingest()["dispositions"])

    def test_coverage_group_bound_fails_explicitly_without_large_array(self):
        with closing(sqlite3.connect(":memory:")) as db:
            db.execute("CREATE TABLE dimension(value INTEGER)")
            db.executemany("INSERT INTO dimension VALUES(?)", ((i,) for i in range(4097)))
            with self.assertRaisesRegex(catalog.CatalogError, "4096-group"):
                catalog.counts(db, "SELECT value,count(*) FROM dimension GROUP BY value")

    def test_coverage_key_bytes_bound_even_with_few_large_groups(self):
        with closing(sqlite3.connect(":memory:")) as db:
            db.execute("CREATE TABLE dimension(value TEXT)")
            db.executemany("INSERT INTO dimension VALUES(?)", (("a" * (5 * 1024**2),), ("b" * (5 * 1024**2),)))
            with self.assertRaisesRegex(catalog.CatalogError, "8 MiB key bound"):
                catalog.counts(db, "SELECT value,1 FROM dimension")


class RecoveryTests(Fixture):
    def test_committed_prefix_hash_must_match_on_resume(self):
        self.write([card(1), card(2)])
        self.ingest(limit=1)
        with closing(sqlite3.connect(self.out / "catalog.sqlite")) as db:
            db.execute("UPDATE source_records SET raw_sha256='changed'")
            db.commit()
        with self.assertRaisesRegex(catalog.CatalogError, "Committed source prefix"):
            self.ingest()
        self.assertEqual([(1,)], self.query("SELECT seq FROM source_records"))

    def test_transaction_rollback_then_resume_no_missing_or_duplicate_records(self):
        self.write([card(i) for i in range(1, 8)])
        def kill(seq):
            if seq == 4:
                raise KeyboardInterrupt()
        with self.assertRaises(KeyboardInterrupt):
            self.ingest(batch_size=2, interrupt_hook=kill)
        self.assertEqual([(1,), (2,)], self.query("SELECT seq FROM source_records"))
        self.assertEqual("INCOMPLETE", json.loads((self.out / "coverage.json").read_text())["status"])
        report = self.ingest(batch_size=2)
        self.assertEqual(7, report["counts"]["printings"])
        self.assertEqual("COMPLETE_SOURCE", report["status"])
        self.assertEqual(7, report["state"]["checkpoint"])
        self.assertEqual(7, self.ingest()["counts"]["source_records"])

    def test_changed_source_rejected_even_same_record_count(self):
        self.write([card()])
        self.ingest()
        self.write([card(2)])
        with self.assertRaisesRegex(catalog.CatalogError, "fingerprint differs"):
            self.ingest()
        self.assertEqual("INCOMPLETE", json.loads((self.out / "coverage.json").read_text())["status"])
        self.assertEqual([(uid(1),)], self.query("SELECT id FROM printings"))

    def test_source_kind_change_rejected(self):
        self.write([card()])
        self.ingest(bulk_type="default_cards")
        with self.assertRaises(catalog.CatalogError):
            self.ingest(bulk_type="all_cards")

    def test_truncated_crc_or_empty_snapshot_never_complete(self):
        for content in (b"", b"junk"):
            self.source.write_bytes(content)
            with self.assertRaises(catalog.CatalogError):
                self.ingest()
        self.write([card()])
        raw = self.source.read_bytes()
        for content in (raw[:-5], raw[:-8] + bytes([raw[-8] ^ 1]) + raw[-7:]):
            self.source.write_bytes(content)
            with self.assertRaises(catalog.CatalogError):
                self.ingest()
        self.assertEqual([], self.query("SELECT * FROM source_records"))

    def test_missing_final_newline_rejected(self):
        self.write(raw=json.dumps(card()).encode())
        with self.assertRaisesRegex(catalog.CatalogError, "final newline"):
            self.ingest()

    def test_limits_enforced_before_inventory(self):
        self.write([card(), card(2)])
        settings = [{"record": 10}, {"records": 1}, {"decompressed": 10}, {"compressed": 10}]
        for setting in settings:
            with self.assertRaises(catalog.CatalogError):
                catalog.ingest(self.source, self.out, bounds=catalog.Bounds(reserve=0, **setting))
        self.assertEqual([], self.query("SELECT seq FROM source_records"))

    def test_limit_never_marks_complete_even_above_total_and_resume(self):
        self.write([card(1), card(2)])
        first = self.ingest(limit=1)
        self.assertEqual("INCOMPLETE", first["status"])
        self.assertEqual(1, first["state"]["checkpoint"])
        self.assertEqual("INCOMPLETE", self.ingest(limit=5)["status"])
        self.assertEqual("COMPLETE_SOURCE", self.ingest()["status"])

    def test_output_in_repository_rejected(self):
        with self.assertRaises(catalog.CatalogError):
            catalog.ingest(self.source, catalog.REPO / "forbidden-output")

    def test_disk_guard(self):
        self.write([card()])
        with patch("catalog.shutil.disk_usage", return_value=type("Disk", (), {"free": 0})()):
            with self.assertRaisesRegex(catalog.CatalogError, "disk space"):
                self.ingest()

    def test_deadline_and_invalid_bounds(self):
        with self.assertRaises(catalog.CatalogError):
            catalog.deadline_check(time.monotonic() - 1)
        for seconds in (0, float("nan"), float("inf")):
            with self.assertRaises(catalog.CatalogError):
                catalog.Bounds(seconds=seconds)

    def test_writer_lock_excludes_second_owner_and_releases(self):
        with catalog.writer_lock(self.out):
            with self.assertRaises(catalog.CatalogError):
                with catalog.writer_lock(self.out):
                    self.fail("Second writer acquired lock")
        with catalog.writer_lock(self.out):
            pass


class Response(io.BytesIO):
    def __init__(self, raw, status=200, headers=None):
        super().__init__(raw)
        self.status = status
        self.headers = headers or {}


class NetworkTests(Fixture):
    def metadata(self, size):
        return {"object": "bulk_data", "type": "default_cards", "updated_at": "2026-10-07T00:00:00Z",
                "jsonl_download_uri": "https://data.scryfall.io/default-cards/default-cards-20261007000000.jsonl.gz",
                "compressed_size": size}

    def test_url_allowlist_rejects_arbitrary_hosts_credentials_ports_and_paths(self):
        good = self.metadata(100)["jsonl_download_uri"]
        self.assertTrue(fetch.allowed_url(good, "data.scryfall.io"))
        self.assertTrue(fetch.allowed_url(fetch.INDEX_URL, "api.scryfall.com"))
        for bad in (good.replace("https:", "http:"), good.replace("data.scryfall.io", "data.scryfall.io.evil"),
                    good.replace("data.scryfall.io", "user@data.scryfall.io"), good + "#x", good + "?x=1",
                    good.replace("data.scryfall.io", "data.scryfall.io:444"), "https://127.0.0.1/file",
                    "https://data.scryfall.io/../secret", "https://data.scryfall.io/default-cards/x.gz"):
            self.assertFalse(fetch.allowed_url(bad, "data.scryfall.io"), bad)

    def test_redirect_handler_rejects_cross_host_and_arbitrary_path(self):
        client = fetch.Http(time.monotonic() + 100)
        handler = next(x for x in client.opener.handlers if isinstance(x, urllib.request.HTTPRedirectHandler))
        request = urllib.request.Request(fetch.INDEX_URL)
        for url in ("http://api.scryfall.com/bulk-data", "https://evil.test/bulk-data", "https://api.scryfall.com/cards"):
            with self.assertRaises(catalog.CatalogError):
                handler.redirect_request(request, None, 302, "redirect", {}, url)

    def test_current_metadata_required_not_old_array_uri(self):
        row = self.metadata(100)
        index = {"object": "list", "has_more": False, "data": [row]}
        self.assertEqual(row, fetch.select_metadata(index, "default_cards", self.bounds))
        for bad in (dict(row, compressed_size=True), dict(row, jsonl_download_uri="https://evil.test/file"),
                    {"type": "default_cards", "download_uri": "https://data.scryfall.io/old.json"}):
            with self.assertRaises(catalog.CatalogError):
                fetch.select_metadata(dict(index, data=[bad]), "default_cards", self.bounds)

    def test_retry_after_bounded_and_date_supported(self):
        self.assertEqual(15, fetch.retry_delay({"Retry-After": "15"}, 0))
        date = fetch.email.utils.formatdate(time.time() + 20, usegmt=True)
        self.assertTrue(18 <= fetch.retry_delay({"Retry-After": date}, 0) <= 20)
        for raw in ("5000", "inf", "nan", "bad", "-1"):
            with self.assertRaises(catalog.CatalogError):
                fetch.retry_delay({"Retry-After": raw}, 0)

    def test_download_resume_exact_etag_and_range(self):
        self.write([card()])
        raw = self.source.read_bytes()
        cache = self.root / "cache"
        cache.mkdir()
        target = cache / "source.jsonl.gz"
        partial = target.with_suffix(".gz.part")
        partial.write_bytes(raw[:10])
        metadata = self.metadata(len(raw))
        source = {key: metadata[key] for key in ("type", "updated_at", "jsonl_download_uri", "compressed_size")}
        catalog.atomic_json(partial.with_suffix(".part.json"), {"source": source, "etag": '"stable"'})
        class Client:
            deadline = time.monotonic() + 100
            def open(inner, url, headers):
                self.assertEqual({"Range": "bytes=10-", "If-Range": '"stable"'}, headers)
                return Response(raw[10:], 206, {"ETag": '"stable"', "Content-Range": f"bytes 10-{len(raw)-1}/{len(raw)}", "Content-Length": str(len(raw)-10)})
        result = fetch.download(Client(), metadata, target, self.bounds)
        self.assertEqual(raw, result.read_bytes())

    def test_changed_range_object_not_concatenated(self):
        cache = self.root / "cache"
        cache.mkdir()
        target = cache / "source.jsonl.gz"
        partial = target.with_suffix(".gz.part")
        partial.write_bytes(b"123")
        metadata = self.metadata(10)
        source = {key: metadata[key] for key in ("type", "updated_at", "jsonl_download_uri", "compressed_size")}
        catalog.atomic_json(partial.with_suffix(".part.json"), {"source": source, "etag": '"old"'})
        class Client:
            deadline = time.monotonic() + 100
            def open(inner, url, headers):
                return Response(b"new", 206, {"ETag": '"new"', "Content-Range": "bytes 3-9/10"})
        with self.assertRaisesRegex(catalog.CatalogError, "Range/ETag"):
            fetch.download(Client(), metadata, target, self.bounds)
        self.assertEqual(b"123", partial.read_bytes())

    def test_range_ignored_200_restarts_and_missing_etag_restarts(self):
        for etag in ('"old"', None):
            cache = self.root / ("etag" if etag else "no-etag")
            cache.mkdir()
            target = cache / "source.jsonl.gz"
            partial = target.with_suffix(".gz.part")
            partial.write_bytes(b"OLD")
            metadata = self.metadata(4)
            source = {key: metadata[key] for key in ("type", "updated_at", "jsonl_download_uri", "compressed_size")}
            catalog.atomic_json(partial.with_suffix(".part.json"), {"source": source, "etag": etag})
            class Client:
                deadline = time.monotonic() + 100
                def open(inner, url, headers):
                    self.assertEqual(bool(etag), bool(headers))
                    return Response(b"FULL", 200, {"Content-Length": "4"})
            self.assertEqual(b"FULL", fetch.download(Client(), metadata, target, self.bounds).read_bytes())

    def test_http_retry_after_honored_and_three_attempt_limit(self):
        class Opener:
            count = 0
            def open(inner, request, timeout):
                inner.count += 1
                raise urllib.error.HTTPError(request.full_url, 429, "limited", {"Retry-After": "7"}, None)
        client = fetch.Http(time.monotonic() + 100)
        client.opener = Opener()
        with patch.object(client, "pace"), patch.object(client, "pause") as pause:
            with self.assertRaises(catalog.CatalogError):
                client.index()
        self.assertEqual(3, client.opener.count)
        self.assertEqual([unittest.mock.call(7), unittest.mock.call(7)], pause.call_args_list)

    def test_fetch_validates_crc_and_atomic_cache_then_detects_corruption(self):
        self.write([card()])
        raw = self.source.read_bytes()
        metadata = self.metadata(len(raw))
        class Client:
            def __init__(inner, deadline):
                inner.deadline = deadline
            def index(inner):
                return {"object": "list", "has_more": False, "data": [metadata]}
            def open(inner, url, headers):
                return Response(raw, 200, {"Content-Length": str(len(raw)), "ETag": '"stable"'})
        cache = self.root / "cache"
        target = fetch.fetch_snapshot("default_cards", cache, self.bounds, Client)
        marker = json.loads(target.with_suffix(".gz.verified.json").read_text())
        self.assertTrue(marker["snapshot"]["gzip_crc_verified"])
        self.assertEqual(target, fetch.fetch_snapshot("default_cards", cache, self.bounds, Client))
        target.write_bytes(raw[:-3])
        with self.assertRaises(catalog.CatalogError):
            fetch.fetch_snapshot("default_cards", cache, self.bounds, Client)


if __name__ == "__main__":
    unittest.main()
