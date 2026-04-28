# ManaHub pHash Generator

Generates `card_hashes.bin` — the on-device card-recognition database for ManaHub's scanner.

## Algorithm

256-bit DCT-based perceptual hash using **OpenCV (`cv2.dct`)**.  
**Do not replace `cv2` with `imagehash`** — the two libraries normalise DCT output differently and will produce hash values that diverge from the Android runtime's `Core.dct()`, breaking the matcher.

Both this script and the Kotlin runtime (`CardRecognizer`) implement:

1. Grayscale conversion  
2. Resize to 64×64 (LANCZOS4)  
3. `cv2.dct` / `Core.dct` (2-D DCT-II)  
4. Upper-left 16×16 coefficient block  
5. Threshold each value against the block **median** → 256 bits  

## Binary format (`card_hashes.bin`)

```
[4 B]  magic        "MHSH"
[1 B]  version      0x01
[4 B]  count        uint32 little-endian
count × (
  [16 B]  UUID       big-endian (RFC 4122 bytes)
  [32 B]  hash       4 × int64 big-endian  (256 bits total)
)
```

Approx. size: ~1.3 MB for ~27 k unique-artwork entries.

## Companion file (`card_index.json`)

```json
{ "<scryfall_id>": { "name": "Lightning Bolt", "set": "lea", "collector_number": "161" }, ... }
```

Used by the app to resolve a matched `scryfallId` into display metadata before the Scryfall API cache is populated.

## Setup

```bash
python -m venv .venv
.venv\Scripts\activate        # Windows
# source .venv/bin/activate   # macOS / Linux
pip install -r requirements.txt
```

## Usage

```bash
python generate_hashes.py
```

What it does:
1. Fetches the `unique-artwork` bulk-data URL from `https://api.scryfall.com/bulk-data`
2. Downloads the bulk JSON (~few hundred MB, cached locally)
3. For each card: downloads `image_uris.art_crop` (or both faces for DFCs) to `image_cache/`
4. Computes 256-bit pHash
5. Writes `card_hashes.bin` + `card_index.json` in this directory
6. Copies `card_hashes.bin` → `app/src/main/assets/card_hashes.bin`

Runtime: ~2–4 hours (Scryfall rate-limit: 100 ms between image downloads).  
Images are cached in `image_cache/` so re-runs are fast.

## Validation (CRITICAL — do before full run)

Run a quick sanity check with 3 known cards before processing all ~27 k:

```python
import cv2, numpy as np, uuid, struct

# Load the binary
data = open("card_hashes.bin", "rb").read()
# Skip header (9 bytes: 4+1+4)
count = struct.unpack_from("<I", data, 5)[0]
print(f"{count} records")

# Download a known card's art_crop manually, compute hash, check distance
```

The Hamming distance between two identical images must be **0**.  
Two different artworks of the same card should be **> 10**.  
A completely unrelated card should be **> 40**.

If the distances look wrong, verify that both `cv2.dct` and `Core.dct` (Android) produce the same float values for the same 64×64 input matrix on your test device.

## Re-generating for new sets

Run the script again. Images are cached so only new artwork is downloaded.  
Rebuilt `card_hashes.bin` can be distributed via Firebase Storage (Phase 6).

## `.gitignore` note

`image_cache/` and `card_hashes.bin` are excluded from git (large binary files).  
`card_index.json` is small enough to commit if useful.
