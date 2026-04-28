#!/usr/bin/env python3
"""
ManaHub pHash generator — produces card_hashes.bin for the on-device scanner.

Algorithm: 256-bit DCT-based perceptual hash using OpenCV (cv2.dct), matching
the OpenCV Android runtime bit-for-bit. Do NOT replace cv2 with imagehash here;
the two libraries normalise DCT output differently and will produce divergent bits.

Binary output format (card_hashes.bin):
  [4B magic "MHSH"][1B version=1][4B count uint32 LE]
  [count × ([16B UUID big-endian][32B hash = 4 × int64 big-endian])]

Companion output (card_index.json):
  { "<scryfall_id>": { "name": "...", "set": "...", "collector_number": "..." }, ... }
"""

import json
import os
import struct
import sys
import time
import uuid
from pathlib import Path

import cv2
import numpy as np
import requests
from tqdm import tqdm

# ── Constants ─────────────────────────────────────────────────────────────────

SCRYFALL_BASE = "https://api.scryfall.com"
SCRYFALL_BULK  = f"{SCRYFALL_BASE}/bulk-data"

HEADERS = {
    "User-Agent": "ManaHub/1.0 hash-generator (contact: miguel.mglez@gmail.com)",
    "Accept": "application/json",
}

HASH_SIZE        = 16          # → 16×16 = 256-bit pHash
IMG_SIZE         = HASH_SIZE * 4  # = 64 (resize target before DCT)
RATE_LIMIT_S     = 0.11        # 110 ms ≥ Scryfall's 100 ms guideline
BINARY_MAGIC     = b"MHSH"
BINARY_VERSION   = 1
MAX_RETRIES      = 3
RETRY_DELAY_S    = 2.0

SCRIPT_DIR  = Path(__file__).parent
CACHE_DIR   = SCRIPT_DIR / "image_cache"
OUTPUT_BIN  = SCRIPT_DIR / "card_hashes.bin"
OUTPUT_JSON = SCRIPT_DIR / "card_index.json"
ASSETS_BIN  = SCRIPT_DIR / "../../app/src/main/assets/card_hashes.bin"


# ── pHash ─────────────────────────────────────────────────────────────────────

def compute_phash_256(img_bgr: np.ndarray) -> np.ndarray:
    """
    Returns a 256-element uint8 array of 0/1 bits.

    Steps (mirror of OpenCV Android implementation):
      1. Grayscale
      2. Resize to IMG_SIZE × IMG_SIZE with LANCZOS4
      3. Convert to float32
      4. cv2.dct (2-D DCT-II, same algorithm as OpenCV Core.dct on Android)
      5. Take upper-left HASH_SIZE × HASH_SIZE block
      6. Threshold each value against the block median → 256 bits
    """
    gray    = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY) if img_bgr.ndim == 3 else img_bgr
    resized = cv2.resize(gray, (IMG_SIZE, IMG_SIZE), interpolation=cv2.INTER_LANCZOS4)
    floats  = resized.astype(np.float32)
    dct     = cv2.dct(floats)
    low     = dct[:HASH_SIZE, :HASH_SIZE].flatten()   # 256 coefficients
    median  = np.median(low)
    return (low > median).astype(np.uint8)


def bits_to_bytes(bits: np.ndarray) -> bytes:
    """
    Pack 256 bits into 32 bytes as 4 big-endian int64 values.
    Bit 0 → MSB of first long; bit 255 → LSB of last long.
    Matches the LongArray(4) read order in the Kotlin runtime.
    """
    assert len(bits) == 256
    value = int("".join(str(b) for b in bits), 2)
    return value.to_bytes(32, byteorder="big")


# ── Network helpers ───────────────────────────────────────────────────────────

def get_json(url: str, session: requests.Session) -> dict:
    for attempt in range(MAX_RETRIES):
        try:
            r = session.get(url, headers=HEADERS, timeout=30)
            r.raise_for_status()
            return r.json()
        except Exception as e:
            if attempt == MAX_RETRIES - 1:
                raise
            print(f"  Retry {attempt + 1}/{MAX_RETRIES} for {url}: {e}")
            time.sleep(RETRY_DELAY_S)


def download_image(url: str, cache_path: Path, session: requests.Session) -> np.ndarray | None:
    """Downloads image to cache_path if not already cached; returns BGR ndarray or None."""
    if cache_path.exists():
        img = cv2.imread(str(cache_path))
        if img is not None:
            return img

    for attempt in range(MAX_RETRIES):
        try:
            time.sleep(RATE_LIMIT_S)
            r = session.get(url, headers=HEADERS, timeout=30, stream=True)
            r.raise_for_status()
            data = r.content
            cache_path.parent.mkdir(parents=True, exist_ok=True)
            cache_path.write_bytes(data)
            arr = np.frombuffer(data, np.uint8)
            img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
            return img
        except Exception as e:
            if attempt == MAX_RETRIES - 1:
                print(f"  Failed to download {url}: {e}")
                return None
            print(f"  Retry {attempt + 1}/{MAX_RETRIES} for image: {e}")
            time.sleep(RETRY_DELAY_S)

    return None


# ── Bulk data URL resolution ───────────────────────────────────────────────────

def get_unique_artwork_url(session: requests.Session) -> str:
    print("Fetching Scryfall bulk-data manifest…")
    data = get_json(SCRYFALL_BULK, session)
    for entry in data.get("data", []):
        if entry.get("type") == "unique_artwork":
            url = entry["download_uri"]
            print(f"  unique-artwork URL: {url}")
            return url
    raise RuntimeError("unique_artwork entry not found in Scryfall bulk-data manifest")


# ── Art crop URLs from a card object ─────────────────────────────────────────

def art_crop_urls(card: dict) -> list[tuple[str, str]]:
    """
    Returns a list of (scryfall_id, art_crop_url) tuples.
    For double-faced cards both faces are included under the same scryfall_id.
    """
    sid = card["id"]
    results = []

    # Single-faced cards
    if "image_uris" in card and "art_crop" in card["image_uris"]:
        results.append((sid, card["image_uris"]["art_crop"]))
        return results

    # Double-faced cards — hash both faces
    for face in card.get("card_faces", []):
        if "image_uris" in face and "art_crop" in face["image_uris"]:
            results.append((sid, face["image_uris"]["art_crop"]))

    return results


# ── Main ──────────────────────────────────────────────────────────────────────

def main() -> None:
    CACHE_DIR.mkdir(parents=True, exist_ok=True)

    session = requests.Session()

    # Step 1: Resolve unique-artwork download URL
    artwork_url = get_unique_artwork_url(session)

    # Step 2: Download and parse bulk JSON (may be large — stream to memory)
    print("Downloading unique-artwork bulk JSON…")
    r = session.get(artwork_url, headers=HEADERS, timeout=120)
    r.raise_for_status()
    cards: list[dict] = r.json()
    print(f"  Loaded {len(cards):,} cards")

    # Step 3: Process each card
    records: list[tuple[str, bytes]] = []   # (scryfall_id, 32-byte hash)
    index:   dict[str, dict]         = {}   # scryfall_id → metadata

    for card in tqdm(cards, unit="card", desc="Hashing"):
        sid  = card["id"]
        name = card.get("name", "")
        set_ = card.get("set", "")
        num  = card.get("collector_number", "")

        urls = art_crop_urls(card)
        if not urls:
            continue  # no art crop available (tokens, some promos)

        for (entry_id, img_url) in urls:
            # Use a URL-derived filename to avoid collisions
            safe_name = img_url.split("/")[-1].split("?")[0]
            cache_path = CACHE_DIR / safe_name

            img = download_image(img_url, cache_path, session)
            if img is None:
                continue

            bits        = compute_phash_256(img)
            hash_bytes  = bits_to_bytes(bits)
            records.append((entry_id, hash_bytes))
            index[entry_id] = {"name": name, "set": set_, "collector_number": num}

    print(f"\nProcessed {len(records):,} hashes from {len(cards):,} cards")

    # Step 4: Write binary file
    count = len(records)
    with open(OUTPUT_BIN, "wb") as f:
        f.write(BINARY_MAGIC)                          # 4 bytes
        f.write(struct.pack("B", BINARY_VERSION))      # 1 byte
        f.write(struct.pack("<I", count))              # 4 bytes, uint32 LE
        for (sid, hash_bytes) in records:
            f.write(uuid.UUID(sid).bytes)              # 16 bytes, big-endian UUID
            f.write(hash_bytes)                        # 32 bytes, 4 × int64 BE

    size_kb = OUTPUT_BIN.stat().st_size / 1024
    print(f"Written {OUTPUT_BIN} ({size_kb:.1f} KB, {count:,} records)")

    # Step 5: Write JSON index
    with open(OUTPUT_JSON, "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, separators=(",", ":"))
    print(f"Written {OUTPUT_JSON}")

    # Step 6: Copy to assets/
    assets_path = ASSETS_BIN.resolve()
    assets_path.parent.mkdir(parents=True, exist_ok=True)
    assets_path.write_bytes(OUTPUT_BIN.read_bytes())
    print(f"Copied to {assets_path}")

    print("\nDone. Validate with at least 3 known cards before shipping.")


if __name__ == "__main__":
    main()
