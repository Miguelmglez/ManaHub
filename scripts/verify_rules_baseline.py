import argparse
import hashlib
import json
import re
from collections import Counter
from datetime import datetime
from pathlib import Path
from urllib.parse import urlsplit


CHAPTER = re.compile(r"^([1-9])\. ([A-Z].*)$")
SECTION = re.compile(r"^(\d{3})\. (.+)$")
RULE = re.compile(r"^(\d{3}\.\d+[a-z]*)(?:\. | )(.+)$")


def inspect(raw):
    if not raw or len(raw) > 4 * 1024 * 1024:
        raise ValueError("Rules source is empty or exceeds the 4 MiB limit")
    text = raw.decode("utf-8-sig", errors="strict")
    if "\ufffd" in text or "\x00" in text:
        raise ValueError("Invalid text encoding")
    lines = text.replace("\r\n", "\n").replace("\r", "\n").splitlines()
    if lines[0] != "Magic: The Gathering Comprehensive Rules":
        raise ValueError("Missing official document heading")
    date_match = re.search(r"These rules are effective as of ([A-Za-z]+ \d{1,2}, \d{4})\.", text)
    if not date_match:
        raise ValueError("Missing effective date")
    effective_date = datetime.strptime(date_match[1], "%B %d, %Y").date().isoformat()
    contents = lines.index("Contents")
    glossary_positions = [i for i, line in enumerate(lines) if line == "Glossary"]
    credits_positions = [i for i, line in enumerate(lines) if line == "Credits"]
    if len(glossary_positions) != 2 or len(credits_positions) != 2:
        raise ValueError("Missing or repeated table of contents/body boundaries")
    start = credits_positions[0] + 1
    glossary, credits = glossary_positions[1], credits_positions[1]
    if not contents < glossary_positions[0] < credits_positions[0] < glossary < credits:
        raise ValueError("Invalid document ordering")
    toc_chapters = [CHAPTER.fullmatch(line).groups() for line in lines[contents:start] if CHAPTER.fullmatch(line)]
    toc_sections = [SECTION.fullmatch(line).groups() for line in lines[contents:start] if SECTION.fullmatch(line)]
    chapters, sections, rule_ids = [], [], []
    current_chapter = current_section = None
    for line in lines[start:glossary]:
        if chapter := CHAPTER.fullmatch(line):
            current_chapter = chapter[1]
            current_section = None
            chapters.append(chapter.groups())
        elif section := SECTION.fullmatch(line):
            if not current_chapter or section[1][0] != current_chapter:
                raise ValueError("Section has no matching chapter")
            current_section = section[1]
            sections.append(section.groups())
        elif rule := RULE.fullmatch(line):
            if not current_section or rule[1].split(".")[0] != current_section:
                raise ValueError("Rule has no matching section")
            rule_ids.append(rule[1])
        elif re.match(r"^\d{3}(?:\.|\s)", line):
            raise ValueError("Unrecognized numbered content")
        elif line.strip() and not rule_ids:
            raise ValueError("Unaccounted content before first rule")
    if chapters != toc_chapters or sections != toc_sections:
        raise ValueError("Body hierarchy differs from table of contents")
    if len(chapters) != 9 or not sections or not rule_ids:
        raise ValueError("Incomplete rule hierarchy")
    if any(count != 1 for count in Counter(rule_ids).values()):
        raise ValueError("Duplicate rule identifiers")
    rule_set = set(rule_ids)
    for rule_id in rule_ids:
        if rule_id[-1].isalpha() and re.sub(r"[a-z]+$", "", rule_id) not in rule_set:
            raise ValueError("Subrule has no parent")
    def natural(value):
        match = re.fullmatch(r"(\d+)\.(\d+)([a-z]*)", value)
        return int(match[1]), int(match[2]), len(match[3]), match[3]
    if rule_ids != sorted(rule_ids, key=natural):
        raise ValueError("Rules are not naturally ordered")
    glossary_text = "\n".join(lines[glossary + 1:credits]).strip()
    entries = re.split(r"\n[^\S\n]*\n", glossary_text)
    terms = []
    for entry in entries:
        paragraphs = entry.splitlines()
        if len(paragraphs) < 2 or not all(line.strip() for line in paragraphs):
            raise ValueError("Malformed glossary entry")
        terms.append(paragraphs[0])
    if len(terms) != len(set(terms)) or len(terms) < 100:
        raise ValueError("Duplicate or incomplete glossary")
    if not any("Wizards of the Coast" in line for line in lines[credits:]) or not any("All Rights Reserved" in line or "All rights reserved" in line for line in lines[credits:]):
        raise ValueError("Missing attribution or truncated copyright notices")
    notice_text = "\n".join(lines[credits + 1:]).strip()
    if not notice_text.endswith("."):
        raise ValueError("Truncated final notice paragraph")
    counts = {"chapters": len(chapters), "sections": len(sections), "rules": len(rule_ids), "glossaryEntries": len(terms), "documentSections": 3}
    return {"effectiveDate": effective_date, "sha256": hashlib.sha256(raw).hexdigest(), "schemaVersion": 1, "nodeCount": sum(counts.values()), "byteCount": len(raw)}, counts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=Path("app/src/main/assets/rules/baseline.txt"))
    parser.add_argument("--manifest", type=Path, default=Path("app/src/main/assets/rules/baseline-manifest.json"))
    parser.add_argument("--source-url")
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--reviewed-change", action="store_true", help="Acknowledge review of a changed source before replacing an accepted manifest")
    args = parser.parse_args()
    manifest, counts = inspect(args.source.read_bytes())
    if args.write:
        if not args.source_url:
            parser.error("--write requires the explicitly verified --source-url")
        url = urlsplit(args.source_url)
        if url.scheme != "https" or url.hostname != "media.wizards.com" or url.username or url.password or url.port not in (None, 443):
            parser.error("Source must be an HTTPS official media.wizards.com URL")
        if args.manifest.exists():
            previous = json.loads(args.manifest.read_text(encoding="utf-8"))
            if previous.get("sha256") != manifest["sha256"] and not args.reviewed_change:
                parser.error("Changed accepted source requires explicit content/count/notice review and --reviewed-change")
        manifest = {"sourceUrl": args.source_url, **manifest}
        args.manifest.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    else:
        accepted = json.loads(args.manifest.read_text(encoding="utf-8"))
        for key, value in manifest.items():
            if accepted.get(key) != value:
                raise ValueError(f"Manifest mismatch: {key}")
    print(json.dumps({"manifest": manifest, "inventory": counts}, indent=2))


if __name__ == "__main__":
    main()
